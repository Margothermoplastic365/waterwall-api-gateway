'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, FormModal, get, post, put, del } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

interface ApiOption {
  id: string;
  name: string;
}

interface DocPage {
  id: string;
  title: string;
  type: string;
  version: string;
  content: string;
  feedbackUp: number;
  feedbackDown: number;
  updatedAt: string;
  [key: string]: unknown;
}

const DOC_TYPES = ['GUIDE', 'TUTORIAL', 'REFERENCE', 'FAQ', 'CHANGELOG'] as const;

export default function DocsAdminPage() {
  const [apis, setApis] = useState<ApiOption[]>([]);
  const [selectedApi, setSelectedApi] = useState('');
  const [pages, setPages] = useState<DocPage[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');

  /* Modal state */
  const [modal, setModal] = useState(false);
  const [editing, setEditing] = useState<DocPage | null>(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    title: '',
    type: 'GUIDE' as string,
    version: '1.0.0',
    content: '',
  });
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  useEffect(() => {
    get<ApiOption[]>('/v1/apis')
      .then((data: unknown) => { const d = data as ApiOption[] | { content: ApiOption[] }; setApis(Array.isArray(d) ? d : d.content ?? []); })
      .catch(() => setApis([]));
  }, []);

  useEffect(() => {
    if (!selectedApi) return;
    setLoading(true);
    const searchParam = search ? `?search=${encodeURIComponent(search)}` : '';
    get<DocPage[]>(`/v1/docs/${selectedApi}/pages${searchParam}`)
      .then((data) => setPages(Array.isArray(data) ? data : []))
      .catch(() => setPages([]))
      .finally(() => setLoading(false));
  }, [selectedApi, search]);

  const openCreate = () => {
    setEditing(null);
    setForm({ title: '', type: 'GUIDE', version: '1.0.0', content: '' });
    setModal(true);
  };

  const openEdit = (page: DocPage) => {
    setEditing(page);
    setForm({ title: page.title, type: page.type, version: page.version, content: page.content });
    setModal(true);
  };

  const handleSave = useCallback(async () => {
    if (!selectedApi) return;
    setSaving(true);
    try {
      if (editing) {
        const updated = await put<DocPage>(`/v1/docs/${selectedApi}/pages/${editing.id}`, form);
        setPages((prev) => prev.map((p) => (p.id === editing.id ? updated : p)));
      } else {
        const created = await post<DocPage>(`/v1/docs/${selectedApi}/pages`, form);
        setPages((prev) => [...prev, created]);
      }
      setModal(false);
    } catch {
      showToast('Failed to save documentation page', 'error');
    } finally {
      setSaving(false);
    }
  }, [selectedApi, editing, form]);

  const handleDelete = useCallback(
    async (pageId: string) => {
      if (!confirm('Delete this documentation page?')) return;
      try {
        await del(`/v1/docs/${selectedApi}/pages/${pageId}`);
        setPages((prev) => prev.filter((p) => p.id !== pageId));
      } catch {
        showToast('Failed to delete page', 'error');
      }
    },
    [selectedApi],
  );

  const columns: Column<DocPage>[] = useMemo(
    () => [
      { key: 'title', label: 'Title' },
      {
        key: 'type',
        label: 'Type',
        render: (row) => (
          <span className="inline-flex items-center rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-medium text-blue-700">
            {row.type}
          </span>
        ),
      },
      { key: 'version', label: 'Version' },
      {
        key: 'feedbackUp',
        label: 'Feedback',
        render: (row) => (
          <span className="text-sm">
            <span className="font-medium text-emerald-600">+{row.feedbackUp}</span>
            <span className="mx-1 text-slate-400">/</span>
            <span className="font-medium text-red-500">-{row.feedbackDown}</span>
          </span>
        ),
      },
      {
        key: 'updatedAt',
        label: 'Updated',
        render: (row) => new Date(row.updatedAt).toLocaleDateString(),
      },
      {
        key: 'id',
        label: 'Actions',
        render: (row) => (
          <div className="flex items-center gap-2">
            <button
              className="inline-flex items-center rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-200"
              onClick={() => openEdit(row)}
            >
              Edit
            </button>
            <button
              className="inline-flex items-center rounded-lg bg-red-50 px-3 py-1.5 text-xs font-medium text-red-600 transition-colors hover:bg-red-100"
              onClick={() => handleDelete(row.id)}
            >
              Delete
            </button>
          </div>
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
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Documentation Management</h1>
            <p className="mt-1 text-sm text-slate-500">Manage API documentation pages</p>
          </div>
          {selectedApi && (
            <button
              className="inline-flex items-center rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-purple-700"
              onClick={openCreate}
            >
              Create Page
            </button>
          )}
        </div>

        {/* Filters */}
        <div className="mb-6 rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="flex flex-wrap items-end gap-4">
            <div className="min-w-[250px]">
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Select API</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={selectedApi}
                onChange={(e) => setSelectedApi(e.target.value)}
              >
                <option value="">-- Select API --</option>
                {apis.map((a) => (
                  <option key={a.id} value={a.id}>{a.name}</option>
                ))}
              </select>
            </div>
            {selectedApi && (
              <div className="min-w-[300px]">
                <label className="mb-1.5 block text-sm font-medium text-slate-700">Search</label>
                <div className="relative">
                  <svg className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
                  </svg>
                  <input
                    className="w-full rounded-lg border border-slate-300 bg-white py-2.5 pl-10 pr-3.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    placeholder="Search documentation..."
                  />
                </div>
              </div>
            )}
          </div>
        </div>

        {selectedApi && (
          <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
            <DataTable data={pages} columns={columns} loading={loading} />
          </div>
        )}

        {!selectedApi && (
          <div className="rounded-xl bg-white p-12 text-center shadow-sm ring-1 ring-slate-200">
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-purple-50">
              <svg className="h-6 w-6 text-purple-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
              </svg>
            </div>
            <p className="text-sm font-medium text-slate-900">Select an API</p>
            <p className="mt-1 text-sm text-slate-500">Choose an API above to manage its documentation pages.</p>
          </div>
        )}

        <FormModal
          open={modal}
          onClose={() => setModal(false)}
          title={editing ? 'Edit Documentation Page' : 'Create Documentation Page'}
          onSubmit={handleSave}
          submitLabel={editing ? 'Save Changes' : 'Create Page'}
          loading={saving}
        >
          <div className="flex flex-col gap-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Title *</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.title}
                onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                placeholder="Getting Started"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Type</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.type}
                onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}
              >
                {DOC_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Version</label>
              <input
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                value={form.version}
                onChange={(e) => setForm((f) => ({ ...f, version: e.target.value }))}
                placeholder="1.0.0"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Content *</label>
              <textarea
                className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 font-mono text-xs text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
                style={{ minHeight: 200 }}
                value={form.content}
                onChange={(e) => setForm((f) => ({ ...f, content: e.target.value }))}
                placeholder="# Documentation content (Markdown)"
              />
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
