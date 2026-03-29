'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface Migration {
  id: string;
  apiId: string;
  apiName: string;
  sourceEnvironment: string;
  targetEnvironment: string;
  status: string;
  initiatedAt: string;
  initiatedBy: string;
}

interface ApiOption {
  id: string;
  name: string;
}

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

function getStatusBadgeClass(status: string): string {
  switch (status?.toUpperCase()) {
    case 'INITIATED':
      return 'bg-blue-100 text-blue-700';
    case 'COMPLETED':
      return 'bg-green-100 text-green-700';
    case 'FAILED':
      return 'bg-red-100 text-red-700';
    case 'ROLLED_BACK':
      return 'bg-amber-100 text-amber-700';
    default:
      return 'bg-slate-100 text-slate-600';
  }
}

export default function MigrationsPage() {
  const [migrations, setMigrations] = useState<Migration[]>([]);
  const [apis, setApis] = useState<ApiOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  /* New migration modal */
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ apiId: '', sourceEnv: 'DEV', targetEnv: 'UAT' });
  const [formLoading, setFormLoading] = useState(false);
  const [formError, setFormError] = useState('');

  const [rollbackLoading, setRollbackLoading] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  const envOrder = ['DEV', 'UAT', 'PRE-PROD', 'PROD'];

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [migRes, apiRes] = await Promise.all([
        fetch(`${API_URL}/v1/migrations`, { headers: authHeaders() }),
        fetch(`${API_URL}/v1/apis`, { headers: authHeaders() }),
      ]);
      if (migRes.ok) {
        const data = await migRes.json();
        setMigrations(Array.isArray(data) ? data : data.content || data.data || []);
      }
      if (apiRes.ok) {
        const data = await apiRes.json();
        const list = Array.isArray(data) ? data : data.content || data.data || [];
        setApis(list.map((a: ApiOption) => ({ id: a.id, name: a.name })));
      }
    } catch {
      setError('Failed to load migrations');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormLoading(true);
    setFormError('');
    try {
      const res = await fetch(`${API_URL}/v1/migrations`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({
          apiId: form.apiId,
          sourceEnvironment: form.sourceEnv,
          targetEnvironment: form.targetEnv,
        }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => null);
        throw new Error(data?.message || 'Failed to create migration');
      }
      setShowModal(false);
      setForm({ apiId: '', sourceEnv: 'DEV', targetEnv: 'UAT' });
      fetchData();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to create migration');
    } finally {
      setFormLoading(false);
    }
  };

  const handleRollback = async (id: string) => {
    setRollbackLoading(id);
    try {
      const res = await fetch(`${API_URL}/v1/migrations/${id}/rollback`, {
        method: 'POST',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Rollback failed');
      fetchData();
    } catch {
      showToast('Rollback failed', 'error');
    } finally {
      setRollbackLoading(null);
    }
  };

  const envColors: Record<string, string> = {
    DEV: 'bg-green-100 text-green-700 border-green-200',
    UAT: 'bg-blue-100 text-blue-700 border-blue-200',
    'PRE-PROD': 'bg-amber-100 text-amber-700 border-amber-200',
    PROD: 'bg-red-100 text-red-700 border-red-200',
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
        <div className="max-w-7xl mx-auto space-y-6">
          <div className="space-y-2">
            <div className="h-7 w-52 bg-slate-200 rounded-lg animate-pulse" />
            <div className="h-4 w-72 bg-slate-100 rounded-lg animate-pulse" />
          </div>
          <div className="bg-white rounded-xl border border-slate-200 p-6">
            <div className="h-5 w-32 bg-slate-200 rounded animate-pulse mb-4" />
            <div className="flex justify-center gap-6 py-6">
              {[...Array(4)].map((_, i) => (
                <div key={i} className="h-12 w-24 bg-slate-100 rounded-xl animate-pulse" />
              ))}
            </div>
          </div>
          <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-12 bg-slate-50 rounded-lg animate-pulse" />
            ))}
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
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Migration Pipeline</h1>
            <p className="mt-1 text-sm text-slate-500">Manage API promotions across environments</p>
          </div>
          <button
            className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200"
            onClick={() => setShowModal(true)}
          >
            <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
            New Migration
          </button>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        {/* Visual Pipeline */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-base font-semibold text-slate-900 mb-5">Pipeline Flow</h3>
          <div className="flex items-center justify-center gap-4 py-4">
            {envOrder.map((env, i) => (
              <div key={env} className="flex items-center gap-4">
                <div className={`px-6 py-3 rounded-xl border font-bold text-sm text-center min-w-[100px] ${envColors[env] || 'bg-slate-100 text-slate-600 border-slate-200'}`}>
                  {env}
                </div>
                {i < envOrder.length - 1 && (
                  <svg className="w-5 h-5 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" /></svg>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Migrations Table */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-6 pt-5 pb-3">
            <h3 className="text-base font-semibold text-slate-900">Recent Migrations</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-t border-b border-slate-200 bg-slate-50/50">
                  <th className="text-left px-4 py-3 font-semibold text-slate-600 text-xs uppercase tracking-wider">API Name</th>
                  <th className="text-left px-4 py-3 font-semibold text-slate-600 text-xs uppercase tracking-wider">Source</th>
                  <th className="text-left px-4 py-3 font-semibold text-slate-600 text-xs uppercase tracking-wider">Target</th>
                  <th className="text-left px-4 py-3 font-semibold text-slate-600 text-xs uppercase tracking-wider">Status</th>
                  <th className="text-left px-4 py-3 font-semibold text-slate-600 text-xs uppercase tracking-wider">Initiated</th>
                  <th className="text-left px-4 py-3 font-semibold text-slate-600 text-xs uppercase tracking-wider">By</th>
                  <th className="text-left px-4 py-3 font-semibold text-slate-600 text-xs uppercase tracking-wider">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {migrations.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center py-12">
                      <div className="flex flex-col items-center">
                        <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center mb-3">
                          <svg className="w-6 h-6 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7.5 21L3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" /></svg>
                        </div>
                        <p className="text-sm text-slate-500">No migrations found</p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  migrations.map((m) => (
                    <tr key={m.id} className="hover:bg-slate-50/50 transition-colors">
                      <td className="px-4 py-3 font-semibold text-slate-900">{m.apiName}</td>
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-600">{m.sourceEnvironment}</span>
                      </td>
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-600">{m.targetEnvironment}</span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${getStatusBadgeClass(m.status)}`}>
                          {m.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-slate-500 text-sm">
                        {new Date(m.initiatedAt).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 text-sm text-slate-600">{m.initiatedBy}</td>
                      <td className="px-4 py-3">
                        {m.status === 'COMPLETED' && (
                          <button
                            className="inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium bg-amber-50 text-amber-700 hover:bg-amber-100 border border-amber-200 transition-all disabled:opacity-50"
                            disabled={rollbackLoading === m.id}
                            onClick={() => handleRollback(m.id)}
                          >
                            {rollbackLoading === m.id ? (
                              <div className="w-3 h-3 border-2 border-amber-300 border-t-amber-600 rounded-full animate-spin mr-1.5" />
                            ) : null}
                            {rollbackLoading === m.id ? '...' : 'Rollback'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* New Migration Modal */}
        {showModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => setShowModal(false)}>
            <div className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 border border-slate-200" onClick={(e) => e.stopPropagation()}>
              <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
                <h3 className="text-lg font-semibold text-slate-900">New Migration</h3>
                <button className="text-slate-400 hover:text-slate-600 transition-colors text-2xl leading-none" onClick={() => setShowModal(false)}>
                  &times;
                </button>
              </div>

              <div className="p-6">
                {formError && (
                  <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700 mb-4">{formError}</div>
                )}

                <form onSubmit={handleCreate} className="space-y-4">
                  <div className="space-y-1.5">
                    <label className="block text-sm font-medium text-slate-700">API</label>
                    <select
                      className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                      value={form.apiId}
                      onChange={(e) => setForm({ ...form, apiId: e.target.value })}
                      required
                    >
                      <option value="">Select API...</option>
                      {apis.map((a) => (
                        <option key={a.id} value={a.id}>{a.name}</option>
                      ))}
                    </select>
                  </div>

                  <div className="space-y-1.5">
                    <label className="block text-sm font-medium text-slate-700">Source Environment</label>
                    <select
                      className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                      value={form.sourceEnv}
                      onChange={(e) => setForm({ ...form, sourceEnv: e.target.value })}
                      required
                    >
                      {envOrder.map((env) => (
                        <option key={env} value={env}>{env}</option>
                      ))}
                    </select>
                  </div>

                  <div className="space-y-1.5">
                    <label className="block text-sm font-medium text-slate-700">Target Environment</label>
                    <select
                      className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                      value={form.targetEnv}
                      onChange={(e) => setForm({ ...form, targetEnv: e.target.value })}
                      required
                    >
                      {envOrder.map((env) => (
                        <option key={env} value={env}>{env}</option>
                      ))}
                    </select>
                  </div>

                  <div className="flex gap-3 justify-end pt-4 border-t border-slate-100">
                    <button
                      type="button"
                      className="px-4 py-2.5 rounded-lg text-sm font-medium border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 transition-all"
                      onClick={() => setShowModal(false)}
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200 disabled:opacity-50"
                      disabled={formLoading}
                    >
                      {formLoading && <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2" />}
                      {formLoading ? 'Creating...' : 'Start Migration'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
