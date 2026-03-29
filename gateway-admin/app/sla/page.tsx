'use client';

import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { DataTable } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const ANALYTICS_URL = process.env.NEXT_PUBLIC_ANALYTICS_URL || 'http://localhost:8083';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface SlaDashboardItem {
  apiId: string;
  apiName: string;
  uptimeTarget: number;
  uptimeActual: number;
  uptimeCompliant: boolean;
  latencyTargetMs: number;
  latencyP95Actual: number;
  latencyCompliant: boolean;
  errorBudgetPct: number;
  errorRateActual: number;
  errorRateCompliant: boolean;
  overallCompliant: boolean;
  activeBreaches: number;
}

interface SlaConfig {
  id: string;
  apiId: string;
  apiName: string;
  uptimeTarget: number;
  latencyTargetMs: number;
  errorBudgetPct: number;
  enabled: boolean;
  [key: string]: unknown;
}

interface SlaBreach {
  id: string;
  apiId?: string;
  apiName: string;
  slaConfigId: string;
  metric: string;
  targetValue: number;
  actualValue: number;
  message: string;
  breachedAt: string;
  resolvedAt: string | null;
  [key: string]: unknown;
}

interface BreachDrillDownRow {
  timestamp: string;
  method: string;
  path: string;
  status: number;
  latency: number;
  [key: string]: unknown;
}

/* ------------------------------------------------------------------ */
/*  Fetch helpers                                                      */
/* ------------------------------------------------------------------ */

function getAuthHeaders(): Record<string, string> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('admin_token') || localStorage.getItem('token') || localStorage.getItem('jwt_token') || '' : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return headers;
}

async function handleResponse<T>(r: Response): Promise<T> {
  if (!r.ok) {
    let msg = `Request failed (${r.status})`;
    try {
      const body = await r.json();
      msg = body.message || body.error || msg;
    } catch { /* ignore parse error */ }
    throw new Error(msg);
  }
  if (r.status === 204) return undefined as T;
  return r.json();
}

function fetchApi<T>(path: string): Promise<T> {
  return fetch(`${ANALYTICS_URL}${path}`, { headers: getAuthHeaders() }).then((r) => handleResponse<T>(r));
}

function postApi<T>(path: string, body: unknown): Promise<T> {
  return fetch(`${ANALYTICS_URL}${path}`, {
    method: 'POST', headers: getAuthHeaders(), body: JSON.stringify(body),
  }).then((r) => handleResponse<T>(r));
}

function putApi<T>(path: string, body: unknown): Promise<T> {
  return fetch(`${ANALYTICS_URL}${path}`, {
    method: 'PUT', headers: getAuthHeaders(), body: JSON.stringify(body),
  }).then((r) => handleResponse<T>(r));
}

function deleteApi(path: string): Promise<void> {
  return fetch(`${ANALYTICS_URL}${path}`, {
    method: 'DELETE', headers: getAuthHeaders(),
  }).then((r) => handleResponse<void>(r));
}

/* ------------------------------------------------------------------ */
/*  Constants                                                          */
/* ------------------------------------------------------------------ */

const BREACH_DRILL_DOWN_COLUMNS: { key: keyof BreachDrillDownRow; label: string }[] = [
  { key: 'timestamp', label: 'Timestamp' },
  { key: 'method', label: 'Method' },
  { key: 'path', label: 'Path' },
  { key: 'status', label: 'Status' },
  { key: 'latency', label: 'Latency' },
];

/* ------------------------------------------------------------------ */
/*  Compliance ring                                                    */
/* ------------------------------------------------------------------ */

