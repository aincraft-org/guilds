package org.aincraft.guilds.gui;

import de.flog99.mapgui.Click;
import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.Screen;
import de.flog99.mapgui.ui.Align;
import de.flog99.mapgui.ui.AwtFont;
import de.flog99.mapgui.ui.Justify;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.PaintContext;
import de.flog99.mapgui.ui.Painter;
import de.flog99.mapgui.ui.Rect;
import de.flog99.mapgui.ui.TextFont;
import net.kyori.adventure.text.Component;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.services.GuildLevelService;
import org.aincraft.guilds.services.GuildProjectService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TechTreeService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static de.flog99.mapgui.ui.Ui.Button;
import static de.flog99.mapgui.ui.Ui.Draw;
import static de.flog99.mapgui.ui.Ui.Overlay;
import static de.flog99.mapgui.ui.Ui.Row;
import static de.flog99.mapgui.ui.Ui.Spacer;
import static de.flog99.mapgui.ui.Ui.Text;

/**
 * MapGUI node-web for /g upgrade combining guild level and tech-tree progression.
 *
 * <p>Nodes are grouped into one lane per {@link TechTreeBranch} (see
 * {@link GuildUpgradeGraphLayout}) so the graph reads as four clean columns with a header label
 * each, prerequisite lines are drawn for every prerequisite (not just the first parent),
 * an active project gets its own color and pulsing ring distinct from a merely-available one,
 * and the traveling "energy" pulse only appears on frontier edges (unlocked to
 * not-yet-unlocked) instead of on every edge in the tree.
 */
public final class GuildUpgradeScreen extends Screen {

    static final int CELL = GuildUpgradeGraphLayout.CELL;
    static final int NODE_SIDE = 12;
    private static final Color BACKGROUND = new Color(22, 24, 30);
    private static final Color EDGE_DIM = new Color(90, 94, 104);
    private static final Color EDGE_LIT = new Color(205, 210, 224);
    private static final Color LOCKED = new Color(140, 140, 150);
    private static final Color AVAILABLE = new Color(230, 190, 60);
    private static final Color ACTIVE = new Color(255, 150, 40);
    private static final Color MAXED = new Color(80, 190, 90);
    private static final Color TEXT = new Color(238, 240, 245);
    private static final Color MUTED = new Color(150, 158, 175);
    private static final Color GLYPH = new Color(24, 24, 30);
    private static final Color TOP_BG = new Color(45, 45, 55);
    private static final Color UPGRADE_ENABLED = new Color(46, 184, 64);
    private static final Color UPGRADE_DISABLED = new Color(90, 90, 90);
    private static final Color PULSE = new Color(255, 255, 255);
    private static final Color PULSE_TAIL = new Color(255, 210, 150);
    private static final int EDGE_SCROLL_ZONE = 10;
    private static final int EDGE_SCROLL_SPEED = 2;
    /**
     * Humanist sans (Carlito) map font, 8pt - compact and more readable than generic SansSerif across the whole screen (header, branch labels, node glyphs,
     * footer) instead of the larger default map font, 8pt instead of the 6pt compact pass: readable but still fits the 128px canvas.
     */
    private static final TextFont FONT = AwtFont.named("Carlito", java.awt.Font.PLAIN, 8, false);

    @Override public TextFont font() {
        return FONT;
    }

    private final JavaPlugin plugin;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final GuildLevelService levelService;
    private final TechTreeService techTreeService;
    private final GuildProjectService projectService;

    private String viewerGuildName;
    private Guild viewerGuild;
    private List<TechTreeNode> allNodes;
    private Map<String, GuildUpgradeGraphLayout.Slot> slots;
    private int laneCells;
    private String activeProjectId;
    private int panX;
    private int panY;
    private String statusMessage = "";
    private long statusUntil = 0;

    public GuildUpgradeScreen(JavaPlugin plugin,
                              GuildService guildService,
                              ResidentService residentService,
                              GuildLevelService levelService,
                              TechTreeService techTreeService,
                              GuildProjectService projectService,
                              Player viewer) {
        this.plugin = plugin;
        this.guildService = guildService;
        this.residentService = residentService;
        this.levelService = levelService;
        this.techTreeService = techTreeService;
        this.projectService = projectService;
        refresh(viewer);
    }

