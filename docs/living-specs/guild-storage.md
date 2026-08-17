# Guild Storage — Living Spec

> Status: active
> Last updated: 2026-08-17
> Related: `docs/superpowers/specs/2026-08-06-guild-storage-design.md`

## Intent

The separate Mint cash guild bank is implemented under the economy domain. It is a namespaced account (`guild:<guildId>`) for coins only; it does not provide item storage and does not alter this item-bank design.

One **shared, guild-owned item bank** accessed through a virtual UI at a
registered `FacilityType.STORAGE` location inside the guild’s territory.

New World–inspired (settlement-bound, permissioned) rather than a literal copy
of Company Treasury: this is an **item** bank, not coin-only, and not personal
housing sheds.

Success looks like: residents deposit/withdraw by role; capacity expands with
progression; inventories never live on the facility metadata record; crash-safe
item moves.

## Boundaries

### In scope (when built)

- Virtual shared inventory owned by guild id.
- Access at `SettlementFacility` of type `STORAGE` (location from **economy** facility directory).
- Role thresholds: deposit / withdraw / manage (defaults: member / officer / mayor).
- Capacity model and expansion hooks.
- Persistence of item stacks (serialization) in Postgres.
- Permission checks via guild membership/roles.

### Out of scope / non-goals

- Physical chests, trophy rooms, housing sheds.
- Shop listings or market matching.
- Per-player personal ender-style storage as the guild bank.
- Putting inventory fields onto `SettlementFacility`.

## Invariants

1. **Facility is location only** — bank state is a separate aggregate.
2. **Guild-owned**, not resident-owned tabs that transfer with mayorship bugs.
3. Only residents of the owning guild access (unless future explicit grants).
4. Item move + DB write atomicity: no delete-from-player without durable bank credit (and reverse).
5. Capacity enforced server-side, not only in client UI.

## Implementation guidance

- Depend on **economy** facility registry for location resolve; do not fork facility types without catalog update.
- Keep serialization versioned; prefer Paper item serialization APIs in `paper` module only.
- Coordinate permissions with **guilds** role model and **governance** land interact rules at the facility block.
- Write living-spec checkboxes as tasks split when implementation starts.

### Do not

- Store NBT blobs in territory JSONB documents.
- Allow access outside the STORAGE facility without a deliberate product change.
- Implement extra tabs or remote access without promoting those horizons first.

## Current

### Capability

- [x] `FacilityType.STORAGE` and facility directory exist (**economy**)
- [x] Guild storage document + `guild_storage_banks` schema
- [x] `GuildStorageEngine` open/save/deposit/withdraw with exclusive viewer
- [x] Virtual 54-slot chest UI at an active STORAGE anchor
- [x] `/guilds building create storage` then right-click chest/barrel
- [x] `/guild storage` at the targeted storage block
- [x] Default ranks: any resident may deposit; assistant/mayor may withdraw
- [ ] Paper runtime smoke: deposit, officer withdraw, second viewer denied
- [ ] Capacity progression
- [ ] Permission thresholds config per guild

### Current notes

v1 is a single `General` 54-slot chest. Item bytes are Paper
`ItemStack.serializeAsBytes` payloads (`paper-itemstack-bytes-v1`). Facility
records stay location-only. Cash Mint banks are unchanged.

## Next

- [ ] Operator/player docs for storage vs `/guild bank`
- [ ] Runtime smoke on a Paper server with a governed territory

## Future

- [ ] Tabs / categories
- [ ] Logs / audit of withdrawals
- [ ] Cross-guild shared warehouses
- [ ] Integration with contracts material sourcing

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-06 | Virtual bank at STORAGE facility | Settlement-bound access without chest spam |
| 2026-08-06 | Facility remains metadata only | Clear ownership boundary with economy |
| 2026-08-17 | Implement v1 item bank at STORAGE anchors | User asked for the items half of guild banks |
| 2026-08-17 | Residents deposit; officers withdraw | Matches design defaults without new permission flags |

## Open questions

- [x] Capacity units: slots, stacks, or weight? — v1 uses 54 logical slots
- [ ] Interaction with plot private storage?
- [x] Promote to Next this milestone or later? — promoted 2026-08-17
