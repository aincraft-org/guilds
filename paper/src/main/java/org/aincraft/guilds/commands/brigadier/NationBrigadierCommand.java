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
import org.aincraft.guilds.models.Nation;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.NationService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Brigadier command for nation system management.
 * /nation create <name> — create a nation (mayor of a guild)
 * /nation invite <guild> — invite a guild (king/minister only)
 * /nation join <nation> — accept invite (mayor only)
 * /nation leave — leave nation
 * /nation list — list all nations
 * /nation info [nation] — show nation details
 * /nation ally <nation> — add ally (king/minister only)
 * /nation enemy <nation> — add enemy (king/minister only)
 * /nation kick <guild> — kick a guild (king/minister only)
 * /nation set king <player> — transfer kingship
 * /nation set tax <rate> — set tax rate
 * /nation set open <true|false> — toggle open/closed
 * /nation minister add <player> — promote to minister
 * /nation minister remove <player> — demote minister
 */
public class NationBrigadierCommand {

    private final JavaPlugin plugin;
    private final NationService nationService;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final GuildsGovernanceSource governanceSource;


    public NationBrigadierCommand(JavaPlugin plugin, NationService nationService,
                                  GuildService guildService, ResidentService residentService,
                                  GuildsGovernanceSource governanceSource) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.guildService = guildService;
        this.residentService = residentService;
        this.governanceSource = governanceSource;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("nation")
                .requires(source -> source.getSender().hasPermission("guilds.commands.nation"))
                .executes(this::handleInfoSelf)
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(this::handleCreate)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("town", StringArgumentType.string())
                                .executes(this::handleInvite)))
                .then(Commands.literal("join")
                        .then(Commands.argument("nation", StringArgumentType.string())
                                .executes(this::handleJoin)))
                .then(Commands.literal("leave")
                        .executes(this::handleLeave))
                .then(Commands.literal("list")
                        .executes(this::handleList))
                .then(Commands.literal("info")
                        .executes(this::handleInfoSelf)
                        .then(Commands.argument("nation", StringArgumentType.string())
                                .executes(this::handleInfoSpecific)))
                .then(Commands.literal("ally")
                        .then(Commands.argument("nation", StringArgumentType.string())
                                .executes(this::handleAlly)))
                .then(Commands.literal("enemy")
                        .then(Commands.argument("nation", StringArgumentType.string())
                                .executes(this::handleEnemy)))
                .then(Commands.literal("kick")
                        .then(Commands.argument("town", StringArgumentType.string())
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

    private Optional<Nation> getPlayerNation(Player player) {
        return getPlayerGuild(player)
                .flatMap(guild -> nationService.getAllNations().stream()
                        .filter(n -> n.hasGuild(guild.getId()))
                        .findFirst());
    }

    private boolean hasNationAuthority(Player player, Nation nation) {
        return nation.isKing(player.getUniqueId()) || nation.isMinister(player.getUniqueId());
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

        if (nationService.getNation(name).isPresent()) {
            player.sendMessage(Component.text("A nation with that name already exists!", NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> guildOpt = getPlayerGuild(player);
        if (guildOpt.isEmpty()) {
            player.sendMessage(Component.text("You must be in a town to create a nation!", NamedTextColor.RED));
            return 0;
        }

        Guild guild = guildOpt.get();
        if (!guild.getMayorUuid().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Only town mayors can create nations!", NamedTextColor.RED));
            return 0;
        }

        try {
            nationService.createNation(name, guild, player.getUniqueId());
            player.sendMessage(Component.text("Nation " + name + " created! Your town is now the capital.", NamedTextColor.GREEN));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleInvite(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String guildName = StringArgumentType.getString(ctx, "town");
        Optional<Nation> nationOpt = getPlayerNation(player);

        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a nation!", NamedTextColor.RED));
            return 0;
        }

        if (!hasNationAuthority(player, nationOpt.get())) {
            player.sendMessage(Component.text("Only the king or ministers can invite guilds!", NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> targetGuild = guildService.getGuild(guildName);
        if (targetGuild.isEmpty()) {
            player.sendMessage(Component.text("Town not found: " + guildName, NamedTextColor.RED));
            return 0;
        }

        // Check if guild is already in a nation
        boolean alreadyInNation = nationService.getAllNations().stream()
                .anyMatch(n -> n.hasGuild(targetGuild.get().getId()));
        if (alreadyInNation) {
            player.sendMessage(Component.text("That town is already in a nation!", NamedTextColor.RED));
            return 0;
        }

        player.sendMessage(Component.text("Invitation sent to " + guildName + "!", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int handleJoin(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String nationName = StringArgumentType.getString(ctx, "nation");
        Optional<Nation> nationOpt = nationService.getNation(nationName);

        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("Nation not found: " + nationName, NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> guildOpt = getPlayerGuild(player);
        if (guildOpt.isEmpty()) {
            player.sendMessage(Component.text("You must be in a town to join a nation!", NamedTextColor.RED));
            return 0;
        }

        if (!guildOpt.get().getMayorUuid().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Only town mayors can join nations!", NamedTextColor.RED));
            return 0;
        }

        if (!nationOpt.get().isOpen()) {
            player.sendMessage(Component.text("That nation is not accepting new guilds!", NamedTextColor.RED));
            return 0;
        }

        nationService.addGuild(nationOpt.get(), guildOpt.get().getId());
        player.sendMessage(Component.text("Your town has joined " + nationName + "!", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int handleLeave(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        Optional<Guild> guildOpt = getPlayerGuild(player);
        if (guildOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a town!", NamedTextColor.RED));
            return 0;
        }

        Optional<Nation> nationOpt = getPlayerNation(player);
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("Your town is not in a nation!", NamedTextColor.RED));
            return 0;
        }

        Nation nation = nationOpt.get();
        if (nation.getCapitalGuildId().equals(guildOpt.get().getId())) {
            player.sendMessage(Component.text("The capital town cannot leave the nation! Transfer or disband first.", NamedTextColor.RED));
            return 0;
        }

        nationService.removeGuild(nation, guildOpt.get().getId());
        player.sendMessage(Component.text("Your town has left " + nation.getName() + ".", NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        var nations = nationService.getAllNations();
        if (nations.isEmpty()) {
            player.sendMessage(Component.text("No nations exist yet.", NamedTextColor.GRAY));
            return 0;
        }

        player.sendMessage(Component.text("=== Nations ===", NamedTextColor.GOLD));
        for (Nation nation : nations) {
            player.sendMessage(Component.text("  " + nation.getName() + " (" + nation.getGuildCount() + " guilds, King: " + nation.getKingUuid() + ")",
                    NamedTextColor.YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleInfoSelf(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        Optional<Nation> nationOpt = getPlayerNation(player);
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a nation!", NamedTextColor.RED));
            return 0;
        }
        displayNationInfo(player, nationOpt.get());
        return Command.SINGLE_SUCCESS;
    }

    private int handleInfoSpecific(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String nationName = StringArgumentType.getString(ctx, "nation");
        Optional<Nation> nationOpt = nationService.getNation(nationName);
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("Nation not found: " + nationName, NamedTextColor.RED));
            return 0;
        }
        displayNationInfo(player, nationOpt.get());
        return Command.SINGLE_SUCCESS;
    }

    private void displayNationInfo(Player player, Nation nation) {
        player.sendMessage(Component.text("=== " + nation.getName() + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("King: ", NamedTextColor.GRAY)
                .append(Component.text(nation.getKingUuid().toString(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Capital: ", NamedTextColor.GRAY)
                .append(Component.text(nation.getCapitalGuildId(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Towns: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(nation.getGuildCount()), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Tax Rate: ", NamedTextColor.GRAY)
                .append(Component.text(nation.getTaxRate() + "%", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Open: ", NamedTextColor.GRAY)
                .append(Component.text(nation.isOpen() ? "Yes" : "No", nation.isOpen() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        player.sendMessage(Component.text("Allies: ", NamedTextColor.GRAY)
                .append(Component.text(nation.getAlliances().isEmpty() ? "None" : String.join(", ", nation.getAlliances()), NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Enemies: ", NamedTextColor.GRAY)
                .append(Component.text(nation.getEnemies().isEmpty() ? "None" : String.join(", ", nation.getEnemies()), NamedTextColor.RED)));
    }

    private int handleAlly(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "nation");
        Optional<Nation> nationOpt = getPlayerNation(player);

        if (nationOpt.isEmpty() || !hasNationAuthority(player, nationOpt.get())) {
            player.sendMessage(Component.text("Only nation leaders can manage alliances!", NamedTextColor.RED));
            return 0;
        }

        Optional<Nation> targetOpt = nationService.getNation(targetName);
        if (targetOpt.isEmpty()) {
            player.sendMessage(Component.text("Nation not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        try {
            nationService.addAlly(nationOpt.get(), targetName);
            nationService.addAlly(targetOpt.get(), nationOpt.get().getName());
            player.sendMessage(Component.text("Alliance formed with " + targetName + "!", NamedTextColor.GREEN));
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to form alliance: " + e.getMessage(), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleEnemy(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "nation");
        Optional<Nation> nationOpt = getPlayerNation(player);

        if (nationOpt.isEmpty() || !hasNationAuthority(player, nationOpt.get())) {
            player.sendMessage(Component.text("Only nation leaders can manage enemies!", NamedTextColor.RED));
            return 0;
        }

        Optional<Nation> targetOpt = nationService.getNation(targetName);
        if (targetOpt.isEmpty()) {
            player.sendMessage(Component.text("Nation not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        try {
            nationService.addEnemy(nationOpt.get(), targetName);
            nationService.addEnemy(targetOpt.get(), nationOpt.get().getName());
            player.sendMessage(Component.text(targetName + " is now an enemy!", NamedTextColor.RED));
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to declare enemy: " + e.getMessage(), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleKick(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String guildName = StringArgumentType.getString(ctx, "town");
        Optional<Nation> nationOpt = getPlayerNation(player);

        if (nationOpt.isEmpty() || !hasNationAuthority(player, nationOpt.get())) {
            player.sendMessage(Component.text("Only nation leaders can kick guilds!", NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> targetGuild = guildService.getGuild(guildName);
        if (targetGuild.isEmpty()) {
            player.sendMessage(Component.text("Town not found: " + guildName, NamedTextColor.RED));
            return 0;
        }

        try {
            nationService.removeGuild(nationOpt.get(), targetGuild.get().getId());
            player.sendMessage(Component.text(guildName + " has been kicked from the nation.", NamedTextColor.YELLOW));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleSetKing(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "player");
        Optional<Nation> nationOpt = getPlayerNation(player);

        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a nation!", NamedTextColor.RED));
            return 0;
        }

        if (!nationOpt.get().isKing(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the king can transfer kingship!", NamedTextColor.RED));
            return 0;
        }

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        nationService.setKing(nationOpt.get(), target.getUniqueId());
        player.sendMessage(Component.text("Kingship transferred to " + targetName + "!", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You are now the king of " + nationOpt.get().getName() + "!", NamedTextColor.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private int handleSetTax(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        double rate = DoubleArgumentType.getDouble(ctx, "rate");
        Optional<Nation> nationOpt = getPlayerNation(player);

        if (nationOpt.isEmpty() || !hasNationAuthority(player, nationOpt.get())) {
            player.sendMessage(Component.text("Only nation leaders can set tax rate!", NamedTextColor.RED));
            return 0;
        }

        nationService.setTaxRate(nationOpt.get(), rate);
        player.sendMessage(Component.text("Tax rate set to " + rate + "%.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int handleSetOpen(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        boolean open = BoolArgumentType.getBool(ctx, "open");
        Optional<Nation> nationOpt = getPlayerNation(player);

        if (nationOpt.isEmpty() || !hasNationAuthority(player, nationOpt.get())) {
            player.sendMessage(Component.text("Only nation leaders can change openness!", NamedTextColor.RED));
            return 0;
        }

        nationService.setOpen(nationOpt.get(), open);
        player.sendMessage(Component.text("Nation is now " + (open ? "open" : "closed") + " for new guilds.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int handleMinisterAdd(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "player");
        Optional<Nation> nationOpt = getPlayerNation(player);

        if (nationOpt.isEmpty() || !hasNationAuthority(player, nationOpt.get())) {
            player.sendMessage(Component.text("Only nation leaders can manage ministers!", NamedTextColor.RED));
            return 0;
        }

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        nationOpt.get().addMinister(target.getUniqueId());
        nationService.addMinister(nationOpt.get(), target.getUniqueId());
        player.sendMessage(Component.text(targetName + " has been promoted to minister.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You are now a minister of " + nationOpt.get().getName() + "!", NamedTextColor.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private int handleMinisterRemove(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String targetName = StringArgumentType.getString(ctx, "player");
        Optional<Nation> nationOpt = getPlayerNation(player);

        if (nationOpt.isEmpty() || !hasNationAuthority(player, nationOpt.get())) {
            player.sendMessage(Component.text("Only nation leaders can manage ministers!", NamedTextColor.RED));
            return 0;
        }

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return 0;
        }

        nationOpt.get().removeMinister(target.getUniqueId());
        nationService.removeMinister(nationOpt.get(), target.getUniqueId());
        player.sendMessage(Component.text(targetName + " has been removed from ministers.", NamedTextColor.YELLOW));
        target.sendMessage(Component.text("You are no longer a minister of " + nationOpt.get().getName() + ".", NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * /nation government &lt;form&gt; — the nation (alliance) picks its governance
     * form; seats derive from nation roles (king, ministers, member-guild mayors).
     * King only.
     */
    private int handleGovernment(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        com.azoth.territory.model.GovernmentForm form =
                GovernmentFormArgumentType.getForm(ctx, "form");
        Optional<Nation> nationOpt = getPlayerNation(player);

        if (nationOpt.isEmpty() || !nationOpt.get().isKing(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the king can change the nation's government!", NamedTextColor.RED));
            return 0;
        }

        if (!governanceSource.setNationForm(nationOpt.get().getId(), form)) {
            player.sendMessage(Component.text("Failed to persist the governance form.", NamedTextColor.RED));
            return 0;
        }
        player.sendMessage(Component.text(
                "Nation government is now " + form.name() + " — seat holders derive from nation roles.",
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }
}
