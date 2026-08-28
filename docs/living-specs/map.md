# Map — Living Spec

> Status: active  
> Last updated: 2026-08-27
> Related: `docs/superpowers/specs/2026-08-08-admin-map-editor-design.md`

## Intent

Give operators and players a **live cartographic view** of territories, zones,
and influence, and an **admin-only draw editor** for boundaries —
without forking squaremap or turning the REST port into a second Minecraft map
stack.

Success looks like: squaremap layers refresh soon after registry/influence
changes; soft-depend means no squaremap ⇒ warn and skip; admin editor uses
chunk tools + existing API validation.

## Boundaries

### In scope

- `TerritorySquaremapBridge` layers: territories, zones, influence.
- Chunk outline helpers; ~5s refresh loop.
- Soft dependency on squaremap (Paper).
- Admin Leaflet editor: polygon/paint/rect/erase on chunk medium;
  squaremap tiles as basemap; saves via **web-api** territory routes.

### Out of scope / non-goals

- Forking or patching squaremap jar/frontend.
- Player-facing draw tools (v1 admin only).
- Auto-save, multi-admin CRDT merge, deep undo stacks (v1).
- In-game wand claim tools (possible later hybrid).
- Playwright E2E of gestures / tile visual regression (v1 non-goal).

## Invariants

1. **Display-only** public layers never mutate domain state.
2. Editor saves go through **same validation** as API/registry (no new spatial rules).
3. Soft-depend: missing squaremap must not fail plugin enable of core systems.
4. Chunk medium for editor tools (not free block polygons as primary UX).

## Implementation guidance

| Piece | Location |
|-------|----------|
| squaremap bridge | `guilds-paper/.../squaremap` |
| Editor static + session | `guilds-common/.../web` + resources |
| React/Vite editor + Vercel proxy | `web/` |
| Live tiles | external squaremap plugin |

- Keep layer paint cheap; don’t block tick thread on heavy geometry.
- Editor auth: paste API token → HttpOnly session cookie (short TTL).
- Configure the Paper tile base URL; the React editor consumes it from `/api/meta`.

### Testing

- Bridge no-ops cleanly without squaremap.
- Layer data reflects registry after reload (integration/manual acceptable).
- Editor: unit-test pure geometry helpers; rely on API tests for persistence.

### Do not

- Duplicate territory storage in the map layer.
- Require squaremap for CI unit tests of domain.

## Current

### Capability (shipped)

- [x] Territory outlines layer
- [x] Zone fills by type with tooltips
- [x] Influence contest layer with owner/attacker tooltips
- [x] Periodic refresh (~5s)
- [x] Soft-depend wiring + `runServer` squaremap pin in README
- [x] Local `runServer` squaremap is the ../squaremap fork (Rust sidecar), not pinned `bf7da8d` + incomplete registry patch
- [x] Paper-hosted static editor with the existing territory API
- [x] React/Vite editor implementation with same-origin session proxy
- [x] Session auth, draft tracking, explicit saves, and delete confirmation
- [x] Editor configuration keys: enable, tile base URL, and session TTL

### Open on the current surface

- [ ] Standing visualization layer? (product — default no)
- [x] Document tile URL assumptions for reverse-proxy deploys

### Current notes

The Paper static editor and the React/Vite editor implementation are shipped.
The React deployment at `guilds.mintychochip.dev` remains unverified until a
Vercel project, DNS record, upstream API, and safe operator credential are
available.

## Next

- [ ] Deploy and verify the Vercel frontend and custom domain
- [ ] Verify the production session-cookie flags and a safe non-destructive save

## Future

- [ ] In-game wand hybrid authoring
- [ ] Collaborative multi-admin editing
- [ ] Player-facing read-only custom web map beyond squaremap

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| (core) | squaremap for public live map | Mature tiles; soft-depend |
| 2026-08-08 | Separate admin editor SPA, not inside squaremap UI | No fork; clear auth boundary |
| 2026-08-08 | Chunk drawing medium | Matches Minecraft claim mental model |

## Open questions

- [ ] Ship editor in same release train as scope-aware governance or after?
- [ ] Expose facility markers on map layers?
