package org.aincraft.guilds.gui;

import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.Click;
import de.flog99.mapgui.Screen;
import de.flog99.mapgui.ui.Align;
import de.flog99.mapgui.ui.AwtFont;
import de.flog99.mapgui.ui.Colors;
import de.flog99.mapgui.ui.Justify;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.PaintContext;
import de.flog99.mapgui.ui.Painter;
import de.flog99.mapgui.ui.Rect;
import de.flog99.mapgui.ui.Ui;
import net.kyori.adventure.text.Component;
import org.aincraft.guilds.map.ClaimLayer;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.awt.Font;

public final class GuildClaimScreen extends Screen {

    public static final int DEFAULT_RADIUS = 5;
    public static final int COMPACT_RADIUS = 3;

    private static final Color WILDERNESS = new Color(34, 90, 34);
    private static final Color OWN_GUILD = new Color(46, 184, 64);
    private static final Color OTHER_GUILD = new Color(212, 168, 40);
    private static final Color CENTER = new Color(230, 255, 230);
    private static final double TINT = 0.55;

    private static final AwtFont COMPASS_FONT = AwtFont.named("SansSerif", Font.BOLD, 8, false);
    private static final String[] COMPASS_DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private static final AwtFont CARDINAL_FONT = AwtFont.named("SansSerif", Font.BOLD, 6, false);
    private static final String[] CARDINALS = {"N", "E", "S", "W"};

    private static final int COMPASS_RADIUS = 19;
    private static final int COMPASS_DIAL = 2 * COMPASS_RADIUS + 2;
    private static final int LABEL_GAP = 2;
    private static final int COMPASS_W = COMPASS_DIAL;
    private static final int COMPASS_H = COMPASS_DIAL + LABEL_GAP + COMPASS_FONT.lineHeight();

    // Tuned against an offscreen render: the cardinal letters need a ~9px radial
    // band, so the star has to stop short of CARDINAL_RADIUS and the needle has to
    // stay inside the star or it hides the north spike when facing south.
    private static final int ROSE_MAJOR = 12;
    private static final int ROSE_MINOR = 7;
    private static final int ROSE_WAIST = 4;
    private static final int CARDINAL_RADIUS = 16;
    private static final int NEEDLE_REACH = 9;
    private static final int NEEDLE_WAIST = 3;

    private static final Color CARDINAL_LABEL = new Color(32, 58, 118);
    private static final Color ROSE_NORTH = new Color(245, 246, 250);
    private static final Color ROSE_LIGHT = new Color(196, 200, 212);
    private static final Color ROSE_DARK = new Color(92, 98, 112);
    private static final Color COMPASS_NEEDLE = new Color(228, 62, 62);
    private static final Color COMPASS_TAIL = new Color(24, 26, 32);

    private final String viewerGuild;
    private final GuildService guilds;
    private final PlotService plots;
    private final PermissionService permissions;
    private final int radius;

    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private String lastWorld = "";

    private ClaimLayer cachedLayer;
    private int cachedCenterX = Integer.MIN_VALUE;
    private int cachedCenterZ = Integer.MIN_VALUE;
    private String cachedWorld;

    private int anchorX = -1;
    private int anchorZ = -1;
    private int currentX = -1;
    private int currentZ = -1;
    private boolean dragging;
    private boolean confirmOpen;
    private String resultFlash = "";
    private boolean suppressHold;

    public GuildClaimScreen(String viewerGuild, GuildService guilds,
                            PlotService plots, PermissionService permissions) {
        this(viewerGuild, guilds, plots, permissions, DEFAULT_RADIUS);
    }

    public GuildClaimScreen(String viewerGuild, GuildService guilds,
                            PlotService plots, PermissionService permissions,
                            int radius) {
        this.viewerGuild = viewerGuild;
        this.guilds = guilds;
        this.plots = plots;
        this.permissions = permissions;
        this.radius = radius;
    }

    @Override
    public Component title() {
        return Component.text("Guilds Map");
    }

    @Override
    public boolean terrain() {
        return true;
    }

    @Override
    public boolean holdable() {
        return true;
    }

