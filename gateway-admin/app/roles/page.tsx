'use client';

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, FormModal } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

const IDENTITY_URL =
  typeof window !== 'undefined'
    ? process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081'
    : process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

interface Role {
  id: string;
  name: string;
  description?: string;
  scope?: string;
  system?: boolean;
  permissions: string[];
  assignmentsCount?: number;
  [key: string]: unknown;
}

interface PermissionGroup {
  resource: string;
  actions: string[];
}

/* ---- Identity-service fetchers (separate base URL) ---- */
function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('jwt_token');
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return headers;
}

async function identityGet<T>(path: string): Promise<T> {
  const res = await fetch(`${IDENTITY_URL}${path}`, { headers: authHeaders() });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(body.message ?? `Request failed: ${res.status}`);
  }
  return res.json();
}

async function identityPost<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${IDENTITY_URL}${path}`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const b = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(b.message ?? `Request failed: ${res.status}`);
  }
  return res.json();
}

const SCOPE_TYPES = ['GLOBAL', 'ORGANIZATION', 'API', 'APPLICATION'] as const;

const SCOPE_COLORS: Record<string, string> = {
  GLOBAL: 'bg-purple-100 text-purple-700 ring-purple-600/20',
  ORGANIZATION: 'bg-blue-100 text-blue-700 ring-blue-600/20',
  API: 'bg-green-100 text-green-700 ring-green-600/20',
  APPLICATION: 'bg-amber-100 text-amber-700 ring-amber-600/20',
};

/* ------------------------------------------------------------------ */
/*  Skeleton Row                                                       */
/* ------------------------------------------------------------------ */

function SkeletonRows({ count = 5 }: { count?: number }) {
  return (
    <>
      {Array.from({ length: count }).map((_, i) => (
        <tr key={i} className="animate-pulse">
          <td className="px-6 py-4"><div className="h-4 w-28 bg-gray-200 rounded" /></td>
          <td className="px-6 py-4"><div className="h-5 w-20 bg-gray-200 rounded-full" /></td>
          <td className="px-6 py-4"><div className="h-4 w-10 bg-gray-200 rounded" /></td>
          <td className="px-6 py-4"><div className="h-4 w-8 bg-gray-200 rounded" /></td>
          <td className="px-6 py-4"><div className="h-4 w-8 bg-gray-200 rounded" /></td>
        </tr>
      ))}
    </>
  );
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function RolesPage() {
  /* ---- Roles state ---- */
  const [roles, setRoles] = useState<Role[]>([]);
  const [rolesLoading, setRolesLoading] = useState(true);
  const [rolesError, setRolesError] = useState<string | null>(null);

  /* ---- Permissions state ---- */
  const [permGroups, setPermGroups] = useState<PermissionGroup[]>([]);
  const [permsLoading, setPermsLoading] = useState(true);

  /* ---- Modal state ---- */
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({
    name: '',
    description: '',
    scope: 'GLOBAL' as string,
    permissions: [] as string[],
  });
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  /* ---- Fetch roles ---- */
  const fetchRoles = useCallback(() => {
    setRolesLoading(true);
    setRolesError(null);
    identityGet<Role[]>('/v1/roles')
      .then(setRoles)
      .catch((err) => setRolesError(err.message))
      .finally(() => setRolesLoading(false));
  }, []);

  useEffect(() => { fetchRoles(); }, [fetchRoles]);

  /* ---- Fetch permissions ---- */
  useEffect(() => {
    setPermsLoading(true);
    identityGet<PermissionGroup[]>('/v1/roles/permissions')
      .then(setPermGroups)
      .catch(() => setPermGroups([]))
      .finally(() => setPermsLoading(false));
  }, []);

  /* ---- All permission strings for checkboxes ---- */
  const allPermissions = useMemo(
    () => permGroups.flatMap((g) => g.actions.map((a) => `${g.resource}:${a}`)),
    [permGroups],
  );

  /* ---- Create role ---- */
  const handleCreate = useCallback(async () => {
    if (!form.name.trim()) return;
    setSaving(true);
    try {
      await identityPost('/v1/roles', {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        scope: form.scope,
        permissions: form.permissions,
      });
      setModalOpen(false);
      setForm({ name: '', description: '', scope: 'GLOBAL', permissions: [] });
      fetchRoles();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to create role';
      showToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  }, [form, fetchRoles]);

  const togglePermission = (perm: string) => {
    setForm((f) => ({
      ...f,
      permissions: f.permissions.includes(perm)
        ? f.permissions.filter((p) => p !== perm)
        : [...f.permissions, perm],
    }));
  };

  /* ---- Columns ---- */
  const columns: Column<Role>[] = useMemo(() => [
    {
      key: 'name',
      label: 'Name',
      sortable: true,
      render: (r) => (
        <div className="flex flex-col">
          <span className="font-semibold text-gray-900">{r.name}</span>
          {r.description && (
            <span className="text-xs text-gray-500 mt-0.5 line-clamp-1">{r.description}</span>
          )}
        </div>
      ),
    },
    {
      key: 'scope',
      label: 'Scope',
      render: (r) => {
        const scope = r.scope ?? 'GLOBAL';
        const color = SCOPE_COLORS[scope] || 'bg-gray-100 text-gray-700 ring-gray-600/20';
        return (
          <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${color}`}>
            {scope}
          </span>
        );
      },
    },
    {
      key: 'system',
      label: 'System',
      render: (r) => (
        r.system ? (
          <span className="inline-flex items-center gap-1 text-xs font-medium text-blue-700">
            <svg className="h-3.5 w-3.5" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.137-.089l4-5.5z" clipRule="evenodd" />
            </svg>
            Yes
          </span>
        ) : (
          <span className="text-xs text-gray-400">No</span>
        )
      ),
    },
    {
      key: 'permissions',
      label: 'Permissions',
      render: (r) => {
        const count = r.permissions?.length ?? 0;
        return (
          <span className={`inline-flex items-center rounded-md px-2 py-1 text-xs font-medium ${count > 0 ? 'bg-indigo-50 text-indigo-700' : 'bg-gray-50 text-gray-500'}`}>
            {count} {count === 1 ? 'permission' : 'permissions'}
          </span>
        );
      },
    },
    {
      key: 'assignmentsCount',
      label: 'Assignments',
      render: (r) => {
        const count = r.assignmentsCount ?? 0;
        return (
          <span className={`text-sm tabular-nums ${count > 0 ? 'font-medium text-gray-900' : 'text-gray-400'}`}>
            {count}
          </span>
        );
      },
    },
  ], []);

  const inputCls =
    'w-full rounded-lg border border-gray-300 bg-white px-3.5 py-2.5 text-sm text-gray-900 shadow-sm placeholder:text-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 transition-colors';

  return (
    <main className="min-h-screen bg-gray-50/50 px-4 py-8 sm:px-6 lg:px-8">
      {toast && (<div className={`fixed top-4 right-4 z-50 flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg border max-w-sm ${toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-emerald-50 border-emerald-200 text-emerald-800'}`}>{toast.type === 'error' ? (<svg className="w-5 h-5 shrink-0 text-red-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>) : (<svg className="w-5 h-5 shrink-0 text-emerald-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>)}<p className="text-sm font-medium flex-1">{toast.message}</p><button onClick={() => setToast(null)} className="shrink-0 opacity-50 hover:opacity-100"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg></button></div>)}
      <div className="mx-auto max-w-7xl">

        {/* ---- Page Header ---- */}
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-gray-900 sm:text-3xl">
              Roles &amp; Permissions
            </h1>
            <p className="mt-1 text-sm text-gray-500">
              Manage access control roles and their associated permissions.
            </p>
          </div>
          <button
            onClick={() => {
              setForm({ name: '', description: '', scope: 'GLOBAL', permissions: [] });
              setModalOpen(true);
            }}
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 active:scale-[0.98]"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
            </svg>
            Create Role
          </button>
        </div>

        {/* ---- Error Banner ---- */}
        {rolesError && (
          <div className="mb-6 flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 shadow-sm">
            <svg className="h-5 w-5 shrink-0 text-red-500" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clipRule="evenodd" />
            </svg>
            {rolesError}
          </div>
        )}

        {/* ---- Section 1: Role List ---- */}
        <div className="mb-10">
          <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-gray-900/5">
            {rolesLoading ? (
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    {['Name', 'Scope', 'System', 'Permissions', 'Assignments'].map((h) => (
                      <th key={h} className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-gray-500">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  <SkeletonRows count={5} />
                </tbody>
              </table>
            ) : roles.length === 0 && !rolesError ? (
              <div className="flex flex-col items-center justify-center py-16 px-6">
                <div className="rounded-full bg-gray-100 p-4">
                  <svg className="h-8 w-8 text-gray-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128H5.228A2.25 2.25 0 013 16.878V4.5A2.25 2.25 0 015.25 2.25h10.5A2.25 2.25 0 0118 4.5v4.307" />
                  </svg>
                </div>
                <h3 className="mt-4 text-sm font-semibold text-gray-900">No roles yet</h3>
                <p className="mt-1 text-sm text-gray-500">Get started by creating your first role.</p>
                <button
                  onClick={() => {
                    setForm({ name: '', description: '', scope: 'GLOBAL', permissions: [] });
                    setModalOpen(true);
                  }}
                  className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-700 transition-colors"
                >
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                  </svg>
                  Create Role
                </button>
              </div>
            ) : (
              <DataTable data={roles} columns={columns} loading={rolesLoading} />
            )}
          </div>
        </div>

        {/* ---- Section 2: Permission Matrix ---- */}
        <div>
          <div className="mb-4 flex items-center gap-3">
            <h2 className="text-lg font-bold text-gray-900">Permission Matrix</h2>
            {!permsLoading && permGroups.length > 0 && (
              <span className="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-600">
                {allPermissions.length} total
              </span>
            )}
          </div>

          {permsLoading ? (
            <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-gray-900/5">
              <div className="animate-pulse divide-y divide-gray-100">
                {[1, 2, 3, 4].map((i) => (
                  <div key={i} className="p-5">
                    <div className="mb-3 h-3.5 w-24 rounded bg-gray-200" />
                    <div className="flex flex-wrap gap-2">
                      {Array.from({ length: 3 + i }).map((_, j) => (
                        <div key={j} className="h-6 w-16 rounded-full bg-gray-100" />
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : permGroups.length === 0 ? (
            <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-gray-900/5">
              <div className="flex flex-col items-center justify-center py-12 px-6">
                <div className="rounded-full bg-gray-100 p-3">
                  <svg className="h-6 w-6 text-gray-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" />
                  </svg>
                </div>
                <p className="mt-3 text-sm text-gray-500">No permissions defined.</p>
              </div>
            </div>
          ) : (
            <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-gray-900/5 divide-y divide-gray-100">
              {permGroups.map((g) => (
                <div key={g.resource} className="p-5">
                  <h3 className="mb-3 flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-gray-500">
                    <svg className="h-3.5 w-3.5 text-gray-400" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6z" />
                    </svg>
                    {g.resource}
                  </h3>
                  <div className="flex flex-wrap gap-2">
                    {g.actions.map((a) => (
                      <span
                        key={a}
                        className="inline-flex items-center rounded-full bg-gradient-to-b from-gray-50 to-gray-100 px-3 py-1 text-xs font-medium text-gray-700 ring-1 ring-inset ring-gray-200 shadow-sm"
                      >
                        {a}
                      </span>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* ---- Create Role Modal ---- */}
        <FormModal
          open={modalOpen}
          onClose={() => setModalOpen(false)}
          title="Create Role"
          onSubmit={handleCreate}
          submitLabel="Create"
          loading={saving}
        >
          <div className="space-y-5">
            {/* Name */}
            <div>
              <label className="mb-1.5 block text-sm font-medium text-gray-700">
                Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                className={inputCls}
                placeholder="e.g. API Manager"
              />
            </div>

            {/* Description */}
            <div>
              <label className="mb-1.5 block text-sm font-medium text-gray-700">
                Description
              </label>
              <textarea
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                rows={2}
                className={inputCls}
                placeholder="Optional description of this role..."
              />
            </div>

            {/* Scope */}
            <div>
              <label className="mb-1.5 block text-sm font-medium text-gray-700">Scope</label>
              <select
                value={form.scope}
                onChange={(e) => setForm((f) => ({ ...f, scope: e.target.value }))}
                className={inputCls}
              >
                {SCOPE_TYPES.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
              <div className="mt-2 flex gap-1.5">
                {SCOPE_TYPES.map((s) => (
                  <span
                    key={s}
                    className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-medium ring-1 ring-inset transition-opacity ${
                      form.scope === s ? 'opacity-100' : 'opacity-40'
                    } ${SCOPE_COLORS[s]}`}
                  >
                    {s}
                  </span>
                ))}
              </div>
            </div>

            {/* Permissions */}
            <div>
              <label className="mb-1.5 flex items-center justify-between text-sm font-medium text-gray-700">
                <span>Permissions</span>
                {form.permissions.length > 0 && (
                  <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700">
                    {form.permissions.length} selected
                  </span>
                )}
              </label>

              <div className="max-h-56 overflow-y-auto rounded-lg border border-gray-200 bg-gray-50/50 shadow-inner">
                {allPermissions.length === 0 ? (
                  <p className="px-4 py-6 text-center text-xs text-gray-400">
                    No permissions available
                  </p>
                ) : (
                  <div className="grid grid-cols-1 gap-px bg-gray-200 sm:grid-cols-2">
                    {permGroups.map((g) => (
                      <div key={g.resource} className="bg-white p-3">
                        <p className="mb-2 text-[10px] font-bold uppercase tracking-wider text-gray-400">
                          {g.resource}
                        </p>
                        <div className="space-y-1.5">
                          {g.actions.map((a) => {
                            const perm = `${g.resource}:${a}`;
                            const checked = form.permissions.includes(perm);
                            return (
                              <label
                                key={perm}
                                className={`flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors ${
                                  checked
                                    ? 'bg-blue-50 text-blue-800'
                                    : 'text-gray-600 hover:bg-gray-50'
                                }`}
                              >
                                <input
                                  type="checkbox"
                                  checked={checked}
                                  onChange={() => togglePermission(perm)}
                                  className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500/20"
                                />
                                <span className="text-xs font-medium">{a}</span>
                              </label>
                            );
                          })}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </FormModal>
      </div>
    </main>
  );
}
