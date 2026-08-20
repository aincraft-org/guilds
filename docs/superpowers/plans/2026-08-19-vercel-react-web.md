# Vercel React Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and deploy a React index page and territory editor at `guilds.mintychochip.dev`, using the existing Guilds session and territory API through a secure same-origin Vercel proxy.

**Architecture:** Create a focused Vite/React/TypeScript workspace under `web/`. The browser uses only same-origin `/api/*`; a Vercel serverless catch-all validates a private `GUILDS_API_ORIGIN`, forwards requests and cookies, and supplies the reverse-proxy headers required for secure backend cookies. The React editor ports the existing vanilla editor's API, immutable draft model, Leaflet coordinate system, geometry tools, and explicit-save behavior without changing the Paper API.

**Tech Stack:** React 19, TypeScript 5, Vite 7, React Router 7, Leaflet 1.9.4, Vitest, Testing Library, jsdom, Vercel Node functions, Playwright/browser smoke verification.

## Global Constraints

- Public production origin is exactly `https://guilds.mintychochip.dev`.
- Browser API calls use relative `/api/*` URLs with `credentials: 'include'`.
- Never store or embed the operator API token in source, `VITE_*` variables, generated assets, URLs, logs, analytics, local storage, or session storage.
- Preserve the backend cookie contract: `GUILDS_SESSION; HttpOnly; Path=/; SameSite=Strict`, with `Secure` when proxy headers report HTTPS.
- Keep the existing Paper-hosted editor and backend API unchanged.
- Reuse the existing territory JSON schema; server validation and SQL persistence remain authoritative.
- No SSR, accounts, roles, collaboration, autosave, or undo history.
- Use explicit save and preserve drafts across session expiry and API errors.
- The named Vercel deployment is incomplete until `/` and `/editor` are verified on the production URL; missing account, DNS, upstream, or operator credentials must be reported as a blocker rather than inferred from local success.

## File Structure

```text
web/
  package.json                       scripts and pinned browser dependencies
  package-lock.json                  reproducible npm dependency graph
  tsconfig.json                      strict application TypeScript
  tsconfig.node.json                 Vite/test/server config TypeScript
  vite.config.ts                     React build and Vitest jsdom configuration
  index.html                         Vite document shell and metadata
  vercel.json                        API-function and SPA route precedence
  api/[...path].ts                   private upstream reverse proxy
  src/main.tsx                       browser bootstrap and router
  src/App.tsx                        route table and shared error boundary
  src/styles/tokens.css              jobs-inspired themes and design tokens
  src/styles/global.css              reset, typography, controls, accessibility
  src/components/SiteHeader.tsx      shared nav and theme control
  src/components/ThemeProvider.tsx   system preference and local theme value
  src/pages/HomePage.tsx             index content
  src/editor/types.ts                API and draft domain types
  src/editor/api.ts                  same-origin session/territory client
  src/editor/model.ts                immutable drafts, dirty state, serialization
  src/editor/geometry.ts             chunk and squaremap coordinate helpers
  src/editor/useEditorState.ts       loading, auth, selection, saves, deletes
  src/editor/EditorPage.tsx          editor composition and responsive drawers
  src/editor/EditorMap.tsx           Leaflet lifecycle, layers, tiles, interactions
  src/editor/Toolbar.tsx             tool selection and save state
  src/editor/TerritoryTree.tsx       world and territory/zone navigation
  src/editor/Inspector.tsx           selected entity fields and destructive action
  src/editor/LoginDialog.tsx         ephemeral token exchange
  src/editor/editor.css              full-screen editor layout
  src/test/setup.ts                  DOM/Leaflet test setup
  src/**/*.test.ts(x)                observable component and model contracts
  README.md                          local build, proxy, Vercel, DNS, auth handoff
```

---

### Task 1: React workspace, theme, and index page

