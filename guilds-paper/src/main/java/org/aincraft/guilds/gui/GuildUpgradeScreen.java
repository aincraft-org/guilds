package org.aincraft.guilds.gui;

import de.flog99.mapgui.Click;
import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.Screen;
import de.flog99.mapgui.ui.AwtFont;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.PaintContext;
import de.flog99.mapgui.ui.Painter;
import de.flog99.mapgui.ui.Rect;
import de.flog99.mapgui.ui.TextFont;
import net.kyori.adventure.text.Component;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.services.GuildLevelService;
import org.aincraft.guilds.services.GuildProjectService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TechTreeService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.flog99.mapgui.ui.Ui.Draw;

/**
 * Native 128x128 MapGUI tech web for guild upgrades.
 *
 * <p>The graph coordinates are supplied by {@link GuildUpgradeGraphLayout}; this screen only
 * paints those coordinates and handles node interaction. Keeping layout and painting separate
 * means new nodes can be added without introducing another grid, lane, or scrolling model.</p>
 */
public final class GuildUpgradeScreen extends Screen {

    private static final int NODE_SIDE = 12;
    private static final int HEADER_HEIGHT = 10;
    private static final Color BACKGROUND = new Color(22, 24, 30);
    private static final Color EDGE_DIM = new Color(72, 77, 91);
    private static final Color EDGE_LIT = new Color(205, 210, 224);
    private static final Color LOCKED = new Color(112, 118, 135);
    private static final Color AVAILABLE = new Color(230, 190, 60);
    private static final Color ACTIVE = new Color(255, 150, 40);
    private static final Color MAXED = new Color(80, 190, 90);
    private static final Color TEXT = new Color(238, 240, 245);
    private static final Color MUTED = new Color(150, 158, 175);
    private static final Color TOP_BG = new Color(45, 45, 55);
    private static final Color PULSE = new Color(255, 255, 255);
    private static final Color PULSE_TAIL = new Color(255, 210, 150);
    private static final TextFont FONT = AwtFont.named("Carlito", Font.PLAIN, 8, false);

    @Override
    public TextFont font() {
        return FONT;
    }

    private final GuildService guildService;
    private final ResidentService residentService;
    private final TechTreeService techTreeService;
    private final GuildProjectService projectService;

    private String viewerGuildName;
    private Guild viewerGuild;
    private List<TechTreeNode> allNodes;
    private Map<String, GuildUpgradeGraphLayout.LayoutNode> layoutNodes;
    private List<GuildUpgradeGraphLayout.SplineEdge> edges;
    private String activeProjectId;
    private String statusMessage = "";
    private long statusUntil;

    public GuildUpgradeScreen(JavaPlugin plugin,
                              GuildService guildService,
                              ResidentService residentService,
                              GuildLevelService levelService,
                              TechTreeService techTreeService,
                              GuildProjectService projectService,
                              Player viewer) {
        this.guildService = guildService;
        this.residentService = residentService;
        this.techTreeService = techTreeService;
        this.projectService = projectService;
        refresh(viewer);
    }

    private void refresh(Player viewer) {
        var resident = residentService.getResident(viewer.getUniqueId());
        viewerGuildName = resident.flatMap(r -> Optional.ofNullable(r.getGuild())).orElse(null);
        viewerGuild = viewerGuildName == null ? null : guildService.getGuild(viewerGuildName).orElse(null);
        allNodes = techTreeService.getAllNodes();
        layoutNodes = GuildUpgradeGraphLayout.layout(allNodes);
        edges = GuildUpgradeGraphLayout.edges(allNodes);
        activeProjectId = viewerGuild == null ? null : projectService.getActiveProjectId(viewerGuild).orElse(null);
    }

    private String headerLabel() {
        if (viewerGuild == null) {
            return "VALHALLA • NO GUILD";
        }
        return "VALHALLA • LVL " + viewerGuild.getGuildLevel() + " • " + viewerGuild.getTechPoints() + " TP";
    }

    @Override
    public Component title() {
        return Component.text("Guild Upgrade");
    }

    @Override
    public boolean terrain() {
        return false;
    }

    @Override
    public boolean holdable() {
        return false;
    }

    @Override
    public HandOptions hand() {
        return HandOptions.popup();
    }

    @Override
    public Click activateOn() {
        return Click.BOTH;
    }

    @Override
    public Color background() {
        return BACKGROUND;
    }

