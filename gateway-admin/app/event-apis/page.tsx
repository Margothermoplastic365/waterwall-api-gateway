'use client';

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, FormModal } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface EventApi {
  id: string;
  name: string;
  protocol: string;
  topics: string[];
  subscriptions: number;
  connectionConfig?: Record<string, unknown>;
  createdAt: string;
  [key: string]: unknown;
}

interface EventApiFormState {
  name: string;
  protocol: string;
  connectionConfig: string;
  topics: string;
}

const PROTOCOLS = ['RABBITMQ', 'KAFKA', 'NATS', 'REDIS_PUBSUB'];

const emptyForm: EventApiFormState = {
  name: '',
  protocol: 'RABBITMQ',
  connectionConfig: '{}',
  topics: '',
};

function authHeaders(): Record<string, string> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return headers;
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function EventApisPage() {
  const [eventApis, setEventApis] = useState<EventApi[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  /* Modal state */
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<EventApiFormState>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  /* ---- Fetch event APIs ---- */
  const fetchEventApis = useCallback(() => {
    setLoading(true);
    setError(null);
    fetch(`${API_URL}/v1/event-apis`, { headers: authHeaders() })
      .then((r) => {
        if (!r.ok) throw new Error('Failed to load event APIs');
        return r.json();
      })
      .then((data) => {
        const list = Array.isArray(data) ? data : data.content ?? [];
        setEventApis(list);
      })
      .catch((err) => setError(err.message ?? 'Failed to load event APIs'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    fetchEventApis();
  }, [fetchEventApis]);

  /* ---- Create event API ---- */
  const handleCreate = useCallback(async () => {
    if (!form.name.trim()) return;
    setSaving(true);
    try {
      let connConfig = {};
      try {
        connConfig = JSON.parse(form.connectionConfig);
      } catch {
        showToast('Invalid JSON in connection config', 'error');
        setSaving(false);
        return;
      }
      const body = {
        name: form.name.trim(),
        protocol: form.protocol,
        connectionConfig: connConfig,
        topics: form.topics
          .split(',')
          .map((t) => t.trim())
          .filter(Boolean),
      };
      const res = await fetch(`${API_URL}/v1/event-apis`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error('Failed to create event API');
      setModalOpen(false);
      setForm(emptyForm);
      fetchEventApis();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to create event API';
      showToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  }, [form, fetchEventApis]);

  /* ---- Columns ---- */
  const columns: Column<EventApi>[] = useMemo(
    () => [
      {
        key: 'name',
        label: 'Name',
        sortable: true,
        render: (row) => (
          <button
            className="text-sm font-semibold text-purple-600 hover:text-purple-800 bg-transparent border-none cursor-pointer p-0 transition-colors"
            onClick={() => setExpandedId(expandedId === row.id ? null : row.id)}
          >
            {row.name}
          </button>
        ),
      },
      {
        key: 'protocol',
        label: 'Protocol',
        render: (row) => <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-cyan-100 text-cyan-700">{row.protocol}</span>,
      },
      {
        key: 'topics',
        label: 'Topics',
        render: (row) => <span className="text-sm text-slate-600">{row.topics?.length ?? 0}</span>,
      },
      {
        key: 'subscriptions',
        label: 'Subscriptions',
        render: (row) => <span className="text-sm text-slate-600">{row.subscriptions ?? 0}</span>,
      },
      {
        key: 'createdAt',
        label: 'Created',
        render: (row) => <span className="text-sm text-slate-500">{new Date(row.createdAt).toLocaleDateString()}</span>,
      },
    ],
    [expandedId],
  );

  const set =
    (key: keyof EventApiFormState) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
        <div className="max-w-7xl mx-auto space-y-6">
          <div className="space-y-2">
            <div className="h-7 w-56 bg-slate-200 rounded-lg animate-pulse" />
            <div className="h-4 w-80 bg-slate-100 rounded-lg animate-pulse" />
          </div>
          <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3">
            {[...Array(5)].map((_, i) => (
              <div key={i} className="h-10 bg-slate-50 rounded-lg animate-pulse" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  /* Find expanded event API */
  const expanded = expandedId ? eventApis.find((e) => e.id === expandedId) : null;

  return (
    <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
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
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Event API Management</h1>
            <p className="mt-1 text-sm text-slate-500">Manage event-driven APIs and message brokers</p>
          </div>
          <button
            className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200"
            onClick={() => { setForm(emptyForm); setModalOpen(true); }}
          >
            <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
            Create Event API
          </button>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <DataTable data={eventApis} columns={columns} />
        </div>

        {/* Expanded Detail */}
        {expanded && (
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <div className="flex items-center justify-between mb-5">
              <h3 className="text-base font-semibold text-slate-900">{expanded.name} - Details</h3>
              <button
                className="inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition-all"
                onClick={() => setExpandedId(null)}
              >
                Close
              </button>
            </div>

            <div className="grid grid-cols-2 gap-x-6 gap-y-3 mb-5">
              <div>
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Protocol</p>
                <div className="mt-1">
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-cyan-100 text-cyan-700">{expanded.protocol}</span>
                </div>
              </div>
              <div>
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Created</p>
                <p className="mt-1 text-sm text-slate-700">{new Date(expanded.createdAt).toLocaleString()}</p>
              </div>
            </div>

            <div className="pt-4 border-t border-slate-100">
              <h4 className="text-sm font-semibold text-slate-900 mb-3">Topics</h4>
              {expanded.topics.length === 0 ? (
                <p className="text-sm text-slate-400">No topics configured</p>
              ) : (
                <div className="flex flex-wrap gap-2 mb-4">
                  {expanded.topics.map((t) => (
                    <span key={t} className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-700">{t}</span>
                  ))}
                </div>
              )}
            </div>

            {expanded.connectionConfig && (
              <div className="pt-4 border-t border-slate-100">
                <h4 className="text-sm font-semibold text-slate-900 mb-3">Connection Config</h4>
                <pre className="bg-slate-50 border border-slate-200 rounded-xl p-4 text-xs text-slate-700 font-mono max-h-[200px] overflow-auto">
                  {JSON.stringify(expanded.connectionConfig, null, 2)}
                </pre>
              </div>
            )}
          </div>
        )}

        {/* Create Event API Modal */}
        <FormModal
          open={modalOpen}
          onClose={() => setModalOpen(false)}
          title="Create Event API"
          onSubmit={handleCreate}
          submitLabel="Create"
          loading={saving}
        >
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">
                Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={form.name}
                onChange={set('name')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                placeholder="order-events"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Protocol</label>
              <select
                value={form.protocol}
                onChange={set('protocol')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
              >
                {PROTOCOLS.map((p) => (
                  <option key={p} value={p}>{p}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Connection Config (JSON)</label>
              <textarea
                value={form.connectionConfig}
                onChange={set('connectionConfig')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm font-mono shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all resize-y"
                rows={5}
                placeholder={'{\n  "host": "rabbitmq.local",\n  "port": 5672\n}'}
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Topics (comma-separated)</label>
              <input
                type="text"
                value={form.topics}
                onChange={set('topics')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                placeholder="orders.created, orders.updated, orders.deleted"
              />
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
