import type {
  EditorMeta,
  GuildsApi,
  LoginResponse,
  TerritoryDocument,
  TerritoryListResponse,
} from './types';

/** An HTTP response that the editor could not use. */
export class ApiError extends Error {
  readonly status: number;
  readonly body: unknown;

  constructor(status: number, body: unknown, message = `Guilds API request failed (${status})`) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

function parseBody(text: string): unknown {
  if (text.trim() === '') return undefined;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'include',
  });
  const text = await response.text();
  const body = parseBody(text);

  if (!response.ok) {
    throw new ApiError(response.status, body);
  }
  return body as T;
}

function jsonRequest(body: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}

export const guildsApi: GuildsApi = {
  getMeta() {
    return request<EditorMeta>('/api/meta');
  },

  login(token: string) {
    return request<LoginResponse>('/api/session', jsonRequest({ token }));
  },

  async logout() {
    await request<unknown>('/api/session', { method: 'DELETE' });
  },

  listTerritories() {
    return request<TerritoryListResponse>('/api/territories');
  },

  putTerritory(territory: TerritoryDocument) {
    return request<TerritoryDocument>(
      `/api/territories/${encodeURIComponent(territory.id)}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(territory),
      },
    );
  },

  async deleteTerritory(id: string) {
    await request<unknown>(`/api/territories/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },
};

export type { GuildsApi } from './types';
