const HOP_BY_HOP_HEADERS: Record<string, true> = {
  connection: true,
  'keep-alive': true,
  'proxy-authenticate': true,
  'proxy-authorization': true,
  'proxy-connection': true,
  te: true,
  trailer: true,
  'transfer-encoding': true,
  upgrade: true,
  // These are message framing or proxy-routing headers. Let fetch derive them.
  'content-length': true,
  host: true,
};

const CONTROLLED_REQUEST_HEADERS: Record<string, true> = {
  authorization: true,
  'x-api-token': true,
  forwarded: true,
  'x-forwarded-for': true,
  'x-forwarded-host': true,
  'x-forwarded-port': true,
  'x-forwarded-proto': true,
};

const CONFIGURATION_FAILURE = JSON.stringify({ error: 'proxy_not_configured' });
const UPSTREAM_FAILURE = JSON.stringify({ error: 'upstream_unavailable' });

type HeaderSource = Headers & {
  getSetCookie?: () => string[];
  raw?: () => Record<string, string[]>;
};

type ProxyRequestInit = RequestInit & { duplex?: 'half' };

/**
 * Parse and normalize the configured API origin.
 *
 * The API routes themselves are rooted at `/api`; the configured pathname is
 * normalized here so the returned URL is stable for callers that inspect it.
 * Credentials and URL components that could change request semantics are
 * deliberately rejected.
 */
