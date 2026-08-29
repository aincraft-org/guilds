import { lazy, Suspense } from 'react';
import { Link, Route, Routes } from 'react-router-dom';
import { HomePage } from './pages/HomePage';


const EditorPage = lazy(async () => {
  const module = await import('./editor/EditorPage');
  return { default: module.EditorPage };
});

function NotFoundPage() {
  return (
    <div className="site-shell placeholder-page">
      <main className="placeholder-content" aria-labelledby="not-found-title">
        <p className="eyebrow">404</p>
        <h1 id="not-found-title">Page not found</h1>
        <p>That Guilds page does not exist.</p>
        <Link className="button button-secondary" to="/">
          Return home
        </Link>
      </main>
    </div>
  );
}

export function App() {
  return (
    <Suspense fallback={<div className="route-loading" role="status">Loading…</div>}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/editor" element={<EditorPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}
