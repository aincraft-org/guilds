package org.aincraft.commands.components.region;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.service.GuildMemberService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Router component for region management commands.
 * Routes subcommands to specialized component handlers.
 * Single Responsibility: Command routing and help display.
 */
public class RegionComponent implements GuildCommand {
    private final RegionSelectionComponent selectionComponent;
    private final RegionBasicComponent basicComponent;
    private final RegionTypeComponent typeComponent;
    private final RegionOwnerComponent ownerComponent;
    private final RegionPermissionComponent permissionComponent;
    private final GuildMemberService memberService;

    @Inject
    public RegionComponent(RegionSelectionComponent selectionComponent, RegionBasicComponent basicComponent,
                          RegionTypeComponent typeComponent, RegionOwnerComponent ownerComponent,
                          RegionPermissionComponent permissionComponent, GuildMemberService memberService) {
        this.selectionComponent = selectionComponent;
        this.basicComponent = basicComponent;
        this.typeComponent = typeComponent;
        this.ownerComponent = ownerComponent;
        this.permissionComponent = permissionComponent;
        this.memberService = memberService;
    }

    @Override
    public String getName() {
        return "region";
    }

    @Override
    public String getPermission() {
        return "guilds.region";
    }

    @Override
    public String getUsage() {
        return "/g region <pos1|pos2|create|cancel|delete|list|info|types|settype|addowner|removeowner|setperm|removeperm|listperms|role|limit>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players</error>");
            return true;
        }

        if (args.length < 2) {
            showHelp(player);
            return true;
        }

        String subCommand = args[1].toLowerCase();

        // Selection workflow commands
        return switch (subCommand) {
            case "pos1" -> selectionComponent.handlePos1(player);
            case "pos2" -> selectionComponent.handlePos2(player);
            case "create" -> selectionComponent.handleCreate(player, args);
            case "cancel" -> selectionComponent.handleCancel(player);

            // Basic CRUD commands
            case "delete" -> basicComponent.handleDelete(player, args);
            case "list" -> basicComponent.handleList(player);
            case "info" -> basicComponent.handleInfo(player, args);
            case "visualize", "show" -> basicComponent.handleVisualize(player, args);

            // Type management commands
            case "types" -> typeComponent.handleTypes(player);
            case "settype" -> typeComponent.handleSetType(player, args);
            case "limit" -> typeComponent.handleLimit(player, args);

            // Owner management commands
            case "addowner" -> ownerComponent.handleAddOwner(player, args);
            case "removeowner" -> ownerComponent.handleRemoveOwner(player, args);

            // Permission management commands
            case "setperm" -> permissionComponent.handleSetPerm(player, args);
            case "removeperm" -> permissionComponent.handleRemovePerm(player, args);
            case "listperms" -> permissionComponent.handleListPerms(player, args);
            case "role" -> {
                Guild guild = memberService.getPlayerGuild(player.getUniqueId());
                if (guild == null) {
                    Mint.sendMessage(player, "<error>You must be in a guild to use this command</error>");
                    yield true;
                }
                yield permissionComponent.handleRole(player, guild, args);
            }

            default -> {
                showHelp(player);
                yield true;
            }
        };
    }

    /**
     * Displays help information for region commands.
     */
    private void showHelp(Player player) {
        Mint.sendMessage(player, "<primary>=== Region Commands ===</primary>");
    }
}
