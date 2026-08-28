package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import org.aincraft.guilds.alliances.AllianceProposal;
import org.aincraft.guilds.alliances.AllianceProposalStore;
import org.aincraft.guilds.commands.arguments.GovernmentFormArgumentType;
import org.aincraft.guilds.commands.arguments.ResidentArgumentType;
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
 * /alliance create <name> <guild> — propose an alliance; the target mayor must accept
 * /alliance accept <name> — target mayor accepts a pending alliance
 * /alliance invite <guild> — invite a guild to a pending or existing alliance
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
    private final AllianceProposalStore proposalStore = new AllianceProposalStore();
    private int minimumGuilds;

    public AllianceBrigadierCommand(JavaPlugin plugin, AllianceService allianceService,
                                    GuildService guildService, ResidentService residentService,
                                    GuildsGovernanceSource governanceSource) {
        this.plugin = plugin;
        this.allianceService = allianceService;
        this.guildService = guildService;
        this.residentService = residentService;
        this.governanceSource = governanceSource;
        this.minimumGuilds = Math.max(2, plugin.getConfig().getInt("alliance.min-guilds", 2));
    }
    private boolean canOverrideRequirement(Player player) {
        return player.isOp() || player.hasPermission("guilds.admin.alliance");
    }

    private int handleRequirement(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;
        if (!canOverrideRequirement(player)) {
            player.sendMessage(Component.text("You do not have permission to change the alliance requirement.", NamedTextColor.RED));
            return 0;
        }
        int count = IntegerArgumentType.getInteger(ctx, "count");
        if (count < 2) {
            player.sendMessage(Component.text("The alliance requirement must be at least 2 guilds.", NamedTextColor.RED));
            return 0;
        }
        minimumGuilds = count;
        plugin.getConfig().set("alliance.min-guilds", count);
        plugin.saveConfig();
        player.sendMessage(Component.text("Alliance creation now requires " + count
                + " guilds. This setting persists across restarts.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("alliance")
                .requires(source -> source.getSender().hasPermission("guilds.commands.alliance"))
                .executes(this::handleInfoSelf)
                .then(Commands.literal("create")
                        .executes(this::handleCreateUsage)
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(this::handleCreateUsage)
                                .then(Commands.argument("guild", StringArgumentType.string())
                                        .executes(this::handleCreate))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(this::handleAccept)))
                .then(Commands.literal("requirement")
                        .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(this::handleRequirement)))
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
                                .then(Commands.argument("player", ResidentArgumentType.resident(residentService))
                                        .executes(this::handleSetKing)))
                        .then(Commands.literal("tax")
                                .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .executes(this::handleSetTax)))
                        .then(Commands.literal("open")
                                .then(Commands.argument("open", BoolArgumentType.bool())
                                        .executes(this::handleSetOpen))))
                .then(Commands.literal("minister")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", ResidentArgumentType.resident(residentService))
                                        .executes(this::handleMinisterAdd)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", ResidentArgumentType.resident(residentService))
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

    /**
     * Resolve a named resident from the persistent Guilds database.
     *
     * <p>Player objects are only available while a player is online. Identity
     * and membership operations must therefore resolve the UUID from the
     * resident store, then optionally notify an online player.</p>
     */
    private Optional<Resident> resolveResident(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Optional<Resident> exact = residentService.getResident(name);
        if (exact.isPresent()) {
            return exact;
        }
        return residentService.searchResidents(name, 50).stream()
                .filter(resident -> resident.getName() != null
                        && resident.getName().equalsIgnoreCase(name))
                .findFirst();
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

    private int handleCreateUsage(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;
        player.sendMessage(Component.text(
                "Usage: /alliance create <name> <guild>. The other guild's mayor must accept before the alliance is created.",
                NamedTextColor.YELLOW));
        return 0;
    }

    private int handleCreate(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");
        String targetGuildName = StringArgumentType.getString(ctx, "guild");

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

        if (guildAlreadyAllied(guild.getId())) {
            player.sendMessage(Component.text("Your guild is already in a alliance!", NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> targetOpt = guildService.getGuild(targetGuildName);
        if (targetOpt.isEmpty()) {
            player.sendMessage(Component.text("Guild not found: " + targetGuildName, NamedTextColor.RED));
            return 0;
        }

        Guild target = targetOpt.get();
        if (guildAlreadyAllied(target.getId())) {
            player.sendMessage(Component.text("That guild is already in a alliance!", NamedTextColor.RED));
            return 0;
        }

        try {
            proposalStore.propose(name, guild.getId(), player.getUniqueId(), target.getId());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return 0;
        }

        player.sendMessage(Component.text(
                "Proposed alliance " + name + " to " + target.getName()
                        + ". Their mayor must run /alliance accept " + name + ".",
                NamedTextColor.GREEN));
        notifyMayor(target, Component.text(
                guild.getName() + " proposed alliance " + name + ". Run /alliance accept " + name + " to join.",
                NamedTextColor.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private int handleAccept(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");
        Optional<Guild> guildOpt = getPlayerGuild(player);
        if (guildOpt.isEmpty()) {
            player.sendMessage(Component.text("You must be in a guild to accept an alliance!", NamedTextColor.RED));
            return 0;
        }

        Guild guild = guildOpt.get();
        if (!isMayorOfGuild(player, guild.getName())) {
            player.sendMessage(Component.text("Only guild mayors can accept alliance proposals!", NamedTextColor.RED));
            return 0;
        }

        Optional<AllianceProposal> pending = proposalStore.get(name);
        if (pending.isEmpty()) {
            player.sendMessage(Component.text("No pending alliance named " + name + ".", NamedTextColor.RED));
            return 0;
        }

        AllianceProposalStore.AcceptOutcome outcome;
        try {
            outcome = proposalStore.accept(name, guild.getId(), minimumGuilds);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return 0;
        }

        if (!outcome.committed()) {
            player.sendMessage(Component.text(
                    name + " now has " + outcome.proposal().acceptedGuildIds().size()
                            + " of " + minimumGuilds + " required guilds.",
                    NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        try {
            persistCommittedProposal(outcome.proposal());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return 0;
        }

        player.sendMessage(Component.text(
                "Alliance " + name + " created with " + outcome.proposal().acceptedGuildIds().size() + " guilds.",
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int handleInvite(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String guildName = StringArgumentType.getString(ctx, "guild");
        Optional<Guild> playerGuild = getPlayerGuild(player);
        if (playerGuild.isEmpty()) {
            player.sendMessage(Component.text("You are not in a guild!", NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> targetGuild = guildService.getGuild(guildName);
        if (targetGuild.isEmpty()) {
            player.sendMessage(Component.text("Guild not found: " + guildName, NamedTextColor.RED));
            return 0;
        }

        if (guildAlreadyAllied(targetGuild.get().getId())) {
            player.sendMessage(Component.text("That guild is already in a alliance!", NamedTextColor.RED));
            return 0;
        }

        Optional<AllianceProposal> pending = proposalStore.findByProposingGuild(playerGuild.get().getId());
        if (pending.isPresent()) {
            if (!playerGuild.get().getMayorUuid().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("Only the proposing mayor can invite guilds to a pending alliance!", NamedTextColor.RED));
                return 0;
            }
            try {
                proposalStore.invite(pending.get().name(), playerGuild.get().getId(), targetGuild.get().getId());
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                return 0;
            }
            player.sendMessage(Component.text(
                    "Invitation sent to " + guildName + " for pending alliance " + pending.get().name() + ".",
                    NamedTextColor.GREEN));
            notifyMayor(targetGuild.get(), Component.text(
                    playerGuild.get().getName() + " invited your guild to alliance " + pending.get().name()
                            + ". Run /alliance accept " + pending.get().name() + " to join.",
                    NamedTextColor.GOLD));
            return Command.SINGLE_SUCCESS;
        }

        Optional<Alliance> allianceOpt = getPlayerAlliance(player);
        if (allianceOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a alliance!", NamedTextColor.RED));
            return 0;
        }

        if (!hasAllianceAuthority(player, allianceOpt.get())) {
            player.sendMessage(Component.text("Only the king or ministers can invite guilds!", NamedTextColor.RED));
            return 0;
        }

        player.sendMessage(Component.text("Invitation sent to " + guildName + "!", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private boolean guildAlreadyAllied(String guildId) {
        return allianceService.getAllAlliances().stream().anyMatch(alliance -> alliance.hasGuild(guildId));
    }

    private void persistCommittedProposal(AllianceProposal proposal) {
        Guild capital = guildService.getGuildById(proposal.proposingGuildId())
                .orElseThrow(() -> new IllegalArgumentException("Proposing guild no longer exists"));
        allianceService.createAlliance(proposal.name(), capital, proposal.proposingMayorUuid());
        Alliance alliance = allianceService.getAlliance(proposal.name())
                .orElseThrow(() -> new IllegalArgumentException("Failed to create alliance " + proposal.name()));
        for (String guildId : proposal.acceptedGuildIds()) {
            if (!guildId.equals(proposal.proposingGuildId())) {
                allianceService.addGuild(alliance, guildId);
            }
        }
    }

    private void notifyMayor(Guild guild, Component message) {
        UUID mayorUuid = guild.getMayorUuid();
        if (mayorUuid == null) {
            return;
        }
        Player mayor = plugin.getServer().getPlayer(mayorUuid);
        if (mayor != null) {
            mayor.sendMessage(message);
        }
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

        Resident targetResident = resolveResident(targetName).orElse(null);
        if (targetResident == null || targetResident.getUuid() == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        UUID targetUuid = targetResident.getUuid();
        allianceService.setKing(allianceOpt.get(), targetUuid);
        player.sendMessage(Component.text("Kingship transferred to " + targetName + "!", NamedTextColor.GREEN));
        Player target = plugin.getServer().getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(Component.text("You are now the king of " + allianceOpt.get().getName() + "!", NamedTextColor.GOLD));
        }
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

        Resident targetResident = resolveResident(targetName).orElse(null);
        if (targetResident == null || targetResident.getUuid() == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        UUID targetUuid = targetResident.getUuid();
        allianceOpt.get().addMinister(targetUuid);
        allianceService.addMinister(allianceOpt.get(), targetUuid);
        player.sendMessage(Component.text(targetName + " has been promoted to minister.", NamedTextColor.GREEN));
        Player target = plugin.getServer().getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(Component.text("You are now a minister of " + allianceOpt.get().getName() + "!", NamedTextColor.GOLD));
        }
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

        Resident targetResident = resolveResident(targetName).orElse(null);
        if (targetResident == null || targetResident.getUuid() == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        UUID targetUuid = targetResident.getUuid();
        allianceOpt.get().removeMinister(targetUuid);
        allianceService.removeMinister(allianceOpt.get(), targetUuid);
        player.sendMessage(Component.text(targetName + " has been removed from ministers.", NamedTextColor.YELLOW));
        Player target = plugin.getServer().getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(Component.text("You are no longer a minister of " + allianceOpt.get().getName() + ".", NamedTextColor.YELLOW));
        }
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

        org.aincraft.guilds.territory.model.GovernmentForm form =
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
