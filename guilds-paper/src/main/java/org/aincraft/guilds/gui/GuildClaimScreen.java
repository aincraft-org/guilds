package org.aincraft.guilds.gui;

import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.Screen;
import de.flog99.mapgui.ui.Align;
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

import java.awt.Color;

public final class GuildClaimScreen extends Screen {

    static final int RADIUS = 5;
    private static final Color WILDERNESS = new Color(34, 90, 34);
    private static final Color OWN_GUILD = new Color(46, 184, 64);
    private static final Color OTHER_GUILD = new Color(212, 168, 40);
    private static final Color CENTER = new Color(230, 255, 230);
    private static final double TINT = 0.55;

    private final String viewerGuild;
    private final GuildService guilds;
    private final PlotService plots;
    private final PermissionService permissions;

    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private String lastWorld = "";

    private int anchorX = -1;
    private int anchorZ = -1;
    private int currentX = -1;
    private int currentZ = -1;
    private boolean dragging;
    private boolean confirmOpen;
    private String resultFlash = "";

    public GuildClaimScreen(String viewerGuild, GuildService guilds,
                            PlotService plots, PermissionService permissions) {
        this.viewerGuild = viewerGuild;
        this.guilds = guilds;
        this.plots = plots;
        this.permissions = permissions;
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

    @Override
    public Boolean clampPitch() {
        return false;
    }

    @Override
    public HandOptions hand() {
        return HandOptions.pinned(4);
    }

    @Override
    protected void onHold(int x, int y) {
        if (x < 0 || y < 0) {
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
    protected Node build() {
        return Ui.Overlay(
                Ui.Draw(this::paintLayer)
                        .tracksCursor(true)
                        .caption(this::hoveredCaption)
                        .fill(),
                marqueeOverlay(),
                resultOverlay(),
                legend()
        ).fill();
    }

    ClaimLayer currentLayer() {
        var loc = player().getLocation();
        return ClaimLayer.classify(
                loc.getChunk().getX(), loc.getChunk().getZ(), loc.getWorld().getName(),
                viewerGuild, RADIUS,
                plots::getGuildBlock,
                guilds::getGuildById);
    }

    int lastChunkX() {
        return lastChunkX;
    }

    int lastChunkZ() {
        return lastChunkZ;
    }

    String lastWorld() {
        return lastWorld;
    }

    void setFollow(int chunkX, int chunkZ, String world) {
        this.lastChunkX = chunkX;
        this.lastChunkZ = chunkZ;
        this.lastWorld = world;
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
                            Ui.Button("Confirm").onClick(this::commitClaims),
                            Ui.Button("Cancel").onClick(() -> {
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
        return player().getLocation().getWorld().getName();
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
