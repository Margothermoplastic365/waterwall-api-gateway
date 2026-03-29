'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, FormModal, get, post, put, del } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

interface Connector {
  id: string;
  name: string;
  type: string;
  config: string;
  enabled: boolean;
  createdAt: string;
  [key: string]: unknown;
}

const CONNECTOR_TYPES = ['SLACK', 'PAGERDUTY', 'JIRA', 'DATADOG'] as const;

function typeBadgeClasses(type: string): string {
  switch (type.toUpperCase()) {
    case 'SLACK': return 'bg-emerald-100 text-emerald-700';
    case 'PAGERDUTY': return 'bg-red-100 text-red-700';
    case 'JIRA': return 'bg-blue-100 text-blue-700';
    case 'DATADOG': return 'bg-purple-100 text-purple-700';
    default: return 'bg-slate-100 text-slate-600';
  }
}

export default function ConnectorsPage() {
  const [connectors, setConnectors] = useState<Connector[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  /* Modal */
  const [modal, setModal] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    name: '',
    type: 'SLACK' as string,
    config: '{}',
  });

  /* Testing */
  const [testing, setTesting] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  useEffect(() => {
    setLoading(true);
    get<Connector[]>('/v1/connectors')
      .then((data) => setConnectors(Array.isArray(data) ? data : []))
      .catch(() => setError('Failed to load connectors'))
      .finally(() => setLoading(false));
  }, []);

  const handleCreate = useCallback(async () => {
    setSaving(true);
    try {
      const created = await post<Connector>('/v1/connectors', {
        name: form.name,
        type: form.type,
        config: form.config,
      });
      setConnectors((prev) => [...prev, created]);
      setModal(false);
      setForm({ name: '', type: 'SLACK', config: '{}' });
    } catch {
      showToast('Failed to create connector', 'error');
    } finally {
      setSaving(false);
    }
  }, [form]);

  const handleToggle = useCallback(async (connector: Connector) => {
    try {
      const updated = await put<Connector>(`/v1/connectors/${connector.id}`, {
        ...connector,
        enabled: !connector.enabled,
      });
      setConnectors((prev) => prev.map((c) => (c.id === connector.id ? updated : c)));
    } catch {
      showToast('Failed to toggle connector', 'error');
    }
  }, []);

  const handleTest = useCallback(async (connectorId: string) => {
    setTesting(connectorId);
    try {
      await post(`/v1/connectors/${connectorId}/test`, {});
      showToast('Connection test successful');
    } catch {
      showToast('Connection test failed', 'error');
    } finally {
      setTesting(null);
    }
  }, []);

  const handleDelete = useCallback(async (connectorId: string) => {
    if (!confirm('Delete this connector?')) return;
    try {
      await del(`/v1/connectors/${connectorId}`);
      setConnectors((prev) => prev.filter((c) => c.id !== connectorId));
    } catch {
      showToast('Failed to delete connector', 'error');
    }
  }, []);

  const columns: Column<Connector>[] = useMemo(
    () => [
      { key: 'name', label: 'Name' },
      {
        key: 'type',
        label: 'Type',
        render: (row) => (
          <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${typeBadgeClasses(row.type)}`}>
            {row.type}
          </span>
        ),
      },
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
            onClick={() => handleToggle(row)}
          >
            {row.enabled ? 'ON' : 'OFF'}
          </button>
        ),
      },
      {
        key: 'createdAt',
        label: 'Created',
        render: (row) => new Date(row.createdAt).toLocaleDateString(),
      },
      {
        key: 'id',
        label: 'Actions',
        render: (row) => (
          <div className="flex items-center gap-2">
            <button
              className="inline-flex items-center rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-200"
              onClick={() => handleTest(row.id)}
              disabled={testing === row.id}
            >
              {testing === row.id ? 'Testing...' : 'Test'}
            </button>
            <button
              className="inline-flex items-center rounded-lg bg-red-50 px-3 py-1.5 text-xs font-medium text-red-600 transition-colors hover:bg-red-100"
              onClick={() => handleDelete(row.id)}
            >
              Delete
            </button>
          </div>
        ),
      },
    ],
    [handleToggle, handleTest, handleDelete, testing],
  );

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
        <div className="mx-auto max-w-7xl">
          <div className="mb-8">
            <div className="h-8 w-64 animate-pulse rounded-lg bg-slate-200" />
            <div className="mt-2 h-4 w-96 animate-pulse rounded-lg bg-slate-200" />
          </div>
          <div className="h-64 animate-pulse rounded-xl bg-white shadow-sm" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
      <div className="mx-auto max-w-7xl">
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
        {/* Header */}
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Integration Connectors</h1>
            <p className="mt-1 text-sm text-slate-500">Manage external integrations and notification channels</p>
          </div>
          <button
            className="inline-flex items-center rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700"
            onClick={() => setModal(true)}
          >
            Add Connector
          </button>
        </div>

        {error && (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          <DataTable data={connectors} columns={columns} loading={loading} />
        </div>

        <FormModal
          open={modal}
          onClose={() => setModal(false)}
          title="Add Connector"
          onSubmit={handleCreate}
          submitLabel="Create Connector"
          loading={saving}
        >
          <div className="flex flex-col gap-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Name *</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm transition-colors placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="My Slack Integration"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Type</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm transition-colors focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.type}
                onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}
              >
                {CONNECTOR_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Configuration (JSON)</label>
              <textarea
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 font-mono text-xs text-slate-900 shadow-sm transition-colors placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                style={{ minHeight: 120 }}
                value={form.config}
                onChange={(e) => setForm((f) => ({ ...f, config: e.target.value }))}
                placeholder='{"webhookUrl": "https://hooks.slack.com/...", "channel": "#alerts"}'
              />
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