export function normalizeUpstream(raw: string): URL {
  if (
    typeof raw !== 'string' ||
    raw.length === 0 ||
    /[\u0000-\u0020]/.test(raw) ||
    !/^https?:\/\/[^/?#]+(?:[/?#]|$)/i.test(raw)
  ) {
    throw new TypeError('GUILDS_API_ORIGIN must be an HTTP(S) origin');
  }

  let upstream: URL;
  try {
    upstream = new URL(raw);
  } catch {
    throw new TypeError('GUILDS_API_ORIGIN must be an HTTP(S) origin');
  }

  if (
    (upstream.protocol !== 'http:' && upstream.protocol !== 'https:') ||
    upstream.username !== '' ||
    upstream.password !== '' ||
    upstream.search !== '' ||
    upstream.hash !== '' ||
    raw.includes('?') ||
    raw.includes('#')
  ) {
    throw new TypeError('GUILDS_API_ORIGIN must be an HTTP(S) origin');
  }

  // URL.pathname always starts with '/', including for a bare origin. Remove
  // duplicate trailing slashes before adding the one separator used below.
  upstream.pathname = `${upstream.pathname.replace(/\/+$/, '')}/`;
  return upstream;
}

function connectionHeaderTokens(headers: Headers): Set<string> {
  const tokens = new Set<string>();
  const connection = headers.get('connection');
  if (!connection) return tokens;

  for (const token of connection.split(',')) {
    const normalized = token.trim().toLowerCase();
    if (normalized) tokens.add(normalized);
  }
  return tokens;
}

function copyEndToEndHeaders(source: Headers, controlled: boolean): Headers {
  const destination = new Headers();
  const connectionTokens = connectionHeaderTokens(source);

  for (const [name, value] of source) {
    const normalizedName = name.toLowerCase();
    if (
      HOP_BY_HOP_HEADERS[normalizedName] === true ||
      connectionTokens.has(normalizedName) ||
      (controlled && CONTROLLED_REQUEST_HEADERS[normalizedName] === true)
    ) {
      continue;
    }
    destination.append(name, value);
  }
  return destination;
}

function encodePathSegment(segment: string): string {
  if (segment === '.' || segment === '..') {
    throw new TypeError('Invalid API path');
  }
  return encodeURIComponent(segment);
}

function upstreamTarget(origin: URL, request: Request, path: string[]): URL {
  const suffix = path.map(encodePathSegment).join('/');
  const target = new URL(`/api/${suffix}`, origin);
  target.search = new URL(request.url).search;
  return target;
}

function requestInit(request: Request, headers: Headers): ProxyRequestInit {
  const init: ProxyRequestInit = {
    method: request.method,
    headers,
    redirect: 'manual',
    signal: request.signal,
  };

  // GET and HEAD bodies are forbidden by fetch. For streaming methods, the
  // Node runtime requires duplex to be explicit when passing request.body.
  if (request.method !== 'GET' && request.method !== 'HEAD' && request.body) {
    init.body = request.body;
    init.duplex = 'half';
  }
  return init;
}

function splitCombinedSetCookie(value: string): string[] {
  const cookies: string[] = [];
  let start = 0;
  let quoted = false;
  let escaped = false;

  for (let index = 0; index < value.length; index += 1) {
    const character = value[index];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (character === '\\' && quoted) {
      escaped = true;
      continue;
    }
    if (character === '"') {
      quoted = !quoted;
      continue;
    }
    if (character !== ',' || quoted) continue;

    let next = index + 1;
    while (next < value.length && /\s/.test(value[next])) next += 1;
    const equals = value.indexOf('=', next);
    const semicolon = value.indexOf(';', next);
    if (equals < next || (semicolon >= 0 && equals > semicolon)) continue;

    const cookieName = value.slice(next, equals);
    if (!/^[^\s;,=]+$/.test(cookieName)) continue;

    cookies.push(value.slice(start, index).trim());
    start = next;
    index = next - 1;
  }

  const last = value.slice(start).trim();
  if (last) cookies.push(last);
  return cookies;
}

function setCookieValues(source: HeaderSource): string[] {
  if (typeof source.getSetCookie === 'function') {
    return source.getSetCookie();
  }

  if (typeof source.raw === 'function') {
    const raw = source.raw();
    if (Array.isArray(raw['set-cookie'])) return raw['set-cookie'];
  }

  const combined = source.get('set-cookie');
  return combined ? splitCombinedSetCookie(combined) : [];
}

function responseHeaders(source: Headers): Headers {
  const destination = new Headers();
  const connectionTokens = connectionHeaderTokens(source);
  const cookies = setCookieValues(source as HeaderSource);

  for (const [name, value] of source) {
    const normalizedName = name.toLowerCase();
    if (
      normalizedName === 'set-cookie' ||
      HOP_BY_HOP_HEADERS[normalizedName] === true ||
      connectionTokens.has(normalizedName)
    ) {
      continue;
    }
    destination.append(name, value);
  }

  for (const cookie of cookies) destination.append('set-cookie', cookie);
  return destination;
}

function configurationError(): Response {
  return new Response(CONFIGURATION_FAILURE, {
    status: 500,
    headers: {
      'cache-control': 'no-store',
      'content-type': 'application/json; charset=utf-8',
    },
  });
}

function upstreamError(): Response {
  return new Response(UPSTREAM_FAILURE, {
    status: 502,
    headers: {
      'cache-control': 'no-store',
      'content-type': 'application/json; charset=utf-8',
    },
  });
}

/** Forward one relative /api route to the configured server-side API. */
export async function proxyRequest(
  request: Request,
  path: string[],
  env: NodeJS.ProcessEnv = process.env,
): Promise<Response> {
  const configuredOrigin = env.GUILDS_API_ORIGIN;
  if (!configuredOrigin) return configurationError();

  let target: URL;
  let publicUrl: URL;
  try {
    const upstream = normalizeUpstream(configuredOrigin);
    publicUrl = new URL(request.url);
    target = upstreamTarget(upstream, request, path);
  } catch {
    return configurationError();
  }

  const headers = copyEndToEndHeaders(request.headers, true);
  headers.set('x-forwarded-host', publicUrl.host);
  headers.set('x-forwarded-proto', publicUrl.protocol.slice(0, -1));

  try {
    const upstreamResponse = await fetch(target.toString(), requestInit(request, headers));
    return new Response(upstreamResponse.body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: responseHeaders(upstreamResponse.headers),
    });
  } catch {
    return upstreamError();
  }
}
