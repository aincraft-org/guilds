import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { SiteHeader } from './SiteHeader';
import { ThemeProvider } from './ThemeProvider';

describe('ThemeProvider', () => {
  it('follows dark defaults and persists the selected light theme', async () => {
    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <ThemeProvider>
          <SiteHeader />
        </ThemeProvider>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: /theme/i }));

    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
    expect(localStorage.getItem('guilds-theme')).toBe('light');
  });
});
