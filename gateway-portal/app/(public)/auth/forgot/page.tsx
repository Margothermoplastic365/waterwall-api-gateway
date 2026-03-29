'use client';

import { useState, FormEvent } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await fetch(`${API_BASE}/v1/auth/forgot-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });
      // Always show success to prevent email enumeration
      setSubmitted(true);
    } catch {
      // Still show success to prevent enumeration
      setSubmitted(true);
    } finally {
      setLoading(false);
    }
  }

  if (submitted) {
    return (
      <main className="auth-container">
        <div className="auth-card text-center">
          <h1 style={{ marginBottom: 8 }}>Check Your Email</h1>
          <p className="text-muted" style={{ marginBottom: 24 }}>
            If an account exists for <strong>{email}</strong>, we&apos;ve sent a password reset link.
            Please check your inbox and spam folder.
          </p>
          <p className="text-sm text-light" style={{ marginBottom: 24 }}>
            The reset link will expire in 1 hour.
          </p>
          <div className="flex-center gap-md">
            <a href="/auth/login" className="btn btn-primary">
              Back to Sign In
            </a>
            <button
              className="btn btn-secondary"
              onClick={() => { setSubmitted(false); setEmail(''); }}
            >
              Try Another Email
            </button>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="auth-container">
      <div className="auth-card">
        <h1>Forgot Password</h1>
        <p className="text-muted">
          Enter your email address and we&apos;ll send you a link to reset your password.
        </p>

        {error && (
          <div className="alert alert-error">{error}</div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="email" className="form-label">Email</label>
            <input
              id="email"
              type="email"
              className="form-input"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
              autoFocus
            />
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
                Sending...
              </>
            ) : (
              'Send Reset Link'
            )}
          </button>
        </form>

        <div className="auth-footer">
          Remember your password?{' '}
          <a href="/auth/login">Sign In</a>
        </div>
      </div>
    </main>
  );
}