**Files:**
- Create: `web/package.json`
- Create: `web/package-lock.json`
- Create: `web/tsconfig.json`
- Create: `web/tsconfig.node.json`
- Create: `web/vite.config.ts`
- Create: `web/index.html`
- Create: `web/src/main.tsx`
- Create: `web/src/App.tsx`
- Create: `web/src/components/ThemeProvider.tsx`
- Create: `web/src/components/SiteHeader.tsx`
- Create: `web/src/pages/HomePage.tsx`
- Create: `web/src/styles/tokens.css`
- Create: `web/src/styles/global.css`
- Create: `web/src/test/setup.ts`
- Test: `web/src/pages/HomePage.test.tsx`
- Test: `web/src/components/ThemeProvider.test.tsx`

**Interfaces:**
- Produces: `ThemeProvider`, `useTheme(): { theme: 'dark' | 'light'; toggleTheme(): void }`.
- Produces: route contract `/` → `HomePage`, `/editor` → lazy `EditorPage` placeholder until Task 5.
- Consumes: no prior web interfaces.

- [ ] **Step 1: Initialize the package manifest and lockfile**

Create `web/package.json` with scripts `dev`, `build`, `preview`, `test`, and `typecheck`. Pin React, React DOM, React Router, Leaflet, Vite, TypeScript, Vitest, jsdom, `@vitejs/plugin-react`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, and required type packages. Run:

```bash
cd web && npm install
```

Expected: `package-lock.json` is created and `npm audit` does not change the requested dependency graph.

- [ ] **Step 2: Write failing landing and theme tests**

In `HomePage.test.tsx`, render through a memory router and assert:

```tsx
expect(screen.getByRole('heading', { level: 1, name: 'Guilds' })).toBeVisible();
expect(screen.getByRole('link', { name: /open editor/i })).toHaveAttribute('href', '/editor');
expect(screen.getByText(/operator-provided token/i)).toBeVisible();
```

In `ThemeProvider.test.tsx`, seed no storage, toggle the accessible theme button, and assert:

```tsx
expect(document.documentElement).toHaveAttribute('data-theme', 'light');
expect(localStorage.getItem('guilds-theme')).toBe('light');
```

- [ ] **Step 3: Run the tests and verify the red state**

Run:

```bash
cd web && npm test -- --run src/pages/HomePage.test.tsx src/components/ThemeProvider.test.tsx
```

Expected: FAIL because the app and providers do not exist.

- [ ] **Step 4: Implement the shell and landing page**

Implement `ThemeProvider` with a `'guilds-theme'` local-storage key used only for the theme. On first load, derive the theme from `matchMedia('(prefers-color-scheme: light)')`; apply `data-theme` to `document.documentElement`; expose `toggleTheme`.

Implement a `SiteHeader` with a Guilds home link, Editor link, theme button, and only real external links confirmed from repository metadata. Build `HomePage` with the approved hero, capabilities, security note, and footer. Use these root design values in `tokens.css`:

```css
:root {
  --content-width: 720px;
  --bg: #121212;
  --surface: #1a1a1a;
  --border: #2a2a2a;
  --border-strong: #3a3a3a;
  --text: #ededed;
  --text-secondary: #a3a3a3;
  --text-muted: #737373;
  --radius: 12px;
  --font-sans: "Geist Sans", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --font-mono: "Geist Mono", ui-monospace, SFMono-Regular, Menlo, monospace;
}
```

Define an equivalent accessible light theme. Use locally available/system fallback fonts rather than introducing a runtime font CDN dependency.

- [ ] **Step 5: Run focused tests, typecheck, and production build**

Run:

```bash
cd web && npm test -- --run src/pages/HomePage.test.tsx src/components/ThemeProvider.test.tsx
cd web && npm run typecheck
cd web && npm run build
```

Expected: all tests PASS, TypeScript reports no errors, and Vite writes `web/dist/`.

- [ ] **Step 6: Browser-check the index page**

Start `npm run dev -- --host 127.0.0.1` through the process supervisor. Open `/` at 1440×1000 and 390×844. Verify the content column, theme toggle, keyboard focus, real links, no horizontal overflow, and visual similarity to the restrained jobs reference.

- [ ] **Step 7: Commit the independently working landing page**

