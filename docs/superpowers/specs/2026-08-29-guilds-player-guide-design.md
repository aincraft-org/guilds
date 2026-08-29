# Guilds Player Guide Design

## Audience and purpose

This guide is for players and server operators who need one reliable view of
what the Guilds plugin currently provides. It translates the repository's
source-backed behavior into player-visible concepts, day-one actions, everyday
commands, advanced workflows, and practical limitations.

The guide covers the complete current Guilds surface rather than treating
enchantments as a standalone subsystem. Enchantments receive an explicit
status section because the repository contains enchanting-table protection and
an Arcane specialization declaration, but it does not contain a custom
enchantment implementation.

## Source authority

The guide uses these sources in descending order of authority:

1. Production command, service, listener, model, configuration, and resource
   behavior under `guilds-paper/`, `guilds-common/`, and `guilds-api/`.
2. The active domain catalogs under `docs/living-specs/`.
3. The current root `README.md` for operator setup and product overview.
4. Tests as corroboration of observable behavior and failure paths.

Archived Guilds documents and unreachable command classes are not treated as
live behavior. When configuration or metadata describes an effect that is not
consumed by runtime code, the page labels it `Declared, not active` rather than
presenting it as a player benefit.

## Deliverables

Create a content-only Fumadocs-compatible documentation surface:

- `content/docs/index.mdx` — a short landing page introducing Guilds and
  linking the comprehensive guide.
- `content/docs/meta.json` — sidebar containing `index` and `guilds-guide` in
  reader-friendly order.
- `content/docs/guilds-guide.mdx` — the complete player/operator guide.
- `scripts/verify-docs.mjs` — the dependency-free docs gate from the
  docs-maintenance skill, installed for this repository's content-only shape.
- `README.md` — a relative link to the new guide, placed with the existing
  player-facing feature overview.

No Next/Fumadocs application, npm dependency, or second documentation hub is
introduced. The repository has a separate `web/` package, not a root docs
application; the content-only gate is run directly with Node.

## Page structure

`guilds-guide.mdx` follows the required floor-to-ceiling ladder and keeps
commands after the concepts they implement:

1. **The idea: Guilds as shared territory and progression** — explain guild
   membership, land, governance, and progression without commands.
2. **The basics: joining a guild and understanding your land** — cover
   membership, roles, claims, plots, protection, and the first useful map.
3. **Everyday: the player command reference** — explain when to use the daily
   guild, plot, map, chat, bank, storage, quest, level, alliance, and travel
   commands, followed by a consolidated command table.
4. **Advanced: progression, governance, travel, and operator systems** — use
   subsections for levels/XP, projects/tech tree, specializations, alliances,
   territories/zones, facilities/fast travel, influence/standing/upkeep,
   invasions, web/editor/API, maps, integrations, and persistence-backed
   behavior. Open the section with the required advanced callout.
5. **What it means for you** — summarize permissions, async/failure behavior,
   optional integrations, current limitations, and safety expectations.

The page also contains a status legend near the beginning:

- **Implemented** — source and tests show the behavior is reachable.
- **Declared, not active** — configuration/model text exists, but no runtime
  consumer applies the advertised effect.
- **API/operator-only** — available to integrations or administrators, not a
  normal player command.
- **Unavailable or pending** — source/spec explicitly leaves it disabled,
  unregistered, unverified, or planned.

## Feature coverage

The guide will cover these feature families and their observable behavior:

- Guild lifecycle: create/new, join, leave, mayor-protected deletion,
  residents, assistants, home/spawn, open state, toggles, and rankings.
- Claims and plots: chunk claims, personal/for-sale plots, plot types,
  buying, plot permissions, and guild protection rules.
- Chat and communication: one-off and toggled guild chat, join/welcome and
  system broadcasts, and the distinction between implemented listeners and
  unimplemented broadcast management help.
- Territories and zones: polygon/chunk boundaries, wilderness/claimable
  zones, lookup, governance binding, reload/save, and spatial rules.
- Facilities: waystones, guild crystals, teleport terminals, boats, airships,
  trading posts, storage, and banks; exact-anchor placement and free-form
  surrounding construction.
- Fast travel: mode prerequisites, same-guild/alliance rules, costs,
  player-bound currency, rewards, warm-up, cancellation, safe landing,
  protection, cooldowns, reservation expiry, and current-state authorization.
- Progression: XP-only guild level upgrades, configured level benefits, tech
  points, one active project, prerequisites, the complete configured tech-tree
  catalog, and the distinction between applied and future effect metadata.
- Specializations: the five selectable specialization labels, level/role
  gates, persistence, and the fact that declared perks—including Arcane's
  enchanting values—are not currently applied by runtime code.
- Alliances and governance: proposals, membership, king/ministers, ally/enemy
  relations, taxes/open state, governance forms, authority gates, and
  governance-derived territory ownership.
- Quests, resources, and contracts: player-visible quest/progress/reward and
  resource contribution flows; contracts are documented as service/API
  behavior only because no player command is registered.
- Storage and economy: facility-gated storage, roles/tabs/slots, Mint guild
  bank operations, capacity, separate legacy SQL balance, simulation/default
  behavior, and failed-operation handling.
- Maps and integrations: MapGUI pan/claim/unclaim with confirmation and chat
  fallback; squaremap display-only layers; WorldGuard read-only projection;
  embedded API/editor authentication and TLS options; Vercel proxy; and
  PlaceholderAPI values.
- Influence, standing, upkeep, and invasions: player/admin actions, scopes,
  declarations, cooldowns, durable status, operator-triggered guild-plot
  invasions, bounded spawning, and fail-closed behavior.
- Enchantments: vanilla enchanting only; access to enchanting blocks follows
  territory interaction permissions; no custom enchant API, item, command,
  event, cost modifier, or chance modifier exists.

## Command treatment

The guide documents reachable command paths exactly as registered, including
aliases and nested forms. It does not advertise a standalone `/techtree`,
`/broadcast` management subcommands, or `/guildperm` mutations because the
current registry exposes those only as help or leaves them unregistered. It
identifies source permission gates and role requirements in user language
(operators, mayors, ministers, residents) and avoids promising defaults where
plugin metadata and source gates differ.

Operator-only commands appear in the Advanced section, not in the day-one
path. Command tables are cheat sheets after the explanatory paragraphs, not the
opening content.

## Verification and acceptance criteria

The documentation change is complete when:

- every MDX page has quoted `title` and `description` frontmatter;
- `content/docs/index.mdx` and at least one guide page exist;
- `meta.json` reaches every page and contains no dangling slug;
- internal `/docs/*` links resolve;
- the guide contains the labels `basics`, `Everyday`, and `Advanced`;
- the guide has no hardcoded hex colors or invented behavior;
- `node scripts/verify-docs.mjs --ladder` passes from the repository root;
- the root README links to the new guide; and
- the final review checks the guide against the source inventory and explicitly
  preserves the no-custom-enchantments and inactive-Arcane conclusions.

A live Paper/server smoke test is not required for a content-only change and
will not be claimed. The guide will call out runtime scenarios that remain
unverified in the repository's active specs.
