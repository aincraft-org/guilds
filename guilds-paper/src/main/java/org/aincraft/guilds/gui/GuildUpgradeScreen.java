package org.aincraft.guilds.gui;

import de.flog99.mapgui.Click;
import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.Screen;
import de.flog99.mapgui.ui.Align;
import de.flog99.mapgui.ui.Button;
import de.flog99.mapgui.ui.Justify;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.PaintContext;
import de.flog99.mapgui.ui.Painter;
import de.flog99.mapgui.ui.Rect;
import de.flog99.mapgui.ui.Ui;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static de.flog99.mapgui.ui.Ui.Button;
import static de.flog99.mapgui.ui.Ui.Draw;
import static de.flog99.mapgui.ui.Ui.Overlay;
import static de.flog99.mapgui.ui.Ui.Row;
import static de.flog99.mapgui.ui.Ui.Spacer;
import static de.flog99.mapgui.ui.Ui.Text;

/**
 * MapGUI node-web for /g upgrade — combined level + tech-tree.
 * Mirrors modularjobs' UpgradeTreeScreen (pan, header, graph) for guilds.
 */
public final class GuildUpgradeScreen extends Screen {

    static final int CELL = GuildUpgradeGraphLayout.CELL;
    static final int NODE_SIDE = 12;
    private static final Color BACKGROUND = new Color(22, 24, 30);
    private static final Color EDGE = new Color(120, 126, 140);
    private static final Color LOCKED = new Color(140, 140, 150);
    private static final Color AVAILABLE = new Color(230, 190, 60);
    private static final Color OWNED = new Color(70, 130, 240);
    private static final Color MAXED = new Color(80, 190, 90);
    private static final Color TEXT = new Color(238, 240, 245);
    private static final Color MUTED = new Color(150, 158, 175);
    private static final Color TOP_BG = new Color(45, 45, 55);
    private static final Color UPGRADE_ENABLED = new Color(46, 184, 64);
    private static final Color UPGRADE_DISABLED = new Color(90, 90, 90);

    private final JavaPlugin plugin;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final GuildLevelService levelService;
    private final TechTreeService techTreeService;
    private final GuildProjectService projectService;

