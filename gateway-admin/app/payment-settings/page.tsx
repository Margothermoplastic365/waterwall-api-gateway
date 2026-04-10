'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable, StatusBadge, FormModal, get, post, put, del } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface PaymentGatewaySettings {
  id: string;
  provider: string;
  displayName: string;
  enabled: boolean;
  environment: string;
  secretKey?: string;
  publicKey?: string;
  baseUrl?: string;
  callbackUrl?: string;
  webhookUrl?: string;
  supportedCurrencies?: string;
  defaultCurrency?: string;
  extraConfig?: string;
  createdAt: string;
  updatedAt: string;
  [key: string]: unknown;
}

const EMPTY_FORM = {
  provider: 'paystack',
  displayName: 'Paystack',
  environment: 'test',
  secretKey: '',
  publicKey: '',
  baseUrl: 'https://api.paystack.co',
  callbackUrl: 'http://localhost:3000/billing?verify=true',
  webhookUrl: '',
  supportedCurrencies: 'NGN,USD,GHS,ZAR,KES',
  defaultCurrency: 'NGN',
  extraConfig: '',
  enabled: true,
};

const PROVIDER_PRESETS: Record<string, Partial<typeof EMPTY_FORM>> = {
  paystack: { displayName: 'Paystack', baseUrl: 'https://api.paystack.co', supportedCurrencies: 'NGN,USD,GHS,ZAR,KES' },
  stripe: { displayName: 'Stripe', baseUrl: 'https://api.stripe.com', supportedCurrencies: 'USD,EUR,GBP,NGN' },
  flutterwave: { displayName: 'Flutterwave', baseUrl: 'https://api.flutterwave.com/v3', supportedCurrencies: 'NGN,USD,GHS,ZAR,KES' },
};

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

function webhookUrlFor(provider: string): string {
  if (typeof window === 'undefined') return '';
  const base = window.location.origin.replace(':3001', ':8082');
  return `${base}/v1/webhooks/${provider}`;
}

