/** Same-origin REST client with credentials for session cookies. */

export async function getMeta() {
  const r = await fetch('/api/meta', { credentials: 'include' });
  if (!r.ok) throw new Error(`meta ${r.status}`);
  return r.json();
}

export async function login(token) {
  const r = await fetch('/api/session', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token }),
  });
  if (!r.ok) {
    const err = new Error('login failed');
    err.status = r.status;
    throw err;
  }
  return r.json();
}

export async function logout() {
  await fetch('/api/session', { method: 'DELETE', credentials: 'include' });
}

export async function listTerritories() {
  const r = await fetch('/api/territories', { credentials: 'include' });
  if (r.status === 401) {
    const err = new Error('unauthorized');
    err.status = 401;
    throw err;
  }
  if (!r.ok) throw new Error(`list ${r.status}`);
  return r.json();
}

export async function putTerritory(id, body) {
  const r = await fetch(`/api/territories/${encodeURIComponent(id)}`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const text = await r.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { message: text };
  }
  if (!r.ok) {
    const err = new Error(json?.message || json?.error || `save ${r.status}`);
    err.status = r.status;
    err.body = json;
    throw err;
  }
  return json;
}

export async function deleteTerritory(id) {
  const r = await fetch(`/api/territories/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    credentials: 'include',
  });
  if (r.status === 204 || r.status === 200) return;
  const text = await r.text();
  const err = new Error(text || `delete ${r.status}`);
  err.status = r.status;
  throw err;
}