    @Override
    public org.bukkit.Sound clickSound() {
        return null;
    }

    @Override
    public int fps() {
        return 20;
    }

    @Override
    protected boolean keepDrawing() {
        return true;
    }

    @Override
    protected Node build() {
        return Draw(this::paintGraph)
                .tracksCursor(true)
                .caption(this::caption)
                .onClick((x, y) -> clickAt(x, y))
                .fill();
    }

    private void paintGraph(PaintContext context) {
        Painter painter = context.painter();
        Rect bounds = context.bounds();
        long now = System.currentTimeMillis();
        painter.fill(bounds, BACKGROUND);
        if (viewerGuild == null) {
            painter.textLine(bounds.x() + 3, bounds.y() + 2, headerLabel(), MUTED, true);
            return;
        }
        if (allNodes == null || allNodes.isEmpty()) {
            painter.textLine(bounds.x() + 3, bounds.y() + 2, headerLabel(), TEXT, true);
            painter.textLine(bounds.x() + 3, bounds.y() + 16, "No upgrade tree configured", MUTED, true);
            return;
        }

        paintEdges(painter, now);
        paintNodes(painter, now);
        paintHeader(painter, bounds);
        paintStatus(painter, bounds);
    }

    private void paintHeader(Painter painter, Rect bounds) {
        painter.fill(new Rect(bounds.x(), bounds.y(), bounds.width(), HEADER_HEIGHT), TOP_BG);
        String label = painter.ellipsize(headerLabel(), Math.max(12, bounds.width() - 6));
        painter.textLine(bounds.x() + 3, bounds.y() + 2, label, TEXT, true);
    }

    private void paintStatus(Painter painter, Rect bounds) {
        if (statusMessage.isEmpty() || System.currentTimeMillis() > statusUntil) {
            statusMessage = "";
            return;
        }
        // Feedback is deliberately text-only; unlike the old screen it does not add a footer bar.
        painter.textLine(bounds.x() + 3, bounds.y() + bounds.height() - 8,
                painter.ellipsize(statusMessage, Math.max(12, bounds.width() - 6)), AVAILABLE, true);
    }

    private void paintEdges(Painter painter, long now) {
        int edgeIndex = 0;
        for (GuildUpgradeGraphLayout.SplineEdge edge : edges) {
            GuildUpgradeGraphLayout.LayoutNode from = layoutNodes.get(edge.fromId());
            GuildUpgradeGraphLayout.LayoutNode to = layoutNodes.get(edge.toId());
            if (from == null || to == null) {
                continue;
            }
            boolean sourceUnlocked = isUnlocked(from);
            drawSteppedSpline(painter, from.x(), from.y(), to.x(), to.y(),
                    sourceUnlocked ? EDGE_LIT : EDGE_DIM);
            if (sourceUnlocked && !isUnlocked(to)) {
                drawEnergySpark(painter, from, to, now, edgeIndex++);
            }
        }
    }

    private void paintNodes(Painter painter, long now) {
        for (GuildUpgradeGraphLayout.LayoutNode node : layoutNodes.values()) {
            int x = node.x() - NODE_SIDE / 2;
            int y = node.y() - NODE_SIDE / 2;
            Color color = colorFor(node);
            if (node.id().equals(activeProjectId)) {
                drawPixelOutline(painter, node.x(), node.y(), 8, ACTIVE);
                if ((now / 280) % 2 == 0) {
                    drawPixelOutline(painter, node.x(), node.y(), 9, ACTIVE);
                }
            }
            drawPixelShape(painter, node.x(), node.y(), node.shape(), color);
            if (cursorIn(x, y)) {
                drawPixelOutline(painter, node.x(), node.y(), 8, Color.WHITE);
            }
        }
    }

    /** Dispatches a branch shape to a hard-edged one-bit pixel renderer. */
    private void drawPixelShape(Painter painter, int centerX, int centerY,
                                GuildUpgradeGraphLayout.ShapeType shape, Color color) {
        switch (shape) {
            case CORE -> drawPixelCore(painter, centerX, centerY, color);
            case HEXAGON -> drawPixelHex(painter, centerX, centerY, color);
            case SHIELD -> drawPixelShield(painter, centerX, centerY, color);
            case COIN -> drawPixelCoin(painter, centerX, centerY, color);
            case DIAMOND -> drawPixelDiamond(painter, centerX, centerY, color);
        }
    }

