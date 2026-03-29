'use client';

import { useEffect, useState, useCallback } from 'react';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';
const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

interface GatewayHealth {
  configVersion: string | number;
  routesLoaded: number;
  plansLoaded: number;
  subscriptionsLoaded: number;
}

function getToken(): string {
  if (typeof window !== 'undefined') {
    return localStorage.getItem('admin_token') || '';
  }
  return '';
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  return token
    ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
    : { 'Content-Type': 'application/json' };
}

/* ── Inline SVG icon components ── */

function IconConfig({ className }: { className?: string }) {
  return (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  );
}

function IconRoute({ className }: { className?: string }) {
  return (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="16 3 21 3 21 8" />
      <line x1="4" y1="20" x2="21" y2="3" />
      <polyline points="21 16 21 21 16 21" />
      <line x1="15" y1="15" x2="21" y2="21" />
      <line x1="4" y1="4" x2="9" y2="9" />
    </svg>
  );
}

function IconPlan({ className }: { className?: string }) {
  return (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
      <line x1="3" y1="9" x2="21" y2="9" />
      <line x1="9" y1="21" x2="9" y2="9" />
    </svg>
  );
}

function IconSubscription({ className }: { className?: string }) {
  return (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  );
}

function IconRefresh({ className }: { className?: string }) {
  return (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="23 4 23 10 17 10" />
      <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
    </svg>
  );
}

function IconTrash({ className }: { className?: string }) {
  return (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
      <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
    </svg>
  );
}

function IconCheck({ className }: { className?: string }) {
  return (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  );
}

function IconAlert({ className }: { className?: string }) {
  return (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </svg>
  );
}

/* ── Skeleton card for loading state ── */

function SkeletonCard() {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between">
        <div className="space-y-3">
          <div className="h-3 w-24 animate-pulse rounded-md bg-slate-200" />
          <div className="h-8 w-16 animate-pulse rounded-md bg-slate-200" />
        </div>
        <div className="h-12 w-12 animate-pulse rounded-xl bg-slate-200" />
      </div>
    </div>
  );
}

/* ── Main page component ── */

