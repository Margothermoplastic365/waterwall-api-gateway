'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import { apiClient } from '@gateway/shared-ui/lib/api-client';
import { DataTable, StatusBadge } from '@gateway/shared-ui';
import FormModal from '@gateway/shared-ui/components/FormModal';
import type { Column } from '@gateway/shared-ui/components/DataTable';

interface Application {
  id: string;
  name: string;
  description: string;
  status: string;
  callbackUrls: string[];
  createdAt: string;
}

interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  environmentSlug: string;
  status: string;
  lastUsedAt: string | null;
  createdAt: string;
  [key: string]: unknown;
}

interface Subscription {
  id: string;
  apiId?: string;
  apiName: string;
  planName: string;
  environmentSlug?: string;
  status: string;
  createdAt: string;
  [key: string]: unknown;
}

interface Certificate {
  id: string;
  applicationId: string;
  subjectCn: string;
  fingerprint: string;
  issuer: string;
  expiresAt: string | null;
  status: string;
  createdAt: string;
  [key: string]: unknown;
}

type Tab = 'overview' | 'keys' | 'basic-auth' | 'certificates' | 'oauth2' | 'subscriptions' | 'mock-testing';

export default function AppDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const [app, setApp] = useState<Application | null>(null);
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [subs, setSubs] = useState<Subscription[]>([]);
  const [certs, setCerts] = useState<Certificate[]>([]);
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Generate key modal
  const [genModalOpen, setGenModalOpen] = useState(false);
  const [genKeyName, setGenKeyName] = useState('');
  const [genKeyEnv, setGenKeyEnv] = useState('dev');
  const [generating, setGenerating] = useState(false);
  const [generatedKey, setGeneratedKey] = useState('');
  const [showKeyModal, setShowKeyModal] = useState(false);
  const [copied, setCopied] = useState(false);

  // Basic Auth state
  const [basicAuthConfigured, setBasicAuthConfigured] = useState(false);
  const [basicAuthLoading, setBasicAuthLoading] = useState(false);
  const [basicAuthSecret, setBasicAuthSecret] = useState<{ clientId: string; clientSecret: string } | null>(null);
  const [showBasicAuthModal, setShowBasicAuthModal] = useState(false);
  const [basicAuthCopied, setBasicAuthCopied] = useState('');

  // Certificate upload state
  const [certModalOpen, setCertModalOpen] = useState(false);
  const [certPem, setCertPem] = useState('');
  const [certUploading, setCertUploading] = useState(false);

  // Mock testing state
  const [mockEnabled, setMockEnabled] = useState(false);
  const [mockEndpoint, setMockEndpoint] = useState('');
  const [mockMethod, setMockMethod] = useState('GET');
  const [mockResponse, setMockResponse] = useState<string | null>(null);
  const [mockIsMocked, setMockIsMocked] = useState(false);
  const [mockLoading, setMockLoading] = useState(false);

  const fetchApp = useCallback(async () => {
    try {
      const data = await apiClient<Application>(`/v1/applications/${id}`);
      setApp(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load application');
    } finally {
      setLoading(false);
    }
  }, [id]);

  const fetchKeys = useCallback(async () => {
    try {
      const data = await apiClient<ApiKey[] | { content: ApiKey[] }>(`/v1/applications/${id}/api-keys`);
      setKeys(Array.isArray(data) ? data : data.content ?? []);
    } catch {
      // keys may not be loaded yet
    }
  }, [id]);

  const fetchSubs = useCallback(async () => {
    try {
      const data = await apiClient<Subscription[] | { content: Subscription[] }>(`/v1/subscriptions?applicationId=${id}`);
      setSubs(Array.isArray(data) ? data : data.content ?? []);
    } catch {
      // subscriptions may not be loaded yet
    }
  }, [id]);

  const fetchCerts = useCallback(async () => {
    try {
      const data = await apiClient<Certificate[] | { content: Certificate[] }>(`/v1/applications/${id}/certificates`);
      setCerts(Array.isArray(data) ? data : data.content ?? []);
    } catch {
      // certificates may not be loaded yet
    }
  }, [id]);

  const fetchBasicAuthStatus = useCallback(async () => {
    try {
      const data = await apiClient<{ configured: boolean }>(`/v1/applications/${id}/basic-auth`);
      setBasicAuthConfigured(data.configured);
    } catch {
      // basic auth status may not be available
    }
  }, [id]);

  useEffect(() => {
    fetchApp();
    fetchKeys();
    fetchSubs();
    fetchCerts();
    fetchBasicAuthStatus();
  }, [fetchApp, fetchKeys, fetchSubs, fetchCerts, fetchBasicAuthStatus]);

  // ── API Key handlers ──────────────────────────────────────────────

  const handleGenerateKey = async () => {
    if (!genKeyName.trim()) return;
    setGenerating(true);
    try {
      const result = await apiClient<{ fullKey: string }>(`/v1/applications/${id}/api-keys`, {
        method: 'POST',
        body: JSON.stringify({ name: genKeyName, environmentSlug: genKeyEnv }),
      });
      setGeneratedKey(result.fullKey);
      setGenModalOpen(false);
      setGenKeyName('');
      setGenKeyEnv('dev');
      setShowKeyModal(true);
      fetchKeys();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to generate key');
    } finally {
      setGenerating(false);
    }
  };

  const handleRevokeKey = async (keyId: string) => {
    if (!confirm('Are you sure you want to revoke this key?')) return;
    try {
      await apiClient(`/v1/applications/${id}/api-keys/${keyId}/revoke`, { method: 'POST' });
      fetchKeys();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to revoke key');
    }
  };

  const handleRotateKey = async (keyId: string) => {
    if (!confirm('Rotate this key? The old key will be revoked.')) return;
    try {
      const result = await apiClient<{ newFullKey: string }>(`/v1/applications/${id}/api-keys/${keyId}/rotate`, {
        method: 'POST',
      });
      setGeneratedKey(result.newFullKey);
      setShowKeyModal(true);
      fetchKeys();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to rotate key');
    }
  };

  const copyToClipboard = (text?: string) => {
    navigator.clipboard.writeText(text ?? generatedKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // ── Basic Auth handlers ───────────────────────────────────────────

  const handleGenerateBasicAuth = async () => {
    if (basicAuthConfigured && !confirm('This will regenerate your Basic Auth secret. The old secret will stop working immediately. Continue?')) return;
    setBasicAuthLoading(true);
    try {
      const result = await apiClient<{ clientId: string; clientSecret: string }>(`/v1/applications/${id}/basic-auth`, {
        method: 'POST',
      });
      setBasicAuthSecret(result);
      setShowBasicAuthModal(true);
      setBasicAuthConfigured(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to generate Basic Auth credentials');
    } finally {
      setBasicAuthLoading(false);
    }
  };

  const handleRevokeBasicAuth = async () => {
    if (!confirm('Revoke Basic Auth credentials? Applications using these credentials will lose access.')) return;
    try {
      await apiClient(`/v1/applications/${id}/basic-auth`, { method: 'DELETE' });
      setBasicAuthConfigured(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to revoke Basic Auth');
    }
  };

  const copyBasicAuth = (field: string, value: string) => {
    navigator.clipboard.writeText(value);
    setBasicAuthCopied(field);
    setTimeout(() => setBasicAuthCopied(''), 2000);
  };

  // ── Certificate handlers ──────────────────────────────────────────

  const handleUploadCert = async () => {
    if (!certPem.trim()) return;
    setCertUploading(true);
    try {
      await apiClient(`/v1/applications/${id}/certificates`, {
        method: 'POST',
        body: JSON.stringify({ certificatePem: certPem }),
      });
      setCertModalOpen(false);
      setCertPem('');
      fetchCerts();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to upload certificate');
    } finally {
      setCertUploading(false);
    }
  };

  const handleRevokeCert = async (certId: string) => {
    if (!confirm('Revoke this certificate? It will no longer be accepted for mTLS authentication.')) return;
    try {
      await apiClient(`/v1/applications/${id}/certificates/${certId}`, { method: 'DELETE' });
      fetchCerts();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to revoke certificate');
    }
  };

  // ── Mock testing handler ──────────────────────────────────────────

  const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';
  const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';
  const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

  const handleMockRequest = async () => {
    if (!mockEndpoint.trim()) return;
    setMockLoading(true);
    setMockResponse(null);
    setMockIsMocked(false);
    try {
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      };
      const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
      if (token) headers['Authorization'] = `Bearer ${token}`;
      if (mockEnabled) headers['X-Mock-Mode'] = 'true';

      const res = await fetch(`${API_BASE}${mockEndpoint}`, {
        method: mockMethod,
        headers,
      });
      const isMocked = res.headers.get('X-Mock') === 'true';
      setMockIsMocked(isMocked);
      const text = await res.text();
      try {
        const json = JSON.parse(text);
        setMockResponse(JSON.stringify(json, null, 2));
      } catch {
        setMockResponse(text);
      }
    } catch (err: unknown) {
      setMockResponse(err instanceof Error ? err.message : 'Request failed');
    } finally {
      setMockLoading(false);
    }
  };

  const methodColors: Record<string, string> = {
    GET: 'bg-emerald-600',
    POST: 'bg-blue-600',
    PUT: 'bg-amber-600',
    DELETE: 'bg-red-600',
    PATCH: 'bg-orange-600',
  };

  if (loading) {
    return (
      <div className="animate-pulse">
        <div className="h-4 w-32 bg-slate-100 rounded mb-6" />
        <div className="h-7 w-56 bg-slate-100 rounded-lg mb-2" />
        <div className="h-5 w-16 bg-slate-50 rounded-full mb-8" />
        <div className="flex gap-4 border-b border-slate-200 mb-6">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-10 w-24 bg-slate-50 rounded" />
          ))}
        </div>
        <div className="bg-white rounded-xl border border-slate-200 p-6 h-48" />
      </div>
    );
  }

  if (error && !app) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <div className="w-14 h-14 rounded-2xl bg-red-50 flex items-center justify-center mb-4">
          <svg className="w-7 h-7 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
          </svg>
        </div>
        <p className="text-sm text-red-600 font-medium">{error}</p>
      </div>
    );
  }

  if (!app) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <div className="w-14 h-14 rounded-2xl bg-slate-100 flex items-center justify-center mb-4">
          <svg className="w-7 h-7 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <p className="text-sm text-slate-500">Application not found</p>
      </div>
    );
  }

  const tabs: { key: Tab; label: string; count?: number }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'keys', label: 'API Keys', count: keys.length },
    { key: 'basic-auth', label: 'Basic Auth' },
    { key: 'certificates', label: 'mTLS Certs', count: certs.length },
    { key: 'oauth2', label: 'OAuth2 / JWT' },
    { key: 'subscriptions', label: 'Subscriptions', count: subs.length },
    { key: 'mock-testing', label: 'Mock Testing' },
  ];

  const ENV_BADGE: Record<string, string> = {
    dev: 'bg-blue-100 text-blue-700',
    uat: 'bg-amber-100 text-amber-700',
    staging: 'bg-purple-100 text-purple-700',
    prod: 'bg-emerald-100 text-emerald-700',
  };

  const keyColumns: Column<ApiKey>[] = [
    { key: 'name', label: 'Name' },
    { key: 'environmentSlug', label: 'Environment',
      render: (k) => {
        const env = k.environmentSlug || 'dev';
        return <span className={`text-[11px] font-bold px-2 py-0.5 rounded-full ${ENV_BADGE[env] || ENV_BADGE.dev}`}>{env.toUpperCase()}</span>;
      },
    },
    { key: 'keyPrefix', label: 'Prefix', render: (k) => <code className="text-xs font-mono bg-slate-100 px-2 py-0.5 rounded text-slate-600">{k.keyPrefix}...</code> },
    { key: 'status', label: 'Status', render: (k) => <StatusBadge status={k.status} size="sm" /> },
    {
      key: 'lastUsedAt',
      label: 'Last Used',
      render: (k) => <span className="text-slate-500">{k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleDateString() : 'Never'}</span>,
    },
    {
      key: 'createdAt',
      label: 'Created',
      render: (k) => <span className="text-slate-500">{new Date(k.createdAt).toLocaleDateString()}</span>,
    },
    {
      key: 'actions',
      label: 'Actions',
      render: (k) => (
        <div className="flex gap-2">
          <button
            onClick={() => handleRotateKey(k.id)}
            className="px-2.5 py-1 text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200 rounded-md hover:bg-amber-100 transition-colors"
          >
            Rotate
          </button>
          {k.status !== 'REVOKED' && (
            <button
              onClick={() => handleRevokeKey(k.id)}
              className="px-2.5 py-1 text-xs font-medium bg-red-50 text-red-700 border border-red-200 rounded-md hover:bg-red-100 transition-colors"
            >
              Revoke
            </button>
          )}
        </div>
      ),
    },
  ];

  const certColumns: Column<Certificate>[] = [
    { key: 'subjectCn', label: 'Subject CN', render: (c) => <code className="text-xs font-mono bg-slate-100 px-2 py-0.5 rounded text-slate-600">{c.subjectCn}</code> },
    {
      key: 'fingerprint',
      label: 'Fingerprint',
      render: (c) => <code className="text-xs font-mono bg-slate-100 px-2 py-0.5 rounded text-slate-600">{c.fingerprint.substring(0, 16)}...</code>,
    },
    { key: 'issuer', label: 'Issuer', render: (c) => <span className="text-xs text-slate-500 truncate max-w-[200px] block">{c.issuer}</span> },
    { key: 'status', label: 'Status', render: (c) => <StatusBadge status={c.status} size="sm" /> },
    {
      key: 'expiresAt',
      label: 'Expires',
      render: (c) => <span className="text-slate-500">{c.expiresAt ? new Date(c.expiresAt).toLocaleDateString() : 'N/A'}</span>,
    },
    {
      key: 'createdAt',
      label: 'Uploaded',
      render: (c) => <span className="text-slate-500">{new Date(c.createdAt).toLocaleDateString()}</span>,
    },
    {
      key: 'actions',
      label: 'Actions',
      render: (c) =>
        c.status !== 'REVOKED' ? (
          <button
            onClick={() => handleRevokeCert(c.id)}
            className="px-2.5 py-1 text-xs font-medium bg-red-50 text-red-700 border border-red-200 rounded-md hover:bg-red-100 transition-colors"
          >
            Revoke
          </button>
        ) : null,
    },
  ];

  const subColumns: Column<Subscription>[] = [
    { key: 'apiName', label: 'API Name',
      render: (s) => (
        <a href={`/api-catalog/${s.apiId || ''}`} className="text-blue-600 hover:text-blue-700 font-medium no-underline">
          {s.apiName || 'Unknown API'}
        </a>
      ),
    },
    { key: 'planName', label: 'Plan' },
    { key: 'status', label: 'Status', render: (s) => <StatusBadge status={s.status} size="sm" /> },
    {
      key: 'createdAt',
      label: 'Created',
      render: (s) => <span className="text-slate-500">{new Date(s.createdAt).toLocaleDateString()}</span>,
    },
    {
      key: 'docs',
      label: '',
      render: (s) => (
        <a href={`/api-catalog/${s.apiId || ''}#api-docs`}
          className="px-2.5 py-1 text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200 rounded-md hover:bg-blue-100 transition-colors no-underline inline-block">
          View Docs
        </a>
      ),
    },
  ];

  return (
    <div>
      {/* Breadcrumb */}
      <div className="mb-5">
        <a href="/apps" className="inline-flex items-center gap-1.5 text-sm text-blue-600 hover:text-blue-700 font-medium transition-colors">
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" />
          </svg>
          Back to Applications
        </a>
      </div>

      {/* Header */}
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900 mb-2">{app.name}</h1>
          <StatusBadge status={app.status} />
        </div>
      </div>

      {error && (
        <div className="mb-5 px-4 py-3 bg-red-50 border border-red-200 text-red-600 rounded-lg text-sm flex items-start gap-2">
          <svg className="w-4 h-4 mt-0.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
          </svg>
          {error}
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-0 border-b border-slate-200 mb-6 overflow-x-auto">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-5 py-3 text-sm font-medium border-b-2 transition-colors -mb-px flex items-center gap-2 whitespace-nowrap ${
              activeTab === tab.key
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-slate-400 hover:text-slate-600'
            }`}
          >
            {tab.label}
            {tab.count !== undefined && (
              <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded-full ${
                activeTab === tab.key ? 'bg-blue-100 text-blue-700' : 'bg-slate-100 text-slate-500'
              }`}>
                {tab.count}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Overview Tab */}
      {activeTab === 'overview' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-100">
            <h3 className="text-sm font-semibold text-slate-700">Application Details</h3>
          </div>
          <div className="p-6">
            <dl className="grid grid-cols-[140px_1fr] gap-x-4 gap-y-4 text-sm">
              <dt className="text-slate-500 font-medium">Name</dt>
              <dd className="text-slate-900">{app.name}</dd>

              <dt className="text-slate-500 font-medium">Description</dt>
              <dd className="text-slate-900">{app.description || <span className="text-slate-400 italic">No description</span>}</dd>

              <dt className="text-slate-500 font-medium">Status</dt>
              <dd><StatusBadge status={app.status} size="sm" /></dd>

              <dt className="text-slate-500 font-medium">Callback URLs</dt>
              <dd className="text-slate-900">
                {app.callbackUrls?.length > 0 ? (
                  <div className="flex flex-col gap-1">
                    {app.callbackUrls.map((url, i) => (
                      <code key={i} className="text-xs font-mono bg-slate-50 px-2 py-1 rounded border border-slate-100 text-slate-600 break-all">
                        {url}
                      </code>
                    ))}
                  </div>
                ) : (
                  <span className="text-slate-400 italic">None configured</span>
                )}
              </dd>

              <dt className="text-slate-500 font-medium">App ID</dt>
              <dd className="text-slate-900"><code className="text-xs font-mono bg-slate-50 px-2 py-1 rounded border border-slate-100 text-slate-600 break-all select-all">{app.id}</code></dd>

              <dt className="text-slate-500 font-medium">Created</dt>
              <dd className="text-slate-900">{new Date(app.createdAt).toLocaleString()}</dd>

              <dt className="text-slate-500 font-medium">Auth Methods</dt>
              <dd className="flex flex-wrap gap-2">
                <span className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold bg-blue-50 text-blue-700 border border-blue-200">API Key</span>
                {basicAuthConfigured && (
                  <span className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold bg-purple-50 text-purple-700 border border-purple-200">Basic Auth</span>
                )}
                {certs.filter(c => c.status === 'ACTIVE').length > 0 && (
                  <span className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">mTLS</span>
                )}
                <span className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">OAuth2 / JWT</span>
              </dd>
            </dl>
          </div>
        </div>
      )}

      {/* API Keys Tab */}
      {activeTab === 'keys' && (
        <div>
          <div className="flex justify-between items-center mb-4">
            <div>
              <p className="text-sm text-slate-500">{keys.length} API key{keys.length !== 1 ? 's' : ''}</p>
              <p className="text-xs text-slate-400 mt-1">Send API keys via the <code className="bg-slate-100 px-1.5 py-0.5 rounded text-[11px] font-mono">X-API-Key</code> header to <code className="bg-slate-100 px-1.5 py-0.5 rounded text-[11px] font-mono">{GATEWAY_URL}</code></p>
            </div>
            <button
              onClick={() => setGenModalOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors shadow-sm"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
              </svg>
              Generate Key
            </button>
          </div>
          {/* Gateway URL info */}
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-4">
            <div className="flex items-center gap-2 mb-2">
              <svg className="w-4 h-4 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M13.19 8.688a4.5 4.5 0 011.242 7.244l-4.5 4.5a4.5 4.5 0 01-6.364-6.364l1.757-1.757m9.86-1.135a4.5 4.5 0 00-1.242-7.244l-4.5-4.5a4.5 4.5 0 00-6.364 6.364L4.343 8.06" />
              </svg>
              <span className="text-sm font-semibold text-blue-800">Gateway Endpoint</span>
            </div>
            <code className="block text-sm font-mono bg-white border border-blue-200 rounded px-3 py-2 text-blue-900 select-all break-all">{GATEWAY_URL}</code>
            <p className="text-xs text-blue-600 mt-2">All API calls go through the gateway. Use your API key in the <code className="bg-white px-1 rounded text-[11px]">X-API-Key</code> header.</p>
          </div>

          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <DataTable<ApiKey> data={keys} columns={keyColumns} />
          </div>

          {/* Quick usage example */}
          {keys.length > 0 && (
            <div className="mt-4 bg-slate-900 rounded-lg p-4 overflow-auto">
              <p className="text-xs text-slate-400 mb-2">Quick usage example:</p>
              <pre className="text-xs font-mono text-slate-200 whitespace-pre-wrap break-all">{`curl -H "X-API-Key: ${keys[0].keyPrefix}..." "${GATEWAY_URL}/your-api-path"`}</pre>
            </div>
          )}
        </div>
      )}

      {/* Basic Auth Tab */}
      {activeTab === 'basic-auth' && (
        <div className="space-y-5">
          {/* Status + Actions */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="px-6 py-4 border-b border-slate-100">
              <h3 className="text-sm font-semibold text-slate-700">Basic Authentication</h3>
              <p className="text-xs text-slate-400 mt-1">
                HTTP Basic Auth uses a Client ID and Secret encoded as Base64. Simple but effective for server-to-server calls.
              </p>
            </div>
            <div className="p-6">
              <div className={`flex items-center justify-between p-4 rounded-lg border mb-4 ${
                basicAuthConfigured ? 'bg-emerald-50 border-emerald-200' : 'bg-slate-50 border-slate-200'
              }`}>
                <div className="flex items-center gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                    basicAuthConfigured ? 'bg-emerald-100' : 'bg-slate-200'
                  }`}>
                    <svg className={`w-4 h-4 ${basicAuthConfigured ? 'text-emerald-600' : 'text-slate-400'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" />
                    </svg>
                  </div>
                  <div>
                    <span className="text-sm font-semibold text-slate-700">Basic Auth Credentials</span>
                    <span className={`ml-2 inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                      basicAuthConfigured ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-500'
                    }`}>
                      {basicAuthConfigured ? 'Configured' : 'Not Configured'}
                    </span>
                  </div>
                </div>
                <div className="flex gap-2">
                  {basicAuthConfigured && (
                    <button onClick={handleRevokeBasicAuth} className="px-3.5 py-1.5 text-xs font-medium rounded-lg bg-red-50 text-red-700 border border-red-200 hover:bg-red-100 transition-colors">Revoke</button>
                  )}
                  <button onClick={handleGenerateBasicAuth} disabled={basicAuthLoading} className="px-3.5 py-1.5 text-xs font-medium rounded-lg bg-blue-600 text-white hover:bg-blue-700 transition-colors disabled:opacity-50">
                    {basicAuthLoading ? 'Generating...' : basicAuthConfigured ? 'Regenerate Secret' : 'Generate Credentials'}
                  </button>
                </div>
              </div>

              {/* Your credentials */}
              <div className="space-y-2 mb-4">
                <h4 className="text-sm font-semibold text-slate-700">Your Client ID</h4>
                <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <code className="flex-1 text-xs font-mono break-all text-slate-800 select-all">{id}</code>
                  <button onClick={() => copyToClipboard(id)} className="px-3 py-1 text-xs font-medium rounded-md bg-blue-600 text-white hover:bg-blue-700 transition-colors whitespace-nowrap">Copy</button>
                </div>
                <p className="text-xs text-slate-400">Your Client Secret was shown when you generated it. Click &quot;Regenerate Secret&quot; to get a new one.</p>
              </div>
            </div>
          </div>

          {/* Step-by-step guide */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="px-6 py-4 border-b border-slate-100">
              <h3 className="text-sm font-semibold text-slate-700">Integration Guide</h3>
            </div>
            <div className="p-6 space-y-5">
              <div>
                <div className="flex items-center gap-2 mb-2">
                  <span className="w-5 h-5 rounded-full bg-blue-100 text-blue-700 text-[11px] font-bold flex items-center justify-center">1</span>
                  <h4 className="text-sm font-semibold text-slate-700">Generate credentials above</h4>
                </div>
                <p className="text-xs text-slate-500 ml-7">Click &quot;Generate Credentials&quot; to get your Client ID and Secret. Copy and store the secret securely — it is only shown once.</p>
              </div>

              <div>
                <div className="flex items-center gap-2 mb-2">
                  <span className="w-5 h-5 rounded-full bg-blue-100 text-blue-700 text-[11px] font-bold flex items-center justify-center">2</span>
                  <h4 className="text-sm font-semibold text-slate-700">Build the Authorization header</h4>
                </div>
                <p className="text-xs text-slate-500 ml-7 mb-2">Base64-encode the string <code className="bg-slate-100 px-1.5 py-0.5 rounded text-[11px] font-mono">clientId:clientSecret</code> and send it as:</p>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-3 rounded-lg ml-7 overflow-auto">Authorization: Basic {"<base64(clientId:clientSecret)>"}</pre>
              </div>

              <div>
                <div className="flex items-center gap-2 mb-2">
                  <span className="w-5 h-5 rounded-full bg-blue-100 text-blue-700 text-[11px] font-bold flex items-center justify-center">3</span>
                  <h4 className="text-sm font-semibold text-slate-700">Make API calls</h4>
                </div>
              </div>

              {/* cURL */}
              <div className="ml-7">
                <h5 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">cURL</h5>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words">
{`curl -X GET ${GATEWAY_URL}/v1/your-api \\
  -u "${id}:YOUR_SECRET" \\
  -H "Accept: application/json"`}
                </pre>
              </div>

              {/* JavaScript */}
              <div className="ml-7">
                <h5 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">JavaScript (Node.js / Browser)</h5>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words">
{`const clientId = "${id}";
const secret = "YOUR_SECRET";
const encoded = btoa(clientId + ":" + secret);

const res = await fetch("${GATEWAY_URL}/v1/your-api", {
  headers: {
    "Authorization": "Basic " + encoded,
    "Accept": "application/json"
  }
});
const data = await res.json();`}
                </pre>
              </div>

              {/* Python */}
              <div className="ml-7">
                <h5 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Python</h5>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words">
{`import requests
from requests.auth import HTTPBasicAuth

response = requests.get(
    "${GATEWAY_URL}/v1/your-api",
    auth=HTTPBasicAuth("${id}", "YOUR_SECRET")
)
data = response.json()`}
                </pre>
              </div>

              {/* Java */}
              <div className="ml-7">
                <h5 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Java</h5>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words">
{`import java.net.http.*;
import java.util.Base64;

String credentials = "${id}" + ":" + "YOUR_SECRET";
String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("${GATEWAY_URL}/v1/your-api"))
    .header("Authorization", "Basic " + encoded)
    .GET().build();

HttpResponse<String> response = HttpClient.newHttpClient()
    .send(request, HttpResponse.BodyHandlers.ofString());`}
                </pre>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* mTLS Certificates Tab */}
      {activeTab === 'certificates' && (
        <div>
          <div className="flex justify-between items-center mb-4">
            <div>
              <p className="text-sm text-slate-500">{certs.length} certificate{certs.length !== 1 ? 's' : ''}</p>
              <p className="text-xs text-slate-400 mt-1">Upload client certificates for mutual TLS authentication</p>
            </div>
            <button
              onClick={() => setCertModalOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors shadow-sm"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
              </svg>
              Upload Certificate
            </button>
          </div>

          {certs.length === 0 ? (
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-12 text-center">
              <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center mx-auto mb-4">
                <svg className="w-6 h-6 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
                </svg>
              </div>
              <h3 className="text-sm font-semibold text-slate-700 mb-1">No certificates uploaded</h3>
              <p className="text-xs text-slate-400 mb-4">Upload a PEM-encoded X.509 client certificate to enable mTLS authentication.</p>
              <button
                onClick={() => setCertModalOpen(true)}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
              >
                Upload Your First Certificate
              </button>
            </div>
          ) : (
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
              <DataTable<Certificate> data={certs} columns={certColumns} />
            </div>
          )}
        </div>
      )}

      {/* OAuth2 / JWT Tab */}
      {activeTab === 'oauth2' && (() => {
        const issuer = IDENTITY_URL;
        return (
        <div className="space-y-5">
          {/* Your Credentials */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="px-6 py-4 border-b border-slate-100">
              <h3 className="text-sm font-semibold text-slate-700">Your OAuth2 Credentials</h3>
              <p className="text-xs text-slate-400 mt-1">Use these to authenticate via OAuth2 flows or direct JWT login.</p>
            </div>
            <div className="p-6 space-y-3">
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">Client ID (Application ID)</label>
                <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <code className="flex-1 text-xs font-mono break-all text-slate-800 select-all">{id}</code>
                  <button onClick={() => copyToClipboard(id)} className="px-3 py-1 text-xs font-medium rounded-md bg-blue-600 text-white hover:bg-blue-700 transition-colors whitespace-nowrap">Copy</button>
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">Token Endpoint</label>
                <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <code className="flex-1 text-xs font-mono break-all text-slate-800 select-all">{issuer}/oauth2/token</code>
                  <button onClick={() => copyToClipboard(`${issuer}/oauth2/token`)} className="px-3 py-1 text-xs font-medium rounded-md bg-blue-600 text-white hover:bg-blue-700 transition-colors whitespace-nowrap">Copy</button>
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">Login Endpoint (get JWT directly)</label>
                <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <code className="flex-1 text-xs font-mono break-all text-slate-800 select-all">{issuer}/v1/auth/login</code>
                  <button onClick={() => copyToClipboard(`${issuer}/v1/auth/login`)} className="px-3 py-1 text-xs font-medium rounded-md bg-blue-600 text-white hover:bg-blue-700 transition-colors whitespace-nowrap">Copy</button>
                </div>
              </div>
            </div>
          </div>

          {/* All Endpoints Reference */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="px-6 py-4 border-b border-slate-100">
              <h3 className="text-sm font-semibold text-slate-700">OAuth2 Endpoints</h3>
            </div>
            <div className="p-6">
              <dl className="grid grid-cols-[140px_1fr] gap-x-4 gap-y-3 text-sm">
                {[
                  { label: 'Authorization', url: `${issuer}/oauth2/authorize` },
                  { label: 'Token', url: `${issuer}/oauth2/token` },
                  { label: 'JWK Set', url: `${issuer}/oauth2/jwks` },
                  { label: 'Userinfo', url: `${issuer}/userinfo` },
                  { label: 'Login (JWT)', url: `${issuer}/v1/auth/login` },
                ].map((ep) => (
                  <React.Fragment key={ep.label}>
                    <dt className="text-slate-500 font-medium">{ep.label}</dt>
                    <dd><code className="text-xs font-mono bg-slate-50 px-2 py-1 rounded border border-slate-100 text-slate-600 break-all">{ep.url}</code></dd>
                  </React.Fragment>
                ))}
              </dl>
              <div className="mt-4 grid grid-cols-3 gap-3 text-xs">
                <div className="p-3 bg-slate-50 rounded-lg border border-slate-200 text-center">
                  <div className="font-semibold text-slate-700">Access Token TTL</div>
                  <div className="text-slate-500 mt-1">30 minutes</div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg border border-slate-200 text-center">
                  <div className="font-semibold text-slate-700">Refresh Token TTL</div>
                  <div className="text-slate-500 mt-1">7 days</div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg border border-slate-200 text-center">
                  <div className="font-semibold text-slate-700">Algorithm</div>
                  <div className="text-slate-500 mt-1">HS256 / RS256</div>
                </div>
              </div>
            </div>
          </div>

          {/* Integration Guide */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="px-6 py-4 border-b border-slate-100">
              <h3 className="text-sm font-semibold text-slate-700">Integration Guide</h3>
              <p className="text-xs text-slate-400 mt-1">Choose the flow that fits your use case.</p>
            </div>
            <div className="p-6 space-y-6">
              {/* Option A: Direct Login (simplest) */}
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-700">Recommended</span>
                  <h4 className="text-sm font-semibold text-slate-700">Option A: Direct Login (get JWT)</h4>
                </div>
                <p className="text-xs text-slate-500 mb-3">Simplest approach — POST your email and password to get an access token.</p>

                <h5 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">cURL</h5>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words mb-3">
{`# Step 1: Get a JWT token
curl -X POST ${issuer}/v1/auth/login \\
  -H "Content-Type: application/json" \\
  -d '{"email":"your@email.com","password":"your-password"}'

# Response: { "accessToken": "eyJhbG...", "refreshToken": "...", "expiresIn": 1800 }

# Step 2: Use the token to call APIs
curl -X GET ${GATEWAY_URL}/v1/your-api \\
  -H "Authorization: Bearer eyJhbG..."`}
                </pre>

                <h5 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">JavaScript</h5>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words mb-3">
{`// Step 1: Login to get a JWT
const loginRes = await fetch("${issuer}/v1/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email: "your@email.com", password: "your-password" })
});
const { accessToken } = await loginRes.json();

// Step 2: Call APIs with the token
const apiRes = await fetch("${GATEWAY_URL}/v1/your-api", {
  headers: { "Authorization": "Bearer " + accessToken }
});
const data = await apiRes.json();`}
                </pre>

                <h5 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Python</h5>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words">
{`import requests

# Step 1: Login
login = requests.post("${issuer}/v1/auth/login", json={
    "email": "your@email.com",
    "password": "your-password"
})
token = login.json()["accessToken"]

# Step 2: Call APIs
response = requests.get("${GATEWAY_URL}/v1/your-api",
    headers={"Authorization": f"Bearer {token}"}
)
data = response.json()`}
                </pre>
              </div>

              {/* Option B: Client Credentials */}
              <div className="pt-4 border-t border-slate-100">
                <h4 className="text-sm font-semibold text-slate-700 mb-1">Option B: Client Credentials Grant (server-to-server)</h4>
                <p className="text-xs text-slate-500 mb-3">For machine-to-machine calls without user context. Uses your Client ID and Secret.</p>

                <h5 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">cURL</h5>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words mb-3">
{`# Get token via client_credentials grant
curl -X POST ${issuer}/oauth2/token \\
  -H "Content-Type: application/x-www-form-urlencoded" \\
  -d "grant_type=client_credentials" \\
  -d "client_id=${id}" \\
  -d "client_secret=YOUR_CLIENT_SECRET" \\
  -d "scope=openid"

# Response: { "access_token": "eyJhbG...", "token_type": "Bearer", "expires_in": 3600 }

# Use the token
curl -X GET ${GATEWAY_URL}/v1/your-api \\
  -H "Authorization: Bearer eyJhbG..."`}
                </pre>

                <h5 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">JavaScript</h5>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words">
{`const tokenRes = await fetch("${issuer}/oauth2/token", {
  method: "POST",
  headers: { "Content-Type": "application/x-www-form-urlencoded" },
  body: new URLSearchParams({
    grant_type: "client_credentials",
    client_id: "${id}",
    client_secret: "YOUR_CLIENT_SECRET",
    scope: "openid"
  })
});
const { access_token } = await tokenRes.json();

const apiRes = await fetch("${GATEWAY_URL}/v1/your-api", {
  headers: { "Authorization": "Bearer " + access_token }
});`}
                </pre>
              </div>

              {/* Option C: Authorization Code */}
              <div className="pt-4 border-t border-slate-100">
                <h4 className="text-sm font-semibold text-slate-700 mb-1">Option C: Authorization Code Grant (web apps)</h4>
                <p className="text-xs text-slate-500 mb-3">For web applications where users log in via a browser redirect. Most secure for user-facing apps.</p>

                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words">
{`# Step 1: Redirect user to authorize
${issuer}/oauth2/authorize?
  response_type=code&
  client_id=${id}&
  redirect_uri=https://your-app.com/callback&
  scope=openid+profile+email

# Step 2: Exchange code for token (server-side)
curl -X POST ${issuer}/oauth2/token \\
  -H "Content-Type: application/x-www-form-urlencoded" \\
  -d "grant_type=authorization_code" \\
  -d "code=AUTH_CODE_FROM_CALLBACK" \\
  -d "client_id=${id}" \\
  -d "client_secret=YOUR_CLIENT_SECRET" \\
  -d "redirect_uri=https://your-app.com/callback"`}
                </pre>
              </div>

              {/* JWT Claims Reference */}
              <div className="pt-4 border-t border-slate-100">
                <h4 className="text-sm font-semibold text-slate-700 mb-3">JWT Token Claims</h4>
                <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto leading-relaxed whitespace-pre-wrap break-words">
{`{
  "iss": "${issuer}",
  "sub": "<user_id>",
  "email": "<email>",
  "email_verified": true,
  "org_id": "<organization_id>",
  "roles": ["DEVELOPER"],
  "permissions": ["api:read", "api:subscribe"],
  "token_type": "access",
  "jti": "<unique_token_id>",
  "iat": 1711440000,
  "exp": 1711441800
}`}
                </pre>
              </div>
            </div>
          </div>
        </div>
        );
      })()}

      {/* Subscriptions Tab */}
      {activeTab === 'subscriptions' && (
        <div>
          <p className="text-sm text-slate-500 mb-4">{subs.length} subscription{subs.length !== 1 ? 's' : ''}</p>
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <DataTable<Subscription> data={subs} columns={subColumns} />
          </div>
        </div>
      )}

      {/* Mock Testing Tab */}
      {activeTab === 'mock-testing' && (
        <div>
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="px-6 py-4 border-b border-slate-100">
              <h2 className="text-sm font-semibold text-slate-700">Mock Testing</h2>
              <p className="text-xs text-slate-400 mt-1">
                Test your API integration using mock responses. When mock mode is enabled, requests are sent with
                the <code className="bg-slate-100 px-1.5 py-0.5 rounded text-[11px] font-mono">X-Mock-Mode: true</code> header.
              </p>
            </div>

            <div className="p-6">
              {/* Mock Toggle */}
              <div className={`flex items-center justify-between p-4 rounded-lg border mb-6 ${
                mockEnabled ? 'bg-emerald-50 border-emerald-200' : 'bg-slate-50 border-slate-200'
              }`}>
                <div className="flex items-center gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                    mockEnabled ? 'bg-emerald-100' : 'bg-slate-200'
                  }`}>
                    <svg className={`w-4 h-4 ${mockEnabled ? 'text-emerald-600' : 'text-slate-400'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19.8 15.3M14.25 3.104c.251.023.501.05.75.082M19.8 15.3l-1.57.393A9.065 9.065 0 0112 15a9.065 9.065 0 00-6.23.693L5 14.5m14.8.8l1.402 1.402c1.232 1.232.65 3.318-1.067 3.611A48.309 48.309 0 0112 21c-2.773 0-5.491-.235-8.135-.687-1.718-.293-2.3-2.379-1.067-3.61L5 14.5" />
                    </svg>
                  </div>
                  <div>
                    <span className="text-sm font-semibold text-slate-700">Mock Mode</span>
                    <span className={`ml-2 inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                      mockEnabled ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-500'
                    }`}>
                      {mockEnabled ? 'Enabled' : 'Disabled'}
                    </span>
                  </div>
                </div>
                <button
                  onClick={() => setMockEnabled(!mockEnabled)}
                  className={`px-3.5 py-1.5 text-xs font-medium rounded-lg transition-colors ${
                    mockEnabled
                      ? 'bg-red-50 text-red-700 border border-red-200 hover:bg-red-100'
                      : 'bg-blue-600 text-white hover:bg-blue-700'
                  }`}
                >
                  {mockEnabled ? 'Disable Mock' : 'Enable Mock'}
                </button>
              </div>

              {/* Test Console */}
              <div className="flex gap-2 mb-5">
                <select
                  value={mockMethod}
                  onChange={(e) => setMockMethod(e.target.value)}
                  className={`px-3 py-2.5 border border-slate-200 rounded-lg text-sm font-medium text-white cursor-pointer focus:outline-none focus:ring-2 focus:ring-blue-500/20 ${methodColors[mockMethod] || 'bg-slate-600'}`}
                >
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                  <option value="PUT">PUT</option>
                  <option value="DELETE">DELETE</option>
                  <option value="PATCH">PATCH</option>
                </select>
                <input
                  type="text"
                  value={mockEndpoint}
                  onChange={(e) => setMockEndpoint(e.target.value)}
                  placeholder="/v1/your-endpoint"
                  onKeyDown={(e) => { if (e.key === 'Enter') handleMockRequest(); }}
                  className="flex-1 px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                />
                <button
                  onClick={handleMockRequest}
                  disabled={mockLoading || !mockEndpoint.trim()}
                  className="px-5 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-200 disabled:text-slate-400 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg transition-colors whitespace-nowrap"
                >
                  {mockLoading ? (
                    <span className="flex items-center gap-2">
                      <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                      Sending...
                    </span>
                  ) : 'Send Request'}
                </button>
              </div>

              {/* Mock Response */}
              {mockResponse !== null && (
                <div>
                  <div className="flex items-center gap-2 mb-2">
                    <span className="text-sm font-semibold text-slate-700">Response</span>
                    {mockIsMocked && (
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-700 ring-1 ring-inset ring-amber-200">
                        X-Mock: true
                      </span>
                    )}
                  </div>
                  <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto max-h-96 leading-relaxed whitespace-pre-wrap break-words">
                    {mockResponse}
                  </pre>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Generate Key Name Modal */}
      <FormModal
        open={genModalOpen}
        onClose={() => setGenModalOpen(false)}
        title="Generate API Key"
        onSubmit={handleGenerateKey}
        submitLabel="Generate"
        loading={generating}
      >
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Key Name *
            </label>
            <input
              type="text"
              value={genKeyName}
              onChange={(e) => setGenKeyName(e.target.value)}
              placeholder="e.g. Production Key"
              className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Environment
            </label>
            <div className="grid grid-cols-4 gap-2">
              {[
                { slug: 'dev', label: 'DEV', prefix: 'dev_', color: 'blue' },
                { slug: 'uat', label: 'UAT', prefix: 'uat_', color: 'amber' },
                { slug: 'staging', label: 'STG', prefix: 'stg_', color: 'purple' },
                { slug: 'prod', label: 'PROD', prefix: 'live_', color: 'emerald' },
              ].map((env) => (
                <button key={env.slug} type="button" onClick={() => setGenKeyEnv(env.slug)}
                  className={`py-2 rounded-lg text-xs font-bold text-center transition-all border-2 ${
                    genKeyEnv === env.slug
                      ? env.color === 'blue' ? 'border-blue-500 bg-blue-50 text-blue-700'
                      : env.color === 'amber' ? 'border-amber-500 bg-amber-50 text-amber-700'
                      : env.color === 'purple' ? 'border-purple-500 bg-purple-50 text-purple-700'
                      : 'border-emerald-500 bg-emerald-50 text-emerald-700'
                      : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
                  }`}>
                  {env.label}
                  <div className="text-[10px] font-normal mt-0.5 opacity-70">{env.prefix}***</div>
                </button>
              ))}
            </div>
            <p className="text-xs text-slate-400 mt-2">
              Keys are scoped to one environment. A dev key won&apos;t work in prod.
            </p>
          </div>
        </div>
      </FormModal>

      {/* Show Generated Key Modal */}
      <FormModal
        open={showKeyModal}
        onClose={() => {
          setShowKeyModal(false);
          setGeneratedKey('');
          setCopied(false);
        }}
        title="API Key Generated"
      >
        <div>
          <div className="flex items-start gap-2 px-3 py-2.5 bg-amber-50 border border-amber-200 rounded-lg mb-4">
            <svg className="w-4 h-4 text-amber-600 mt-0.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
            </svg>
            <p className="text-sm text-amber-800 font-medium">
              Copy this key now. You will not be able to see it again.
            </p>
          </div>
          <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-lg border border-slate-200">
            <code className="flex-1 text-xs font-mono break-all text-slate-800 select-all">
              {generatedKey}
            </code>
            <button
              onClick={() => copyToClipboard()}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors whitespace-nowrap ${
                copied
                  ? 'bg-emerald-100 text-emerald-700 border border-emerald-200'
                  : 'bg-blue-600 text-white hover:bg-blue-700'
              }`}
            >
              {copied ? 'Copied!' : 'Copy'}
            </button>
          </div>
        </div>
      </FormModal>

      {/* Basic Auth Secret Modal */}
      <FormModal
        open={showBasicAuthModal}
        onClose={() => {
          setShowBasicAuthModal(false);
          setBasicAuthSecret(null);
          setBasicAuthCopied('');
        }}
        title="Basic Auth Credentials Generated"
      >
        <div>
          <div className="flex items-start gap-2 px-3 py-2.5 bg-amber-50 border border-amber-200 rounded-lg mb-4">
            <svg className="w-4 h-4 text-amber-600 mt-0.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
            </svg>
            <p className="text-sm text-amber-800 font-medium">
              Copy these credentials now. The secret will not be shown again.
            </p>
          </div>

          {basicAuthSecret && (
            <div className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">Client ID</label>
                <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <code className="flex-1 text-xs font-mono break-all text-slate-800 select-all">{basicAuthSecret.clientId}</code>
                  <button
                    onClick={() => copyBasicAuth('clientId', basicAuthSecret.clientId)}
                    className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors whitespace-nowrap ${
                      basicAuthCopied === 'clientId' ? 'bg-emerald-100 text-emerald-700 border border-emerald-200' : 'bg-blue-600 text-white hover:bg-blue-700'
                    }`}
                  >
                    {basicAuthCopied === 'clientId' ? 'Copied!' : 'Copy'}
                  </button>
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">Client Secret</label>
                <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <code className="flex-1 text-xs font-mono break-all text-slate-800 select-all">{basicAuthSecret.clientSecret}</code>
                  <button
                    onClick={() => copyBasicAuth('secret', basicAuthSecret.clientSecret)}
                    className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors whitespace-nowrap ${
                      basicAuthCopied === 'secret' ? 'bg-emerald-100 text-emerald-700 border border-emerald-200' : 'bg-blue-600 text-white hover:bg-blue-700'
                    }`}
                  >
                    {basicAuthCopied === 'secret' ? 'Copied!' : 'Copy'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </FormModal>

      {/* Upload Certificate Modal */}
      <FormModal
        open={certModalOpen}
        onClose={() => { setCertModalOpen(false); setCertPem(''); }}
        title="Upload Client Certificate"
        onSubmit={handleUploadCert}
        submitLabel="Upload"
        loading={certUploading}
      >
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1.5">
            Certificate (PEM format) *
          </label>
          <textarea
            value={certPem}
            onChange={(e) => setCertPem(e.target.value)}
            rows={8}
            placeholder={"-----BEGIN CERTIFICATE-----\nMIID...\n-----END CERTIFICATE-----"}
            className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-xs text-slate-700 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all resize-vertical"
          />
          <p className="text-xs text-slate-400 mt-2">Paste your PEM-encoded X.509 client certificate. The Subject CN and fingerprint will be extracted automatically.</p>
        </div>
      </FormModal>
    </div>
  );
}