```bash
git add web/package.json web/package-lock.json web/tsconfig.json web/tsconfig.node.json web/vite.config.ts web/index.html web/src/main.tsx web/src/App.tsx web/src/components web/src/pages/HomePage.tsx web/src/pages/HomePage.test.tsx web/src/styles web/src/test/setup.ts
git commit -m "feat: add Guilds React landing page"
```

---

### Task 2: Secure Vercel same-origin API proxy

**Files:**
- Create: `web/api/[...path].ts`
- Create: `web/api/proxy.ts`
- Create: `web/api/proxy.test.ts`
- Create: `web/vercel.json`
- Modify: `web/package.json`
- Modify: `web/tsconfig.node.json`

**Interfaces:**
- Produces: `normalizeUpstream(raw: string): URL` accepting only `http:` or `https:` origins with no credentials, query, or hash.
- Produces: `proxyRequest(request: Request, path: string[], env?: NodeJS.ProcessEnv): Promise<Response>`.
- Produces: catch-all Vercel route `/api/:path*` before SPA fallback.
- Consumes: the backend API paths unchanged; server-only `GUILDS_API_ORIGIN`.

- [ ] **Step 1: Write failing proxy security tests**

Use standard `Request` objects and mock `globalThis.fetch`. Cover:

```ts
expect(() => normalizeUpstream('javascript:alert(1)')).toThrow();
expect(() => normalizeUpstream('https://user:pass@api.example.test')).toThrow();
expect(() => normalizeUpstream('https://api.example.test/base?secret=1')).toThrow();
```

Assert a proxied `POST /api/session`:

- targets `${GUILDS_API_ORIGIN}/api/session`;
- preserves method, JSON body, `content-type`, `cookie`, and `user-agent`;
- removes incoming `host`, `authorization` to Vercel itself, `x-forwarded-host`, and `x-forwarded-proto` before setting controlled values;
- sets `x-forwarded-host: guilds.mintychochip.dev` and `x-forwarded-proto: https` from the public request URL;
- forwards every upstream `set-cookie` value without concatenating cookie semantics;
- returns controlled `500 {"error":"proxy_not_configured"}` with no origin details when `GUILDS_API_ORIGIN` is absent.

- [ ] **Step 2: Run the proxy tests and verify failure**

Run:

```bash
cd web && npm test -- --run api/proxy.test.ts
```

Expected: FAIL because proxy functions are undefined.

- [ ] **Step 3: Implement the server-only proxy**

`normalizeUpstream` must reject credentials, query, and fragment, strip a trailing slash from the pathname, and never return the configured origin to clients.

`proxyRequest` builds the upstream URL with:

```ts
const upstreamUrl = new URL(`/api/${path.map(encodeURIComponent).join('/')}`, upstream);
upstreamUrl.search = publicUrl.search;
```

Forward safe end-to-end headers and request body for non-GET/HEAD methods. Remove hop-by-hop headers (`connection`, `keep-alive`, `proxy-authenticate`, `proxy-authorization`, `te`, `trailer`, `transfer-encoding`, `upgrade`) and overwrite forwarded host/protocol values. Use `redirect: 'manual'`. Return a generic `502 {"error":"upstream_unavailable"}` on network failure without logging request headers or bodies.

The Vercel handler adapts its request to `proxyRequest`. `vercel.json` routes `/api/(.*)` to the function first, static assets normally, then all remaining paths to `/index.html`.

- [ ] **Step 4: Run proxy tests, typecheck, and build**

Run:

```bash
cd web && npm test -- --run api/proxy.test.ts
cd web && npm run typecheck
cd web && npm run build
```

Expected: PASS; the browser build contains no `GUILDS_API_ORIGIN` string value from test fixtures.

- [ ] **Step 5: Inspect the generated bundle for secret channels**

Run a repository search over `web/dist` for `GUILDS_API_ORIGIN`, `web.api-token`, `X-Api-Token`, and fixture token values. Expected: no embedded environment value or credential; endpoint copy may mention only the user-facing session flow.

- [ ] **Step 6: Commit the proxy as a separate security boundary**

```bash
git add web/api web/vercel.json web/package.json web/package-lock.json web/tsconfig.node.json
git commit -m "feat: proxy Guilds API through Vercel"
```

