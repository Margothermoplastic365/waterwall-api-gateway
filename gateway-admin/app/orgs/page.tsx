'use client';

import React, { useEffect, useState, useMemo } from 'react';

const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

interface Organization {
  id: string;
  name: string;
  slug?: string;
  status: string;
  memberCount?: number;
  membersCount?: number;
  createdAt?: string;
  created?: string;
}

interface OrgMember {
  id: string;
  name?: string;
  email: string;
  role: string;
  joinedAt?: string;
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

function getStatusClasses(status: string): string {
  switch (status?.toUpperCase()) {
    case 'ACTIVE':
      return 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20';
    case 'SUSPENDED':
      return 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20';
    case 'INACTIVE':
      return 'bg-gray-50 text-gray-600 ring-1 ring-gray-500/20';
    default:
      return 'bg-gray-50 text-gray-600 ring-1 ring-gray-500/20';
  }
}

function getRoleBadgeClasses(role: string): string {
  switch (role?.toUpperCase()) {
    case 'OWNER':
      return 'bg-purple-50 text-purple-700 ring-1 ring-purple-600/20';
    case 'ADMIN':
      return 'bg-blue-50 text-blue-700 ring-1 ring-blue-600/20';
    case 'MEMBER':
      return 'bg-gray-50 text-gray-600 ring-1 ring-gray-500/20';
    default:
      return 'bg-gray-50 text-gray-600 ring-1 ring-gray-500/20';
  }
}

function SkeletonRow() {
  return (
    <div className="bg-white rounded-xl p-5 shadow-sm animate-pulse">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className="h-10 w-10 rounded-lg bg-gray-200" />
          <div className="space-y-2">
            <div className="h-4 w-40 rounded bg-gray-200" />
            <div className="h-3 w-24 rounded bg-gray-200" />
          </div>
        </div>
        <div className="flex items-center gap-6">
          <div className="h-5 w-16 rounded-full bg-gray-200" />
          <div className="h-4 w-12 rounded bg-gray-200" />
          <div className="h-4 w-20 rounded bg-gray-200" />
        </div>
      </div>
    </div>
  );
}

export default function OrganizationsPage() {
  const [orgs, setOrgs] = useState<Organization[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createForm, setCreateForm] = useState({ name: '', slug: '' });
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState('');

  /* Member management */
  const [expandedOrg, setExpandedOrg] = useState<string | null>(null);
  const [members, setMembers] = useState<OrgMember[]>([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [showInviteModal, setShowInviteModal] = useState(false);
  const [inviteOrgId, setInviteOrgId] = useState('');
  const [inviteForm, setInviteForm] = useState({ email: '', role: 'MEMBER' });
  const [inviteLoading, setInviteLoading] = useState(false);
  const [inviteError, setInviteError] = useState('');
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  async function fetchOrgs() {
    setLoading(true);
    setError('');
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/organizations`, { headers: authHeaders() });
      if (!res.ok) throw new Error('Failed to fetch organizations');
      const data = await res.json();
      const list = Array.isArray(data) ? data : data.content || data.data || [];
      setOrgs(list);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load organizations');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchOrgs();
  }, []);

  const filteredOrgs = useMemo(() => {
    if (!search) return orgs;
    const q = search.toLowerCase();
    return orgs.filter(
      (org) =>
        org.name.toLowerCase().includes(q) ||
        (org.slug && org.slug.toLowerCase().includes(q))
    );
  }, [orgs, search]);

  const handleCreateOrg = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateLoading(true);
    setCreateError('');
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/organizations`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(createForm),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => null);
        throw new Error(data?.message || 'Failed to create organization');
      }
      setShowCreateModal(false);
      setCreateForm({ name: '', slug: '' });
      fetchOrgs();
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : 'Failed to create organization');
    } finally {
      setCreateLoading(false);
    }
  };

  const fetchMembers = async (orgId: string) => {
    setMembersLoading(true);
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/organizations/${orgId}/members`, { headers: authHeaders() });
      if (!res.ok) throw new Error('Failed to fetch members');
      const data = await res.json();
      setMembers(Array.isArray(data) ? data : data.content || data.data || []);
    } catch {
      setMembers([]);
    } finally {
      setMembersLoading(false);
    }
  };

  const toggleExpand = (orgId: string) => {
    if (expandedOrg === orgId) {
      setExpandedOrg(null);
      setMembers([]);
    } else {
      setExpandedOrg(orgId);
      fetchMembers(orgId);
    }
  };

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    setInviteLoading(true);
    setInviteError('');
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/organizations/${inviteOrgId}/members`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(inviteForm),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => null);
        throw new Error(data?.message || 'Failed to invite member');
      }
      setShowInviteModal(false);
      setInviteForm({ email: '', role: 'MEMBER' });
      if (expandedOrg === inviteOrgId) fetchMembers(inviteOrgId);
    } catch (err) {
      setInviteError(err instanceof Error ? err.message : 'Failed to invite member');
    } finally {
      setInviteLoading(false);
    }
  };

  const handleRemoveMember = async (orgId: string, memberId: string) => {
    if (!confirm('Remove this member from the organization?')) return;
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/organizations/${orgId}/members/${memberId}`, {
        method: 'DELETE',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Failed to remove member');
      fetchMembers(orgId);
    } catch {
      showToast('Failed to remove member', 'error');
    }
  };

  /* ---------- Loading skeleton ---------- */
  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50/50 p-6 lg:p-10">
        <div className="mx-auto max-w-6xl space-y-4">
          <div className="mb-8 animate-pulse space-y-2">
            <div className="h-7 w-48 rounded bg-gray-200" />
            <div className="h-4 w-64 rounded bg-gray-200" />
          </div>
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </div>
      </div>
    );
  }

  /* ---------- Main render ---------- */
  return (
    <div className="min-h-screen bg-gray-50/50 p-6 lg:p-10">
      {toast && (<div className={`fixed top-4 right-4 z-50 flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg border max-w-sm ${toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-emerald-50 border-emerald-200 text-emerald-800'}`}>{toast.type === 'error' ? (<svg className="w-5 h-5 shrink-0 text-red-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>) : (<svg className="w-5 h-5 shrink-0 text-emerald-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>)}<p className="text-sm font-medium flex-1">{toast.message}</p><button onClick={() => setToast(null)} className="shrink-0 opacity-50 hover:opacity-100"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg></button></div>)}
      <div className="mx-auto max-w-6xl">

        {/* -------- Header -------- */}
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">
              Organizations
            </h1>
            <p className="mt-1 text-sm text-gray-500">
              Manage platform organizations, members, and roles
            </p>
          </div>
          <button
            onClick={() => setShowCreateModal(true)}
            className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="h-4 w-4">
              <path d="M10.75 4.75a.75.75 0 0 0-1.5 0v4.5h-4.5a.75.75 0 0 0 0 1.5h4.5v4.5a.75.75 0 0 0 1.5 0v-4.5h4.5a.75.75 0 0 0 0-1.5h-4.5v-4.5Z" />
            </svg>
            Create Organization
          </button>
        </div>

        {/* -------- Error -------- */}
        {error && (
          <div className="mb-6 flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="h-5 w-5 flex-shrink-0 text-red-400">
              <path fillRule="evenodd" d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM8.28 7.22a.75.75 0 0 0-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 1 0 1.06 1.06L10 11.06l1.72 1.72a.75.75 0 1 0 1.06-1.06L11.06 10l1.72-1.72a.75.75 0 0 0-1.06-1.06L10 8.94 8.28 7.22Z" clipRule="evenodd" />
            </svg>
            {error}
          </div>
        )}

        {/* -------- Toolbar -------- */}
        <div className="mb-6 flex items-center gap-4">
          <div className="relative flex-1 max-w-md">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 20 20"
              fill="currentColor"
              className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"
            >
              <path fillRule="evenodd" d="M9 3.5a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11ZM2 9a7 7 0 1 1 12.452 4.391l3.328 3.329a.75.75 0 1 1-1.06 1.06l-3.329-3.328A7 7 0 0 1 2 9Z" clipRule="evenodd" />
            </svg>
            <input
              type="text"
              placeholder="Search organizations..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full rounded-lg border border-gray-300 bg-white py-2 pl-10 pr-4 text-sm text-gray-900 placeholder-gray-400 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
            />
          </div>
          <span className="whitespace-nowrap text-xs font-medium text-gray-500">
            {filteredOrgs.length} organization{filteredOrgs.length !== 1 ? 's' : ''}
          </span>
        </div>

        {/* -------- Org list -------- */}
        <div className="space-y-3">
          {filteredOrgs.length === 0 ? (
            <div className="flex flex-col items-center justify-center rounded-xl bg-white py-20 shadow-sm ring-1 ring-gray-200/60">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.2} stroke="currentColor" className="mb-4 h-12 w-12 text-gray-300">
                <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12m-.75 4.5H21m-3.75 7.5h.008v.008h-.008v-.008Zm0 3h.008v.008h-.008v-.008Z" />
              </svg>
              <p className="text-sm font-medium text-gray-500">No organizations found</p>
              <p className="mt-1 text-xs text-gray-400">
                {search ? 'Try a different search term' : 'Create your first organization to get started'}
              </p>
            </div>
          ) : (
            filteredOrgs.map((org) => {
              const isExpanded = expandedOrg === org.id;
              return (
                <div key={org.id} className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-gray-200/60 transition-shadow hover:shadow-md">
                  {/* Org row */}
                  <button
                    type="button"
                    onClick={() => toggleExpand(org.id)}
                    className="flex w-full items-center gap-4 px-5 py-4 text-left transition-colors hover:bg-gray-50/60"
                  >
                    {/* Expand chevron */}
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 20 20"
                      fill="currentColor"
                      className={`h-4 w-4 flex-shrink-0 text-gray-400 transition-transform duration-200 ${isExpanded ? 'rotate-90' : ''}`}
                    >
                      <path fillRule="evenodd" d="M7.21 14.77a.75.75 0 0 1 .02-1.06L11.168 10 7.23 6.29a.75.75 0 1 1 1.04-1.08l4.5 4.25a.75.75 0 0 1 0 1.08l-4.5 4.25a.75.75 0 0 1-1.06-.02Z" clipRule="evenodd" />
                    </svg>

                    {/* Icon + Name */}
                    <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-indigo-50 text-sm font-bold text-indigo-600">
                      {org.name.charAt(0).toUpperCase()}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-gray-900">{org.name}</p>
                      <p className="truncate text-xs text-gray-400">{org.slug || 'no slug'}</p>
                    </div>

                    {/* Status */}
                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${getStatusClasses(org.status)}`}>
                      {org.status || 'UNKNOWN'}
                    </span>

                    {/* Member count */}
                    <span className="hidden text-xs text-gray-500 sm:inline-flex sm:items-center sm:gap-1">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="currentColor" className="h-3.5 w-3.5 text-gray-400">
                        <path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM12.735 14c.618 0 1.093-.561.872-1.139a6.002 6.002 0 0 0-11.215 0c-.22.578.254 1.139.872 1.139h9.47Z" />
                      </svg>
                      {org.memberCount ?? org.membersCount ?? '-'}
                    </span>

                    {/* Created date */}
                    <span className="hidden text-xs text-gray-400 lg:block">
                      {org.createdAt || org.created
                        ? new Date(org.createdAt || org.created!).toLocaleDateString()
                        : '-'}
                    </span>
                  </button>

                  {/* Expanded member panel */}
                  {isExpanded && (
                    <div className="border-t border-gray-100 bg-gray-50/60 px-5 py-4">
                      <div className="mb-4 flex items-center justify-between">
                        <h3 className="text-sm font-semibold text-gray-700">
                          Members of {org.name}
                        </h3>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setInviteOrgId(org.id);
                            setShowInviteModal(true);
                          }}
                          className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm transition hover:bg-indigo-500"
                        >
                          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="currentColor" className="h-3.5 w-3.5">
                            <path d="M8.75 3.75a.75.75 0 0 0-1.5 0v3.5h-3.5a.75.75 0 0 0 0 1.5h3.5v3.5a.75.75 0 0 0 1.5 0v-3.5h3.5a.75.75 0 0 0 0-1.5h-3.5v-3.5Z" />
                          </svg>
                          Invite Member
                        </button>
                      </div>

                      {membersLoading ? (
                        <div className="flex items-center justify-center py-8">
                          <svg className="h-6 w-6 animate-spin text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                          </svg>
                        </div>
                      ) : members.length === 0 ? (
                        <div className="flex flex-col items-center py-8 text-gray-400">
                          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.2} stroke="currentColor" className="mb-2 h-8 w-8">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 0 0 2.625.372 9.337 9.337 0 0 0 4.121-.952 4.125 4.125 0 0 0-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 0 1 8.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0 1 11.964-3.07M12 6.375a3.375 3.375 0 1 1-6.75 0 3.375 3.375 0 0 1 6.75 0Zm8.25 2.25a2.625 2.625 0 1 1-5.25 0 2.625 2.625 0 0 1 5.25 0Z" />
                          </svg>
                          <p className="text-sm">No members found</p>
                        </div>
                      ) : (
                        <div className="overflow-hidden rounded-lg bg-white ring-1 ring-gray-200/80">
                          <table className="min-w-full divide-y divide-gray-200">
                            <thead>
                              <tr className="bg-gray-50">
                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Name</th>
                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Email</th>
                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Role</th>
                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Joined</th>
                                <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-gray-500">Actions</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                              {members.map((m) => (
                                <tr key={m.id} className="transition-colors hover:bg-gray-50/60">
                                  <td className="whitespace-nowrap px-4 py-3 text-sm font-medium text-gray-900">{m.name || '-'}</td>
                                  <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">{m.email}</td>
                                  <td className="whitespace-nowrap px-4 py-3">
                                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${getRoleBadgeClasses(m.role)}`}>
                                      {m.role}
                                    </span>
                                  </td>
                                  <td className="whitespace-nowrap px-4 py-3 text-xs text-gray-400">
                                    {m.joinedAt ? new Date(m.joinedAt).toLocaleDateString() : '-'}
                                  </td>
                                  <td className="whitespace-nowrap px-4 py-3 text-right">
                                    <button
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        handleRemoveMember(org.id, m.id);
                                      }}
                                      className="inline-flex items-center rounded-md px-2.5 py-1 text-xs font-medium text-red-600 transition hover:bg-red-50 hover:text-red-700"
                                    >
                                      Remove
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* ======== Invite Member Modal ======== */}
      {showInviteModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
          onClick={() => setShowInviteModal(false)}
        >
          <div
            className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl ring-1 ring-gray-200"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-5 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">Invite Member</h3>
              <button
                onClick={() => setShowInviteModal(false)}
                className="rounded-lg p-1 text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
              >
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="h-5 w-5">
                  <path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
                </svg>
              </button>
            </div>

            {inviteError && (
              <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
                {inviteError}
              </div>
            )}

            <form onSubmit={handleInvite} className="space-y-4">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-gray-700">Email</label>
                <input
                  type="email"
                  placeholder="user@example.com"
                  value={inviteForm.email}
                  onChange={(e) => setInviteForm({ ...inviteForm, email: e.target.value })}
                  required
                  className="w-full rounded-lg border border-gray-300 px-3.5 py-2 text-sm text-gray-900 placeholder-gray-400 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-gray-700">Role</label>
                <select
                  value={inviteForm.role}
                  onChange={(e) => setInviteForm({ ...inviteForm, role: e.target.value })}
                  className="w-full rounded-lg border border-gray-300 px-3.5 py-2 text-sm text-gray-900 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                >
                  <option value="MEMBER">Member</option>
                  <option value="ADMIN">Admin</option>
                  <option value="OWNER">Owner</option>
                </select>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowInviteModal(false)}
                  className="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={inviteLoading}
                  className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {inviteLoading && (
                    <svg className="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                  )}
                  {inviteLoading ? 'Inviting...' : 'Invite Member'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ======== Create Org Modal ======== */}
      {showCreateModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
          onClick={() => setShowCreateModal(false)}
        >
          <div
            className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl ring-1 ring-gray-200"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-5 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">Create Organization</h3>
              <button
                onClick={() => setShowCreateModal(false)}
                className="rounded-lg p-1 text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
              >
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="h-5 w-5">
                  <path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
                </svg>
              </button>
            </div>

            {createError && (
              <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
                {createError}
              </div>
            )}

            <form onSubmit={handleCreateOrg} className="space-y-4">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-gray-700">Organization Name</label>
                <input
                  type="text"
                  placeholder="Acme Corp"
                  value={createForm.name}
                  onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                  required
                  className="w-full rounded-lg border border-gray-300 px-3.5 py-2 text-sm text-gray-900 placeholder-gray-400 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-gray-700">Slug</label>
                <input
                  type="text"
                  placeholder="acme-corp"
                  value={createForm.slug}
                  onChange={(e) => setCreateForm({ ...createForm, slug: e.target.value })}
                  className="w-full rounded-lg border border-gray-300 px-3.5 py-2 text-sm text-gray-900 placeholder-gray-400 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                />
                <p className="mt-1.5 text-xs text-gray-400">URL-friendly identifier. Auto-generated if left blank.</p>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createLoading}
                  className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {createLoading && (
                    <svg className="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                  )}
                  {createLoading ? 'Creating...' : 'Create Organization'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
