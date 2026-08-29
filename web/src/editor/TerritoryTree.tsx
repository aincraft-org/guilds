import { useState } from 'react';
import type { Selection, TerritoryDraft } from './types';

export interface TerritoryTreeProps {
  territories: readonly TerritoryDraft[];
  selection: Selection | null;
  onSelect: (selection: Selection) => void;
  onCreateTerritory: () => void;
  onCreateZone: (territoryId: string) => void;
}

export function TerritoryTree({
  territories,
  selection,
  onSelect,
  onCreateTerritory,
  onCreateZone,
}: TerritoryTreeProps) {
  const [worldFilter, setWorldFilter] = useState('all');
  const worlds = [...new Set(territories.map((territory) => territory.world))].sort();
  const visibleWorlds = worldFilter === 'all' ? worlds : worlds.filter((world) => world === worldFilter);

  return (
    <div className="territory-tree">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Worlds</p>
          <h2 id="territory-tree-title">Territories</h2>
        </div>
        <button className="icon-button" type="button" onClick={onCreateTerritory} aria-label="Create territory" title="Create territory">
          +
        </button>
      </div>
      <label className="world-selector-label" htmlFor="world-selector">World</label>
      <select id="world-selector" className="world-selector" value={worldFilter} onChange={(event) => setWorldFilter(event.target.value)}>
        <option value="all">All worlds</option>
        {worlds.map((world) => <option value={world} key={world}>{world}</option>)}
      </select>
      {visibleWorlds.length === 0 ? (
        <p className="empty-state">No territories yet. Create one to begin mapping.</p>
      ) : (
        <div className="world-list">
          {visibleWorlds.map((world) => (
            <section className="world-group" key={world} aria-labelledby={`world-${world}`}>
              <h3 id={`world-${world}`} className="world-label">{world}</h3>
              <ul className="territory-list">
                {territories.filter((territory) => territory.world === world).map((territory) => (
                  <li key={territory.id}>
                    <button
                      type="button"
                      className={`tree-item${selection?.territoryId === territory.id && !selection.zoneId ? ' is-selected' : ''}`}
                      onClick={() => onSelect({ territoryId: territory.id })}
                      aria-current={selection?.territoryId === territory.id && !selection.zoneId ? 'true' : undefined}
                    >
                      <span className="tree-item-name">{territory.name}</span>
                      {territory.dirty && <span className="dirty-dot" aria-label="Unsaved changes">*</span>}
                    </button>
                    <div className="zone-list" aria-label={`${territory.name} zones`}>
                      {territory.zones.map((zone) => (
                        <button
                          className={`tree-item tree-zone${selection?.territoryId === territory.id && selection.zoneId === zone.id ? ' is-selected' : ''}`}
                          key={zone.id}
                          type="button"
                          onClick={() => onSelect({ territoryId: territory.id, zoneId: zone.id })}
                          aria-current={selection?.territoryId === territory.id && selection.zoneId === zone.id ? 'true' : undefined}
                        >
                          <span className="tree-item-name">{zone.name}</span>
                        </button>
                      ))}
                      <button className="add-zone" type="button" onClick={() => onCreateZone(territory.id)}>
                        + Add zone
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
      <button className="button button-secondary create-territory" type="button" onClick={onCreateTerritory}>
        + New territory
      </button>
    </div>
  );
}
