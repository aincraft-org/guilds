# Brigadier Command Migration Plan

## Overview
Migrate all Towny commands from legacy Bukkit `CommandExecutor` to Paper's modern Brigadier API. Brigadier provides better tab completion, argument validation, and command structure.

## Current Commands (9 total)
1. `/town` - TownCommand.java
2. `/plot` - PlotCommand.java
3. `/towny` - TownyGeneralCommand.java
4. `/townlevel` - TownLevelCommand.java
5. `/townymap` - MapCommand.java
6. `/perm` - PermCommand.java
7. `/plottype` - PlotTypeCommand.java
8. `/broadcast` - TownBroadcastCommand.java
9. `/townperm` - TownPermCommand.java (new, to be created)

## Migration Steps

### Phase 1: Infrastructure Setup
1. **Create Brigadier Command Registry**
   - Location: `src/main/java/org/aincraft/towny/commands/BrigadierCommandRegistry.java`
   - Centralized registration via Paper's LifecycleEvents.COMMANDS
   - Inject Guice dependencies for all command handlers

2. **Update TownyPlugin.java**
   - Replace `registerCommands()` with Brigadier lifecycle registration
   - Register event handler for `LifecycleEvents.COMMANDS`
   - Remove legacy CommandExecutor registrations

3. **Remove plugin.yml command registrations**
   - Keep permissions only
   - Brigadier handles registration dynamically

### Phase 2: Create Brigadier Argument Types
Create custom argument types in `src/main/java/org/aincraft/towny/commands/arguments/`:

1. **TownArgumentType.java**
   - Suggests existing town names
   - Validates town exists

2. **ResidentArgumentType.java**
   - Suggests online/offline residents
   - Validates resident exists

3. **PlotTypeArgumentType.java**
   - Suggests: residential, shop, arena, embassy, farm, etc.

4. **PermissionArgumentType.java**
   - Suggests: build, destroy, switch, item_use, claim, etc.

5. **RoleArgumentType.java**
   - Suggests: resident, ally, outsider, nation, mayor, assistant

### Phase 3: Migrate Each Command

#### Template Structure
```java
public class TownBrigadierCommand {
    private final TownService townService;
    // ... other services

    @Inject
    public TownBrigadierCommand(...) { }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("town")
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(this::handleCreate)))
            .then(Commands.literal("join")
                .then(Commands.argument("town", TownArgumentType.town())
                    .executes(this::handleJoin)))
            .then(Commands.literal("claim")
                .executes(this::handleClaim))
            // ... more subcommands
            .build();
    }

    private int handleCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        // Logic here
        return Command.SINGLE_SUCCESS;
    }
}
```

#### Migration Order (by complexity)
1. **PermCommand** (simplest, 7 subcommands)
2. **MapCommand** (single command)
3. **TownLevelCommand** (moderate, ~5 subcommands)
4. **PlotTypeCommand** (moderate)
5. **TownyGeneralCommand** (moderate)
6. **PlotCommand** (complex, ~10 subcommands)
7. **TownCommand** (complex, ~12 subcommands)
8. **TownBroadcastCommand** (complex, ~10 subcommands)
9. **TownPermCommand** (new command to create)

### Phase 4: Command-Specific Details

#### /town Command
Subcommands:
- `create <name>` - StringArgumentType
- `join <town>` - TownArgumentType
- `leave`
- `delete`
- `claim`
- `unclaim`
- `list [page]` - IntegerArgumentType
- `info [town]` - TownArgumentType (optional)
- `spawn [town]` - TownArgumentType (optional)
- `setspawn`
- `toggle <setting> [value]` - CustomEnumType + BoolArgumentType
- `invite <player>` - EntityArgumentType
- `kick <resident>` - ResidentArgumentType

#### /plot Command
Subcommands:
- `claim`
- `unclaim`
- `buy`
- `sell <price>` - DoubleArgumentType
- `forsale <price>` - DoubleArgumentType
- `notforsale`
- `info`
- `perm set <role> <perm> <value>` - RoleArgumentType + PermissionArgumentType + BoolArgumentType
- `perm list`
- `set <type>` - PlotTypeArgumentType
- `toggle <setting> [value]`

#### /townperm Command (NEW)
Subcommands:
- `set <role> <perms>` - RoleArgumentType + PermissionArgumentType (multi)
- `add <role> <perms>` - RoleArgumentType + PermissionArgumentType (multi)
- `remove <role> <perms>` - RoleArgumentType + PermissionArgumentType (multi)
- `list [role]` - RoleArgumentType (optional)
- `reset <role>` - RoleArgumentType

#### /broadcast Command
Subcommands:
- `create <type> <message...>` - EnumType + GreedyStringArgumentType
- `announce <message...>` - GreedyStringArgumentType
- `alert <message...>` - GreedyStringArgumentType
- `welcome <message...>` - GreedyStringArgumentType
- `list [page]` - IntegerArgumentType
- `read [id]` - IntegerArgumentType
- `archive <id>` - IntegerArgumentType
- `delete <id>` - IntegerArgumentType
- `stats`
- `cleanup`
- `send <type> <message...>` - EnumType + GreedyStringArgumentType

