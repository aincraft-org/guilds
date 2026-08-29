export interface BlockPoint {
  x: number;
  z: number;
}

/** Chunk coordinates used by the editor model. */
export interface ChunkPoint {
  x: number;
  z: number;
}

export interface Boundary {
  polygon: BlockPoint[];
  chunks: ChunkPoint[];
}

export type ZoneType = 'WILDERNESS' | 'CLAIMABLE';

export interface ZoneDocument {
  id: string;
  name: string;
  type: ZoneType;
  priority: number;
  boundary: Boundary;
}

export interface TerritoryDocument {
  id: string;
  name: string;
  world: string;
  defaultZoneType: ZoneType;
  boundary: Boundary;
  zones: ZoneDocument[];
  governedByGuildId?: string;
  government?: unknown;
  policies?: unknown[];
}

export interface ApiChunkDocument {
  cx: number;
  cz: number;
}

export interface ApiBoundaryDocument {
  polygon: BlockPoint[];
  chunks: ApiChunkDocument[];
}

export type ApiTerritoryDocument = Omit<TerritoryDocument, 'boundary' | 'zones'> & {
  boundary: ApiBoundaryDocument;
  zones: Array<Omit<ZoneDocument, 'boundary'> & { boundary: ApiBoundaryDocument }>;
};

export type TerritoryWriteDocument = TerritoryDocument | ApiTerritoryDocument;

export interface TerritoryListResponse {
  territories: TerritoryDocument[];
}

export interface EditorMeta {
  authRequired: boolean;
  squaremapTileBaseUrl: string;
  sessionTtlSeconds: number;
  secure: boolean;
}

export interface Selection {
  territoryId: string;
  zoneId?: string;
}

export interface ZoneDraft extends ZoneDocument {}

export interface TerritoryDraft extends TerritoryDocument {
  dirty: boolean;
}

export interface EditorState {
  territories: TerritoryDraft[];
  dirtyIds: ReadonlySet<string>;
  selection: Selection | null;
}

export interface LoginResponse {
  ok: boolean;
  authRequired?: boolean;
}

export interface GuildsApi {
  getMeta(): Promise<EditorMeta>;
  login(token: string): Promise<LoginResponse>;
  logout(): Promise<void>;
  listTerritories(): Promise<TerritoryListResponse>;
  putTerritory(territory: TerritoryWriteDocument): Promise<TerritoryDocument>;
  deleteTerritory(id: string): Promise<void>;
}
