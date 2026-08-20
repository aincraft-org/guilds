# Vercel React Web Design

Date: 2026-08-19
Status: Approved

## Background

Guilds already exposes an embedded JDK HTTP API and a vanilla JavaScript map
editor from the Paper process. The existing editor lives under
`common/src/main/resources/org/aincraft/guilds/territory/web/static/editor/`.
The new public web surface will instead be a React application deployed at
`https://guilds.mintychochip.dev` on Vercel. The Paper server remains the
source of territory data, validation, persistence, and sessions.

The visual reference is `https://jobs.mintychochip.dev`: a narrow, typography-led
site using Geist, dark and light themes, a near-black default background,
subtle borders, restrained monochrome controls, and compact navigation. Guilds
will reuse that design vocabulary without copying product-specific content or
page structure.

## Goals

- Add a React index page at `/` for the Guilds project.
- Add a React territory editor at `/editor`.
- Deploy the application to Vercel at `guilds.mintychochip.dev`.
- Proxy `/api/*` through the Vercel deployment to the existing Guilds API so the
  browser sees one origin.
- Preserve the existing API's token-to-session flow and territory contracts.
- Keep the API token out of source, Vercel public environment variables, and the
  compiled browser bundle.
- Preserve the existing Paper-hosted editor and API while the Vercel client is
  introduced; the React application is a separate frontend, not a replacement
  backend.

## Non-goals

- Reimplementing territory validation or persistence in Vercel.
- Storing the operator API token in the browser, bundle, or a `VITE_*` variable.
- Changing the Guilds API's session-cookie policy.
- Server-side rendering, user accounts, role-based access, collaborative editing,
  autosave, or undo history.
- Replacing squaremap or generating map tiles in the React application.
- Removing the current Paper-hosted static editor in this change.

## Decisions

| Topic | Choice |
|---|---|
| Frontend | React, TypeScript, Vite |
| Hosting | Vercel |
| Public origin | `https://guilds.mintychochip.dev` |
| API topology | Same-origin `/api/*` Vercel proxy to the Guilds server |
| Auth | Operator-entered API token exchanged for existing HttpOnly session |
| Styling | Shared design tokens modeled after `jobs.mintychochip.dev` |
| Map | Leaflet with the existing squaremap tile metadata |
| State | Local React state with explicit save and dirty tracking |
| API schema | Existing Guilds territory/session endpoints and JSON documents |

## Architecture

```text
Browser
  ├── GET /                         React index page
  ├── GET /editor                   React editor route
  └── /api/*                        same-origin requests
          │
          ▼
Vercel deployment
  ├── static Vite assets
  ├── SPA route fallback
  └── private upstream proxy configuration
          │
          ▼
Guilds Paper HTTP server
  ├── /api/session                  token exchange / logout
  ├── /api/meta                     editor metadata and tile configuration
  ├── /api/territories*             territory documents
  └── registry → SQL persistence    authoritative validation and storage
```

The upstream API origin is deployment configuration and must not be exposed as a
`VITE_*` value. If Vercel rewrites cannot satisfy the required proxy behavior for
the chosen upstream, a minimal Vercel server-side function will forward method,
body, cookies, and required headers. It must not log credentials or response
cookies. The browser contract remains same-origin `/api/*` either way.

## Components

### Application shell

The shell owns routing, theme preference, page metadata, and shared design
tokens. Routes are `/` and `/editor`; unknown client routes render a small
not-found state rather than silently opening the editor.

Theme preference uses local storage only for the non-sensitive light/dark value.
The default follows the reference site's dark palette and honors the operating
system preference on first visit.

### Index page

The index uses a centered content column with a maximum width near 720px:

- Compact header with Guilds wordmark, theme control, Editor, Docs, Discord, and
  GitHub links.
- Hero with a short Paper-plugin label, large `Guilds` heading, concise product
  statement, primary `Open editor` action, and secondary source/docs action.
- Capability sections for territory authoring, guild systems, SQL persistence,
  and squaremap integration.
- A security note explaining that the editor exchanges an operator-provided
  token for a server session and does not retain the token.
- Compact footer mirroring the header's external links.

External URLs must come from existing project metadata where available. Missing
links are omitted rather than represented by placeholders.

### Editor route

The editor is a full-height workspace using the same typography, colors, border
radii, and theme behavior as the index:

- **Header:** product link, connection/session state, theme toggle, logout.
- **Left panel:** world selector; territory and nested-zone tree; create actions.
- **Center:** Leaflet map, squaremap tiles, chunk grid, coordinate readout, and
  Select, Polygon, Paint, Rectangle, and Erase tools.
- **Right panel:** selected entity ID, name, zone type, geometry counts,
  validation state, and delete action.
- **Action area:** explicit Save with visible dirty and in-flight states.

Desktop uses three columns. Narrow layouts keep the map usable and expose the
left and right panels as accessible drawers. Tool buttons retain text labels or
accessible names; color is never the only indication of selection or error.

The React implementation ports observable behavior from the existing editor's
`api.js`, `model.js`, `map.js`, `tools.js`, and `ui.js`. It does not create a
second territory schema or validation engine.

