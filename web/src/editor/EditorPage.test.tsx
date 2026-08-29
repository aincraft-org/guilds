import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ThemeProvider } from '../components/ThemeProvider';
import type { EditorController } from './useEditorState';

vi.mock('./EditorMap', () => ({
  EditorMap: ({ activeTool }: { activeTool: string }) => <div data-testid="editor-map">Map {activeTool}</div>,
}));

import { EditorPage } from './EditorPage';

function territory(dirty = false) {
  return {
    id: 'overworld',
    name: 'Overworld',
    world: 'world',
    defaultZoneType: 'WILDERNESS' as const,
    boundary: { polygon: [{ x: 0, z: 0 }], chunks: [{ x: 1, z: 1 }] },
    zones: [],
    dirty,
  };
}

function controller(overrides: Partial<EditorController> = {}): EditorController {
  const draft = territory();
  return {
    status: 'ready',
    meta: { authRequired: true, squaremapTileBaseUrl: '', sessionTtlSeconds: 60, secure: true },
    state: { territories: [draft], dirtyIds: new Set(), selection: { territoryId: draft.id } },
    selection: { territoryId: draft.id },
    error: null,
    savingIds: new Set(),
    login: vi.fn(async () => undefined),
    logout: vi.fn(async () => undefined),
    retry: vi.fn(async () => undefined),
    select: vi.fn(),
    createTerritory: vi.fn(),
    createZone: vi.fn(),
    updateSelected: vi.fn(),
    updateBoundary: vi.fn(),
    save: vi.fn(async () => undefined),
    removeSelected: vi.fn(async () => undefined),
    ...overrides,
  };
}

function renderEditor(value: EditorController) {
  return render(
    <MemoryRouter>
      <ThemeProvider><EditorPage controller={value} /></ThemeProvider>
    </MemoryRouter>,
  );
}

describe('EditorPage', () => {
  it('renders the ready workspace and connection state', () => {
    renderEditor(controller());
    expect(screen.getByRole('heading', { name: 'Territory workspace' })).toBeVisible();
    expect(screen.getByRole('status', { name: /connected/i })).toBeVisible();
    expect(screen.getAllByText('Overworld')[0]).toBeVisible();
    expect(screen.getByRole('button', { name: 'Polygon' })).toBeVisible();
  });

  it('exposes dirty and saving states in text', () => {
    const draft = territory(true);
    const value = controller({
      state: { territories: [draft], dirtyIds: new Set(['overworld']), selection: { territoryId: 'overworld' } },
      savingIds: new Set(['overworld']),
    });
    renderEditor(value);
    expect(screen.getAllByText(/saving/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/unsaved changes/i)).toBeVisible();
  });

  it('keeps the territory tree visible behind the login dialog', () => {
    const value = controller({ status: 'login-required', error: null });
    renderEditor(value);
    expect(screen.getByRole('dialog')).toBeVisible();
    expect(screen.getAllByText('Overworld')[0]).toBeVisible();
    expect(screen.getByLabelText('Editor token')).toBeVisible();
  });

  it('closes narrow drawers on Escape', () => {
    renderEditor(controller());
    const worlds = screen.getByRole('button', { name: 'Worlds', hidden: true });
    fireEvent.click(worlds);
    expect(worlds).toHaveAttribute('aria-expanded', 'true');
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(worlds).toHaveAttribute('aria-expanded', 'false');
  });

  it('requires confirmation before deleting', () => {
    const value = controller();
    renderEditor(value);
    fireEvent.click(screen.getByRole('button', { name: /delete territory/i }));
    expect(screen.getByRole('alertdialog')).toBeVisible();
    expect(value.removeSelected).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /confirm delete/i }));
    expect(value.removeSelected).toHaveBeenCalledTimes(1);
  });

  it('clears the token after a failed login without echoing it', async () => {
    const onLogin = vi.fn(async () => { throw new Error('token leaked should not render'); });
    const value = controller({ status: 'login-required', login: onLogin });
    renderEditor(value);
    const input = screen.getByLabelText('Editor token');
    fireEvent.change(input, { target: { value: 'secret-value' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => expect(input).toHaveValue(''));
    expect(screen.getByRole('alert')).toHaveTextContent(/invalid token/i);
    expect(screen.queryByText(/secret-value|token leaked/i)).not.toBeInTheDocument();
  });
});
