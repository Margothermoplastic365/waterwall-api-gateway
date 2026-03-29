'use client';

import React, { useEffect, useState, useCallback } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';
const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';
const ANALYTICS_URL = process.env.NEXT_PUBLIC_ANALYTICS_URL || 'http://localhost:8083';

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token') || localStorage.getItem('admin_token') || localStorage.getItem('jwt_token');
}
function authHeaders(): Record<string, string> {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
}

/* ---------- types ---------- */

interface Application {
  id: string;
  name: string;
  status: string;
}

interface UsageSummary {
  requestsToday: number;
  requestsThisWeek: number;
  requestsThisMonth: number;
  averageLatencyMs: number;
  errorRate: number;
  activeSubscriptions: number;
  topApis: { apiId: string; apiName: string; requestCount: number }[];
}

interface UsageHistoryEntry {
  date: string;
  requestCount: number;
  errorCount: number;
  averageLatencyMs: number;
}

interface ApiUsage {
  apiId: string;
  apiName: string;
  totalRequests: number;
  averageLatencyMs: number;
  errorCount: number;
  errorRate: number;
}

interface Subscription {
  applicationId: string;
  apiId: string;
  apiName: string;
  planName: string;
  status: string;
}

interface LatencyPercentiles {
  p50: number;
  p75: number;
  p90: number;
  p95: number;
  p99: number;
}

/* ---------- helpers ---------- */

type TimeRange = '7d' | '30d' | '90d';

function fmtDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  } catch {
    return iso;
  }
}

function errorRateColor(rate: number): string {
  if (rate > 5) return '#dc2626';
  if (rate >= 2) return '#d97706';
  return '#16a34a';
}

/* ---------- shared styles ---------- */

const card: React.CSSProperties = {
  backgroundColor: '#fff', borderRadius: 12, border: '1px solid #e2e8f0', padding: 24, marginBottom: 24,
};
const sectionTitle: React.CSSProperties = { fontSize: 17, fontWeight: 600, color: '#1e293b', marginBottom: 16, marginTop: 0 };
const thStyle: React.CSSProperties = {
  textAlign: 'left', padding: '10px 14px', fontSize: 12, fontWeight: 600, color: '#64748b',
  textTransform: 'uppercase', letterSpacing: '0.05em', borderBottom: '1px solid #e2e8f0',
};
const tdStyle: React.CSSProperties = {
  padding: '12px 14px', fontSize: 14, color: '#334155', borderBottom: '1px solid #f1f5f9',
};
const btnPrimary: React.CSSProperties = {
  padding: '8px 18px', backgroundColor: '#3b82f6', color: '#fff', border: 'none',
  borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer',
};
const btnSecondary: React.CSSProperties = {
  padding: '6px 14px', backgroundColor: '#f8fafc', border: '1px solid #e2e8f0',
  borderRadius: 8, fontSize: 13, fontWeight: 500, color: '#475569', cursor: 'pointer',
};

const errBox = (msg: string) => (
  <div style={{ padding: 14, backgroundColor: '#fef2f2', border: '1px solid #fecaca', borderRadius: 8, color: '#dc2626', fontSize: 13, marginBottom: 16 }}>
    {msg}
  </div>
);

const emptyState = (title: string, subtitle: string) => (
  <div style={{ padding: 48, textAlign: 'center' }}>
    <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.4 }}>
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" style={{ display: 'inline-block' }}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
      </svg>
    </div>
    <h3 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginBottom: 6 }}>{title}</h3>
    <p style={{ fontSize: 13, color: '#94a3b8', margin: 0 }}>{subtitle}</p>
  </div>
);

/* ---------- component ---------- */

