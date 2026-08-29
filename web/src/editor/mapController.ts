import L from 'leaflet';
import type { Boundary, Selection, TerritoryDraft } from './types';
import {
  addChunkRect,
  eraseChunk,
  removePolygonVertexNear,
  toggleChunk,
} from './model';
import {
  blockToChunk,
  bukkitToSquaremapWorld,
  fromLeafletPoint,
  snapBlockToChunkCorner,
  toLeafletPoint,
} from './geometry';
import type { EditorTool } from './Toolbar';

const NATIVE_ZOOM = 3;
const MAX_ZOOM = 5;
const CHUNK_SIZE = 16;
const MAX_GRID_CELLS = 80 * 80;
const TILE_ERROR_THRESHOLD = 5;

export interface MapCoordinate {
  blockX: number;
  blockZ: number;
  chunkX: number;
  chunkZ: number;
}

export interface MapViewModel {
  world?: string;
  tileBaseUrl?: string;
  territories?: readonly TerritoryDraft[];
  selection?: Selection | null;
  selectedBoundary?: Boundary | null;
}

export interface MapControllerOptions extends MapViewModel {
  onBoundaryChange?: (boundary: Boundary) => void;
  onSelect?: (selection: Selection) => void;
  onCoordinateChange?: (coordinate: MapCoordinate) => void;
  onBasemapStatusChange?: (available: boolean) => void;
}

export interface MapController {
  readonly map: L.Map;
  setViewModel(viewModel: MapViewModel): void;
  setTool(tool: EditorTool): void;
  destroy(): void;
}

interface NormalizedViewModel {
  world: string;
  tileBaseUrl: string;
  territories: readonly TerritoryDraft[];
  selection: Selection | null;
  selectedBoundary: Boundary | null;
}

type LayerGroup = L.LayerGroup;

function normalizeViewModel(viewModel: MapViewModel): NormalizedViewModel {
  return {
    world: viewModel.world || 'world',
    tileBaseUrl: viewModel.tileBaseUrl || '',
    territories: viewModel.territories || [],
    selection: viewModel.selection || null,
    selectedBoundary: viewModel.selectedBoundary || null,
  };
}

function coordinateFromLatLng(lat: number, lng: number): MapCoordinate {
  const point = fromLeafletPoint(lat, lng);
  const blockX = Math.floor(point.x);
  const blockZ = Math.floor(point.z);
  const chunk = blockToChunk(blockX, blockZ);
  return { blockX, blockZ, chunkX: chunk.x, chunkZ: chunk.z };
}

function pointToLatLng(x: number, z: number): L.LatLngExpression {
  const point = toLeafletPoint(x, z);
  return [point.lat, point.lng];
}

function chunkPolygon(chunkX: number, chunkZ: number): L.LatLngExpression[] {
  const x0 = chunkX * CHUNK_SIZE;
  const z0 = chunkZ * CHUNK_SIZE;
  return [
    pointToLatLng(x0, z0),
    pointToLatLng(x0 + CHUNK_SIZE, z0),
    pointToLatLng(x0 + CHUNK_SIZE, z0 + CHUNK_SIZE),
    pointToLatLng(x0, z0 + CHUNK_SIZE),
  ];
}

function polygonPoints(boundary: Boundary): L.LatLngExpression[] {
  return boundary.polygon.map((point) => pointToLatLng(point.x, point.z));
}

function chunkKey(x: number, z: number): string {
  return `${x},${z}`;
}

function isSelected(selection: Selection | null, territoryId: string, zoneId?: string): boolean {
  return selection?.territoryId === territoryId && selection.zoneId === zoneId;
}

function boundaryHasChunk(boundary: Boundary, x: number, z: number): boolean {
  return boundary.chunks.some((chunk) => chunk.x === x && chunk.z === z);
}

function hasBoundaryChange(before: Boundary, after: Boundary): boolean {
  return before.polygon.length !== after.polygon.length || before.chunks.length !== after.chunks.length;
}

