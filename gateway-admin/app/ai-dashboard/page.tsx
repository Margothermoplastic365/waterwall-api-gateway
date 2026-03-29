'use client';

import { useEffect, useState, useMemo } from 'react';
import { DataTable } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const ANALYTICS_URL = process.env.NEXT_PUBLIC_ANALYTICS_URL || 'http://localhost:8083';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface AiDashboardStats {
  totalTokens: number;
  totalCost: number;
  totalRequests: number;
  avgTokensPerRequest: number;
}

interface TopConsumer {
  consumerId: string;
  consumerName: string;
  tokens: number;
  requests: number;
  cost: number;
  [key: string]: unknown;
}

interface ModelUsage {
  model: string;
  provider: string;
  requests: number;
  tokens: number;
  avgLatency: number;
  [key: string]: unknown;
}

interface ProviderStatus {
  name: string;
  enabled: boolean;
  models: string[];
}

const RANGES = ['1h', '6h', '24h', '7d', '30d'] as const;
type Range = (typeof RANGES)[number];

function fetchAi<T>(path: string): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(`${ANALYTICS_URL}${path}`, { headers }).then((r) => {
    if (!r.ok) throw new Error('Failed');
    return r.json();
  });
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function AiDashboardPage() {
  const [range, setRange] = useState<Range>('24h');
  const [stats, setStats] = useState<AiDashboardStats | null>(null);
  const [topConsumers, setTopConsumers] = useState<TopConsumer[]>([]);
  const [models, setModels] = useState<ModelUsage[]>([]);
  const [providers, setProviders] = useState<ProviderStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError('');
      try {
        const [dashboard, consumers, modelData] = await Promise.all([
          fetchAi<AiDashboardStats>(`/v1/analytics/ai/dashboard?range=${range}`),
          fetchAi<TopConsumer[]>(`/v1/analytics/ai/top-consumers?range=${range}`),
          fetchAi<ModelUsage[]>(`/v1/analytics/ai/models?range=${range}`),
        ]);
        setStats(dashboard);
        setTopConsumers(Array.isArray(consumers) ? consumers : []);
        setModels(Array.isArray(modelData) ? modelData : []);
      } catch {
        setError('Failed to load AI analytics data');
        setStats({ totalTokens: 0, totalCost: 0, totalRequests: 0, avgTokensPerRequest: 0 });
        setTopConsumers([]);
        setModels([]);
      }

      /* Provider status — best-effort */
      try {
        const p = await fetchAi<ProviderStatus[]>(`/v1/analytics/ai/providers`);
        setProviders(Array.isArray(p) ? p : []);
      } catch {
        setProviders([
          { name: 'OpenAI', enabled: true, models: ['gpt-4o', 'gpt-4o-mini'] },
          { name: 'Anthropic', enabled: true, models: ['claude-sonnet'] },
          { name: 'DeepSeek', enabled: false, models: ['deepseek-chat'] },
        ]);
      }

      setLoading(false);
    }
    load();
  }, [range]);

  const consumerColumns: Column<TopConsumer>[] = useMemo(
    () => [
      { key: 'consumerName', label: 'Consumer' },
      {
        key: 'tokens',
        label: 'Tokens',
        render: (row) => row.tokens.toLocaleString(),
      },
      {
        key: 'requests',
        label: 'Requests',
        render: (row) => row.requests.toLocaleString(),
      },
      {
        key: 'cost',
        label: 'Cost',
        render: (row) => `$${row.cost.toFixed(4)}`,
      },
    ],
    [],
  );

  const modelColumns: Column<ModelUsage>[] = useMemo(
    () => [
      { key: 'model', label: 'Model' },
      { key: 'provider', label: 'Provider' },
      {
        key: 'requests',
        label: 'Requests',
        render: (row) => row.requests.toLocaleString(),
      },
      {
        key: 'tokens',
        label: 'Tokens',
        render: (row) => row.tokens.toLocaleString(),
      },
      {
        key: 'avgLatency',
        label: 'Avg Latency',
        render: (row) => `${row.avgLatency}ms`,
      },
    ],
    [],
  );

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
        <div className="mx-auto max-w-7xl">
          <div className="mb-8">
            <div className="h-8 w-64 animate-pulse rounded-lg bg-slate-200" />
            <div className="mt-2 h-4 w-96 animate-pulse rounded-lg bg-slate-200" />
          </div>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-28 animate-pulse rounded-xl bg-white shadow-sm" />
            ))}
          </div>
          <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-24 animate-pulse rounded-xl bg-white shadow-sm" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
      <div className="mx-auto max-w-7xl">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">AI / LLM Dashboard</h1>
          <p className="mt-1 text-sm text-slate-500">Token usage, costs, and model analytics</p>
        </div>

        {error && (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {/* Time Range Selector */}
        <div className="mb-6 inline-flex items-center gap-1 rounded-xl bg-white p-1 shadow-sm ring-1 ring-slate-200">
          {RANGES.map((r) => (
            <button
              key={r}
              className={`rounded-lg px-4 py-2 text-sm font-medium transition-all ${
                range === r
                  ? 'bg-purple-600 text-white shadow-sm'
                  : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
              }`}
              onClick={() => setRange(r)}
            >
              {r}
            </button>
          ))}
        </div>

        {/* Stat Cards */}
        {stats && (
          <div className="mb-8 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
            <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">Total Tokens</p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">{stats.totalTokens.toLocaleString()}</p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-xs font-bold text-blue-600">
                  TKN
                </div>
              </div>
            </div>
            <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">Total Cost</p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">${stats.totalCost.toFixed(2)}</p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-50 text-xs font-bold text-emerald-600">
                  $
                </div>
              </div>
            </div>
            <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">Total AI Requests</p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">{stats.totalRequests.toLocaleString()}</p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-50 text-xs font-bold text-purple-600">
                  REQ
                </div>
              </div>
            </div>
            <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">Avg Tokens/Request</p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">{stats.avgTokensPerRequest.toLocaleString()}</p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50 text-xs font-bold text-amber-600">
                  AVG
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Provider Status */}
        <div className="mb-6 rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
          <h3 className="mb-4 text-base font-semibold text-slate-900">Provider Status</h3>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            {providers.map((p) => (
              <div
                key={p.name}
                className={`rounded-lg border-l-4 bg-slate-50 p-4 ${
                  p.enabled ? 'border-l-emerald-500' : 'border-l-red-500'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-semibold text-slate-900">{p.name}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      {p.models.join(', ')}
                    </p>
                  </div>
                  <span
                    className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                      p.enabled
                        ? 'bg-emerald-100 text-emerald-700'
                        : 'bg-red-100 text-red-700'
                    }`}
                  >
                    {p.enabled ? 'Enabled' : 'Disabled'}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Top Consumers */}
        <div className="mb-6 overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          <div className="border-b border-slate-200 px-6 py-4">
            <h3 className="text-base font-semibold text-slate-900">Top Consumers</h3>
          </div>
          <div className="p-0">
            <DataTable data={topConsumers} columns={consumerColumns} />
          </div>
        </div>

        {/* Model Usage */}
        <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          <div className="border-b border-slate-200 px-6 py-4">
            <h3 className="text-base font-semibold text-slate-900">Model Usage Breakdown</h3>
          </div>
          <div className="p-0">
            <DataTable data={models} columns={modelColumns} />
          </div>
        </div>
      </div>
    </div>
  );
}