export default function UsagePage() {
  const [applications, setApplications] = useState<Application[]>([]);
  const [selectedAppId, setSelectedAppId] = useState<string>('all');
  const [timeRange, setTimeRange] = useState<TimeRange>('30d');

  const [summary, setSummary] = useState<UsageSummary | null>(null);
  const [history, setHistory] = useState<UsageHistoryEntry[]>([]);
  const [apiUsages, setApiUsages] = useState<ApiUsage[]>([]);
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [percentiles, setPercentiles] = useState<LatencyPercentiles | null>(null);

  const [loading, setLoading] = useState(true);
  const [appsLoading, setAppsLoading] = useState(true);
  const [sectionErrors, setSectionErrors] = useState<Record<string, string>>({});

  const setErr = (key: string, msg: string) =>
    setSectionErrors((prev) => ({ ...prev, [key]: msg }));

  /* Fetch applications once on mount */
  useEffect(() => {
    async function fetchApps() {
      try {
        const res = await fetch(`${IDENTITY_URL}/v1/applications`, { headers: authHeaders() });
        if (res.ok) {
          const data = await res.json();
          const list: Application[] = Array.isArray(data) ? data : data?.content || [];
          setApplications(list);
        }
      } catch {
        /* silently fall back to no app filter */
      } finally {
        setAppsLoading(false);
      }
    }
    fetchApps();
  }, []);

  /* Fetch usage data whenever filters change */
  const loadData = useCallback(async () => {
    setLoading(true);
    setSectionErrors({});
    const headers = authHeaders();

    const results = await Promise.allSettled([
      fetch(`${API_BASE}/v1/consumer/usage/summary`, { headers }).then((r) => r.ok ? r.json() : Promise.reject(new Error(`${r.status}`))),
      fetch(`${API_BASE}/v1/consumer/usage/history?range=${timeRange}`, { headers }).then((r) => r.ok ? r.json() : Promise.reject(new Error(`${r.status}`))),
      fetch(`${API_BASE}/v1/consumer/usage/apis`, { headers }).then((r) => r.ok ? r.json() : Promise.reject(new Error(`${r.status}`))),
      fetch(`${API_BASE}/v1/subscriptions`, { headers }).then((r) => r.ok ? r.json() : Promise.reject(new Error(`${r.status}`))),
      fetch(`${ANALYTICS_URL}/v1/analytics/health/percentiles?range=24h`, { headers }).then((r) => r.ok ? r.json() : Promise.reject(new Error(`${r.status}`))),
    ]);

    // Summary
    if (results[0].status === 'fulfilled') {
      setSummary(results[0].value as UsageSummary);
    } else {
      setSummary(null);
      setErr('summary', `Failed to load usage summary (${(results[0] as PromiseRejectedResult).reason})`);
    }

    // History
    if (results[1].status === 'fulfilled') {
      const d = results[1].value as { data: UsageHistoryEntry[] };
      setHistory(d.data || []);
    } else {
      setHistory([]);
      setErr('history', `Failed to load usage history (${(results[1] as PromiseRejectedResult).reason})`);
    }

    // API Breakdown
    if (results[2].status === 'fulfilled') {
      const d = results[2].value as { apis: ApiUsage[] };
      const list = d.apis || [];
      list.sort((a, b) => b.totalRequests - a.totalRequests);
      setApiUsages(list);
    } else {
      setApiUsages([]);
      setErr('apis', `Failed to load API breakdown (${(results[2] as PromiseRejectedResult).reason})`);
    }

    // Subscriptions
    if (results[3].status === 'fulfilled') {
      const raw = results[3].value;
      const list: Subscription[] = Array.isArray(raw) ? raw : raw?.content || [];
      setSubscriptions(list);
    } else {
      setSubscriptions([]);
      setErr('subscriptions', `Failed to load subscriptions (${(results[3] as PromiseRejectedResult).reason})`);
    }

    // Latency Percentiles
    if (results[4].status === 'fulfilled') {
      setPercentiles(results[4].value as LatencyPercentiles);
    } else {
      setPercentiles(null);
      setErr('percentiles', `Failed to load latency percentiles (${(results[4] as PromiseRejectedResult).reason})`);
    }

    setLoading(false);
  }, [timeRange]);

  useEffect(() => { loadData(); }, [loadData]);

  /* Filtered subscriptions by selected app */
  const filteredSubscriptions = selectedAppId === 'all'
    ? subscriptions
    : subscriptions.filter((s) => s.applicationId === selectedAppId);

  /* Export CSV */
  const exportCsv = () => {
    if (apiUsages.length === 0) return;
    const header = 'API Name,Total Requests,Avg Latency (ms),Errors,Error Rate (%)';
    const rows = apiUsages.map((api) =>
      `"${api.apiName}",${api.totalRequests},${Math.round(api.averageLatencyMs)},${api.errorCount},${api.errorRate.toFixed(2)}`
    );
    const csvContent = [header, ...rows].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `api-usage-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  /* Chart data */
  const maxRequests = Math.max(...history.map((h) => h.requestCount), 1);

  /* ---------- loading skeleton ---------- */
  if (loading && !summary) {
    return (
      <div style={{ maxWidth: 1060 }}>
        <div style={{ marginBottom: 28 }}>
          <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>Usage &amp; Analytics</h1>
          <p style={{ fontSize: 14, color: '#64748b', margin: 0 }}>Monitor your API consumption, performance, and errors</p>
        </div>
        {/* Skeleton filter row */}
        <div style={{ display: 'flex', gap: 12, marginBottom: 24 }}>
          <div style={{ width: 200, height: 38, borderRadius: 8, background: 'linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.5s infinite' }} />
          <div style={{ width: 180, height: 38, borderRadius: 8, background: 'linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.5s infinite' }} />
        </div>
        {/* Skeleton stat cards */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 28 }}>
          {[1, 2, 3, 4].map((i) => (
            <div key={i} style={{ height: 100, borderRadius: 10, background: 'linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.5s infinite' }} />
          ))}
        </div>
        {/* Skeleton chart */}
        <div style={{ ...card, height: 220, background: 'linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.5s infinite' }} />
        {/* Skeleton table */}
        <div style={{ ...card, height: 160, background: 'linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.5s infinite' }} />
        <style>{`@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }`}</style>
      </div>
    );
  }

  const rangeOptions: TimeRange[] = ['7d', '30d', '90d'];
  const rangeLabels: Record<TimeRange, string> = { '7d': '7 Days', '30d': '30 Days', '90d': '90 Days' };

  /* Latency percentile helpers */
  const percentileKeys: { key: keyof LatencyPercentiles; label: string; color: string }[] = [
    { key: 'p50', label: 'P50', color: '#16a34a' },
    { key: 'p75', label: 'P75', color: '#2563eb' },
    { key: 'p90', label: 'P90', color: '#8b5cf6' },
    { key: 'p95', label: 'P95', color: '#d97706' },
    { key: 'p99', label: 'P99', color: '#dc2626' },
  ];
  const maxPercentile = percentiles
    ? Math.max(...percentileKeys.map((pk) => percentiles[pk.key]), 1)
    : 1;

  return (
    <div style={{ maxWidth: 1060 }}>
      {/* =========== HEADER =========== */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 28 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>Usage &amp; Analytics</h1>
          <p style={{ fontSize: 14, color: '#64748b', margin: 0 }}>Monitor your API consumption, performance, and errors</p>
        </div>
        <button
          style={{ ...btnPrimary, display: 'flex', alignItems: 'center', gap: 6, opacity: apiUsages.length === 0 ? 0.5 : 1 }}
          onClick={exportCsv}
          disabled={apiUsages.length === 0}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" />
          </svg>
          Export CSV
        </button>
      </div>

      {/* =========== FILTERS ROW =========== */}
      <div style={{
        position: 'sticky', top: 0, zIndex: 10, backgroundColor: '#f8fafc',
        padding: '12px 0', marginBottom: 20, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap',
      }}>
        {/* Application dropdown */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <label style={{ fontSize: 13, fontWeight: 600, color: '#475569' }}>Application:</label>
          <select
            style={{
              padding: '7px 12px', border: '1px solid #e2e8f0', borderRadius: 8,
              fontSize: 14, color: '#334155', backgroundColor: '#fff', cursor: 'pointer', minWidth: 180,
            }}
            value={selectedAppId}
            onChange={(e) => setSelectedAppId(e.target.value)}
            disabled={appsLoading}
          >
            <option value="all">All Applications</option>
            {applications.map((app) => (
              <option key={app.id} value={app.id}>{app.name}</option>
            ))}
          </select>
        </div>

        {/* Time range toggle */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <label style={{ fontSize: 13, fontWeight: 600, color: '#475569' }}>Range:</label>
          <div style={{ display: 'flex', borderRadius: 8, border: '1px solid #e2e8f0', overflow: 'hidden' }}>
            {rangeOptions.map((r) => (
              <button
                key={r}
                onClick={() => setTimeRange(r)}
                style={{
                  padding: '7px 16px', border: 'none', fontSize: 13, fontWeight: 600, cursor: 'pointer',
                  backgroundColor: timeRange === r ? '#3b82f6' : '#fff',
                  color: timeRange === r ? '#fff' : '#64748b',
                  transition: 'all 0.15s',
                }}
              >
                {rangeLabels[r]}
              </button>
            ))}
          </div>
        </div>

        {loading && (
          <span style={{ fontSize: 12, color: '#94a3b8', fontStyle: 'italic' }}>Refreshing...</span>
        )}
      </div>

      {/* =========== SUMMARY STAT CARDS =========== */}
      {sectionErrors.summary && errBox(sectionErrors.summary)}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 28 }}>
        {[
          {
            label: 'Total Requests',
            value: summary ? summary.requestsThisMonth.toLocaleString() : '--',
            sub: 'This month',
            color: '#3b82f6',
            iconBg: '#dbeafe',
          },
          {
            label: 'Avg Latency',
            value: summary ? `${Math.round(summary.averageLatencyMs)} ms` : '--',
            sub: 'This month',
            color: '#8b5cf6',
            iconBg: '#ede9fe',
          },
          {
            label: 'Error Rate',
            value: summary ? `${summary.errorRate.toFixed(2)}%` : '--',
            sub: summary && summary.errorRate > 5 ? 'Above threshold' : 'Healthy',
            color: summary ? errorRateColor(summary.errorRate) : '#16a34a',
            iconBg: summary && summary.errorRate > 5 ? '#fee2e2' : '#dcfce7',
          },
          {
            label: 'Active Subscriptions',
            value: summary ? summary.activeSubscriptions.toString() : '--',
            sub: 'Current',
            color: '#0891b2',
            iconBg: '#cffafe',
          },
        ].map((stat) => (
          <div key={stat.label} style={{
            backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: 20,
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div style={{ fontSize: 12, color: '#94a3b8', marginBottom: 6, textTransform: 'uppercase', fontWeight: 600, letterSpacing: '0.05em' }}>{stat.label}</div>
                <div style={{ fontSize: 28, fontWeight: 700, color: stat.color, lineHeight: 1 }}>{stat.value}</div>
                <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 6 }}>{stat.sub}</div>
              </div>
              <div style={{
                width: 40, height: 40, borderRadius: 10, backgroundColor: stat.iconBg,
                display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
              }}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={stat.color} strokeWidth="2">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
                </svg>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* =========== LATENCY DISTRIBUTION =========== */}
      <div style={card}>
        <h2 style={sectionTitle}>Latency Distribution (Last 24h)</h2>
        {sectionErrors.percentiles && errBox(sectionErrors.percentiles)}
        {!percentiles && !sectionErrors.percentiles
          ? emptyState('No latency data yet', 'Latency percentiles will appear once you start making API calls.')
          : percentiles && (
            <div>
              {/* Card row showing each percentile value */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 12, marginBottom: 24 }}>
                {percentileKeys.map((pk) => (
                  <div key={pk.key} style={{
                    backgroundColor: '#f8fafc', borderRadius: 10, border: '1px solid #e2e8f0',
                    padding: '16px 14px', textAlign: 'center',
                  }}>
                    <div style={{ fontSize: 11, fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 6 }}>{pk.label}</div>
                    <div style={{ fontSize: 22, fontWeight: 700, color: pk.color, lineHeight: 1 }}>{Math.round(percentiles[pk.key])} ms</div>
                  </div>
                ))}
              </div>

              {/* Horizontal bar chart */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {percentileKeys.map((pk) => {
                  const val = percentiles[pk.key];
                  const widthPct = (val / maxPercentile) * 100;
                  return (
                    <div key={pk.key} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <div style={{ width: 40, fontSize: 12, fontWeight: 600, color: '#64748b', textAlign: 'right', flexShrink: 0 }}>{pk.label}</div>
                      <div style={{ flex: 1, height: 22, backgroundColor: '#f1f5f9', borderRadius: 6, overflow: 'hidden', position: 'relative' }}>
                        <div style={{
                          height: '100%',
                          width: `${Math.max(widthPct, 2)}%`,
                          backgroundColor: pk.color,
                          borderRadius: 6,
                          transition: 'width 0.4s ease',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'flex-end',
                          paddingRight: 8,
                        }}>
                          {widthPct > 15 && (
                            <span style={{ fontSize: 11, fontWeight: 600, color: '#fff' }}>{Math.round(val)} ms</span>
                          )}
                        </div>
                        {widthPct <= 15 && (
                          <span style={{ position: 'absolute', left: `calc(${Math.max(widthPct, 2)}% + 6px)`, top: '50%', transform: 'translateY(-50%)', fontSize: 11, fontWeight: 600, color: '#64748b' }}>
                            {Math.round(val)} ms
                          </span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
      </div>

      {/* =========== USAGE TREND CHART =========== */}
      <div style={card}>
        <h2 style={sectionTitle}>Usage Trend ({rangeLabels[timeRange]})</h2>
        {sectionErrors.history && errBox(sectionErrors.history)}
        {history.length === 0 && !sectionErrors.history
          ? emptyState('No usage data yet', 'Usage history will appear once you start making API calls.')
          : (
            <div>
              {/* Y-axis label */}
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 0, height: 220, paddingBottom: 4 }}>
                {history.map((entry, idx) => {
                  const successCount = entry.requestCount - entry.errorCount;
                  const successPct = (successCount / maxRequests) * 100;
                  const errorPct = (entry.errorCount / maxRequests) * 100;
                  const barWidth = Math.max(Math.floor(600 / history.length) - 2, 6);
                  return (
                    <div
                      key={entry.date}
                      style={{
                        flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center',
                        justifyContent: 'flex-end', height: '100%', position: 'relative',
                      }}
                      title={`${fmtDate(entry.date)}: ${entry.requestCount.toLocaleString()} requests, ${entry.errorCount} errors`}
                    >
                      {/* Stacked bars */}
                      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'flex-end', width: barWidth, maxWidth: 32, height: '100%' }}>
                        {entry.errorCount > 0 && (
                          <div style={{
                            width: '100%', height: `${errorPct}%`, backgroundColor: '#ef4444',
                            borderRadius: entry.requestCount === entry.errorCount ? '3px 3px 0 0' : 0,
                            minHeight: errorPct > 0 ? 2 : 0,
                          }} />
                        )}
                        {successCount > 0 && (
                          <div style={{
                            width: '100%', height: `${successPct}%`, backgroundColor: '#3b82f6',
                            borderRadius: entry.errorCount > 0 ? '0 0 0 0' : '3px 3px 0 0',
                            minHeight: successPct > 0 ? 2 : 0,
                          }} />
                        )}
                      </div>
                      {/* X-axis label - show every nth label depending on data count */}
                      {(history.length <= 14 || idx % Math.ceil(history.length / 14) === 0) && (
                        <div style={{
                          fontSize: 10, color: '#94a3b8', marginTop: 6, whiteSpace: 'nowrap',
                          transform: history.length > 14 ? 'rotate(-45deg)' : 'none',
                          transformOrigin: 'top center',
                        }}>
                          {fmtDate(entry.date)}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
              {/* Legend */}
              <div style={{ display: 'flex', gap: 16, marginTop: 20, fontSize: 12, color: '#94a3b8' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <span style={{ display: 'inline-block', width: 10, height: 10, backgroundColor: '#3b82f6', borderRadius: 2 }} />
                  Successful
                </span>
                <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <span style={{ display: 'inline-block', width: 10, height: 10, backgroundColor: '#ef4444', borderRadius: 2 }} />
                  Errors
                </span>
              </div>
            </div>
          )}
      </div>

      {/* =========== PER-API BREAKDOWN TABLE =========== */}
      <div style={card}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h2 style={{ ...sectionTitle, marginBottom: 0 }}>Per-API Breakdown</h2>
          <button
            style={{ ...btnSecondary, display: 'flex', alignItems: 'center', gap: 4, opacity: apiUsages.length === 0 ? 0.5 : 1 }}
            onClick={exportCsv}
            disabled={apiUsages.length === 0}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" />
            </svg>
            CSV
          </button>
        </div>
        {sectionErrors.apis && errBox(sectionErrors.apis)}
        {apiUsages.length === 0 && !sectionErrors.apis
          ? emptyState('No API usage yet', 'Per-API statistics will appear once you subscribe and start calling APIs.')
          : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={thStyle}>API Name</th>
                    <th style={thStyle}>Volume</th>
                    <th style={{ ...thStyle, textAlign: 'right' }}>Total Requests</th>
                    <th style={{ ...thStyle, textAlign: 'right' }}>Avg Latency</th>
                    <th style={{ ...thStyle, textAlign: 'right' }}>Errors</th>
                    <th style={{ ...thStyle, textAlign: 'right' }}>Error Rate</th>
                  </tr>
                </thead>
                <tbody>
                  {apiUsages.map((api) => {
                    const maxReq = apiUsages[0]?.totalRequests || 1;
                    const volumePct = Math.round((api.totalRequests / maxReq) * 100);
                    return (
                      <tr key={api.apiId} style={{ transition: 'background 0.1s' }} onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#f8fafc')} onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}>
                        <td style={{ ...tdStyle, fontWeight: 600 }}>{api.apiName}</td>
                        <td style={{ ...tdStyle, width: 120 }}>
                          <div style={{ height: 8, backgroundColor: '#f1f5f9', borderRadius: 4, overflow: 'hidden' }}>
                            <div style={{ height: '100%', width: `${volumePct}%`, backgroundColor: '#3b82f6', borderRadius: 4, transition: 'width 0.3s' }} />
                          </div>
                        </td>
                        <td style={{ ...tdStyle, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{api.totalRequests.toLocaleString()}</td>
                        <td style={{ ...tdStyle, textAlign: 'right' }}>{Math.round(api.averageLatencyMs)} ms</td>
                        <td style={{ ...tdStyle, textAlign: 'right', color: api.errorCount > 0 ? '#dc2626' : '#334155' }}>{api.errorCount.toLocaleString()}</td>
                        <td style={{ ...tdStyle, textAlign: 'right' }}>
                          <span style={{
                            display: 'inline-block', padding: '2px 8px', borderRadius: 999,
                            fontSize: 12, fontWeight: 600,
                            backgroundColor: api.errorRate > 5 ? '#fee2e2' : api.errorRate >= 2 ? '#fef3c7' : '#dcfce7',
                            color: errorRateColor(api.errorRate),
                          }}>
                            {api.errorRate.toFixed(2)}%
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
      </div>

      {/* =========== SUBSCRIPTIONS USAGE TABLE =========== */}
      <div style={card}>
        <h2 style={sectionTitle}>Subscriptions Usage</h2>
        {sectionErrors.subscriptions && errBox(sectionErrors.subscriptions)}
        {filteredSubscriptions.length === 0 && !sectionErrors.subscriptions
          ? emptyState(
              selectedAppId !== 'all' ? 'No subscriptions for this application' : 'No subscriptions yet',
              'Subscribe to APIs from the catalog to see subscription usage here.',
            )
          : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={thStyle}>API Name</th>
                    <th style={thStyle}>Plan</th>
                    <th style={{ ...thStyle, textAlign: 'center' }}>Status</th>
                    <th style={{ ...thStyle, textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredSubscriptions.map((sub, idx) => {
                    const statusStyle: Record<string, { bg: string; fg: string }> = {
                      APPROVED: { bg: '#dcfce7', fg: '#16a34a' },
                      PENDING: { bg: '#fef3c7', fg: '#d97706' },
                      REJECTED: { bg: '#fee2e2', fg: '#dc2626' },
                      SUSPENDED: { bg: '#f1f5f9', fg: '#64748b' },
                    };
                    const colors = statusStyle[sub.status] || { bg: '#f1f5f9', fg: '#64748b' };
                    return (
                      <tr key={`${sub.applicationId}-${sub.apiId}-${idx}`}>
                        <td style={{ ...tdStyle, fontWeight: 600 }}>{sub.apiName || sub.apiId}</td>
                        <td style={tdStyle}>{sub.planName || '-'}</td>
                        <td style={{ ...tdStyle, textAlign: 'center' }}>
                          <span style={{
                            display: 'inline-block', padding: '3px 10px', borderRadius: 999,
                            fontSize: 12, fontWeight: 600, backgroundColor: colors.bg, color: colors.fg,
                          }}>
                            {sub.status}
                          </span>
                        </td>
                        <td style={{ ...tdStyle, textAlign: 'right' }}>
                          <a
                            href={`/billing`}
                            style={{ fontSize: 13, fontWeight: 500, color: '#3b82f6', textDecoration: 'none' }}
                          >
                            View Usage
                          </a>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
      </div>

      <style>{`@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }`}</style>
    </div>
  );
}
