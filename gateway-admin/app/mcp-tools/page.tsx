'use client';

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, FormModal } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8082';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface McpTool {
  name: string;
  description: string;
  serverUrl: string;
  enabled: boolean;
  consumers: string[];
  invocations?: number;
  avgLatency?: number;
  errorRate?: number;
  [key: string]: unknown;
}

interface McpToolFormState {
  name: string;
  description: string;
  serverUrl: string;
  allowedConsumers: string;
}

interface TestInvokeState {
  toolName: string;
  input: string;
  result: string;
  loading: boolean;
}

const emptyForm: McpToolFormState = {
  name: '',
  description: '',
  serverUrl: '',
  allowedConsumers: '',
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

export default function McpToolsPage() {
  const [tools, setTools] = useState<McpTool[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /* Modal state */
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<McpToolFormState>(emptyForm);
  const [saving, setSaving] = useState(false);

  /* Test invoke state */
  const [testInvoke, setTestInvoke] = useState<TestInvokeState>({
    toolName: '',
    input: '{}',
    result: '',
    loading: false,
  });
  const [testModalOpen, setTestModalOpen] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  /* ---- Fetch tools ---- */
  const fetchTools = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${GATEWAY_URL}/v1/ai/mcp/tools`, { headers: authHeaders() });
      if (!res.ok) throw new Error('Failed to load MCP tools');
      const data = await res.json();
      const list: McpTool[] = Array.isArray(data) ? data : data.content ?? [];

      /* Enrich with per-tool stats (best effort) */
      const enriched = await Promise.all(
        list.map(async (tool) => {
          try {
            const statsRes = await fetch(`${GATEWAY_URL}/v1/ai/mcp/tools/${tool.name}/stats`, {
              headers: authHeaders(),
            });
            if (statsRes.ok) {
              const stats = await statsRes.json();
              return { ...tool, ...stats };
            }
          } catch {
            /* ignore */
          }
          return tool;
        }),
      );
      setTools(enriched);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load MCP tools';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchTools();
  }, [fetchTools]);

  /* ---- Register tool ---- */
  const handleRegister = useCallback(async () => {
    if (!form.name.trim() || !form.serverUrl.trim()) return;
    setSaving(true);
    try {
      const body = {
        name: form.name.trim(),
        description: form.description.trim(),
        serverUrl: form.serverUrl.trim(),
        allowedConsumers: form.allowedConsumers
          .split('\n')
          .map((s) => s.trim())
          .filter(Boolean),
      };
      const res = await fetch(`${GATEWAY_URL}/v1/ai/mcp/tools`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error('Failed to register tool');
      setModalOpen(false);
      setForm(emptyForm);
      fetchTools();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to register tool';
      showToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  }, [form, fetchTools]);

  /* ---- Toggle enabled ---- */
  const handleToggle = useCallback(
    async (tool: McpTool) => {
      try {
        const res = await fetch(`${GATEWAY_URL}/v1/ai/mcp/tools/${tool.name}`, {
          method: 'PATCH',
          headers: authHeaders(),
          body: JSON.stringify({ enabled: !tool.enabled }),
        });
        if (!res.ok) throw new Error('Failed to toggle');
        fetchTools();
      } catch {
        showToast('Failed to toggle tool status', 'error');
      }
    },
    [fetchTools],
  );

  /* ---- Test invoke ---- */
  const openTestModal = (toolName: string) => {
    setTestInvoke({ toolName, input: '{}', result: '', loading: false });
    setTestModalOpen(true);
  };

  const handleTestInvoke = useCallback(async () => {
    setTestInvoke((s) => ({ ...s, loading: true, result: '' }));
    try {
      let parsedInput = {};
      try {
        parsedInput = JSON.parse(testInvoke.input);
      } catch {
        setTestInvoke((s) => ({ ...s, loading: false, result: 'Invalid JSON input' }));
        return;
      }
      const res = await fetch(`${GATEWAY_URL}/v1/ai/mcp/tools/${testInvoke.toolName}/invoke`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(parsedInput),
      });
      const text = await res.text();
      setTestInvoke((s) => ({ ...s, loading: false, result: text }));
    } catch {
      setTestInvoke((s) => ({ ...s, loading: false, result: 'Invocation failed' }));
    }
  }, [testInvoke.toolName, testInvoke.input]);

  /* ---- Columns ---- */
  const columns: Column<McpTool>[] = useMemo(
    () => [
      { key: 'name', label: 'Name', sortable: true },
      { key: 'description', label: 'Description' },
      { key: 'serverUrl', label: 'Server URL' },
      {
        key: 'enabled',
        label: 'Enabled',
        render: (t) => (
          <button
            className={`inline-flex items-center rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              t.enabled
                ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-200'
                : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
            }`}
            onClick={() => handleToggle(t)}
          >
            {t.enabled ? 'Enabled' : 'Disabled'}
          </button>
        ),
      },
      {
        key: 'consumers',
        label: 'Consumers',
        render: (t) => (
          <span className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-700">
            {t.consumers?.length ?? 0}
          </span>
        ),
      },
      {
        key: 'invocations',
        label: 'Invocations',
        render: (t) => (t.invocations ?? 0).toLocaleString(),
      },
      {
        key: 'avgLatency',
        label: 'Avg Latency',
        render: (t) => (t.avgLatency != null ? `${t.avgLatency}ms` : <span className="text-slate-400">-</span>),
      },
      {
        key: 'errorRate',
        label: 'Error Rate',
        render: (t) =>
          t.errorRate != null ? (
            <span className={`font-medium ${(t.errorRate as number) > 5 ? 'text-red-600' : 'text-emerald-600'}`}>
              {(t.errorRate as number).toFixed(2)}%
            </span>
          ) : (
            <span className="text-slate-400">-</span>
          ),
      },
      {
        key: 'actions',
        label: 'Actions',
        render: (t) => (
          <button
            className="inline-flex items-center rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-200"
            onClick={() => openTestModal(t.name)}
          >
            Test Invoke
          </button>
        ),
      },
    ],
    [handleToggle],
  );

  const set =
    (key: keyof McpToolFormState) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
        <div className="mx-auto max-w-7xl">
          <div className="mb-8">
            <div className="h-8 w-56 animate-pulse rounded-lg bg-slate-200" />
            <div className="mt-2 h-4 w-96 animate-pulse rounded-lg bg-slate-200" />
          </div>
          <div className="h-64 animate-pulse rounded-xl bg-white shadow-sm" />
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
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">MCP Tool Registry</h1>
            <p className="mt-1 text-sm text-slate-500">Manage Model Context Protocol tool integrations</p>
          </div>
          <button
            className="inline-flex items-center rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700"
            onClick={() => { setForm(emptyForm); setModalOpen(true); }}
          >
            Register Tool
          </button>
        </div>

        {error && (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          <DataTable data={tools} columns={columns} />
        </div>

        {/* Register Tool Modal */}
        <FormModal
          open={modalOpen}
          onClose={() => setModalOpen(false)}
          title="Register MCP Tool"
          onSubmit={handleRegister}
          submitLabel="Register"
          loading={saving}
        >
          <div className="flex flex-col gap-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={form.name}
                onChange={set('name')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                placeholder="weather-lookup"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Description</label>
              <input
                type="text"
                value={form.description}
                onChange={set('description')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                placeholder="Look up weather for a given city"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Server URL <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={form.serverUrl}
                onChange={set('serverUrl')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                placeholder="https://mcp-server.example.com"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Allowed Consumers (one per line)</label>
              <textarea
                value={form.allowedConsumers}
                onChange={set('allowedConsumers')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                rows={4}
                placeholder={"consumer-1\nconsumer-2"}
              />
            </div>
          </div>
        </FormModal>

        {/* Test Invoke Modal */}
        <FormModal
          open={testModalOpen}
          onClose={() => setTestModalOpen(false)}
          title={`Test Invoke: ${testInvoke.toolName}`}
          onSubmit={handleTestInvoke}
          submitLabel="Invoke"
          loading={testInvoke.loading}
        >
          <div className="flex flex-col gap-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Input (JSON)</label>
              <textarea
                value={testInvoke.input}
                onChange={(e) => setTestInvoke((s) => ({ ...s, input: e.target.value }))}
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 font-mono text-xs text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                rows={5}
              />
            </div>
            {testInvoke.result && (
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">Result</label>
                <pre className="max-h-[200px] overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 font-mono text-xs text-slate-700">
                  {testInvoke.result}
                </pre>
              </div>
            )}
          </div>
        </FormModal>
      </div>
    </div>
  );
}
