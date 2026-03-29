'use client';

import React, { useEffect, useState, useMemo, useCallback } from 'react';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';
const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

function getToken(): string {
  if (typeof window === 'undefined') return '';
  return localStorage.getItem('admin_token') || localStorage.getItem('token') || '';
}
function authHeaders(): Record<string, string> {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
}

interface Subscription {
  id: string;
  applicationId: string;
  apiId: string;
  apiName?: string;
  planId: string;
  planName?: string;
  status: string;
  approvedAt?: string;
  createdAt: string;
}

interface AppInfo { id: string; name: string; userId?: string; }
interface UserInfo { id: string; email: string; displayName?: string; }

const STATUS_OPTIONS = ['ALL', 'PENDING', 'APPROVED', 'ACTIVE', 'REJECTED', 'SUSPENDED'] as const;

const STATUS_CONFIG: Record<string, { dot: string; badge: string; label: string }> = {
  ACTIVE:    { dot: 'bg-emerald-500', badge: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', label: 'Active' },
  APPROVED:  { dot: 'bg-blue-500',    badge: 'bg-blue-50 text-blue-700 ring-blue-600/20',       label: 'Approved' },
  PENDING:   { dot: 'bg-amber-500',   badge: 'bg-amber-50 text-amber-700 ring-amber-600/20',    label: 'Pending' },
  REJECTED:  { dot: 'bg-red-500',     badge: 'bg-red-50 text-red-700 ring-red-600/20',          label: 'Rejected' },
  SUSPENDED: { dot: 'bg-slate-400',   badge: 'bg-slate-50 text-slate-600 ring-slate-500/20',    label: 'Suspended' },
};

export default function SubscriptionsPage() {
  const [subs, setSubs] = useState<Subscription[]>([]);
  const [apps, setApps] = useState<Record<string, AppInfo>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [apiFilter, setApiFilter] = useState('ALL');
  const [apiQuery, setApiQuery] = useState('');
  const [apiDropdownOpen, setApiDropdownOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState('');

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [subsRes, appsRes] = await Promise.all([
        fetch(`${API_URL}/v1/subscriptions`, { headers: authHeaders() }),
        fetch(`${IDENTITY_URL}/v1/applications?page=0&size=500`, { headers: authHeaders() }).catch(() => null),
      ]);

      if (!subsRes.ok) throw new Error('Failed to load subscriptions');
      const subsData = await subsRes.json();
      const allSubs: Subscription[] = Array.isArray(subsData) ? subsData : subsData.content || [];
      setSubs(allSubs);

      // Build app lookup
      if (appsRes?.ok) {
        const appsData = await appsRes.json();
        const appList: AppInfo[] = Array.isArray(appsData) ? appsData : appsData.content || [];
        const lookup: Record<string, AppInfo> = {};
        appList.forEach(a => { lookup[a.id] = a; });
        setApps(lookup);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  // Auto-dismiss success
  useEffect(() => {
    if (!successMsg) return;
    const t = setTimeout(() => setSuccessMsg(''), 4000);
    return () => clearTimeout(t);
  }, [successMsg]);

  const handleAction = async (id: string, action: 'approve' | 'reject' | 'suspend') => {
    const labels: Record<string, string> = { approve: 'Approve', reject: 'Reject', suspend: 'Suspend' };
    if (!confirm(`${labels[action]} this subscription?`)) return;
    setActionLoading(id);
    setError('');
    try {
      const endpoint = action === 'suspend'
        ? `${API_URL}/v1/subscriptions/${id}/suspend`
        : `${API_URL}/v1/subscriptions/${id}/${action}`;
      const res = await fetch(endpoint, { method: 'POST', headers: authHeaders() });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message || `${labels[action]} failed (${res.status})`);
      }
      const statusMap: Record<string, string> = { approve: 'APPROVED', reject: 'REJECTED', suspend: 'SUSPENDED' };
      setSubs(prev => prev.map(s => s.id === id ? { ...s, status: statusMap[action] } : s));
      setSuccessMsg(`Subscription ${labels[action].toLowerCase()}d successfully`);
    } catch (err) {
      setError(err instanceof Error ? err.message : `${labels[action]} failed`);
    } finally {
      setActionLoading(null);
    }
  };

  // Unique API list for filter dropdown
  const apiOptions = useMemo(() => {
    const seen = new Map<string, string>();
    subs.forEach(s => {
      if (!seen.has(s.apiId)) seen.set(s.apiId, s.apiName || s.apiId.substring(0, 12));
    });
    return Array.from(seen.entries()).sort((a, b) => a[1].localeCompare(b[1]));
  }, [subs]);

  // Filtered & searched
  const filtered = useMemo(() => {
    let list = subs;
    if (statusFilter !== 'ALL') list = list.filter(s => s.status === statusFilter);
    if (apiFilter !== 'ALL') list = list.filter(s => s.apiId === apiFilter);
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter(s => {
        const appName = apps[s.applicationId]?.name || '';
        return (s.apiName || '').toLowerCase().includes(q)
          || appName.toLowerCase().includes(q)
          || (s.planName || '').toLowerCase().includes(q)
          || s.id.toLowerCase().includes(q);
      });
    }
    return list;
  }, [subs, statusFilter, apiFilter, search, apps]);

  // Stats
  const stats = useMemo(() => ({
    total: subs.length,
    active: subs.filter(s => s.status === 'ACTIVE' || s.status === 'APPROVED').length,
    pending: subs.filter(s => s.status === 'PENDING').length,
    rejected: subs.filter(s => s.status === 'REJECTED').length,
  }), [subs]);

  const statCards = [
    { label: 'Total', value: stats.total, color: 'bg-slate-50 text-slate-700', icon: (
      <svg className="w-5 h-5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M3.75 12h16.5m-16.5 3.75h16.5M3.75 19.5h16.5M5.625 4.5h12.75a1.875 1.875 0 010 3.75H5.625a1.875 1.875 0 010-3.75z" /></svg>
    )},
    { label: 'Active', value: stats.active, color: 'bg-emerald-50 text-emerald-700', icon: (
      <svg className="w-5 h-5 text-emerald-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
    )},
    { label: 'Pending', value: stats.pending, color: 'bg-amber-50 text-amber-700', icon: (
      <svg className="w-5 h-5 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
    )},
    { label: 'Rejected', value: stats.rejected, color: 'bg-red-50 text-red-700', icon: (
      <svg className="w-5 h-5 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" /></svg>
    )},
  ];

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Subscription Management</h1>
        <p className="text-sm text-slate-500 mt-1">Review, approve, and manage API subscription requests from consumers.</p>
      </div>

      {/* Success toast */}
      {successMsg && (
        <div className="flex items-center gap-2 px-4 py-3 bg-emerald-50 border border-emerald-200 text-emerald-700 rounded-lg text-sm">
          <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" /></svg>
          {successMsg}
        </div>
      )}

      {error && (
        <div className="flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
          <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>
          {error}
        </div>
      )}

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {statCards.map(c => (
          <div key={c.label} className={`${c.color} rounded-xl p-4 flex items-center gap-3`}>
            {c.icon}
            <div>
              <p className="text-2xl font-bold">{loading ? '-' : c.value}</p>
              <p className="text-xs font-medium opacity-70">{c.label}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-3 bg-white border border-slate-200 rounded-xl px-4 py-3">
        <div className="relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" /></svg>
          <input
            type="text" placeholder="Search by API, app, or plan..."
            value={search} onChange={e => setSearch(e.target.value)}
            className="pl-9 pr-3 py-2 w-64 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500"
          />
        </div>
        {/* API autocomplete */}
        <div className="relative">
          <div className="flex items-center">
            <input
              type="text"
              placeholder="Filter by API..."
              value={apiFilter === 'ALL' ? apiQuery : apiOptions.find(([id]) => id === apiFilter)?.[1] || apiQuery}
              onChange={e => {
                setApiQuery(e.target.value);
                setApiFilter('ALL');
                setApiDropdownOpen(true);
              }}
              onFocus={() => setApiDropdownOpen(true)}
              onBlur={() => setTimeout(() => setApiDropdownOpen(false), 200)}
              className="px-3 py-2 w-52 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500"
            />
            {apiFilter !== 'ALL' && (
              <button
                onClick={() => { setApiFilter('ALL'); setApiQuery(''); }}
                className="absolute right-2 text-slate-400 hover:text-slate-600"
              >
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
              </button>
            )}
          </div>
          {apiDropdownOpen && (
            <div className="absolute top-full left-0 mt-1 w-64 bg-white border border-slate-200 rounded-lg shadow-lg z-20 max-h-60 overflow-y-auto">
              <button
                onMouseDown={() => { setApiFilter('ALL'); setApiQuery(''); setApiDropdownOpen(false); }}
                className="w-full text-left px-3 py-2 text-sm text-slate-500 hover:bg-slate-50 font-medium"
              >
                All APIs
              </button>
              {apiOptions
                .filter(([, name]) => !apiQuery || name.toLowerCase().includes(apiQuery.toLowerCase()))
                .map(([id, name]) => {
                  const count = subs.filter(s => s.apiId === id).length;
                  return (
                    <button
                      key={id}
                      onMouseDown={() => { setApiFilter(id); setApiQuery(''); setApiDropdownOpen(false); }}
                      className={`w-full text-left px-3 py-2 text-sm hover:bg-purple-50 flex items-center justify-between ${apiFilter === id ? 'bg-purple-50 text-purple-700 font-medium' : 'text-slate-700'}`}
                    >
                      <span className="truncate">{name}</span>
                      <span className="text-xs text-slate-400 shrink-0 ml-2">{count}</span>
                    </button>
                  );
                })}
              {apiOptions.filter(([, name]) => !apiQuery || name.toLowerCase().includes(apiQuery.toLowerCase())).length === 0 && (
                <div className="px-3 py-3 text-sm text-slate-400 text-center">No APIs match</div>
              )}
            </div>
          )}
        </div>
        <select
          value={statusFilter} onChange={e => { setStatusFilter(e.target.value); }}
          className="px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500"
        >
          {STATUS_OPTIONS.map(s => <option key={s} value={s}>{s === 'ALL' ? 'All Statuses' : s}</option>)}
        </select>
        {(statusFilter !== 'ALL' || apiFilter !== 'ALL' || search) && (
          <button onClick={() => { setStatusFilter('ALL'); setApiFilter('ALL'); setApiQuery(''); setSearch(''); }} className="text-xs text-slate-500 hover:text-slate-700 flex items-center gap-1">
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
            Clear
          </button>
        )}
        <span className="ml-auto text-xs text-slate-400">{filtered.length} result{filtered.length !== 1 ? 's' : ''}</span>
      </div>

      {/* Table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        {loading ? (
          <div className="animate-pulse p-6 space-y-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="flex gap-4">
                <div className="h-4 w-32 bg-slate-100 rounded" />
                <div className="h-4 w-40 bg-slate-100 rounded" />
                <div className="h-4 w-24 bg-slate-100 rounded" />
                <div className="h-4 w-20 bg-slate-100 rounded" />
                <div className="h-4 w-24 bg-slate-100 rounded" />
                <div className="h-4 w-32 bg-slate-100 rounded" />
              </div>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-20 text-center">
            <svg className="mx-auto w-12 h-12 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1}><path strokeLinecap="round" strokeLinejoin="round" d="M3.75 12h16.5m-16.5 3.75h16.5M3.75 19.5h16.5M5.625 4.5h12.75a1.875 1.875 0 010 3.75H5.625a1.875 1.875 0 010-3.75z" /></svg>
            <p className="mt-3 text-sm font-medium text-slate-800">No subscriptions found</p>
            <p className="text-xs text-slate-400 mt-1">{statusFilter !== 'ALL' || search ? 'Try adjusting your filters.' : 'Subscriptions appear when consumers subscribe to APIs.'}</p>
          </div>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="bg-slate-50/80 border-b border-slate-100">
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Application</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">API</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Plan</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                <th className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Created</th>
                <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtered.map(sub => {
                const app = apps[sub.applicationId];
                const cfg = STATUS_CONFIG[sub.status] || STATUS_CONFIG.SUSPENDED;
                const isActing = actionLoading === sub.id;
                return (
                  <tr key={sub.id} className="hover:bg-slate-50/50 transition-colors">
                    {/* Application */}
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-2.5">
                        <div className="w-8 h-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center text-xs font-bold shrink-0">
                          {(app?.name || sub.applicationId).charAt(0).toUpperCase()}
                        </div>
                        <div className="min-w-0">
                          <p className="text-sm font-medium text-slate-800 truncate">{app?.name || 'Unknown App'}</p>
                          <p className="text-[11px] text-slate-400 font-mono truncate">{sub.applicationId.substring(0, 12)}...</p>
                        </div>
                      </div>
                    </td>
                    {/* API */}
                    <td className="px-5 py-3.5">
                      <p className="text-sm font-medium text-slate-800">{sub.apiName || sub.apiId.substring(0, 12) + '...'}</p>
                    </td>
                    {/* Plan */}
                    <td className="px-5 py-3.5">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700">
                        {sub.planName || sub.planId.substring(0, 8)}
                      </span>
                    </td>
                    {/* Status */}
                    <td className="px-5 py-3.5">
                      <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold ring-1 ring-inset ${cfg.badge}`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${cfg.dot}`} />
                        {cfg.label}
                      </span>
                    </td>
                    {/* Created */}
                    <td className="px-5 py-3.5">
                      <p className="text-sm text-slate-500 tabular-nums">{new Date(sub.createdAt).toLocaleDateString()}</p>
                      <p className="text-[11px] text-slate-400">{new Date(sub.createdAt).toLocaleTimeString()}</p>
                    </td>
                    {/* Actions */}
                    <td className="px-5 py-3.5 text-right">
                      <div className="flex items-center justify-end gap-2">
                        {sub.status === 'PENDING' && (
                          <>
                            <button onClick={() => handleAction(sub.id, 'approve')} disabled={isActing}
                              className="px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 rounded-md disabled:opacity-50 transition-colors">
                              {isActing ? '...' : 'Approve'}
                            </button>
                            <button onClick={() => handleAction(sub.id, 'reject')} disabled={isActing}
                              className="px-3 py-1.5 text-xs font-semibold text-white bg-red-600 hover:bg-red-700 rounded-md disabled:opacity-50 transition-colors">
                              {isActing ? '...' : 'Reject'}
                            </button>
                          </>
                        )}
                        {(sub.status === 'ACTIVE' || sub.status === 'APPROVED') && (
                          <button onClick={() => handleAction(sub.id, 'suspend')} disabled={isActing}
                            className="px-3 py-1.5 text-xs font-medium text-slate-600 bg-white border border-slate-200 hover:bg-slate-50 rounded-md disabled:opacity-50 transition-colors">
                            {isActing ? '...' : 'Suspend'}
                          </button>
                        )}
                        {(sub.status === 'REJECTED' || sub.status === 'SUSPENDED') && (
                          <span className="text-xs text-slate-400">-</span>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