---

### Task 3: Typed API client and immutable editor model

**Files:**
- Create: `web/src/editor/types.ts`
- Create: `web/src/editor/api.ts`
- Create: `web/src/editor/api.test.ts`
- Create: `web/src/editor/model.ts`
- Create: `web/src/editor/model.test.ts`
- Create: `web/src/editor/geometry.ts`
- Create: `web/src/editor/geometry.test.ts`

**Interfaces:**
- Produces: `ApiError extends Error { status: number; body: unknown }`.
- Produces: `guildsApi` with `getMeta`, `login`, `logout`, `listTerritories`, `putTerritory`, and `deleteTerritory`.
- Produces: `EditorState`, `TerritoryDraft`, `ZoneDraft`, `Boundary`, `Selection`, `EditorMeta` types.
- Produces immutable functions `loadTerritories`, `markDirty`, `clearDirty`, `updateTerritory`, `updateZone`, `toggleChunk`, `addChunkRect`, `eraseChunk`, `removePolygonVertexNear`, `toApiDocument`, `hasGeometry`, `boundaryStats`, and `newTerritoryId`.
- Produces geometry functions `blockToChunk`, `snapBlockToChunkCorner`, `bukkitToSquaremapWorld`, `toLeafletPoint`, and `fromLeafletPoint`.

- [ ] **Step 1: Write failing API client tests**

Mock `fetch` and assert exact requests:

```ts
await guildsApi.login('operator-secret');
expect(fetch).toHaveBeenCalledWith('/api/session', expect.objectContaining({
  method: 'POST',
  credentials: 'include',
  body: JSON.stringify({ token: 'operator-secret' }),
}));
```

Assert every call is relative and credentialed, non-JSON error bodies become `ApiError`, and `401` retains `status === 401`. Assert the API module never accesses `localStorage` or `sessionStorage`.

- [ ] **Step 2: Write failing model and geometry tests**

Port observable invariants from the current `model.js` and `map.js`:

- negative coordinates floor toward negative infinity;
- chunk corners multiply by 16;
- squaremap world mapping covers overworld, nether, end, and namespaced fallback;
- load/updates never mutate API input or prior `EditorState` objects;
- toggling a chunk twice restores the original boundary;
- rectangle addition is inclusive;
- polygon vertex removal honors threshold;
- `hasGeometry` requires three polygon vertices or one chunk;
- serialization removes editor-only `dirty` state while preserving optional `governedByGuildId` and `government` fields.

- [ ] **Step 3: Run tests and verify failure**

Run:

```bash
cd web && npm test -- --run src/editor/api.test.ts src/editor/model.test.ts src/editor/geometry.test.ts
```

Expected: FAIL because the editor modules do not exist.

- [ ] **Step 4: Define exact API and editor types**

Define:

```ts
export interface BlockPoint { x: number; z: number }
export interface ChunkPoint { x: number; z: number }
export interface Boundary { polygon: BlockPoint[]; chunks: ChunkPoint[] }
export type ZoneType = 'WILDERNESS' | 'CLAIMABLE';
export interface ZoneDocument { id: string; name: string; type: ZoneType; priority: number; boundary: Boundary }
export interface TerritoryDocument {
  id: string;
  name: string;
  world: string;
  defaultZoneType: ZoneType;
  boundary: Boundary;
  zones: ZoneDocument[];
  governedByGuildId?: string;
  government?: unknown;
}
export interface TerritoryListResponse { territories: TerritoryDocument[] }
export interface EditorMeta {
  authRequired: boolean;
  squaremapTileBaseUrl: string;
  sessionTtlSeconds: number;
  secure: boolean;
}
export interface Selection { territoryId: string; zoneId?: string }
```

Draft types add `dirty: boolean` only to territory drafts; `EditorState` contains `territories`, `dirtyIds: ReadonlySet<string>`, and `selection`.

- [ ] **Step 5: Implement client, immutable model, and geometry helpers**