    private String viewerGuildName;
    private Guild viewerGuild;
    private List<TechTreeNode> allNodes;
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
        activeProjectId = viewerGuild != null ? projectService.getActiveProjectId(viewerGuild).orElse(null) : null;
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
    protected Node build() {
        Node graph = Draw(this::paintGraph)
                .tracksCursor(true)
                .caption(this::caption)
                .onClick((x, y) -> clickAt(x, y))
                .fill();
        Node header = Row(
                Text(viewerGuild != null ? viewerGuild.getName() + "  Lv " + viewerGuild.getGuildLevel() + "  TechPts: " + viewerGuild.getTechPoints() : "Not in guild").color(TEXT).shadow(),
                Spacer(),
                upgradeButton()
        ).gap(5).padding(5).fillWidth().background(TOP_BG);
        Node toolbar = Row(
                panButton("←", -1, 0), panButton("→", 1, 0),
                panButton("↑", 0, -1), panButton("↓", 0, 1),
                Button("Center").size(28, 12).padding(0).caption("Center graph").onClick(() -> { panX = 0; panY = 0; invalidate(); })
        ).gap(3).padding(3).background(new Color(0,0,0,160)).radius(3).place(Justify.CENTER, Align.END);
        Node footer = Draw(this::paintFooter).height(12).fillWidth();
        return Overlay(graph, header, toolbar, footer).fill();
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

    private Button panButton(String label, int dx, int dy) {
        return Button(label).size(14, 12).padding(0).caption("Pan graph").onClick(() -> {
            panX = GuildUpgradeGraphLayout.clampPan(panX + dx * CELL);
            panY = GuildUpgradeGraphLayout.clampPan(panY + dy * CELL);
            invalidate();
        });
    }

    @Override protected boolean onScroll(int notches) {
        int next = GuildUpgradeGraphLayout.clampPan(panY + notches * CELL);
        if (next == panY) return false;
        panY = next;
        invalidate();
        return true;
    }

    private void paintGraph(PaintContext ctx) {
        Painter p = ctx.painter();
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
        GuildUpgradeGraphLayout.Origin origin = graphOrigin();
        // edges first
        for (TechTreeNode node : allNodes) {
            var prereqs = node.getPrerequisites();
            if (prereqs == null) continue;
            for (String prereq : prereqs) {
                TechTreeNode parent = findNode(prereq);
                if (parent == null) continue;
                p.line(nodeX(origin, parent) + NODE_SIDE/2, nodeY(origin, parent) + NODE_SIDE/2,
                       nodeX(origin, node) + NODE_SIDE/2, nodeY(origin, node) + NODE_SIDE/2, EDGE);
            }
        }
        // nodes
        for (TechTreeNode node : allNodes) {
            int x = nodeX(origin, node);
            int y = nodeY(origin, node);
            Rect cell = new Rect(x, y, NODE_SIDE, NODE_SIDE).intersect(b);
            Color color = colorFor(node);
            int level = levelFor(node);
            p.rect(cell, color, 1, color.brighter(), 1);
            p.textLine(x + 3, y + 3, Integer.toString(Math.max(0, level)), Color.WHITE, true);
            if (cursorIn(x, y)) {
                p.rect(new Rect(x-2, y-2, NODE_SIDE+4, NODE_SIDE+4), null, 1, Color.WHITE, 1);
            }
        }
    }

    private GuildUpgradeGraphLayout.Origin graphOrigin() {
        // Centralized origin — use full canvas (like paintGraph's Draw fill) so hover/click match rendering
        return GuildUpgradeGraphLayout.origin(panX, panY, width(), height(), allNodes);
    }

    private Color colorFor(TechTreeNode node) {
        String id = node.getId();
        if (isUnlocked(node)) {
            return MAXED;
        }
        if (node.getId().equals(activeProjectId)) return AVAILABLE;
        if (techTreeService.canUnlockNode(viewerGuild, id)) return AVAILABLE;
        return LOCKED;
    }

    private int levelFor(TechTreeNode node) {
        return isUnlocked(node) ? 1 : 0;
    }

    private boolean isUnlocked(TechTreeNode node) {
        try { return viewerGuild != null && techTreeService.isTechNodeUnlocked(viewerGuild, node.getId()); } catch (Exception e) { return false; }
    }

    private TechTreeNode findNode(String id) {
        for (TechTreeNode n : allNodes) if (n.getId().equals(id)) return n;
        return null;
    }

    private int nodeX(GuildUpgradeGraphLayout.Origin origin, TechTreeNode node) { return GuildUpgradeGraphLayout.nodeX(origin, node); }
    private int nodeY(GuildUpgradeGraphLayout.Origin origin, TechTreeNode node) { return GuildUpgradeGraphLayout.nodeY(origin, node); }

    private boolean cursorIn(int x, int y) {
        return cursorX() >= x && cursorX() < x + NODE_SIDE && cursorY() >= y && cursorY() < y + NODE_SIDE;
    }

    private String caption() {
        GuildUpgradeGraphLayout.Origin origin = graphOrigin();
        for (TechTreeNode node : allNodes) {
            int x = nodeX(origin, node);
            int y = nodeY(origin, node);
            if (cursorIn(x, y)) {
                String name = node.getName() != null ? node.getName() : node.getId();
                int cost = node.getCost();
                return name + " — Cost " + cost + " pts " + (isUnlocked(node) ? "[Owned]" : techTreeService.canUnlockNode(viewerGuild, node.getId()) ? "[Available]" : "[Locked]");
            }
        }
        return "Click node for details · arrows/scroll pan";
    }

    private void clickAt(int x, int y) {
        if (viewerGuild == null || allNodes == null) return;
        GuildUpgradeGraphLayout.Origin origin = graphOrigin();
        for (TechTreeNode node : allNodes) {
            int nx = nodeX(origin, node);
            int ny = nodeY(origin, node);
            if (x >= nx && x < nx + NODE_SIDE && y >= ny && y < ny + NODE_SIDE) {
                handleNodeClick(node);
                return;
            }
        }
    }
    private void handleNodeClick(TechTreeNode node) {
        Player p = player();
        if (p == null || viewerGuild == null) return;
        var guildOpt = guildService.getGuild(viewerGuild.getName());
        if (guildOpt.isEmpty()) { setStatus("Guild not found"); return; }
        Guild g = guildOpt.get();
        String active = projectService.getActiveProjectId(g).orElse(null);
        if (node.getId().equals(active)) {
            boolean ok = projectService.clearActiveProject(g);
            setStatus(ok ? "Cleared active project" : "Failed to clear");
            refresh(p);
            return;
        }
        if (active != null) { setStatus("Already active: " + active + " — clear first"); return; }
        var result = projectService.startProject(g, node.getId());
        if (result.isSuccessful()) setStatus("Started " + node.getId());
        else setStatus(result.getStatus().name());
        refresh(p);
    }

    private void paintFooter(PaintContext ctx) {
        if (System.currentTimeMillis() > statusUntil) statusMessage = "";
        if (statusMessage.isEmpty()) return;
        Rect b = ctx.bounds();
        ctx.painter().textLine(b.x() + 4, b.y() + 2, statusMessage, new Color(255,220,100), false);
    }

    private void setStatus(String msg) {
        statusMessage = msg;
        statusUntil = System.currentTimeMillis() + 4000;
        invalidate();
    }

    @Override
    protected boolean clickedAnywhere(int x, int y, Click click) {
        // handled via caption/onClick in graph, keep for Upgrade button fallback (already via Button)
        return false;
    }
}
