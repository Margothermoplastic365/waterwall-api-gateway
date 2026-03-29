'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface DrillResult {
  id: string;
  scenario: string;
  result: string;
  duration: string;
  findings: string[];
  executedAt: string;
  [key: string]: unknown;
}

const SCENARIOS = ['DB_FAILOVER', 'NODE_DOWN', 'CACHE_FAILURE', 'FULL_OUTAGE'] as const;

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

export default function DrDrillsPage() {
  const [drills, setDrills] = useState<DrillResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [scenario, setScenario] = useState<string>(SCENARIOS[0]);
  const [running, setRunning] = useState(false);
  const [latestResult, setLatestResult] = useState<DrillResult | null>(null);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  const fetchDrills = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await fetch(`${API_URL}/v1/dr/drills`, { headers: authHeaders() });
      if (res.ok) {
        const data = await res.json();
        setDrills(Array.isArray(data) ? data : data.content || data.data || []);
      }
    } catch {
      setError('Failed to load drill history');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDrills();
  }, [fetchDrills]);

  const handleRunDrill = useCallback(async () => {
    setRunning(true);
    setLatestResult(null);
    try {
      const res = await fetch(`${API_URL}/v1/dr/drill`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ scenario }),
      });
      if (!res.ok) throw new Error('Drill failed');
      const result = await res.json();
      setLatestResult(result);
      setDrills((prev) => [result, ...prev]);
    } catch {
      showToast('Failed to run DR drill', 'error');
    } finally {
      setRunning(false);
    }
  }, [scenario]);

  const columns: Column<DrillResult>[] = useMemo(
    () => [
      {
        key: 'scenario',
        label: 'Scenario',
        render: (row) => <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-700">{row.scenario}</span>,
      },
      {
        key: 'result',
        label: 'Result',
        render: (row) => (
          <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${row.result === 'PASSED' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
            {row.result}
          </span>
        ),
      },
      { key: 'duration', label: 'Duration' },
      {
        key: 'executedAt',
        label: 'Date',
        render: (row) => <span className="text-sm text-slate-500">{new Date(row.executedAt).toLocaleString()}</span>,
      },
    ],
    [],
  );

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
        <div className="max-w-7xl mx-auto">
          <div className="space-y-6">
            {/* Skeleton header */}
            <div className="space-y-2">
              <div className="h-7 w-40 bg-slate-200 rounded-lg animate-pulse" />
              <div className="h-4 w-72 bg-slate-100 rounded-lg animate-pulse" />
            </div>
            {/* Skeleton card */}
            <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-4">
              <div className="h-5 w-32 bg-slate-200 rounded animate-pulse" />
              <div className="flex gap-4">
                <div className="h-10 flex-1 bg-slate-100 rounded-lg animate-pulse" />
                <div className="h-10 w-28 bg-slate-200 rounded-lg animate-pulse" />
              </div>
            </div>
            {/* Skeleton table */}
            <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3">
              {[...Array(4)].map((_, i) => (
                <div key={i} className="h-10 bg-slate-50 rounded-lg animate-pulse" />
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
      {toast && (<div className={`fixed top-4 right-4 z-50 flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg border max-w-sm ${toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-emerald-50 border-emerald-200 text-emerald-800'}`}>{toast.type === 'error' ? (<svg className="w-5 h-5 shrink-0 text-red-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>) : (<svg className="w-5 h-5 shrink-0 text-emerald-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>)}<p className="text-sm font-medium flex-1">{toast.message}</p><button onClick={() => setToast(null)} className="shrink-0 opacity-50 hover:opacity-100"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg></button></div>)}
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">DR Drills</h1>
          <p className="mt-1 text-sm text-slate-500">Disaster recovery drill execution and history</p>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        {/* Run Drill */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-base font-semibold text-slate-900 mb-4">Run Drill</h3>
          <div className="flex flex-wrap gap-4 items-end">
            <div className="flex-1 min-w-[200px] space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Scenario</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={scenario}
                onChange={(e) => setScenario(e.target.value)}
              >
                {SCENARIOS.map((s) => (
                  <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
                ))}
              </select>
            </div>
            <button
              className="inline-flex items-center px-5 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200 disabled:opacity-50"
              onClick={handleRunDrill}
              disabled={running}
            >
              {running ? (
                <>
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2" />
                  Running...
                </>
              ) : (
                'Run Drill'
              )}
            </button>
          </div>
        </div>

        {/* Latest Result */}
        {latestResult && (
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <h3 className="text-base font-semibold text-slate-900 mb-4">Latest Result</h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
              <div className="bg-slate-50 rounded-xl p-4 border border-slate-100">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Scenario</p>
                    <p className="mt-1 text-lg font-bold text-slate-900">
                      {latestResult.scenario.replace(/_/g, ' ')}
                    </p>
                  </div>
                  <div className="w-10 h-10 rounded-xl bg-blue-100 text-blue-600 flex items-center justify-center text-xs font-bold">
                    DR
                  </div>
                </div>
              </div>
              <div className="bg-slate-50 rounded-xl p-4 border border-slate-100">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Result</p>
                    <p className={`mt-1 text-lg font-bold ${latestResult.result === 'PASSED' ? 'text-green-600' : 'text-red-600'}`}>
                      {latestResult.result}
                    </p>
                  </div>
                  <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-xs font-bold ${latestResult.result === 'PASSED' ? 'bg-green-100 text-green-600' : 'bg-red-100 text-red-600'}`}>
                    {latestResult.result === 'PASSED' ? 'OK' : 'ERR'}
                  </div>
                </div>
              </div>
              <div className="bg-slate-50 rounded-xl p-4 border border-slate-100">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Duration</p>
                    <p className="mt-1 text-lg font-bold text-slate-900">
                      {latestResult.duration}
                    </p>
                  </div>
                  <div className="w-10 h-10 rounded-xl bg-amber-100 text-amber-600 flex items-center justify-center text-xs font-bold">
                    DUR
                  </div>
                </div>
              </div>
            </div>

            {latestResult.findings && latestResult.findings.length > 0 && (
              <>
                <h4 className="text-sm font-semibold text-slate-900 mb-3">Findings</h4>
                <ul className="space-y-2">
                  {latestResult.findings.map((f, i) => (
                    <li key={i} className="flex items-start gap-2 text-sm text-slate-700">
                      <span className="mt-1.5 w-1.5 h-1.5 rounded-full bg-amber-400 flex-shrink-0" />
                      {f}
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}

        {/* Past Drills */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-6 pt-5 pb-3">
            <h3 className="text-base font-semibold text-slate-900">Drill History</h3>
            <p className="text-sm text-slate-500 mt-1">Past disaster recovery drill executions</p>
          </div>
          <DataTable data={drills} columns={columns} />
        </div>
      </div>
    </div>
  );
}
