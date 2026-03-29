'use client';

import React, { useEffect, useState, useMemo } from 'react';
import { DataTable } from '@gateway/shared-ui';
import type { Column, Pagination } from '@gateway/shared-ui';

const ANALYTICS_URL = process.env.NEXT_PUBLIC_ANALYTICS_URL || 'http://localhost:8083';

function getToken(): string {
  if (typeof window !== 'undefined') {
    return localStorage.getItem('admin_token') || '';
  }
  return '';
}

function auditAuthHeaders(): Record<string, string> {
  const token = getToken();
  return token
    ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
    : { 'Content-Type': 'application/json' };
}

async function auditGet<T>(path: string): Promise<T> {
  const res = await fetch(`${ANALYTICS_URL}${path}`, { headers: auditAuthHeaders() });
  if (!res.ok) throw new Error(`Request failed: ${res.status}`);
  return res.json();
}

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface AuditEvent {
  id: string;
  timestamp: string;
  actor: string;
  action: string;
  resourceType: string;
  resourceId: string;
  result: string;
  details?: string;
  [key: string]: unknown;
}

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

const ACTIONS = [
  'ALL',
  'CREATE',
  'UPDATE',
  'DELETE',
  'PUBLISH',
  'DEPRECATE',
  'RETIRE',
  'APPROVE',
  'REJECT',
  'LOGIN',
  'LOGOUT',
] as const;

/* ------------------------------------------------------------------ */
/*  Skeleton rows for loading state                                    */
/* ------------------------------------------------------------------ */

