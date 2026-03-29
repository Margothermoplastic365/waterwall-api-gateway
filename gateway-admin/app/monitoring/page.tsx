'use client';

import React, { useEffect, useState, useRef, useCallback } from 'react';

const ANALYTICS_URL = process.env.NEXT_PUBLIC_ANALYTICS_URL || 'http://localhost:8083';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface ServiceHealth {
  name: string;
  port: number;
  status: 'UP' | 'DOWN';
  latencyMs: number;
}

interface GatewayStats {
  currentRps: number;
  errorRate: number;
  avgLatencyMs: number;
}

interface LatencyPercentiles {
  p50: number;
  p75: number;
  p90: number;
  p95: number;
  p99: number;
}

interface TopError {
  code: number;
  message: string;
  count: number;
  [key: string]: unknown;
}

interface LiveEvent {
  id: string;
  timestamp: string;
  message: string;
}

interface ErrorDrillDownRow {
  timestamp: string;
  method: string;
  path: string;
  consumer: string;
  latency: number;
  clientIp: string;
  [key: string]: unknown;
}

/* ------------------------------------------------------------------ */
/*  Fetch helpers                                                      */
/* ------------------------------------------------------------------ */

function fetchApi<T>(path: string): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('admin_token') || localStorage.getItem('token') || localStorage.getItem('jwt_token') || '' : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(`${ANALYTICS_URL}${path}`, { headers }).then((r) => {
    if (!r.ok) throw new Error('Failed');
    return r.json();
  });
}

