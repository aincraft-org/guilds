import { Link } from 'react-router-dom';
import { useTheme } from './ThemeProvider';

const GITHUB_URL = 'https://github.com/aincraft-org/guilds';
const DOCS_URL = 'https://github.com/aincraft-org/guilds/tree/master/docs';

export function SiteHeader() {
  const { theme, toggleTheme } = useTheme();
  const nextTheme = theme === 'dark' ? 'light' : 'dark';

  return (
    <header className="site-header">
      <Link className="wordmark" to="/" aria-label="Guilds home">
        Guilds
      </Link>
      <nav className="site-nav" aria-label="Primary navigation">
        <Link to="/editor">Editor</Link>
        <a href={DOCS_URL} target="_blank" rel="noreferrer">
          Docs
        </a>
        <a href={GITHUB_URL} target="_blank" rel="noreferrer">
          GitHub
        </a>
        <button
          className="theme-toggle"
          type="button"
          onClick={toggleTheme}
          aria-label={`Switch to ${nextTheme} theme`}
          aria-pressed={theme === 'light'}
        >
          {nextTheme} mode
        </button>
      </nav>
    </header>
  );
}