    private void refresh(Player viewer) {
        var resident = residentService.getResident(viewer.getUniqueId());
        viewerGuildName = resident.flatMap(r -> Optional.ofNullable(r.getGuild())).orElse(null);
        viewerGuild = viewerGuildName != null ? guildService.getGuild(viewerGuildName).orElse(null) : null;
        allNodes = techTreeService.getAllNodes();
        slots = GuildUpgradeGraphLayout.slots(allNodes);
        laneCells = GuildUpgradeGraphLayout.laneCells(allNodes);
        activeProjectId = viewerGuild != null ? projectService.getActiveProjectId(viewerGuild).orElse(null) : null;
    }

    /**
     * Header label with the guild name/level/tech points, ellipsized so it never
     * overflows the 128px canvas at the 8pt sans-serif font (the right side is
     * consumed by the Upgrade button).
     */
    private String headerLabel() {
        String raw = viewerGuild != null
                ? viewerGuild.getName() + "  Lv " + viewerGuild.getGuildLevel() + "  T " + viewerGuild.getTechPoints()
                : "Not in guild";
        return fitWidth(raw, 75);
    }

    /** Truncate {@code text} with an ellipsis so its rendered width fits {@code maxPx}. */
    private static String fitWidth(String text, int maxPx) {
        int textWidth = FONT.widthOf(text);
        if (textWidth <= maxPx) {
            return text;
        }
        String ellipsis = "...";
        int budget = maxPx - FONT.widthOf(ellipsis);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && FONT.widthOf(out.toString() + text.charAt(i)) <= budget; i++) {
            out.append(text.charAt(i));
        }
        return out + ellipsis;
    }

    @Override public Component title() { return Component.text("Guild Upgrade"); }
    @Override public boolean terrain() { return false; }
    @Override public boolean holdable() { return false; }
    @Override public HandOptions hand() { return HandOptions.popup(); }
    @Override public Click activateOn() { return Click.BOTH; }
    @Override public Color background() { return BACKGROUND; }
    @Override public org.bukkit.Sound clickSound() { return null; }
    @Override public int fps() { return 20; }

    @Override
    protected boolean keepDrawing() {
        return edgeScrollActive();
    }

    @Override
    protected Node build() {
        Node graph = Draw(this::paintGraph)
                .tracksCursor(true)
                .caption(this::caption)
                .onClick((x, y) -> clickAt(x, y))
                .fill();
        Node header = Row(
                Text(headerLabel()).color(TEXT).shadow(),
                Spacer(),
                upgradeButton()
        ).gap(5).padding(5).fillWidth().background(TOP_BG);
        Node footer = Draw(this::paintFooter).height(12).fillWidth().place(Justify.START, Align.END);
        return Overlay(graph, header, footer).fill();
    }

    private Node upgradeButton() {
        boolean canUpgrade = viewerGuild != null && viewerGuild.getGuildLevel() < levelService.getMaxLevel();
        Color fill = canUpgrade ? UPGRADE_ENABLED : UPGRADE_DISABLED;
        return Button("Upgrade").size(38, 14).background(fill).caption(canUpgrade ? "Upgrade guild level" : "Max level or no permission").onClick(() -> {
            Player p = player();
            if (p == null || viewerGuild == null) return;
            var guildOpt = guildService.getGuild(viewerGuild.getName());
            if (guildOpt.isEmpty()) { setStatus("Guild not found"); return; }
            Guild g = guildOpt.get();
            boolean authorized = g.getMayorUuid() != null && g.getMayorUuid().equals(p.getUniqueId());
            if (!authorized && !p.hasPermission("guilds.admin.guild")) { setStatus("Only mayor/admin can upgrade"); return; }
            var result = levelService.performGuildUpgrade(g);
            if (result.isSuccessful()) {
                setStatus("Upgraded to Lv " + result.getNewLevel() + "!");
                refresh(p);
            } else setStatus(result.getMessage());
        });
    }

    private boolean edgeScrollActive() {
        if (viewerGuild == null || slots == null || slots.isEmpty()) {
            return false;
        }
        int dx = edgeDirection(cursorX(), width());
        int dy = edgeDirection(cursorY(), height());
        boolean hasHorizontalOverflow = GuildUpgradeGraphLayout.horizontalOverflow(slots, width());
        boolean hasVerticalOverflow = verticalOverflow();
        return (hasHorizontalOverflow && canPan(panX, dx, horizontalPanMax()))
                || (hasVerticalOverflow && canPan(panY, dy, verticalPanMax()));
    }

    private static boolean canPan(int pan, int direction, int maxPan) {
        return direction != 0
                && GuildUpgradeGraphLayout.clampPan(
                        pan + direction * EDGE_SCROLL_SPEED, maxPan) != pan;
    }

    private void panAtEdges() {
        int dx = edgeDirection(cursorX(), width());
        int dy = edgeDirection(cursorY(), height());
        if (dx != 0 && GuildUpgradeGraphLayout.horizontalOverflow(slots, width())) {
            panX = GuildUpgradeGraphLayout.clampPan(
                    panX + dx * EDGE_SCROLL_SPEED, horizontalPanMax());
        }
        if (dy != 0 && verticalOverflow()) {
            panY = GuildUpgradeGraphLayout.clampPan(
                    panY + dy * EDGE_SCROLL_SPEED, verticalPanMax());
        }
    }

    private static int edgeDirection(int position, int size) {
        if (position < 0 || size <= 0) {
            return 0;
        }
        if (position < EDGE_SCROLL_ZONE) {
            return -1;
        }
        return position >= size - EDGE_SCROLL_ZONE ? 1 : 0;
    }

    private int horizontalPanMax() {
        return GuildUpgradeGraphLayout.horizontalPanMaxPx(slots, width());
    }

    /**
     * Click on the right-edge scrollbar: jump the graph so the clicked position lands at
     * the corresponding point in the content's pan range. Returns true when handled.
     */
    private boolean scrollbarClickAt(int x, int y) {
        if (slots.isEmpty() || !verticalOverflow()) {
            return false;
        }
        if (x < width() - 5 || y < 8 || y > height() - 8) {
            return false;
        }
        int trackHeight = Math.max(4, height() - 16);
        int maxPanY = verticalPanMax();
        double posRatio = (double) (y - 8) / trackHeight;
        panY = (int) Math.round(-maxPanY + posRatio * 2 * maxPanY);
        invalidate();
        return true;
    }

    private void paintGraph(PaintContext ctx) {
        Painter p = ctx.painter();
        long now = System.currentTimeMillis();
        Rect b = ctx.bounds();
        p.fill(b, BACKGROUND);
        if (viewerGuild == null) {
            p.textLine(b.x() + 8, b.y() + 8, "Not in a guild", MUTED, true);
            return;
        }
        if (allNodes == null || allNodes.isEmpty()) {
            p.textLine(b.x() + 8, b.y() + 8, "No upgrade tree configured", MUTED, true);
            return;
        }
        panAtEdges();
        GuildUpgradeGraphLayout.Origin origin = graphOrigin();
        paintColumnHeaders(p, origin);
        paintEdges(p, origin);
        paintNodes(p, origin, now);
        paintFrontierPulses(p, origin, now);
        paintScrollbar(p, b);
    }

    /**
     * Right-edge vertical scrollbar for the graph when its content is taller than the
     * usable canvas (rows x CELL vs height minus reserved margins). The thumb tracks
     * {@link #panY} across the full pan range; clicking the track scrolls proportionally.
     * Purely visual + click-to-scroll on top of edge panning.
     */
    private void paintScrollbar(Painter p, Rect bounds) {
        if (slots.isEmpty() || !verticalOverflow()) {
            return;
        }
        int trackX = bounds.x() + bounds.width() - 3;
        int top = bounds.y() + 8;
        int bottom = bounds.y() + bounds.height() - 8;
        int trackHeight = Math.max(4, bottom - top);
        p.fill(new Rect(trackX, top, 2, trackHeight), new Color(52, 56, 70)); // track

        int maxPanY = verticalPanMax();
        if (maxPanY <= 0) {
            return;
        }
        double ratio = (double) trackHeight / (trackHeight + maxPanY);
        int thumbHeight = Math.max(4, (int) Math.round(trackHeight * ratio));
        double posRatio = (panY + maxPanY) / (2.0 * maxPanY); // panY in [-max, +max]
        int thumbY = top + (int) Math.round((trackHeight - thumbHeight) * posRatio);
        p.fill(new Rect(trackX, thumbY, 2, thumbHeight), MUTED); // thumb
    }

    /** True when the graph's vertical extent exceeds the usable canvas. */
    private boolean verticalOverflow() {
        return GuildUpgradeGraphLayout.verticalOverflow(slots, height());
    }

    /** Full pan travel (symmetric around the centered origin) when the graph overflows. */
    private int verticalPanMax() {
        return GuildUpgradeGraphLayout.verticalPanMaxPx(slots, height());
    }

    private void paintEdges(Painter p, GuildUpgradeGraphLayout.Origin origin) {
        for (GuildUpgradeGraphLayout.Edge edge : GuildUpgradeGraphLayout.edges(allNodes)) {
            TechTreeNode prereq = findNode(edge.fromId());
            TechTreeNode node = findNode(edge.toId());
            if (prereq == null || node == null) continue;
            boolean lit = isUnlocked(prereq);
            p.line(centerX(origin, prereq), centerY(origin, prereq),
                   centerX(origin, node), centerY(origin, node), lit ? EDGE_LIT : EDGE_DIM);
        }
    }

    private void paintNodes(Painter p, GuildUpgradeGraphLayout.Origin origin, long now) {
        for (TechTreeNode node : allNodes) {
            GuildUpgradeGraphLayout.Slot slot = slots.get(node.getId());
            if (slot == null) continue;
            int x = GuildUpgradeGraphLayout.nodeX(origin, slot);
            int y = GuildUpgradeGraphLayout.nodeY(origin, slot);
            Color color = colorFor(node);
            boolean isActive = node.getId().equals(activeProjectId);
            if (isActive) {
                double breathe = 0.5 + 0.5 * Math.sin(now / 260.0);
                int ringRadius = NODE_SIDE / 2 + 2 + (int) Math.round(breathe * 2);
                p.circle(x + NODE_SIDE / 2, y + NODE_SIDE / 2, ringRadius, null, ACTIVE);
            }
            p.circle(x + NODE_SIDE / 2, y + NODE_SIDE / 2, NODE_SIDE / 2, color, color.brighter());
            String glyph = glyphFor(node);
            p.textLine(x + NODE_SIDE / 2 - 2, y + NODE_SIDE / 2 - 3, glyph, GLYPH, false);
            if (cursorIn(x, y)) {
                p.circle(x + NODE_SIDE / 2, y + NODE_SIDE / 2, NODE_SIDE / 2 + 4, null, Color.WHITE);
            }
        }
    }

    /** Traveling pulse only on frontier edges: source already unlocked, target not yet unlocked. */
    private void paintFrontierPulses(Painter p, GuildUpgradeGraphLayout.Origin origin, long now) {
        int edgeIndex = 0;
        for (GuildUpgradeGraphLayout.Edge edge : GuildUpgradeGraphLayout.edges(allNodes)) {
            TechTreeNode prereq = findNode(edge.fromId());
            TechTreeNode node = findNode(edge.toId());
            if (prereq == null || node == null) continue;
            if (isUnlocked(node) || !isUnlocked(prereq)) continue;
            int x1 = centerX(origin, prereq);
            int y1 = centerY(origin, prereq);
            int x2 = centerX(origin, node);
            int y2 = centerY(origin, node);
            double period = 1200.0;
            double phase = ((now + edgeIndex * 250L) % (long) period) / period;
            int px = (int) Math.round(x1 + (x2 - x1) * phase);
            int py = (int) Math.round(y1 + (y2 - y1) * phase);
            p.circle(px, py, 2, PULSE, PULSE);
            double tailPhase = phase - 0.2;
            if (tailPhase >= 0) {
                int tx = (int) Math.round(x1 + (x2 - x1) * tailPhase);
                int ty = (int) Math.round(y1 + (y2 - y1) * tailPhase);
                p.circle(tx, ty, 1, PULSE_TAIL, PULSE_TAIL);
            }
            edgeIndex++;
        }
    }

    private void paintColumnHeaders(Painter p, GuildUpgradeGraphLayout.Origin origin) {
        for (TechTreeBranch branch : TechTreeBranch.values()) {
            boolean present = allNodes.stream().anyMatch(n -> n.getBranch() == branch);
            if (!present) continue;
            String label = abbreviate(branch);
            int laneWidth = laneCells * CELL;
            int laneX = origin.x() + GuildUpgradeGraphLayout.laneStart(branch, laneCells) * CELL;
            int textWidth = p.font().widthOf(label);
            int x = laneX + Math.max(0, (laneWidth - textWidth) / 2);
            p.textLine(x, origin.y() - 9, label, MUTED, false);
        }
    }

    private static String abbreviate(TechTreeBranch branch) {
        String name = branch.getDisplayName();
        return name.length() <= 4 ? name : name.substring(0, 4);
    }

    private static String glyphFor(TechTreeNode node) {
        String name = node.getName() != null && !node.getName().isBlank() ? node.getName() : node.getId();
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private GuildUpgradeGraphLayout.Origin graphOrigin() {
        // Centralized origin - use full canvas (like paintGraph's Draw fill) so hover/click match rendering
        return GuildUpgradeGraphLayout.origin(panX, panY, width(), height(), slots);
    }

    private int centerX(GuildUpgradeGraphLayout.Origin origin, TechTreeNode node) {
        GuildUpgradeGraphLayout.Slot slot = slots.get(node.getId());
        return slot == null ? origin.x() : GuildUpgradeGraphLayout.nodeX(origin, slot) + NODE_SIDE / 2;
    }

    private int centerY(GuildUpgradeGraphLayout.Origin origin, TechTreeNode node) {
        GuildUpgradeGraphLayout.Slot slot = slots.get(node.getId());
        return slot == null ? origin.y() : GuildUpgradeGraphLayout.nodeY(origin, slot) + NODE_SIDE / 2;
    }

    private Color colorFor(TechTreeNode node) {
        if (isUnlocked(node)) {
            return MAXED;
        }
        if (node.getId().equals(activeProjectId)) return ACTIVE;
        if (techTreeService.canUnlockNode(viewerGuild, node.getId())) return AVAILABLE;
        return LOCKED;
    }

    private boolean isUnlocked(TechTreeNode node) {
        try { return viewerGuild != null && techTreeService.isTechNodeUnlocked(viewerGuild, node.getId()); } catch (Exception e) { return false; }
    }

    private TechTreeNode findNode(String id) {
        for (TechTreeNode n : allNodes) if (n.getId().equals(id)) return n;
        return null;
    }

    private boolean cursorIn(int x, int y) {
        return cursorX() >= x && cursorX() < x + NODE_SIDE && cursorY() >= y && cursorY() < y + NODE_SIDE;
    }

    private String caption() {
        GuildUpgradeGraphLayout.Origin origin = graphOrigin();
        for (TechTreeNode node : allNodes) {
            GuildUpgradeGraphLayout.Slot slot = slots.get(node.getId());
            if (slot == null) continue;
            int x = GuildUpgradeGraphLayout.nodeX(origin, slot);
            int y = GuildUpgradeGraphLayout.nodeY(origin, slot);
            if (cursorIn(x, y)) {
                String name = node.getName() != null ? node.getName() : node.getId();
                int cost = node.getCost();
                String status = isUnlocked(node) ? "[Owned]"
                        : node.getId().equals(activeProjectId) ? "[Active]"
                        : techTreeService.canUnlockNode(viewerGuild, node.getId()) ? "[Available]" : "[Locked]";
                return name + " - Cost " + cost + " pts " + status;
            }
        }
        return "Gray=Locked  Yellow=Ready  Orange=Active  Green=Owned  - move cursor to an edge to pan";
    }

    private void clickAt(int x, int y) {
        if (viewerGuild == null || allNodes == null) return;
        if (scrollbarClickAt(x, y)) {
            return;
        }
        GuildUpgradeGraphLayout.Origin origin = graphOrigin();
        for (TechTreeNode node : allNodes) {
            GuildUpgradeGraphLayout.Slot slot = slots.get(node.getId());
            if (slot == null) continue;
            int nx = GuildUpgradeGraphLayout.nodeX(origin, slot);
            int ny = GuildUpgradeGraphLayout.nodeY(origin, slot);
            if (x >= nx && x < nx + NODE_SIDE && y >= ny && y < ny + NODE_SIDE) {
                handleNodeClick(node);
                return;
            }
        }
    }

    private void handleNodeClick(TechTreeNode node) {
        if (player() == null || viewerGuild == null) return;
        session().push(new GuildUpgradeNodeScreen(
                guildService,
                techTreeService,
                projectService,
                this,
                viewerGuild,
                node));
    }

    private void paintFooter(PaintContext ctx) {
        if (System.currentTimeMillis() > statusUntil) statusMessage = "";
        if (statusMessage.isEmpty()) return;
        Rect b = ctx.bounds();
        Painter p = ctx.painter();
        p.fill(b, TOP_BG);
        int maxWidth = Math.max(8, b.width() - 4);
        String shown = p.ellipsize(statusMessage, maxWidth);
        p.textLine(b.x() + 4, b.y() + 2, shown, new Color(255, 220, 100), false);
    }

    private void setStatus(String msg) {
        statusMessage = msg;
        statusUntil = System.currentTimeMillis() + 4000;
        invalidate();
    }
    void refreshOnReturn(Player p) {
        refresh(p);
    }

    void setStatusOnReturn(String msg) {
        setStatus(msg);
    }

    @Override
    protected boolean clickedAnywhere(int x, int y, Click click) {
        // handled via caption/onClick in graph, keep for Upgrade button fallback (already via Button)
        return false;
    }
}