export default function GatewayPage() {
  const [health, setHealth] = useState<GatewayHealth>({
    configVersion: '-',
    routesLoaded: 0,
    plansLoaded: 0,
    subscriptionsLoaded: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshLoading, setRefreshLoading] = useState(false);
  const [purgeLoading, setPurgeLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  const fetchHealth = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [versionRes, healthRes] = await Promise.all([
        fetch(`${GATEWAY_URL}/v1/gateway/config/version`, { headers: authHeaders() }).catch(() => null),
        fetch(`${GATEWAY_URL}/v1/gateway/health/detailed`, { headers: authHeaders() }).catch(() => null),
      ]);

      let configVersion: string | number = '-';
      let routesLoaded = 0;
      let plansLoaded = 0;
      let subscriptionsLoaded = 0;

      if (versionRes?.ok) {
        const data = await versionRes.json();
        configVersion = data.version ?? data.configVersion ?? data ?? '-';
      }
      if (healthRes?.ok) {
        const data = await healthRes.json();
        const comp = data.components || data;
        routesLoaded = comp.routesLoaded ?? comp.routes ?? data.routesLoaded ?? 0;
        plansLoaded = comp.plansLoaded ?? comp.plans ?? data.plansLoaded ?? 0;
        subscriptionsLoaded = comp.subscriptionsLoaded ?? comp.subscriptions ?? data.subscriptionsLoaded ?? 0;
        if (data.configVersion) configVersion = data.configVersion;
      }

      setHealth({ configVersion, routesLoaded, plansLoaded, subscriptionsLoaded });
    } catch {
      setError('Failed to load gateway health data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchHealth();
  }, [fetchHealth]);

  /* Auto-dismiss success messages */
  useEffect(() => {
    if (!successMsg) return;
    const timer = setTimeout(() => setSuccessMsg(''), 4000);
    return () => clearTimeout(timer);
  }, [successMsg]);

  const handleRefreshConfig = async () => {
    setRefreshLoading(true);
    setSuccessMsg('');
    try {
      const res = await fetch(`${GATEWAY_URL}/v1/gateway/config/refresh`, {
        method: 'POST',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Refresh failed');
      setSuccessMsg('Gateway configuration refreshed successfully');
      fetchHealth();
    } catch {
      showToast('Failed to refresh gateway configuration', 'error');
    } finally {
      setRefreshLoading(false);
    }
  };

  const handlePurgeCache = async () => {
    if (!confirm('Are you sure you want to purge all gateway cache?')) return;
    setPurgeLoading(true);
    setSuccessMsg('');
    try {
      const res = await fetch(`${GATEWAY_URL}/v1/gateway/cache/purge-all`, {
        method: 'DELETE',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Purge failed');
      setSuccessMsg('All gateway cache purged successfully');
    } catch {
      showToast('Failed to purge cache', 'error');
    } finally {
      setPurgeLoading(false);
    }
  };

  /* ── Stat card definitions ── */
  const stats = [
    {
      label: 'Config Version',
      value: String(health.configVersion),
      icon: <IconConfig className="h-6 w-6" />,
      iconBg: 'bg-blue-100',
      iconColor: 'text-blue-600',
    },
    {
      label: 'Routes Loaded',
      value: health.routesLoaded,
      icon: <IconRoute className="h-6 w-6" />,
      iconBg: 'bg-emerald-100',
      iconColor: 'text-emerald-600',
    },
    {
      label: 'Plans Loaded',
      value: health.plansLoaded,
      icon: <IconPlan className="h-6 w-6" />,
      iconBg: 'bg-primary-100',
      iconColor: 'text-primary-700',
    },
    {
      label: 'Subscriptions Loaded',
      value: health.subscriptionsLoaded,
      icon: <IconSubscription className="h-6 w-6" />,
      iconBg: 'bg-amber-100',
      iconColor: 'text-amber-600',
    },
  ];

  /* ── Loading skeleton ── */
  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 px-6 py-10 lg:px-8">
        <div className="mx-auto max-w-6xl">
          {/* Header skeleton */}
          <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div className="space-y-3">
              <div className="h-8 w-56 animate-pulse rounded-lg bg-slate-200" />
              <div className="h-4 w-80 animate-pulse rounded-md bg-slate-200" />
            </div>
            <div className="h-10 w-40 animate-pulse rounded-lg bg-slate-200" />
          </div>

          {/* Stat card skeletons */}
          <div className="mb-8 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <SkeletonCard key={i} />
            ))}
          </div>

          {/* Cache management skeleton */}
          <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="h-5 w-44 animate-pulse rounded-md bg-slate-200" />
            <div className="mt-3 h-4 w-96 animate-pulse rounded-md bg-slate-100" />
            <div className="mt-5 h-10 w-36 animate-pulse rounded-lg bg-slate-200" />
          </div>
        </div>
      </div>
    );
  }

  /* ── Main render ── */
  return (
    <div className="min-h-screen bg-slate-50 px-6 py-10 lg:px-8">
      <div className="mx-auto max-w-6xl">

        {/* ── Toast notifications ── */}
        {toast && (
          <div className={`fixed top-4 right-4 z-50 flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg border max-w-sm ${
            toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-emerald-50 border-emerald-200 text-emerald-800'
          }`}>
            {toast.type === 'error' ? (
              <svg className="w-5 h-5 shrink-0 text-red-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>
            ) : (
              <svg className="w-5 h-5 shrink-0 text-emerald-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
            )}
            <p className="text-sm font-medium flex-1">{toast.message}</p>
            <button onClick={() => setToast(null)} className="shrink-0 opacity-50 hover:opacity-100">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
            </button>
          </div>
        )}

        {/* ── Toast alerts ── */}
        {successMsg && (
          <div className="fixed right-6 top-6 z-50 flex items-center gap-3 rounded-xl border border-emerald-200 bg-emerald-50 px-5 py-3 text-sm font-medium text-emerald-800 shadow-lg shadow-emerald-100/50 transition-all">
            <IconCheck className="h-5 w-5 shrink-0 text-emerald-500" />
            {successMsg}
          </div>
        )}

        {error && (
          <div className="fixed right-6 top-6 z-50 flex items-center gap-3 rounded-xl border border-red-200 bg-red-50 px-5 py-3 text-sm font-medium text-red-800 shadow-lg shadow-red-100/50 transition-all">
            <IconAlert className="h-5 w-5 shrink-0 text-red-500" />
            {error}
          </div>
        )}

        {/* ── Header ── */}
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              Gateway Health
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              Monitor gateway runtime status and manage configuration
            </p>
          </div>

          <button
            onClick={handleRefreshConfig}
            disabled={refreshLoading}
            className="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-primary-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <IconRefresh
              className={`h-4 w-4 ${refreshLoading ? 'animate-spin' : ''}`}
            />
            {refreshLoading ? 'Refreshing...' : 'Refresh Config'}
          </button>
        </div>

        {/* ── Stat cards ── */}
        <div className="mb-8 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {stats.map((stat) => (
            <div
              key={stat.label}
              className="group rounded-xl border border-slate-200 bg-white p-6 shadow-sm transition-shadow hover:shadow-md"
            >
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    {stat.label}
                  </p>
                  <p className="mt-2 text-3xl font-bold tracking-tight text-slate-900">
                    {stat.value}
                  </p>
                </div>
                <div
                  className={`flex h-12 w-12 items-center justify-center rounded-xl ${stat.iconBg} ${stat.iconColor} transition-transform group-hover:scale-110`}
                >
                  {stat.icon}
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* ── Cache Management ── */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 className="text-base font-semibold text-slate-900">
            Cache Management
          </h3>
          <p className="mt-1 text-sm text-slate-500">
            Purge gateway cache to force reload of all configuration data from
            the management API.
          </p>
          <button
            onClick={handlePurgeCache}
            disabled={purgeLoading}
            className="mt-5 inline-flex items-center gap-2 rounded-lg bg-red-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-red-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <IconTrash
              className={`h-4 w-4 ${purgeLoading ? 'animate-pulse' : ''}`}
            />
            {purgeLoading ? 'Purging...' : 'Purge All Cache'}
          </button>
        </div>
      </div>
    </div>
  );
}
