'use client';

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, FormModal } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface AiPlan {
  id: string;
  name: string;
  dailyTokenLimit: number;
  monthlyTokenLimit: number;
  allowedModels: string[];
  pricePerThousandTokens: number;
  createdAt?: string;
  [key: string]: unknown;
}

interface AiPlanFormState {
  name: string;
  dailyTokenLimit: string;
  monthlyTokenLimit: string;
  allowedModels: Record<string, boolean>;
  pricePerThousandTokens: string;
}

const ALL_MODELS = [
  'gpt-4o',
  'gpt-4o-mini',
  'claude-sonnet',
  'claude-haiku',
  'deepseek-chat',
  'deepseek-coder',
];

const emptyForm: AiPlanFormState = {
  name: '',
  dailyTokenLimit: '',
  monthlyTokenLimit: '',
  allowedModels: Object.fromEntries(ALL_MODELS.map((m) => [m, false])),
  pricePerThousandTokens: '',
};

function authHeaders(): Record<string, string> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return headers;
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function AiPlansPage() {
  const [plans, setPlans] = useState<AiPlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /* Modal state */
  const [modalOpen, setModalOpen] = useState(false);
  const [editingPlan, setEditingPlan] = useState<AiPlan | null>(null);
  const [form, setForm] = useState<AiPlanFormState>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  /* ---- Fetch plans ---- */
  const fetchPlans = useCallback(() => {
    setLoading(true);
    setError(null);
    fetch(`${API_URL}/v1/ai/plans`, { headers: authHeaders() })
      .then((r) => {
        if (!r.ok) throw new Error('Failed to load AI plans');
        return r.json();
      })
      .then((data) => {
        const list = Array.isArray(data) ? data : data.content ?? [];
        setPlans(list);
      })
      .catch((err) => setError(err.message ?? 'Failed to load AI plans'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    fetchPlans();
  }, [fetchPlans]);

  /* ---- Open create / edit modal ---- */
  const openCreate = () => {
    setEditingPlan(null);
    setForm(emptyForm);
    setModalOpen(true);
  };

  const openEdit = (plan: AiPlan) => {
    setEditingPlan(plan);
    const modelMap: Record<string, boolean> = {};
    ALL_MODELS.forEach((m) => {
      modelMap[m] = plan.allowedModels.includes(m);
    });
    setForm({
      name: plan.name,
      dailyTokenLimit: String(plan.dailyTokenLimit),
      monthlyTokenLimit: String(plan.monthlyTokenLimit),
      allowedModels: modelMap,
      pricePerThousandTokens: String(plan.pricePerThousandTokens),
    });
    setModalOpen(true);
  };

  /* ---- Create / Update ---- */
  const handleSubmit = useCallback(async () => {
    if (!form.name.trim()) return;
    setSaving(true);
    try {
      const body = {
        name: form.name.trim(),
        dailyTokenLimit: Number(form.dailyTokenLimit) || 0,
        monthlyTokenLimit: Number(form.monthlyTokenLimit) || 0,
        allowedModels: ALL_MODELS.filter((m) => form.allowedModels[m]),
        pricePerThousandTokens: Number(form.pricePerThousandTokens) || 0,
      };
      const url = editingPlan
        ? `${API_URL}/v1/ai/plans/${editingPlan.id}`
        : `${API_URL}/v1/ai/plans`;
      const method = editingPlan ? 'PUT' : 'POST';
      const res = await fetch(url, {
        method,
        headers: authHeaders(),
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error('Failed to save AI plan');
      setModalOpen(false);
      setForm(emptyForm);
      setEditingPlan(null);
      fetchPlans();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save AI plan';
      showToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  }, [form, editingPlan, fetchPlans]);

  /* ---- Delete ---- */
  const handleDelete = useCallback(
    async (plan: AiPlan) => {
      if (!confirm(`Delete AI plan "${plan.name}"?`)) return;
      try {
        const res = await fetch(`${API_URL}/v1/ai/plans/${plan.id}`, {
          method: 'DELETE',
          headers: authHeaders(),
        });
        if (!res.ok) throw new Error('Failed to delete');
        fetchPlans();
      } catch {
        showToast('Failed to delete AI plan', 'error');
      }
    },
    [fetchPlans],
  );

  /* ---- Columns ---- */
  const columns: Column<AiPlan>[] = useMemo(
    () => [
      { key: 'name', label: 'Name', sortable: true },
      {
        key: 'dailyTokenLimit',
        label: 'Daily Token Limit',
        render: (p) => <span className="text-sm text-slate-700">{p.dailyTokenLimit.toLocaleString()}</span>,
      },
      {
        key: 'monthlyTokenLimit',
        label: 'Monthly Token Limit',
        render: (p) => <span className="text-sm text-slate-700">{p.monthlyTokenLimit.toLocaleString()}</span>,
      },
      {
        key: 'allowedModels',
        label: 'Allowed Models',
        render: (p) => (
          <div className="flex flex-wrap gap-1.5">
            {p.allowedModels.map((m) => (
              <span key={m} className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-purple-100 text-purple-700">
                {m}
              </span>
            ))}
          </div>
        ),
      },
      {
        key: 'pricePerThousandTokens',
        label: 'Price/1K Tokens',
        render: (p) => <span className="font-semibold text-slate-900">${p.pricePerThousandTokens.toFixed(4)}</span>,
      },
      {
        key: 'actions',
        label: 'Actions',
        render: (p) => (
          <div className="flex gap-2">
            <button
              className="inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium border border-slate-200 bg-white text-slate-600 hover:bg-purple-50 hover:text-purple-600 hover:border-purple-200 transition-all"
              onClick={() => openEdit(p)}
            >
              Edit
            </button>
            <button
              className="inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium bg-red-50 text-red-600 hover:bg-red-100 transition-all"
              onClick={() => handleDelete(p)}
            >
              Delete
            </button>
          </div>
        ),
      },
    ],
    [handleDelete],
  );

  const set =
    (key: keyof Omit<AiPlanFormState, 'allowedModels'>) =>
    (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  const toggleModel = (model: string) =>
    setForm((f) => ({
      ...f,
      allowedModels: { ...f.allowedModels, [model]: !f.allowedModels[model] },
    }));

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
        <div className="max-w-7xl mx-auto space-y-6">
          <div className="space-y-2">
            <div className="h-7 w-52 bg-slate-200 rounded-lg animate-pulse" />
            <div className="h-4 w-80 bg-slate-100 rounded-lg animate-pulse" />
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
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">AI Plan Management</h1>
            <p className="mt-1 text-sm text-slate-500">Configure token limits and pricing for AI consumers</p>
          </div>
          <button
            className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200"
            onClick={openCreate}
          >
            <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
            Create AI Plan
          </button>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <DataTable data={plans} columns={columns} />
        </div>

        {/* Create / Edit Modal */}
        <FormModal
          open={modalOpen}
          onClose={() => {
            setModalOpen(false);
            setEditingPlan(null);
          }}
          title={editingPlan ? 'Edit AI Plan' : 'Create AI Plan'}
          onSubmit={handleSubmit}
          submitLabel={editingPlan ? 'Update' : 'Create'}
          loading={saving}
        >
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">
                Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={form.name}
                onChange={set('name')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                placeholder="Premium AI Plan"
              />
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Daily Token Limit</label>
              <input
                type="number"
                min={0}
                value={form.dailyTokenLimit}
                onChange={set('dailyTokenLimit')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                placeholder="100000"
              />
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Monthly Token Limit</label>
              <input
                type="number"
                min={0}
                value={form.monthlyTokenLimit}
                onChange={set('monthlyTokenLimit')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                placeholder="3000000"
              />
            </div>

            <div className="space-y-2">
              <label className="block text-sm font-medium text-slate-700">Allowed Models</label>
              <div className="grid grid-cols-2 gap-2">
                {ALL_MODELS.map((model) => (
                  <label
                    key={model}
                    className={`flex items-center gap-2.5 px-3 py-2.5 rounded-lg border cursor-pointer transition-all ${
                      form.allowedModels[model]
                        ? 'bg-purple-50 border-purple-300 text-purple-700'
                        : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={form.allowedModels[model] || false}
                      onChange={() => toggleModel(model)}
                      className="rounded border-slate-300 text-purple-600 focus:ring-purple-500"
                    />
                    <span className="text-sm font-medium">{model}</span>
                  </label>
                ))}
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Price per 1K Tokens ($)</label>
              <input
                type="number"
                min={0}
                step={0.0001}
                value={form.pricePerThousandTokens}
                onChange={set('pricePerThousandTokens')}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                placeholder="0.0060"
              />
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
