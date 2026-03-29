'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { DataTable } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

type Tab = 'reports' | 'classification' | 'gdpr';

interface ComplianceReport {
  id: string;
  type: string;
  score: number;
  status: string;
  generatedAt: string;
  [key: string]: unknown;
}

interface DataClassification {
  apiId: string;
  apiName: string;
  classification: string;
  piiFields: string[];
}

interface ConsentRecord {
  id: string;
  userId: string;
  purpose: string;
  granted: boolean;
  grantedAt: string;
  [key: string]: unknown;
}

const REPORT_TYPES = ['SOC2', 'ISO27001', 'GDPR', 'PCI_DSS'] as const;
const CLASSIFICATIONS = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'] as const;

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

export default function CompliancePage() {
  const [tab, setTab] = useState<Tab>('reports');

  /* Reports */
  const [reports, setReports] = useState<ComplianceReport[]>([]);
  const [reportsLoading, setReportsLoading] = useState(false);
  const [generating, setGenerating] = useState('');

  /* Classification */
  const [classifications, setClassifications] = useState<DataClassification[]>([]);
  const [classLoading, setClassLoading] = useState(false);
  const [selectedApi, setSelectedApi] = useState('');
  const [selectedClassification, setSelectedClassification] = useState<DataClassification | null>(null);
  const [editClass, setEditClass] = useState('');
  const [classSaving, setClassSaving] = useState(false);

  /* GDPR */
  const [consents, setConsents] = useState<ConsentRecord[]>([]);
  const [consentsLoading, setConsentsLoading] = useState(false);
  const [gdprUserId, setGdprUserId] = useState('');
  const [gdprAction, setGdprAction] = useState<'export' | 'delete' | ''>('');
  const [gdprLoading, setGdprLoading] = useState(false);
  const [gdprResult, setGdprResult] = useState('');
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  /* Load reports */
  useEffect(() => {
    if (tab !== 'reports') return;
    setReportsLoading(true);
    fetch(`${API_URL}/v1/compliance/reports`, { headers: authHeaders() })
      .then((r) => r.ok ? r.json() : [])
      .then((data) => setReports(Array.isArray(data) ? data : data.content || data.data || []))
      .catch(() => setReports([]))
      .finally(() => setReportsLoading(false));
  }, [tab]);

  /* Load classifications */
  useEffect(() => {
    if (tab !== 'classification') return;
    setClassLoading(true);
    fetch(`${API_URL}/v1/compliance/data-classification`, { headers: authHeaders() })
      .then((r) => r.ok ? r.json() : [])
      .then((data) => setClassifications(Array.isArray(data) ? data : data.content || data.data || []))
      .catch(() => setClassifications([]))
      .finally(() => setClassLoading(false));
  }, [tab]);

  /* Load consents */
  useEffect(() => {
    if (tab !== 'gdpr') return;
    setConsentsLoading(true);
    fetch(`${API_URL}/v1/compliance/consents`, { headers: authHeaders() })
      .then((r) => r.ok ? r.json() : [])
      .then((data) => setConsents(Array.isArray(data) ? data : data.content || data.data || []))
      .catch(() => setConsents([]))
      .finally(() => setConsentsLoading(false));
  }, [tab]);

  /* Select API for classification */
  useEffect(() => {
    if (selectedApi) {
      const found = classifications.find((c) => c.apiId === selectedApi);
      setSelectedClassification(found || null);
      setEditClass(found?.classification || 'PUBLIC');
    } else {
      setSelectedClassification(null);
    }
  }, [selectedApi, classifications]);

  const handleGenerateReport = useCallback(async (type: string) => {
    setGenerating(type);
    try {
      const res = await fetch(`${API_URL}/v1/compliance/reports/generate`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ type }),
      });
      if (!res.ok) throw new Error('Generate failed');
      const created = await res.json();
      setReports((prev) => [created, ...prev]);
    } catch {
      showToast(`Failed to generate ${type} report`, 'error');
    } finally {
      setGenerating('');
    }
  }, []);

  const handleSaveClassification = useCallback(async () => {
    if (!selectedApi) return;
    setClassSaving(true);
    try {
      const res = await fetch(`${API_URL}/v1/compliance/data-classification/${selectedApi}`, {
        method: 'PUT',
        headers: authHeaders(),
        body: JSON.stringify({ classification: editClass }),
      });
      if (!res.ok) throw new Error('Save failed');
      setClassifications((prev) =>
        prev.map((c) => (c.apiId === selectedApi ? { ...c, classification: editClass } : c)),
      );
    } catch {
      showToast('Failed to save classification', 'error');
    } finally {
      setClassSaving(false);
    }
  }, [selectedApi, editClass]);

  const handleGdprAction = useCallback(async () => {
    if (!gdprUserId || !gdprAction) return;
    setGdprLoading(true);
    setGdprResult('');
    try {
      if (gdprAction === 'export') {
        const res = await fetch(`${API_URL}/v1/compliance/user-data/${gdprUserId}/export`, {
          headers: authHeaders(),
        });
        if (!res.ok) throw new Error('Export failed');
        const data = await res.json();
        setGdprResult(`Export successful. Data: ${JSON.stringify(data).substring(0, 200)}...`);
      } else {
        if (!confirm(`Are you sure you want to delete all data for user ${gdprUserId}? This cannot be undone.`)) {
          setGdprLoading(false);
          return;
        }
        const res = await fetch(`${API_URL}/v1/compliance/user-data/${gdprUserId}`, {
          method: 'DELETE',
          headers: authHeaders(),
        });
        if (!res.ok) throw new Error('Delete failed');
        setGdprResult(`User data for ${gdprUserId} has been deleted.`);
      }
    } catch {
      showToast(`Failed to ${gdprAction} user data`, 'error');
    } finally {
      setGdprLoading(false);
    }
  }, [gdprUserId, gdprAction]);

  const typeBadge = (type: string) => {
    const colors: Record<string, string> = {
      SOC2: 'bg-blue-100 text-blue-700',
      ISO27001: 'bg-purple-100 text-purple-700',
      GDPR: 'bg-green-100 text-green-700',
      PCI_DSS: 'bg-amber-100 text-amber-700',
    };
    return <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${colors[type] || 'bg-slate-100 text-slate-600'}`}>{type}</span>;
  };

  const reportColumns: Column<ComplianceReport>[] = useMemo(
    () => [
      { key: 'type', label: 'Type', render: (row) => typeBadge(row.type) },
      {
        key: 'score',
        label: 'Score',
        render: (row) => (
          <span className={`font-bold ${row.score >= 80 ? 'text-green-600' : row.score >= 60 ? 'text-amber-600' : 'text-red-600'}`}>
            {row.score}%
          </span>
        ),
      },
      {
        key: 'status',
        label: 'Status',
        render: (row) => (
          <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${row.status === 'COMPLIANT' ? 'bg-green-100 text-green-700' : row.status === 'PARTIAL' ? 'bg-amber-100 text-amber-700' : 'bg-red-100 text-red-700'}`}>
            {row.status}
          </span>
        ),
      },
      {
        key: 'generatedAt',
        label: 'Generated',
        render: (row) => <span className="text-slate-500 text-sm">{new Date(row.generatedAt).toLocaleString()}</span>,
      },
    ],
    [],
  );

  const consentColumns: Column<ConsentRecord>[] = useMemo(
    () => [
      { key: 'userId', label: 'User ID' },
      { key: 'purpose', label: 'Purpose' },
      {
        key: 'granted',
        label: 'Granted',
        render: (row) => (
          <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${row.granted ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
            {row.granted ? 'Yes' : 'No'}
          </span>
        ),
      },
      {
        key: 'grantedAt',
        label: 'Date',
        render: (row) => <span className="text-slate-500 text-sm">{new Date(row.grantedAt).toLocaleString()}</span>,
      },
    ],
    [],
  );

  const tabs: { key: Tab; label: string }[] = [
    { key: 'reports', label: 'Reports' },
    { key: 'classification', label: 'Data Classification' },
    { key: 'gdpr', label: 'GDPR' },
  ];

  return (
    <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
      {toast && (<div className={`fixed top-4 right-4 z-50 flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg border max-w-sm ${toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-emerald-50 border-emerald-200 text-emerald-800'}`}>{toast.type === 'error' ? (<svg className="w-5 h-5 shrink-0 text-red-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>) : (<svg className="w-5 h-5 shrink-0 text-emerald-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>)}<p className="text-sm font-medium flex-1">{toast.message}</p><button onClick={() => setToast(null)} className="shrink-0 opacity-50 hover:opacity-100"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg></button></div>)}
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Compliance</h1>
          <p className="mt-1 text-sm text-slate-500">Compliance reports, data classification, and GDPR management</p>
        </div>

        {/* Tabs */}
        <div className="flex gap-1 bg-slate-100 rounded-xl p-1 w-fit">
          {tabs.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`px-4 py-2 text-sm font-medium rounded-lg transition-all duration-200 ${
                tab === key
                  ? 'bg-white text-purple-700 shadow-sm'
                  : 'text-slate-500 hover:text-slate-700 hover:bg-white/50'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {/* Reports Tab */}
        {tab === 'reports' && (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-3">
              {REPORT_TYPES.map((type) => (
                <button
                  key={type}
                  className="inline-flex items-center px-4 py-2 rounded-lg text-sm font-medium border border-slate-200 bg-white text-slate-700 hover:bg-purple-50 hover:text-purple-700 hover:border-purple-200 transition-all duration-200 shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
                  disabled={generating === type}
                  onClick={() => handleGenerateReport(type)}
                >
                  {generating === type ? (
                    <>
                      <div className="w-4 h-4 border-2 border-purple-300 border-t-purple-600 rounded-full animate-spin mr-2" />
                      Generating...
                    </>
                  ) : (
                    `Generate ${type.replace('_', ' ')}`
                  )}
                </button>
              ))}
            </div>
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
              <DataTable data={reports} columns={reportColumns} loading={reportsLoading} />
            </div>
          </div>
        )}

        {/* Data Classification Tab */}
        {tab === 'classification' && (
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            {classLoading ? (
              <div className="flex items-center justify-center min-h-[200px]">
                <div className="w-10 h-10 border-4 border-purple-200 border-t-purple-600 rounded-full animate-spin" />
              </div>
            ) : (
              <>
                <div className="space-y-2">
                  <label className="block text-sm font-medium text-slate-700">Select API</label>
                  <select
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                    value={selectedApi}
                    onChange={(e) => setSelectedApi(e.target.value)}
                  >
                    <option value="">Select an API...</option>
                    {classifications.map((c) => (
                      <option key={c.apiId} value={c.apiId}>{c.apiName}</option>
                    ))}
                  </select>
                </div>

                {selectedClassification && (
                  <div className="mt-6 pt-6 border-t border-slate-100 space-y-4">
                    <div className="space-y-2">
                      <label className="block text-sm font-medium text-slate-700">Classification</label>
                      <select
                        className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                        value={editClass}
                        onChange={(e) => setEditClass(e.target.value)}
                      >
                        {CLASSIFICATIONS.map((c) => (
                          <option key={c} value={c}>{c}</option>
                        ))}
                      </select>
                    </div>
                    <button
                      className="inline-flex items-center px-4 py-2 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200 disabled:opacity-50"
                      onClick={handleSaveClassification}
                      disabled={classSaving}
                    >
                      {classSaving ? (
                        <>
                          <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2" />
                          Saving...
                        </>
                      ) : (
                        'Save Classification'
                      )}
                    </button>

                    {selectedClassification.piiFields && selectedClassification.piiFields.length > 0 && (
                      <div className="mt-6">
                        <h3 className="text-sm font-semibold text-slate-900 mb-3">PII Fields</h3>
                        <div className="flex flex-wrap gap-2">
                          {selectedClassification.piiFields.map((field, i) => (
                            <span key={i} className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-red-100 text-red-700">{field}</span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {classifications.length === 0 && (
                  <div className="flex flex-col items-center justify-center py-12 text-center">
                    <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center mb-3">
                      <svg className="w-6 h-6 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m6.75 12H9.75m3 0h3m-10.125 0A2.625 2.625 0 013 17.625V4.875C3 3.839 3.84 3 4.875 3h4.5c.621 0 1.218.247 1.658.688l6.024 6.024c.44.44.688 1.037.688 1.658v6.005A2.625 2.625 0 0115.12 20H4.875z" /></svg>
                    </div>
                    <p className="text-sm text-slate-500">No API classifications found.</p>
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* GDPR Tab */}
        {tab === 'gdpr' && (
          <div className="space-y-4">
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
              <h3 className="text-base font-semibold text-slate-900 mb-4">User Data Actions</h3>
              <div className="flex flex-wrap gap-4 items-end">
                <div className="flex-1 min-w-[200px] space-y-2">
                  <label className="block text-sm font-medium text-slate-700">User ID</label>
                  <input
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                    value={gdprUserId}
                    onChange={(e) => setGdprUserId(e.target.value)}
                    placeholder="Enter user ID"
                  />
                </div>
                <button
                  className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 shadow-sm transition-all duration-200 disabled:opacity-50"
                  disabled={!gdprUserId || gdprLoading}
                  onClick={() => { setGdprAction('export'); }}
                >
                  Export User Data
                </button>
                <button
                  className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-red-600 text-white hover:bg-red-700 shadow-sm transition-all duration-200 disabled:opacity-50"
                  disabled={!gdprUserId || gdprLoading}
                  onClick={() => { setGdprAction('delete'); }}
                >
                  Delete User Data
                </button>
              </div>
              {gdprResult && (
                <div className="mt-4 rounded-lg bg-blue-50 border border-blue-200 px-4 py-3 text-sm text-blue-700">
                  {gdprResult}
                </div>
              )}
            </div>

            {/* Trigger action when gdprAction is set */}
            {gdprAction && gdprUserId && !gdprLoading && gdprResult === '' && (
              <GdprActionTrigger action={gdprAction} onExecute={handleGdprAction} onReset={() => setGdprAction('')} />
            )}

            <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="px-6 pt-5 pb-3">
                <h3 className="text-base font-semibold text-slate-900">Consent Records</h3>
                <p className="text-sm text-slate-500 mt-1">User consent tracking for data processing</p>
              </div>
              <DataTable data={consents} columns={consentColumns} loading={consentsLoading} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function GdprActionTrigger({
  action,
  onExecute,
  onReset,
}: {
  action: string;
  onExecute: () => void;
  onReset: () => void;
}) {
  useEffect(() => {
    if (action) {
      onExecute();
      onReset();
    }
  }, [action, onExecute, onReset]);
  return null;
}