function ComplianceRing({ value, size = 60 }: { value: number; size?: number }) {
  const radius = (size - 8) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (value / 100) * circumference;
  const color = value >= 99 ? '#10b981' : value >= 95 ? '#f59e0b' : '#ef4444';

  return (
    <svg width={size} height={size} className="shrink-0">
      <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="#e2e8f0" strokeWidth={4} />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={color}
        strokeWidth={4}
        strokeDasharray={circumference}
        strokeDashoffset={offset}
        strokeLinecap="round"
        transform={`rotate(-90 ${size / 2} ${size / 2})`}
        className="transition-all duration-500"
      />
      <text x="50%" y="50%" textAnchor="middle" dominantBaseline="central" className="fill-slate-900 text-[11px] font-bold">
        {value.toFixed(1)}%
      </text>
    </svg>
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

export default function SlaPage() {
  /* Dashboard */
  const [dashboard, setDashboard] = useState<SlaDashboardItem[]>([]);
  const [dashboardLoading, setDashboardLoading] = useState(true);

  /* Configs */
  const [configs, setConfigs] = useState<SlaConfig[]>([]);
  const [configsLoading, setConfigsLoading] = useState(true);

  /* Breaches */
  const [breaches, setBreaches] = useState<SlaBreach[]>([]);
  const [breachesLoading, setBreachesLoading] = useState(true);

  /* Breach drill-down state */
  const [expandedBreachId, setExpandedBreachId] = useState<string | null>(null);
  const [breachDrillDownData, setBreachDrillDownData] = useState<BreachDrillDownRow[]>([]);
  const [breachDrillDownLoading, setBreachDrillDownLoading] = useState(false);
  const [breachDrillDownError, setBreachDrillDownError] = useState('');

  const [apiList, setApiList] = useState<{ id: string; name: string }[]>([]);

  /* Load real APIs */
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

  /* Modal */
  const [modalOpen, setModalOpen] = useState(false);
  const [editingConfig, setEditingConfig] = useState<SlaConfig | null>(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    apiId: '',
    uptimeTarget: 99.9,
    latencyTargetMs: 200,
    errorBudgetPct: 1.0,
  });

  /* Toast */
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  /* Load all data */
  useEffect(() => {
    fetchApi<SlaDashboardItem[]>('/v1/sla/dashboard')
      .then((data) => setDashboard(Array.isArray(data) ? data : []))
      .catch(() => setDashboard([]))
      .finally(() => setDashboardLoading(false));

    fetchApi<SlaConfig[]>('/v1/sla/configs')
      .then((data) => setConfigs(Array.isArray(data) ? data : []))
      .catch(() => setConfigs([]))
      .finally(() => setConfigsLoading(false));

    fetchApi<SlaBreach[]>('/v1/sla/breaches')
      .then((data) => setBreaches(Array.isArray(data) ? data : []))
      .catch(() => setBreaches([]))
      .finally(() => setBreachesLoading(false));
  }, []);

  /* Open create modal */
  const openCreateModal = () => {
    setEditingConfig(null);
    setForm({ apiId: '', uptimeTarget: 99.9, latencyTargetMs: 200, errorBudgetPct: 1.0 });
    setModalOpen(true);
  };

  /* Open edit modal */
  const openEditModal = (config: SlaConfig) => {
    setEditingConfig(config);
    setForm({
      apiId: config.apiId,
      uptimeTarget: config.uptimeTarget,
      latencyTargetMs: config.latencyTargetMs,
      errorBudgetPct: config.errorBudgetPct,
    });
    setModalOpen(true);
  };

  /* Save config */
  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const selectedApi = apiList.find((a) => a.id === form.apiId);
      const payload = { ...form, apiName: selectedApi?.name || 'Unknown API' };
      if (editingConfig) {
        const updated = await putApi<SlaConfig>(`/v1/sla/configs/${editingConfig.id}`, payload);
        setConfigs((prev) => prev.map((c) => (c.id === editingConfig.id ? updated : c)));
        showToast('SLA config updated');
      } else {
        const created = await postApi<SlaConfig>('/v1/sla/configs', payload);
        setConfigs((prev) => [...prev, created]);
        showToast('SLA config created');
      }
      setModalOpen(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save SLA config';
      showToast(msg.includes('409') || msg.includes('conflict') || msg.includes('exists')
        ? 'An SLA config already exists for this API. Edit the existing one instead.'
        : msg, 'error');
    } finally {
      setSaving(false);
    }
  }, [form, editingConfig, apiList]);

  /* Delete config */
  const handleDelete = useCallback(async (id: string) => {
    if (!confirm('Delete this SLA configuration?')) return;
    try {
      await deleteApi(`/v1/sla/configs/${id}`);
      setConfigs((prev) => prev.filter((c) => c.id !== id));
      showToast('SLA config deleted');
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : 'Failed to delete SLA config', 'error');
    }
  }, []);

  /* Breach drill-down handler */
  const handleBreachClick = useCallback(async (breach: SlaBreach) => {
    if (expandedBreachId === breach.id) {
      setExpandedBreachId(null);
      setBreachDrillDownData([]);
      setBreachDrillDownError('');
      return;
    }

    setExpandedBreachId(breach.id);
    setBreachDrillDownLoading(true);
    setBreachDrillDownError('');
    setBreachDrillDownData([]);

    try {
      const breachTime = new Date(breach.breachedAt);
      const windowStart = new Date(breachTime.getTime() - 5 * 60 * 1000); // 5 min before

      const apiId = breach.apiId;

      const payload = {
        metrics: ['request_count'],
        filters: {
          dateFrom: windowStart.toISOString(),
          dateTo: breachTime.toISOString(),
          apiId: apiId || undefined,
        },
        // No groupBy -- raw logs
      };

      const data = await postApi<BreachDrillDownRow[]>('/v1/analytics/report/query', payload);
      setBreachDrillDownData(Array.isArray(data) ? data : []);
    } catch {
      setBreachDrillDownError('Failed to load breach request details');
    } finally {
      setBreachDrillDownLoading(false);
    }
  }, [expandedBreachId]);

  /* Config table columns */
  const configColumns: Column<SlaConfig>[] = useMemo(
    () => [
      { key: 'apiName', label: 'API' },
      {
        key: 'uptimeTarget',
        label: 'Uptime Target',
        render: (row) => `${row.uptimeTarget}%`,
      },
      {
        key: 'latencyTargetMs',
        label: 'Latency Target',
        render: (row) => `${row.latencyTargetMs}ms`,
      },
      {
        key: 'errorBudgetPct',
        label: 'Error Budget',
        render: (row) => `${row.errorBudgetPct}%`,
      },
      {
        key: 'id',
        label: 'Actions',
        render: (row) => (
          <div className="flex gap-2">
            <button
              onClick={() => openEditModal(row)}
              className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 transition-colors"
            >
              Edit
            </button>
            <button
              onClick={() => handleDelete(row.id)}
              className="rounded-lg border border-red-200 bg-white px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 transition-colors"
            >
              Delete
            </button>
          </div>
        ),
      },
    ],
    [handleDelete],
  );

  const inputCls =
    'w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm transition-colors placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20';
  const labelCls = 'mb-1.5 block text-sm font-medium text-slate-700';

  return (
    <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
      {/* Toast */}
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

      {/* Modal overlay */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl">
            <div className="mb-5 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-slate-900">
                {editingConfig ? 'Edit SLA Config' : 'Create SLA Config'}
              </h3>
              <button
                onClick={() => setModalOpen(false)}
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition-colors"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="flex flex-col gap-4">
              <div>
                <label className={labelCls}>API *</label>
                <select
                  className={inputCls}
                  value={form.apiId}
                  onChange={(e) => setForm((f) => ({ ...f, apiId: e.target.value }))}
                  disabled={!!editingConfig}
                >
                  <option value="">Select an API</option>
                  {apiList.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                </select>
              </div>
              <div>
                <label className={labelCls}>Uptime Target (%)</label>
                <input
                  type="number"
                  step="0.01"
                  className={inputCls}
                  value={form.uptimeTarget}
                  onChange={(e) => setForm((f) => ({ ...f, uptimeTarget: parseFloat(e.target.value) || 99.9 }))}
                />
              </div>
              <div>
                <label className={labelCls}>Latency Target (ms)</label>
                <input
                  type="number"
                  className={inputCls}
                  value={form.latencyTargetMs}
                  onChange={(e) => setForm((f) => ({ ...f, latencyTargetMs: parseInt(e.target.value) || 200 }))}
                />
              </div>
              <div>
                <label className={labelCls}>Error Budget (%)</label>
                <input
                  type="number"
                  step="0.01"
                  className={inputCls}
                  value={form.errorBudgetPct}
                  onChange={(e) => setForm((f) => ({ ...f, errorBudgetPct: parseFloat(e.target.value) || 1.0 }))}
                />
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-3">
              <button
                onClick={() => setModalOpen(false)}
                className="rounded-xl border border-slate-300 bg-white px-5 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={saving || !form.apiId}
                className="inline-flex items-center gap-2 rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {saving && (
                  <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                )}
                {editingConfig ? 'Update' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="mx-auto max-w-7xl space-y-8">
        {/* ---- Header ---- */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">SLA Management</h1>
            <p className="mt-1 text-sm text-slate-500">
              Monitor service level agreements, compliance, and breach history
            </p>
          </div>
          <button
            onClick={openCreateModal}
            className="inline-flex items-center rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700"
          >
            Create SLA Config
          </button>
        </div>

        {/* ---- SLA Dashboard ---- */}
        <div>
          <h2 className="mb-4 text-base font-semibold text-slate-900">SLA Compliance Dashboard</h2>
          {dashboardLoading ? (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="rounded-xl border border-slate-200 bg-white p-5">
                  <div className="animate-pulse space-y-3">
                    <div className="h-4 w-28 rounded bg-slate-200" />
                    <div className="h-16 w-16 rounded-full bg-slate-200" />
                    <div className="h-3 w-40 rounded bg-slate-200" />
                  </div>
                </div>
              ))}
            </div>
          ) : dashboard.length > 0 ? (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {dashboard.map((item) => (
                <div
                  key={item.apiId}
                  className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md"
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-sm font-semibold text-slate-900">{item.apiName}</p>
                      <p className="text-xs text-slate-400 mt-0.5">{item.apiId}</p>
                    </div>
                    <ComplianceRing value={item.uptimeActual ?? 100} />
                  </div>
                  <div className="mt-4 grid grid-cols-3 gap-3 text-center">
                    <div>
                      <p className="text-xs text-slate-500">Uptime</p>
                      <p className={`text-sm font-bold ${item.uptimeCompliant ? 'text-emerald-600' : 'text-red-600'}`}>
                        {(item.uptimeActual ?? 0).toFixed(2)}%
                      </p>
                      <p className="text-[10px] text-slate-400">Target: {(item.uptimeTarget ?? 0).toFixed(1)}%</p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Latency P95</p>
                      <p className={`text-sm font-bold ${item.latencyCompliant ? 'text-emerald-600' : 'text-amber-600'}`}>
                        {(item.latencyP95Actual ?? 0).toFixed(0)}ms
                      </p>
                      <p className="text-[10px] text-slate-400">Target: {item.latencyTargetMs ?? 0}ms</p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Error Rate</p>
                      <p className={`text-sm font-bold ${item.errorRateCompliant ? 'text-emerald-600' : 'text-red-600'}`}>
                        {(item.errorRateActual ?? 0).toFixed(2)}%
                      </p>
                      <p className="text-[10px] text-slate-400">Budget: {(item.errorBudgetPct ?? 0).toFixed(1)}%</p>
                    </div>
                  </div>
                  {item.activeBreaches > 0 && (
                    <div className="mt-3 rounded-lg bg-red-50 px-3 py-1.5 text-center text-xs font-medium text-red-700">
                      {item.activeBreaches} active breach{item.activeBreaches > 1 ? 'es' : ''}
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white py-12 text-center">
              <svg className="h-10 w-10 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
              <p className="text-sm text-slate-500">No SLA data available. Create an SLA config to start monitoring.</p>
            </div>
          )}
        </div>

        {/* ---- SLA Configs Table ---- */}
        <div>
          <h2 className="mb-4 text-base font-semibold text-slate-900">SLA Configurations</h2>
          <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
            <DataTable data={configs} columns={configColumns} loading={configsLoading} />
          </div>
        </div>

        {/* ---- Breach History with Drill-Down ---- */}
        <div>
          <h2 className="mb-4 text-base font-semibold text-slate-900">Breach History</h2>
          <p className="mb-2 text-xs text-slate-400">Click a breach row to see the requests that caused it</p>
          <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
            {breachesLoading ? (
              <div className="animate-pulse p-6 space-y-3">
                {Array.from({ length: 3 }).map((_, i) => (
                  <div key={i} className="h-10 w-full rounded bg-slate-100" />
                ))}
              </div>
            ) : breaches.length === 0 ? (
              <div className="px-4 py-8 text-center text-gray-400">No data available</div>
            ) : (
              <div className="w-full overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200 bg-white text-sm">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">API</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Metric</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Target</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actual</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">When</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {breaches.map((breach) => (
                      <React.Fragment key={breach.id}>
                        <tr
                          onClick={() => handleBreachClick(breach)}
                          className={`cursor-pointer transition-colors ${
                            expandedBreachId === breach.id
                              ? 'bg-purple-50 border-l-4 border-l-purple-500'
                              : 'hover:bg-gray-50'
                          }`}
                        >
                          <td className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">{breach.apiName || breach.apiId}</td>
                          <td className="px-4 py-3 text-sm whitespace-nowrap">
                            <span className="rounded bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-700">
                              {breach.metric}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">{breach.targetValue}</td>
                          <td className="px-4 py-3 text-sm whitespace-nowrap">
                            <span className="font-semibold text-red-600">{breach.actualValue}</span>
                          </td>
                          <td className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">
                            {new Date(breach.breachedAt).toLocaleString()}
                          </td>
                        </tr>

                        {/* Breach drill-down expanded section */}
                        {expandedBreachId === breach.id && (
                          <tr>
                            <td colSpan={5} className="p-0">
                              <div className="bg-purple-50/50 border-t border-purple-100 px-6 py-4">
                                <div className="flex items-center justify-between mb-3">
                                  <h4 className="text-sm font-semibold text-purple-900">
                                    Requests during breach window ({breach.apiName}, 5 min before breach)
                                  </h4>
                                  <button
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      setExpandedBreachId(null);
                                      setBreachDrillDownData([]);
                                      setBreachDrillDownError('');
                                    }}
                                    className="inline-flex items-center gap-1 rounded-lg border border-purple-200 bg-white px-3 py-1.5 text-xs font-medium text-purple-700 hover:bg-purple-50 transition-colors"
                                  >
                                    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                                    </svg>
                                    Close
                                  </button>
                                </div>

                                {breachDrillDownLoading && (
                                  <div className="flex items-center justify-center gap-2 py-8">
                                    <Spinner />
                                    <span className="text-sm text-purple-600">Loading breach details...</span>
                                  </div>
                                )}

                                {breachDrillDownError && (
                                  <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
                                    <svg className="h-4 w-4 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
                                    </svg>
                                    <p className="text-sm text-red-700">{breachDrillDownError}</p>
                                  </div>
                                )}

                                {!breachDrillDownLoading && !breachDrillDownError && breachDrillDownData.length === 0 && (
                                  <div className="flex flex-col items-center justify-center gap-1 py-8 text-center">
                                    <svg className="h-8 w-8 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                                      <path strokeLinecap="round" strokeLinejoin="round" d="M20 13V7a2 2 0 00-2-2H6a2 2 0 00-2 2v6m16 0v6a2 2 0 01-2 2H6a2 2 0 01-2-2v-6m16 0H4" />
                                    </svg>
                                    <p className="text-sm text-slate-500">No matching requests found for this breach window</p>
                                  </div>
                                )}

                                {!breachDrillDownLoading && breachDrillDownData.length > 0 && (
                                  <div className="overflow-x-auto rounded-lg border border-purple-100 bg-white">
                                    <table className="w-full text-left text-xs">
                                      <thead className="border-b border-purple-100 bg-purple-50/80">
                                        <tr>
                                          {BREACH_DRILL_DOWN_COLUMNS.map((col) => (
                                            <th key={col.key} className="px-4 py-2.5 font-semibold text-purple-700 whitespace-nowrap">
                                              {col.label}
                                            </th>
                                          ))}
                                        </tr>
                                      </thead>
                                      <tbody className="divide-y divide-slate-100">
                                        {breachDrillDownData.map((detail, j) => (
                                          <tr key={j} className="hover:bg-slate-50/50 transition-colors">
                                            {BREACH_DRILL_DOWN_COLUMNS.map((col) => (
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
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
