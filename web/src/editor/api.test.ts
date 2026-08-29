import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, guildsApi } from './api';

const response = (body: unknown, status = 200, headers?: Record<string, string>) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });

const fetchMock = vi.fn<typeof fetch>();

describe('guildsApi', () => {
  afterEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  it('logs in with a relative credentialed request and an ephemeral token body', async () => {
    fetchMock.mockResolvedValue(response({ ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    await guildsApi.login('operator-secret');

    expect(fetchMock).toHaveBeenCalledWith('/api/session', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ token: 'operator-secret' }),
    }));
  });

  it('uses relative paths and credentials for every operation', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ authRequired: false, squaremapTileBaseUrl: '', sessionTtlSeconds: 1, secure: true }))
      .mockResolvedValueOnce(response({ territories: [] }))
      .mockResolvedValueOnce(response({ id: 'one' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await guildsApi.getMeta();
    await guildsApi.listTerritories();
    await guildsApi.putTerritory({
      id: 'one', name: 'One', world: 'world', defaultZoneType: 'WILDERNESS',
      boundary: { polygon: [], chunks: [] }, zones: [],
    });
    await guildsApi.logout();
    await guildsApi.deleteTerritory('one');

    for (const [path, init] of fetchMock.mock.calls) {
      expect(path).toMatch(/^\/api\//);
      expect(init?.credentials).toBe('include');
    }
  });

  it('parses JSON and text error bodies into ApiError', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ message: 'nope' }, 401))
      .mockResolvedValueOnce(new Response('upstream unavailable', {
        status: 502,
        headers: { 'Content-Type': 'text/plain' },
      }));
    vi.stubGlobal('fetch', fetchMock);

    const unauthorized = guildsApi.listTerritories();
    await expect(unauthorized).rejects.toMatchObject({
      status: 401,
      body: { message: 'nope' },
    });
    await expect(guildsApi.getMeta()).rejects.toEqual(
      expect.objectContaining({ status: 502, body: 'upstream unavailable' }),
    );
    await expect(unauthorized).rejects.toBeInstanceOf(ApiError);
  });

  it('does not access browser storage', async () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage must not be accessed');
    });
    fetchMock.mockResolvedValue(response({ ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    await guildsApi.login('not-persisted');
    expect(getItem).not.toHaveBeenCalled();
    getItem.mockRestore();
  });
});
