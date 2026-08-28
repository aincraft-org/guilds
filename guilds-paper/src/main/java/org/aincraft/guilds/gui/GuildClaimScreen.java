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
import de.flog99.mapgui.ui.TextFont;
import net.kyori.adventure.text.Component;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.map.ClaimLayer;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.awt.Font;
import java.util.Objects;
import java.util.Optional;
public final class GuildClaimScreen extends Screen {

    public static final int DEFAULT_RADIUS = 5;
    public static final int COMPACT_RADIUS = 3;

    private static final Color WILDERNESS = new Color(34, 90, 34);
    private static final Color OWN_GUILD = new Color(46, 184, 64);
    private static final Color OTHER_GUILD = new Color(212, 168, 40);
    private static final Color[] GUILD_PALETTE = {
        new Color(59, 130, 246),  // Azure Blue
        new Color(212, 168, 40),  // Royal Gold
        new Color(168, 85, 247),  // Mystic Purple
        new Color(249, 115, 22),  // Ember Orange
        new Color(236, 72, 153),  // Crimson Pink
        new Color(6, 182, 212),   // Sky Cyan
        new Color(234, 179, 8),   // Amber
        new Color(133, 77, 14)    // Bronze Brown
    };
    private static final double TINT = 0.55;
    /** Slightly larger humanist map font for readable legend and feedback text. */
    private static final TextFont FONT = AwtFont.named("Carlito", Font.PLAIN, 9, false);

    private static final String[] COMPASS_DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final AwtFont CARDINAL_FONT = AwtFont.named("SansSerif", Font.BOLD, 7, false);
    private static final String[] CARDINALS = {"N", "E", "S", "W"};

    private static final int COMPASS_W = 32;
    private static final int COMPASS_H = 32;
    private static final int COMPASS_RADIUS = 13;
    private static final int CARDINAL_RADIUS = 9;
    private static final int NEEDLE_REACH = 9;
    private static final int NEEDLE_WAIST = 3;

    private static final Color CASING_FILL = new Color(18, 21, 30);
    private static final Color CASING_BORDER = new Color(74, 82, 102);
    private static final Color CARDINAL_NORTH = new Color(255, 213, 79);
    private static final Color CARDINAL_LABEL = new Color(128, 138, 158);
    private static final Color COMPASS_NEEDLE = new Color(229, 57, 53);
    private static final Color COMPASS_TAIL = new Color(42, 48, 62);
    private static final Color PIVOT_COLOR = new Color(160, 170, 190);
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
    public TextFont font() {
        return FONT;
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
        int cx = bounds.x() + COMPASS_W / 2;
        int cy = bounds.y() + COMPASS_H / 2;

        // Clean circular casing plate (uncluttered dark dial with metallic border)
        painter.circle(cx, cy, COMPASS_RADIUS, CASING_FILL, CASING_BORDER);

        // 4 Cardinal markers at N, E, S, W
        painter.font(CARDINAL_FONT);
        int cardinalHalf = CARDINAL_FONT.lineHeight() / 2;
        for (int i = 0; i < CARDINALS.length; i++) {
            String letter = CARDINALS[i];
            double bearing = Math.toRadians(i * 90);
            Color letterColor = (i == 0) ? CARDINAL_NORTH : CARDINAL_LABEL;
            painter.textLine(polarX(cx, CARDINAL_RADIUS, bearing) - CARDINAL_FONT.widthOf(letter) / 2,
                    polarY(cy, CARDINAL_RADIUS, bearing) - cardinalHalf,
                    letter, letterColor, false);
        }

        // Bold two-tone heading needle: red facing tip, dark steel tail
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

        // Center pivot pin
        painter.circle(cx, cy, 2, PIVOT_COLOR, CASING_FILL);
        painter.pixel(cx, cy, CASING_FILL);
    }

    int compassWidth() {
        return COMPASS_W;
    }

