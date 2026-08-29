import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ThemeProvider } from '../components/ThemeProvider';
import { HomePage } from './HomePage';

function renderHome() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <ThemeProvider>
        <HomePage />
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe('HomePage', () => {
  it('shows the Guilds landing page and its primary editor action', () => {
    renderHome();

    expect(screen.getByRole('heading', { level: 1, name: 'Guilds' })).toBeVisible();
    expect(screen.getByRole('link', { name: /open editor/i })).toHaveAttribute('href', '/editor');
    expect(screen.getByText(/operator-provided token/i)).toBeVisible();
  });
});
