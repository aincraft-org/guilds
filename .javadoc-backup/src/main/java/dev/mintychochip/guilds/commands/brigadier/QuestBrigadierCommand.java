package dev.mintychochip.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.models.Resident;
import dev.mintychochip.guilds.models.GuildQuest;
import dev.mintychochip.guilds.services.QuestService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.GuildService;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

import static com.mojang.brigadier.arguments.StringArgumentType.string;

/** Command handler for quest brigadier. */
public class QuestBrigadierCommand {

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The quest service. */
    private final QuestService questService;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;


    /**
     * Creates a new quest brigadier command instance.
     * @param plugin the plugin
     * @param questService the quest service
     * @param guildService the guild service
     * @param residentService the resident service
     */
    public QuestBrigadierCommand(JavaPlugin plugin, QuestService questService, GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.questService = questService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    /**
     * Builds the command.
     * @return the result
     */
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

    /**
     * Handles the quest list.
     * @param context the context
     * @return the result
     */
    private int handleQuestList(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Optional<Resident> residentOpt = residentService.getResident(sender.getName());

        if (residentOpt.isEmpty() || residentOpt.get().getGuild() == null) {
            sender.sendMessage(Component.text("You must be a member of a guild to use this command.").color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Resident resident = residentOpt.get();
        String guildId = resident.getGuild();
        List<GuildQuest> activeQuests = questService.getActiveQuests(guildId);

        if (activeQuests.isEmpty()) {
            sender.sendMessage(Component.text("No active quests for your guild.").color(NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text("Active Guild Quests:").color(NamedTextColor.GREEN));
        for (GuildQuest quest : activeQuests) {
            sender.sendMessage(buildQuestProgressMessage(quest));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the progress detail.
     * @param context the context
     * @return the result
     */
    private int handleProgressDetail(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Optional<Resident> residentOpt = residentService.getResident(sender.getName());

        if (residentOpt.isEmpty() || residentOpt.get().getGuild() == null) {
            sender.sendMessage(Component.text("You must be a member of a guild to use this command.").color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Resident resident = residentOpt.get();
        String guildId = resident.getGuild();
        List<GuildQuest> activeQuests = questService.getActiveQuests(guildId);

        if (activeQuests.isEmpty()) {
            sender.sendMessage(Component.text("No active quests for your guild.").color(NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text("Guild Quest Progress Details:").color(NamedTextColor.GREEN));
        for (GuildQuest quest : activeQuests) {
            sender.sendMessage(buildDetailedQuestMessage(quest));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the refresh.
     * @param context the context
     * @return the result
     */
    private int handleRefresh(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Optional<Resident> residentOpt = residentService.getResident(sender.getName());

        if (residentOpt.isEmpty() || residentOpt.get().getGuild() == null) {
            sender.sendMessage(Component.text("You must be a member of a guild to use this command.").color(NamedTextColor.RED));
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

    /**
     * Builds the quest progress message.
     * @param quest the quest
     * @return the result
     */
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

    /**
     * Builds the detailed quest message.
     * @param quest the quest
     * @return the result
     */
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

    /**
     * Performs the generate progress bar operation.
     * @param current the current
     * @param target the target
     * @return the result
     */
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