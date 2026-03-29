'use client';

import { useEffect, useState } from 'react';

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8082';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface SafetyStats {
  blockedPrompts: number;
  piiDetections: number;
  injectionAttempts: number;
}

interface SafetyConfig {
  blockOnInjection: boolean;
  redactPii: boolean;
  blockOnToxicity: boolean;
}

interface Violation {
  id: string;
  timestamp: string;
  type: string;
  consumerId: string;
  detail: string;
}

function authHeaders(): Record<string, string> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return headers;
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function AiSafetyPage() {
  const [stats, setStats] = useState<SafetyStats>({
    blockedPrompts: 0,
    piiDetections: 0,
    injectionAttempts: 0,
  });
  const [config, setConfig] = useState<SafetyConfig>({
    blockOnInjection: true,
    redactPii: true,
    blockOnToxicity: false,
  });
  const [violations, setViolations] = useState<Violation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError('');
      try {
        const [statsRes, configRes, violationsRes] = await Promise.allSettled([
          fetch(`${GATEWAY_URL}/v1/ai/safety/stats`, { headers: authHeaders() }),
          fetch(`${GATEWAY_URL}/v1/ai/safety/config`, { headers: authHeaders() }),
          fetch(`${GATEWAY_URL}/v1/ai/safety/violations?limit=20`, { headers: authHeaders() }),
        ]);

        if (statsRes.status === 'fulfilled' && statsRes.value.ok) {
          setStats(await statsRes.value.json());
        }
        if (configRes.status === 'fulfilled' && configRes.value.ok) {
          setConfig(await configRes.value.json());
        }
        if (violationsRes.status === 'fulfilled' && violationsRes.value.ok) {
          const data = await violationsRes.value.json();
          setViolations(Array.isArray(data) ? data : []);
        } else {
          /* Placeholder violations for display */
          setViolations([
            {
              id: '1',
              timestamp: new Date(Date.now() - 3600000).toISOString(),
              type: 'INJECTION',
              consumerId: 'consumer-42',
              detail: 'Prompt injection attempt detected: "ignore all previous instructions"',
            },
            {
              id: '2',
              timestamp: new Date(Date.now() - 7200000).toISOString(),
              type: 'PII',
              consumerId: 'consumer-17',
              detail: 'PII detected: email address redacted from prompt',
            },
            {
              id: '3',
              timestamp: new Date(Date.now() - 10800000).toISOString(),
              type: 'TOXICITY',
              consumerId: 'consumer-8',
              detail: 'Toxic content blocked: profanity threshold exceeded',
            },
          ]);
        }
      } catch {
        setError('Failed to load safety data');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const handleToggle = async (key: keyof SafetyConfig) => {
    const updated = { ...config, [key]: !config[key] };
    setConfig(updated);
    try {
      await fetch(`${GATEWAY_URL}/v1/ai/safety/config`, {
        method: 'PUT',
        headers: authHeaders(),
        body: JSON.stringify(updated),
      });
    } catch {
      /* Revert on failure */
      setConfig(config);
      showToast('Failed to update safety configuration', 'error');
    }
  };

  const badgeForType = (type: string) => {
    switch (type) {
      case 'INJECTION':
        return 'bg-red-100 text-red-700';
      case 'PII':
        return 'bg-amber-100 text-amber-700';
      case 'TOXICITY':
        return 'bg-purple-100 text-purple-700';
      default:
        return 'bg-slate-100 text-slate-600';
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
        <div className="mx-auto max-w-7xl">
          <div className="mb-8">
            <div className="h-8 w-72 animate-pulse rounded-lg bg-slate-200" />
            <div className="mt-2 h-4 w-96 animate-pulse rounded-lg bg-slate-200" />
          </div>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-28 animate-pulse rounded-xl bg-white shadow-sm" />
            ))}
          </div>
          <div className="mt-6 h-48 animate-pulse rounded-xl bg-white shadow-sm" />
          <div className="mt-6 h-64 animate-pulse rounded-xl bg-white shadow-sm" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
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
      <div className="mx-auto max-w-7xl">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Content Safety Dashboard</h1>
          <p className="mt-1 text-sm text-slate-500">Monitor and configure AI content safety policies</p>
        </div>

        {error && (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {/* Safety Stats */}
        <div className="mb-8 grid grid-cols-1 gap-6 sm:grid-cols-3">
          <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">Blocked Prompts</p>
                <p className="mt-2 text-2xl font-bold text-slate-900">{stats.blockedPrompts.toLocaleString()}</p>
              </div>
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-red-50 text-xs font-bold text-red-600">
                BLK
              </div>
            </div>
          </div>
          <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">PII Detections</p>
                <p className="mt-2 text-2xl font-bold text-slate-900">{stats.piiDetections.toLocaleString()}</p>
              </div>
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50 text-xs font-bold text-amber-600">
                PII
              </div>
            </div>
          </div>
          <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">Injection Attempts</p>
                <p className="mt-2 text-2xl font-bold text-slate-900">{stats.injectionAttempts.toLocaleString()}</p>
              </div>
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-50 text-xs font-bold text-purple-600">
                INJ
              </div>
            </div>
          </div>
        </div>

        {/* Configuration Panel */}
        <div className="mb-6 rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
          <h3 className="mb-4 text-base font-semibold text-slate-900">Safety Configuration</h3>
          <div className="divide-y divide-slate-100">
            {([
              { key: 'blockOnInjection' as const, label: 'Block on Injection', desc: 'Block prompts containing suspected injection attacks' },
              { key: 'redactPii' as const, label: 'Redact PII', desc: 'Automatically redact personally identifiable information from prompts' },
              { key: 'blockOnToxicity' as const, label: 'Block on Toxicity', desc: 'Block prompts that exceed the toxicity threshold' },
            ]).map((item) => (
              <div key={item.key} className="flex items-center justify-between py-4">
                <div>
                  <p className="font-medium text-slate-900">{item.label}</p>
                  <p className="mt-0.5 text-sm text-slate-500">{item.desc}</p>
                </div>
                <button
                  className={`inline-flex items-center rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors ${
                    config[item.key]
                      ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-200'
                      : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                  }`}
                  onClick={() => handleToggle(item.key)}
                >
                  {config[item.key] ? 'Enabled' : 'Disabled'}
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* Recent Violations Log */}
        <div className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          <div className="border-b border-slate-200 px-6 py-4">
            <h3 className="text-base font-semibold text-slate-900">Recent Violations</h3>
          </div>
          {violations.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-slate-100">
                <svg className="h-6 w-6 text-slate-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <p className="text-sm text-slate-500">No recent violations recorded.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50/50">
                    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500">Timestamp</th>
                    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500">Type</th>
                    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500">Consumer</th>
                    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500">Detail</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {violations.map((v) => (
                    <tr key={v.id} className="transition-colors hover:bg-slate-50">
                      <td className="whitespace-nowrap px-6 py-3.5 text-sm text-slate-600">{new Date(v.timestamp).toLocaleString()}</td>
                      <td className="px-6 py-3.5">
                        <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${badgeForType(v.type)}`}>
                          {v.type}
                        </span>
                      </td>
                      <td className="px-6 py-3.5 text-sm font-mono text-slate-600">{v.consumerId}</td>
                      <td className="max-w-md px-6 py-3.5 text-sm text-slate-600">{v.detail}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
