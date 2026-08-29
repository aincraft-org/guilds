import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Mock } from 'vitest';
import { createMapController } from './mapController';
import type { Boundary } from './types';

interface FakeLayer {
  addTo: Mock;
  on: Mock;
  off: Mock;
  clearLayers?: Mock;
  emit: (event: string, payload?: unknown) => void;
}

interface FakeMap {
  on: Mock;
  off: Mock;
  getZoom: Mock;
  getBounds: Mock;
  removeLayer: Mock;
  remove: Mock;
  dragging: { disable: Mock; enable: Mock };
  emit: (event: string, payload?: unknown) => void;
}

const leaflet = vi.hoisted(() => {
  const mapHandlers = new Map<string, (...args: never[]) => void>();
  const layers: FakeLayer[] = [];
  const map: FakeMap = {
    on: vi.fn((events: string, handler: (...args: never[]) => void) => {
      for (const event of events.split(' ')) mapHandlers.set(event, handler);
      return map;
    }),
    off: vi.fn((events: string) => {
      for (const event of events.split(' ')) mapHandlers.delete(event);
      return map;
    }),
    getZoom: vi.fn(() => 3),
    getBounds: vi.fn(() => ({
      getSouthWest: () => ({ lat: -8, lng: -8 }),
      getNorthEast: () => ({ lat: 8, lng: 8 }),
    })),
    removeLayer: vi.fn(),
    remove: vi.fn(),
    dragging: { disable: vi.fn(), enable: vi.fn() },
    emit(event: string, payload?: unknown) {
      mapHandlers.get(event)?.(payload as never);
    },
  };

  function layer(clear = false): FakeLayer {
    const handlers = new Map<string, (...args: never[]) => void>();
    const value: FakeLayer = {
      addTo: vi.fn(() => value),
      on: vi.fn((event: string, handler: (...args: never[]) => void) => {
        handlers.set(event, handler);
        return value;
      }),
      off: vi.fn((event: string) => {
        handlers.delete(event);
        return value;
      }),
      emit(event: string, payload?: unknown) {
        handlers.get(event)?.(payload as never);
      },
    };
    if (clear) value.clearLayers = vi.fn();
    layers.push(value);
    return value;
  }

  const mapFactory = vi.fn(() => map);
  const layerGroup = vi.fn(() => layer(true));
  const polygon = vi.fn(() => layer());
  const polyline = vi.fn(() => layer());
  const tileLayer = vi.fn(() => layer());
  return {
    CRS: { Simple: {} },
    map: mapFactory,
    layerGroup,
    polygon,
    polyline,
    tileLayer,
    layers,
    fakeMap: map,
    reset() {
      mapHandlers.clear();
      layers.length = 0;
      mapFactory.mockClear();
      layerGroup.mockClear();
      polygon.mockClear();
      polyline.mockClear();
      tileLayer.mockClear();
      map.on.mockClear();
      map.off.mockClear();
      map.removeLayer.mockClear();
      map.remove.mockClear();
    },
  };
});

vi.mock('leaflet', () => ({ default: leaflet }));

afterEach(() => leaflet.reset());

const boundary: Boundary = {
  polygon: [],
  chunks: [],
};

function eventAt(blockX: number, blockZ: number) {
  return {
    latlng: {
      lat: -blockZ / 8,
      lng: blockX / 8,
    },
    originalEvent: { button: 0 },
  };
}

describe('createMapController', () => {
  it('creates a squaremap-compatible map and tile URL', () => {
    const controller = createMapController(document.createElement('div'), {
      world: 'world_nether',
      tileBaseUrl: 'https://tiles.example.test/',
      selectedBoundary: boundary,
    });

    expect(leaflet.map).toHaveBeenCalledWith(expect.any(HTMLDivElement), expect.objectContaining({
      crs: leaflet.CRS.Simple,
      zoom: 3,
      minZoom: 0,
      maxZoom: 5,
      attributionControl: false,
      preferCanvas: true,
    }));
    expect(leaflet.tileLayer).toHaveBeenCalledWith(
      'https://tiles.example.test/tiles/minecraft_the_nether/{z}/{x}_{y}.png',
      expect.objectContaining({ tileSize: 512, maxNativeZoom: 3 }),
    );
    controller.destroy();
  });

  it('updates the tile layer when the world changes and keeps the old layer removable', () => {
    const controller = createMapController('map', { tileBaseUrl: 'https://tiles.test', world: 'world' });
    controller.setViewModel({ world: 'world_the_end' });
    expect(leaflet.tileLayer).toHaveBeenCalledTimes(2);
    expect(leaflet.fakeMap.removeLayer).toHaveBeenCalledTimes(1);
    controller.destroy();
  });

  it('appends snapped polygon vertices and paints one entered chunk', () => {
    const changes: Boundary[] = [];
    const controller = createMapController('map', {
      selectedBoundary: boundary,
      onBoundaryChange: (next) => changes.push(next),
    });
    controller.setTool('polygon');
    leaflet.fakeMap.emit('click', eventAt(17, -1));
    expect(changes.at(-1)?.polygon).toEqual([{ x: 16, z: -16 }]);

    const painted: Boundary = { polygon: [], chunks: [] };
    controller.setViewModel({ selectedBoundary: painted });
    controller.setTool('paint');
    leaflet.fakeMap.emit('mousedown', eventAt(1, 1));
    leaflet.fakeMap.emit('mousemove', eventAt(1, 1));
    leaflet.fakeMap.emit('mouseup', eventAt(1, 1));
    expect(changes.at(-1)?.chunks).toEqual([{ x: 0, z: 0 }]);
    controller.destroy();
  });

  it('adds inclusive rectangle chunks and erases chunks before vertices', () => {
    const changes: Boundary[] = [];
    const controller = createMapController('map', {
      selectedBoundary: boundary,
      onBoundaryChange: (next) => changes.push(next),
    });
    controller.setTool('rect');
    leaflet.fakeMap.emit('mousedown', eventAt(0, 0));
    leaflet.fakeMap.emit('mouseup', eventAt(17, 17));
    expect(changes.at(-1)?.chunks).toHaveLength(4);

    const withChunk: Boundary = { polygon: [], chunks: [{ x: 0, z: 0 }] };
    controller.setViewModel({ selectedBoundary: withChunk });
    controller.setTool('erase');
    leaflet.fakeMap.emit('click', eventAt(1, 1));
    expect(changes.at(-1)?.chunks).toEqual([]);
    controller.destroy();
  });

  it('caps the chunk grid and removes listeners on destroy', () => {
    leaflet.fakeMap.getBounds.mockReturnValue({
      getSouthWest: () => ({ lat: -10_000, lng: -10_000 }),
      getNorthEast: () => ({ lat: 10_000, lng: 10_000 }),
    });
    const controller = createMapController('map');
    const initialPolylineCalls = leaflet.polyline.mock.calls.length;
    expect(initialPolylineCalls).toBe(0);
    controller.destroy();
    expect(leaflet.fakeMap.off).toHaveBeenCalled();
    expect(leaflet.fakeMap.remove).toHaveBeenCalledTimes(1);
  });
});
