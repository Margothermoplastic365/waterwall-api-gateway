'use client';

import { Suspense, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';

const API_BASE = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

export const dynamic = 'force-dynamic';

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get('token');

  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    async function verifyEmail() {
      if (!token) {
        setStatus('error');
        setMessage('No verification token provided. Please check your email for the correct link.');
        return;
      }

      try {
        const res = await fetch(`${API_BASE}/v1/auth/verify-email?token=${encodeURIComponent(token)}`, {
          method: 'POST',
        });

        if (!res.ok) {
          const body = await res.json().catch(() => null);
          throw new Error(body?.message || 'Verification failed. The token may be expired or invalid.');
        }

        setStatus('success');
        setMessage('Your email has been verified successfully. You can now sign in to your account.');
      } catch (err: unknown) {
        setStatus('error');
        setMessage(err instanceof Error ? err.message : 'An unexpected error occurred during verification.');
      }
    }

    verifyEmail();
  }, [token]);

  return (
    <main className="auth-container">
      <div className="auth-card text-center">
        {status === 'loading' && (
          <>
            <div className="flex-center mb-lg">
              <div
                className="spinner"
                style={{
                  borderColor: 'var(--color-border)',
                  borderTopColor: 'var(--color-primary)',
                  width: 40,
                  height: 40,
                }}
              />
            </div>
            <h1 style={{ marginBottom: 8 }}>Verifying Email</h1>
            <p className="text-muted">Please wait while we verify your email address...</p>
          </>
        )}

        {status === 'success' && (
          <>
            <div style={{ fontSize: '3rem', marginBottom: 16, color: 'var(--color-success)' }}>
              &#10003;
            </div>
            <h1 style={{ marginBottom: 8 }}>Email Verified</h1>
            <p className="text-muted" style={{ marginBottom: 24 }}>{message}</p>
            <a href="/auth/login" className="btn btn-primary btn-lg">
              Sign In
            </a>
          </>
        )}

        {status === 'error' && (
          <>
            <div style={{ fontSize: '3rem', marginBottom: 16, color: 'var(--color-danger)' }}>
              &#10007;
            </div>
            <h1 style={{ marginBottom: 8 }}>Verification Failed</h1>
            <p className="text-muted" style={{ marginBottom: 24 }}>{message}</p>
            <div className="flex-center gap-md">
              <a href="/auth/login" className="btn btn-primary">
                Sign In
              </a>
              <a href="/auth/register" className="btn btn-secondary">
                Register Again
              </a>
            </div>
          </>
        )}
      </div>
    </main>
  );
}


export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<div className="auth-container"><div className="auth-card"><p>Loading...</p></div></div>}>
      <VerifyEmailContent />
    </Suspense>
  );
}
