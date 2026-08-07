# Guild-Owned Storage — Design Spec

**Date:** 2026-08-06
**Status:** Draft for review
**Parent:** `../azothmc/docs/superpowers/plans/2026-08-05-azothmc-sdd-phased-roadmap.md` (P4)

## 1. Decision

Add one shared, guild-owned item bank. It is not a collection of trophies,
chests, or physical storage blocks.

The bank is accessed through a virtual guild-storage UI at a registered
`FacilityType.STORAGE` location inside the guild's territory. The bank is
owned by the guild, not by an individual resident. Only residents of the
owning guild may access it.

Default role thresholds are configurable per guild:

- **Deposit:** Member and above
- **Withdraw:** Assistant/officer and above
- **Manage tabs and capacity:** Mayor/leader only

The existing `SettlementFacility` remains location metadata. Inventory state,
access policy, and item serialization are not added to that record.

## 2. New World parity

New World provides personal connected storage sheds and a shared Company
Treasury for coin, but not a shared company item bank. This design uses the
Company Treasury's settlement-bound and permissioned access pattern while
adding the shared item-bank behavior required by AzothMC.

The result is New World-inspired rather than a literal copy:

- settlement-bound access;
- guild-owned progression and permissions;
- virtual storage instead of physical chests;
- expandable capacity;
- no housing, trophy, or chest-placement system.

### Reference material

- New World returning-player guide: https://www.newworld.com/en-us/news/articles/rise-of-the-angry-earth-new-and-returning-player-guide
- New World Brimstone Sands release notes: https://www.newworld.com/en-us/game/releases/brimstone-sands-release

## 3. Scope and ownership

### Territory owns

- guild-bank identity and guild ownership;
- storage facility/location validation;
- resident and rank authorization;
- tabs, capacity, expansion state, and policy thresholds;
- PostgreSQL transactions and audit records;
- location restrictions and access-denied responses.

### Items owns

- item instance identity and schema;
- serialization/deserialization of item instances;
- gear score, perks, sockets, bind state, durability, and future item rules;
- conversion between a player inventory item and an opaque storage payload.

Territory stores item payloads opaquely and never reconstructs or edits item
semantics. The cross-plugin boundary is a versioned item-storage codec/service;
missing or incompatible codecs fail closed without deleting stored payloads.

### Not in scope

- physical chest or trophy blocks;
- personal storage tabs;
- alliance-wide shared storage;
- public storage access;
- weight, death, durability, or combat rules;
- trading-post inventory or listings;
- item serialization owned by territory.

## 4. Access model

A player can open the bank only when all conditions hold:

1. the player is a resident of the owning guild;
2. the access location resolves to a registered `FacilityType.STORAGE`;
3. the facility's territory resolves to the same guild;
4. the player is at the facility's configured access block/location;
5. the player's rank meets the requested operation threshold.

The initial command/UI entry point is `/town storage` (with the existing `t`
alias), but it is valid only at an allowed storage facility. There is no
remote bank command and no hearthstone shortcut into storage.

A failed check returns a stable denial reason and performs no database or item
mutation. Alliance membership does not grant access by default.

## 5. Storage layout

The bank is one logical item pool. Tabs are capacity pages, not separate banks
or separate permission domains.

The v1 layout is:

- one `General` tab;
- a configurable initial capacity, defaulting to 54 logical slots;
- expansion unlocks additional logical tabs of the same size;
- all tabs use the same guild policy thresholds.

The slot count is a virtual UI/storage capacity, not a physical chest size.
The data model supports named tabs and per-tab metadata so future category tabs
or per-tab permissions do not require a storage rewrite, but those features are
not required for v1.

Capacity expansion exposes a quote/purchase hook. The economy implementation
and pricing remain a separate integration; storage must not debit balances
itself.

## 6. PostgreSQL contract

Guild storage uses the shared mandatory PostgreSQL database. No SQLite or JSON
fallback is introduced.

The implementation adds idempotent tables separate from `facilities`:

