'use client';

import React, { useEffect, useState, useMemo } from 'react';
import { DataTable, StatusBadge, get } from '@gateway/shared-ui';
import type { Column, Pagination } from '@gateway/shared-ui';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('admin_token') || localStorage.getItem('token') || localStorage.getItem('jwt_token');
}
function authHeaders(): Record<string, string> {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
}

interface ApiItem {
  id: string;
  name: string;
  version: string;
  status: string;
  protocol?: string;
  protocolType?: string;
  category?: string;
  sensitivity?: string;
  versionStatus?: string;
  apiGroupId?: string;
  apiGroupName?: string;
  createdAt: string;
  [key: string]: unknown;
}

interface PageResponse<T> { content: T[]; totalElements: number; }

const STATUSES = ['ALL', 'CREATED', 'DRAFT', 'PUBLISHED', 'DEPRECATED', 'RETIRED'] as const;
const SENS_COLORS: Record<string, string> = {
  LOW: 'bg-slate-100 text-slate-600',
  MEDIUM: 'bg-amber-100 text-amber-700',
  HIGH: 'bg-red-100 text-red-700',
  CRITICAL: 'bg-red-200 text-red-800',
};
const VS_COLORS: Record<string, string> = {
  DRAFT: 'bg-slate-100 text-slate-600',
  IN_REVIEW: 'bg-amber-100 text-amber-700',
  ACTIVE: 'bg-emerald-100 text-emerald-700',
  DEPRECATED: 'bg-orange-100 text-orange-700',
  RETIRED: 'bg-red-100 text-red-700',
};