Use a single internal `request<T>` that always sets `credentials: 'include'`, parses JSON or text safely, and throws `ApiError`. Login token exists only as the argument and serialized request body. Implement pure cloning/updating helpers; never mutate a previous state or caller-owned document.

Use squaremap's existing transform at native zoom 3:

```ts
const SCALE = 1 / 2 ** 3;
export const toLeafletPoint = (x: number, z: number) => ({ lat: -z * SCALE, lng: x * SCALE });
export const fromLeafletPoint = (lat: number, lng: number) => ({ x: lng / SCALE, z: -lat / SCALE });
```

- [ ] **Step 6: Run focused tests and typecheck**

Run:

```bash
cd web && npm test -- --run src/editor/api.test.ts src/editor/model.test.ts src/editor/geometry.test.ts
cd web && npm run typecheck
```

Expected: all PASS.

- [ ] **Step 7: Commit the editor domain boundary**

```bash
git add web/src/editor/types.ts web/src/editor/api.ts web/src/editor/api.test.ts web/src/editor/model.ts web/src/editor/model.test.ts web/src/editor/geometry.ts web/src/editor/geometry.test.ts
git commit -m "feat: add typed territory editor model"
```

---

### Task 4: Editor session and draft state controller

**Files:**
- Create: `web/src/editor/useEditorState.ts`
- Create: `web/src/editor/useEditorState.test.tsx`

**Interfaces:**
- Produces: `useEditorState(api = guildsApi): EditorController`.
- `EditorController` exposes `status: 'loading' | 'login-required' | 'ready' | 'offline'`, `meta`, `state`, `selection`, `error`, `savingIds`, and actions `login`, `logout`, `retry`, `select`, `createTerritory`, `createZone`, `updateSelected`, `updateBoundary`, `save`, `removeSelected`.
- Consumes: Task 3 API/model/types functions.

- [ ] **Step 1: Write failing controller behavior tests**

Use `renderHook` with a fake `GuildsApi`. Cover:

- meta with `authRequired: false` loads territories directly;
- list `401` produces `login-required`;
- `login(token)` calls login then clears the caller-controlled input through the dialog contract in Task 5;
- edits replace state immutably and mark the parent territory dirty;
- save uses `toApiDocument`, clears dirty only on success, and preserves draft/error on failure;
- a save `401` opens login while preserving state;
- logout invokes the API and sets `login-required` when auth is required;
- `beforeunload` is registered only while `dirtyIds.size > 0`;
- duplicate saves for the same ID are ignored while in flight.

- [ ] **Step 2: Run the controller tests and verify failure**

Run:

```bash
cd web && npm test -- --run src/editor/useEditorState.test.tsx
```

Expected: FAIL because `useEditorState` does not exist.

- [ ] **Step 3: Implement the controller state machine**

Keep one reducer-controlled state so auth transitions do not discard drafts. Bootstrap order is `getMeta()` then `listTerritories()`. Treat only `ApiError(401)` as login-required; other failures become offline/error states. Save one territory document at a time and refresh the saved document from the returned response only when it has the expected shape; otherwise retain the draft and clear dirty after the confirmed 2xx.

Creation defaults:

```ts
const territory: TerritoryDraft = {
  id: newTerritoryId(name), name, world: selectedWorld,
  defaultZoneType: 'WILDERNESS', boundary: emptyBoundary(), zones: [], dirty: true,
};
const zone: ZoneDraft = {
  id: newTerritoryId(name), name, type: 'CLAIMABLE', priority: 0, boundary: emptyBoundary(),
};
```

Block save when the selected territory boundary lacks geometry; expose a specific validation message instead of issuing a request.

- [ ] **Step 4: Run focused tests and typecheck**

Run:

```bash
cd web && npm test -- --run src/editor/useEditorState.test.tsx
cd web && npm run typecheck
```

Expected: PASS.

- [ ] **Step 5: Commit the editor state machine**

```bash
git add web/src/editor/useEditorState.ts web/src/editor/useEditorState.test.tsx
git commit -m "feat: manage authenticated editor drafts"
```

---

### Task 5: Responsive editor interface and login flow

