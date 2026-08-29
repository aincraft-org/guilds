package org.aincraft.guilds.gui;

import de.flog99.mapgui.Click;
import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.Screen;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.TextAlign;
import net.kyori.adventure.text.Component;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.services.GuildProjectService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.TechTreeService;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.flog99.mapgui.ui.Ui.Button;
import static de.flog99.mapgui.ui.Ui.Column;
import static de.flog99.mapgui.ui.Ui.Row;
import static de.flog99.mapgui.ui.Ui.Scroll;
import static de.flog99.mapgui.ui.Ui.Spacer;
import static de.flog99.mapgui.ui.Ui.Text;
import static de.flog99.mapgui.ui.Ui.each;

/**
 * Detail view for a single tech-tree node in the /g upgrade MapGUI.
 * Pushed from {@link GuildUpgradeScreen} when a player clicks a node.
 */
public final class GuildUpgradeNodeScreen extends Screen {

    private static final Color BACKGROUND = new Color(22, 24, 30);
    private static final Color TEXT = new Color(238, 240, 245);
    private static final Color MUTED = new Color(150, 158, 175);
    private static final Color DANGER = new Color(220, 80, 80);
    private static final Color LOCKED = new Color(140, 140, 150);
    private static final Color AVAILABLE = new Color(230, 190, 60);
    private static final Color MAXED = new Color(80, 190, 90);
    private static final Color TOP_BG = new Color(45, 45, 55);
    private static final Color UPGRADE_ENABLED = new Color(46, 184, 64);
    private static final Color UPGRADE_DISABLED = new Color(90, 90, 90);

    private final GuildService guildService;
    private final GuildProjectService projectService;
    private final GuildUpgradeScreen parent;
    private final Guild guild;
    private final TechTreeNode node;
    private final boolean unlocked;
    private final boolean active;
    private final boolean available;
    private final boolean canAfford;
    private final List<PrereqInfo> prereqInfos;
    private final List<String> effectLines;

    private record PrereqInfo(String name, boolean met) {
    }

    public GuildUpgradeNodeScreen(
            GuildService guildService,
            TechTreeService techTreeService,
            GuildProjectService projectService,
            GuildUpgradeScreen parent,
            Guild guild,
            TechTreeNode node) {
        this.guildService = guildService;
        this.projectService = projectService;
        this.parent = parent;
        this.guild = guild;
        this.node = node;

        this.unlocked = techTreeService.isTechNodeUnlocked(guild, node.getId());
        String activeId = projectService.getActiveProjectId(guild).orElse(null);
        this.active = node.getId().equals(activeId);
        this.available = !unlocked && !active && techTreeService.canUnlockNode(guild, node.getId());
        this.canAfford = guild.getTechPoints() >= node.getCost();

        this.prereqInfos = new ArrayList<>();
        if (node.getPrerequisites() != null) {
            for (String prereqId : node.getPrerequisites()) {
                String name = techTreeService.getNode(prereqId)
                        .map(TechTreeNode::getName)
                        .orElse(prereqId);
                boolean met = techTreeService.isTechNodeUnlocked(guild, prereqId);
                prereqInfos.add(new PrereqInfo(name, met));
            }
        }

        this.effectLines = new ArrayList<>();
        if (node.getEffects() != null) {
            for (Map.Entry<String, Object> e : node.getEffects().entrySet()) {
                effectLines.add(formatEffect(e.getKey(), e.getValue()));
            }
        }
    }