export default function ApisPage() {
  const [apis, setApis] = useState<ApiItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  // Version creation modal
  const [showVersionModal, setShowVersionModal] = useState(false);
  const [versionSourceId, setVersionSourceId] = useState('');
  const [newVersionStr, setNewVersionStr] = useState('');
  const [creating, setCreating] = useState(false);

  // Submit for review
  const [submitting, setSubmitting] = useState<string | null>(null);

  // Delete
  const [deleting, setDeleting] = useState<string | null>(null);

  const pageSize = 20;

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    const params = new URLSearchParams({ page: String(page - 1), size: String(pageSize) });
    if (statusFilter !== 'ALL') params.set('status', statusFilter);
    if (search.trim()) params.set('name', search.trim());

    get<PageResponse<ApiItem> | ApiItem[]>(`/v1/apis?${params}`)
      .then((res) => {
        if (cancelled) return;
        if (Array.isArray(res)) { setApis(res); setTotal(res.length); }
        else { setApis(res.content ?? []); setTotal(res.totalElements ?? 0); }
      })
      .catch((err) => { if (!cancelled) setError(err.message ?? 'Failed to load APIs'); })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [page, statusFilter, search]);

  const handleCreateVersion = async () => {
    if (!versionSourceId || !newVersionStr.trim()) return;
    setCreating(true);
    try {
      const res = await fetch(`${API_URL}/v1/versions`, {
        method: 'POST', headers: authHeaders(),
        body: JSON.stringify({ sourceVersionId: versionSourceId, newVersion: newVersionStr }),
      });
      if (!res.ok) { const b = await res.json().catch(() => null); throw new Error(b?.message || 'Failed'); }
      setShowVersionModal(false);
      setNewVersionStr('');
      setPage(1); // trigger refresh
      setStatusFilter(statusFilter === 'ALL' ? 'ALL' : statusFilter); // force re-fetch
      window.location.reload();
    } catch (err) { setError(err instanceof Error ? err.message : 'Create version failed'); }
    finally { setCreating(false); }
  };

  const handleSubmitForReview = async (apiId: string) => {
    setSubmitting(apiId);
    try {
      const res = await fetch(`${API_URL}/v1/versions/${apiId}/submit`, {
        method: 'POST', headers: authHeaders(),
      });
      if (!res.ok) { const b = await res.json().catch(() => null); throw new Error(b?.message || 'Failed'); }
      window.location.reload();
    } catch (err) { setError(err instanceof Error ? err.message : 'Submit failed'); }
    finally { setSubmitting(null); }
  };

  const handleDelete = async (apiId: string, apiName: string) => {
    if (!confirm(`Delete "${apiName}"? This will retire the API and it will no longer be accessible.`)) return;
    setDeleting(apiId);
    try {
      const res = await fetch(`${API_URL}/v1/apis/${apiId}`, {
        method: 'DELETE', headers: authHeaders(),
      });
      if (!res.ok && res.status !== 204) {
        const b = await res.json().catch(() => null);
        throw new Error(b?.message || `Delete failed (${res.status})`);
      }
      setApis((prev) => prev.filter((a) => a.id !== apiId));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    } finally {
      setDeleting(null);
    }
  };

  // Group APIs by apiGroupName for display
  const grouped = useMemo(() => {
    const groups: Record<string, ApiItem[]> = {};
    apis.forEach((api) => {
      const key = api.apiGroupName || api.name;
      if (!groups[key]) groups[key] = [];
      groups[key].push(api);
    });
    return groups;
  }, [apis]);

  const columns: Column<ApiItem>[] = useMemo(() => [
    { key: 'name', label: 'Name', sortable: true,
      render: (item) => (
        <a href={`/apis/${item.id}`} className="text-blue-600 hover:text-blue-700 font-medium no-underline">
          {item.name}
        </a>
      ),
    },
    { key: 'version', label: 'Version' },
    { key: 'status', label: 'API Status', render: (item) => <StatusBadge status={item.status} size="sm" /> },
    { key: 'versionStatus', label: 'Version Status',
      render: (item) => {
        const vs = item.versionStatus || 'ACTIVE';
        return <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-full ${VS_COLORS[vs] || VS_COLORS.ACTIVE}`}>{vs}</span>;
      },
    },
    { key: 'sensitivity', label: 'Sensitivity',
      render: (item) => {
        const s = item.sensitivity || 'LOW';
        return <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-full ${SENS_COLORS[s] || SENS_COLORS.LOW}`}>{s}</span>;
      },
    },
    { key: 'protocolType', label: 'Protocol', render: (item) => <span>{item.protocolType || item.protocol || 'REST'}</span> },
    { key: 'category', label: 'Category' },
    { key: 'actions', label: 'Actions',
      render: (item) => (
        <div className="flex gap-2">
          {item.status === 'PUBLISHED' && (
            <button
              onClick={(e) => { e.stopPropagation(); setVersionSourceId(item.id); setShowVersionModal(true); }}
              className="px-2 py-1 text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200 rounded hover:bg-blue-100"
            >
              + Version
            </button>
          )}
          {(item.versionStatus === 'DRAFT' || (item.status === 'DRAFT' || item.status === 'CREATED')) && item.versionStatus !== 'IN_REVIEW' && item.status !== 'IN_REVIEW' && item.status !== 'PUBLISHED' && (
            <button
              onClick={(e) => { e.stopPropagation(); handleSubmitForReview(item.id); }}
              disabled={submitting === item.id}
              className="px-2 py-1 text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200 rounded hover:bg-amber-100 disabled:opacity-50"
            >
              {submitting === item.id ? 'Submitting...' : 'Submit Review'}
            </button>
          )}
          {(item.versionStatus === 'IN_REVIEW' || item.status === 'IN_REVIEW') && (
            <span className="px-2 py-1 text-xs font-medium bg-amber-100 text-amber-700 rounded">
              In Review
            </span>
          )}
          {item.status !== 'RETIRED' && (
            <button
              onClick={(e) => { e.stopPropagation(); handleDelete(item.id, item.name); }}
              disabled={deleting === item.id}
              className="px-2 py-1 text-xs font-medium bg-red-50 text-red-700 border border-red-200 rounded hover:bg-red-100 disabled:opacity-50"
            >
              {deleting === item.id ? 'Deleting...' : 'Delete'}
            </button>
          )}
        </div>
      ),
    },
  ], [submitting, deleting]);

  const pagination: Pagination = { page, size: pageSize, total, onPageChange: setPage };

  return (
    <main className="p-6 max-w-7xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">API Management</h1>
          <p className="text-sm text-gray-500 mt-1">Manage APIs, versions, and approval workflows</p>
        </div>
        <div className="flex gap-2">
          <a href="/apis/import" className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 no-underline">
            Import API
          </a>
          <a href="/apis/new" className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 no-underline">
            Create API
          </a>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4 mb-4">
        <input type="text" placeholder="Search by name..." value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(1); }}
          className="px-3 py-2 border border-gray-300 rounded-md text-sm w-64 focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }}
          className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          {STATUSES.map((s) => <option key={s} value={s}>{s === 'ALL' ? 'All Statuses' : s}</option>)}
        </select>
      </div>

      {error && <div className="mb-4 p-3 bg-red-50 text-red-700 rounded-md text-sm">{error}</div>}

      {/* Table */}
      <div className="border border-gray-200 rounded-lg overflow-hidden">
        <DataTable data={apis} columns={columns} pagination={pagination} loading={loading} />
      </div>

      {/* Create Version Modal */}
      {showVersionModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setShowVersionModal(false)}>
          <div className="bg-white rounded-xl w-full max-w-md shadow-2xl" onClick={(e) => e.stopPropagation()}>
            <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center">
              <h2 className="text-lg font-semibold text-gray-900">Create New Version</h2>
              <button onClick={() => setShowVersionModal(false)} className="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
            </div>
            <div className="p-6">
              <p className="text-sm text-gray-500 mb-4">
                This will clone routes and policies from the source API into a new DRAFT version.
              </p>
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-1">New Version *</label>
                <input type="text" value={newVersionStr} onChange={(e) => setNewVersionStr(e.target.value)}
                  placeholder="e.g. v3.0.0" autoFocus
                  className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div className="flex gap-2 justify-end">
                <button onClick={() => setShowVersionModal(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button onClick={handleCreateVersion} disabled={creating || !newVersionStr.trim()}
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {creating ? 'Creating...' : 'Create Version'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