    /**
     * Keeps MapGUI's pitch clamp off. With it on the session pins the cursor to the
     * middle row and forces the player's pitch, which would stop a drag selection
     * from ever reaching another row of the grid.
     */
    @Override
    public Boolean clampPitch() {
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
    protected void onHold(int x, int y) {
        if (x < 0 || y < 0 || confirmOpen || suppressHold) {
            suppressHold = false;
            return;
        }
        int[] cell = cellAtCursor(x, y);
        if (cell == null) {
            return;
        }
        if (!dragging) {
            anchorX = cell[0];
            anchorZ = cell[1];
            resultFlash = "";
        }
        currentX = cell[0];
        currentZ = cell[1];
        dragging = true;
        invalidate();
    }

    @Override
    protected void onHoldEnd() {
        suppressHold = false;
        if (!dragging) {
            return;
        }
        dragging = false;
        if (anchorX == currentX && anchorZ == currentZ) {
            confirmOpen = false;
            resultFlash = "";
            invalidate();
            return;
        }
        if (!permissions.canClaimForGuild(player().getUniqueId(), viewerGuild)) {
            resultFlash = "You need mayor/assistant permission to claim.";
            confirmOpen = false;
            invalidate();
            return;
        }
        confirmOpen = true;
        resultFlash = "";
        invalidate();
    }
    @Override
    public int fps() {
        return 20;
    }

    @Override
    public int loopFps() {
        return 20;
    }

    @Override
    protected boolean keepDrawing() {
        return true;
    }


    @Override
    protected Node build() {
        return Ui.Overlay(
                Ui.Draw(this::paintLayer)
                        .tracksCursor(true)
                        .caption(this::hoveredCaption)
                        .fill(),
                marqueeOverlay(),
                resultOverlay(),
                legend(),
                compassOverlay()
        ).fill();
    }
    private Node compassOverlay() {
        return Ui.Row(
                Ui.Spacer(),
                Ui.Column(
                        Ui.Draw(this::paintCompass).size(COMPASS_W, COMPASS_H),
                        Ui.Spacer()
                ).align(Align.END)
        ).align(Align.START).padding(4).fill();
    }

    private void paintCompass(PaintContext context) {
        Player player = player();
        if (player == null) {
            return;
        }

        float yaw = player.getLocation().getYaw();
        Rect bounds = context.bounds();
        Painter painter = context.painter();
        int cx = bounds.x() + bounds.width() / 2;
        int cy = bounds.y() + COMPASS_DIAL / 2;

        // Eight-point rose, each point one solid kite. Splitting a point into lit and
        // shaded halves leaves background seams at this size, so shade whole points
        // instead. Intercardinals go down first for the longer cardinals to overlap.
        for (int pass = 0; pass < 2; pass++) {
            for (int point = 0; point < 8; point++) {
                boolean cardinal = point % 2 == 0;
                if (cardinal != (pass == 1)) {
                    continue;
                }
                int reach = cardinal ? ROSE_MAJOR : ROSE_MINOR;
                double axis = Math.toRadians(point * 45);
                double left = Math.toRadians((point + 7) * 45);
                double right = Math.toRadians((point + 1) * 45);
                painter.polygon(point == 0 ? ROSE_NORTH : cardinal ? ROSE_LIGHT : ROSE_DARK,
                        new int[] {cx, polarX(cx, ROSE_WAIST, left), polarX(cx, reach, axis),
                                polarX(cx, ROSE_WAIST, right)},
                        new int[] {cy, polarY(cy, ROSE_WAIST, left), polarY(cy, reach, axis),
                                polarY(cy, ROSE_WAIST, right)});
            }
        }

        // N/E/S/W sit just outside the rose tips. textLine takes the glyph box top,
        // so back off half a line height to centre each letter.
        painter.font(CARDINAL_FONT);
        int cardinalHalf = CARDINAL_FONT.lineHeight() / 2;
        for (int i = 0; i < CARDINALS.length; i++) {
            String letter = CARDINALS[i];
            double bearing = Math.toRadians(i * 90);
            painter.textLine(polarX(cx, CARDINAL_RADIUS, bearing) - CARDINAL_FONT.widthOf(letter) / 2,
                    polarY(cy, CARDINAL_RADIUS, bearing) - cardinalHalf,
                    letter, CARDINAL_LABEL, false);
        }

        // Solid heading needle over the rose: red toward facing, black behind.
        double heading = Math.toRadians(yaw + 180.0);
        double across = heading + Math.PI / 2;
        int[] waistX = {polarX(cx, NEEDLE_WAIST, across), polarX(cx, NEEDLE_WAIST, across + Math.PI)};
        int[] waistY = {polarY(cy, NEEDLE_WAIST, across), polarY(cy, NEEDLE_WAIST, across + Math.PI)};
        painter.polygon(COMPASS_TAIL,
                new int[] {polarX(cx, NEEDLE_REACH, heading + Math.PI), waistX[0], waistX[1], cx},
                new int[] {polarY(cy, NEEDLE_REACH, heading + Math.PI), waistY[0], waistY[1], cy});
        painter.polygon(COMPASS_NEEDLE,
                new int[] {polarX(cx, NEEDLE_REACH, heading), waistX[0], waistX[1], cx},
                new int[] {polarY(cy, NEEDLE_REACH, heading), waistY[0], waistY[1], cy});

        // textLine takes the glyph top, not a baseline, so the label sits flush
        // under the dial rather than hanging off the bottom of the widget.
        painter.font(COMPASS_FONT);
        String label = facingLabel(yaw);
        int labelX = bounds.x() + (bounds.width() - painter.font().widthOf(label)) / 2;
        painter.textLine(labelX, bounds.y() + COMPASS_DIAL + LABEL_GAP, label, Color.WHITE, true);
    }

    /** Screen X for a point {@code radius} out along a compass bearing (clockwise from north). */
    private static int polarX(int cx, int radius, double bearing) {
        return (int) Math.round(cx + radius * Math.sin(bearing));
    }

    /** Screen Y for a point {@code radius} out along a compass bearing (clockwise from north). */
    private static int polarY(int cy, int radius, double bearing) {
        return (int) Math.round(cy - radius * Math.cos(bearing));
    }

    static String facingLabel(float yaw) {
        int facing = Math.floorMod(Math.round(yaw + 180f), 360);
        int index = (int) ((facing + 22.5) / 45) % 8;
        return COMPASS_DIRS[index];
    }

    ClaimLayer currentLayer() {
        int centerChunkX;
        int centerChunkZ;

        if (lastChunkX == Integer.MIN_VALUE) {
            var loc = player().getLocation();
            centerChunkX = loc.getChunk().getX();
            centerChunkZ = loc.getChunk().getZ();
        } else {
            centerChunkX = lastChunkX;
            centerChunkZ = lastChunkZ;
        }

        String world = displayWorld();
        if (cachedLayer == null
                || cachedCenterX != centerChunkX
                || cachedCenterZ != centerChunkZ
                || !world.equals(cachedWorld)) {
            cachedLayer = ClaimLayer.classify(
                    centerChunkX, centerChunkZ, world,
                    viewerGuild, radius,
                    plots::getGuildBlock,
                    guilds::getGuildById);
            cachedCenterX = centerChunkX;
            cachedCenterZ = centerChunkZ;
            cachedWorld = world;
        }

        return cachedLayer;
    }

    String displayWorld() {
        return lastWorld != null && !lastWorld.isEmpty()
                ? lastWorld
                : player().getWorld().getName();
    }

    public void setFixedCenter(int chunkX, int chunkZ, String world) {
        this.lastChunkX = chunkX;
        this.lastChunkZ = chunkZ;
        this.lastWorld = world;
        clearLayerCache();
    }

    private void clearLayerCache() {
        cachedLayer = null;
        cachedCenterX = Integer.MIN_VALUE;
        cachedCenterZ = Integer.MIN_VALUE;
        cachedWorld = null;
    }

    private void paintLayer(PaintContext context) {
        ClaimLayer layer = currentLayer();
        Rect bounds = context.bounds();
        int cell = Math.max(1, Math.min(bounds.width(), bounds.height()) / layer.size());
        int grid = cell * layer.size();
        int originX = bounds.x() + (bounds.width() - grid) / 2;
        int originY = bounds.y() + (bounds.height() - grid) / 2;
        Painter painter = context.painter();
        for (ClaimLayer.Cell claim : layer.cells()) {
            int col = claim.chunkX() - layer.centerChunkX() + layer.radius();
            int row = claim.chunkZ() - layer.centerChunkZ() + layer.radius();
            Rect rect = new Rect(originX + col * cell, originY + row * cell, cell, cell);
            tint(painter, rect, colorFor(claim.kind()));
            if (claim.kind() == ClaimLayer.Kind.CENTER) {
                painter.rect(rect, null, 1, Color.WHITE, 0);
            }
        }

        if (dragging || confirmOpen) {
            int minX = minX();
            int maxX = maxX();
            int minZ = minZ();
            int maxZ = maxZ();
            Color selectionFill = Colors.alpha(Color.WHITE, 90);
            Color unclaimable = new Color(255, 0, 0, 80);
            int firstCol = -1;
            int firstRow = -1;
            int lastCol = -1;
            int lastRow = -1;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int col = x - layer.centerChunkX() + layer.radius();
                    int row = z - layer.centerChunkZ() + layer.radius();
                    if (col < 0 || row < 0 || col >= layer.size() || row >= layer.size()) {
                        continue;
                    }
                    if (firstCol == -1) {
                        firstCol = col;
                        firstRow = row;
                    }
                    lastCol = col;
                    lastRow = row;

                    Rect r = new Rect(originX + col * cell, originY + row * cell, cell, cell);
                    painter.fill(r, selectionFill);
                    if (plots.getGuildBlock(x, z, world()).isPresent()) {
                        painter.fill(r, unclaimable);
                    }
                }
            }

            if (firstCol != -1) {
                Rect outer = new Rect(
                        originX + firstCol * cell,
                        originY + firstRow * cell,
                        (lastCol - firstCol + 1) * cell,
                        (lastRow - firstRow + 1) * cell);
                painter.rect(outer, null, 1, Color.WHITE, 0);
            }
        }
    }

    private String hoveredCaption() {
        ClaimLayer layer = currentLayer();
        Rect bounds = new Rect(0, 0, width(), height());
        int cell = Math.max(1, Math.min(bounds.width(), bounds.height()) / layer.size());
        int grid = cell * layer.size();
        int originX = (bounds.width() - grid) / 2;
        int originY = (bounds.height() - grid) / 2;
        int col = (cursorX() - originX) / cell;
        int row = (cursorY() - originY) / cell;
        if (col < 0 || row < 0 || col >= layer.size() || row >= layer.size()) {
            return "Guilds map";
        }
        int chunkX = layer.centerChunkX() - layer.radius() + col;
        int chunkZ = layer.centerChunkZ() - layer.radius() + row;
        return layer.cellAt(chunkX, chunkZ)
                .map(claim -> labelFor(claim.kind()) + " [" + claim.chunkX() + ", " + claim.chunkZ() + "]")
                .orElse("Guilds map");
    }

    private int[] cellAtCursor(int x, int y) {
        ClaimLayer layer = currentLayer();
        Rect bounds = new Rect(0, 0, width(), height());
        int cell = Math.max(1, Math.min(bounds.width(), bounds.height()) / layer.size());
        int grid = cell * layer.size();
        int originX = (bounds.width() - grid) / 2;
        int originY = (bounds.height() - grid) / 2;
        int col = (x - originX) / cell;
        int row = (y - originY) / cell;
        if (col < 0 || row < 0 || col >= layer.size() || row >= layer.size()) {
            return null;
        }
        int chunkX = layer.centerChunkX() - layer.radius() + col;
        int chunkZ = layer.centerChunkZ() - layer.radius() + row;
        return new int[] { chunkX, chunkZ };
    }

    private Node marqueeOverlay() {
        if (dragging) {
            return Ui.Spacer();
        }
        if (confirmOpen) {
            return Ui.Column(
                    Ui.Text("Claim " + selectionCount() + " chunks for " + viewerGuild + "?").color(Color.WHITE),
                    Ui.Row(
                            Ui.Button("Confirm").onClick(() -> {
                                ignoreHold();
                                commitClaims();
                            }),
                            Ui.Button("Cancel").onClick(() -> {
                                ignoreHold();
                                confirmOpen = false;
                                resultFlash = "";
                                invalidate();
                            })
                    ).gap(3).align(Align.CENTER)
            ).gap(3).align(Align.CENTER)
                    .padding(4)
                    .background(Colors.alpha(Color.BLACK, 170))
                    .radius(3)
                    .place(Justify.CENTER, Align.CENTER);
        }
        return Ui.Spacer();
    }

    /**
     * Marks the next hold as already consumed by a click. MapGUI delivers a hold
     * alongside the button press, which would otherwise start a fresh drag
     * selection behind the confirmation dialog.
     */
    private void ignoreHold() {
        this.suppressHold = true;
    }

    private Node resultOverlay() {
        if (resultFlash.isBlank()) {
            return Ui.Spacer();
        }
        return Ui.Text(resultFlash)
                .color(Color.WHITE)
                .background(Colors.alpha(Color.BLACK, 170))
                .padding(2)
                .radius(3)
                .place(Justify.CENTER, Align.CENTER);
    }

    private int selectionCount() {
        return (Math.abs(currentX - anchorX) + 1) * (Math.abs(currentZ - anchorZ) + 1);
    }

    private void commitClaims() {
        confirmOpen = false;
        MarqueeClaim.Result result = MarqueeClaim.commit(
                plots, permissions, player().getUniqueId(), viewerGuild, world(),
                minX(), maxX(), minZ(), maxZ());
        if (!result.allowed()) {
            resultFlash = "You need mayor/assistant permission to claim.";
        } else {
            resultFlash = "Claimed " + result.claimed() + ", skipped " + result.skipped()
                    + " (already claimed / no permission / failed).";
        }
        clearLayerCache();
        invalidate();
    }

    private int minX() {
        return Math.min(anchorX, currentX);
    }

    private int maxX() {
        return Math.max(anchorX, currentX);
    }

    private int minZ() {
        return Math.min(anchorZ, currentZ);
    }

    private int maxZ() {
        return Math.max(anchorZ, currentZ);
    }

    private String world() {
        return displayWorld();
    }

    private Node legend() {
        return Ui.Column(
                Ui.Spacer(),
                Ui.Row(
                        swatch(OWN_GUILD, "Your guild"),
                        swatch(OTHER_GUILD, "Other guild"),
                        swatch(WILDERNESS, "Wilderness"),
                        swatch(CENTER, "You")
                ).gap(3).justify(Justify.CENTER)
                        .padding(2)
                        .background(Colors.alpha(Color.BLACK, 170))
                        .radius(3)
        ).align(Align.STRETCH).padding(3).fill();
    }

    private static Node swatch(Color color, String label) {
        return Ui.Row(
                Ui.Box(color).size(7, 7).radius(1),
                Ui.Text(label).color(Color.WHITE)
        ).gap(2).align(Align.CENTER);
    }

    private static void tint(Painter painter, Rect rect, Color color) {
        for (int y = rect.y(); y < rect.y() + rect.height(); y++) {
            for (int x = rect.x(); x < rect.x() + rect.width(); x++) {
                Color under = painter.palette().color(painter.surface().get(x, y));
                painter.pixel(x, y, Colors.mix(under, color, TINT));
            }
        }
    }

    private static Color colorFor(ClaimLayer.Kind kind) {
        return switch (kind) {
            case WILDERNESS -> WILDERNESS;
            case OWN_GUILD -> OWN_GUILD;
            case OTHER_GUILD -> OTHER_GUILD;
            case CENTER -> CENTER;
        };
    }

    private static String labelFor(ClaimLayer.Kind kind) {
        return switch (kind) {
            case WILDERNESS -> "Wilderness";
            case OWN_GUILD -> "Your guild";
            case OTHER_GUILD -> "Other guild";
            case CENTER -> "You";
        };
    }
}
