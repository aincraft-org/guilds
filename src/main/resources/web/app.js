(() => {
  const canvas = document.getElementById("map");
  const ctx = canvas.getContext("2d");
  const listEl = document.getElementById("list");
  const metaEl = document.getElementById("meta");
  const hoverEl = document.getElementById("hover");
  const worldInput = document.getElementById("world");
  const refreshBtn = document.getElementById("refresh");

  let territories = [];
  let selectedId = null;
  let view = { scale: 0.4, ox: 450, oz: 350 }; // screen = world * scale + offset
  let dragging = false;
  let last = null;

  async function api(path, opts) {
    const res = await fetch(path, opts);
    if (!res.ok) throw new Error(path + " → " + res.status);
    if (res.status === 204) return null;
    return res.json();
  }

  async function loadMeta() {
    try {
      const m = await api("/api/meta");
      const h = await api("/api/health");
      metaEl.textContent =
        (m.publicOrigin || "") +
        " · " +
        (m.secure ? "HTTPS" : "HTTP") +
        (m.trustProxy ? " · proxy" : "") +
        (m.tlsEnabled ? " · tls" : "") +
        " · " +
        h.territories +
        " territories";
    } catch (e) {
      metaEl.textContent = "meta unavailable: " + e.message;
    }
  }

  async function loadTerritories() {
    const data = await api("/api/territories");
    territories = data.territories || [];
    renderList();
    draw();
  }

  function renderList() {
    listEl.innerHTML = "<h2>Territories</h2>";
    if (!territories.length) {
      listEl.innerHTML += '<p class="sub">No territories loaded.</p>';
      return;
    }
    for (const t of territories) {
      const div = document.createElement("div");
      div.className = "item" + (t.id === selectedId ? " active" : "");
      div.innerHTML =
        '<div class="name">' +
        escapeHtml(t.name || t.id) +
        '</div><div class="sub">' +
        escapeHtml(t.id) +
        " · " +
        escapeHtml(t.world) +
        " · zones " +
        (t.zones ? t.zones.length : 0) +
        "</div>";
      div.onclick = () => {
        selectedId = t.id;
        focusTerritory(t);
        renderList();
        draw();
      };
      listEl.appendChild(div);
    }
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function boundsOf(t) {
    let minX = Infinity,
      minZ = Infinity,
      maxX = -Infinity,
      maxZ = -Infinity;
    const b = t.boundary || {};
    for (const p of b.polygon || []) {
      minX = Math.min(minX, p.x);
      maxX = Math.max(maxX, p.x);
      minZ = Math.min(minZ, p.z);
      maxZ = Math.max(maxZ, p.z);
    }
    for (const c of b.chunks || []) {
      const x0 = c.cx * 16,
        z0 = c.cz * 16;
      minX = Math.min(minX, x0);
      maxX = Math.max(maxX, x0 + 16);
      minZ = Math.min(minZ, z0);
      maxZ = Math.max(maxZ, z0 + 16);
    }
    if (!isFinite(minX)) return null;
    return { minX, minZ, maxX, maxZ };
  }

  function focusTerritory(t) {
    const b = boundsOf(t);
    if (!b) return;
    const w = b.maxX - b.minX || 1;
    const h = b.maxZ - b.minZ || 1;
    const sx = (canvas.width - 80) / w;
    const sz = (canvas.height - 80) / h;
    view.scale = Math.min(sx, sz, 4);
    view.ox = canvas.width / 2 - ((b.minX + b.maxX) / 2) * view.scale;
    view.oz = canvas.height / 2 - ((b.minZ + b.maxZ) / 2) * view.scale;
  }

  function worldToScreen(x, z) {
    return { x: x * view.scale + view.ox, y: z * view.scale + view.oz };
  }

  function screenToWorld(sx, sy) {
    return {
      x: Math.floor((sx - view.ox) / view.scale),
      z: Math.floor((sy - view.oz) / view.scale),
    };
  }

  function drawPoly(points, fill, stroke) {
    if (!points || points.length < 2) return;
    ctx.beginPath();
    const p0 = worldToScreen(points[0].x, points[0].z);
    ctx.moveTo(p0.x, p0.y);
    for (let i = 1; i < points.length; i++) {
      const p = worldToScreen(points[i].x, points[i].z);
      ctx.lineTo(p.x, p.y);
    }
    ctx.closePath();
    if (fill) {
      ctx.fillStyle = fill;
      ctx.fill();
    }
    if (stroke) {
      ctx.strokeStyle = stroke;
      ctx.lineWidth = 2;
      ctx.stroke();
    }
  }

  function drawChunks(chunks, fill, stroke) {
    for (const c of chunks || []) {
      const a = worldToScreen(c.cx * 16, c.cz * 16);
      const b = worldToScreen(c.cx * 16 + 16, c.cz * 16 + 16);
      ctx.fillStyle = fill;
      ctx.fillRect(a.x, a.y, b.x - a.x, b.y - a.y);
      ctx.strokeStyle = stroke;
      ctx.strokeRect(a.x, a.y, b.x - a.x, b.y - a.y);
    }
  }

  function zoneColor(type, alpha) {
    const a = alpha == null ? 0.35 : alpha;
    if (type === "CLAIMABLE") return "rgba(58,110,165," + a + ")";
    return "rgba(61,122,74," + a + ")";
  }

  function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    // grid
    ctx.strokeStyle = "rgba(255,255,255,0.04)";
    const step = 16 * Math.max(1, Math.round(1 / view.scale));
    const w0 = screenToWorld(0, 0);
    const w1 = screenToWorld(canvas.width, canvas.height);
    const gx0 = Math.floor(w0.x / step) * step;
    const gz0 = Math.floor(w0.z / step) * step;
    for (let x = gx0; x < w1.x; x += step) {
      const s = worldToScreen(x, 0);
      ctx.beginPath();
      ctx.moveTo(s.x, 0);
      ctx.lineTo(s.x, canvas.height);
      ctx.stroke();
    }
    for (let z = gz0; z < w1.z; z += step) {
      const s = worldToScreen(0, z);
      ctx.beginPath();
      ctx.moveTo(0, s.y);
      ctx.lineTo(canvas.width, s.y);
      ctx.stroke();
    }

    const world = worldInput.value || "world";
    for (const t of territories) {
      if (t.world !== world) continue;
      const active = t.id === selectedId;
      const stroke = active ? "#d4a017" : "rgba(231,236,243,0.5)";
      const b = t.boundary || {};
      drawPoly(b.polygon, "rgba(212,160,23,0.12)", stroke);
      drawChunks(b.chunks, "rgba(212,160,23,0.18)", stroke);
      for (const z of t.zones || []) {
        const zb = z.boundary || {};
        drawPoly(zb.polygon, zoneColor(z.type), active ? "#fff" : "rgba(255,255,255,0.25)");
        drawChunks(zb.chunks, zoneColor(z.type, 0.45), active ? "#fff" : "rgba(255,255,255,0.25)");
      }
      // label
      const bb = boundsOf(t);
      if (bb) {
        const c = worldToScreen((bb.minX + bb.maxX) / 2, (bb.minZ + bb.maxZ) / 2);
        ctx.fillStyle = "#e7ecf3";
        ctx.font = "12px system-ui";
        ctx.textAlign = "center";
        ctx.fillText(t.name || t.id, c.x, c.y);
      }
    }
  }

  canvas.addEventListener("wheel", (e) => {
    e.preventDefault();
    const rect = canvas.getBoundingClientRect();
    const sx = e.clientX - rect.left;
    const sy = e.clientY - rect.top;
    const before = screenToWorld(sx, sy);
    const factor = e.deltaY < 0 ? 1.12 : 1 / 1.12;
    view.scale = Math.min(20, Math.max(0.02, view.scale * factor));
    const after = screenToWorld(sx, sy);
    view.ox += (after.x - before.x) * view.scale;
    view.oz += (after.z - before.z) * view.scale;
    draw();
  }, { passive: false });

  canvas.addEventListener("mousedown", (e) => {
    dragging = true;
    last = { x: e.clientX, y: e.clientY };
  });
  window.addEventListener("mouseup", () => {
    dragging = false;
  });
  window.addEventListener("mousemove", (e) => {
    if (!dragging) return;
    view.ox += e.clientX - last.x;
    view.oz += e.clientY - last.y;
    last = { x: e.clientX, y: e.clientY };
    draw();
  });

  canvas.addEventListener("mousemove", (e) => {
    const rect = canvas.getBoundingClientRect();
    const w = screenToWorld(e.clientX - rect.left, e.clientY - rect.top);
    hoverEl.textContent = "x=" + w.x + " z=" + w.z;
  });

  canvas.addEventListener("click", async (e) => {
    if (dragging) return;
    const rect = canvas.getBoundingClientRect();
    const w = screenToWorld(e.clientX - rect.left, e.clientY - rect.top);
    const world = worldInput.value || "world";
    try {
      const r = await api(
        "/api/resolve?world=" +
          encodeURIComponent(world) +
          "&x=" +
          w.x +
          "&z=" +
          w.z
      );
      if (!r.contained) {
        hoverEl.textContent = "x=" + w.x + " z=" + w.z + " · outside";
        return;
      }
      selectedId = r.territoryId;
      hoverEl.textContent =
        r.territoryId +
        " · " +
        r.zoneType +
        (r.zoneId ? " [" + r.zoneId + "]" : " [default]");
      renderList();
      draw();
    } catch (err) {
      hoverEl.textContent = err.message;
    }
  });

  refreshBtn.addEventListener("click", () => {
    loadMeta();
    loadTerritories().catch((e) => (metaEl.textContent = e.message));
  });
  worldInput.addEventListener("change", draw);

  loadMeta();
  loadTerritories().catch((e) => (metaEl.textContent = e.message));
})();