    @Override
    public Component title() {
        return Component.text(node.getName() != null ? node.getName() : node.getId());
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

    /**
     * Humanist sans (Carlito) map font, 8pt - compact and more readable than generic SansSerif matching the parent upgrade graph screen, so the perk detail
     * text (description, prerequisites, effects) stays small and readable in the
     * scrollable body instead of the larger default map font.
     */
    @Override
    public de.flog99.mapgui.ui.TextFont font() {
        return de.flog99.mapgui.ui.AwtFont.named("Carlito", java.awt.Font.PLAIN, 8, false);
    }

    @Override
    protected Node build() {
        String status;
        Color statusColor;
        if (unlocked) {
            status = "Owned";
            statusColor = MAXED;
        } else if (active) {
            status = "Active";
            statusColor = AVAILABLE;
        } else if (available) {
            status = "Available";
            statusColor = AVAILABLE;
        } else {
            status = "Locked";
            statusColor = LOCKED;
        }

        String branch = node.getBranch() != null
                ? node.getBranch().getDisplayName()
                : "Unknown";

        Node header = Row(
                Text(node.getName() != null ? node.getName() : node.getId())
                        .color(TEXT)
                        .shadow()
                        .align(TextAlign.LEFT),
                Spacer(),
                Text(status).color(statusColor).shadow()
        ).gap(5).padding(4).fillWidth().background(TOP_BG);

        List<Node> content = new ArrayList<>();
        content.add(Text("Branch: " + branch).color(MUTED).align(TextAlign.LEFT));
        content.add(Text("Cost: " + node.getCost() + " tech points").color(AVAILABLE));
        if (!unlocked && !active) {
            content.add(Text("Tech points: " + guild.getTechPoints() + (canAfford ? "" : " (not enough)"))
                    .color(canAfford ? MUTED : DANGER)
                    .align(TextAlign.LEFT));
        }

        content.add(Text("Description:").color(MUTED).align(TextAlign.LEFT));
        String desc = node.getDescription() != null && !node.getDescription().isBlank()
                ? node.getDescription()
                : "No description";
        content.add(Text(desc).color(TEXT).wrap().align(TextAlign.LEFT));

        content.add(Text("Prerequisites:").color(MUTED).align(TextAlign.LEFT));
        if (prereqInfos.isEmpty()) {
            content.add(Text("None").color(MUTED).align(TextAlign.LEFT));
        } else {
            content.addAll(each(prereqInfos,
                    info -> Text("  - " + info.name + (info.met ? " (met)" : " (missing)"))
                            .color(info.met ? MAXED : DANGER)
                            .align(TextAlign.LEFT)));
        }

        content.add(Text("Effects:").color(MUTED).align(TextAlign.LEFT));
        if (effectLines.isEmpty()) {
            content.add(Text("None").color(MUTED).align(TextAlign.LEFT));
        } else {
            content.addAll(each(effectLines,
                    line -> Text("  - " + line).color(TEXT).align(TextAlign.LEFT)));
        }

        Node body = Scroll(Column(content)
                .gap(2)
                .padding(3)
                .fillWidth())
                .scrollbar(true)
                .scrollbarColors(MUTED, new Color(52, 56, 70))
                .fill();

        Node footer = Row(
                actionButton(),
                Spacer(),
                Button("Back").size(28, 14).background(UPGRADE_DISABLED).textColor(TEXT)
                        .caption("Return to upgrade tree")
                        .onClick(this::goBack)
        ).gap(5).padding(4).fillWidth().background(TOP_BG);

        return Column(header, body, footer).gap(0).fill();
    }

    private Node actionButton() {
        if (unlocked) {
            return Button("Completed").size(58, 14).background(MAXED).textColor(TEXT)
                    .caption("Already unlocked")
                    .onClick(this::goBack);
        }
        if (active) {
            return Button("Clear Active").size(58, 14).background(AVAILABLE).textColor(new Color(30, 30, 30))
                    .caption("Stop working on this project")
                    .onClick(this::clearProject);
        }
        if (available) {
            return Button("Start Project").size(58, 14).background(UPGRADE_ENABLED).textColor(TEXT)
                    .caption("Spend " + node.getCost() + " tech points")
                    .onClick(this::startProject);
        }
        return Button("Locked").size(58, 14).background(UPGRADE_DISABLED).textColor(MUTED)
                .caption("Requirements not met")
                .onClick(() -> {
                });
    }

    private void goBack() {
        session().pop();
    }

    private void startProject() {
        Player p = player();
        if (p == null || guild == null) {
            goBack();
            return;
        }
        Guild current = resolveGuild();
        if (current == null) {
            goBack();
            return;
        }
        var result = projectService.startProject(current, node.getId());
        if (result.isSuccessful()) {
            parent.setStatusOnReturn("Started " + node.getName());
        } else {
            parent.setStatusOnReturn("Cannot start: " + result.getStatus().name());
        }
        parent.refreshOnReturn(p);
        goBack();
    }

    private void clearProject() {
        Player p = player();
        if (p == null || guild == null) {
            goBack();
            return;
        }
        Guild current = resolveGuild();
        if (current == null) {
            goBack();
            return;
        }
        boolean ok = projectService.clearActiveProject(current);
        parent.setStatusOnReturn(ok ? "Cleared active project" : "Failed to clear");
        parent.refreshOnReturn(p);
        goBack();
    }

    private Guild resolveGuild() {
        if (guild == null) {
            return null;
        }
        return guildService.getGuild(guild.getId()).orElse(null);
    }

    private String formatEffect(String key, Object value) {
        String[] parts = key.split("_", -1);
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!name.isEmpty()) {
                name.append(" ");
            }
            if (!part.isEmpty()) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return name + ": " + value;
    }
}
