'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, StatusBadge, FormModal, get, post, put } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const ANALYTICS_URL = process.env.NEXT_PUBLIC_ANALYTICS_URL || 'http://localhost:8083';

type Tab = 'rules' | 'history';

interface AlertRule {
  id: string;
  name: string;
  metric: string;
  condition: string;
  threshold: number;
  windowMinutes: number;
  apiId?: string;
  channels: string[];
  enabled: boolean;
  [key: string]: unknown;
}

interface AlertEvent {
  id: string;
  ruleName: string;
  status: string;
  value: number;
  triggeredAt: string;
  [key: string]: unknown;
}

const METRICS = ['error_rate', 'avg_latency', 'request_count', 'p99_latency'] as const;
const CONDITIONS = ['>', '<', '='] as const;

function fetchAnalytics<T>(path: string): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(`${ANALYTICS_URL}${path}`, { headers }).then((r) => {
    if (!r.ok) throw new Error('Failed');
    return r.json();
  });
}

function postAnalytics<T>(path: string, body: unknown): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
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

function putAnalytics<T>(path: string, body: unknown): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
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

export default function AlertsPage() {
  const [tab, setTab] = useState<Tab>('rules');

  /* Rules state */
  const [rules, setRules] = useState<AlertRule[]>([]);
  const [rulesLoading, setRulesLoading] = useState(false);

  /* History state */
  const [history, setHistory] = useState<AlertEvent[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  /* Modal state */
  const [modal, setModal] = useState(false);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };
  const [form, setForm] = useState({
    name: '',
    metric: 'error_rate' as string,
    condition: '>' as string,
    threshold: 0,
    windowMinutes: 5,
    apiId: '',
    channels: [] as string[],
  });

  /* Load rules */
  useEffect(() => {
    if (tab !== 'rules') return;
    setRulesLoading(true);
    fetchAnalytics<AlertRule[]>('/v1/alerts/rules')
      .then((data) => setRules(Array.isArray(data) ? data : []))
      .catch(() => setRules([]))
      .finally(() => setRulesLoading(false));
  }, [tab]);

  /* Load history */
  useEffect(() => {
    if (tab !== 'history') return;
    setHistoryLoading(true);
    fetchAnalytics<AlertEvent[]>('/v1/alerts/history')
      .then((data) => setHistory(Array.isArray(data) ? data : []))
      .catch(() => setHistory([]))
      .finally(() => setHistoryLoading(false));
  }, [tab]);

  const handleToggleRule = useCallback(async (rule: AlertRule) => {
    try {
      const updated = await putAnalytics<AlertRule>(`/v1/alerts/rules/${rule.id}`, {
        ...rule,
        enabled: !rule.enabled,
      });
      setRules((prev) => prev.map((r) => (r.id === rule.id ? updated : r)));
    } catch {
      showToast('Failed to toggle rule', 'error');
    }
  }, []);

  const handleCreateRule = useCallback(async () => {
    setSaving(true);
    try {
      const created = await postAnalytics<AlertRule>('/v1/alerts/rules', {
        name: form.name,
        metric: form.metric,
        condition: form.condition,
        threshold: form.threshold,
        windowMinutes: form.windowMinutes,
        apiId: form.apiId || undefined,
        channels: form.channels,
      });
      setRules((prev) => [...prev, created]);
      setModal(false);
      setForm({ name: '', metric: 'error_rate', condition: '>', threshold: 0, windowMinutes: 5, apiId: '', channels: [] });
    } catch {
      showToast('Failed to create rule', 'error');
    } finally {
      setSaving(false);
    }
  }, [form]);

  const handleAcknowledge = useCallback(async (eventId: string) => {
    try {
      await postAnalytics(`/v1/alerts/history/${eventId}/acknowledge`, {});
      setHistory((prev) =>
        prev.map((e) => (e.id === eventId ? { ...e, status: 'ACKNOWLEDGED' } : e)),
      );
    } catch {
      showToast('Failed to acknowledge alert', 'error');
    }
  }, []);

  const toggleChannel = (channel: string) => {
    setForm((prev) => ({
      ...prev,
      channels: prev.channels.includes(channel)
        ? prev.channels.filter((c) => c !== channel)
        : [...prev.channels, channel],
    }));
  };

  const ruleColumns: Column<AlertRule>[] = useMemo(
    () => [
      { key: 'name', label: 'Name' },
      { key: 'metric', label: 'Metric' },
      {
        key: 'condition',
        label: 'Condition',
        render: (row) => (
          <span className="rounded bg-slate-100 px-2 py-1 font-mono text-sm text-slate-700">
            {row.condition} {row.threshold}
          </span>
        ),
      },
      { key: 'windowMinutes', label: 'Window (min)' },
      {
        key: 'enabled',
        label: 'Enabled',
        render: (row) => (
          <button
            className={`inline-flex items-center rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              row.enabled
                ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-200'
                : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
            }`}
            onClick={() => handleToggleRule(row)}
          >
            {row.enabled ? 'ON' : 'OFF'}
          </button>
        ),
      },
    ],
    [handleToggleRule],
  );

  const historyColumns: Column<AlertEvent>[] = useMemo(
    () => [
      { key: 'ruleName', label: 'Rule Name' },
      {
        key: 'status',
        label: 'Status',
        render: (row) => <StatusBadge status={row.status} size="sm" />,
      },
      { key: 'value', label: 'Value' },
      {
        key: 'triggeredAt',
        label: 'Triggered At',
        render: (row) => new Date(row.triggeredAt).toLocaleString(),
      },
      {
        key: 'id',
        label: 'Actions',
        render: (row) =>
          row.status === 'TRIGGERED' ? (
            <button
              className="inline-flex items-center rounded-lg bg-amber-100 px-3 py-1.5 text-xs font-medium text-amber-700 transition-colors hover:bg-amber-200"
              onClick={() => handleAcknowledge(row.id)}
            >
              Acknowledge
            </button>
          ) : null,
      },
    ],
    [handleAcknowledge],
  );

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
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Alerts</h1>
            <p className="mt-1 text-sm text-slate-500">Alert rules and triggered event history</p>
          </div>
          {tab === 'rules' && (
            <button
              className="inline-flex items-center rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700"
              onClick={() => setModal(true)}
            >
              Create Rule
            </button>
          )}
        </div>

        {/* Tabs */}
        <div className="mb-6 inline-flex items-center gap-1 rounded-xl bg-white p-1 shadow-sm ring-1 ring-slate-200">
          {(['rules', 'history'] as Tab[]).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`rounded-lg px-5 py-2 text-sm font-medium capitalize transition-all ${
                tab === t
                  ? 'bg-purple-600 text-white shadow-sm'
                  : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        {tab === 'rules' && (
          <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
            <DataTable data={rules} columns={ruleColumns} loading={rulesLoading} />
          </div>
        )}

        {tab === 'history' && (
          <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
            <DataTable data={history} columns={historyColumns} loading={historyLoading} />
          </div>
        )}

        <FormModal
          open={modal}
          onClose={() => setModal(false)}
          title="Create Alert Rule"
          onSubmit={handleCreateRule}
          submitLabel="Create Rule"
          loading={saving}
        >
          <div className="flex flex-col gap-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Name *</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm transition-colors placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="High error rate alert"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Metric</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm transition-colors focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.metric}
                onChange={(e) => setForm((f) => ({ ...f, metric: e.target.value }))}
              >
                {METRICS.map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">Condition</label>
                <select
                  className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm transition-colors focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                  value={form.condition}
                  onChange={(e) => setForm((f) => ({ ...f, condition: e.target.value }))}
                >
                  {CONDITIONS.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">Threshold</label>
                <input
                  type="number"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm transition-colors placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                  value={form.threshold}
                  onChange={(e) => setForm((f) => ({ ...f, threshold: parseFloat(e.target.value) || 0 }))}
                />
              </div>
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Window (minutes)</label>
              <input
                type="number"
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm transition-colors placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.windowMinutes}
                onChange={(e) => setForm((f) => ({ ...f, windowMinutes: parseInt(e.target.value) || 5 }))}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">API (optional)</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm transition-colors placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.apiId}
                onChange={(e) => setForm((f) => ({ ...f, apiId: e.target.value }))}
                placeholder="Leave empty for all APIs"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Channels</label>
              <div className="flex gap-4">
                {['email', 'webhook'].map((ch) => (
                  <label key={ch} className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-slate-300 text-purple-600 focus:ring-purple-500/20"
                      checked={form.channels.includes(ch)}
                      onChange={() => toggleChannel(ch)}
                    />
                    {ch.charAt(0).toUpperCase() + ch.slice(1)}
                  </label>
                ))}
              </div>
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
