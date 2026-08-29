# Guilds Player Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish one source-backed, player-facing Guilds guide that covers the complete reachable feature surface and makes enchantment support and inactive metadata explicit.

**Architecture:** Add a content-only Fumadocs-compatible `content/docs/` surface without creating a docs application. Keep the landing page small, put all explanatory material and the command cheat sheet in one guide page, and use a dependency-free Node verifier to enforce frontmatter, navigation, links, and the floor-to-ceiling ladder.

**Tech Stack:** Plain MDX, JSON navigation, Node.js builtins for the docs gate, Markdown link from the Java project README.

**Spec:** `docs/superpowers/specs/2026-08-29-guilds-player-guide-design.md`

## Global Constraints

- Write for players and operators; describe observable behavior rather than packages, handlers, or implementation classes.
- Treat production source as authoritative, then active living specs, then the current README and tests; do not use archived docs as live behavior.
- The content must state that no custom enchantments exist; enchanting-table access is protected territory interaction, and Arcane enchantment perks are declared metadata not consumed by runtime.
- Label behavior as Implemented, Declared/not active, API/operator-only, or Unavailable/pending when source evidence requires it.
- Do not advertise standalone `/techtree`, `/broadcast` management subcommands, `/guildperm` mutations, Vault integration, or contracts as player commands.
- Preserve unrelated working-tree changes on local `master`; work only in the isolated documentation worktree.
- Commands follow concepts; the guide must contain the labels `basics`, `Everyday`, and `Advanced`.
- Internal guide links use `/docs/...`; frontmatter `title` and `description` values are quoted when necessary.

---

### Task 1: Install content-only docs contract

**Files:**
- Create: `content/docs/index.mdx`
- Create: `content/docs/meta.json`
- Create: `scripts/verify-docs.mjs` (copy the dependency-free implementation from `skill://docs-maintenance/references/verify-docs.mjs`)

**Interfaces:**
- Produces a content root that the guide page in Task 2 can join without a docs app.
- Produces `node scripts/verify-docs.mjs --ladder` as the verification command used by Task 4.

- [ ] **Step 1: Create the content landing page**

  Write `content/docs/index.mdx` with frontmatter:

  ```mdx
  ---
  title: "Guilds documentation"
  description: "A player and operator guide to Guilds features, commands, progression, territory, and travel."
  ---
  ```

  Explain in prose that Guilds combines guild membership, claims, territories,
  progression, and travel. Link to `/docs/guilds-guide` after the explanation.
  Keep this page an aggregation; it is exempt from ladder labels.

- [ ] **Step 2: Create ordered navigation**

  Write `content/docs/meta.json` with valid JSON and this ordered page list:

  ```json
  {
    "pages": ["index", "guilds-guide"]
  }
  ```

- [ ] **Step 3: Install the docs verifier**

  Copy the complete Node-builtin verifier from the docs-maintenance reference to
  `scripts/verify-docs.mjs`. Do not add npm dependencies or a root package.
  The repository is content-only, so the verifier must be run directly from the
  repository root.

- [ ] **Step 4: Commit the content shell**

  Run:

  ```bash
  git add content/docs/index.mdx content/docs/meta.json scripts/verify-docs.mjs
  git commit -m "docs: add content-only documentation surface"
  ```

### Task 2: Draft the comprehensive Guilds guide

**Files:**
- Create: `content/docs/guilds-guide.mdx`
- Read for accuracy: `guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/BrigadierCommandRegistry.java`, all registered command classes, `TerritoryCommand.java`, `BuildingCommand.java`, `guilds-config.yml`, `techtree.yml`, `plugin.yml`, and the active `docs/living-specs/*.md` catalogs.

**Interfaces:**
- Produces the page listed by `content/docs/meta.json`.
- Uses only `/docs/...` links that resolve to the pages created in Task 1.

- [ ] **Step 1: Add frontmatter and status legend**

  Start the page with:

  ```mdx
  ---
  title: "Guilds player guide"
  description: "How Guilds works: the basics, everyday commands, progression, territory, travel, and current limitations."
  ---
  ```

  Follow with the idea section and a short status legend defining Implemented,
  Declared, not active, API/operator-only, and Unavailable or pending.

- [ ] **Step 2: Write the idea and basics levels**

  Add headings whose text includes `The idea`, `The basics`, and `basics`.
  Explain guild membership, roles, claims, plots, territories, governance,
  and progression before showing any command. In basics, show how a player
  understands current land, opens the guild map, joins or creates a guild, and
  distinguishes a guild plot from a territory zone.

- [ ] **Step 3: Write the Everyday level and command reference**

  Add a heading containing `Everyday`. Explain when to use the reachable forms
  of `/guild` (including `/g`, `/t`, and `/town`), `/plot`, `/guilds`, `/guildlevel`,
  `/perm`, `/tc`, `/alliance` (including `/n` and `/a`), `/quest`, `/guild map`,
  and `/territory building travel`. Include a compact table after the prose.
  Include role and permission notes in human language instead of dumping
  permission-node names into the opening explanation.

  The table must include, where reachable, guild lifecycle, claims/plots,
  map/chat, bank/storage, quest/level/progression, alliance, and facility
  travel commands. State that map commands fall back to a chat map without
  MapGUI and that travel is asynchronous and cancellable.