**Files:**
- Create: `web/src/editor/EditorPage.tsx`
- Create: `web/src/editor/Toolbar.tsx`
- Create: `web/src/editor/TerritoryTree.tsx`
- Create: `web/src/editor/Inspector.tsx`
- Create: `web/src/editor/LoginDialog.tsx`
- Create: `web/src/editor/editor.css`
- Create: `web/src/editor/EditorPage.test.tsx`
- Modify: `web/src/App.tsx`

**Interfaces:**
- Produces: complete `/editor` route excluding the Leaflet canvas internals supplied by Task 6.
- Produces: `MapSurfaceProps { controller: EditorController; activeTool: EditorTool; onToolChange(tool): void }` seam.
- Consumes: `useEditorState` and shared theme/header interfaces.

- [ ] **Step 1: Write failing editor component tests**

Render with a fake controller and assert:

- login dialog is accessible with `role="dialog"`, labelled token input, error region, and submit button;
- submitting passes the token once, immediately clears the controlled input after the promise settles, and no token appears in rendered text;
- ready state renders world selector, territory/zone tree, named tools, Save, session status, and inspector;
- selecting a zone updates the inspector;
- dirty and saving states are textual, not color-only;
- `401` transition leaves draft tree visible behind the modal;
- narrow mode panel buttons have `aria-expanded` and close with Escape;
- delete requires a confirmation dialog before invoking `removeSelected`.

- [ ] **Step 2: Run component tests and verify failure**

Run:

```bash
cd web && npm test -- --run src/editor/EditorPage.test.tsx
```

Expected: FAIL because components do not exist.

- [ ] **Step 3: Implement accessible editor composition**

Compose the header, left tree, center map seam, toolbar, right inspector, banners, and dialogs. `LoginDialog` owns only a transient controlled `token` string:

```tsx
async function submit(event: FormEvent) {
  event.preventDefault();
  const submitted = token;
  try { await onLogin(submitted); }
  finally { setToken(''); }
}
```

Never pass the token to global state. Use buttons for all tools, visible focus rings, `aria-pressed` on active tool, `aria-live="polite"` for connection/save state, and `role="alert"` for errors.

Use CSS grid columns `minmax(220px, 280px) minmax(0, 1fr) minmax(220px, 280px)`. Below 900px, panels become fixed drawers controlled by header buttons; the map remains full width. Respect `prefers-reduced-motion`.

- [ ] **Step 4: Wire `/editor` in the route table**

Replace the placeholder with a lazy import of `EditorPage`. Keep `/` and a deterministic not-found route. Ensure refreshing `/editor` works with Vercel SPA routing from Task 2.

- [ ] **Step 5: Run focused tests, typecheck, and build**

Run:

```bash
cd web && npm test -- --run src/editor/EditorPage.test.tsx
cd web && npm run typecheck
cd web && npm run build
```

Expected: PASS.

- [ ] **Step 6: Browser-check editor shell with controlled API routes**

Start Vite and use browser request interception for `/api/meta` and `/api/territories`. Verify desktop and mobile layouts, login rejection, token input clearing, drawers, focus order, dirty labels, confirmation dialog, and no browser storage values besides `guilds-theme`.

- [ ] **Step 7: Commit the complete non-map editor UI**

```bash
git add web/src/App.tsx web/src/editor/EditorPage.tsx web/src/editor/Toolbar.tsx web/src/editor/TerritoryTree.tsx web/src/editor/Inspector.tsx web/src/editor/LoginDialog.tsx web/src/editor/editor.css web/src/editor/EditorPage.test.tsx
git commit -m "feat: add responsive territory editor UI"
```

---

### Task 6: Leaflet map, boundary rendering, and geometry tools

**Files:**
- Create: `web/src/editor/EditorMap.tsx`
- Create: `web/src/editor/EditorMap.test.tsx`
- Create: `web/src/editor/mapController.ts`
- Create: `web/src/editor/mapController.test.ts`
- Modify: `web/src/editor/EditorPage.tsx`
- Modify: `web/src/editor/editor.css`
- Modify: `web/package.json`
- Modify: `web/package-lock.json`