```sql
CREATE TABLE IF NOT EXISTS guild_storage_banks (
    guild_id TEXT PRIMARY KEY,
    schema_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS guild_storage_tabs (
    guild_id TEXT NOT NULL,
    tab_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    capacity_slots INTEGER NOT NULL CHECK (capacity_slots > 0),
    unlocked BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (guild_id, tab_id),
    UNIQUE (guild_id, ordinal),
    FOREIGN KEY (guild_id)
        REFERENCES guild_storage_banks (guild_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS guild_storage_slots (
    guild_id TEXT NOT NULL,
    tab_id TEXT NOT NULL,
    slot_index INTEGER NOT NULL CHECK (slot_index >= 0),
    item_schema TEXT NOT NULL,
    item_payload JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (guild_id, tab_id, slot_index),
    FOREIGN KEY (guild_id, tab_id)
        REFERENCES guild_storage_tabs (guild_id, tab_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS guild_storage_policies (
    guild_id TEXT PRIMARY KEY,
    deposit_rank TEXT NOT NULL,
    withdraw_rank TEXT NOT NULL,
    manage_rank TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (guild_id)
        REFERENCES guild_storage_banks (guild_id)
        ON DELETE CASCADE
);


CREATE TABLE IF NOT EXISTS guild_storage_audit (
    id BIGSERIAL PRIMARY KEY,
    guild_id TEXT NOT NULL,
    actor_uuid UUID NOT NULL,
    operation TEXT NOT NULL,
    tab_id TEXT NOT NULL,
    slot_index INTEGER,
    item_schema TEXT,
    item_fingerprint TEXT,
    facility_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
```

The first bank creation transaction creates the bank, the default policy, and
the `general` tab. Slot writes and audit writes commit atomically.

Deposit, withdraw, and expansion mutations lock the bank/tab rows (or use an
optimistic version check) and commit before the UI reports success. A failed
item-codec operation or SQL transaction leaves the previous slot unchanged.

## 7. Service boundary

The territory-facing service is conceptually:

```text
open(UUID actor, WorldLocation location) -> StorageView or AccessDenied
 deposit(UUID actor, StorageAddress, OpaqueItemPayload) -> StorageResult
withdraw(UUID actor, StorageAddress) -> OpaqueItemPayload or StorageResult
quoteExpansion(UUID actor, GuildId) -> ExpansionQuote
applyExpansion(UUID actor, GuildId, ExpansionReceipt) -> StorageResult
```

`OpaqueItemPayload` contains a codec/schema identifier and the serialized item
instance supplied by the items layer. Territory may validate the schema id and
payload size, but does not inspect gear score, perks, durability, or stack rules.

The service resolves the owning guild through the existing territory/facility
and governance APIs. It must not duplicate alliance or block-protection logic.

## 8. Error and recovery rules

- PostgreSQL unavailable: storage operations fail closed and report a storage
  error; no in-memory substitute is authoritative.
- Invalid facility, foreign guild, insufficient rank, or wrong location: deny
  without touching storage.
- Invalid item payload: reject the operation and preserve the stored payload.
- Concurrent slot mutation: return a retryable conflict; never overwrite a
  newer slot silently.
- Audit failure: roll back the item mutation rather than creating an
  unaudited transfer.
- Startup loads metadata only as needed; corrupt JSONB or incompatible payloads
  remain visible as unavailable records rather than being dropped.

## 9. Verification contract

Tests must cover:

- guild-only access and denial for non-residents/alliance members;
- default member deposit, assistant withdraw, mayor management thresholds;
- configurable threshold changes;
- exact facility/location enforcement;
- no access through a non-storage facility or remote command;
- tab/capacity expansion hook;
- opaque payload deposit/withdraw round trip;
- transaction rollback on codec, audit, and database failure;
- concurrent slot conflict behavior;
- PostgreSQL restart round trip and idempotent schema initialization.

The broader P4 exit gate remains separate: combat PvP flag/death behavior belongs
to `azoth`, item semantics belong to `items`, and guild storage must not absorb
those domains.