export function createMapController(
  element: HTMLElement | string,
  options: MapControllerOptions = {},
): MapController {
  const map = L.map(element, {
    crs: L.CRS.Simple,
    center: [0, 0],
    zoom: NATIVE_ZOOM,
    minZoom: 0,
    maxZoom: MAX_ZOOM,
    attributionControl: false,
    preferCanvas: true,
  });
  const shapes: LayerGroup = L.layerGroup().addTo(map);
  const draft: LayerGroup = L.layerGroup().addTo(map);
  const grid: LayerGroup = L.layerGroup().addTo(map);
  let model = normalizeViewModel(options);
  let tool: EditorTool = 'select';
  let tileLayer: L.TileLayer | null = null;
  let tileIdentity = '';
  let tileErrors = 0;
  let basemapAvailable = true;
  let pointerDown = false;
  let rectangleStart: { x: number; z: number } | null = null;
  let paintedChunks = new Set<string>();
  let destroyed = false;

  const onBoundaryChange = options.onBoundaryChange || (() => undefined);
  const onSelect = options.onSelect || (() => undefined);
  const onCoordinateChange = options.onCoordinateChange || (() => undefined);
  const onBasemapStatusChange = options.onBasemapStatusChange || (() => undefined);

  function reportBasemapStatus(available: boolean): void {
    if (basemapAvailable === available) return;
    basemapAvailable = available;
    onBasemapStatusChange(available);
  }

  function replaceTiles(): void {
    const squaremapWorld = bukkitToSquaremapWorld(model.world);
    const base = model.tileBaseUrl.replace(/\/+$/, '');
    const nextIdentity = base ? `${base}|${squaremapWorld}` : '';
    if (nextIdentity === tileIdentity) return;
    if (tileLayer) {
      map.removeLayer(tileLayer);
      tileLayer = null;
    }
    tileIdentity = nextIdentity;
    tileErrors = 0;
    if (!base) {
      reportBasemapStatus(true);
      return;
    }
    const template = `${base}/tiles/${squaremapWorld}/{z}/{x}_{y}.png`;
    tileLayer = L.tileLayer(template, {
      tileSize: 512,
      minNativeZoom: 0,
      maxNativeZoom: NATIVE_ZOOM,
      noWrap: true,
      errorTileUrl: '',
    });
    tileLayer.on('tileerror', () => {
      tileErrors += 1;
      if (tileErrors >= TILE_ERROR_THRESHOLD) reportBasemapStatus(false);
    });
    tileLayer.on('load', () => {
      tileErrors = 0;
      reportBasemapStatus(true);
    });
    tileLayer.addTo(map);
  }

  function renderBoundary(
    boundary: Boundary,
    style: L.PathOptions,
    target: LayerGroup,
    territoryId?: string,
    zoneId?: string,
  ): void {
    if (boundary.polygon.length >= 3) {
      const polygon = L.polygon(polygonPoints(boundary), { ...style, interactive: territoryId === undefined ? false : true });
      polygon.addTo(target);
      if (territoryId) {
        polygon.on('click', () => {
          if (tool === 'select') onSelect({ territoryId, ...(zoneId ? { zoneId } : {}) });
        });
      }
    } else if (boundary.polygon.length > 0 && target === draft) {
      const line = L.polyline(polygonPoints(boundary), { ...style, interactive: false });
      line.addTo(target);
    }
    for (const chunk of boundary.chunks) {
      const chunkLayer = L.polygon(chunkPolygon(chunk.x, chunk.z), {
        ...style,
        fillOpacity: (style.fillOpacity ?? 0.2) * 0.8,
        interactive: territoryId === undefined ? false : true,
      });
      chunkLayer.addTo(target);
      if (territoryId) {
        chunkLayer.on('click', () => {
          if (tool === 'select') onSelect({ territoryId, ...(zoneId ? { zoneId } : {}) });
        });
      }
    }
  }

  function renderShapes(): void {
    shapes.clearLayers();
    for (const territory of model.territories) {
      if (territory.world !== model.world) continue;
      const territorySelected = isSelected(model.selection, territory.id);
      renderBoundary(
        territory.boundary,
        {
          color: territorySelected ? '#9bd5ff' : '#589bd1',
          weight: territorySelected ? 3 : 2,
          fillColor: '#589bd1',
          fillOpacity: territorySelected ? 0.28 : 0.15,
        },
        shapes,
        territory.id,
      );
      for (const zone of territory.zones) {
        const zoneSelected = isSelected(model.selection, territory.id, zone.id);
        const claimable = zone.type === 'CLAIMABLE';
        renderBoundary(
          zone.boundary,
          {
            color: zoneSelected ? '#fff3a0' : claimable ? '#e6c15a' : '#7ec87e',
            weight: zoneSelected ? 3 : 2,
            fillColor: claimable ? '#e6c15a' : '#7ec87e',
            fillOpacity: zoneSelected ? 0.35 : 0.2,
          },
          shapes,
          territory.id,
          zone.id,
        );
      }
    }
  }

  function renderDraft(): void {
    draft.clearLayers();
    if (!model.selectedBoundary) return;
    renderBoundary(
      model.selectedBoundary,
      {
        color: '#ffffff',
        weight: 2,
        fillColor: '#ffffff',
        fillOpacity: 0.08,
        dashArray: '6 5',
      },
      draft,
    );
  }

  function redrawGrid(): void {
    grid.clearLayers();
    if (map.getZoom() < 2) return;
    const bounds = map.getBounds();
    const southWest = coordinateFromLatLng(bounds.getSouthWest().lat, bounds.getSouthWest().lng);
    const northEast = coordinateFromLatLng(bounds.getNorthEast().lat, bounds.getNorthEast().lng);
    const minX = Math.floor(Math.min(southWest.blockX, northEast.blockX) / CHUNK_SIZE) - 1;
    const maxX = Math.ceil(Math.max(southWest.blockX, northEast.blockX) / CHUNK_SIZE) + 1;
    const minZ = Math.floor(Math.min(southWest.blockZ, northEast.blockZ) / CHUNK_SIZE) - 1;
    const maxZ = Math.ceil(Math.max(southWest.blockZ, northEast.blockZ) / CHUNK_SIZE) + 1;
    if ((maxX - minX) * (maxZ - minZ) > MAX_GRID_CELLS) return;
    const style: L.PolylineOptions = {
      color: '#ffffff',
      weight: 1,
      opacity: 0.08,
      interactive: false,
    };
    for (let x = minX; x <= maxX; x += 1) {
      const blockX = x * CHUNK_SIZE;
      L.polyline([
        pointToLatLng(blockX, minZ * CHUNK_SIZE),
        pointToLatLng(blockX, maxZ * CHUNK_SIZE),
      ], style).addTo(grid);
    }
    for (let z = minZ; z <= maxZ; z += 1) {
      const blockZ = z * CHUNK_SIZE;
      L.polyline([
        pointToLatLng(minX * CHUNK_SIZE, blockZ),
        pointToLatLng(maxX * CHUNK_SIZE, blockZ),
      ], style).addTo(grid);
    }
  }

  function selectedBoundary(): Boundary | null {
    return model.selectedBoundary;
  }

  function applyBoundary(next: Boundary): void {
    if (destroyed) return;
    onBoundaryChange(next);
  }

  function pointAt(event: L.LeafletMouseEvent): MapCoordinate {
    return coordinateFromLatLng(event.latlng.lat, event.latlng.lng);
  }

  function appendPolygonVertex(event: L.LeafletMouseEvent): void {
    const boundary = selectedBoundary();
    if (!boundary) return;
    const point = pointAt(event);
    const corner = snapBlockToChunkCorner(point.blockX, point.blockZ);
    const duplicate = boundary.polygon.some((vertex) => vertex.x === corner.x && vertex.z === corner.z);
    if (duplicate) return;
    applyBoundary({
      polygon: [...boundary.polygon, { x: corner.x, z: corner.z }],
      chunks: boundary.chunks.map((chunk) => ({ ...chunk })),
    });
  }

  function paintChunk(event: L.LeafletMouseEvent): void {
    const boundary = selectedBoundary();
    if (!boundary) return;
    const point = pointAt(event);
    const key = chunkKey(point.chunkX, point.chunkZ);
    if (paintedChunks.has(key)) return;
    paintedChunks.add(key);
    applyBoundary(toggleChunk(boundary, point.chunkX, point.chunkZ));
  }

  function eraseAt(event: L.LeafletMouseEvent): void {
    const boundary = selectedBoundary();
    if (!boundary) return;
    const point = pointAt(event);
    let next = eraseChunk(boundary, point.chunkX, point.chunkZ);
    if (!boundaryHasChunk(boundary, point.chunkX, point.chunkZ)) {
      next = removePolygonVertexNear(boundary, point.blockX, point.blockZ, 8);
    }
    if (hasBoundaryChange(boundary, next)) applyBoundary(next);
  }

  function handleClick(event: L.LeafletMouseEvent): void {
    if (tool === 'polygon') appendPolygonVertex(event);
    else if (tool === 'erase') eraseAt(event);
  }

  function handleMouseDown(event: L.LeafletMouseEvent): void {
    const original = event.originalEvent;
    if (original && 'button' in original && original.button !== 0) return;
    if (tool === 'paint') {
      pointerDown = true;
      paintedChunks = new Set<string>();
      paintChunk(event);
    } else if (tool === 'rect') {
      pointerDown = true;
      const point = pointAt(event);
      rectangleStart = { x: point.chunkX, z: point.chunkZ };
    }
  }

  function handleMouseMove(event: L.LeafletMouseEvent): void {
    onCoordinateChange(pointAt(event));
    if (!pointerDown) return;
    if (tool === 'paint') paintChunk(event);
  }

  function handleMouseUp(event: L.LeafletMouseEvent): void {
    if (tool === 'rect' && rectangleStart) {
      const point = pointAt(event);
      const boundary = selectedBoundary();
      if (boundary) applyBoundary(addChunkRect(boundary, rectangleStart, { x: point.chunkX, z: point.chunkZ }));
    }
    pointerDown = false;
    paintedChunks.clear();
    rectangleStart = null;
  }

  function handleMouseOut(): void {
    if (tool === 'paint') {
      pointerDown = false;
      paintedChunks.clear();
    }
  }

  function setViewModel(viewModel: MapViewModel): void {
    if (destroyed) return;
    model = normalizeViewModel({
      ...model,
      ...viewModel,
    });
    replaceTiles();
    renderShapes();
    renderDraft();
    redrawGrid();
  }

  function setTool(nextTool: EditorTool): void {
    tool = nextTool;
    pointerDown = false;
    paintedChunks.clear();
    rectangleStart = null;
    if (map.dragging) {
      if (tool === 'paint' || tool === 'rect') map.dragging.disable();
      else map.dragging.enable();
    }
  }

  map.on('click', handleClick);
  map.on('mousedown', handleMouseDown);
  map.on('mousemove', handleMouseMove);
  map.on('mouseup', handleMouseUp);
  map.on('mouseout', handleMouseOut);
  map.on('moveend zoomend', redrawGrid);
  replaceTiles();
  renderShapes();
  renderDraft();
  redrawGrid();

  return {
    map,
    setViewModel,
    setTool,
    destroy() {
      if (destroyed) return;
      destroyed = true;
      map.off('click', handleClick);
      map.off('mousedown', handleMouseDown);
      map.off('mousemove', handleMouseMove);
      map.off('mouseup', handleMouseUp);
      map.off('mouseout', handleMouseOut);
      map.off('moveend zoomend', redrawGrid);
      if (tileLayer) map.removeLayer(tileLayer);
      map.remove();
    },
  };
}
