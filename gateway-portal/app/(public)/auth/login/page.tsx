'use client';

import { Suspense, useState, FormEvent } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

export const dynamic = 'force-dynamic';

const features = [
  {
    icon: (
      <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
    ),
    title: 'API Catalog',
    desc: 'Browse and discover APIs across the platform',
  },
  {
    icon: (
      <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z" />
      </svg>
    ),
    title: 'API Keys & OAuth',
    desc: 'Manage credentials and authentication',
  },
  {
    icon: (
      <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 3v11.25A2.25 2.25 0 006 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0118 16.5h-2.25m-7.5 0h7.5m-7.5 0l-1 3m8.5-3l1 3m0 0l.5 1.5m-.5-1.5h-9.5m0 0l-.5 1.5" />
      </svg>
    ),
    title: 'Try-It Console',
    desc: 'Test API calls directly from your browser',
  },
  {
    icon: (
      <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
      </svg>
    ),
    title: 'AI Playground',
    desc: 'Experiment with LLM APIs and AI tools',
  },
];

function LoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get('redirect') || '/dashboard';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(
    searchParams.get('expired') === 'true' ? 'Your session has expired. Please sign in again.' : ''
  );
  const [loading, setLoading] = useState(false);

  const [mfaRequired, setMfaRequired] = useState(false);
  const [mfaSessionToken, setMfaSessionToken] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [useRecoveryCode, setUseRecoveryCode] = useState(false);
  const [verifyingMfa, setVerifyingMfa] = useState(false);
  const [showDemoUsers, setShowDemoUsers] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message || `Login failed (${res.status})`);
      }
      const data = await res.json();
      if (data.mfaRequired) {
        setMfaRequired(true);
        setMfaSessionToken(data.sessionToken || data.mfaToken || '');
        return;
      }
      const jwt = data.accessToken || data.token;
      if (jwt) {
        document.cookie = `token=${jwt}; path=/; max-age=${60 * 60 * 24 * 7}; SameSite=Lax`;
        localStorage.setItem('token', jwt);
      }
      if (data.user) localStorage.setItem('user', JSON.stringify(data.user));
      router.push(redirectTo);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'An unexpected error occurred');
    } finally {
      setLoading(false);
    }
  }

  async function handleMfaVerify(e: FormEvent) {
    e.preventDefault();
    setError('');
    setVerifyingMfa(true);
    try {
      const endpoint = useRecoveryCode
        ? `${IDENTITY_URL}/v1/mfa/recovery/verify`
        : `${IDENTITY_URL}/v1/mfa/totp/verify`;
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionToken: mfaSessionToken, code: mfaCode }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message || 'Verification failed');
      }
      const data = await res.json();
      const mfaJwt = data.accessToken || data.token;
      if (mfaJwt) {
        document.cookie = `token=${mfaJwt}; path=/; max-age=${60 * 60 * 24 * 7}; SameSite=Lax`;
        localStorage.setItem('token', mfaJwt);
      }
      if (data.user) localStorage.setItem('user', JSON.stringify(data.user));
      router.push(redirectTo);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Verification failed');
    } finally {
      setVerifyingMfa(false);
    }
  }

  // MFA screen — centered single column
  if (mfaRequired) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-900 via-blue-900 to-slate-900 px-4">
        <div className="w-full max-w-md">
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-br from-blue-500 to-blue-700 text-white shadow-lg shadow-blue-900/40 mb-4">
              <svg className="w-7 h-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
              </svg>
            </div>
            <h1 className="text-2xl font-bold text-white">Two-Factor Authentication</h1>
            <p className="text-blue-300/70 text-sm mt-1">
              {useRecoveryCode ? 'Enter one of your recovery codes.' : 'Enter the 6-digit code from your authenticator app.'}
            </p>
          </div>
          <div className="bg-white rounded-2xl shadow-2xl shadow-black/20 p-8">
            {error && (
              <div className="mb-5 px-4 py-3 bg-red-50 border border-red-200 text-red-600 rounded-lg text-sm flex items-start gap-2">
                <svg className="w-4 h-4 mt-0.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>
                {error}
              </div>
            )}
            <form onSubmit={handleMfaVerify} className="space-y-5">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5" htmlFor="mfaCode">
                  {useRecoveryCode ? 'Recovery Code' : 'Authentication Code'}
                </label>
                <input id="mfaCode" type="text" required autoFocus autoComplete="one-time-code"
                  className="w-full px-4 py-3 rounded-lg border border-slate-200 text-slate-800 text-center tracking-widest placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all font-mono text-lg"
                  placeholder={useRecoveryCode ? 'Enter recovery code' : '000000'}
                  value={mfaCode}
                  onChange={(e) => setMfaCode(useRecoveryCode ? e.target.value : e.target.value.replace(/\D/g, '').slice(0, 6))} />
              </div>
              <button type="submit" disabled={verifyingMfa}
                className="w-full py-2.5 px-4 bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-sm font-medium rounded-lg transition-colors flex items-center justify-center gap-2">
                {verifyingMfa && <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" /><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" /></svg>}
                {verifyingMfa ? 'Verifying...' : 'Verify'}
              </button>
            </form>
            <div className="mt-5 pt-4 border-t border-slate-100 text-center">
              <button onClick={() => { setUseRecoveryCode(!useRecoveryCode); setMfaCode(''); setError(''); }}
                className="text-sm text-blue-600 hover:text-blue-700 font-medium bg-transparent border-none cursor-pointer">
                {useRecoveryCode ? 'Use authenticator code instead' : 'Use recovery code instead'}
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Main login — two column layout
  return (
    <div className="min-h-screen flex" style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      {/* Left panel — branding & features */}
      <div className="hidden lg:flex lg:w-1/2 xl:w-[55%] bg-gradient-to-br from-slate-900 via-blue-900 to-slate-900 relative overflow-hidden">
        {/* Background pattern */}
        <div className="absolute inset-0 opacity-10">
          <svg className="absolute top-0 left-0 w-full h-full" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
                <path d="M 40 0 L 0 0 0 40" fill="none" stroke="white" strokeWidth="0.5" />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#grid)" />
          </svg>
        </div>
        {/* Glow effect */}
        <div className="absolute top-1/4 left-1/3 w-96 h-96 bg-blue-500/20 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 right-1/4 w-64 h-64 bg-purple-500/15 rounded-full blur-3xl" />

        <div className="relative z-10 flex flex-col justify-center px-12 xl:px-20 py-16 w-full">
          {/* Logo */}
          <div className="flex items-center gap-3 mb-12">
            <img src="/logo.svg" alt="Waterwall" className="w-11 h-11 rounded-xl shadow-lg shadow-blue-900/40" />
            <div>
              <div className="text-white font-bold text-lg leading-tight">Waterwall</div>
              <div className="text-blue-400/70 text-xs font-medium tracking-wide uppercase">Developer Portal</div>
            </div>
          </div>

          {/* Hero text */}
          <h1 className="text-4xl xl:text-5xl font-bold text-white leading-tight mb-4">
            Build with
            <br />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-cyan-300">powerful APIs</span>
          </h1>
          <p className="text-blue-200/60 text-lg mb-12 max-w-md leading-relaxed">
            Discover, integrate, and manage APIs. Everything you need to build great products.
          </p>

          {/* Feature cards */}
          <div className="grid grid-cols-2 gap-4">
            {features.map((f) => (
              <div key={f.title} className="rounded-xl bg-white/[0.06] border border-white/[0.08] p-4 backdrop-blur-sm hover:bg-white/[0.1] transition-colors">
                <div className="w-10 h-10 rounded-lg bg-blue-500/20 text-blue-300 flex items-center justify-center mb-3">
                  {f.icon}
                </div>
                <div className="text-white text-sm font-semibold mb-1">{f.title}</div>
                <div className="text-blue-300/50 text-xs leading-relaxed">{f.desc}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right panel — login form */}
      <div className="flex-1 flex items-center justify-center bg-slate-50 px-6 py-12">
        <div className="w-full max-w-md">
          {/* Mobile-only brand */}
          <div className="lg:hidden text-center mb-8">
            <img src="/logo.svg" alt="Waterwall" className="w-12 h-12 rounded-xl shadow-lg mb-3 mx-auto" />
            <h1 className="text-xl font-bold text-slate-900">Waterwall</h1>
            <p className="text-slate-400 text-sm">Developer Portal</p>
          </div>

          <div className="bg-white rounded-2xl shadow-xl shadow-slate-200/50 border border-slate-200/60 p-8">
            <h2 className="text-2xl font-bold text-slate-900 mb-1">Welcome back</h2>
            <p className="text-slate-400 text-sm mb-8">Sign in to your developer account</p>

            {error && (
              <div className="mb-6 px-4 py-3 bg-red-50 border border-red-200 text-red-600 rounded-lg text-sm flex items-start gap-2">
                <svg className="w-4 h-4 mt-0.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5" htmlFor="email">Email address</label>
                <input id="email" type="email" required autoComplete="email" autoFocus
                  className="w-full px-4 py-2.5 rounded-lg border border-slate-200 text-slate-800 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                  placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
              </div>
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="block text-sm font-medium text-slate-700" htmlFor="password">Password</label>
                  <a href="/auth/forgot" className="text-xs text-blue-600 hover:text-blue-700 font-medium no-underline">Forgot?</a>
                </div>
                <input id="password" type="password" required autoComplete="current-password"
                  className="w-full px-4 py-2.5 rounded-lg border border-slate-200 text-slate-800 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                  placeholder="Enter your password" value={password} onChange={(e) => setPassword(e.target.value)} />
              </div>
              <button type="submit" disabled={loading}
                className="w-full py-3 px-4 bg-blue-600 hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed text-white text-sm font-semibold rounded-lg transition-colors flex items-center justify-center gap-2 shadow-sm shadow-blue-600/20">
                {loading && <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" /><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" /></svg>}
                {loading ? 'Signing in...' : 'Sign In'}
              </button>
            </form>

            {/* Quick access — collapsible demo users */}
            <div className="mt-6 pt-5 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setShowDemoUsers(!showDemoUsers)}
                className="w-full flex items-center justify-between px-1 py-1 text-xs text-slate-400 hover:text-slate-600 transition-colors"
              >
                <span className="font-medium">Demo credentials</span>
                <svg className={`w-4 h-4 transition-transform duration-200 ${showDemoUsers ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              <div className={`overflow-hidden transition-all duration-300 ease-in-out ${showDemoUsers ? 'max-h-[400px] opacity-100 mt-3' : 'max-h-0 opacity-0'}`}>
                <div className="space-y-1.5 max-h-[300px] overflow-y-auto pr-1">
                  {[
                    { email: 'developer@gateway.local', password: 'changeme', name: 'Dev Developer', role: 'DEVELOPER', color: 'blue', desc: 'Browse, subscribe, create apps' },
                    { email: 'dave@globex.io', password: 'changeme', name: 'Dave Wilson', role: 'DEVELOPER', color: 'emerald', desc: 'Globex backend developer' },
                    { email: 'frank@initech.dev', password: 'changeme', name: 'Frank Mueller', role: 'DEVELOPER', color: 'amber', desc: 'Initech junior developer' },
                    { email: 'alice@acme-corp.com', password: 'changeme', name: 'Alice Chen', role: 'API_PUBLISHER_ADMIN', color: 'pink', desc: 'Acme API team lead' },
                  ].map((user) => {
                    const colorMap: Record<string, { bg: string; text: string; hover: string }> = {
                      blue:    { bg: 'bg-blue-100',    text: 'text-blue-600',    hover: 'hover:border-blue-300 hover:bg-blue-50/50' },
                      emerald: { bg: 'bg-emerald-100', text: 'text-emerald-600', hover: 'hover:border-emerald-300 hover:bg-emerald-50/50' },
                      amber:   { bg: 'bg-amber-100',   text: 'text-amber-600',   hover: 'hover:border-amber-300 hover:bg-amber-50/50' },
                      pink:    { bg: 'bg-pink-100',    text: 'text-pink-600',    hover: 'hover:border-pink-300 hover:bg-pink-50/50' },
                    };
                    const c = colorMap[user.color] || colorMap.blue;
                    return (
                      <button key={user.email} type="button"
                        onClick={() => { setEmail(user.email); setPassword(user.password); setShowDemoUsers(false); }}
                        className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg border border-dashed border-slate-200 ${c.hover} transition-all text-left group`}>
                        <div className={`w-7 h-7 rounded-full ${c.bg} ${c.text} flex items-center justify-center shrink-0 text-xs font-bold`}>
                          {user.name.charAt(0)}
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2">
                            <p className="text-sm font-medium text-slate-700 leading-tight">{user.name}</p>
                            <span className="text-[10px] font-medium px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 shrink-0">{user.role}</span>
                          </div>
                          <p className="text-[11px] text-slate-400 truncate">{user.desc}</p>
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>

            <div className="mt-6 text-center text-sm text-slate-500">
              Don&apos;t have an account?{' '}
              <a href="/auth/register" className="text-blue-600 hover:text-blue-700 font-medium no-underline">Register</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <svg className="w-8 h-8 animate-spin text-blue-500" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    }>
      <LoginContent />
    </Suspense>
  );
}