**Interfaces:**
- Produces: `EditorTool = 'select' | 'polygon' | 'paint' | 'rect' | 'erase'`.
- Produces: `EditorMap` consuming selected boundary, all visible territories, world, tile base URL, active tool, and immutable callbacks `onBoundaryChange` / `onCoordinateChange`.
- Produces: `createMapController(element, options): MapController` with `setViewModel`, `setTool`, and `destroy` methods.
- Consumes: Task 3 geometry/model helpers and Task 5 `MapSurfaceProps`.

- [ ] **Step 1: Write failing map-controller tests**

Mock Leaflet factory methods and test lifecycle and behavior, not pixel output:

- controller creates `L.map` with `L.CRS.Simple`, zoom 3, min 0, max 5, no attribution, and `preferCanvas`;
- tile URL is `${base}/tiles/${bukkitToSquaremapWorld(world)}/{z}/{x}_{y}.png` with size 512 and max native zoom 3;
- replacing world/tile base removes the old layer;
- selected and unselected boundaries render to distinct layer groups;
- polygon clicks append chunk-corner-snapped vertices;
- paint toggles one chunk per entered chunk while dragging;
- rectangle drag adds the inclusive chunk rectangle;
- erase removes a chunk first, otherwise removes a nearby vertex;
- `destroy()` unregisters listeners and removes the map.

- [ ] **Step 2: Run map tests and verify failure**

Run:

```bash
cd web && npm test -- --run src/editor/mapController.test.ts src/editor/EditorMap.test.tsx
```

Expected: FAIL because map components do not exist.

- [ ] **Step 3: Implement the imperative Leaflet controller**

Keep Leaflet objects out of React state. `mapController.ts` owns the map, tile layer, shape/draft/grid groups, and pointer listeners. Cap chunk-grid work using the existing 80×80 visible-cell threshold. Use non-interactive layers except active draft handles. Track painted chunk keys per drag to prevent repeated toggles from move events.

Tool semantics:

```text
select   pan map and select rendered shape
polygon click chunk corner → append block vertex
paint    pointer drag → toggle each entered whole chunk once
rect     pointer down/up → inclusive addChunkRect
erase    click chunk → eraseChunk; otherwise removePolygonVertexNear(..., 8)
```

The erase tool name remains `erase` in code and UI. A tile error threshold of five displays the basemap warning while leaving the grid and editing active.

- [ ] **Step 4: Implement the React lifecycle wrapper**

`EditorMap.tsx` creates one controller in an effect tied only to the container, destroys it on unmount, and sends changing view-model/tool values through controller methods. Avoid rebuilding the Leaflet map on each draft edit.

- [ ] **Step 5: Wire the real map into `EditorPage`**

Pass current world, meta tile URL, selected boundary, territory layers, active tool, and immutable boundary callbacks. Display block/chunk coordinates and basemap status in accessible live regions.

- [ ] **Step 6: Run focused tests, typecheck, and build**

Run:

```bash
cd web && npm test -- --run src/editor/mapController.test.ts src/editor/EditorMap.test.tsx src/editor/EditorPage.test.tsx
cd web && npm run typecheck
cd web && npm run build
```

Expected: PASS.

- [ ] **Step 7: Exercise tools in a real browser**

With controlled API and tile routes, draw a polygon, paint chunks, create a rectangle, erase a chunk/vertex, switch selections, and save. Inspect the outbound JSON and confirm block vertices are multiples of 16 and chunk coordinates remain integer chunk coordinates. Confirm the map remains usable when tiles return 404.

- [ ] **Step 8: Commit map behavior with its tests**

```bash
git add web/src/editor/EditorMap.tsx web/src/editor/EditorMap.test.tsx web/src/editor/mapController.ts web/src/editor/mapController.test.ts web/src/editor/EditorPage.tsx web/src/editor/editor.css web/package.json web/package-lock.json
git commit -m "feat: add chunk-based Leaflet editor tools"
```

---

### Task 7: Deployment documentation and complete verification

**Files:**
- Create: `web/README.md`
- Modify: `.gitignore`
- Modify: `README.md`