- [ ] **Step 4: Write the Advanced level**

  Open this section with:

  ```mdx
  <Callout type="info">
    Advanced — you do not need this to get started.
  </Callout>
  ```

  Then cover these subsections in prose and tables where useful:

  1. Guild levels, XP-only upgrades, tech points, one active project,
     prerequisites, and every configured tech-tree node in Infrastructure,
     Defense, Commerce, and Culture. Mark effect metadata that runtime does
     not apply as `Declared, not active`.
  2. Specializations and their role/level gates. List Mining, Trade Hub,
     Military, Arcane, and Agricultural. State that Arcane's declared
     enchanting values are not active gameplay effects.
  3. Territories, zones, protection, governance, influence, standing, upkeep,
     and operator-triggered invasions.
  4. Facilities and fast travel: waystones, guild crystals, teleport
     terminals, boats, airships, trading posts, storage, and banks; explain
     anchor placement, same-guild/alliance rules, tech prerequisites, costs,
     rewards, warm-up, cancellation, safe landing, protection, cooldowns,
     reservation expiry, and current-state rechecks.
  5. Alliances, governance forms, authority roles, and governance-derived
     ownership.
  6. Quests, resource contributions, service/API-only contracts, storage,
     Mint guild bank behavior, and the distinction from the legacy SQL balance.
  7. MapGUI, squaremap, WorldGuard, embedded API/editor, TLS/authentication,
     Vercel proxy, and PlaceholderAPI. State optional/disabled defaults and
     do not document Vault as supported.
  8. **Enchantments and enhancements:** vanilla enchanting only; enchanting
     blocks are protected/interactable according to territory rules; no custom
     enchant API, item, command, event, cost modifier, or chance modifier.

- [ ] **Step 5: Write practical meaning and limitations**

  Add a heading containing `What it means for you`. Explain role gates,
  persistence-before-memory behavior, failure/refund behavior where source
  supports it, optional integrations, and the difference between a configured
  description and active runtime behavior. Mention that live Paper/server
  smoke and database-gated paths may remain environment-dependent; do not
  claim a live scenario was observed.

- [ ] **Step 6: Add final command/status tables**

  Put any longer cheat sheets after the explanatory sections. Include a status
  table for intentionally absent or limited surfaces: custom enchantments,
  standalone `/techtree`, broadcast management, guild-permission mutations,
  contracts as commands, Vault, and any web/runtime features that are disabled
  or unverified by current source/spec evidence.

- [ ] **Step 7: Commit the guide**

  Run:

  ```bash
  git add content/docs/guilds-guide.mdx
  git commit -m "docs: publish comprehensive Guilds player guide"
  ```

### Task 3: Link the guide from the project README

**Files:**
- Modify: `README.md` near the existing `## Features` overview.

**Interfaces:**
- Adds a relative repository link to `content/docs/guilds-guide.mdx` without
  changing existing feature claims or setup instructions.

- [ ] **Step 1: Add one discoverable link**

  Add a short paragraph after the feature list:

  ```md
  For the complete player and operator guide, see
  [`content/docs/guilds-guide.mdx`](content/docs/guilds-guide.mdx).
  ```

  Do not rewrite the README overview or duplicate the entire guide.

- [ ] **Step 2: Commit the README link**

  Run:

  ```bash
  git add README.md
  git commit -m "docs: link Guilds player guide"
  ```

### Task 4: Verify documentation structure and source accuracy

**Files:**
- Read: `content/docs/index.mdx`, `content/docs/guilds-guide.mdx`,
  `content/docs/meta.json`, `scripts/verify-docs.mjs`, and the source paths
  named in Task 2.

**Interfaces:**
- Verifies the committed documentation surface; does not modify Java behavior.

- [ ] **Step 1: Run the structural and ladder gate**

  Run:

  ```bash
  node scripts/verify-docs.mjs --ladder
  ```

  Expected output begins with `verify-docs: OK (content-only, 2 pages)` and
  lists both MDX pages.

- [ ] **Step 2: Check repository whitespace and focused diff**

  Run:

  ```bash
  git diff --check master...HEAD
  git status --short
  ```

  Expected: no whitespace errors; only the documentation commits are present
  in the isolated worktree. Unrelated local-master changes remain outside this
  worktree.

- [ ] **Step 3: Perform the final accuracy review**

  Confirm the guide does not claim custom enchantments, active Arcane perks,
  Vault support, reachable standalone `/techtree`, or implemented command
  surfaces that the registry leaves as help-only. Confirm the complete 19-node
  `techtree.yml` catalog is represented with inactive effects labeled, and
  confirm every command described is registered or explicitly marked limited.

- [ ] **Step 4: Commit any verification-only wording corrections**

  If the accuracy review finds a wording issue, edit only the affected MDX or
  README lines, rerun the gate, and commit:

  ```bash
  git add content/docs README.md
  git commit -m "docs: clarify Guilds guide behavior"
  ```
