'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, FormModal, get, post, del } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

interface ApiOption {
  id: string;
  name: string;
}

interface MockConfig {
  id: string;
  path: string;
  method: string;
  statusCode: number;
  responseBody: string;
  headers: string;
  latencyMs: number;
  errorRatePercent: number;
  mockEnabled: boolean;
  [key: string]: unknown;
}

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const;

export default function MockingPage() {
  const [apis, setApis] = useState<ApiOption[]>([]);
  const [selectedApi, setSelectedApi] = useState('');
  const [mockEnabled, setMockEnabled] = useState(false);
  const [configs, setConfigs] = useState<MockConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [toggling, setToggling] = useState(false);

  /* Modal state */
  const [modal, setModal] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    path: '',
    method: 'GET',
    statusCode: 200,
    responseBody: '',
    headers: '{}',
    latencyMs: 0,
    errorRate: 0,
  });
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  /* Load APIs */
  useEffect(() => {
    get<ApiOption[] | { content: ApiOption[] }>('/v1/apis?size=200')
      .then((data) => {
        const list = Array.isArray(data) ? data : data.content ?? [];
        setApis(list);
        if (list.length > 0 && !selectedApi) setSelectedApi(list[0].id);
      })
      .catch(() => setApis([]));
  }, []);

  /* Load mock configs when API selected */
  useEffect(() => {
    if (!selectedApi) return;
    setLoading(true);
    get<MockConfig[]>(`/v1/mocking/${selectedApi}/configs`)
      .then((data) => setConfigs(Array.isArray(data) ? data : []))
      .catch(() => setConfigs([]))
      .finally(() => setLoading(false));
  }, [selectedApi]);

  const handleToggleMock = useCallback(async () => {
    if (!selectedApi) return;
    setToggling(true);
    try {
      const action = mockEnabled ? 'disable' : 'enable';
      await post(`/v1/mocking/${selectedApi}/${action}`, {});
      setMockEnabled(!mockEnabled);
    } catch {
      showToast('Failed to toggle mock mode', 'error');
    } finally {
      setToggling(false);
    }
  }, [selectedApi, mockEnabled]);

  const handleAddMock = useCallback(async () => {
    if (!selectedApi) return;
    setSaving(true);
    try {
      const created = await post<MockConfig>(`/v1/mocking/${selectedApi}/configs`, {
        path: form.path,
        method: form.method,
        statusCode: form.statusCode,
        responseBody: form.responseBody,
        headers: form.headers,
        latencyMs: form.latencyMs,
        errorRatePercent: form.errorRate,
        enabled: true,
      });
      setConfigs((prev) => [...prev, created]);
      setModal(false);
      setForm({ path: '', method: 'GET', statusCode: 200, responseBody: '', headers: '{}', latencyMs: 0, errorRate: 0 });
    } catch {
      showToast('Failed to add mock response', 'error');
    } finally {
      setSaving(false);
    }
  }, [selectedApi, form]);

  const handleDelete = useCallback(
    async (configId: string) => {
      if (!confirm('Delete this mock config?')) return;
      try {
        await del(`/v1/mocking/${selectedApi}/configs/${configId}`);
        setConfigs((prev) => prev.filter((c) => c.id !== configId));
      } catch {
        showToast('Failed to delete mock config', 'error');
      }
    },
    [selectedApi],
  );

  const columns: Column<MockConfig>[] = useMemo(
    () => [
      {
        key: 'method',
        label: 'Method',
        render: (row) => (
          <span className={`method-badge method-${row.method.toLowerCase()}`}>{row.method}</span>
        ),
      },
      { key: 'path', label: 'Path' },
      { key: 'statusCode', label: 'Status' },
      { key: 'latencyMs', label: 'Latency (ms)' },
      {
        key: 'errorRatePercent',
        label: 'Error Rate %',
        render: (row) => (
          <span className={`text-sm font-medium ${row.errorRatePercent > 0 ? 'text-amber-600' : 'text-slate-500'}`}>
            {row.errorRatePercent}%
          </span>
        ),
      },
      {
        key: 'mockEnabled',
        label: 'Enabled',
        render: (row) => (
          <span className={`inline-flex items-center gap-1.5 text-xs font-medium ${row.mockEnabled ? 'text-emerald-600' : 'text-slate-400'}`}>
            <span className={`w-1.5 h-1.5 rounded-full ${row.mockEnabled ? 'bg-emerald-500' : 'bg-slate-300'}`} />
            {row.mockEnabled ? 'On' : 'Off'}
          </span>
        ),
      },
      {
        key: 'id',
        label: 'Actions',
        render: (row) => (
          <button
            className="inline-flex items-center rounded-lg bg-red-50 px-3 py-1.5 text-xs font-medium text-red-600 transition-colors hover:bg-red-100"
            onClick={() => handleDelete(row.id)}
          >
            Delete
          </button>
        ),
      },
    ],
    [handleDelete],
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
        <div className="mb-8">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">API Mocking</h1>
          <p className="mt-1 text-sm text-slate-500">Manage mock responses for APIs</p>
        </div>

        {/* API Selector */}
        <div className="mb-6 rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="flex flex-wrap items-center gap-5">
            <div className="min-w-[250px]">
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Select API</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={selectedApi}
                onChange={(e) => setSelectedApi(e.target.value)}
              >
                <option value="">-- Select API --</option>
                {apis.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name}
                  </option>
                ))}
              </select>
            </div>
            {selectedApi && (
              <div className="flex items-center gap-3">
                <span className="text-sm text-slate-500">Mock Mode:</span>
                <button
                  className={`inline-flex items-center rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors ${
                    mockEnabled
                      ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-200'
                      : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                  }`}
                  onClick={handleToggleMock}
                  disabled={toggling}
                >
                  {toggling ? '...' : mockEnabled ? 'Enabled' : 'Disabled'}
                </button>
              </div>
            )}
          </div>
        </div>

        {selectedApi && (
          <>
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-base font-semibold text-slate-900">Mock Configurations</h3>
              <button
                className="inline-flex items-center rounded-xl bg-purple-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700"
                onClick={() => setModal(true)}
              >
                Add Mock Response
              </button>
            </div>

            <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
              <DataTable data={configs} columns={columns} loading={loading} />
            </div>
          </>
        )}

        {!selectedApi && (
          <div className="rounded-xl bg-white p-12 text-center shadow-sm ring-1 ring-slate-200">
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-purple-50">
              <svg className="h-6 w-6 text-purple-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5" />
              </svg>
            </div>
            <p className="text-sm font-medium text-slate-900">Select an API</p>
            <p className="mt-1 text-sm text-slate-500">Choose an API above to manage its mock configurations.</p>
          </div>
        )}

        <FormModal
          open={modal}
          onClose={() => setModal(false)}
          title="Add Mock Response"
          onSubmit={handleAddMock}
          submitLabel="Add Mock"
          loading={saving}
        >
          <div className="flex flex-col gap-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Path *</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.path}
                onChange={(e) => setForm((f) => ({ ...f, path: e.target.value }))}
                placeholder="/api/resource"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Method</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.method}
                onChange={(e) => setForm((f) => ({ ...f, method: e.target.value }))}
              >
                {METHODS.map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Status Code</label>
              <input
                type="number"
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.statusCode}
                onChange={(e) => setForm((f) => ({ ...f, statusCode: parseInt(e.target.value) || 200 }))}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Response Body</label>
              <textarea
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 font-mono text-xs text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                style={{ minHeight: 100 }}
                value={form.responseBody}
                onChange={(e) => setForm((f) => ({ ...f, responseBody: e.target.value }))}
                placeholder='{"key": "value"}'
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Headers (JSON)</label>
              <textarea
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 font-mono text-xs text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                style={{ minHeight: 60 }}
                value={form.headers}
                onChange={(e) => setForm((f) => ({ ...f, headers: e.target.value }))}
                placeholder='{"Content-Type": "application/json"}'
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">Latency (ms)</label>
                <input
                  type="number"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                  value={form.latencyMs}
                  onChange={(e) => setForm((f) => ({ ...f, latencyMs: parseInt(e.target.value) || 0 }))}
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">Error Rate %</label>
                <input
                  type="number"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                  value={form.errorRate}
                  onChange={(e) => setForm((f) => ({ ...f, errorRate: parseInt(e.target.value) || 0 }))}
                />
              </div>
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
