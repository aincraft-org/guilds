# Guilds web frontend

The `web/` directory is a separate React/Vite client for Guilds. It serves the
public project page at `/` and the authenticated territory editor at `/editor`.
It does not replace the Paper-hosted static editor or the embedded Guilds HTTP
API. Those remain available at the configured Paper `web.port`.

## Local development

Requirements: Node.js 20 or newer and npm.

```bash
npm ci
npm run dev -- --host 127.0.0.1
```

The Vite server serves the client, but it does not provide the Vercel function
that proxies `/api/*`. Use `vercel dev` with the server-only configuration when
you need to exercise the complete local proxy path:

```bash
GUILDS_API_ORIGIN=http://127.0.0.1:8765 vercel dev
```

The Paper server must be running on that address, with its `web.*` settings and
SQL database configured. The existing Paper editor remains at
`http://127.0.0.1:8765/editor/`.

Run the frontend checks from this directory:

```bash
npm test -- --run
npm run typecheck
npm run build
npm run preview
```

`npm run build` writes the deployable static files to `dist/`. The API function
under `api/` is deployed by Vercel separately from those static assets.

## Vercel configuration

Create or link the Vercel project with **`web/` as its project root**. Use:

- Build command: `npm run build`
- Output directory: `dist`
- Install command: `npm ci`
- Production domain: `guilds.mintychochip.dev`

`vercel.json` routes `/api/*` to the server-side catch-all function before the
filesystem and SPA fallback routes. `/editor` is therefore a client route and
must continue to resolve to `index.html` on a direct refresh.

Set exactly one server-side deployment variable:

```text
GUILDS_API_ORIGIN=https://paper-api.example.net
```

Use an HTTPS origin with no username, password, query, or fragment. The value
is read only by `api/proxy.ts`; it is not a `VITE_*` variable and must not be
referenced by browser code. Do not print it in deployment logs or expose it in
responses. A missing or invalid value produces a controlled proxy error.

The operator's Guilds token is a different secret. It is configured as
`web.api-token` on the Paper server and is entered in the editor login form. It
must never be placed in Vercel environment variables, a `VITE_*` variable, the
repository, generated assets, a URL, browser storage, or application state. The
client sends it once to `POST /api/session`, then clears the input. The backend
owns the HttpOnly `GUILDS_SESSION` cookie.

## Paper reverse proxy settings

The public browser origin must use HTTPS. Configure the Paper server behind the
proxy so it trusts the proxy's forwarded scheme and host:

```yaml
web:
  enabled: true
  bind: 127.0.0.1
  port: 8765
  public-base-url: "https://guilds.mintychochip.dev"
  trust-proxy: true
  api-token: "configure-on-the-Paper-server"
```

Keep the token out of source control and deployment configuration. The Vercel
proxy forwards the controlled `X-Forwarded-Proto` and `X-Forwarded-Host` values.
The backend uses the HTTPS scheme to add `Secure` to
`GUILDS_SESSION; HttpOnly; Path=/; SameSite=Strict`. Do not disable TLS or
`trust-proxy` for the public deployment.

The squaremap tile base URL remains a Paper `web.squaremap-tile-base-url`
setting. It is returned by `/api/meta` for the map; it is not an API origin
secret.

## Custom domain and DNS

In the Vercel project, add `guilds.mintychochip.dev` under **Settings →
Domains**. Apply the exact DNS record and target Vercel displays for the
project. Do not substitute a guessed A, CNAME, or proxy record. Wait for the
Vercel domain check and certificate to become valid before treating production
as reachable.

The deployment is not complete merely because a local build succeeds. Account,
DNS, Paper API, and a safe operator credential are required for the production
checks below. If any access is unavailable, report that access as a blocker
rather than claiming an authenticated deployment.

## Safe production verification

Use a browser against both URLs:

```text
https://guilds.mintychochip.dev/
https://guilds.mintychochip.dev/editor
```

Verify all of the following without printing or recording a token:

1. `/` loads its assets over HTTPS, and `/editor` loads both directly and after
   a hard refresh.
2. Desktop and narrow layouts retain keyboard focus, readable tool labels, and
   usable world/inspector drawers. Theme preference persists only as the
   `guilds-theme` light/dark value.
3. Browser network requests use same-origin relative `/api/*` paths with
   credentials included. The configured Paper origin is not visible to the
   browser response or generated bundle.
4. With an operator-approved credential, submit the login form and confirm the
   token input is cleared after success or failure. Confirm JavaScript cannot
   read the `GUILDS_SESSION` cookie and that its response flags are
   `HttpOnly`, `Path=/`, `SameSite=Strict`, and `Secure` on HTTPS.
5. Load a known territory, make a non-destructive draft change, and inspect the
   `PUT /api/territories/:id` payload. Save only against an operator-approved
   test territory. Confirm server validation failures preserve the draft.
6. Exercise logout and an expired-session `401`; both return to login while an
   unsaved in-memory draft remains visible. Confirm no token appears in local
   storage, session storage, cookies readable by JavaScript, URLs, console
   output, or built asset text.

The existing Paper static editor/API can be verified independently at its
configured host and port; the Vercel client is only a new frontend and proxy
boundary.

## Current deployment state

The frontend and proxy are locally buildable, but production deployment is
still blocked: this checkout has no Vercel CLI/account or DNS-management
access, and `guilds.mintychochip.dev` does not currently resolve from the
verification environment. A Vercel project, upstream Paper API, DNS record,
and safe operator credential are still required. Do not describe
`guilds.mintychochip.dev` as deployed or authenticated until the production
checks above have passed.