export default function PaymentSettingsPage() {
  const [gateways, setGateways] = useState<PaymentGatewaySettings[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [modal, setModal] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [showSecret, setShowSecret] = useState(false);
  const [copied, setCopied] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  const fetchGateways = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await fetch(`${API_URL}/v1/payment-gateway-settings`, { headers: authHeaders() });
      if (res.ok) {
        const data = await res.json();
        setGateways(Array.isArray(data) ? data : data.content || data.data || []);
      } else {
        setError('Failed to load payment gateways');
      }
    } catch {
      setError('Failed to load payment gateways');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchGateways();
  }, [fetchGateways]);

  const openCreate = () => {
    setEditingId(null);
    setForm({ ...EMPTY_FORM });
    setShowSecret(false);
    setCopied(false);
    setModal(true);
  };

  const openEdit = (gw: PaymentGatewaySettings) => {
    setEditingId(gw.id);
    setForm({
      provider: gw.provider,
      displayName: gw.displayName,
      environment: gw.environment || 'test',
      secretKey: '',
      publicKey: gw.publicKey || '',
      baseUrl: gw.baseUrl || '',
      callbackUrl: gw.callbackUrl || '',
      webhookUrl: gw.webhookUrl || '',
      supportedCurrencies: gw.supportedCurrencies || '',
      defaultCurrency: (gw as Record<string, unknown>).defaultCurrency as string || 'NGN',
      extraConfig: gw.extraConfig || '',
      enabled: gw.enabled,
    });
    setShowSecret(false);
    setCopied(false);
    setModal(true);
  };

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const payload: Record<string, unknown> = { ...form };
      if (editingId && !form.secretKey) {
        delete payload.secretKey;
      }
      if (editingId) {
        const res = await fetch(`${API_URL}/v1/payment-gateway-settings/${editingId}`, {
          method: 'PUT',
          headers: authHeaders(),
          body: JSON.stringify(payload),
        });
        if (!res.ok) throw new Error('Update failed');
        showToast('Payment gateway updated successfully');
      } else {
        const res = await fetch(`${API_URL}/v1/payment-gateway-settings`, {
          method: 'POST',
          headers: authHeaders(),
          body: JSON.stringify(payload),
        });
        if (!res.ok) throw new Error('Create failed');
        showToast('Payment gateway created successfully');
      }
      setModal(false);
      setEditingId(null);
      setForm({ ...EMPTY_FORM });
      fetchGateways();
    } catch {
      showToast(editingId ? 'Failed to update gateway' : 'Failed to create gateway', 'error');
    } finally {
      setSaving(false);
    }
  }, [form, editingId, fetchGateways]);

  const handleToggle = useCallback(async (gw: PaymentGatewaySettings) => {
    try {
      const res = await fetch(`${API_URL}/v1/payment-gateway-settings/${gw.id}/toggle`, {
        method: 'PATCH',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Toggle failed');
      setGateways((prev) =>
        prev.map((g) => (g.id === gw.id ? { ...g, enabled: !g.enabled } : g)),
      );
      showToast(`${gw.displayName} ${gw.enabled ? 'disabled' : 'enabled'}`);
    } catch {
      showToast('Failed to toggle gateway', 'error');
    }
  }, []);

  const handleDelete = useCallback(async (gw: PaymentGatewaySettings) => {
    if (!confirm(`Are you sure you want to delete "${gw.displayName}"?`)) return;
    try {
      const res = await fetch(`${API_URL}/v1/payment-gateway-settings/${gw.id}`, {
        method: 'DELETE',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Delete failed');
      setGateways((prev) => prev.filter((g) => g.id !== gw.id));
      showToast('Payment gateway deleted');
    } catch {
      showToast('Failed to delete gateway', 'error');
    }
  }, []);

  const copyWebhookUrl = (url: string) => {
    navigator.clipboard.writeText(url);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const columns: Column<PaymentGatewaySettings>[] = useMemo(
    () => [
      {
        key: 'provider',
        label: 'Provider',
        render: (row) => (
          <span className="font-semibold text-slate-900">{row.provider}</span>
        ),
      },
      { key: 'displayName', label: 'Display Name' },
      {
        key: 'environment',
        label: 'Environment',
        render: (row) => (
          <span
            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
              row.environment === 'live'
                ? 'bg-amber-100 text-amber-700'
                : 'bg-blue-100 text-blue-700'
            }`}
          >
            {row.environment}
          </span>
        ),
      },
      {
        key: 'enabled',
        label: 'Status',
        render: (row) => (
          <StatusBadge status={row.enabled ? 'active' : 'inactive'} />
        ),
      },
      {
        key: 'supportedCurrencies',
        label: 'Currencies',
        render: (row) => (
          <div className="flex flex-wrap gap-1">
            {(row.supportedCurrencies || '')
              .split(',')
              .filter(Boolean)
              .map((c) => (
                <span
                  key={c.trim()}
                  className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-slate-100 text-slate-600"
                >
                  {c.trim()}
                </span>
              ))}
          </div>
        ),
      },
      {
        key: 'id',
        label: 'Actions',
        render: (row) => (
          <div className="flex items-center gap-2">
            <button
              className="inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium bg-indigo-50 text-indigo-600 hover:bg-indigo-100 transition-all"
              onClick={() => openEdit(row)}
            >
              Edit
            </button>
            <button
              className={`inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                row.enabled
                  ? 'bg-amber-50 text-amber-600 hover:bg-amber-100'
                  : 'bg-emerald-50 text-emerald-600 hover:bg-emerald-100'
              }`}
              onClick={() => handleToggle(row)}
            >
              {row.enabled ? 'Disable' : 'Enable'}
            </button>
            <button
              className="inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-medium bg-red-50 text-red-600 hover:bg-red-100 transition-all"
              onClick={() => handleDelete(row)}
            >
              Delete
            </button>
          </div>
        ),
      },
    ],
    [handleToggle, handleDelete],
  );

  /* ── Input class helpers ─────────────────────────────────────────── */
  const inputCls =
    'w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm placeholder:text-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none transition-all';
  const labelCls = 'block text-sm font-medium text-slate-700';

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
      {/* Toast */}
      {toast && (
        <div
          className={`fixed top-4 right-4 z-50 flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg border max-w-sm ${
            toast.type === 'error'
              ? 'bg-red-50 border-red-200 text-red-800'
              : 'bg-emerald-50 border-emerald-200 text-emerald-800'
          }`}
        >
          {toast.type === 'error' ? (
            <svg className="w-5 h-5 shrink-0 text-red-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
            </svg>
          ) : (
            <svg className="w-5 h-5 shrink-0 text-emerald-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          )}
          <p className="text-sm font-medium flex-1">{toast.message}</p>
          <button onClick={() => setToast(null)} className="shrink-0 opacity-50 hover:opacity-100">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      )}

      <div className="max-w-7xl mx-auto space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Payment Gateway Settings</h1>
            <p className="mt-1 text-sm text-slate-500">
              Configure payment providers (Paystack, Stripe, Flutterwave), credentials, and callback URLs
            </p>
          </div>
          <button
            className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-indigo-600 text-white hover:bg-indigo-700 shadow-sm transition-all duration-200"
            onClick={openCreate}
          >
            <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            Add Gateway
          </button>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {/* Table */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <DataTable data={gateways} columns={columns} />
        </div>

        {/* Create / Edit Modal */}
        <FormModal
          open={modal}
          onClose={() => {
            setModal(false);
            setEditingId(null);
          }}
          title={editingId ? 'Edit Payment Gateway' : 'Add Payment Gateway'}
          onSubmit={handleSave}
          submitLabel={editingId ? 'Save Changes' : 'Create Gateway'}
          loading={saving}
        >
          <div className="space-y-4">
            {/* Row 1: Provider + Display Name + Environment */}
            <div className="grid grid-cols-3 gap-3">
              <div className="space-y-1">
                <label className={labelCls}>Provider <span className="text-red-500">*</span></label>
                {editingId ? (
                  <input className={inputCls + ' bg-slate-50'} value={form.provider} disabled />
                ) : (
                  <select className={inputCls} value={form.provider}
                    onChange={(e) => {
                      const p = e.target.value;
                      const preset = PROVIDER_PRESETS[p] || {};
                      setForm((f) => ({ ...f, provider: p, ...preset }));
                    }}>
                    <option value="paystack">Paystack</option>
                    <option value="stripe">Stripe</option>
                    <option value="flutterwave">Flutterwave</option>
                  </select>
                )}
              </div>
              <div className="space-y-1">
                <label className={labelCls}>Display Name <span className="text-red-500">*</span></label>
                <input className={inputCls} value={form.displayName}
                  onChange={(e) => setForm((f) => ({ ...f, displayName: e.target.value }))} placeholder="Paystack" />
              </div>
              <div className="space-y-1">
                <label className={labelCls}>Environment</label>
                <select className={inputCls} value={form.environment}
                  onChange={(e) => setForm((f) => ({ ...f, environment: e.target.value }))}>
                  <option value="test">Test</option>
                  <option value="live">Live</option>
                </select>
              </div>
            </div>

            {/* Row 2: Secret Key + Public Key */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <label className={labelCls}>Secret Key</label>
                <div className="relative">
                  <input type={showSecret ? 'text' : 'password'} className={inputCls + ' pr-16'}
                    value={form.secretKey} placeholder={editingId ? '••••••••' : 'sk_test_xxxxxxxx'}
                    onChange={(e) => setForm((f) => ({ ...f, secretKey: e.target.value }))} />
                  <button type="button" onClick={() => setShowSecret((s) => !s)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 px-2 py-0.5 rounded text-xs font-medium text-indigo-600 hover:bg-indigo-50">
                    {showSecret ? 'Hide' : 'Show'}
                  </button>
                </div>
                {editingId && <p className="text-[11px] text-slate-400">Leave empty to keep current</p>}
              </div>
              <div className="space-y-1">
                <label className={labelCls}>Public Key</label>
                <input className={inputCls} value={form.publicKey} placeholder="pk_test_xxxxxxxx"
                  onChange={(e) => setForm((f) => ({ ...f, publicKey: e.target.value }))} />
              </div>
            </div>

            {/* Row 3: Base URL + Callback URL */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <label className={labelCls}>Base URL</label>
                <input className={inputCls} value={form.baseUrl} placeholder="https://api.paystack.co"
                  onChange={(e) => setForm((f) => ({ ...f, baseUrl: e.target.value }))} />
              </div>
              <div className="space-y-1">
                <label className={labelCls}>Callback URL</label>
                <input className={inputCls} value={form.callbackUrl} placeholder="http://localhost:3000/billing?verify=true"
                  onChange={(e) => setForm((f) => ({ ...f, callbackUrl: e.target.value }))} />
              </div>
            </div>

            {/* Row 4: Webhook URL + Currencies */}
            <div className="grid grid-cols-2 gap-3">
              {form.provider && (
                <div className="space-y-1">
                  <label className={labelCls}>Webhook URL</label>
                  <div className="flex items-center gap-1.5">
                    <input className={inputCls + ' bg-slate-50 text-slate-500 flex-1 text-xs'}
                      value={webhookUrlFor(form.provider)} readOnly />
                    <button type="button" onClick={() => copyWebhookUrl(webhookUrlFor(form.provider))}
                      className={`shrink-0 px-2.5 py-2.5 rounded-lg text-xs font-medium transition-all ${
                        copied ? 'bg-emerald-100 text-emerald-700' : 'bg-indigo-50 text-indigo-600 hover:bg-indigo-100'}`}>
                      {copied ? 'Copied' : 'Copy'}
                    </button>
                  </div>
                  <p className="text-[11px] text-slate-400">Set this in your provider&apos;s webhook config</p>
                </div>
              )}
              <div className="space-y-1">
                <label className={labelCls}>Supported Currencies</label>
                <input className={inputCls} value={form.supportedCurrencies} placeholder="NGN, USD, GHS, ZAR, KES"
                  onChange={(e) => setForm((f) => ({ ...f, supportedCurrencies: e.target.value }))} />
                <p className="text-[11px] text-slate-400">Comma-separated</p>
              </div>
            </div>

            {/* Row 5: Default Currency + Extra Config */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <label className={labelCls}>Default Currency</label>
                <select className={inputCls} value={form.defaultCurrency}
                  onChange={(e) => setForm((f) => ({ ...f, defaultCurrency: e.target.value }))}>
                  {(form.supportedCurrencies || 'NGN').split(',').map(c => c.trim()).filter(Boolean).map(c => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
                <p className="text-[11px] text-slate-400">Used as default for invoices and billing</p>
              </div>
              <div className="space-y-1">
                <label className={labelCls}>Extra Config (JSON)</label>
                <textarea className={inputCls + ' font-mono text-xs'} rows={3}
                  value={form.extraConfig}
                  placeholder={form.provider === 'stripe' ? '{"webhookSecret":"whsec_xxx"}' : form.provider === 'flutterwave' ? '{"encryptionKey":"FLWSECK_xxx"}' : '{}'}
                  onChange={(e) => setForm((f) => ({ ...f, extraConfig: e.target.value }))} />
                <p className="text-[11px] text-slate-400">
                  {form.provider === 'stripe' && 'Add webhookSecret for Stripe signature verification'}
                  {form.provider === 'flutterwave' && 'Add encryptionKey for Flutterwave encryption'}
                  {form.provider === 'paystack' && 'Optional provider-specific configuration'}
                </p>
              </div>
            </div>

            {/* Row 6: Enabled toggle */}
            <div className="flex items-center justify-between pt-2 border-t border-slate-100">
              <div>
                <label className={labelCls}>Enabled</label>
                <p className="text-[11px] text-slate-400 mt-0.5">Accept payments through this gateway</p>
              </div>
              <button type="button" onClick={() => setForm((f) => ({ ...f, enabled: !f.enabled }))}
                className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
                  form.enabled ? 'bg-indigo-600' : 'bg-slate-200'}`}>
                <span className={`pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow transition ${
                  form.enabled ? 'translate-x-5' : 'translate-x-0'}`} />
              </button>
            </div>
          </div>
        </FormModal>
      </div>
    </div>
  );
}