**Interfaces:**
- Produces: exact local, Vercel, DNS, proxy, and production verification instructions.
- Consumes: all prior tasks and the existing Paper `web.*` configuration.

- [ ] **Step 1: Document local and production configuration**

`web/README.md` must document:

- `npm ci`, `npm run dev`, `npm test -- --run`, `npm run typecheck`, `npm run build`;
- server-only `GUILDS_API_ORIGIN` with an HTTPS production example containing no credential;
- that `web.api-token` is entered by the operator and must never be placed in Vercel or `VITE_*` variables;
- backend `web.trust-proxy: true` and public HTTPS origin requirements so `Secure` is added;
- Vercel project root `web`, build command `npm run build`, output `dist`;
- attaching `guilds.mintychochip.dev` and adding the DNS record Vercel requests;
- safe production checks for `/`, `/editor`, direct refresh, session cookie flags, and a non-destructive editor save only with an operator-approved target;
- clear distinction between the new Vercel React client and the retained Paper static editor/API.

Update root `README.md` with a short Web frontend section linking `web/README.md`. Add `web/node_modules/`, `web/dist/`, `.vercel/`, and test/browser artifacts to `.gitignore` without ignoring source or lockfiles.

- [ ] **Step 2: Run the complete automated verification set**

Run:

```bash
cd web && npm ci
cd web && npm test -- --run
cd web && npm run typecheck
cd web && npm run build
./gradlew :common:test --tests 'org.aincraft.guilds.territory.web.*'
```

Expected: all frontend tests pass, TypeScript passes, production build succeeds, and existing backend web/session tests pass.

- [ ] **Step 3: Run final local production smoke verification**

Serve `web/dist` with the configured Vercel-compatible runtime or `vercel dev`, not the source dev server. Browser-check `/` and `/editor` at desktop and mobile widths. Verify direct refresh, theme persistence, relative API requests, login input clearing, drawer accessibility, editor tool behavior, save errors, and no token in cookies readable by JavaScript, local storage, session storage, console, or generated asset text.

- [ ] **Step 4: Commit documentation and deployment handoff**

```bash
git add web/README.md README.md .gitignore
git commit -m "docs: document Guilds web deployment"
```

- [ ] **Step 5: Deploy the Vercel project**

Authenticate with the existing Vercel account, link/create the intended project using `web/` as root, set server-only `GUILDS_API_ORIGIN`, and deploy production. Attach `guilds.mintychochip.dev`; apply the exact DNS record requested by Vercel if repository-accessible DNS tooling is available. Never store or print the API token.

Expected: Vercel reports a production deployment and the custom domain shows a valid HTTPS certificate. If account or DNS access is unavailable, record the precise missing access and leave deployment status blocked.

- [ ] **Step 6: Verify the named production URL**

Open:

```text
https://guilds.mintychochip.dev/
https://guilds.mintychochip.dev/editor
```

Verify both routes and direct refresh, inspect actual network requests for same-origin `/api/*`, and check the session cookie flags after an operator provides a safe credential. Exercise a non-destructive save only against an approved test territory. Capture exact HTTP/browser evidence. Do not claim authenticated production integration if safe credentials or upstream access are missing.

- [ ] **Step 7: Review commit boundaries and repository state**

Run `git status --short`, review every remaining path, and leave the user's pre-existing `.javadoc-backup/` untouched and uncommitted. Confirm each commit contains one logical change and no secret or generated deployment state.

## Plan Self-Review

- Spec coverage: index, editor, same-origin proxy, exact session contract, map tools, responsive behavior, tests, docs, deployment, DNS, and production verification each map to explicit tasks.
- Security: token lifecycle is ephemeral; proxy configuration is server-only; controlled forwarded headers enable the backend's existing Secure-cookie decision.
- Type consistency: `TerritoryDocument`, `TerritoryDraft`, `EditorState`, `EditorController`, `EditorTool`, and map seams are defined once and consumed by named later tasks.
- Scope: existing Paper editor/API remain unchanged; no SSR, account system, collaboration, autosave, or backend schema work is introduced.
