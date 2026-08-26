import {
  addChunkRect,
  blockToChunk,
  eraseChunk,
  removePolygonVertexNear,
  snapBlockToChunkCorner,
  toggleChunk,
} from './model.js';

/**
 * Map interaction tools: select, polygon, paint, rect, erase.
 * @param {object} mapApi from createEditorMap
 * @param {() => object|null} getTarget returns { territory, boundary }
 * @param {(territoryId: string) => void} onChange
 */
export function createTools(mapApi, getTarget, onChange) {
  let tool = 'select';
  let polyVerts = [];
  let polyPreview = null;
  let rectStart = null;
  let rectPreview = null;

  function setTool(name) {
    tool = name;
    polyVerts = [];
    rectStart = null;
    clearPreview();
  }

  function clearPreview() {
    if (polyPreview) {
      mapApi.map.removeLayer(polyPreview);
      polyPreview = null;
    }
    if (rectPreview) {
      mapApi.map.removeLayer(rectPreview);
      rectPreview = null;
    }
    mapApi.clearDraft();
  }

  function blockFromEvent(e) {
    const p = mapApi.toPoint(e.latlng);
    return { x: p.x, z: p.y };
  }

  function finishPolygon() {
    const target = getTarget();
    if (!target || polyVerts.length < 3) {
      polyVerts = [];
      clearPreview();
      return;
    }
    target.boundary.polygon = polyVerts.map((v) => ({ x: v.x, z: v.z }));
    polyVerts = [];
    clearPreview();
    onChange(target.territory.id);
  }

  function onClick(e) {
    const target = getTarget();
    if (!target && tool !== 'select') return;
    const b = blockFromEvent(e);

    if (tool === 'polygon') {
      if (!target) return;
      const snap = snapBlockToChunkCorner(b.x, b.z);
      polyVerts.push({ x: snap.x, z: snap.z });
      redrawPolyPreview();
      return;
    }

    if (tool === 'paint') {
      if (!target) return;
      const c = blockToChunk(b.x, b.z);
      toggleChunk(target.boundary, c.x, c.z);
      onChange(target.territory.id);
      return;
    }

    if (tool === 'erase') {
      if (!target) return;
      // Prefer vertex remove if near a polygon corner
      if (removePolygonVertexNear(target.boundary, b.x, b.z, 12)) {
        onChange(target.territory.id);
        return;
      }
      const c = blockToChunk(b.x, b.z);
      eraseChunk(target.boundary, c.x, c.z);
      onChange(target.territory.id);
      return;
    }

    if (tool === 'rect') {
      if (!target) return;
      const c = blockToChunk(b.x, b.z);
      if (!rectStart) {
        rectStart = c;
        return;
      }
      addChunkRect(target.boundary, rectStart, c);
      rectStart = null;
      clearPreview();
      onChange(target.territory.id);
    }
  }

  function onDblClick(e) {
    if (tool === 'polygon') {
      L.DomEvent.preventDefault(e);
      finishPolygon();
    }
  }

  function onMouseMove(e) {
    const b = blockFromEvent(e);
    if (tool === 'polygon' && polyVerts.length > 0) {
      const snap = snapBlockToChunkCorner(b.x, b.z);
      redrawPolyPreview(snap);
    }
    if (tool === 'rect' && rectStart) {
      const c = blockToChunk(b.x, b.z);
      redrawRectPreview(rectStart, c);
    }
  }

  function redrawPolyPreview(hover) {
    clearPreview();
    const pts = polyVerts.slice();
    if (hover) pts.push(hover);
    if (pts.length === 0) return;
    const latlngs = pts.map((v) => mapApi.toLatLng(v.x, v.z));
    if (latlngs.length === 1) {
      polyPreview = L.circleMarker(latlngs[0], { radius: 4, color: '#7ec8ff' }).addTo(mapApi.map);
    } else if (latlngs.length === 2) {
      polyPreview = L.polyline(latlngs, { color: '#7ec8ff', dashArray: '4 4' }).addTo(mapApi.map);
    } else {
      polyPreview = L.polygon(latlngs, {
        color: '#7ec8ff',
        fillColor: '#5b9fd4',
        fillOpacity: 0.2,
        dashArray: '4 4',
      }).addTo(mapApi.map);
    }
  }

  function redrawRectPreview(c0, c1) {
    if (rectPreview) mapApi.map.removeLayer(rectPreview);
    const minX = Math.min(c0.x, c1.x) * 16;
    const maxX = (Math.max(c0.x, c1.x) + 1) * 16;
    const minZ = Math.min(c0.z, c1.z) * 16;
    const maxZ = (Math.max(c0.z, c1.z) + 1) * 16;
    rectPreview = L.polygon(
      [
        mapApi.toLatLng(minX, minZ),
        mapApi.toLatLng(maxX, minZ),
        mapApi.toLatLng(maxX, maxZ),
        mapApi.toLatLng(minX, maxZ),
      ],
      { color: '#7ec87e', fillOpacity: 0.15, dashArray: '4 4' }
    ).addTo(mapApi.map);
  }

  function onKey(e) {
    if (e.key === 'Enter' && tool === 'polygon') {
      finishPolygon();
    }
    if (e.key === 'Escape') {
      polyVerts = [];
      rectStart = null;
      clearPreview();
    }
  }

  mapApi.map.on('click', onClick);
  mapApi.map.on('dblclick', onDblClick);
  mapApi.map.on('mousemove', onMouseMove);
  window.addEventListener('keydown', onKey);

  // Disable double-click zoom while drawing polygons
  mapApi.map.doubleClickZoom.disable();

  return {
    setTool,
    getTool: () => tool,
    destroy() {
      mapApi.map.off('click', onClick);
      mapApi.map.off('dblclick', onDblClick);
      mapApi.map.off('mousemove', onMouseMove);
      window.removeEventListener('keydown', onKey);
    },
  };
}