## Data and Save Flow

1. The editor requests `/api/meta` and an authenticated territory endpoint.
2. A `401` opens the login dialog without discarding an existing in-memory draft.
3. After login, the editor loads the current territory documents.
4. Editing mutates a client-side draft and marks its parent territory dirty.
5. Save sends one complete territory document through the existing `PUT`
   endpoint. Zone changes are saved as part of their parent territory document.
6. Server validation remains authoritative. Success clears that document's dirty
   state; failure preserves the draft and exposes the returned error.
7. Territory deletion uses the existing `DELETE` endpoint after confirmation.
8. Navigation or reload while dirty invokes a leave warning.

There is no autosave and no optimistic claim that a server write succeeded.

## Authentication and Session Handling

```text
Browser                      Vercel proxy                   Guilds API
   | POST /api/session {token}   |                              |
   |---------------------------->| POST /api/session {token}    |
   |                             |----------------------------->|
   |                             |   Set-Cookie: GUILDS_SESSION |
   |<-----------------------------------------------------------|
   | HttpOnly session cookie; token value is not retained       |
   |                                                             |
   | GET/PUT /api/territories with cookie                        |
   |------------------------------------------------------------>|
```

The frontend accepts the token only in the login form and submits it over HTTPS
to `POST /api/session`. It clears the input after the request and never writes
the token to local storage, session storage, logs, analytics, URL parameters, or
application state beyond the active request.

The backend's existing cookie contract is preserved: `GUILDS_SESSION` with
`HttpOnly`, `Path=/`, and `SameSite=Strict`; `Secure` is added when the API
recognizes the request as HTTPS/proxy-secure. The proxy must therefore forward
the request information the backend uses to identify HTTPS. Login may use the
existing accepted body/header forms, but the React client uses the JSON body and
does not send a persistent authorization header afterward.

Logout calls `DELETE /api/session`, after which the editor returns to the login
state. `401` responses caused by expiry do the same while preserving unsaved
in-memory drafts.

## Error Handling

| Condition | Behavior |
|---|---|
| Login rejected | Keep dialog open; show generic invalid-token message; clear token input |
| Session expired | Reopen login; keep in-memory draft |
| API unavailable | Persistent connection banner with retry; editor stays read-only where needed |
| Territory validation failure | Show server message near Save; preserve draft |
| Tile failure | Keep controls and chunk grid available; show basemap-unavailable notice |
| Save in flight | Disable duplicate save/delete actions |
| Delete conflict/not found | Show result and refresh authoritative list |
| Missing deployment configuration | Vercel API route returns a controlled server error without exposing upstream details |

## Vercel Configuration

- Build command and output directory follow the Vite defaults selected by the
  repository package manager.
- `/editor` and static client routes fall back to the Vite index document.
- `/api/*` is handled before SPA fallback and forwards to a private configured
  Guilds API origin.
- `guilds.mintychochip.dev` is attached to the Vercel project and HTTPS is
  required.
- Only server-side Vercel configuration may know the upstream origin. The Guilds
  API token is not a Vercel environment variable because operators provide it at
  login.
- Deployment cannot be declared complete until the named production URL is
  reachable and both `/` and `/editor` have been exercised there. If Vercel
  credentials or DNS access are unavailable, implementation may be complete but
  deployment remains explicitly blocked.

## Testing and Verification

### Automated

- Component/behavior tests cover route rendering, theme persistence, login
  submission and token clearing, `401` relogin behavior, draft preservation,
  dirty-state transitions, save payloads, and API error display.
- Pure editor model and chunk-geometry behavior receives unit coverage matching
  the existing JavaScript editor's observable contracts.
- Production build must pass without embedding a test token or upstream secret.
- Existing Guilds web/session tests remain green because the backend contract is
  unchanged.

### Browser verification

- Run the production frontend locally and exercise `/` and `/editor` at desktop
  and narrow viewports.
- Confirm theme switching, keyboard focus, panel behavior, login error handling,
  territory loading through a controlled API response, draft editing, and save
  failure/success states.
- Inspect browser storage to confirm no token is retained.
- Inspect network requests to confirm the client uses same-origin `/api/*` paths.

### Production verification

- Open `https://guilds.mintychochip.dev/` and `/editor` in a browser.
- Confirm assets and SPA routing load directly and after refresh.
- Confirm HTTPS and the production domain.
- Exercise session login against the proxied production API, verify the
  `GUILDS_SESSION` cookie flags, then load and save a non-destructive test change
  only when an operator-provided credential and safe target are available.
- Without access to Vercel, DNS, the upstream API, or a safe credential,
  production deployment/integration is reported as blocked rather than inferred
  from a local build.

## Security Invariants

- No API token in git, generated assets, public environment variables, browser
  storage, logs, analytics, or URLs.
- Session cookie remains HttpOnly and is managed by the backend.
- The browser communicates only with the HTTPS public origin.
- Proxy responses do not expose the configured upstream origin or credentials.
- Server-side registry validation and persistence remain the authority for every
  mutation.
- The Vercel frontend is not described as a deployed authenticated integration
  until the actual production API flow has been verified.
