import { Link } from 'react-router-dom';
import { SiteHeader } from '../components/SiteHeader';

const GITHUB_URL = 'https://github.com/aincraft-org/guilds';
const DOCS_URL = 'https://github.com/aincraft-org/guilds/tree/master/docs';

const capabilities = [
  {
    number: '01',
    title: 'Territory authoring',
    description:
      'Shape territories and nested zones on a visual map, with clear ownership and boundaries for every world.',
  },
  {
    number: '02',
    title: 'Guild systems',
    description:
      'Give communities a durable home in your server with permissions, membership, and territory-aware gameplay.',
  },
  {
    number: '03',
    title: 'SQL persistence',
    description:
      'Keep server data authoritative and durable with the existing SQL-backed persistence layer.',
  },
  {
    number: '04',
    title: 'Squaremap integration',
    description:
      'See territory boundaries in context through squaremap tiles and a map workflow built for Paper servers.',
  },
];

export function HomePage() {
  return (
    <div className="site-shell">
      <SiteHeader />
      <main>
        <section className="hero" aria-labelledby="hero-title">
          <p className="eyebrow">Paper plugin / territory systems</p>
          <h1 id="hero-title">Guilds</h1>
          <p className="hero-copy">
            A focused territory and guild toolkit for Paper servers. Author the world your
            players share, then let the server keep it consistent.
          </p>
          <div className="hero-actions">
            <Link className="button button-primary" to="/editor">
              Open editor
            </Link>
            <a className="button button-secondary" href={DOCS_URL} target="_blank" rel="noreferrer">
              Read the docs
            </a>
          </div>
        </section>

        <section className="content-section" aria-labelledby="capabilities-title">
          <div className="section-heading">
            <p className="eyebrow">Built for the whole server</p>
            <h2 id="capabilities-title">A clear foundation for shared worlds.</h2>
          </div>
          <div className="capability-list">
            {capabilities.map((capability) => (
              <article className="capability" key={capability.number}>
                <span className="capability-number" aria-hidden="true">
                  {capability.number}
                </span>
                <div>
                  <h3>{capability.title}</h3>
                  <p>{capability.description}</p>
                </div>
              </article>
            ))}
          </div>
        </section>

        <aside className="security-note" aria-labelledby="security-title">
          <p className="eyebrow">Operator security</p>
          <h2 id="security-title">Your server stays in control.</h2>
          <p>
            The editor accepts an operator-provided token only to exchange it for a server
            session. The token is sent over the request, cleared after login, and never retained
            in browser storage.
          </p>
        </aside>
      </main>

      <footer className="site-footer">
        <span>Guilds for Paper</span>
        <nav aria-label="Footer navigation">
          <Link to="/editor">Editor</Link>
          <a href={DOCS_URL} target="_blank" rel="noreferrer">
            Docs
          </a>
          <a href={GITHUB_URL} target="_blank" rel="noreferrer">
            GitHub
          </a>
        </nav>
      </footer>
    </div>
  );
}
