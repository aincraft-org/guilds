package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.GuildQuest;
import org.aincraft.guilds.services.QuestService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

import static com.mojang.brigadier.arguments.StringArgumentType.string;

public class QuestBrigadierCommand {

    private final JavaPlugin plugin;
    private final QuestService questService;
    private final GuildService guildService;
    private final ResidentService residentService;


    public QuestBrigadierCommand(JavaPlugin plugin, QuestService questService, GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.questService = questService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("quest")
                .requires(source -> source.getSender().hasPermission("guilds.quest"))
                .executes(this::handleQuestList)
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("progress")
                        .executes(this::handleProgressDetail))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("refresh")
                        .requires(source -> source.getSender().hasPermission("guilds.quest.admin"))
                        .executes(this::handleRefresh))
                .build();
    }

    private int handleQuestList(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Optional<Resident> residentOpt = residentService.getResident(sender.getName());

        if (residentOpt.isEmpty() || residentOpt.get().getGuild() == null) {
            sender.sendMessage(Component.text("You must be a member of a town to use this command.").color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Resident resident = residentOpt.get();
        String guildId = resident.getGuild();
        List<GuildQuest> activeQuests = questService.getActiveQuests(guildId);

        if (activeQuests.isEmpty()) {
            sender.sendMessage(Component.text("No active quests for your town.").color(NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text("Active Town Quests:").color(NamedTextColor.GREEN));
        for (GuildQuest quest : activeQuests) {
            sender.sendMessage(buildQuestProgressMessage(quest));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleProgressDetail(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Optional<Resident> residentOpt = residentService.getResident(sender.getName());

        if (residentOpt.isEmpty() || residentOpt.get().getGuild() == null) {
            sender.sendMessage(Component.text("You must be a member of a town to use this command.").color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Resident resident = residentOpt.get();
        String guildId = resident.getGuild();
        List<GuildQuest> activeQuests = questService.getActiveQuests(guildId);

        if (activeQuests.isEmpty()) {
            sender.sendMessage(Component.text("No active quests for your town.").color(NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text("Town Quest Progress Details:").color(NamedTextColor.GREEN));
        for (GuildQuest quest : activeQuests) {
            sender.sendMessage(buildDetailedQuestMessage(quest));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleRefresh(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Optional<Resident> residentOpt = residentService.getResident(sender.getName());

        if (residentOpt.isEmpty() || residentOpt.get().getGuild() == null) {
            sender.sendMessage(Component.text("You must be a member of a town to use this command.").color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Resident resident = residentOpt.get();
        String guildId = resident.getGuild();
        questService.generateWeeklyQuests(guildId);

        List<GuildQuest> newQuests = questService.getActiveQuests(guildId);
        sender.sendMessage(Component.text("Refreshed weekly quests!").color(NamedTextColor.GREEN));
        if (!newQuests.isEmpty()) {
            sender.sendMessage(Component.text("New active quests:").color(NamedTextColor.GOLD));
            for (GuildQuest quest : newQuests) {
                sender.sendMessage(buildQuestProgressMessage(quest));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private Component buildQuestProgressMessage(GuildQuest quest) {
        double progress = (double) quest.getCurrentProgress() / quest.getTargetAmount() * 100;
        String progressBar = generateProgressBar(quest.getCurrentProgress(), quest.getTargetAmount());

        return Component.text()
                .append(Component.text(quest.getQuestType().getDisplayName()).color(NamedTextColor.AQUA))
                .append(Component.text(": " + quest.getDescription()).color(NamedTextColor.WHITE))
                .append(Component.text(" (" + String.format("%.1f", progress) + "%)").color(NamedTextColor.YELLOW))
                .append(Component.text("\n" + progressBar).color(NamedTextColor.GRAY))
                .append(Component.text(" Reward: " + quest.getTechPointReward() + " tech points").color(NamedTextColor.GOLD))
                .build();
    }

    private Component buildDetailedQuestMessage(GuildQuest quest) {
        double progress = (double) quest.getCurrentProgress() / quest.getTargetAmount() * 100;
        String progressBar = generateProgressBar(quest.getCurrentProgress(), quest.getTargetAmount());

        return Component.text()
                .append(Component.text("=== " + quest.getQuestType().getDisplayName() + " ===\n").color(NamedTextColor.AQUA))
                .append(Component.text("Description: " + quest.getDescription() + "\n").color(NamedTextColor.WHITE))
                .append(Component.text("Progress: " + quest.getCurrentProgress() + "/" + quest.getTargetAmount() +
                        " (" + String.format("%.1f", progress) + "%)\n").color(NamedTextColor.GREEN))
                .append(Component.text(progressBar + "\n").color(NamedTextColor.GRAY))
                .append(Component.text("Tech Point Reward: " + quest.getTechPointReward()).color(NamedTextColor.GOLD))
                .build();
    }

    private String generateProgressBar(int current, int target) {
        int totalBars = 20;
        int filledBars = (int) ((double) current / target * totalBars);

        StringBuilder progressBar = new StringBuilder("[");
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                progressBar.append("▓");
            } else {
                progressBar.append("░");
            }
        }
        progressBar.append("]");
        return progressBar.toString();
    }
}