package dev.mintychochip.guilds.gui;

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
import dev.mintychochip.guilds.map.ClaimLayer;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.PlotService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.awt.Color;

/**
 * MapGUI screen that paints nearby guild claims on a vanilla map-item canvas.
 */
public final class GuildClaimScreen extends Screen {

    /** The radius constant. */
    static final int RADIUS = 5;

    /** The wilderness constant. */
    private static final Color WILDERNESS = new Color(34, 90, 34);
    /** The own guild constant. */
    private static final Color OWN_GUILD = new Color(46, 184, 64);
    /** The other guild constant. */
    private static final Color OTHER_GUILD = new Color(212, 168, 40);
    /** The center constant. */
    private static final Color CENTER = new Color(230, 255, 230);
    /** The tint constant. */
    private static final double TINT = 0.55;

    /** The guilds. */
    private final GuildService guilds;
    /** The plots. */
    private final PlotService plots;
    /** The viewer guild. */
    private final String viewerGuild;

    /**
     * Creates a new guild claim screen instance.
     * @param viewerGuild the viewer guild
     * @param guilds the guilds
     * @param plots the plots
     */
    public GuildClaimScreen(String viewerGuild, GuildService guilds, PlotService plots) {
        this.viewerGuild = viewerGuild;
        this.guilds = guilds;
        this.plots = plots;
    }

    /**
     * Performs the title operation.
     * @return the result
     */
    @Override
    public Component title() {
        return Component.text("Guilds Map");
    }

    /**
     * Performs the terrain operation.
     * @return the result
     */
    @Override
    public boolean terrain() {
        return true;
    }

    /**
     * Performs the hand operation.
     * @return the result
     */
    @Override
    public HandOptions hand() {
        return HandOptions.popup();
    }

    /**
     * Performs the build operation.
     * @return the result
     */
    @Override
    protected Node build() {
        return Ui.Overlay(
                Ui.Draw(this::paintLayer)
                        .tracksCursor(true)
                        .caption(this::hoveredCaption)
                        .fill(),
                legend()
        ).fill();
    }

    /**
     * Performs the current layer operation.
     * @return the result
     */
    ClaimLayer currentLayer() {
        Player viewer = player();
        return ClaimLayer.classify(
                viewer.getLocation().getChunk().getX(),
                viewer.getLocation().getChunk().getZ(),
                viewer.getWorld().getName(),
                viewerGuild,
                RADIUS,
                plots::getGuildBlock,
                guilds::getGuildById);
    }

    /**
     * Performs the paint layer operation.
     * @param context the context
     */
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
    }

    /**
     * Performs the hovered caption operation.
     * @return the result
     */
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

    /**
     * Performs the legend operation.
     * @return the result
     */
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

    /**
     * Performs the swatch operation.
     * @param color the color
     * @param label the label
     * @return the result
     */
    private static Node swatch(Color color, String label) {
        return Ui.Row(
                Ui.Box(color).size(7, 7).radius(1),
                Ui.Text(label).color(Color.WHITE)
        ).gap(2).align(Align.CENTER);
    }

    /**
     * Performs the tint operation.
     * @param painter the painter
     * @param rect the rect
     * @param color the color
     */
    private static void tint(Painter painter, Rect rect, Color color) {
        for (int y = rect.y(); y < rect.y() + rect.height(); y++) {
            for (int x = rect.x(); x < rect.x() + rect.width(); x++) {
                Color under = painter.palette().color(painter.surface().get(x, y));
                painter.pixel(x, y, Colors.mix(under, color, TINT));
            }
        }
    }

    /**
     * Performs the color for operation.
     * @param kind the kind
     * @return the result
     */
    private static Color colorFor(ClaimLayer.Kind kind) {
        return switch (kind) {
            case WILDERNESS -> WILDERNESS;
            case OWN_GUILD -> OWN_GUILD;
            case OTHER_GUILD -> OTHER_GUILD;
            case CENTER -> CENTER;
        };
    }

    /**
     * Performs the label for operation.
     * @param kind the kind
     * @return the result
     */
    private static String labelFor(ClaimLayer.Kind kind) {
        return switch (kind) {
            case WILDERNESS -> "Wilderness";
            case OWN_GUILD -> "Your guild";
            case OTHER_GUILD -> "Other guild";
            case CENTER -> "You";
        };
    }
}
