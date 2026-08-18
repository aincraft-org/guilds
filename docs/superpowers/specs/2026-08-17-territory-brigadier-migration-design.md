# Territory Command Brigadier Migration Design

## Goal

Migrate the remaining `/territory` command from Bukkit `CommandExecutor`/`TabCompleter` wiring to Paper Brigadier while preserving its current behavior and command names.

## Scope

- Preserve `/territory` and the existing aliases `/guildsterritory` and `/at`.
- Preserve all current subcommands: lookup, here, list, reload, save, web, govern, influence, declare, upkeep, standing, invasion, and building.
- Preserve current permissions, messages, subsystem checks, and building-command delegation.
- Replace executor and tab-completer registration with Paper lifecycle registration.
- Keep the existing `plugin.yml` declaration unless build/runtime verification proves it is redundant.

## Architecture

Add `TerritoryBrigadierCommand`, following the existing `*BrigadierCommand` classes. It builds a `LiteralCommandNode<CommandSourceStack>` rooted at `territory`; aliases are registered as redirects in `BrigadierCommandRegistry`. Brigadier callbacks pass `CommandSourceStack.getSender()` into the existing territory behavior implementation.

The migration uses a thin adapter rather than rewriting domain behavior. Existing `TerritoryCommand` methods remain the source of user-facing behavior, with a small execution boundary that accepts a sender and argument list. Brigadier owns syntax, literals, aliases, and suggestions; the legacy Bukkit interfaces and `onCommand`/`onTabComplete` entry points are removed.

Use greedy string arguments for values whose current implementation accepts arbitrary text or reports domain-specific errors. Use numeric Brigadier arguments only where parser-level conversion does not change an existing user-facing contract. Suggestions reproduce the current top-level and invasion suggestions; building suggestions delegate to `BuildingCommand.complete`.

## Registration

Construct `TerritoryBrigadierCommand` after the plugin subsystems are initialized. Pass it to `BrigadierCommandRegistry`, register the root node, and register aliases by redirect. Remove `getCommand("territory").setExecutor(...)` and `.setTabCompleter(...)` wiring. Do not remove `plugin.yml` metadata until verification confirms Paper lifecycle registration fully replaces it without warnings or command conflicts.

## Error Handling

Callbacks preserve existing behavior: command handlers send their current messages and return success. Brigadier syntax errors only cover malformed typed arguments, and should not replace domain-level unknown territory/guild messages where the current command accepted the text.

## Testing

Retain existing service and behavior tests. Add focused tests for the Brigadier command tree: root and alias registration shape, top-level suggestions, invasion suggestions, and callback delegation for at least one existing command path. Run the territory command test classes and the paper module test task.

## Alternatives Considered

1. Thin adapter around existing behavior — selected; smallest behavior risk and aligns with the current codebase's Brigadier classes.
2. Full typed rewrite — rejected for this migration because it risks changing validation and messages across a large command tree.
3. Keep a Bukkit executor behind a Brigadier shim — rejected because it leaves the legacy command path and tab completion in place rather than completing the migration.
