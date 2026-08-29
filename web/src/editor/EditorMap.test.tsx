import { render } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { EditorController } from './useEditorState';
import type { Boundary } from './types';

const mapController = vi.hoisted(() => ({
  setViewModel: vi.fn(),
  setTool: vi.fn(),
  destroy: vi.fn(),
  map: {},
}));
const createMapController = vi.hoisted(() => vi.fn(() => mapController));

vi.mock('./mapController', () => ({
  createMapController,
}));

import { EditorMap } from './EditorMap';

const boundary: Boundary = { polygon: [], chunks: [] };
const controller = {
  status: 'ready',
  meta: {
    authRequired: false,
    squaremapTileBaseUrl: 'https://tiles.example.test',
    sessionTtlSeconds: 60,
    secure: true,
  },
  state: {
    territories: [{
      id: 'north',
      name: 'North',
      world: 'world',
      defaultZoneType: 'WILDERNESS',
      boundary,
      zones: [],
      dirty: false,
    }],
    dirtyIds: new Set<string>(),
    selection: { territoryId: 'north' },
  },
  selection: { territoryId: 'north' },
  error: null,
  savingIds: new Set<string>(),
  updateBoundary: vi.fn(),
  select: vi.fn(),
} as unknown as EditorController;

beforeEach(() => {
  createMapController.mockClear();
  mapController.setViewModel.mockClear();
  mapController.setTool.mockClear();
  mapController.destroy.mockClear();
});

describe('EditorMap', () => {
  it('creates one controller, updates it without rebuilding, and destroys it on unmount', () => {
    const view = render(
      <EditorMap controller={controller} activeTool="select" onToolChange={vi.fn()} />,
    );
    expect(createMapController).toHaveBeenCalledTimes(1);
    expect(mapController.setViewModel).toHaveBeenCalled();
    expect(mapController.setTool).toHaveBeenCalledWith('select');

    view.rerender(
      <EditorMap controller={controller} activeTool="paint" onToolChange={vi.fn()} />,
    );
    expect(createMapController).toHaveBeenCalledTimes(1);
    expect(mapController.setTool).toHaveBeenCalledWith('paint');

    view.unmount();
    expect(mapController.destroy).toHaveBeenCalledTimes(1);
  });
});