    private void drawPixelCore(Painter painter, int centerX, int centerY, Color color) {
        drawPattern(painter, centerX, centerY, color, new String[]{
                "001000100", "001111100", "011111110", "111111111", "011111110",
                "001111100", "001111100", "011111110", "001000100"
        });
    }

    private void drawPixelHex(Painter painter, int centerX, int centerY, Color color) {
        drawPattern(painter, centerX, centerY, color, new String[]{
                "001111100", "011111110", "111000111", "110000011", "110000011",
                "111000111", "011111110", "001111100"
        });
    }

    private void drawPixelShield(Painter painter, int centerX, int centerY, Color color) {
        drawPattern(painter, centerX, centerY, color, new String[]{
                "011111110", "111111111", "111111111", "111111111", "011111110",
                "011111110", "001111100", "001111100", "000110000"
        });
    }

    private void drawPixelCoin(Painter painter, int centerX, int centerY, Color color) {
        drawPattern(painter, centerX, centerY, color, new String[]{
                "001111100", "011111110", "111111111", "111011111", "111111111",
                "111011111", "111111111", "011111110", "001111100"
        });
    }

    private void drawPixelDiamond(Painter painter, int centerX, int centerY, Color color) {
        drawPattern(painter, centerX, centerY, color, new String[]{
                "000010000", "000111000", "001111100", "011111110", "111111111",
                "011111110", "001111100", "000111000", "000010000"
        });
    }

    private void drawPattern(Painter painter, int centerX, int centerY, Color color, String[] pattern) {
        int left = centerX - pattern[0].length() / 2;
        int top = centerY - pattern.length / 2;
        for (int row = 0; row < pattern.length; row++) {
            String pixels = pattern[row];
            for (int column = 0; column < pixels.length(); column++) {
                if (pixels.charAt(column) == '1') {
                    painter.fill(new Rect(left + column, top + row, 1, 1), color);
                }
            }
        }
    }

    private void drawPixelOutline(Painter painter, int centerX, int centerY, int radius, Color color) {
        int left = centerX - radius;
        int right = centerX + radius;
        int top = centerY - radius;
        int bottom = centerY + radius;
        painter.fill(new Rect(left, top, right - left + 1, 1), color);
        painter.fill(new Rect(left, bottom, right - left + 1, 1), color);
        painter.fill(new Rect(left, top, 1, bottom - top + 1), color);
        painter.fill(new Rect(right, top, 1, bottom - top + 1), color);
    }

