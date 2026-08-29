import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Mocked } from 'vitest';
import { ApiError } from './api';
import { useEditorState } from './useEditorState';
import type { GuildsApi, TerritoryDocument } from './types';

const meta = {
  authRequired: true,
  squaremapTileBaseUrl: 'https://tiles.example.test',
  sessionTtlSeconds: 3600,
  secure: true,
};

function territory(id = 'north'): TerritoryDocument {
  return {
    id,
    name: 'North',
    world: 'world',
    defaultZoneType: 'WILDERNESS',
    boundary: {
      polygon: [{ x: 0, z: 0 }, { x: 16, z: 0 }, { x: 16, z: 16 }],
      chunks: [],
    },
    zones: [],
  };
}

function fakeApi(documents: TerritoryDocument[] = [territory()]): Mocked<GuildsApi> {
  return {
    getMeta: vi.fn().mockResolvedValue(meta),
    login: vi.fn().mockResolvedValue({ ok: true }),
    logout: vi.fn().mockResolvedValue(undefined),
    listTerritories: vi.fn().mockResolvedValue({ territories: documents }),
    putTerritory: vi.fn().mockImplementation(async (document) => document as TerritoryDocument),
    deleteTerritory: vi.fn().mockResolvedValue(undefined),
  } as Mocked<GuildsApi>;
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('useEditorState', () => {
  it('loads metadata and territories in order', async () => {
    const api = fakeApi();
    const { result } = renderHook(() => useEditorState(api));

    await waitFor(() => expect(result.current.status).toBe('ready'));

    expect(api.getMeta.mock.invocationCallOrder[0]).toBeLessThan(api.listTerritories.mock.invocationCallOrder[0]);
    expect(result.current.meta).toEqual(meta);
    expect(result.current.selection).toEqual({ territoryId: 'north' });
  });

  it('enters login-required on a 401 without dropping the loaded draft', async () => {
    const api = fakeApi();
    const { result } = renderHook(() => useEditorState(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    act(() => {
      result.current.createTerritory('Draft Land');
      result.current.updateBoundary({
        polygon: [{ x: 0, z: 0 }, { x: 16, z: 0 }, { x: 16, z: 16 }],
        chunks: [],
      });
    });
    expect(result.current.state.dirtyIds.has('draft-land')).toBe(true);
    api.putTerritory.mockRejectedValueOnce(new ApiError(401, { error: 'unauthorized' }));

    await act(async () => {
      await expect(result.current.save('draft-land')).rejects.toBeInstanceOf(ApiError);
    });

    expect(result.current.status).toBe('login-required');
    expect(result.current.state.territories.some(({ id }) => id === 'draft-land')).toBe(true);
    expect(result.current.state.dirtyIds.has('draft-land')).toBe(true);
  });

  it('clears dirty state only after a successful save and preserves failures', async () => {
    const api = fakeApi();
    const { result } = renderHook(() => useEditorState(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    act(() => result.current.updateSelected({ name: 'Renamed North' }));
    expect(result.current.state.dirtyIds.has('north')).toBe(true);
    await act(async () => result.current.save('north'));
    expect(result.current.state.dirtyIds.has('north')).toBe(false);
    expect(api.putTerritory).toHaveBeenCalledWith(expect.objectContaining({ name: 'Renamed North' }));

    act(() => result.current.updateSelected({ name: 'Unsaved North' }));
    api.putTerritory.mockRejectedValueOnce(new ApiError(422, { message: 'bad geometry' }));
    await act(async () => {
      await expect(result.current.save('north')).rejects.toMatchObject({ status: 422 });
    });
    expect(result.current.state.dirtyIds.has('north')).toBe(true);
    expect(result.current.error).toBe('bad geometry');
  });

  it('registers beforeunload only while drafts are dirty', async () => {
    const api = fakeApi();
    const { result } = renderHook(() => useEditorState(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    const cleanEvent = new Event('beforeunload', { cancelable: true });
    expect(window.dispatchEvent(cleanEvent)).toBe(true);

    act(() => result.current.updateSelected({ name: 'Dirty North' }));
    const dirtyEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(dirtyEvent);
    expect(dirtyEvent.defaultPrevented).toBe(true);
  });

  it('ignores duplicate saves for the same territory while in flight', async () => {
    const api = fakeApi();
    let resolveSave!: (document: TerritoryDocument) => void;
    api.putTerritory.mockImplementationOnce(
      () => new Promise<TerritoryDocument>((resolve) => { resolveSave = resolve; }),
    );
    const { result } = renderHook(() => useEditorState(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));
    act(() => result.current.updateSelected({ name: 'Slow North' }));

    let first!: Promise<void>;
    let second!: Promise<void>;
    act(() => {
      first = result.current.save('north');
      second = result.current.save('north');
    });
    expect(api.putTerritory).toHaveBeenCalledTimes(1);
    resolveSave(territory());
    await act(async () => {
      await first;
      await second;
    });
  });
});
