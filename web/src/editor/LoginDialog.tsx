import { useState } from 'react';
import type { FormEvent } from 'react';

export interface LoginDialogProps {
  open: boolean;
  onLogin: (token: string) => Promise<void>;
  error?: string | null;
}

export function LoginDialog({ open, onLogin, error }: LoginDialogProps) {
  const [token, setToken] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  if (!open) return null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token.trim() || submitting) return;
    setSubmitting(true);
    setLoginError(null);
    try {
      await onLogin(token);
    } catch {
      // Do not expose API details or echo the credential in an error message.
      setLoginError('Invalid token. Check your token and try again.');
    } finally {
      setToken('');
      setSubmitting(false);
    }
  }

  const visibleError = loginError ?? (error ? 'Invalid token. Check your token and try again.' : null);
  return (
    <div className="login-backdrop">
      <section className="login-dialog" role="dialog" aria-modal="true" aria-labelledby="login-title" aria-describedby="login-description">
        <p className="eyebrow">Authenticated workspace</p>
        <h2 id="login-title">Sign in to edit</h2>
        <p id="login-description">Enter the editor token to load and save territory drafts.</p>
        <form onSubmit={submit}>
          <label htmlFor="editor-token">Editor token</label>
          <input
            id="editor-token"
            name="token"
            type="password"
            autoFocus
            autoComplete="off"
            value={token}
            onChange={(event) => setToken(event.target.value)}
            aria-invalid={visibleError ? 'true' : 'false'}
            aria-describedby={visibleError ? 'login-error' : 'login-description'}
            disabled={submitting}
          />
          {visibleError && <p id="login-error" className="form-error" role="alert">{visibleError}</p>}
          <button className="button button-primary login-submit" type="submit" disabled={submitting || !token.trim()}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </section>
    </div>
  );
}
