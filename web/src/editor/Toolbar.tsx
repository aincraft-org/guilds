import type { ReactNode } from 'react';

export type EditorTool = 'select' | 'polygon' | 'paint' | 'rect' | 'erase';

export interface ToolbarProps {
  tool: EditorTool;
  onToolChange: (tool: EditorTool) => void;
  onSave: () => void | Promise<void>;
  dirty: boolean;
  saving: boolean;
  disabled?: boolean;
  children?: ReactNode;
}

const tools: Array<{ id: EditorTool; label: string; hint: string }> = [
  { id: 'select', label: 'Select', hint: 'Select a territory or zone' },
  { id: 'polygon', label: 'Polygon', hint: 'Draw a polygon boundary' },
  { id: 'paint', label: 'Paint', hint: 'Paint chunks into the boundary' },
  { id: 'rect', label: 'Rectangle', hint: 'Add a rectangular chunk area' },
  { id: 'erase', label: 'Erase', hint: 'Erase boundary chunks' },
];

export function Toolbar({
  tool,
  onToolChange,
  onSave,
  dirty,
  saving,
  disabled = false,
  children,
}: ToolbarProps) {
  return (
    <div className="editor-toolbar" aria-label="Editor tools">
      <div className="tool-group" role="toolbar" aria-label="Map drawing tools">
        {tools.map((item) => (
          <button
            className={`tool-button${tool === item.id ? ' is-active' : ''}`}
            key={item.id}
            type="button"
            onClick={() => onToolChange(item.id)}
            aria-pressed={tool === item.id}
            aria-label={item.label}
            disabled={disabled}
          >
            {item.label}
          </button>
        ))}
      </div>
      {children}
      <div className="save-group">
        <span className={`save-state${dirty ? ' is-dirty' : ''}`} role="status" aria-live="polite">
          {saving ? dirty ? 'Saving… · Unsaved changes' : 'Saving…' : dirty ? 'Unsaved changes' : 'Saved'}
        </span>
        <button
          className="button button-primary save-button"
          type="button"
          onClick={() => void onSave()}
          disabled={disabled || saving || !dirty}
          aria-label={saving ? 'Saving changes' : dirty ? 'Save changes' : 'No changes to save'}
        >
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>
    </div>
  );
}
