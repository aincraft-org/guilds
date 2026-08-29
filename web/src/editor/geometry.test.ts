import { describe, expect, it } from 'vitest';
import {
  blockToChunk,
  bukkitToSquaremapWorld,
  fromLeafletPoint,
  snapBlockToChunkCorner,
  toLeafletPoint,
} from './geometry';

describe('editor geometry', () => {
  it('floors negative block coordinates into containing chunks', () => {
    expect(blockToChunk(0, 0)).toEqual({ x: 0, z: 0 });
    expect(blockToChunk(-1, -16)).toEqual({ x: -1, z: -1 });
    expect(blockToChunk(-17, 17)).toEqual({ x: -2, z: 1 });
  });

  it('snaps to chunk corners using a chunk size of 16', () => {
    expect(snapBlockToChunkCorner(-1, 31)).toEqual({ x: -16, z: 16, cx: -1, cz: 1 });
    expect(snapBlockToChunkCorner(32, 32)).toEqual({ x: 32, z: 32, cx: 2, cz: 2 });
  });

  it('maps standard and namespaced Bukkit worlds to squaremap names', () => {
    expect(bukkitToSquaremapWorld('world')).toBe('minecraft_overworld');
    expect(bukkitToSquaremapWorld('')).toBe('minecraft_overworld');
    expect(bukkitToSquaremapWorld('world_nether')).toBe('minecraft_the_nether');
    expect(bukkitToSquaremapWorld('world_the_end')).toBe('minecraft_the_end');
    expect(bukkitToSquaremapWorld('my:custom/world')).toBe('minecraft_my_custom_world');
  });

  it('converts block coordinates to and from Leaflet points at native zoom 3', () => {
    expect(toLeafletPoint(16, -24)).toEqual({ lat: 3, lng: 2 });
    expect(fromLeafletPoint(3, 2)).toEqual({ x: 16, z: -24 });
  });
});
