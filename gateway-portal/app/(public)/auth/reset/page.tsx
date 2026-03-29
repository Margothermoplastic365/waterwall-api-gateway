'use client';

import { Suspense, useState, FormEvent } from 'react';
import { useSearchParams } from 'next/navigation';

const API_BASE = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

export const dynamic = 'force-dynamic';

function ResetPasswordContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get('token');

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  if (!token) {
    return (
      <main className="auth-container">
        <div className="auth-card text-center">
          <h1 style={{ marginBottom: 8 }}>Invalid Link</h1>
          <p className="text-muted" style={{ marginBottom: 24 }}>
            This password reset link is invalid or missing. Please request a new one.
          </p>
          <a href="/auth/forgot" className="btn btn-primary">
            Request New Link
          </a>
        </div>
      </main>
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');

    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters');
      return;
    }

    setLoading(true);

    try {
      const res = await fetch(`${API_BASE}/v1/auth/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, password }),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message || 'Password reset failed. The link may be expired.');
      }

      setSuccess(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'An unexpected error occurred');
    } finally {
      setLoading(false);
    }
  }

  if (success) {
    return (
      <main className="auth-container">
        <div className="auth-card text-center">
          <div style={{ fontSize: '3rem', marginBottom: 16, color: 'var(--color-success)' }}>
            &#10003;
          </div>
          <h1 style={{ marginBottom: 8 }}>Password Reset</h1>
          <p className="text-muted" style={{ marginBottom: 24 }}>
            Your password has been reset successfully. You can now sign in with your new password.
          </p>
          <a href="/auth/login" className="btn btn-primary btn-lg">
            Sign In
          </a>
        </div>
      </main>
    );
  }

  return (
    <main className="auth-container">
      <div className="auth-card">
        <h1>Reset Password</h1>
        <p className="text-muted">Enter your new password below.</p>

        {error && (
          <div className="alert alert-error">{error}</div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="password" className="form-label">New Password</label>
            <input
              id="password"
              type="password"
              className="form-input"
              placeholder="At least 8 characters"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="new-password"
              minLength={8}
              autoFocus
            />
          </div>

          <div className="form-group">
            <label htmlFor="confirmPassword" className="form-label">Confirm New Password</label>
            <input
              id="confirmPassword"
              type="password"
              className="form-input"
              placeholder="Re-enter your new password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              autoComplete="new-password"
            />
            {confirmPassword && password !== confirmPassword && (
              <span className="form-error">Passwords do not match</span>
            )}
          </div>

          <button
            type="submit"
            className="btn btn-primary btn-full"
            disabled={loading}
            style={{ marginTop: 8 }}
          >
            {loading ? (
              <>
                <span className="spinner" />
                Resetting...
              </>
            ) : (
              'Reset Password'
            )}
          </button>
        </form>

        <div className="auth-footer">
          <a href="/auth/login">Back to Sign In</a>
        </div>
      </div>
    </main>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<div className="auth-container"><div className="auth-card"><p>Loading...</p></div></div>}>
      <ResetPasswordContent />
    </Suspense>
  );
}
