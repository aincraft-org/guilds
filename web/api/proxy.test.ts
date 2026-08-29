import { afterEach, describe, expect, it, vi } from 'vitest';
import { normalizeUpstream, proxyRequest } from './proxy';

const upstreamOrigin = 'https://api.example.test';

function cookieValues(headers: Headers): string[] {
  const source = headers as Headers & { getSetCookie?: () => string[] };
  return typeof source.getSetCookie === 'function'
    ? source.getSetCookie()
    : [headers.get('set-cookie') ?? ''];
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('normalizeUpstream', () => {
  it('normalizes a base pathname to one trailing slash', () => {
    expect(normalizeUpstream('https://api.example.test/service///').toString()).toBe(
      'https://api.example.test/service/',
    );
    expect(normalizeUpstream('http://api.example.test').pathname).toBe('/');
  });

  it.each([
    'ftp://api.example.test',
    'https://user:password@api.example.test',
    'https://api.example.test/service?secret=value',
    'https://api.example.test/service#fragment',
    'https:api.example.test',
  ])('rejects unsafe upstream value %s', (raw) => {
    expect(() => normalizeUpstream(raw)).toThrow(TypeError);
  });
});

describe('proxyRequest', () => {
  it('forwards the relative path, query, safe headers, and body', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response('{"ok":true}', {
        status: 201,
        headers: { 'content-type': 'application/json', 'x-upstream': 'yes' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const request = new Request('https://public.example.test/api/territories?view=all', {
      method: 'POST',
      headers: {
        authorization: 'Bearer operator-input',
        connection: 'keep-alive, x-hop-by-hop',
        'content-type': 'application/json',
        cookie: 'guilds_session=session-value',
        'x-api-token': 'operator-input',
        'x-forwarded-host': 'attacker.example.test',
        'x-forwarded-proto': 'http',
        'x-hop-by-hop': 'remove-me',
        'user-agent': 'proxy-test-client',
      },
      body: '{"name":"North"}',
    });

    const response = await proxyRequest(request, ['territories'], {
      GUILDS_API_ORIGIN: upstreamOrigin,
    });

    expect(response.status).toBe(201);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [input, init] = fetchMock.mock.calls[0];
    expect(input).toBe('https://api.example.test/api/territories?view=all');
    expect(init?.method).toBe('POST');
    expect(init?.redirect).toBe('manual');

    const forwarded = new Headers(init?.headers);
    expect(forwarded.has('authorization')).toBe(false);
    expect(forwarded.has('x-api-token')).toBe(false);
    expect(forwarded.get('cookie')).toBe('guilds_session=session-value');
    expect(forwarded.get('content-type')).toBe('application/json');
    expect(forwarded.get('user-agent')).toBe('proxy-test-client');
    expect(forwarded.get('x-forwarded-host')).toBe('public.example.test');
    expect(forwarded.get('x-forwarded-proto')).toBe('https');
    expect(forwarded.has('connection')).toBe(false);
    expect(forwarded.has('x-hop-by-hop')).toBe(false);
    expect(forwarded.has('x-forwarded-for')).toBe(false);
    expect(await new Response(init?.body).text()).toBe('{"name":"North"}');
  });

  it('preserves each upstream Set-Cookie header and strips response hop-by-hop headers', async () => {
    const upstreamHeaders = new Headers({ connection: 'close, x-response-hop' });
    upstreamHeaders.append('set-cookie', 'guilds_session=first; Path=/');
    upstreamHeaders.append('set-cookie', 'guilds_refresh=second; Path=/');
    upstreamHeaders.set('x-response-hop', 'remove-me');
    upstreamHeaders.set('x-visible', 'keep-me');
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response('ok', { status: 200, headers: upstreamHeaders }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const response = await proxyRequest(
      new Request('https://public.example.test/api/session'),
      ['session'],
      { GUILDS_API_ORIGIN: upstreamOrigin },
    );

    expect(response.status).toBe(200);
    expect(cookieValues(response.headers)).toEqual([
      'guilds_session=first; Path=/',
      'guilds_refresh=second; Path=/',
    ]);
    expect(response.headers.get('x-visible')).toBe('keep-me');
    expect(response.headers.has('connection')).toBe(false);
    expect(response.headers.has('x-response-hop')).toBe(false);
  });

  it('returns controlled JSON errors for missing configuration and failed fetches', async () => {
    const request = new Request('https://public.example.test/api/meta');

    const unconfigured = await proxyRequest(request, ['meta'], {});
    expect(unconfigured.status).toBe(500);
    expect(await unconfigured.text()).toBe('{"error":"proxy_not_configured"}');

    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(new Error('network details')));
    const failed = await proxyRequest(request, ['meta'], { GUILDS_API_ORIGIN: upstreamOrigin });
    expect(failed.status).toBe(502);
    expect(await failed.text()).toBe('{"error":"upstream_unavailable"}');
  });
});
