# Map — Living Spec

> Status: active  
> Last updated: 2026-08-24  
> Related: `docs/superpowers/specs/2026-08-08-admin-map-editor-design.md`

## Intent

Give operators and players a **live cartographic view** of territories, zones,
and influence, and (planned) an **admin-only draw editor** for boundaries —
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
- Admin Leaflet SPA (planned): polygon/paint/rect/erase on chunk medium;
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
| Editor static + session | planned under `guilds-common/.../web` + resources |
| Live tiles | external squaremap plugin |

- Keep layer paint cheap; don’t block tick thread on heavy geometry.
- Editor auth: paste API token → HttpOnly session cookie (short TTL).
- Config for squaremap tile base URL when editor lands.

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

### Open on the current surface

- [ ] Standing visualization layer? (product — default no)
- [ ] Document tile URL assumptions for reverse-proxy deploys

### Current notes

Admin editor is **approved design**, not implemented — track under Next.

## Next

- [ ] Admin map editor v1 (Leaflet SPA, chunk tools, session auth) per design
- [ ] Config keys: editor enable, tile base URL, session TTL
- [ ] Delete-shape confirm UX

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
