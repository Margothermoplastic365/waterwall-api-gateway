'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, StatusBadge, FormModal } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface Gateway {
  id: string;
  name: string;
  type: string;
  status: string;
  lastSync: string;
  apisCount: number;
  [key: string]: unknown;
}

interface Workspace {
  id: string;
  name: string;
  orgId: string;
  orgName: string;
  gatewayCount: number;
  [key: string]: unknown;
}

interface CatalogApi {
  id: string;
  name: string;
  gatewayName: string;
  gatewayType: string;
  version: string;
  [key: string]: unknown;
}

const GATEWAY_TYPES = ['AWS_API_GW', 'AZURE_APIM', 'KONG', 'APIGEE'] as const;

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

export default function FederationPage() {
  const [gateways, setGateways] = useState<Gateway[]>([]);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [catalog, setCatalog] = useState<CatalogApi[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [gwModal, setGwModal] = useState(false);
  const [gwSaving, setGwSaving] = useState(false);
  const [gwForm, setGwForm] = useState({ name: '', type: 'AWS_API_GW', apiUrl: '', apiKey: '' });

  const [wsModal, setWsModal] = useState(false);
  const [wsSaving, setWsSaving] = useState(false);
  const [wsForm, setWsForm] = useState({ name: '', orgId: '' });
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [gwRes, wsRes, catRes] = await Promise.all([
        fetch(`${API_URL}/v1/federation/gateways`, { headers: authHeaders() }),
        fetch(`${API_URL}/v1/federation/workspaces`, { headers: authHeaders() }),
        fetch(`${API_URL}/v1/federation/catalog`, { headers: authHeaders() }),
      ]);
      if (gwRes.ok) {
        const data = await gwRes.json();
        setGateways(Array.isArray(data) ? data : data.content || data.data || []);
      }
      if (wsRes.ok) {
        const data = await wsRes.json();
        setWorkspaces(Array.isArray(data) ? data : data.content || data.data || []);
      }
      if (catRes.ok) {
        const data = await catRes.json();
        setCatalog(Array.isArray(data) ? data : data.content || data.data || []);
      }
    } catch {
      setError('Failed to load federation data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleSync = useCallback(async (id: string) => {
    try {
      const res = await fetch(`${API_URL}/v1/federation/gateways/${id}/sync`, {
        method: 'POST',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Sync failed');
      fetchData();
    } catch {
      showToast('Failed to sync gateway', 'error');
    }
  }, [fetchData]);

  const handleCreateGateway = useCallback(async () => {
    setGwSaving(true);
    try {
      const res = await fetch(`${API_URL}/v1/federation/gateways`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(gwForm),
      });
      if (!res.ok) throw new Error('Create failed');
      setGwModal(false);
      setGwForm({ name: '', type: 'AWS_API_GW', apiUrl: '', apiKey: '' });
      fetchData();
    } catch {
      showToast('Failed to register gateway', 'error');
    } finally {
      setGwSaving(false);
    }
  }, [gwForm, fetchData]);

  const handleCreateWorkspace = useCallback(async () => {
    setWsSaving(true);
    try {
      const res = await fetch(`${API_URL}/v1/federation/workspaces`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(wsForm),
      });
      if (!res.ok) throw new Error('Create failed');
      setWsModal(false);
      setWsForm({ name: '', orgId: '' });
      fetchData();
    } catch {
      showToast('Failed to create workspace', 'error');
    } finally {
      setWsSaving(false);
    }
  }, [wsForm, fetchData]);

  const typeBadge = (type: string) => {
    const colors: Record<string, string> = {
      AWS_API_GW: 'bg-amber-100 text-amber-700',
      AZURE_APIM: 'bg-blue-100 text-blue-700',
      KONG: 'bg-green-100 text-green-700',
      APIGEE: 'bg-purple-100 text-purple-700',
    };
    return <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${colors[type] || 'bg-slate-100 text-slate-600'}`}>{type}</span>;
  };

  const gwColumns: Column<Gateway>[] = useMemo(
    () => [
      { key: 'name', label: 'Name' },
      { key: 'type', label: 'Type', render: (row) => typeBadge(row.type) },
      {
        key: 'status',
        label: 'Status',
        render: (row) => <StatusBadge status={row.status} size="sm" />,
      },
      {
        key: 'lastSync',
        label: 'Last Sync',
        render: (row) => <span className="text-sm text-slate-500">{row.lastSync ? new Date(row.lastSync).toLocaleString() : 'Never'}</span>,
      },
      { key: 'apisCount', label: 'APIs Count' },
      {
        key: 'id',
        label: 'Actions',
        render: (row) => (
          <button
            className="inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium border border-slate-200 bg-white text-slate-600 hover:bg-purple-50 hover:text-purple-600 hover:border-purple-200 transition-all"
            onClick={() => handleSync(row.id)}
          >
            Sync
          </button>
        ),
      },
    ],
    [handleSync],
  );

  const wsColumns: Column<Workspace>[] = useMemo(
    () => [
      { key: 'name', label: 'Name' },
      { key: 'orgName', label: 'Organization' },
      { key: 'gatewayCount', label: 'Gateways' },
    ],
    [],
  );

  const catColumns: Column<CatalogApi>[] = useMemo(
    () => [
      { key: 'name', label: 'API Name' },
      { key: 'gatewayName', label: 'Gateway' },
      { key: 'gatewayType', label: 'Type', render: (row) => typeBadge(row.gatewayType) },
      { key: 'version', label: 'Version' },
    ],
    [],
  );

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
        <div className="max-w-7xl mx-auto space-y-6">
          <div className="space-y-2">
            <div className="h-7 w-40 bg-slate-200 rounded-lg animate-pulse" />
            <div className="h-4 w-96 bg-slate-100 rounded-lg animate-pulse" />
          </div>
          {[...Array(3)].map((_, i) => (
            <div key={i} className="bg-white rounded-xl border border-slate-200 p-6 space-y-3">
              <div className="h-5 w-44 bg-slate-200 rounded animate-pulse" />
              {[...Array(3)].map((_, j) => (
                <div key={j} className="h-10 bg-slate-50 rounded-lg animate-pulse" />
              ))}
            </div>
          ))}
        </div>
      </div>
    );
  }

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
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Federation</h1>
            <p className="mt-1 text-sm text-slate-500">Manage federated gateways, workspaces, and unified API catalog</p>
          </div>
          <div className="flex gap-3">
            <button
              className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 shadow-sm transition-all duration-200"
              onClick={() => setWsModal(true)}
            >
              Create Workspace
            </button>
            <button
              className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200"
              onClick={() => setGwModal(true)}
            >
              <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
              Register Gateway
            </button>
          </div>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        {/* Federated Gateways */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-6 pt-5 pb-3">
            <h3 className="text-base font-semibold text-slate-900">Federated Gateways</h3>
            <p className="text-sm text-slate-500 mt-1">External gateway instances synced into this platform</p>
          </div>
          <DataTable data={gateways} columns={gwColumns} />
        </div>

        {/* Workspaces */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-6 pt-5 pb-3">
            <h3 className="text-base font-semibold text-slate-900">Workspaces</h3>
            <p className="text-sm text-slate-500 mt-1">Isolated workspaces for organizational separation</p>
          </div>
          <DataTable data={workspaces} columns={wsColumns} />
        </div>

        {/* Federated Catalog */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-6 pt-5 pb-3">
            <h3 className="text-base font-semibold text-slate-900">Federated Catalog</h3>
            <p className="text-sm text-slate-500 mt-1">Combined API list from all federated gateways</p>
          </div>
          <DataTable data={catalog} columns={catColumns} />
        </div>

        {/* Register Gateway Modal */}
        <FormModal
          open={gwModal}
          onClose={() => setGwModal(false)}
          title="Register Gateway"
          onSubmit={handleCreateGateway}
          submitLabel="Register"
          loading={gwSaving}
        >
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Name <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={gwForm.name}
                onChange={(e) => setGwForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="Production Kong Gateway"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Type</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={gwForm.type}
                onChange={(e) => setGwForm((f) => ({ ...f, type: e.target.value }))}
              >
                {GATEWAY_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">API URL <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={gwForm.apiUrl}
                onChange={(e) => setGwForm((f) => ({ ...f, apiUrl: e.target.value }))}
                placeholder="https://gateway.example.com/admin"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">API Key</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                type="password"
                value={gwForm.apiKey}
                onChange={(e) => setGwForm((f) => ({ ...f, apiKey: e.target.value }))}
                placeholder="Enter API key"
              />
            </div>
          </div>
        </FormModal>

        {/* Create Workspace Modal */}
        <FormModal
          open={wsModal}
          onClose={() => setWsModal(false)}
          title="Create Workspace"
          onSubmit={handleCreateWorkspace}
          submitLabel="Create"
          loading={wsSaving}
        >
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Name <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={wsForm.name}
                onChange={(e) => setWsForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="Engineering Workspace"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Organization ID <span className="text-red-500">*</span></label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={wsForm.orgId}
                onChange={(e) => setWsForm((f) => ({ ...f, orgId: e.target.value }))}
                placeholder="org-123"
              />
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