    int compassHeight() {
        return COMPASS_H;
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
        int size = layer.size();
        Painter painter = context.painter();

        // Pass 1: Tint territory fills completely flush to bounds
        for (ClaimLayer.Cell claim : layer.cells()) {
            int col = claim.chunkX() - layer.centerChunkX() + layer.radius();
            int row = claim.chunkZ() - layer.centerChunkZ() + layer.radius();
            Rect rect = cellRect(bounds, size, col, row);
            if (claim.kind() != ClaimLayer.Kind.WILDERNESS) {
                tint(painter, rect, colorForCell(claim));
            }
        }

        // Pass 2: Connected territory borders (samples outside layer to avoid false edge lines)
        for (ClaimLayer.Cell claim : layer.cells()) {
            if (claim.kind() == ClaimLayer.Kind.WILDERNESS) {
                continue;
            }
            int col = claim.chunkX() - layer.centerChunkX() + layer.radius();
            int row = claim.chunkZ() - layer.centerChunkZ() + layer.radius();
            Rect r = cellRect(bounds, size, col, row);
            Color c = colorForCell(claim);

            if (!sameOwnerAt(claim, claim.chunkX(), claim.chunkZ() - 1, layer.world())) {
                painter.line(r.x(), r.y(), r.x() + r.width(), r.y(), c);
            }
            if (!sameOwnerAt(claim, claim.chunkX(), claim.chunkZ() + 1, layer.world())) {
                painter.line(r.x(), r.y() + r.height(), r.x() + r.width(), r.y() + r.height(), c);
            }
            if (!sameOwnerAt(claim, claim.chunkX() - 1, claim.chunkZ(), layer.world())) {
                painter.line(r.x(), r.y(), r.x(), r.y() + r.height(), c);
            }
            if (!sameOwnerAt(claim, claim.chunkX() + 1, claim.chunkZ(), layer.world())) {
                painter.line(r.x() + r.width(), r.y(), r.x() + r.width(), r.y() + r.height(), c);
            }
        }

        // Player position marker (directional chevron at player's current chunk)
        Player player = player();
        if (player != null && player.getWorld().getName().equals(layer.world())) {
            int playerChunkX = player.getLocation().getChunk().getX();
            int playerChunkZ = player.getLocation().getChunk().getZ();
            int pCol = playerChunkX - layer.centerChunkX() + layer.radius();
            int pRow = playerChunkZ - layer.centerChunkZ() + layer.radius();
            if (pCol >= 0 && pRow >= 0 && pCol < size && pRow < size) {
                Rect pr = cellRect(bounds, size, pCol, pRow);
                int px = pr.x() + pr.width() / 2;
                int py = pr.y() + pr.height() / 2;
                double heading = Math.toRadians(player.getLocation().getYaw() + 180.0);
                int reach = Math.max(3, pr.width() / 4);
                int base = Math.max(2, pr.width() / 5);
                int tipX = polarX(px, reach, heading);
                int tipY = polarY(py, reach, heading);
                int leftX = polarX(px, base, heading + 2.3);
                int leftY = polarY(py, base, heading + 2.3);
                int rightX = polarX(px, base, heading - 2.3);
                int rightY = polarY(py, base, heading - 2.3);
                painter.polygon(Color.WHITE,
                        new int[] {tipX, leftX, px, rightX},
                        new int[] {tipY, leftY, py, rightY});
                painter.pixel(px, py, Color.RED);
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
                    Rect r = cellRect(bounds, size, col, row);
                    painter.fill(r, selectionFill);
                    if (plots.getGuildBlock(x, z, world()).isPresent()) {
                        painter.fill(r, unclaimable);
                    }
                }
            }

            if (firstCol != -1) {
                Rect rFirst = cellRect(bounds, size, firstCol, firstRow);
                Rect rLast = cellRect(bounds, size, lastCol, lastRow);
                Rect outer = new Rect(
                        rFirst.x(),
                        rFirst.y(),
                        rLast.x() + rLast.width() - rFirst.x(),
                        rLast.y() + rLast.height() - rFirst.y());
                painter.rect(outer, null, 1, Color.WHITE, 0);
            }
        }
    }

    private String hoveredCaption() {
        ClaimLayer layer = currentLayer();
        int size = layer.size();
        int cx = cursorX();
        int cy = cursorY();
        if (cx < 0 || cy < 0 || cx >= width() || cy >= height() || size <= 0) {
            return "Guilds map";
        }
        int col = Math.min(size - 1, (cx * size) / width());
        int row = Math.min(size - 1, (cy * size) / height());
        int chunkX = layer.centerChunkX() - layer.radius() + col;
        int chunkZ = layer.centerChunkZ() - layer.radius() + row;
        return layer.cellAt(chunkX, chunkZ)
                .map(claim -> {
                    String owner = claim.guildName() != null ? claim.guildName() : labelFor(claim.kind());
                    return owner + " [" + claim.chunkX() + ", " + claim.chunkZ() + "]";
                })
                .orElse("Guilds map");
    }

    private int[] cellAtCursor(int x, int y) {
        ClaimLayer layer = currentLayer();
        int size = layer.size();
        if (x < 0 || y < 0 || x >= width() || y >= height() || size <= 0) {
            return null;
        }
        int col = Math.min(size - 1, (x * size) / width());
        int row = Math.min(size - 1, (y * size) / height());
        int chunkX = layer.centerChunkX() - layer.radius() + col;
        int chunkZ = layer.centerChunkZ() - layer.radius() + row;
        return new int[] {chunkX, chunkZ};
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
                        swatch(OTHER_GUILD, "Other guilds"),
                        swatch(WILDERNESS, "Wilderness"),
                        swatch(Color.WHITE, "You")
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

    private static Color colorForCell(ClaimLayer.Cell cell) {
        if (cell.kind() == ClaimLayer.Kind.OWN_GUILD) {
            return OWN_GUILD;
        }
        if (cell.kind() == ClaimLayer.Kind.OTHER_GUILD) {
            if (cell.guildName() == null || cell.guildName().isBlank()) {
                return OTHER_GUILD;
            }
            int index = Math.floorMod(cell.guildName().hashCode(), GUILD_PALETTE.length);
            return GUILD_PALETTE[index];
        }
        return WILDERNESS;
    }

    static boolean sameOwner(ClaimLayer.Cell cell, Optional<ClaimLayer.Cell> neighbor) {
        if (neighbor.isEmpty()) {
            return false;
        }
        ClaimLayer.Cell other = neighbor.get();
        if (cell.kind() != other.kind()) {
            return false;
        }
        if (cell.kind() == ClaimLayer.Kind.OWN_GUILD) {
            return true;
        }
        return Objects.equals(cell.guildName(), other.guildName());
    }

    static Rect cellRect(Rect bounds, int size, int col, int row) {
        int x0 = bounds.x() + (col * bounds.width()) / size;
        int x1 = bounds.x() + ((col + 1) * bounds.width()) / size;
        int y0 = bounds.y() + (row * bounds.height()) / size;
        int y1 = bounds.y() + ((row + 1) * bounds.height()) / size;
        return new Rect(x0, y0, x1 - x0, y1 - y0);
    }

    private boolean sameOwnerAt(ClaimLayer.Cell cell, int chunkX, int chunkZ, String world) {
        Optional<ClaimLayer.Cell> cached = currentLayer().cellAt(chunkX, chunkZ);
        if (cached.isPresent()) {
            return sameOwner(cell, cached);
        }
        Optional<GuildBlock> block = plots.getGuildBlock(chunkX, chunkZ, world);
        if (block.isEmpty()) {
            return false;
        }
        Optional<Guild> guild = guilds.getGuild(block.get().getGuildId());
        String name = guild.map(Guild::getName).orElse(null);
        if (cell.kind() == ClaimLayer.Kind.OWN_GUILD) {
            return viewerGuild != null && viewerGuild.equals(name);
        }
        return Objects.equals(cell.guildName(), name);
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
