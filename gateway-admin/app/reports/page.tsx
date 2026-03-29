'use client';

import React, { useEffect, useState, useCallback } from 'react';

const ANALYTICS_URL = process.env.NEXT_PUBLIC_ANALYTICS_URL || 'http://localhost:8083';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface ReportRow {
  [key: string]: unknown;
}

interface DrillDownRow {
  timestamp: string;
  method: string;
  path: string;
  status: number;
  latency: number;
  consumer: string;
  authType: string;
  clientIp: string;
  [key: string]: unknown;
}

interface ScheduleConfig {
  frequency: 'daily' | 'weekly' | 'monthly';
  recipients: string;
  enabled: boolean;
}

interface SavedReport {
  id: string;
  name: string;
  createdAt: string;
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

function putApi<T>(path: string, body: unknown): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('admin_token') || localStorage.getItem('token') || localStorage.getItem('jwt_token') || '' : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(`${ANALYTICS_URL}${path}`, {
    method: 'PUT',
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

const METRICS = ['request_count', 'avg_latency', 'error_rate', 'p99_latency'] as const;
const GROUP_BY_OPTIONS = ['api_id', 'day', 'week', 'month', 'status_code', 'auth_type'] as const;
const STATUS_CODES = ['200', '201', '400', '401', '403', '404', '429', '500', '502', '503'] as const;

const DRILL_DOWN_COLUMNS: { key: keyof DrillDownRow; label: string }[] = [
  { key: 'timestamp', label: 'Timestamp' },
  { key: 'method', label: 'Method' },
  { key: 'path', label: 'Path' },
  { key: 'status', label: 'Status' },
  { key: 'latency', label: 'Latency' },
  { key: 'consumer', label: 'Consumer' },
  { key: 'authType', label: 'Auth Type' },
  { key: 'clientIp', label: 'Client IP' },
];

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

export default function ReportsPage() {
  /* Metric selection */
  const [selectedMetrics, setSelectedMetrics] = useState<Set<string>>(new Set(['request_count']));

  /* Filters */
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [apiFilter, setApiFilter] = useState('');
  const [apiList, setApiList] = useState<{ id: string; name: string }[]>([]);
  const [consumerFilter, setConsumerFilter] = useState('');
  const [selectedStatuses, setSelectedStatuses] = useState<Set<string>>(new Set());

  /* Group By */
  const [groupBy, setGroupBy] = useState<Set<string>>(new Set(['day']));

  /* Results */
  const [results, setResults] = useState<ReportRow[]>([]);
  const [resultColumns, setResultColumns] = useState<string[]>([]);
  const [querying, setQuerying] = useState(false);
  const [queryError, setQueryError] = useState('');

  /* Drill-down state */
  const [expandedRow, setExpandedRow] = useState<number | null>(null);
  const [drillDownData, setDrillDownData] = useState<DrillDownRow[]>([]);
  const [drillDownLoading, setDrillDownLoading] = useState(false);
  const [drillDownError, setDrillDownError] = useState('');

  /* Saved reports */
  const [savedReports, setSavedReports] = useState<SavedReport[]>([
    { id: '1', name: 'Weekly Error Summary', createdAt: '2026-03-20T10:00:00Z' },
    { id: '2', name: 'Monthly Latency Report', createdAt: '2026-03-15T08:30:00Z' },
    { id: '3', name: 'Daily Request Counts', createdAt: '2026-03-10T14:00:00Z' },
  ]);

  /* Schedule config */
  const [schedule, setSchedule] = useState<ScheduleConfig>({
    frequency: 'weekly',
    recipients: '',
    enabled: false,
  });
  const [scheduleLoading, setScheduleLoading] = useState(false);
  const [scheduleSaved, setScheduleSaved] = useState(false);

  /* Toast */
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  /* Load schedule config */
  useEffect(() => {
    fetchApi<ScheduleConfig>('/v1/reports/config')
      .then((cfg) => setSchedule(cfg))
      .catch(() => { /* use defaults */ });
  }, []);

  /* Load real APIs from management-api */
  useEffect(() => {
    const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';
    const token = typeof window !== 'undefined'
      ? localStorage.getItem('admin_token') || localStorage.getItem('token') || '' : '';
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    fetch(`${API_URL}/v1/apis?size=200`, { headers })
      .then(r => r.ok ? r.json() : [])
      .then(data => {
        const list = Array.isArray(data) ? data : data.content || [];
        setApiList(list.map((a: { id: string; name: string }) => ({ id: a.id, name: a.name })));
      })
      .catch(() => setApiList([]));
  }, []);

  /* Toggle helpers */
  const toggleMetric = (m: string) => {
    setSelectedMetrics((prev) => {
      const next = new Set(prev);
      if (next.has(m)) next.delete(m);
      else next.add(m);
      return next;
    });
  };

  const toggleGroupBy = (g: string) => {
    setGroupBy((prev) => {
      const next = new Set(prev);
      if (next.has(g)) next.delete(g);
      else next.add(g);
      return next;
    });
  };

  const toggleStatus = (s: string) => {
    setSelectedStatuses((prev) => {
      const next = new Set(prev);
      if (next.has(s)) next.delete(s);
      else next.add(s);
      return next;
    });
  };

  /* Preview query */
  const handlePreview = useCallback(async () => {
    setQuerying(true);
    setQueryError('');
    setExpandedRow(null);
    setDrillDownData([]);
    try {
      const payload = {
        metrics: Array.from(selectedMetrics),
        filters: {
          dateFrom: dateFrom || undefined,
          dateTo: dateTo || undefined,
          apiId: apiFilter || undefined,
          consumerId: consumerFilter || undefined,
          statusCodes: selectedStatuses.size > 0 ? Array.from(selectedStatuses).map(Number) : undefined,
        },
        groupBy: Array.from(groupBy),
      };
      const data = await postApi<ReportRow[]>('/v1/analytics/report/query', payload);
      const rows = Array.isArray(data) ? data : [];
      setResults(rows);
      if (rows.length > 0) {
        setResultColumns(Object.keys(rows[0]));
      } else {
        setResultColumns([]);
      }
    } catch {
      setQueryError('Failed to run report query');
    } finally {
      setQuerying(false);
    }
  }, [selectedMetrics, dateFrom, dateTo, apiFilter, consumerFilter, selectedStatuses, groupBy]);

  /* Drill-down: fetch raw logs for a specific row */
  const handleRowClick = useCallback(async (rowIndex: number) => {
    if (expandedRow === rowIndex) {
      setExpandedRow(null);
      setDrillDownData([]);
      setDrillDownError('');
      return;
    }

    setExpandedRow(rowIndex);
    setDrillDownLoading(true);
    setDrillDownError('');
    setDrillDownData([]);

    try {
      const row = results[rowIndex];
      const filters: Record<string, unknown> = {
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        consumerId: consumerFilter || undefined,
      };

      // Add row-specific filters based on groupBy columns
      if (row.api_id) filters.apiId = row.api_id;
      else if (apiFilter) filters.apiId = apiFilter;

      if (row.status_code) filters.statusCodes = [Number(row.status_code)];
      else if (selectedStatuses.size > 0) filters.statusCodes = Array.from(selectedStatuses).map(Number);

      if (row.auth_type) filters.authType = row.auth_type;

      // If grouped by day/week/month, narrow the date range
      if (row.day && typeof row.day === 'string') {
        filters.dateFrom = row.day;
        const nextDay = new Date(row.day);
        nextDay.setDate(nextDay.getDate() + 1);
        filters.dateTo = nextDay.toISOString().split('T')[0];
      }

      const payload = {
        metrics: Array.from(selectedMetrics),
        filters,
        // No groupBy -- raw logs
      };

      const data = await postApi<DrillDownRow[]>('/v1/analytics/report/query', payload);
      setDrillDownData(Array.isArray(data) ? data : []);
    } catch {
      setDrillDownError('Failed to load request details');
    } finally {
      setDrillDownLoading(false);
    }
  }, [expandedRow, results, dateFrom, dateTo, apiFilter, consumerFilter, selectedMetrics, selectedStatuses]);

  /* Export CSV */
  const handleExport = () => {
    const token = typeof window !== 'undefined' ? localStorage.getItem('admin_token') || localStorage.getItem('token') || localStorage.getItem('jwt_token') || '' : '';
    const params = new URLSearchParams();
    if (token) params.set('token', token);
    selectedMetrics.forEach((m) => params.append('metrics', m));
    if (dateFrom) params.set('dateFrom', dateFrom);
    if (dateTo) params.set('dateTo', dateTo);
    if (apiFilter) params.set('apiId', apiFilter);
    if (consumerFilter) params.set('consumerId', consumerFilter);
    groupBy.forEach((g) => params.append('groupBy', g));
    window.open(`${ANALYTICS_URL}/v1/analytics/report/export?${params.toString()}`, '_blank');
  };

  /* Save schedule */
  const handleSaveSchedule = useCallback(async () => {
    setScheduleLoading(true);
    setScheduleSaved(false);
    try {
      await putApi('/v1/reports/config', schedule);
      setScheduleSaved(true);
      showToast('Schedule configuration saved');
      setTimeout(() => setScheduleSaved(false), 3000);
    } catch {
      showToast('Failed to save schedule', 'error');
    } finally {
      setScheduleLoading(false);
    }
  }, [schedule]);

  /* ---- Shared input classes ---- */
  const inputCls =
    'w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm transition-colors placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20';
  const labelCls = 'mb-1.5 block text-sm font-medium text-slate-700';
  const checkboxCls = 'h-4 w-4 rounded border-slate-300 text-purple-600 focus:ring-purple-500/20';

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

      <div className="mx-auto max-w-7xl space-y-8">
        {/* ---- Header ---- */}
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Report Builder</h1>
          <p className="mt-1 text-sm text-slate-500">
            Build custom analytics reports with flexible metrics, filters, and grouping
          </p>
        </div>

        {/* ---- Metric Selection ---- */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-base font-semibold text-slate-900">Metrics</h2>
          <div className="flex flex-wrap gap-5">
            {METRICS.map((m) => (
              <label key={m} className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  className={checkboxCls}
                  checked={selectedMetrics.has(m)}
                  onChange={() => toggleMetric(m)}
                />
                <span className="font-mono text-xs bg-slate-100 rounded px-2 py-0.5">{m}</span>
              </label>
            ))}
          </div>
        </div>

        {/* ---- Filters ---- */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-base font-semibold text-slate-900">Filters</h2>
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <label className={labelCls}>Date From</label>
              <input
                type="date"
                className={inputCls}
                value={dateFrom}
                onChange={(e) => setDateFrom(e.target.value)}
              />
            </div>
            <div>
              <label className={labelCls}>Date To</label>
              <input
                type="date"
                className={inputCls}
                value={dateTo}
                onChange={(e) => setDateTo(e.target.value)}
              />
            </div>
            <div>
              <label className={labelCls}>API</label>
              <select
                className={inputCls}
                value={apiFilter}
                onChange={(e) => setApiFilter(e.target.value)}
              >
                <option value="">All APIs</option>
                {apiList.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            </div>
            <div>
              <label className={labelCls}>Consumer</label>
              <input
                className={inputCls}
                placeholder="Consumer ID or name"
                value={consumerFilter}
                onChange={(e) => setConsumerFilter(e.target.value)}
              />
            </div>
          </div>

          {/* Status code multi-select */}
          <div className="mt-5">
            <label className={labelCls}>Status Codes</label>
            <div className="flex flex-wrap gap-2">
              {STATUS_CODES.map((code) => (
                <button
                  key={code}
                  onClick={() => toggleStatus(code)}
                  className={`rounded-full px-3 py-1 text-xs font-semibold transition-colors ${
                    selectedStatuses.has(code)
                      ? 'bg-purple-600 text-white shadow-sm'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {code}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* ---- Group By ---- */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-base font-semibold text-slate-900">Group By</h2>
          <div className="flex flex-wrap gap-5">
            {GROUP_BY_OPTIONS.map((g) => (
              <label key={g} className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  className={checkboxCls}
                  checked={groupBy.has(g)}
                  onChange={() => toggleGroupBy(g)}
                />
                <span className="font-mono text-xs bg-slate-100 rounded px-2 py-0.5">{g}</span>
              </label>
            ))}
          </div>
        </div>

        {/* ---- Action buttons ---- */}
        <div className="flex flex-wrap gap-3">
          <button
            onClick={handlePreview}
            disabled={querying || selectedMetrics.size === 0}
            className="inline-flex items-center gap-2 rounded-xl bg-purple-600 px-6 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {querying && (
              <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
            )}
            Preview Report
          </button>
          <button
            onClick={handleExport}
            disabled={selectedMetrics.size === 0}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-6 py-2.5 text-sm font-medium text-slate-700 shadow-sm transition-colors hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 10l5 5m0 0l5-5m-5 5V3" />
            </svg>
            Export CSV
          </button>
        </div>

        {/* ---- Query Error ---- */}
        {queryError && (
          <div className="flex items-center gap-3 rounded-xl border border-red-200 bg-red-50 px-5 py-4">
            <svg className="h-5 w-5 shrink-0 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
            </svg>
            <p className="text-sm font-medium text-red-700">{queryError}</p>
          </div>
        )}

        {/* ---- Results Table with Drill-Down ---- */}
        {results.length > 0 && (
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-100 px-6 py-4">
              <h3 className="text-base font-semibold text-slate-900">
                Results ({results.length} rows)
              </h3>
              <p className="text-xs text-slate-400 mt-1">Click a row to drill down into individual request logs</p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-100 bg-slate-50/60">
                  <tr>
                    {resultColumns.map((col) => (
                      <th key={col} className="px-6 py-3 font-semibold text-slate-600 whitespace-nowrap">
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {results.map((row, i) => (
                    <React.Fragment key={i}>
                      <tr
                        onClick={() => handleRowClick(i)}
                        className={`cursor-pointer transition-colors ${
                          expandedRow === i
                            ? 'bg-purple-50 border-l-4 border-l-purple-500'
                            : 'hover:bg-slate-50/50'
                        }`}
                      >
                        {resultColumns.map((col) => (
                          <td key={col} className="px-6 py-3 text-slate-700 whitespace-nowrap">
                            {String(row[col] ?? '')}
                          </td>
                        ))}
                      </tr>
                      {/* Drill-down expanded section */}
                      {expandedRow === i && (
                        <tr>
                          <td colSpan={resultColumns.length} className="p-0">
                            <div className="bg-purple-50/50 border-t border-purple-100 px-6 py-4">
                              <div className="flex items-center justify-between mb-3">
                                <h4 className="text-sm font-semibold text-purple-900">
                                  Request Details
                                </h4>
                                <button
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setExpandedRow(null);
                                    setDrillDownData([]);
                                    setDrillDownError('');
                                  }}
                                  className="inline-flex items-center gap-1 rounded-lg border border-purple-200 bg-white px-3 py-1.5 text-xs font-medium text-purple-700 hover:bg-purple-50 transition-colors"
                                >
                                  <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                                  </svg>
                                  Close
                                </button>
                              </div>

                              {drillDownLoading && (
                                <div className="flex items-center justify-center gap-2 py-8">
                                  <Spinner />
                                  <span className="text-sm text-purple-600">Loading request logs...</span>
                                </div>
                              )}

                              {drillDownError && (
                                <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
                                  <svg className="h-4 w-4 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
                                  </svg>
                                  <p className="text-sm text-red-700">{drillDownError}</p>
                                </div>
                              )}

                              {!drillDownLoading && !drillDownError && drillDownData.length === 0 && (
                                <div className="flex flex-col items-center justify-center gap-1 py-8 text-center">
                                  <svg className="h-8 w-8 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M20 13V7a2 2 0 00-2-2H6a2 2 0 00-2 2v6m16 0v6a2 2 0 01-2 2H6a2 2 0 01-2-2v-6m16 0H4" />
                                  </svg>
                                  <p className="text-sm text-slate-500">No matching request logs found</p>
                                </div>
                              )}

                              {!drillDownLoading && drillDownData.length > 0 && (
                                <div className="overflow-x-auto rounded-lg border border-purple-100 bg-white">
                                  <table className="w-full text-left text-xs">
                                    <thead className="border-b border-purple-100 bg-purple-50/80">
                                      <tr>
                                        {DRILL_DOWN_COLUMNS.map((col) => (
                                          <th key={col.key} className="px-4 py-2.5 font-semibold text-purple-700 whitespace-nowrap">
                                            {col.label}
                                          </th>
                                        ))}
                                      </tr>
                                    </thead>
                                    <tbody className="divide-y divide-slate-100">
                                      {drillDownData.map((detail, j) => (
                                        <tr key={j} className="hover:bg-slate-50/50 transition-colors">
                                          {DRILL_DOWN_COLUMNS.map((col) => (
                                            <td key={col.key} className="px-4 py-2 text-slate-700 whitespace-nowrap">
                                              {col.key === 'timestamp'
                                                ? new Date(String(detail[col.key])).toLocaleString()
                                                : col.key === 'latency'
                                                  ? `${detail[col.key]}ms`
                                                  : col.key === 'status'
                                                    ? (
                                                      <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${
                                                        Number(detail[col.key]) >= 400
                                                          ? 'bg-red-100 text-red-700'
                                                          : 'bg-emerald-100 text-emerald-700'
                                                      }`}>
                                                        {String(detail[col.key] ?? '')}
                                                      </span>
                                                    )
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
            </div>
          </div>
        )}

        {/* ---- Saved Reports (placeholder) ---- */}
        <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-100 px-6 py-4">
            <h3 className="text-base font-semibold text-slate-900">Saved Reports</h3>
          </div>
          <div className="divide-y divide-slate-100">
            {savedReports.map((report) => (
              <div key={report.id} className="flex items-center justify-between px-6 py-4 hover:bg-slate-50/50 transition-colors">
                <div>
                  <p className="text-sm font-medium text-slate-900">{report.name}</p>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Created {new Date(report.createdAt).toLocaleDateString()}
                  </p>
                </div>
                <div className="flex gap-2">
                  <button className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 transition-colors">
                    Load
                  </button>
                  <button className="rounded-lg border border-red-200 bg-white px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 transition-colors">
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* ---- Scheduled Reports ---- */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-base font-semibold text-slate-900">Scheduled Reports</h2>
          <p className="mb-5 text-sm text-slate-500">
            Configure automated report delivery to email recipients
          </p>
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
            <div>
              <label className={labelCls}>Frequency</label>
              <select
                className={inputCls}
                value={schedule.frequency}
                onChange={(e) =>
                  setSchedule((s) => ({ ...s, frequency: e.target.value as ScheduleConfig['frequency'] }))
                }
              >
                <option value="daily">Daily</option>
                <option value="weekly">Weekly</option>
                <option value="monthly">Monthly</option>
              </select>
            </div>
            <div>
              <label className={labelCls}>Recipients (comma-separated)</label>
              <input
                className={inputCls}
                placeholder="team@company.com, admin@company.com"
                value={schedule.recipients}
                onChange={(e) => setSchedule((s) => ({ ...s, recipients: e.target.value }))}
              />
            </div>
            <div>
              <label className={labelCls}>Enabled</label>
              <div className="flex items-center gap-3 pt-1.5">
                <button
                  onClick={() => setSchedule((s) => ({ ...s, enabled: !s.enabled }))}
                  className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                    schedule.enabled ? 'bg-purple-600' : 'bg-slate-300'
                  }`}
                >
                  <span
                    className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform shadow-sm ${
                      schedule.enabled ? 'translate-x-6' : 'translate-x-1'
                    }`}
                  />
                </button>
                <span className="text-sm text-slate-600">{schedule.enabled ? 'Active' : 'Inactive'}</span>
              </div>
            </div>
          </div>
          <div className="mt-5 flex items-center gap-3">
            <button
              onClick={handleSaveSchedule}
              disabled={scheduleLoading}
              className="inline-flex items-center gap-2 rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700 disabled:opacity-50"
            >
              {scheduleLoading && (
                <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              )}
              Save Schedule
            </button>
            {scheduleSaved && (
              <span className="text-sm text-emerald-600 font-medium">Saved</span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

