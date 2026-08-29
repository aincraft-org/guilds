import type { BlockPoint, ChunkPoint } from './types';

export const CHUNK_SIZE = 16;
export const SQUAREMAP_NATIVE_ZOOM = 3;
export const SQUAREMAP_SCALE = 1 / 2 ** SQUAREMAP_NATIVE_ZOOM;

/** Convert a block coordinate to its containing chunk, including negative values. */
export function blockToChunk(blockX: number, blockZ: number): ChunkPoint {
  return { x: Math.floor(blockX / CHUNK_SIZE), z: Math.floor(blockZ / CHUNK_SIZE) };
}

export interface ChunkCorner extends BlockPoint {
  cx: number;
  cz: number;
}

/** Return the block corner of the block's containing chunk. */
export function snapBlockToChunkCorner(blockX: number, blockZ: number): ChunkCorner {
  const chunk = blockToChunk(blockX, blockZ);
  return {
    x: chunk.x * CHUNK_SIZE,
    z: chunk.z * CHUNK_SIZE,
    cx: chunk.x,
    cz: chunk.z,
  };
}

export function bukkitToSquaremapWorld(bukkitName: string): string {
  if (!bukkitName || bukkitName === 'world') return 'minecraft_overworld';
  if (bukkitName === 'world_nether') return 'minecraft_the_nether';
  if (bukkitName === 'world_the_end') return 'minecraft_the_end';
  return `minecraft_${String(bukkitName).replace(/[:/]/g, '_')}`;
}

export interface LeafletPoint {
  lat: number;
  lng: number;
}

export function toLeafletPoint(x: number, z: number): LeafletPoint {
  return { lat: -z * SQUAREMAP_SCALE, lng: x * SQUAREMAP_SCALE };
}

export function fromLeafletPoint(lat: number, lng: number): BlockPoint {
  return { x: lng / SQUAREMAP_SCALE, z: -lat / SQUAREMAP_SCALE };
}
