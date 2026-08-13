package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.GuildsGovernanceSource;
import org.aincraft.guilds.commands.arguments.GovernmentFormArgumentType;
import org.aincraft.guilds.models.Alliance;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Brigadier command for alliance system management.
 * /alliance create <name> — create a alliance (mayor of a guild)
 * /alliance invite <guild> — invite a guild (king/minister only)
 * /alliance join <alliance> — accept invite (mayor only)
 * /alliance leave — leave alliance
 * /alliance list — list all alliances
 * /alliance info [alliance] — show alliance details
 * /alliance ally <alliance> — add ally (king/minister only)
 * /alliance enemy <alliance> — add enemy (king/minister only)
 * /alliance kick <guild> — kick a guild (king/minister only)
 * /alliance set king <player> — transfer kingship
 * /alliance set tax <rate> — set tax rate
 * /alliance set open <true|false> — toggle open/closed
 * /alliance minister add <player> — promote to minister
 * /alliance minister remove <player> — demote minister
 */
public class AllianceBrigadierCommand {

    private final JavaPlugin plugin;
    private final AllianceService allianceService;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final GuildsGovernanceSource governanceSource;


    public AllianceBrigadierCommand(JavaPlugin plugin, AllianceService allianceService,
                                  GuildService guildService, ResidentService residentService,
                                  GuildsGovernanceSource governanceSource) {
        this.plugin = plugin;
        this.allianceService = allianceService;
        this.guildService = guildService;
        this.residentService = residentService;
        this.governanceSource = governanceSource;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("alliance")
                .requires(source -> source.getSender().hasPermission("guilds.commands.alliance"))
                .executes(this::handleInfoSelf)
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(this::handleCreate)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("guild", StringArgumentType.string())
                                .executes(this::handleInvite)))
                .then(Commands.literal("join")
                        .then(Commands.argument("alliance", StringArgumentType.string())
                                .executes(this::handleJoin)))
                .then(Commands.literal("leave")
                        .executes(this::handleLeave))
                .then(Commands.literal("list")
                        .executes(this::handleList))
                .then(Commands.literal("info")
                        .executes(this::handleInfoSelf)
                        .then(Commands.argument("alliance", StringArgumentType.string())
                                .executes(this::handleInfoSpecific)))
                .then(Commands.literal("ally")
                        .then(Commands.argument("alliance", StringArgumentType.string())
                                .executes(this::handleAlly)))
                .then(Commands.literal("enemy")
                        .then(Commands.argument("alliance", StringArgumentType.string())
                                .executes(this::handleEnemy)))
                .then(Commands.literal("kick")
                        .then(Commands.argument("guild", StringArgumentType.string())
                                .executes(this::handleKick)))
                .then(Commands.literal("set")
                        .then(Commands.literal("king")
                                .then(Commands.argument("player", StringArgumentType.string())
                                        .executes(this::handleSetKing)))
                        .then(Commands.literal("tax")
                                .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .executes(this::handleSetTax)))
                        .then(Commands.literal("open")
                                .then(Commands.argument("open", BoolArgumentType.bool())
                                        .executes(this::handleSetOpen))))
                .then(Commands.literal("minister")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.string())
                                        .executes(this::handleMinisterAdd)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.string())
                                        .executes(this::handleMinisterRemove))))
                .then(Commands.literal("government")
                        .then(Commands.argument("form", GovernmentFormArgumentType.form())
                                .executes(this::handleGovernment)))
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Player getPlayer(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return null;
        }
        return player;
    }

    private Optional<Guild> getPlayerGuild(Player player) {
        return residentService.getResident(player.getUniqueId())
                .filter(Resident::hasGuild)
                .flatMap(r -> guildService.getGuild(r.getGuild()));
    }

    private Optional<Alliance> getPlayerAlliance(Player player) {
        return getPlayerGuild(player)
                .flatMap(guild -> allianceService.getAllAlliances().stream()
                        .filter(n -> n.hasGuild(guild.getId()))
                        .findFirst());
    }

    private boolean hasAllianceAuthority(Player player, Alliance alliance) {
        return alliance.isKing(player.getUniqueId()) || alliance.isMinister(player.getUniqueId());
    }

    private boolean isMayorOfGuild(Player player, String guildName) {
        return residentService.getResident(player.getUniqueId())
                .filter(r -> r.hasGuild() && r.getGuild().equalsIgnoreCase(guildName))
                .flatMap(r -> guildService.getGuild(r.getGuild()))
                .map(t -> t.getMayorUuid() != null && t.getMayorUuid().equals(player.getUniqueId()))
                .orElse(false);
    }

    // ── Command Handlers ───────────────────────────────────────────────

    private int handleCreate(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");

        if (allianceService.getAlliance(name).isPresent()) {
            player.sendMessage(Component.text("A alliance with that name already exists!", NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> guildOpt = getPlayerGuild(player);
        if (guildOpt.isEmpty()) {
            player.sendMessage(Component.text("You must be in a guild to create a alliance!", NamedTextColor.RED));
            return 0;
        }

        Guild guild = guildOpt.get();
        if (!guild.getMayorUuid().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Only guild mayors can create alliances!", NamedTextColor.RED));
            return 0;
        }

        try {
            allianceService.createAlliance(name, guild, player.getUniqueId());
            player.sendMessage(Component.text("Alliance " + name + " created! Your guild is now the capital.", NamedTextColor.GREEN));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleInvite(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String guildName = StringArgumentType.getString(ctx, "guild");
        Optional<Alliance> allianceOpt = getPlayerAlliance(player);

        if (allianceOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a alliance!", NamedTextColor.RED));
            return 0;
        }

        if (!hasAllianceAuthority(player, allianceOpt.get())) {
            player.sendMessage(Component.text("Only the king or ministers can invite guilds!", NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> targetGuild = guildService.getGuild(guildName);
        if (targetGuild.isEmpty()) {
            player.sendMessage(Component.text("Guild not found: " + guildName, NamedTextColor.RED));
            return 0;
        }

        // Check if guild is already in a alliance
        boolean alreadyInAlliance = allianceService.getAllAlliances().stream()
                .anyMatch(n -> n.hasGuild(targetGuild.get().getId()));
        if (alreadyInAlliance) {
            player.sendMessage(Component.text("That guild is already in a alliance!", NamedTextColor.RED));
            return 0;
        }

        player.sendMessage(Component.text("Invitation sent to " + guildName + "!", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int handleJoin(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String allianceName = StringArgumentType.getString(ctx, "alliance");
        Optional<Alliance> allianceOpt = allianceService.getAlliance(allianceName);

        if (allianceOpt.isEmpty()) {
            player.sendMessage(Component.text("Alliance not found: " + allianceName, NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> guildOpt = getPlayerGuild(player);
        if (guildOpt.isEmpty()) {
            player.sendMessage(Component.text("You must be in a guild to join a alliance!", NamedTextColor.RED));
            return 0;
        }

        if (!guildOpt.get().getMayorUuid().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Only guild mayors can join alliances!", NamedTextColor.RED));
            return 0;
        }

        if (!allianceOpt.get().isOpen()) {
            player.sendMessage(Component.text("That alliance is not accepting new guilds!", NamedTextColor.RED));
            return 0;
        }

        allianceService.addGuild(allianceOpt.get(), guildOpt.get().getId());
        player.sendMessage(Component.text("Your guild has joined " + allianceName + "!", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int handleLeave(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        Optional<Guild> guildOpt = getPlayerGuild(player);
        if (guildOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a guild!", NamedTextColor.RED));
            return 0;
        }

        Optional<Alliance> allianceOpt = getPlayerAlliance(player);
        if (allianceOpt.isEmpty()) {
            player.sendMessage(Component.text("Your guild is not in a alliance!", NamedTextColor.RED));
            return 0;
        }

        Alliance alliance = allianceOpt.get();
        if (alliance.getCapitalGuildId().equals(guildOpt.get().getId())) {
            player.sendMessage(Component.text("The capital guild cannot leave the alliance! Transfer or disband first.", NamedTextColor.RED));
            return 0;
        }

        allianceService.removeGuild(alliance, guildOpt.get().getId());
        player.sendMessage(Component.text("Your guild has left " + alliance.getName() + ".", NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        var alliances = allianceService.getAllAlliances();
        if (alliances.isEmpty()) {
            player.sendMessage(Component.text("No alliances exist yet.", NamedTextColor.GRAY));
            return 0;
        }

        player.sendMessage(Component.text("=== Alliances ===", NamedTextColor.GOLD));
        for (Alliance alliance : alliances) {
            player.sendMessage(Component.text("  " + alliance.getName() + " (" + alliance.getGuildCount() + " guilds, King: " + alliance.getKingUuid() + ")",
                    NamedTextColor.YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleInfoSelf(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        Optional<Alliance> allianceOpt = getPlayerAlliance(player);
        if (allianceOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a alliance!", NamedTextColor.RED));
            return 0;
        }
        displayAllianceInfo(player, allianceOpt.get());
        return Command.SINGLE_SUCCESS;
    }

    private int handleInfoSpecific(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String allianceName = StringArgumentType.getString(ctx, "alliance");
        Optional<Alliance> allianceOpt = allianceService.getAlliance(allianceName);
        if (allianceOpt.isEmpty()) {
            player.sendMessage(Component.text("Alliance not found: " + allianceName, NamedTextColor.RED));
            return 0;
        }
        displayAllianceInfo(player, allianceOpt.get());
        return Command.SINGLE_SUCCESS;
    }

    private void displayAllianceInfo(Player player, Alliance alliance) {
        player.sendMessage(Component.text("=== " + alliance.getName() + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("King: ", NamedTextColor.GRAY)
                .append(Component.text(alliance.getKingUuid().toString(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Capital: ", NamedTextColor.GRAY)
                .append(Component.text(alliance.getCapitalGuildId(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Guilds: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(alliance.getGuildCount()), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Tax Rate: ", NamedTextColor.GRAY)
                .append(Component.text(alliance.getTaxRate() + "%", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Open: ", NamedTextColor.GRAY)
                .append(Component.text(alliance.isOpen() ? "Yes" : "No", alliance.isOpen() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        player.sendMessage(Component.text("Allies: ", NamedTextColor.GRAY)
                .append(Component.text(alliance.getAllies().isEmpty() ? "None" : String.join(", ", alliance.getAllies()), NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Enemies: ", NamedTextColor.GRAY)
                .append(Component.text(alliance.getEnemies().isEmpty() ? "None" : String.join(", ", alliance.getEnemies()), NamedTextColor.RED)));
    }

    private int handleAlly(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "alliance");
        Optional<Alliance> allianceOpt = getPlayerAlliance(player);

        if (allianceOpt.isEmpty() || !hasAllianceAuthority(player, allianceOpt.get())) {
            player.sendMessage(Component.text("Only alliance leaders can manage alliances!", NamedTextColor.RED));
            return 0;
        }

        Optional<Alliance> targetOpt = allianceService.getAlliance(targetName);
        if (targetOpt.isEmpty()) {
            player.sendMessage(Component.text("Alliance not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        try {
            allianceService.addAlly(allianceOpt.get(), targetName);
            allianceService.addAlly(targetOpt.get(), allianceOpt.get().getName());
            player.sendMessage(Component.text("Alliance formed with " + targetName + "!", NamedTextColor.GREEN));
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to form alliance: " + e.getMessage(), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleEnemy(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "alliance");
        Optional<Alliance> allianceOpt = getPlayerAlliance(player);

        if (allianceOpt.isEmpty() || !hasAllianceAuthority(player, allianceOpt.get())) {
            player.sendMessage(Component.text("Only alliance leaders can manage enemies!", NamedTextColor.RED));
            return 0;
        }

        Optional<Alliance> targetOpt = allianceService.getAlliance(targetName);
        if (targetOpt.isEmpty()) {
            player.sendMessage(Component.text("Alliance not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        try {
            allianceService.addEnemy(allianceOpt.get(), targetName);
            allianceService.addEnemy(targetOpt.get(), allianceOpt.get().getName());
            player.sendMessage(Component.text(targetName + " is now an enemy!", NamedTextColor.RED));
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to declare enemy: " + e.getMessage(), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleKick(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String guildName = StringArgumentType.getString(ctx, "guild");
        Optional<Alliance> allianceOpt = getPlayerAlliance(player);

        if (allianceOpt.isEmpty() || !hasAllianceAuthority(player, allianceOpt.get())) {
            player.sendMessage(Component.text("Only alliance leaders can kick guilds!", NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> targetGuild = guildService.getGuild(guildName);
        if (targetGuild.isEmpty()) {
            player.sendMessage(Component.text("Guild not found: " + guildName, NamedTextColor.RED));
            return 0;
        }

        try {
            allianceService.removeGuild(allianceOpt.get(), targetGuild.get().getId());
            player.sendMessage(Component.text(guildName + " has been kicked from the alliance.", NamedTextColor.YELLOW));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleSetKing(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "player");
        Optional<Alliance> allianceOpt = getPlayerAlliance(player);

        if (allianceOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a alliance!", NamedTextColor.RED));
            return 0;
        }

        if (!allianceOpt.get().isKing(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the king can transfer kingship!", NamedTextColor.RED));
            return 0;
        }

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        allianceService.setKing(allianceOpt.get(), target.getUniqueId());
        player.sendMessage(Component.text("Kingship transferred to " + targetName + "!", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You are now the king of " + allianceOpt.get().getName() + "!", NamedTextColor.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private int handleSetTax(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        double rate = DoubleArgumentType.getDouble(ctx, "rate");
        Optional<Alliance> allianceOpt = getPlayerAlliance(player);

        if (allianceOpt.isEmpty() || !hasAllianceAuthority(player, allianceOpt.get())) {
            player.sendMessage(Component.text("Only alliance leaders can set tax rate!", NamedTextColor.RED));
            return 0;
        }

        allianceService.setTaxRate(allianceOpt.get(), rate);
        player.sendMessage(Component.text("Tax rate set to " + rate + "%.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int handleSetOpen(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        boolean open = BoolArgumentType.getBool(ctx, "open");
        Optional<Alliance> allianceOpt = getPlayerAlliance(player);

        if (allianceOpt.isEmpty() || !hasAllianceAuthority(player, allianceOpt.get())) {
            player.sendMessage(Component.text("Only alliance leaders can change openness!", NamedTextColor.RED));
            return 0;
        }

        allianceService.setOpen(allianceOpt.get(), open);
        player.sendMessage(Component.text("Alliance is now " + (open ? "open" : "closed") + " for new guilds.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int handleMinisterAdd(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "player");
        Optional<Alliance> allianceOpt = getPlayerAlliance(player);

        if (allianceOpt.isEmpty() || !hasAllianceAuthority(player, allianceOpt.get())) {
            player.sendMessage(Component.text("Only alliance leaders can manage ministers!", NamedTextColor.RED));
            return 0;
        }

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        allianceOpt.get().addMinister(target.getUniqueId());
        allianceService.addMinister(allianceOpt.get(), target.getUniqueId());
        player.sendMessage(Component.text(targetName + " has been promoted to minister.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You are now a minister of " + allianceOpt.get().getName() + "!", NamedTextColor.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private int handleMinisterRemove(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "player");
        Optional<Alliance> allianceOpt = getPlayerAlliance(player);

        if (allianceOpt.isEmpty() || !hasAllianceAuthority(player, allianceOpt.get())) {
            player.sendMessage(Component.text("Only alliance leaders can manage ministers!", NamedTextColor.RED));
            return 0;
        }

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        allianceOpt.get().removeMinister(target.getUniqueId());
        allianceService.removeMinister(allianceOpt.get(), target.getUniqueId());
        player.sendMessage(Component.text(targetName + " has been removed from ministers.", NamedTextColor.YELLOW));
        target.sendMessage(Component.text("You are no longer a minister of " + allianceOpt.get().getName() + ".", NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * /alliance government &lt;form&gt; — the alliance (alliance) picks its governance
     * form; seats derive from alliance roles (king, ministers, member-guild mayors).
     * King only.
     */
    private int handleGovernment(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        com.azoth.territory.model.GovernmentForm form =
                GovernmentFormArgumentType.getForm(ctx, "form");
        Optional<Alliance> allianceOpt = getPlayerAlliance(player);

        if (allianceOpt.isEmpty() || !allianceOpt.get().isKing(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the king can change the alliance's government!", NamedTextColor.RED));
            return 0;
        }

        if (!governanceSource.setAllianceForm(allianceOpt.get().getId(), form)) {
            player.sendMessage(Component.text("Failed to persist the governance form.", NamedTextColor.RED));
            return 0;
        }
        player.sendMessage(Component.text(
                "Alliance government is now " + form.name() + " — seat holders derive from alliance roles.",
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }
}