function postApi<T>(path: string, body: unknown): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('admin_token') || localStorage.getItem('token') || localStorage.getItem('jwt_token') || '' : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(`${ANALYTICS_URL}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  }).then((r) => {
    if (!r.ok) throw new Error('Failed');
    return r.json();
  });
}

/* ------------------------------------------------------------------ */
/*  Constants                                                          */
/* ------------------------------------------------------------------ */

const SERVICES: { name: string; port: number }[] = [
  { name: 'Identity Service', port: 8081 },
  { name: 'Management Service', port: 8082 },
  { name: 'API Gateway', port: 8080 },
  { name: 'Analytics Service', port: 8083 },
  { name: 'Notification Service', port: 8084 },
];

const REFRESH_INTERVAL = 10_000;

const ERROR_DRILL_DOWN_COLUMNS: { key: keyof ErrorDrillDownRow; label: string }[] = [
  { key: 'timestamp', label: 'Timestamp' },
  { key: 'method', label: 'Method' },
  { key: 'path', label: 'Path' },
  { key: 'consumer', label: 'Consumer' },
  { key: 'latency', label: 'Latency' },
  { key: 'clientIp', label: 'Client IP' },
];

/* ------------------------------------------------------------------ */
/*  Skeleton                                                           */
/* ------------------------------------------------------------------ */

function CardSkeleton() {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5">
      <div className="animate-pulse space-y-3">
        <div className="h-3 w-24 rounded bg-slate-200" />
        <div className="h-7 w-32 rounded bg-slate-200" />
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Spinner                                                            */
/* ------------------------------------------------------------------ */

function Spinner() {
  return (
    <svg className="h-5 w-5 animate-spin text-purple-500" fill="none" viewBox="0 0 24 24">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  );
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function MonitoringPage() {
  const [services, setServices] = useState<ServiceHealth[]>([]);
  const [gatewayStats, setGatewayStats] = useState<GatewayStats | null>(null);
  const [percentiles, setPercentiles] = useState<LatencyPercentiles | null>(null);
  const [topErrors, setTopErrors] = useState<TopError[]>([]);
  const [liveEvents, setLiveEvents] = useState<LiveEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const sseRef = useRef<EventSource | null>(null);
  const tickerRef = useRef<HTMLDivElement>(null);

  /* Error drill-down state */
  const [expandedErrorCode, setExpandedErrorCode] = useState<number | null>(null);
  const [errorDrillDownData, setErrorDrillDownData] = useState<ErrorDrillDownRow[]>([]);
  const [errorDrillDownLoading, setErrorDrillDownLoading] = useState(false);
  const [errorDrillDownError, setErrorDrillDownError] = useState('');

  const loadData = useCallback(async () => {
    try {
      // Single endpoint returns all monitoring data
      const data = await fetchApi<{
        services?: ServiceHealth[];
        gateway?: GatewayStats;
        percentiles?: LatencyPercentiles;
        topErrors?: TopError[];
        queueDepth?: number;
      }>('/v1/analytics/health');

      if (data.services && data.services.length > 0) {
        setServices(data.services);
      } else {
        setServices(
          SERVICES.map((s) => ({ name: s.name, port: s.port, status: 'DOWN', latencyMs: 0 })),
        );
      }

      if (data.gateway) setGatewayStats(data.gateway);
      if (data.percentiles) setPercentiles(data.percentiles);
      setTopErrors(Array.isArray(data.topErrors) ? data.topErrors : []);
      setError('');
    } catch {
      setError('Failed to load monitoring data');
      // Populate default services on error
      setServices(
        SERVICES.map((s) => ({ name: s.name, port: s.port, status: 'DOWN', latencyMs: 0 })),
      );
    } finally {
      setLoading(false);
    }
  }, []);

  /* Error drill-down handler */
  const handleErrorClick = useCallback(async (errorCode: number) => {
    if (expandedErrorCode === errorCode) {
      setExpandedErrorCode(null);
      setErrorDrillDownData([]);
      setErrorDrillDownError('');
      return;
    }

    setExpandedErrorCode(errorCode);
    setErrorDrillDownLoading(true);
    setErrorDrillDownError('');
    setErrorDrillDownData([]);

    try {
      const now = new Date();
      const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);

      const payload = {
        metrics: ['request_count'],
        filters: {
          dateFrom: oneHourAgo.toISOString(),
          dateTo: now.toISOString(),
          statusCodes: [errorCode],
        },
        limit: 20,
        // No groupBy -- raw logs
      };

      const data = await postApi<ErrorDrillDownRow[]>('/v1/analytics/report/query', payload);
      setErrorDrillDownData(Array.isArray(data) ? data.slice(0, 20) : []);
    } catch {
      setErrorDrillDownError('Failed to load error details');
    } finally {
      setErrorDrillDownLoading(false);
    }
  }, [expandedErrorCode]);

  /* Initial load + auto-refresh */
  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, [loadData]);

  /* SSE live stream */
  useEffect(() => {
    const token = typeof window !== 'undefined' ? localStorage.getItem('admin_token') || localStorage.getItem('token') || localStorage.getItem('jwt_token') || '' : '';
    const url = `${ANALYTICS_URL}/v1/analytics/stream${token ? `?token=${encodeURIComponent(token)}` : ''}`;

    const es = new EventSource(url);
    sseRef.current = es;

    es.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        const liveEvent: LiveEvent = {
          id: data.id || crypto.randomUUID(),
          timestamp: data.timestamp || new Date().toISOString(),
          message: data.message || JSON.stringify(data),
        };
        setLiveEvents((prev) => [liveEvent, ...prev].slice(0, 50));
      } catch {
        // non-JSON event
        setLiveEvents((prev) => [
          { id: crypto.randomUUID(), timestamp: new Date().toISOString(), message: event.data },
          ...prev,
        ].slice(0, 50));
      }
    };

    es.onerror = () => {
      // SSE will auto-reconnect
    };

    return () => {
      es.close();
      sseRef.current = null;
    };
  }, []);

  /* ---------- Loading skeleton ---------- */
  if (loading) {
    return (
      <div className="mx-auto max-w-7xl space-y-8 px-4 py-10 sm:px-6 lg:px-8">
        <div className="animate-pulse space-y-2">
          <div className="h-8 w-56 rounded bg-slate-200" />
          <div className="h-4 w-80 rounded bg-slate-200" />
        </div>
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-5">
          {Array.from({ length: 5 }).map((_, i) => (
            <CardSkeleton key={i} />
          ))}
        </div>
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <CardSkeleton key={i} />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl space-y-8 px-4 py-10 sm:px-6 lg:px-8">
      {/* ---- Header ---- */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Platform Monitoring</h1>
        <p className="mt-1 text-sm text-slate-500">
          Real-time health, latency, and error monitoring across all platform services
        </p>
      </div>

      {/* ---- Error banner ---- */}
      {error && (
        <div className="flex items-center gap-3 rounded-xl border border-red-200 bg-red-50 px-5 py-4">
          <svg className="h-5 w-5 shrink-0 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
          </svg>
          <p className="text-sm font-medium text-red-700">{error}</p>
          <button onClick={loadData} className="ml-auto rounded-lg bg-red-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-red-700">
            Retry
          </button>
        </div>
      )}

      {/* ---- Service Health Cards ---- */}
      <div>
        <h2 className="mb-4 text-base font-semibold text-slate-900">Service Health</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {services.map((svc) => (
            <div
              key={svc.name}
              className="relative overflow-hidden rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md"
              style={{ borderLeftWidth: '4px', borderLeftColor: svc.status === 'UP' ? '#8b5cf6' : '#ef4444' }}
            >
              <div className="flex items-center gap-2 mb-2">
                <span
                  className={`inline-block h-2.5 w-2.5 rounded-full ${
                    svc.status === 'UP' ? 'bg-emerald-500 shadow-sm shadow-emerald-300' : 'bg-red-500 shadow-sm shadow-red-300'
                  }`}
                />
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                  {svc.status}
                </span>
              </div>
              <p className="text-sm font-semibold text-slate-900">{svc.name}</p>
              <p className="text-xs text-slate-400 mt-0.5">Port {svc.port}</p>
              <p className="mt-2 text-lg font-bold text-purple-700">{svc.latencyMs}ms</p>
            </div>
          ))}
        </div>
      </div>

      {/* ---- Gateway Stats ---- */}
      {gatewayStats && (
        <div>
          <h2 className="mb-4 text-base font-semibold text-slate-900">Gateway Stats</h2>
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm" style={{ borderLeftWidth: '4px', borderLeftColor: '#8b5cf6' }}>
              <p className="text-xs font-medium uppercase tracking-wider text-slate-500">Current RPS</p>
              <p className="mt-1 text-2xl font-bold text-slate-900">{gatewayStats.currentRps.toLocaleString()}</p>
            </div>
            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm" style={{ borderLeftWidth: '4px', borderLeftColor: gatewayStats.errorRate > 5 ? '#ef4444' : '#8b5cf6' }}>
              <p className="text-xs font-medium uppercase tracking-wider text-slate-500">Error Rate</p>
              <p className={`mt-1 text-2xl font-bold ${gatewayStats.errorRate > 5 ? 'text-red-600' : 'text-slate-900'}`}>
                {gatewayStats.errorRate.toFixed(2)}%
              </p>
            </div>
            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm" style={{ borderLeftWidth: '4px', borderLeftColor: '#8b5cf6' }}>
              <p className="text-xs font-medium uppercase tracking-wider text-slate-500">Avg Latency</p>
              <p className="mt-1 text-2xl font-bold text-slate-900">{gatewayStats.avgLatencyMs}ms</p>
            </div>
          </div>
        </div>
      )}

      {/* ---- Latency Percentiles ---- */}
      {percentiles && (
        <div>
          <h2 className="mb-4 text-base font-semibold text-slate-900">Latency Percentiles</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-5">
            {(['p50', 'p75', 'p90', 'p95', 'p99'] as const).map((key) => {
              const value = percentiles[key];
              const maxVal = percentiles.p99 || 1;
              const pct = Math.min((value / maxVal) * 100, 100);
              return (
                <div key={key} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-wider text-purple-600">{key.toUpperCase()}</p>
                  <p className="mt-1 text-xl font-bold text-slate-900">{value}ms</p>
                  <div className="mt-2 h-2 w-full rounded-full bg-slate-100">
                    <div
                      className="h-2 rounded-full bg-purple-500 transition-all"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* ---- Top Errors with Drill-Down ---- */}
      <div>
        <h2 className="mb-4 text-base font-semibold text-slate-900">Top Errors</h2>
        <p className="mb-2 text-xs text-slate-400">Click an error code to see the last 20 requests with that status</p>
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          {topErrors.length > 0 ? (
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-100 bg-slate-50/60">
                <tr>
                  <th className="px-6 py-3 font-semibold text-slate-600">Error Code</th>
                  <th className="px-6 py-3 font-semibold text-slate-600">Message</th>
                  <th className="px-6 py-3 text-right font-semibold text-slate-600">Count</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {topErrors.map((err, i) => (
                  <React.Fragment key={i}>
                    <tr
                      onClick={() => handleErrorClick(err.code)}
                      className={`cursor-pointer transition-colors ${
                        expandedErrorCode === err.code
                          ? 'bg-purple-50 border-l-4 border-l-purple-500'
                          : 'hover:bg-slate-50/50'
                      }`}
                    >
                      <td className="px-6 py-3">
                        <span className="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-700">
                          {err.code}
                        </span>
                      </td>
                      <td className="px-6 py-3 text-slate-700">{err.message}</td>
                      <td className="px-6 py-3 text-right font-mono font-semibold text-slate-900">{err.count.toLocaleString()}</td>
                    </tr>

                    {/* Drill-down expanded section */}
                    {expandedErrorCode === err.code && (
                      <tr>
                        <td colSpan={3} className="p-0">
                          <div className="bg-purple-50/50 border-t border-purple-100 px-6 py-4">
                            <div className="flex items-center justify-between mb-3">
                              <h4 className="text-sm font-semibold text-purple-900">
                                Last 20 requests with status {err.code}
                              </h4>
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setExpandedErrorCode(null);
                                  setErrorDrillDownData([]);
                                  setErrorDrillDownError('');
                                }}
                                className="inline-flex items-center gap-1 rounded-lg border border-purple-200 bg-white px-3 py-1.5 text-xs font-medium text-purple-700 hover:bg-purple-50 transition-colors"
                              >
                                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                                </svg>
                                Close
                              </button>
                            </div>

                            {errorDrillDownLoading && (
                              <div className="flex items-center justify-center gap-2 py-8">
                                <Spinner />
                                <span className="text-sm text-purple-600">Loading error details...</span>
                              </div>
                            )}

                            {errorDrillDownError && (
                              <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
                                <svg className="h-4 w-4 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
                                </svg>
                                <p className="text-sm text-red-700">{errorDrillDownError}</p>
                              </div>
                            )}

                            {!errorDrillDownLoading && !errorDrillDownError && errorDrillDownData.length === 0 && (
                              <div className="flex flex-col items-center justify-center gap-1 py-8 text-center">
                                <svg className="h-8 w-8 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M20 13V7a2 2 0 00-2-2H6a2 2 0 00-2 2v6m16 0v6a2 2 0 01-2 2H6a2 2 0 01-2-2v-6m16 0H4" />
                                </svg>
                                <p className="text-sm text-slate-500">No matching error requests found</p>
                              </div>
                            )}

                            {!errorDrillDownLoading && errorDrillDownData.length > 0 && (
                              <div className="overflow-x-auto rounded-lg border border-purple-100 bg-white">
                                <table className="w-full text-left text-xs">
                                  <thead className="border-b border-purple-100 bg-purple-50/80">
                                    <tr>
                                      {ERROR_DRILL_DOWN_COLUMNS.map((col) => (
                                        <th key={col.key} className="px-4 py-2.5 font-semibold text-purple-700 whitespace-nowrap">
                                          {col.label}
                                        </th>
                                      ))}
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-slate-100">
                                    {errorDrillDownData.map((detail, j) => (
                                      <tr key={j} className="hover:bg-slate-50/50 transition-colors">
                                        {ERROR_DRILL_DOWN_COLUMNS.map((col) => (
                                          <td key={col.key} className="px-4 py-2 text-slate-700 whitespace-nowrap">
                                            {col.key === 'timestamp'
                                              ? new Date(String(detail[col.key])).toLocaleString()
                                              : col.key === 'latency'
                                                ? `${detail[col.key]}ms`
                                                : String(detail[col.key] ?? '')}
                                          </td>
                                        ))}
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="flex flex-col items-center justify-center gap-2 py-12 text-center">
              <svg className="h-10 w-10 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <p className="text-sm text-slate-500">No recent errors</p>
            </div>
          )}
        </div>
      </div>

      {/* ---- Live Event Ticker ---- */}
      <div>
        <div className="mb-4 flex items-center gap-2">
          <span className="relative flex h-2.5 w-2.5">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-purple-400 opacity-75" />
            <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-purple-500" />
          </span>
          <h2 className="text-base font-semibold text-slate-900">Live Event Stream</h2>
        </div>
        <div
          ref={tickerRef}
          className="max-h-64 overflow-y-auto rounded-xl border border-slate-200 bg-slate-900 p-4 shadow-sm"
        >
          {liveEvents.length > 0 ? (
            <div className="space-y-1.5 font-mono text-xs">
              {liveEvents.map((evt) => (
                <div key={evt.id} className="flex gap-3 text-slate-300">
                  <span className="shrink-0 text-purple-400">
                    {new Date(evt.timestamp).toLocaleTimeString()}
                  </span>
                  <span className="text-slate-400">{evt.message}</span>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-center text-xs text-slate-500">Waiting for live events...</p>
          )}
        </div>
      </div>

      {/* ---- Auto-refresh indicator ---- */}
      <div className="flex items-center justify-center gap-2 text-xs text-slate-400">
        <svg className="h-3.5 w-3.5 animate-spin text-purple-500" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        Auto-refreshing every 10 seconds
      </div>
    </div>
  );
}
