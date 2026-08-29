import { useState } from 'react';
import type { Boundary, TerritoryDraft, ZoneDraft } from './types';

export interface InspectorProps {
  territory: TerritoryDraft | null;
  zone: ZoneDraft | null;
  boundary: Boundary | null;
  onUpdate: (patch: Record<string, unknown>) => void;
  onDelete: () => void | Promise<void>;
  deleting?: boolean;
}

function geometrySummary(boundary: Boundary | null) {
  return {
    vertices: boundary?.polygon.length ?? 0,
    chunks: boundary?.chunks.length ?? 0,
  };
}

export function Inspector({ territory, zone, boundary, onUpdate, onDelete, deleting = false }: InspectorProps) {
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const selected = zone ?? territory;
  const geometry = geometrySummary(boundary);

  if (!selected || !territory) {
    return (
      <aside className="inspector" aria-labelledby="inspector-title">
        <div className="panel-heading"><h2 id="inspector-title">Inspector</h2></div>
        <p className="empty-state">Select a territory or zone to inspect it.</p>
      </aside>
    );
  }

  const title = zone ? 'Zone inspector' : 'Territory inspector';
  return (
    <aside className="inspector" aria-labelledby="inspector-title">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Details</p>
          <h2 id="inspector-title">{title}</h2>
        </div>
        {territory.dirty && <span className="dirty-label">Dirty</span>}
      </div>
      <form className="inspector-form" onSubmit={(event) => event.preventDefault()}>
        <label htmlFor="editor-name">Name</label>
        <input
          id="editor-name"
          value={selected.name}
          onChange={(event) => onUpdate({ name: event.target.value })}
        />
        {!zone && (
          <>
            <label htmlFor="editor-world">World</label>
            <input
              id="editor-world"
              value={territory.world}
              onChange={(event) => onUpdate({ world: event.target.value })}
            />
            <label htmlFor="editor-default-zone">Default zone type</label>
            <select
              id="editor-default-zone"
              value={territory.defaultZoneType}
              onChange={(event) => onUpdate({ defaultZoneType: event.target.value })}
            >
              <option value="WILDERNESS">Wilderness</option>
              <option value="CLAIMABLE">Claimable</option>
            </select>
          </>
        )}
        {zone && (
          <>
            <label htmlFor="editor-zone-type">Zone type</label>
            <select id="editor-zone-type" value={zone.type} onChange={(event) => onUpdate({ type: event.target.value })}>
              <option value="WILDERNESS">Wilderness</option>
              <option value="CLAIMABLE">Claimable</option>
            </select>
            <label htmlFor="editor-priority">Priority</label>
            <input
              id="editor-priority"
              type="number"
              value={zone.priority}
              onChange={(event) => onUpdate({ priority: Number(event.target.value) || 0 })}
            />
          </>
        )}
      </form>
      <dl className="geometry-summary" aria-label="Geometry counts">
        <div><dt>Polygon vertices</dt><dd>{geometry.vertices}</dd></div>
        <div><dt>Chunks</dt><dd>{geometry.chunks}</dd></div>
      </dl>
      {confirmingDelete ? (
        <div className="delete-confirm" role="alertdialog" aria-label="Confirm deletion">
          <p>Delete <strong>{selected.name}</strong>? This cannot be undone.</p>
          <div className="inline-actions">
            <button className="button button-danger" type="button" onClick={() => void onDelete()} disabled={deleting}>
              {deleting ? 'Deleting…' : 'Confirm delete'}
            </button>
            <button className="button button-secondary" type="button" onClick={() => setConfirmingDelete(false)} disabled={deleting}>
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <button className="button button-danger delete-button" type="button" onClick={() => setConfirmingDelete(true)}>
          Delete {zone ? 'zone' : 'territory'}
        </button>
      )}
    </aside>
  );
}
