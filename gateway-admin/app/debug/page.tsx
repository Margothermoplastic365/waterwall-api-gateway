'use client';

import { useState, useCallback } from 'react';

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8082';
const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const;

interface HeaderEntry {
  key: string;
  value: string;
}

interface FilterStep {
  filterName: string;
  decision: string;
  durationMs: number;
  details: string;
}

interface TraceResult {
  requestPath: string;
  method: string;
  totalDurationMs: number;
  steps: FilterStep[];
}

export default function DebugPage() {
  const [path, setPath] = useState('/api/v1/test');
  const [method, setMethod] = useState('GET');
  const [headers, setHeaders] = useState<HeaderEntry[]>([{ key: '', value: '' }]);
  const [body, setBody] = useState('');
  const [tracing, setTracing] = useState(false);
  const [result, setResult] = useState<TraceResult | null>(null);
  const [error, setError] = useState('');

  const addHeader = () => setHeaders((prev) => [...prev, { key: '', value: '' }]);

  const updateHeader = (idx: number, field: 'key' | 'value', val: string) => {
    setHeaders((prev) => prev.map((h, i) => (i === idx ? { ...h, [field]: val } : h)));
  };

  const removeHeader = (idx: number) => {
    setHeaders((prev) => prev.filter((_, i) => i !== idx));
  };

  const handleTrace = useCallback(async () => {
    setTracing(true);
    setError('');
    setResult(null);
    try {
      const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
      const hdrs: Record<string, string> = { 'Content-Type': 'application/json' };
      if (token) hdrs['Authorization'] = `Bearer ${token}`;

      const headerMap: Record<string, string> = {};
      headers.forEach((h) => {
        if (h.key.trim()) headerMap[h.key.trim()] = h.value;
      });

      const res = await fetch(`${GATEWAY_URL}/v1/gateway/debug/trace`, {
        method: 'POST',
        headers: hdrs,
        body: JSON.stringify({
          path,
          method,
          headers: headerMap,
          body: body || undefined,
        }),
      });

      if (!res.ok) throw new Error('Trace request failed');
      const data: TraceResult = await res.json();
      setResult(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to execute trace');
    } finally {
      setTracing(false);
    }
  }, [path, method, headers, body]);

  const decisionStyles = (decision: string) => {
    switch (decision.toUpperCase()) {
      case 'PASS': return { bg: 'bg-emerald-50', border: 'border-emerald-200', badge: 'bg-emerald-600', text: 'text-emerald-700' };
      case 'REJECT': return { bg: 'bg-red-50', border: 'border-red-200', badge: 'bg-red-600', text: 'text-red-700' };
      default: return { bg: 'bg-slate-50', border: 'border-slate-200', badge: 'bg-slate-500', text: 'text-slate-600' };
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
      <div className="mx-auto max-w-7xl">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Debug &amp; Trace</h1>
          <p className="mt-1 text-sm text-slate-500">Trace requests through the gateway pipeline</p>
        </div>

        {/* Trace Request Form */}
        <div className="mb-6 rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
          <h3 className="mb-4 text-base font-semibold text-slate-900">Trace Request</h3>

          <div className="mb-4 flex items-end gap-3">
            <div className="w-36">
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Method</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={method}
                onChange={(e) => setMethod(e.target.value)}
              >
                {METHODS.map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
            </div>
            <div className="flex-1">
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Path</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={path}
                onChange={(e) => setPath(e.target.value)}
                placeholder="/api/v1/resource"
              />
            </div>
          </div>

          <div className="mb-4">
            <div className="mb-2 flex items-center justify-between">
              <label className="text-sm font-medium text-slate-700">Headers</label>
              <button
                className="inline-flex items-center rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-200"
                onClick={addHeader}
              >
                + Add Header
              </button>
            </div>
            <div className="space-y-2">
              {headers.map((h, i) => (
                <div key={i} className="flex items-center gap-2">
                  <input
                    className="flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                    value={h.key}
                    onChange={(e) => updateHeader(i, 'key', e.target.value)}
                    placeholder="Header name"
                  />
                  <input
                    className="flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                    value={h.value}
                    onChange={(e) => updateHeader(i, 'value', e.target.value)}
                    placeholder="Header value"
                  />
                  <button
                    className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-red-50 text-red-500 transition-colors hover:bg-red-100"
                    onClick={() => removeHeader(i)}
                  >
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>
          </div>

          <div className="mb-5">
            <label className="mb-1.5 block text-sm font-medium text-slate-700">Body</label>
            <textarea
              className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 font-mono text-xs text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
              style={{ minHeight: 100 }}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder='{"key": "value"}'
            />
          </div>

          <button
            className="inline-flex items-center rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700 disabled:cursor-not-allowed disabled:opacity-50"
            onClick={handleTrace}
            disabled={tracing}
          >
            {tracing ? (
              <>
                <svg className="mr-2 h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                Tracing...
              </>
            ) : (
              'Execute Trace'
            )}
          </button>
        </div>

        {error && (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {/* Trace Results */}
        {result && (
          <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
            <h3 className="mb-2 text-base font-semibold text-slate-900">Pipeline Results</h3>
            <p className="mb-5 text-sm text-slate-500">
              <span className="mr-2 rounded bg-slate-100 px-2 py-0.5 font-mono text-xs font-medium text-slate-700">{result.method}</span>
              {result.requestPath}
              <span className="ml-2 text-slate-400">&mdash;</span>
              <span className="ml-2 font-medium text-slate-700">Total: {result.totalDurationMs}ms</span>
            </p>

            <div className="space-y-0">
              {result.steps.map((step, i) => {
                const styles = decisionStyles(step.decision);
                return (
                  <div key={i}>
                    <div
                      className={`flex items-center gap-4 rounded-lg border px-4 py-3 ${styles.bg} ${styles.border}`}
                    >
                      <span className="min-w-[180px] text-sm font-semibold text-slate-900">
                        {step.filterName}
                      </span>
                      <span
                        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium text-white ${styles.badge}`}
                      >
                        {step.decision}
                      </span>
                      <span className="text-sm text-slate-500">{step.durationMs}ms</span>
                      <span className="flex-1 text-sm text-slate-500">
                        {step.details}
                      </span>
                    </div>
                    {i < result.steps.length - 1 && (
                      <div className="ml-6 h-4 w-0.5 bg-slate-200" />
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