#### /perm Command (debug)
Subcommands:
- `check`
- `build`
- `destroy`
- `plot [flag]` - PermissionArgumentType (optional)
- `town <townname>` - TownArgumentType
- `flags`
- `here`

#### /townlevel Command
Subcommands:
- `info`
- `progress`
- `requirements [level]` - IntegerArgumentType (optional)
- `list`
- `contribute <resource> <amount>` - ResourceTypeArgumentType + IntegerArgumentType

#### /townymap Command
Args:
- `[mode]` - EnumArgumentType (ascii, fancy)

#### /plottype Command
Subcommands:
- `list`
- `info <type>` - PlotTypeArgumentType
- `create <type> <name>` - StringArgumentType x2
- `delete <type>` - PlotTypeArgumentType
- `set <type> <setting> <value>` - PlotTypeArgumentType + StringArgumentType x2

### Phase 5: Permission Integration
Brigadier supports permission checks via `.requires()`:

```java
Commands.literal("delete")
    .requires(source -> source.getSender().hasPermission("towny.town.delete"))
    .executes(this::handleDelete)
```

Map all existing permissions from plugin.yml to Brigadier `.requires()` calls.

### Phase 6: Tab Completion
Brigadier auto-generates tab completion from command structure. Custom suggestions:

```java
Commands.argument("town", TownArgumentType.town())
    .suggests((ctx, builder) -> {
        townService.getAllTownNames()
            .forEach(builder::suggest);
        return builder.buildFuture();
    })
```

### Phase 7: Testing
1. Test each command individually
2. Verify tab completion works
3. Verify permission checks
4. Verify error messages
5. Test edge cases (empty args, invalid args)
6. Test all aliases

## Implementation Notes

### Brigadier Imports
```java
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
```

### Getting Player from Context
```java
private int someCommand(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) {
        sender.sendMessage("§cPlayers only");
        return 0;
    }
    // Use player
}
```

### Error Handling
```java
private int handleCommand(CommandContext<CommandSourceStack> ctx) {
    try {
        // Command logic
        return Command.SINGLE_SUCCESS;
    } catch (Exception e) {
        ctx.getSource().getSender().sendMessage("§cError: " + e.getMessage());
        return 0;
    }
}
```

### Argument Types Available
- `StringArgumentType.word()` - Single word
- `StringArgumentType.string()` - Quoted string
- `StringArgumentType.greedyString()` - Rest of line
- `IntegerArgumentType.integer()` - Integer
- `IntegerArgumentType.integer(min, max)` - Range
- `DoubleArgumentType.doubleArg()` - Double
- `BoolArgumentType.bool()` - Boolean
- `EntityArgumentType` (from Paper) - Players/entities

## File Structure After Migration
```
src/main/java/org/aincraft/towny/commands/
├── BrigadierCommandRegistry.java (new)
├── arguments/ (new)
│   ├── TownArgumentType.java
│   ├── ResidentArgumentType.java
│   ├── PlotTypeArgumentType.java
│   ├── PermissionArgumentType.java
│   └── RoleArgumentType.java
├── brigadier/ (new)
│   ├── TownBrigadierCommand.java
│   ├── PlotBrigadierCommand.java
│   ├── TownyGeneralBrigadierCommand.java
│   ├── TownLevelBrigadierCommand.java
│   ├── MapBrigadierCommand.java
│   ├── PermBrigadierCommand.java
│   ├── PlotTypeBrigadierCommand.java
│   ├── TownBroadcastBrigadierCommand.java
│   └── TownPermBrigadierCommand.java
└── legacy/ (move old files here, then delete)
    ├── TownCommand.java
    ├── PlotCommand.java
    └── ... (others)
```

## Estimated Effort
- Phase 1: 2 hours
- Phase 2: 3 hours
- Phase 3-4: 10-15 hours (varies by command complexity)
- Phase 5: 1 hour
- Phase 6: 2 hours
- Phase 7: 3 hours
- **Total**: ~20-25 hours

## Benefits
1. **Better UX** - Rich tab completion with argument validation
2. **Type Safety** - Compile-time argument type checking
3. **Modern API** - Paper's recommended approach
4. **Client Suggestions** - Client-side command hints
5. **Cleaner Code** - Declarative command structure
6. **Reduced Boilerplate** - No manual arg parsing

## Risks
1. Breaking changes for any plugins hooking into commands
2. Learning curve for Brigadier
3. More complex command registration
4. Must test thoroughly before release

## Rollback Plan
Keep legacy commands in `commands/legacy/` until migration fully tested. Can revert by:
1. Restoring old `registerCommands()` in TownyPlugin
2. Re-adding commands to plugin.yml
3. Removing Brigadier lifecycle handler
