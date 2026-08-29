import { useEffect, useState } from 'react';
import { SiteHeader } from '../components/SiteHeader';
import { getBoundaryTarget } from './model';
import { useEditorState, type EditorController } from './useEditorState';
import type { GuildsApi } from './types';
import { Inspector } from './Inspector';
import { LoginDialog } from './LoginDialog';
import { TerritoryTree } from './TerritoryTree';
import { Toolbar } from './Toolbar';
import type { EditorTool } from './Toolbar';
import { EditorMap } from './EditorMap';
import './editor.css';

export interface MapSurfaceProps {
  controller: EditorController;
  activeTool: EditorTool;
  onToolChange: (tool: EditorTool) => void;
}

export function MapSurface({ controller, activeTool, onToolChange }: MapSurfaceProps) {
  return (
    <EditorMap
      controller={controller}
      activeTool={activeTool}
      onToolChange={onToolChange}
    />
  );
}

interface EditorWorkspaceProps {
  controller: EditorController;
}

function EditorWorkspace({ controller }: EditorWorkspaceProps) {
  const [tool, setTool] = useState<EditorTool>('select');
  const [worldsOpen, setWorldsOpen] = useState(false);
  const [inspectorOpen, setInspectorOpen] = useState(false);
  const target = getBoundaryTarget(controller.state, controller.selection);
  const selectedTerritory = target?.territory ?? null;
  const selectedZone = target?.zone ?? null;
  const dirty = controller.state.dirtyIds.size > 0;
  const selectedSaving = selectedTerritory ? controller.savingIds.has(selectedTerritory.id) : false;

  useEffect(() => {
    function closeDrawers(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setWorldsOpen(false);
        setInspectorOpen(false);
      }
    }
    window.addEventListener('keydown', closeDrawers);
    return () => window.removeEventListener('keydown', closeDrawers);
  }, []);

  function createTerritory() {
    const name = window.prompt('Territory name');
    if (name?.trim()) controller.createTerritory(name.trim());
  }

  function createZone(territoryId: string) {
    const name = window.prompt('Zone name');
    if (name?.trim()) controller.createZone(name.trim(), territoryId);
  }

  function save() {
    void controller.save().catch(() => undefined);
  }

  async function logout() {
    await controller.logout();
  }

  const connectionText = controller.status === 'ready'
    ? 'Connected'
    : controller.status === 'loading'
      ? 'Connecting…'
      : controller.status === 'login-required'
        ? 'Authentication required'
        : 'Offline';

  return (
    <div className="site-shell editor-shell">
      <SiteHeader />
      <header className="editor-header">
        <div>
          <p className="eyebrow">World editor</p>
          <h1>Territory workspace</h1>
        </div>
        <div className="editor-session">
          <span className={`connection-status connection-${controller.status}`} role="status" aria-label={`Connection status: ${connectionText}`} aria-live="polite">
            <span className="connection-dot" aria-hidden="true" />
            {connectionText}
          </span>
          {controller.status === 'offline' && (
            <button className="button button-secondary" type="button" onClick={() => void controller.retry()}>
              Retry connection
            </button>
          )}
          {controller.status === 'ready' && (
            <button className="button button-secondary" type="button" onClick={() => void logout()}>
              Log out
            </button>
          )}
        </div>
      </header>
      {controller.error && controller.status !== 'login-required' && (
        <div className="editor-banner editor-banner-error" role="alert">{controller.error}</div>
      )}
      {controller.status === 'offline' && !controller.error && (
        <div className="editor-banner" role="status">The editor is offline. Your local drafts remain available.</div>
      )}
      <div className="drawer-controls" aria-label="Editor panels">
        <button className="button button-secondary" type="button" aria-expanded={worldsOpen} aria-controls="worlds-panel" onClick={() => setWorldsOpen((open) => !open)}>
          Worlds
        </button>
        <button className="button button-secondary" type="button" aria-expanded={inspectorOpen} aria-controls="inspector-panel" onClick={() => setInspectorOpen((open) => !open)}>
          Inspector
        </button>
      </div>
      <main className="editor-workspace">
        <aside id="worlds-panel" className={`editor-panel worlds-panel${worldsOpen ? ' drawer-open' : ''}`} aria-labelledby="territory-tree-title">
          <TerritoryTree
            territories={controller.state.territories}
            selection={controller.selection}
            onSelect={(selection) => {
              controller.select(selection);
              setInspectorOpen(true);
            }}
            onCreateTerritory={createTerritory}
            onCreateZone={createZone}
          />
        </aside>
        <section className="editor-center" aria-label="Map editor">
          <Toolbar
            tool={tool}
            onToolChange={setTool}
            onSave={save}
            dirty={dirty}
            saving={selectedSaving}
            disabled={controller.status !== 'ready' || !selectedTerritory}
          />
          <MapSurface controller={controller} activeTool={tool} onToolChange={setTool} />
        </section>
        <aside id="inspector-panel" className={`editor-panel inspector-panel${inspectorOpen ? ' drawer-open' : ''}`}>
          <Inspector
            territory={selectedTerritory}
            zone={selectedZone}
            boundary={target?.boundary ?? null}
            onUpdate={controller.updateSelected}
            onDelete={() => controller.removeSelected()}
            deleting={selectedTerritory ? controller.savingIds.has(selectedTerritory.id) : false}
          />
        </aside>
      </main>
      <LoginDialog open={controller.status === 'login-required'} onLogin={controller.login} error={controller.error} />
    </div>
  );
}

function ConnectedEditorPage({ api }: { api?: GuildsApi }) {
  const controller = useEditorState(api);
  return <EditorWorkspace controller={controller} />;
}

export interface EditorPageProps {
  /** Optional controller injection keeps the shell independently testable. */
  controller?: EditorController;
  api?: GuildsApi;
}

export function EditorPage({ controller, api }: EditorPageProps) {
  if (controller) return <EditorWorkspace controller={controller} />;
  return <ConnectedEditorPage api={api} />;
}

export { Inspector } from './Inspector';
export { LoginDialog } from './LoginDialog';
export { TerritoryTree } from './TerritoryTree';
export { Toolbar } from './Toolbar';
export type { EditorTool } from './Toolbar';
