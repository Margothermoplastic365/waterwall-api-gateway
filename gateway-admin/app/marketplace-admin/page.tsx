'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, FormModal } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface Plugin {
  id: string;
  name: string;
  author: string;
  version: string;
  type: string;
  rating: number;
  installs: number;
  certified: boolean;
  description: string;
  downloadUrl: string;
  [key: string]: unknown;
}

const PLUGIN_TYPES = ['FILTER', 'TRANSFORM', 'AUTH', 'RATE_LIMIT', 'LOGGING', 'ANALYTICS', 'OTHER'] as const;

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

export default function MarketplaceAdminPage() {
  const [plugins, setPlugins] = useState<Plugin[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [modal, setModal] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    name: '',
    description: '',
    author: '',
    version: '',
    type: 'FILTER' as string,
    downloadUrl: '',
  });
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  const fetchPlugins = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await fetch(`${API_URL}/v1/marketplace/plugins`, { headers: authHeaders() });
      if (res.ok) {
        const data = await res.json();
        setPlugins(Array.isArray(data) ? data : data.content || data.data || []);
      }
    } catch {
      setError('Failed to load plugins');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPlugins();
  }, [fetchPlugins]);

  const handlePublish = useCallback(async () => {
    setSaving(true);
    try {
      const res = await fetch(`${API_URL}/v1/marketplace/plugins`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(form),
      });
      if (!res.ok) throw new Error('Publish failed');
      setModal(false);
      setForm({ name: '', description: '', author: '', version: '', type: 'FILTER', downloadUrl: '' });
      fetchPlugins();
    } catch {
      showToast('Failed to publish plugin', 'error');
    } finally {
      setSaving(false);
    }
  }, [form, fetchPlugins]);

  const handleToggleCertify = useCallback(async (plugin: Plugin) => {
    try {
      const res = await fetch(`${API_URL}/v1/marketplace/plugins/${plugin.id}`, {
        method: 'PUT',
        headers: authHeaders(),
        body: JSON.stringify({ ...plugin, certified: !plugin.certified }),
      });
      if (!res.ok) throw new Error('Update failed');
      setPlugins((prev) =>
        prev.map((p) => (p.id === plugin.id ? { ...p, certified: !p.certified } : p)),
      );
    } catch {
      showToast('Failed to update certification', 'error');
    }
  }, []);

  const handleDelete = useCallback(async (id: string) => {
    if (!confirm('Are you sure you want to delete this plugin?')) return;
    try {
      const res = await fetch(`${API_URL}/v1/marketplace/plugins/${id}`, {
        method: 'DELETE',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Delete failed');
      setPlugins((prev) => prev.filter((p) => p.id !== id));
    } catch {
      showToast('Failed to delete plugin', 'error');
    }
  }, []);

  const columns: Column<Plugin>[] = useMemo(
    () => [
      { key: 'name', label: 'Name' },
      { key: 'author', label: 'Author' },
      { key: 'version', label: 'Version' },
      {
        key: 'type',
        label: 'Type',
        render: (row) => <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-700">{row.type}</span>,
      },
      {
        key: 'rating',
        label: 'Rating',
        render: (row) => (
          <div className="flex items-center gap-1.5">
            <svg className="w-4 h-4 text-amber-400" fill="currentColor" viewBox="0 0 20 20"><path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" /></svg>
            <span className="text-sm font-medium text-slate-700">{row.rating?.toFixed(1) || '0.0'}</span>
          </div>
        ),
      },
      {
        key: 'installs',
        label: 'Installs',
        render: (row) => <span className="text-sm text-slate-600">{row.installs?.toLocaleString() || '0'}</span>,
      },
      {
        key: 'certified',
        label: 'Certified',
        render: (row) => (
          <button
            className={`inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
              row.certified
                ? 'bg-green-100 text-green-700 hover:bg-green-200'
                : 'border border-slate-200 bg-white text-slate-500 hover:bg-slate-50'
            }`}
            onClick={() => handleToggleCertify(row)}
          >
            {row.certified ? 'Certified' : 'Uncertified'}
          </button>
        ),
      },
      {
        key: 'id',
        label: 'Actions',
        render: (row) => (
          <button
            className="inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium bg-red-50 text-red-600 hover:bg-red-100 transition-all"
            onClick={() => handleDelete(row.id)}
          >
            Delete
          </button>
        ),
      },
    ],
    [handleToggleCertify, handleDelete],
  );

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
        <div className="max-w-7xl mx-auto space-y-6">
          <div className="space-y-2">
            <div className="h-7 w-40 bg-slate-200 rounded-lg animate-pulse" />
            <div className="h-4 w-80 bg-slate-100 rounded-lg animate-pulse" />
          </div>
          <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3">
            {[...Array(5)].map((_, i) => (
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
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Marketplace</h1>
            <p className="mt-1 text-sm text-slate-500">Manage plugins, certifications, and marketplace listings</p>
          </div>
          <button
            className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200"
            onClick={() => setModal(true)}
          >
            <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
            Publish Plugin
          </button>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <DataTable data={plugins} columns={columns} />
        </div>

        <FormModal
          open={modal}
          onClose={() => setModal(false)}
          title="Publish Plugin"
          onSubmit={handlePublish}
          submitLabel="Publish"
          loading={saving}
        >
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Name <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="Rate Limit Pro"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Description</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                placeholder="Advanced rate limiting plugin"
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-slate-700">Author <span className="text-red-500">*</span></label>
                <input
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                  value={form.author}
                  onChange={(e) => setForm((f) => ({ ...f, author: e.target.value }))}
                  placeholder="Acme Corp"
                />
              </div>
              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-slate-700">Version <span className="text-red-500">*</span></label>
                <input
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                  value={form.version}
                  onChange={(e) => setForm((f) => ({ ...f, version: e.target.value }))}
                  placeholder="1.0.0"
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Type</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={form.type}
                onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}
              >
                {PLUGIN_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Download URL <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={form.downloadUrl}
                onChange={(e) => setForm((f) => ({ ...f, downloadUrl: e.target.value }))}
                placeholder="https://registry.example.com/plugins/rate-limit-pro-1.0.0.jar"
              />
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
