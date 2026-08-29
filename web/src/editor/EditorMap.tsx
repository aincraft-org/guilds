import { useEffect, useRef, useState } from 'react';
import { getBoundaryTarget } from './model';
import type { EditorController } from './useEditorState';
import type { Boundary } from './types';
import { createMapController, type MapCoordinate, type MapController } from './mapController';
import type { EditorTool } from './Toolbar';
import './editor.css';
import 'leaflet/dist/leaflet.css';

export interface EditorMapProps {
  controller: EditorController;
  activeTool: EditorTool;
  onToolChange: (tool: EditorTool) => void;
}

function coordinateLabel(coordinate: MapCoordinate | null): string {
  if (!coordinate) return 'Coordinates: move over the map';
  return `Block ${coordinate.blockX}, ${coordinate.blockZ} · Chunk ${coordinate.chunkX}, ${coordinate.chunkZ}`;
}

export function EditorMap({ controller, activeTool }: EditorMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapController | null>(null);
  const controllerRef = useRef(controller);
  const [coordinate, setCoordinate] = useState<MapCoordinate | null>(null);
  const [basemapAvailable, setBasemapAvailable] = useState(true);
  controllerRef.current = controller;

  const target = getBoundaryTarget(controller.state, controller.selection);
  const world = target?.territory.world ?? controller.state.territories[0]?.world ?? 'world';
  const selectedBoundary: Boundary | null = target?.boundary ?? null;
  const tileBaseUrl = controller.meta?.squaremapTileBaseUrl ?? '';
  const territories = controller.state.territories;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;
    const map = createMapController(container, {
      onBoundaryChange: (boundary) => controllerRef.current.updateBoundary(boundary),
      onSelect: (selection) => controllerRef.current.select(selection),
      onCoordinateChange: setCoordinate,
      onBasemapStatusChange: setBasemapAvailable,
    });
    mapRef.current = map;
    return () => {
      map.destroy();
      mapRef.current = null;
    };
  }, []);

  useEffect(() => {
    mapRef.current?.setViewModel({
      world,
      tileBaseUrl,
      territories,
      selection: controller.selection,
      selectedBoundary,
    });
  }, [controller.selection, selectedBoundary, territories, tileBaseUrl, world]);

  useEffect(() => {
    mapRef.current?.setTool(activeTool);
  }, [activeTool]);

  return (
    <section className="map-surface" aria-label="Territory map" data-tool={activeTool}>
      <div className="map-host" ref={containerRef} />
      <div className="map-overlay" aria-live="polite">
        <span className="map-coordinate">{coordinateLabel(coordinate)}</span>
        <span className="map-tool-status">Tool: {activeTool}</span>
      </div>
      {tileBaseUrl && !basemapAvailable && (
        <p className="map-basemap-warning" role="status">
          Basemap tiles unavailable — chunk grid and editing remain available.
        </p>
      )}
    </section>
  );
}
