'use client';

import { useEffect, useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { DataTable, get, post } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

interface ScoreEntry {
  apiId: string;
  apiName: string;
  score: number;
  status: string;
  [key: string]: unknown;
}

interface GovernanceOverview {
  averageScore: number;
  apisBelowThreshold: number;
  topViolations: { rule: string; count: number }[];
}

function scoreColor(score: number): string {
  if (score >= 80) return 'text-emerald-600';
  if (score >= 60) return 'text-amber-600';
  return 'text-red-600';
}

function scoreBarColor(score: number): string {
  if (score >= 80) return 'bg-emerald-500';
  if (score >= 60) return 'bg-amber-500';
  return 'bg-red-500';
}

function scoreBadgeClasses(score: number): string {
  if (score >= 80) return 'bg-emerald-100 text-emerald-700';
  if (score >= 60) return 'bg-amber-100 text-amber-700';
  return 'bg-red-100 text-red-700';
}

export default function GovernancePage() {
  const router = useRouter();
  const [scores, setScores] = useState<ScoreEntry[]>([]);
  const [overview, setOverview] = useState<GovernanceOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [linting, setLinting] = useState(false);
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
        const data = await get<ScoreEntry[]>('/v1/governance/scores');
        const list = Array.isArray(data) ? data : [];
        setScores(list);

        const total = list.length;
        const avg = total > 0 ? Math.round(list.reduce((s, e) => s + e.score, 0) / total) : 0;
        const below = list.filter((e) => e.score < 60).length;
        setOverview({ averageScore: avg, apisBelowThreshold: below, topViolations: [] });
      } catch {
        setError('Failed to load governance scores');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const handleLintAll = async () => {
    setLinting(true);
    try {
      await post('/v1/governance/lint', {});
      window.location.reload();
    } catch {
      showToast('Failed to lint all APIs', 'error');
    } finally {
      setLinting(false);
    }
  };

  const columns: Column<ScoreEntry>[] = useMemo(
    () => [
      { key: 'apiName', label: 'API Name' },
      {
        key: 'score',
        label: 'Score',
        render: (row) => (
          <div className="flex items-center gap-3">
            <div className="h-2 w-24 overflow-hidden rounded-full bg-slate-200">
              <div
                className={`h-full rounded-full transition-all ${scoreBarColor(row.score)}`}
                style={{ width: `${row.score}%` }}
              />
            </div>
            <span className={`text-sm font-semibold ${scoreColor(row.score)}`}>
              {row.score}
            </span>
          </div>
        ),
      },
      {
        key: 'status',
        label: 'Status',
        render: (row) => (
          <span
            className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${scoreBadgeClasses(row.score)}`}
          >
            {row.score >= 80 ? 'GOOD' : row.score >= 60 ? 'WARNING' : 'CRITICAL'}
          </span>
        ),
      },
    ],
    [],
  );

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
        <div className="mx-auto max-w-7xl">
          <div className="mb-8">
            <div className="h-8 w-48 animate-pulse rounded-lg bg-slate-200" />
            <div className="mt-2 h-4 w-72 animate-pulse rounded-lg bg-slate-200" />
          </div>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-28 animate-pulse rounded-xl bg-white shadow-sm" />
            ))}
          </div>
          <div className="mt-6 h-64 animate-pulse rounded-xl bg-white shadow-sm" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
      {toast && (<div className={`fixed top-4 right-4 z-50 flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg border max-w-sm ${toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-emerald-50 border-emerald-200 text-emerald-800'}`}>{toast.type === 'error' ? (<svg className="w-5 h-5 shrink-0 text-red-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>) : (<svg className="w-5 h-5 shrink-0 text-emerald-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>)}<p className="text-sm font-medium flex-1">{toast.message}</p><button onClick={() => setToast(null)} className="shrink-0 opacity-50 hover:opacity-100"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg></button></div>)}
      <div className="mx-auto max-w-7xl">
        {/* Header */}
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">API Governance</h1>
            <p className="mt-1 text-sm text-slate-500">Platform-wide API quality overview</p>
          </div>
          <button
            className="inline-flex items-center rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700 disabled:cursor-not-allowed disabled:opacity-50"
            onClick={handleLintAll}
            disabled={linting}
          >
            {linting ? (
              <>
                <svg className="mr-2 h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                Linting...
              </>
            ) : (
              'Lint All APIs'
            )}
          </button>
        </div>

        {error && (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {overview && (
          <div className="mb-8 grid grid-cols-1 gap-6 sm:grid-cols-3">
            <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">Avg Score</p>
                  <p className={`mt-2 text-2xl font-bold ${scoreColor(overview.averageScore)}`}>
                    {overview.averageScore}
                  </p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-xs font-bold text-blue-600">
                  AVG
                </div>
              </div>
            </div>
            <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">APIs Below Threshold</p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">{overview.apisBelowThreshold}</p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-red-50 text-xs font-bold text-red-600">
                  LOW
                </div>
              </div>
            </div>
            <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">Total APIs Scored</p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">{scores.length}</p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-50 text-xs font-bold text-emerald-600">
                  ALL
                </div>
              </div>
            </div>
          </div>
        )}

        <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          <div className="border-b border-slate-200 px-6 py-4">
            <h3 className="text-base font-semibold text-slate-900">API Scores</h3>
          </div>
          <div
            className="cursor-pointer"
            onClick={(e) => {
              const row = (e.target as HTMLElement).closest('tr');
              if (!row || row.parentElement?.tagName === 'THEAD') return;
              const idx = Array.from(row.parentElement?.children ?? []).indexOf(row);
              if (idx >= 0 && scores[idx]) {
                router.push(`/governance/${scores[idx].apiId}`);
              }
            }}
          >
            <DataTable data={scores} columns={columns} loading={loading} />
          </div>
        </div>
      </div>
    </div>
  );
}
