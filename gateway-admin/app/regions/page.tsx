'use client';

import { useEffect, useState, useCallback } from 'react';
import { FormModal } from '@gateway/shared-ui';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface Region {
  id: string;
  name: string;
  slug: string;
  endpointUrl: string;
  dataResidencyZone: string;
  status: string;
  geoRouting: string[];
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

export default function RegionsPage() {
  const [regions, setRegions] = useState<Region[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [createModal, setCreateModal] = useState(false);
  const [createSaving, setCreateSaving] = useState(false);
  const [createForm, setCreateForm] = useState({
    name: '',
    slug: '',
    endpointUrl: '',
    dataResidencyZone: '',
  });

  const [deployModal, setDeployModal] = useState(false);
  const [deploySaving, setDeploySaving] = useState(false);
  const [deployForm, setDeployForm] = useState({ apiId: '', regionId: '' });
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  const fetchRegions = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await fetch(`${API_URL}/v1/regions`, { headers: authHeaders() });
      if (res.ok) {
        const data = await res.json();
        setRegions(Array.isArray(data) ? data : data.content || data.data || []);
      }
    } catch {
      setError('Failed to load regions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRegions();
  }, [fetchRegions]);

  const handleCreate = useCallback(async () => {
    setCreateSaving(true);
    try {
      const res = await fetch(`${API_URL}/v1/regions`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(createForm),
      });
      if (!res.ok) throw new Error('Create failed');
      setCreateModal(false);
      setCreateForm({ name: '', slug: '', endpointUrl: '', dataResidencyZone: '' });
      fetchRegions();
    } catch {
      showToast('Failed to create region', 'error');
    } finally {
      setCreateSaving(false);
    }
  }, [createForm, fetchRegions]);

  const handleDeploy = useCallback(async () => {
    setDeploySaving(true);
    try {
      const res = await fetch(`${API_URL}/v1/regions/${deployForm.regionId}/deploy`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ apiId: deployForm.apiId }),
      });
      if (!res.ok) throw new Error('Deploy failed');
      setDeployModal(false);
      setDeployForm({ apiId: '', regionId: '' });
      fetchRegions();
    } catch {
      showToast('Failed to deploy API to region', 'error');
    } finally {
      setDeploySaving(false);
    }
  }, [deployForm, fetchRegions]);

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
        <div className="max-w-7xl mx-auto space-y-6">
          <div className="space-y-2">
            <div className="h-7 w-40 bg-slate-200 rounded-lg animate-pulse" />
            <div className="h-4 w-80 bg-slate-100 rounded-lg animate-pulse" />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="bg-white rounded-xl border border-slate-200 p-5 space-y-4">
                <div className="flex justify-between">
                  <div className="h-5 w-28 bg-slate-200 rounded animate-pulse" />
                  <div className="h-5 w-16 bg-slate-100 rounded-full animate-pulse" />
                </div>
                <div className="space-y-3">
                  {[...Array(4)].map((_, j) => (
                    <div key={j} className="h-4 bg-slate-50 rounded animate-pulse" />
                  ))}
                </div>
              </div>
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
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Multi-Region</h1>
            <p className="mt-1 text-sm text-slate-500">Manage regions, data residency, and geo-routing configuration</p>
          </div>
          <div className="flex gap-3">
            <button
              className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 shadow-sm transition-all duration-200"
              onClick={() => setDeployModal(true)}
            >
              Deploy API to Region
            </button>
            <button
              className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200"
              onClick={() => setCreateModal(true)}
            >
              <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
              Create Region
            </button>
          </div>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        {/* Region Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {regions.map((region) => {
            const isActive = region.status?.toUpperCase() === 'ACTIVE';
            return (
              <div key={region.id} className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 hover:shadow-md transition-shadow duration-200">
                <div className="flex items-center justify-between mb-4">
                  <span className="text-base font-semibold text-slate-900">{region.name}</span>
                  <span
                    className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                      isActive ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
                    }`}
                  >
                    {region.status}
                  </span>
                </div>
                <div className="space-y-3">
                  <div>
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Slug</p>
                    <p className="mt-0.5 text-sm font-mono text-slate-700">{region.slug}</p>
                  </div>
                  <div>
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Endpoint URL</p>
                    <p className="mt-0.5 text-sm text-slate-700 truncate">{region.endpointUrl}</p>
                  </div>
                  <div>
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Data Residency</p>
                    <div className="mt-1">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-700">{region.dataResidencyZone}</span>
                    </div>
                  </div>
                  <div>
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Geo-Routing</p>
                    <p className="mt-0.5 text-sm text-slate-600">
                      {region.geoRouting && region.geoRouting.length > 0
                        ? region.geoRouting.join(', ')
                        : 'Not configured'}
                    </p>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {regions.length === 0 && !error && (
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center mb-3">
                <svg className="w-6 h-6 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 21a9.004 9.004 0 008.716-6.747M12 21a9.004 9.004 0 01-8.716-6.747M12 21c2.485 0 4.5-4.03 4.5-9S14.485 3 12 3m0 18c-2.485 0-4.5-4.03-4.5-9S9.515 3 12 3m0 0a8.997 8.997 0 017.843 4.582M12 3a8.997 8.997 0 00-7.843 4.582m15.686 0A11.953 11.953 0 0112 10.5c-2.998 0-5.74-1.1-7.843-2.918m15.686 0A8.959 8.959 0 0121 12c0 .778-.099 1.533-.284 2.253m0 0A17.919 17.919 0 0112 16.5c-3.162 0-6.133-.815-8.716-2.247m0 0A9.015 9.015 0 013 12c0-1.605.42-3.113 1.157-4.418" /></svg>
              </div>
              <p className="text-sm text-slate-500">No regions configured. Create a region to get started with multi-region deployment.</p>
            </div>
          </div>
        )}

        {/* Create Region Modal */}
        <FormModal
          open={createModal}
          onClose={() => setCreateModal(false)}
          title="Create Region"
          onSubmit={handleCreate}
          submitLabel="Create"
          loading={createSaving}
        >
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Name <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={createForm.name}
                onChange={(e) => setCreateForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="US East"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Slug <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={createForm.slug}
                onChange={(e) => setCreateForm((f) => ({ ...f, slug: e.target.value }))}
                placeholder="us-east-1"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Endpoint URL <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={createForm.endpointUrl}
                onChange={(e) => setCreateForm((f) => ({ ...f, endpointUrl: e.target.value }))}
                placeholder="https://us-east.gateway.example.com"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Data Residency Zone <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={createForm.dataResidencyZone}
                onChange={(e) => setCreateForm((f) => ({ ...f, dataResidencyZone: e.target.value }))}
                placeholder="US, EU, APAC"
              />
            </div>
          </div>
        </FormModal>

        {/* Deploy API to Region Modal */}
        <FormModal
          open={deployModal}
          onClose={() => setDeployModal(false)}
          title="Deploy API to Region"
          onSubmit={handleDeploy}
          submitLabel="Deploy"
          loading={deploySaving}
        >
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">API ID <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={deployForm.apiId}
                onChange={(e) => setDeployForm((f) => ({ ...f, apiId: e.target.value }))}
                placeholder="Enter API ID"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Region</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={deployForm.regionId}
                onChange={(e) => setDeployForm((f) => ({ ...f, regionId: e.target.value }))}
              >
                <option value="">Select region...</option>
                {regions.map((r) => (
                  <option key={r.id} value={r.id}>{r.name}</option>
                ))}
              </select>
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
