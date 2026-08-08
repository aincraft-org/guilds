import { boundaryStats } from './model.js';

export function showBanner(msg, isError = false) {
  const el = document.getElementById('banner');
  if (!msg) {
    el.classList.add('hidden');
    el.textContent = '';
    return;
  }
  el.textContent = msg;
  el.classList.toggle('error', !!isError);
  el.classList.remove('hidden');
}

export function setLoginVisible(visible) {
  document.getElementById('login-modal').classList.toggle('hidden', !visible);
}

export function renderTree(state, selection, onSelect) {
  const root = document.getElementById('territory-tree');
  root.innerHTML = '';
  const world = document.getElementById('world-select').value;
  const list = state.territories.filter((t) => !world || t.world === world);
  for (const t of list) {
    const item = document.createElement('div');
    item.className = 'tree-item' + (t.dirty ? ' dirty' : '');
    if (selection && selection.territoryId === t.id && !selection.zoneId) {
      item.classList.add('selected');
    }
    item.textContent = t.name || t.id;
    item.addEventListener('click', () => onSelect({ territoryId: t.id, zoneId: null }));
    root.appendChild(item);
    for (const z of t.zones || []) {
      const zi = document.createElement('div');
      zi.className = 'tree-item zone' + (t.dirty ? ' dirty' : '');
      if (selection && selection.territoryId === t.id && selection.zoneId === z.id) {
        zi.classList.add('selected');
      }
      zi.textContent = `└ ${z.name || z.id} (${z.type})`;
      zi.addEventListener('click', () => onSelect({ territoryId: t.id, zoneId: z.id }));
      root.appendChild(zi);
    }
  }
  if (list.length === 0) {
    root.innerHTML = '<div class="muted">No territories in this world</div>';
  }
}

export function fillWorlds(state, preferred) {
  const sel = document.getElementById('world-select');
  const worlds = [...new Set(state.territories.map((t) => t.world || 'world'))].sort();
  if (!worlds.includes('world')) worlds.unshift('world');
  const current = preferred || sel.value || 'world';
  sel.innerHTML = '';
  for (const w of worlds) {
    const o = document.createElement('option');
    o.value = w;
    o.textContent = w;
    sel.appendChild(o);
  }
  if ([...sel.options].some((o) => o.value === current)) sel.value = current;
}

export function renderInspector(target, selection) {
  const empty = document.getElementById('inspector-empty');
  const form = document.getElementById('inspector-form');
  if (!target) {
    empty.classList.remove('hidden');
    form.classList.add('hidden');
    return;
  }
  empty.classList.add('hidden');
  form.classList.remove('hidden');
  const isZone = !!selection.zoneId;
  const entity = isZone ? target.zone : target.territory;
  document.getElementById('insp-id').value = entity.id;
  document.getElementById('insp-id').readOnly = true;
  document.getElementById('insp-name').value = entity.name || '';
  const typeWrap = document.getElementById('insp-type-wrap');
  typeWrap.classList.toggle('hidden', !isZone);
  if (isZone) {
    document.getElementById('insp-type').value = entity.type || 'CLAIMABLE';
  }
  const st = boundaryStats(target.boundary);
  document.getElementById('insp-stats').textContent =
    `Chunks: ${st.chunks} · Vertices: ${st.vertices}`;
}

export function setSessionStatus(text) {
  document.getElementById('session-status').textContent = text;
}

export function bindToolbar(onTool) {
  document.querySelectorAll('#toolbar .tool').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('#toolbar .tool').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      onTool(btn.dataset.tool);
    });
  });
}
