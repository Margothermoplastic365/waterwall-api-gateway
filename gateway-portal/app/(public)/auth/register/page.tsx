'use client';

import { useState, FormEvent } from 'react';

const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

function getPasswordStrength(password: string): { level: 'weak' | 'medium' | 'strong'; label: string } {
  if (!password) return { level: 'weak', label: '' };

  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
  if (/\d/.test(password)) score++;
  if (/[^a-zA-Z0-9]/.test(password)) score++;

  if (score <= 2) return { level: 'weak', label: 'Weak' };
  if (score <= 3) return { level: 'medium', label: 'Medium' };
  return { level: 'strong', label: 'Strong' };
}

export default function RegisterPage() {
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [agreedToTerms, setAgreedToTerms] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const strength = getPasswordStrength(password);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');

    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    if (!agreedToTerms) {
      setError('You must agree to the terms and conditions');
      return;
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters');
      return;
    }

    setLoading(true);

    try {
      const res = await fetch(`${IDENTITY_URL}/v1/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ displayName, email, password }),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message || `Registration failed (${res.status})`);
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
          <h1 style={{ marginBottom: 12 }}>Check Your Email</h1>
          <p className="text-muted" style={{ marginBottom: 24 }}>
            We&apos;ve sent a verification link to <strong>{email}</strong>.
            Please check your inbox and click the link to activate your account.
          </p>
          <p className="text-sm text-light" style={{ marginBottom: 24 }}>
            Didn&apos;t receive the email? Check your spam folder or try registering again.
          </p>
          <a href="/auth/login" className="btn btn-primary">
            Go to Sign In
          </a>
        </div>
      </main>
    );
  }

  return (
    <main className="auth-container">
      <div className="auth-card">
        <h1>Create Account</h1>
        <p className="text-muted">Sign up to start using the Waterwall API Gateway platform.</p>

        {error && (
          <div className="alert alert-error">{error}</div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="displayName" className="form-label">Display Name</label>
            <input
              id="displayName"
              type="text"
              className="form-input"
              placeholder="John Doe"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
              autoFocus
            />
          </div>

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
            />
          </div>

          <div className="form-group">
            <label htmlFor="password" className="form-label">Password</label>
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
            />
            {password && (
              <>
                <div className="strength-bar">
                  <div className={`strength-fill strength-${strength.level}`} />
                </div>
                <span className="text-sm text-muted">
                  Password strength: <strong>{strength.label}</strong>
                </span>
              </>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="confirmPassword" className="form-label">Confirm Password</label>
            <input
              id="confirmPassword"
              type="password"
              className="form-input"
              placeholder="Re-enter your password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              autoComplete="new-password"
            />
            {confirmPassword && password !== confirmPassword && (
              <span className="form-error">Passwords do not match</span>
            )}
          </div>

          <div className="form-group">
            <div className="checkbox-group">
              <input
                id="terms"
                type="checkbox"
                checked={agreedToTerms}
                onChange={(e) => setAgreedToTerms(e.target.checked)}
              />
              <label htmlFor="terms" className="text-sm">
                I agree to the <a href="/terms">Terms of Service</a> and{' '}
                <a href="/privacy">Privacy Policy</a>
              </label>
            </div>
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
                Creating account...
              </>
            ) : (
              'Create Account'
            )}
          </button>
        </form>

        <div className="auth-footer">
          Already have an account?{' '}
          <a href="/auth/login">Sign In</a>
        </div>
      </div>
    </main>
  );
}