    /** Rasterizes a cubic Bezier into one-pixel, axis-stepped segments. */
    private void drawSteppedSpline(Painter painter, int x1, int y1, int x2, int y2, Color color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int distance = Math.max(Math.abs(dx), Math.abs(dy));
        int bend = Math.max(4, Math.min(14, distance / 4));
        int control1X = x1 + dx / 3 - Integer.signum(dy) * bend;
        int control1Y = y1 + dy / 3 + Integer.signum(dx) * bend;
        int control2X = x1 + dx * 2 / 3 - Integer.signum(dy) * bend;
        int control2Y = y1 + dy * 2 / 3 + Integer.signum(dx) * bend;
        int previousX = x1;
        int previousY = y1;
        int steps = Math.max(12, distance * 2);
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            int currentX = bezier(x1, control1X, control2X, x2, t);
            int currentY = bezier(y1, control1Y, control2Y, y2, t);
            drawPixelLine(painter, previousX, previousY, currentX, currentY, color);
            previousX = currentX;
            previousY = currentY;
        }
    }

    private static int bezier(int p0, int p1, int p2, int p3, double t) {
        double inverse = 1.0 - t;
        return (int) Math.round(inverse * inverse * inverse * p0
                + 3 * inverse * inverse * t * p1
                + 3 * inverse * t * t * p2
                + t * t * t * p3);
    }

    private void drawPixelLine(Painter painter, int x1, int y1, int x2, int y2, Color color) {
        int dx = Math.abs(x2 - x1);
        int sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1);
        int sy = y1 < y2 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            painter.fill(new Rect(x1, y1, 1, 1), color);
            if (x1 == x2 && y1 == y2) {
                return;
            }
            int twiceError = 2 * error;
            if (twiceError >= dy) {
                error += dy;
                x1 += sx;
            }
            if (twiceError <= dx) {
                error += dx;
                y1 += sy;
            }
        }
    }

    private void drawEnergySpark(Painter painter,
                                 GuildUpgradeGraphLayout.LayoutNode from,
                                 GuildUpgradeGraphLayout.LayoutNode to,
                                 long now, int edgeIndex) {
        double phase = ((now + edgeIndex * 250L) % 1200L) / 1200.0;
        drawSparkAt(painter, from, to, phase, PULSE);
        if (phase >= 0.18) {
            drawSparkAt(painter, from, to, phase - 0.18, PULSE_TAIL);
        }
    }

    private void drawSparkAt(Painter painter,
                             GuildUpgradeGraphLayout.LayoutNode from,
                             GuildUpgradeGraphLayout.LayoutNode to,
                             double phase, Color color) {
        int x = splinePoint(from.x(), from.y(), to.x(), to.y(), phase, true);
        int y = splinePoint(from.x(), from.y(), to.x(), to.y(), phase, false);
        painter.fill(new Rect(x, y, 2, 2), color);
    }

    private int splinePoint(int x1, int y1, int x2, int y2, double phase, boolean horizontal) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int distance = Math.max(Math.abs(dx), Math.abs(dy));
        int bend = Math.max(4, Math.min(14, distance / 4));
        int control1 = horizontal
                ? x1 + dx / 3 - Integer.signum(dy) * bend
                : y1 + dy / 3 + Integer.signum(dx) * bend;
        int control2 = horizontal
                ? x1 + dx * 2 / 3 - Integer.signum(dy) * bend
                : y1 + dy * 2 / 3 + Integer.signum(dx) * bend;
        return bezier(horizontal ? x1 : y1, control1, control2, horizontal ? x2 : y2, phase);
    }

    private Color colorFor(GuildUpgradeGraphLayout.LayoutNode node) {
        if (node.shape() == GuildUpgradeGraphLayout.ShapeType.CORE || isUnlocked(node)) {
            return MAXED;
        }
        if (node.id().equals(activeProjectId)) {
            return ACTIVE;
        }
        return techTreeService.canUnlockNode(viewerGuild, node.id()) ? AVAILABLE : LOCKED;
    }

    private boolean isUnlocked(GuildUpgradeGraphLayout.LayoutNode node) {
        if (node.shape() == GuildUpgradeGraphLayout.ShapeType.CORE) {
            return true;
        }
        try {
            return viewerGuild != null && techTreeService.isTechNodeUnlocked(viewerGuild, node.id());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean cursorIn(int x, int y) {
        return cursorX() >= x && cursorX() < x + NODE_SIDE
                && cursorY() >= y && cursorY() < y + NODE_SIDE;
    }

    private String caption() {
        if (layoutNodes == null) {
            return "";
        }
        GuildUpgradeGraphLayout.LayoutNode node = GuildUpgradeGraphLayout.findNodeAt(
                layoutNodes, cursorX(), cursorY());
        if (node == null || node.rawNode() == null) {
            return "";
        }
        TechTreeNode raw = node.rawNode();
        String name = raw.getName() == null ? raw.getId() : raw.getName();
        String status = isUnlocked(node) ? "[Owned]"
                : node.id().equals(activeProjectId) ? "[Active]"
                : techTreeService.canUnlockNode(viewerGuild, node.id()) ? "[Available]" : "[Locked]";
        return name + " - Cost " + raw.getCost() + " pts " + status;
    }

    private void clickAt(int x, int y) {
        if (viewerGuild == null || layoutNodes == null) {
            return;
        }
        GuildUpgradeGraphLayout.LayoutNode node = GuildUpgradeGraphLayout.findNodeAt(layoutNodes, x, y);
        if (node != null && node.rawNode() != null) {
            handleNodeClick(node.rawNode());
        }
    }

    private void handleNodeClick(TechTreeNode node) {
        if (player() == null || viewerGuild == null) {
            return;
        }
        session().push(new GuildUpgradeNodeScreen(
                guildService, techTreeService, projectService, this, viewerGuild, node));
    }

    private void setStatus(String message) {
        statusMessage = message;
        statusUntil = System.currentTimeMillis() + 4000;
        invalidate();
    }

    void refreshOnReturn(Player player) {
        refresh(player);
    }

    void setStatusOnReturn(String message) {
        setStatus(message);
    }

    @Override
    protected boolean clickedAnywhere(int x, int y, Click click) {
        return false;
    }
}