function SkeletonRows() {
  return (
    <div className="animate-pulse space-y-3 p-6">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="flex items-center gap-4">
          <div className="h-4 w-40 rounded bg-gray-200" />
          <div className="h-4 w-24 rounded bg-gray-200" />
          <div className="h-4 w-20 rounded bg-gray-100" />
          <div className="h-4 w-28 rounded bg-gray-200" />
          <div className="h-4 w-32 rounded bg-gray-100" />
          <div className="h-4 w-16 rounded bg-gray-200" />
        </div>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Empty state                                                        */
/* ------------------------------------------------------------------ */

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-gray-400">
      <svg
        className="h-16 w-16 mb-4 text-gray-300"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={1.2}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M9 12h6m-3-3v6m-7.5 3.75h15A2.25 2.25 0 0021.75 16.5V7.5a2.25 2.25 0 00-2.25-2.25h-15A2.25 2.25 0 002.25 7.5v9a2.25 2.25 0 002.25 2.25z"
        />
      </svg>
      <p className="text-sm font-medium text-gray-500">No audit events found</p>
      <p className="text-xs text-gray-400 mt-1">Try adjusting your filters or date range</p>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function AuditPage() {
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 25;

  /* Filters */
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [actor, setActor] = useState('');
  const [action, setAction] = useState<string>('ALL');
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  /* Fetch audit events */
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    const params = new URLSearchParams({
      page: String(page - 1),
      size: String(pageSize),
    });
    if (dateFrom) params.set('from', dateFrom);
    if (dateTo) params.set('to', dateTo);
    if (actor.trim()) params.set('actor', actor.trim());
    if (action !== 'ALL') params.set('action', action);

    auditGet<PageResponse<AuditEvent>>(`/v1/audit?${params}`)
      .then((res) => {
        if (cancelled) return;
        setEvents(res.content);
        setTotal(res.totalElements);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err.message ?? 'Failed to load audit events');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [page, dateFrom, dateTo, actor, action]);

  /* Columns */
  const columns: Column<AuditEvent>[] = useMemo(() => [
    {
      key: 'timestamp',
      label: 'Timestamp',
      sortable: true,
      render: (e) => (
        <span className="text-sm text-gray-600 tabular-nums">
          {new Date(e.timestamp).toLocaleString()}
        </span>
      ),
    },
    {
      key: 'actor',
      label: 'Actor',
      sortable: true,
      render: (e) => (
        <span className="text-sm font-medium text-gray-900">{e.actor}</span>
      ),
    },
    {
      key: 'action',
      label: 'Action',
      render: (e) => (
        <span className="inline-flex items-center rounded-md bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700 ring-1 ring-inset ring-indigo-600/20">
          {e.action}
        </span>
      ),
    },
    {
      key: 'resourceType',
      label: 'Resource Type',
      render: (e) => (
        <span className="text-sm text-gray-600">{e.resourceType}</span>
      ),
    },
    {
      key: 'resourceId',
      label: 'Resource ID',
      render: (e) => (
        <span className="text-sm font-mono text-gray-500 truncate max-w-[180px] inline-block" title={e.resourceId}>
          {e.resourceId}
        </span>
      ),
    },
    {
      key: 'result',
      label: 'Result',
      render: (e) => {
        const isSuccess = e.result === 'SUCCESS';
        const isFailure = e.result === 'FAILURE';
        return (
          <span
            className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${
              isSuccess
                ? 'bg-green-50 text-green-700 ring-green-600/20'
                : isFailure
                  ? 'bg-red-50 text-red-700 ring-red-600/20'
                  : 'bg-gray-50 text-gray-600 ring-gray-500/20'
            }`}
          >
            {isSuccess && (
              <svg className="h-3 w-3" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z" clipRule="evenodd" />
              </svg>
            )}
            {isFailure && (
              <svg className="h-3 w-3" viewBox="0 0 20 20" fill="currentColor">
                <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
              </svg>
            )}
            {e.result}
          </span>
        );
      },
    },
  ], []);

  const pagination: Pagination = {
    page,
    size: pageSize,
    total,
    onPageChange: setPage,
  };

  /* Export CSV */
  const handleExportCsv = async () => {
    try {
      const params = new URLSearchParams({ format: 'csv' });
      if (dateFrom) params.set('from', dateFrom);
      if (dateTo) params.set('to', dateTo);
      if (actor.trim()) params.set('actor', actor.trim());
      if (action !== 'ALL') params.set('action', action);

      const res = await fetch(`${ANALYTICS_URL}/v1/audit/export?${params}`, {
        headers: auditAuthHeaders(),
      });
      if (!res.ok) throw new Error('Export failed');
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `audit-export-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch {
      showToast('Failed to export CSV', 'error');
    }
  };

  const inputCls =
    'block w-full rounded-lg border border-gray-300 bg-gray-50 px-3 py-2 text-sm text-gray-900 shadow-sm placeholder:text-gray-400 focus:border-blue-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/30 transition-colors';

  return (
    <main className="min-h-screen bg-gray-50/50 px-4 py-8 sm:px-6 lg:px-8">
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
      <div className="mx-auto max-w-7xl space-y-6">

        {/* ---- Header ---- */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">
              Audit Log
            </h1>
            <p className="mt-1 text-sm text-gray-500">
              Track and review all system activity, user actions, and security events.
            </p>
          </div>
          <button
            onClick={handleExportCsv}
            className="inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-semibold text-gray-700 shadow-sm transition-all hover:bg-gray-50 hover:shadow active:scale-[0.98] focus:outline-none focus:ring-2 focus:ring-blue-500/30"
          >
            <svg className="h-4 w-4 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" />
            </svg>
            Export CSV
          </button>
        </div>

        {/* ---- Filter toolbar ---- */}
        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-gray-400">
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 3c2.755 0 5.455.232 8.083.678.533.09.917.556.917 1.096v1.044a2.25 2.25 0 01-.659 1.591l-5.432 5.432a2.25 2.25 0 00-.659 1.591v2.927a2.25 2.25 0 01-1.244 2.013L9.75 21v-6.568a2.25 2.25 0 00-.659-1.591L3.659 7.409A2.25 2.25 0 013 5.818V4.774c0-.54.384-1.006.917-1.096A48.32 48.32 0 0112 3z" />
            </svg>
            Filters
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-gray-600">
                From
              </label>
              <input
                type="date"
                value={dateFrom}
                onChange={(e) => { setDateFrom(e.target.value); setPage(1); }}
                className={inputCls}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-medium text-gray-600">
                To
              </label>
              <input
                type="date"
                value={dateTo}
                onChange={(e) => { setDateTo(e.target.value); setPage(1); }}
                className={inputCls}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-medium text-gray-600">
                Actor
              </label>
              <input
                type="text"
                placeholder="username or ID"
                value={actor}
                onChange={(e) => { setActor(e.target.value); setPage(1); }}
                className={inputCls}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-medium text-gray-600">
                Action
              </label>
              <select
                value={action}
                onChange={(e) => { setAction(e.target.value); setPage(1); }}
                className={inputCls}
              >
                {ACTIONS.map((a) => (
                  <option key={a} value={a}>{a === 'ALL' ? 'All Actions' : a}</option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {/* ---- Error banner ---- */}
        {error && (
          <div className="flex items-center gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 shadow-sm">
            <svg className="h-5 w-5 flex-shrink-0 text-red-400" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clipRule="evenodd" />
            </svg>
            {error}
          </div>
        )}

        {/* ---- Table card ---- */}
        <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
          {loading ? (
            <SkeletonRows />
          ) : !error && events.length === 0 ? (
            <EmptyState />
          ) : (
            <DataTable data={events} columns={columns} pagination={pagination} loading={loading} />
          )}
        </div>

      </div>
    </main>
  );
}
