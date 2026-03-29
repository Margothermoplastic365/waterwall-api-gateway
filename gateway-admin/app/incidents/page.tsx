'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, FormModal } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface Incident {
  id: string;
  severity: string;
  title: string;
  description: string;
  status: string;
  affectedApis: string[];
  createdAt: string;
  [key: string]: unknown;
}

interface ServiceStatus {
  name: string;
  status: string;
}

const SEVERITIES = ['P1', 'P2', 'P3', 'P4'] as const;

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

export default function IncidentsPage() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [serviceStatuses, setServiceStatuses] = useState<ServiceStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [modal, setModal] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    severity: 'P3' as string,
    title: '',
    description: '',
    affectedApis: '',
  });
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [incRes, statusRes] = await Promise.all([
        fetch(`${API_URL}/v1/incidents`, { headers: authHeaders() }),
        fetch(`${API_URL}/v1/incidents/status-page`, { headers: authHeaders() }).catch(() => null),
      ]);
      if (incRes.ok) {
        const data = await incRes.json();
        setIncidents(Array.isArray(data) ? data : data.content || data.data || []);
      }
      if (statusRes?.ok) {
        const data = await statusRes.json();
        setServiceStatuses(Array.isArray(data) ? data : data.services || []);
      }
    } catch {
      setError('Failed to load incidents');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleCreate = useCallback(async () => {
    setSaving(true);
    try {
      const res = await fetch(`${API_URL}/v1/incidents`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({
          severity: form.severity,
          title: form.title,
          description: form.description,
          affectedApis: form.affectedApis.split(',').map((s) => s.trim()).filter(Boolean),
        }),
      });
      if (!res.ok) throw new Error('Create failed');
      setModal(false);
      setForm({ severity: 'P3', title: '', description: '', affectedApis: '' });
      fetchData();
    } catch {
      showToast('Failed to create incident', 'error');
    } finally {
      setSaving(false);
    }
  }, [form, fetchData]);

  const handleResolve = useCallback(async (id: string) => {
    try {
      const res = await fetch(`${API_URL}/v1/incidents/${id}/resolve`, {
        method: 'POST',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Resolve failed');
      setIncidents((prev) =>
        prev.map((inc) => (inc.id === id ? { ...inc, status: 'RESOLVED' } : inc)),
      );
    } catch {
      showToast('Failed to resolve incident', 'error');
    }
  }, []);

  const handleUpdateServiceStatus = useCallback(async (name: string, status: string) => {
    try {
      const res = await fetch(`${API_URL}/v1/incidents/status-page/${name}`, {
        method: 'PUT',
        headers: authHeaders(),
        body: JSON.stringify({ status }),
      });
      if (!res.ok) throw new Error('Update failed');
      setServiceStatuses((prev) =>
        prev.map((s) => (s.name === name ? { ...s, status } : s)),
      );
    } catch {
      showToast('Failed to update service status', 'error');
    }
  }, []);

  const severityBadge = (severity: string) => {
    const colors: Record<string, string> = {
      P1: 'bg-red-100 text-red-700',
      P2: 'bg-amber-100 text-amber-700',
      P3: 'bg-blue-100 text-blue-700',
      P4: 'bg-slate-100 text-slate-600',
    };
    return <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${colors[severity] || 'bg-slate-100 text-slate-600'}`}>{severity}</span>;
  };

  const columns: Column<Incident>[] = useMemo(
    () => [
      { key: 'severity', label: 'Severity', render: (row) => severityBadge(row.severity) },
      { key: 'title', label: 'Title' },
      {
        key: 'status',
        label: 'Status',
        render: (row) => (
          <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${row.status === 'RESOLVED' ? 'bg-green-100 text-green-700' : row.status === 'INVESTIGATING' ? 'bg-amber-100 text-amber-700' : 'bg-red-100 text-red-700'}`}>
            {row.status}
          </span>
        ),
      },
      {
        key: 'affectedApis',
        label: 'Affected APIs',
        render: (row) => <span className="text-sm text-slate-600">{(row.affectedApis || []).join(', ') || '-'}</span>,
      },
      {
        key: 'createdAt',
        label: 'Created',
        render: (row) => <span className="text-sm text-slate-500">{new Date(row.createdAt).toLocaleString()}</span>,
      },
      {
        key: 'id',
        label: 'Actions',
        render: (row) =>
          row.status !== 'RESOLVED' ? (
            <button
              className="inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium bg-green-600 text-white hover:bg-green-700 transition-all"
              onClick={() => handleResolve(row.id)}
            >
              Resolve
            </button>
          ) : null,
      },
    ],
    [handleResolve],
  );

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
        <div className="max-w-7xl mx-auto space-y-6">
          <div className="space-y-2">
            <div className="h-7 w-36 bg-slate-200 rounded-lg animate-pulse" />
            <div className="h-4 w-80 bg-slate-100 rounded-lg animate-pulse" />
          </div>
          <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-12 bg-slate-50 rounded-lg animate-pulse" />
            ))}
          </div>
          <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3">
            <div className="h-5 w-32 bg-slate-200 rounded animate-pulse" />
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-10 bg-slate-50 rounded-lg animate-pulse" />
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
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Incidents</h1>
            <p className="mt-1 text-sm text-slate-500">Incident management and status page administration</p>
          </div>
          <button
            className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200"
            onClick={() => setModal(true)}
          >
            <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
            Create Incident
          </button>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <DataTable data={incidents} columns={columns} />
        </div>

        {/* Status Page Management */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-base font-semibold text-slate-900 mb-1">Status Page</h3>
          <p className="text-sm text-slate-500 mb-5">Manage public service status indicators</p>
          {serviceStatuses.length > 0 ? (
            <div className="overflow-x-auto rounded-lg border border-slate-200">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200">
                    <th className="text-left px-4 py-3 font-semibold text-slate-600 text-xs uppercase tracking-wider">Service</th>
                    <th className="text-left px-4 py-3 font-semibold text-slate-600 text-xs uppercase tracking-wider">Status</th>
                    <th className="text-left px-4 py-3 font-semibold text-slate-600 text-xs uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {serviceStatuses.map((svc) => (
                    <tr key={svc.name} className="hover:bg-slate-50/50 transition-colors">
                      <td className="px-4 py-3 font-semibold text-slate-900">{svc.name}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                            svc.status === 'OPERATIONAL'
                              ? 'bg-green-100 text-green-700'
                              : svc.status === 'DEGRADED'
                              ? 'bg-amber-100 text-amber-700'
                              : 'bg-red-100 text-red-700'
                          }`}
                        >
                          {svc.status}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex gap-2">
                          {['OPERATIONAL', 'DEGRADED', 'OUTAGE'].map((s) => (
                            <button
                              key={s}
                              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                                svc.status === s
                                  ? 'bg-purple-600 text-white shadow-sm'
                                  : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                              }`}
                              onClick={() => handleUpdateServiceStatus(svc.name, s)}
                            >
                              {s}
                            </button>
                          ))}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center mb-3">
                <svg className="w-6 h-6 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
              </div>
              <p className="text-sm text-slate-500">No services configured on the status page.</p>
            </div>
          )}
        </div>

        {/* Create Incident Modal */}
        <FormModal
          open={modal}
          onClose={() => setModal(false)}
          title="Create Incident"
          onSubmit={handleCreate}
          submitLabel="Create Incident"
          loading={saving}
        >
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Severity</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={form.severity}
                onChange={(e) => setForm((f) => ({ ...f, severity: e.target.value }))}
              >
                {SEVERITIES.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Title <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={form.title}
                onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                placeholder="API Gateway High Latency"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Description</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                placeholder="Describe the incident..."
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Affected APIs (comma-separated)</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={form.affectedApis}
                onChange={(e) => setForm((f) => ({ ...f, affectedApis: e.target.value }))}
                placeholder="users-api, orders-api"
              />
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
