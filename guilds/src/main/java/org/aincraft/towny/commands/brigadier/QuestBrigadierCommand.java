package org.aincraft.towny.commands.brigadier;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.commands.CommandSender;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.TownQuest;
import org.aincraft.towny.models.TownResident;
import org.aincraft.towny.services.QuestService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;

import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static org.aincraft.towny.commands.CommandHelper.sender;

public class QuestBrigadierCommand implements BrigadierCommand {

    private final TownyPlugin plugin;
    private final QuestService questService;
    private final TownService townService;
    private final ResidentService residentService;

    @Inject
    public QuestBrigadierCommand(TownyPlugin plugin, QuestService questService, TownService townService, ResidentService residentService) {
        this.plugin = plugin;
        this.questService = questService;
        this.townService = townService;
        this.residentService = residentService;
    }

    @Override
    public LiteralCommandNode<CommandSender> buildCommand() {
        return LiteralArgumentBuilder.<CommandSender>literal("quest")
                .requires(source -> sender(source).hasPermission("towny.quest"))
                .executes(this::handleQuestList)
                .then(LiteralArgumentBuilder.<CommandSender>literal("progress")
                        .executes(this::handleProgressDetail))
                .then(LiteralArgumentBuilder.<CommandSender>literal("refresh")
                        .requires(source -> sender(source).hasPermission("towny.quest.admin"))
                        .executes(this::handleRefresh))
                .build();
    }

    private int handleQuestList(CommandContext<CommandSender> context) {
        CommandSender sender = sender(context);
        TownResident resident = residentService.getResident(sender.getName());
        
        if (resident == null || resident.getTown() == null) {
            sender.sendMessage(Component.text("You must be a member of a town to use this command.").color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        String townId = resident.getTown().getName();
        List<TownQuest> activeQuests = questService.getActiveQuests(townId);

        if (activeQuests.isEmpty()) {
            sender.sendMessage(Component.text("No active quests for your town.").color(NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text("Active Town Quests:").color(NamedTextColor.GREEN));
        for (TownQuest quest : activeQuests) {
            sender.sendMessage(buildQuestProgressMessage(quest));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleProgressDetail(CommandContext<CommandSender> context) {
        CommandSender sender = sender(context);
        TownResident resident = residentService.getResident(sender.getName());
        
        if (resident == null || resident.getTown() == null) {
            sender.sendMessage(Component.text("You must be a member of a town to use this command.").color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        String townId = resident.getTown().getName();
        List<TownQuest> activeQuests = questService.getActiveQuests(townId);

        if (activeQuests.isEmpty()) {
            sender.sendMessage(Component.text("No active quests for your town.").color(NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text("Town Quest Progress Details:").color(NamedTextColor.GREEN));
        for (TownQuest quest : activeQuests) {
            sender.sendMessage(buildDetailedQuestMessage(quest));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleRefresh(CommandContext<CommandSender> context) {
        CommandSender sender = sender(context);
        TownResident resident = residentService.getResident(sender.getName());
        
        if (resident == null || resident.getTown() == null) {
            sender.sendMessage(Component.text("You must be a member of a town to use this command.").color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        String townId = resident.getTown().getName();
        questService.generateWeeklyQuests(townId);
        
        List<TownQuest> newQuests = questService.getActiveQuests(townId);
        sender.sendMessage(Component.text("Refreshed weekly quests!").color(NamedTextColor.GREEN));
        if (!newQuests.isEmpty()) {
            sender.sendMessage(Component.text("New active quests:").color(NamedTextColor.GOLD));
            for (TownQuest quest : newQuests) {
                sender.sendMessage(buildQuestProgressMessage(quest));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private Component buildQuestProgressMessage(TownQuest quest) {
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

    private Component buildDetailedQuestMessage(TownQuest quest) {
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