/**
 * Leaflet map with squaremap-compatible CRS and optional tile basemap.
 * Matches squaremap: CRS.Simple, tileSize 512, URL tiles/{world}/{z}/{x}_{y}.png
 */

const MAX_NATIVE_ZOOM = 3;
const EXTRA_ZOOM = 2;

export function bukkitToSquaremapWorld(bukkitName) {
  if (!bukkitName || bukkitName === 'world') return 'minecraft_overworld';
  if (bukkitName === 'world_nether') return 'minecraft_the_nether';
  if (bukkitName === 'world_the_end') return 'minecraft_the_end';
  return 'minecraft_' + String(bukkitName).replace(/[:/]/g, '_');
}

export function createEditorMap(containerId, options = {}) {
  const maxZoom = MAX_NATIVE_ZOOM + EXTRA_ZOOM;
  const scale = 1 / Math.pow(2, MAX_NATIVE_ZOOM);

  const map = L.map(containerId, {
    crs: L.CRS.Simple,
    center: [0, 0],
    zoom: MAX_NATIVE_ZOOM,
    minZoom: 0,
    maxZoom,
    attributionControl: false,
    preferCanvas: true,
  });

  function pixelsToMeters(p) {
    return p * scale;
  }
  function metersToPixels(m) {
    return m / scale;
  }
  function toLatLng(x, z) {
    return L.latLng(pixelsToMeters(-z), pixelsToMeters(x));
  }
  function toPoint(latlng) {
    return L.point(metersToPixels(latlng.lng), metersToPixels(-latlng.lat));
  }

  let tileLayer = null;
  let basemapOk = true;
  let currentSqWorld = 'minecraft_overworld';

  function setTileBase(baseUrl, squaremapWorld) {
    if (tileLayer) {
      map.removeLayer(tileLayer);
      tileLayer = null;
    }
    currentSqWorld = squaremapWorld || currentSqWorld;
    if (!baseUrl) {
      basemapOk = false;
      return;
    }
    const template = `${baseUrl.replace(/\/+$/, '')}/tiles/${currentSqWorld}/{z}/{x}_{y}.png`;
    tileLayer = L.tileLayer(template, {
      tileSize: 512,
      minNativeZoom: 0,
      maxNativeZoom: MAX_NATIVE_ZOOM,
      noWrap: true,
      errorTileUrl: '',
    });
    let errorCount = 0;
    tileLayer.on('tileerror', () => {
      errorCount++;
      if (errorCount > 4) basemapOk = false;
    });
    tileLayer.on('load', () => {
      basemapOk = true;
    });
    tileLayer.addTo(map);
  }

  const shapes = L.layerGroup().addTo(map);
  const draft = L.layerGroup().addTo(map);
  const grid = L.layerGroup().addTo(map);

  function clearShapes() {
    shapes.clearLayers();
  }
  function clearDraft() {
    draft.clearLayers();
  }

  /** Draw a light chunk grid around the map center (updates on move/zoom). */
  function redrawChunkGrid() {
    grid.clearLayers();
    if (map.getZoom() < 2) return;
    const bounds = map.getBounds();
    const sw = toPoint(bounds.getSouthWest());
    const ne = toPoint(bounds.getNorthEast());
    const minX = Math.floor(Math.min(sw.x, ne.x) / 16) - 1;
    const maxX = Math.ceil(Math.max(sw.x, ne.x) / 16) + 1;
    const minZ = Math.floor(Math.min(sw.y, ne.y) / 16) - 1;
    const maxZ = Math.ceil(Math.max(sw.y, ne.y) / 16) + 1;
    // Cap lines for performance
    if ((maxX - minX) * (maxZ - minZ) > 80 * 80) return;
    const style = { color: '#ffffff', weight: 1, opacity: 0.08, interactive: false };
    for (let cx = minX; cx <= maxX; cx++) {
      const x = cx * 16;
      L.polyline([toLatLng(x, minZ * 16), toLatLng(x, maxZ * 16)], style).addTo(grid);
    }
    for (let cz = minZ; cz <= maxZ; cz++) {
      const z = cz * 16;
      L.polyline([toLatLng(minX * 16, z), toLatLng(maxX * 16, z)], style).addTo(grid);
    }
  }

  map.on('moveend zoomend', redrawChunkGrid);
  setTimeout(redrawChunkGrid, 0);

  function polygonLatLngs(vertices) {
    return (vertices || []).map((v) => toLatLng(v.x, v.z));
  }

  function chunkRectLatLngs(cx, cz) {
    const x0 = cx * 16;
    const z0 = cz * 16;
    const x1 = x0 + 16;
    const z1 = z0 + 16;
    return [toLatLng(x0, z0), toLatLng(x1, z0), toLatLng(x1, z1), toLatLng(x0, z1)];
  }

  function renderBoundary(boundary, style, target = shapes) {
    if (!boundary) return;
    if (boundary.polygon && boundary.polygon.length >= 3) {
      L.polygon(polygonLatLngs(boundary.polygon), style).addTo(target);
    }
    for (const c of boundary.chunks || []) {
      L.polygon(chunkRectLatLngs(c.x, c.z), {
        ...style,
        fillOpacity: (style.fillOpacity ?? 0.2) * 0.8,
      }).addTo(target);
    }
  }

  function centerOn(x, z, zoom = MAX_NATIVE_ZOOM) {
    map.setView(toLatLng(x, z), zoom);
  }

  return {
    map,
    toLatLng,
    toPoint,
    setTileBase,
    clearShapes,
    clearDraft,
    shapes,
    draft,
    renderBoundary,
    centerOn,
    isBasemapOk: () => basemapOk,
    getSquaremapWorld: () => currentSqWorld,
  };
}
