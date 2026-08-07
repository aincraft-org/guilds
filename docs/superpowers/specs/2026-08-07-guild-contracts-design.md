# Guild Contracts Design

## Goal

Provide a service API so a guild that needs materials for its next level-up can post a
**contract** that other guilds fulfill, with the payment held in escrow (New World-style)
and released to the fulfiller on completion.

This is a service-API + persistence deliverable only. No commands, no events, no
inventory/material sourcing on the fulfiller's side.

## Current state

Guilds level up by collecting materials (`DIAMOND`, `GOLD_INGOT`, etc.) into their
`upgrade_progress` map, stored as simple JSON in the `guilds.upgrade_progress` column
(format `{"KEY":value}`). Level definitions carry per-level resource costs. Guilds hold a
`double balance` (currency); `GuildService.updateGuildBalance(guildName, amount)` mutates it
atomically.

Note: the existing `/townlevel deposit` path calls `GuildService.updateGuild(...)`, which
does not persist `upgrade_progress`. This contract service persists the contribution itself
via direct SQL inside its transaction, so a fulfilled contract is durably recorded.

## Decisions

### Escrow model (New World-style)

Posting a contract debits the contracting guild's `balance` immediately and holds the payment
in escrow. Fulfillment releases the escrow to the fulfiller; cancellation refunds the escrow
to the contracting guild. A contract cannot be posted unless the contracting guild can afford
the payment at post time, so no shortfall is possible at fulfillment time.

### Data model — `guild_contracts` table

| column | type | notes |
|---|---|---|
| `id` | TEXT PK | UUID |
| `contracting_guild_id` | TEXT FK→guilds | guild needing materials |
| `resource_type` | TEXT | e.g. `DIAMOND` |
| `amount` | INTEGER | total units needed |
| `payment` | REAL | total to fulfiller (held in escrow) |
| `filled` | INTEGER | units fulfilled so far |
| `status` | TEXT | `OPEN` / `FULFILLED` / `CANCELLED` |
| `fulfilled_by_guild_id` | TEXT | fulfiller |
| `created_at` | TEXT | timestamp |
| `fulfilled_at` | TEXT | timestamp |

A contract is one-shot: a single fulfiller supplies the full `amount` and the contract moves
to `FULFILLED`.

### Service API

```java
ContractResult createContract(String contractingGuildId, String resourceType, int amount, double payment);
List<GuildContract> getOpenContracts();
List<GuildContract> getContractsForGuild(String guildId);
Optional<GuildContract> getContract(String contractId);
FulfillResult fulfillContract(String contractId, String fulfillingGuildId);
boolean cancelContract(String contractId, String contractingGuildId);
```

`createContract`:
- validates the contracting guild exists and can afford `payment`;
- atomically debits `payment` from its `balance` (escrow held in the row);
- inserts the contract with `status=OPEN`, `filled=0`.

`fulfillContract`:
- rejects when the contract is not `OPEN`, or `fulfillingGuildId` equals the contracting guild;
- atomically:
  1. adds `amount` to the contracting guild's `upgrade_progress` for `resource_type`
     (direct SQL `UPDATE guilds SET upgrade_progress = ...`);
  2. credits `payment` from escrow to the fulfilling guild's `balance`;
  3. marks the contract `FULFILLED`, sets `fulfilled_by_guild_id` and `fulfilled_at`;
- all inside `DatabaseManager.executeTransactionWithResult` so nothing half-applies.
- returns a status distinguishing `FULFILLED`, `NOT_FOUND`, `NOT_OPEN`, `SELF_FULFILL`.

`cancelContract`:
- only the contracting guild may cancel; only `OPEN` contracts can be cancelled;
- refunds the escrowed `payment` to the contracting guild's `balance`, marks `CANCELLED`.

### Guards

- a guild cannot fulfill its own contract;
- a non-`OPEN` contract cannot be fulfilled or cancelled;
- a contract cannot be posted if the contracting guild cannot afford the payment.

### What the API does NOT do

- No commands, no events.
- No inventory or material verification on the fulfiller's side — the caller decides where
  the fulfilling guild's materials come from. The API only credits the target guild's
  `upgrade_progress` and settles the escrow.

## Verification

Tests must prove:

1. creating a contract debits the contracting guild's balance and persists the contract;
2. creating a contract fails when the guild cannot afford the payment;
3. fulfilling a contract credits the fulfiller's balance, adds to the contracting guild's
   `upgrade_progress`, and marks the contract `FULFILLED`;
4. a guild cannot fulfill its own contract;
5. a non-`OPEN` contract cannot be fulfilled or cancelled;
6. cancelling an `OPEN` contract refunds the escrow to the contracting guild;
7. root tests/build pass.

## Out of scope

- commands, arguments, or permission checks;
- broadcast/event hooks;
- fulfiller-side inventory or material sourcing;
- partial fulfillment across multiple guilds;
- market pricing or price discovery.