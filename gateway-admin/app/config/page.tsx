'use client';

import { useEffect, useState, useCallback } from 'react';
import { get, post } from '@gateway/shared-ui';

interface ApiOption {
  id: string;
  name: string;
}

interface DiffResult {
  additions: string[];
  deletions: string[];
  modifications: string[];
}

export default function ConfigPage() {
  const [apis, setApis] = useState<ApiOption[]>([]);
  const [selectedApi, setSelectedApi] = useState('');
  const [exportContent, setExportContent] = useState('');
  const [importContent, setImportContent] = useState('');
  const [diffContent, setDiffContent] = useState('');
  const [diffResult, setDiffResult] = useState<DiffResult | null>(null);
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [diffing, setDiffing] = useState(false);
  const [message, setMessage] = useState('');
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

  const handleExportAll = useCallback(async () => {
    setExporting(true);
    setMessage('');
    try {
      const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (token) headers['Authorization'] = `Bearer ${token}`;

      const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';
      const res = await fetch(`${API_URL}/v1/config/export/all?format=yaml`, { headers });
      if (!res.ok) throw new Error('Export failed');
      const text = await res.text();

      const blob = new Blob([text], { type: 'application/x-yaml' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'gateway-config.yaml';
      a.click();
      URL.revokeObjectURL(url);
      setMessage('Export downloaded successfully');
    } catch {
      setMessage('Failed to export configuration');
    } finally {
      setExporting(false);
    }
  }, []);

  const handleExportApi = useCallback(async () => {
    if (!selectedApi) {
      showToast('Please select an API', 'error');
      return;
    }
    setExporting(true);
    setMessage('');
    try {
      const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (token) headers['Authorization'] = `Bearer ${token}`;

      const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';
      const res = await fetch(`${API_URL}/v1/config/export/${selectedApi}?format=yaml`, { headers });
      if (!res.ok) throw new Error('Export failed');
      const text = await res.text();
      setExportContent(text);
    } catch {
      setMessage('Failed to export API configuration');
    } finally {
      setExporting(false);
    }
  }, [selectedApi]);

  const handleImport = useCallback(async () => {
    if (!importContent.trim()) {
      showToast('Please enter YAML content to import', 'error');
      return;
    }
    setImporting(true);
    setMessage('');
    try {
      await post('/v1/config/import', { content: importContent });
      setMessage('Configuration imported successfully');
    } catch {
      setMessage('Failed to import configuration');
    } finally {
      setImporting(false);
    }
  }, [importContent]);

  const handleDiff = useCallback(async () => {
    if (!diffContent.trim()) {
      showToast('Please enter YAML content to diff', 'error');
      return;
    }
    setDiffing(true);
    setDiffResult(null);
    try {
      const result = await post<DiffResult>('/v1/config/diff', { content: diffContent });
      setDiffResult(result);
    } catch {
      setMessage('Failed to compute diff');
    } finally {
      setDiffing(false);
    }
  }, [diffContent]);

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
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Config-as-Code</h1>
          <p className="mt-1 text-sm text-slate-500">Export, import, and diff gateway configurations</p>
        </div>

        {/* Message Banner */}
        {message && (
          <div className={`rounded-xl border px-4 py-3 text-sm font-medium ${
            message.includes('Failed')
              ? 'bg-red-50 border-red-200 text-red-700'
              : 'bg-green-50 border-green-200 text-green-700'
          }`}>
            {message}
          </div>
        )}

        {/* Export Section */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-base font-semibold text-slate-900 mb-4">Export</h3>
          <div className="flex flex-wrap gap-4 items-end mb-4">
            <button
              className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200 disabled:opacity-50"
              onClick={handleExportAll}
              disabled={exporting}
            >
              {exporting ? (
                <>
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2" />
                  Exporting...
                </>
              ) : (
                'Export All'
              )}
            </button>
            <div className="min-w-[250px] space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">Select API</label>
              <select
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all"
                value={selectedApi}
                onChange={(e) => setSelectedApi(e.target.value)}
              >
                <option value="">-- Select API --</option>
                {apis.map((a) => (
                  <option key={a.id} value={a.id}>{a.name}</option>
                ))}
              </select>
            </div>
            <button
              className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 shadow-sm transition-all duration-200 disabled:opacity-50"
              onClick={handleExportApi}
              disabled={exporting || !selectedApi}
            >
              Export API
            </button>
          </div>
          {exportContent && (
            <textarea
              className="w-full rounded-lg border border-slate-300 bg-slate-50 px-4 py-3 text-sm text-slate-800 font-mono shadow-inner focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all min-h-[200px] resize-y"
              value={exportContent}
              readOnly
            />
          )}
        </div>

        {/* Import Section */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-base font-semibold text-slate-900 mb-4">Import</h3>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">YAML Configuration</label>
              <textarea
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-3 text-sm text-slate-800 font-mono shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all min-h-[200px] resize-y"
                value={importContent}
                onChange={(e) => setImportContent(e.target.value)}
                placeholder="Paste YAML configuration here..."
              />
            </div>
            <button
              className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium bg-purple-600 text-white hover:bg-purple-700 shadow-sm transition-all duration-200 disabled:opacity-50"
              onClick={handleImport}
              disabled={importing}
            >
              {importing ? (
                <>
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2" />
                  Importing...
                </>
              ) : (
                'Import'
              )}
            </button>
          </div>
        </div>

        {/* Diff Section */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-base font-semibold text-slate-900 mb-4">Diff</h3>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-slate-700">YAML to Compare</label>
              <textarea
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-3 text-sm text-slate-800 font-mono shadow-sm placeholder:text-slate-400 focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 focus:outline-none transition-all min-h-[200px] resize-y"
                value={diffContent}
                onChange={(e) => setDiffContent(e.target.value)}
                placeholder="Paste YAML configuration to diff against current..."
              />
            </div>
            <button
              className="inline-flex items-center px-4 py-2.5 rounded-lg text-sm font-medium border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 shadow-sm transition-all duration-200 disabled:opacity-50"
              onClick={handleDiff}
              disabled={diffing}
            >
              {diffing ? (
                <>
                  <div className="w-4 h-4 border-2 border-slate-300 border-t-slate-600 rounded-full animate-spin mr-2" />
                  Computing diff...
                </>
              ) : (
                'Diff'
              )}
            </button>

            {diffResult && (
              <div className="mt-4 pt-4 border-t border-slate-100">
                <h3 className="text-sm font-semibold text-slate-900 mb-3">Differences</h3>
                {diffResult.additions.length === 0 &&
                  diffResult.deletions.length === 0 &&
                  diffResult.modifications.length === 0 ? (
                  <div className="rounded-lg bg-blue-50 border border-blue-200 px-4 py-3 text-sm text-blue-700">No differences found.</div>
                ) : (
                  <div className="space-y-2">
                    {diffResult.additions.map((a, i) => (
                      <div
                        key={`add-${i}`}
                        className="px-4 py-2.5 bg-green-50 text-green-800 rounded-lg font-mono text-xs border border-green-200"
                      >
                        + {a}
                      </div>
                    ))}
                    {diffResult.deletions.map((d, i) => (
                      <div
                        key={`del-${i}`}
                        className="px-4 py-2.5 bg-red-50 text-red-800 rounded-lg font-mono text-xs border border-red-200"
                      >
                        - {d}
                      </div>
                    ))}
                    {diffResult.modifications.map((m, i) => (
                      <div
                        key={`mod-${i}`}
                        className="px-4 py-2.5 bg-amber-50 text-amber-800 rounded-lg font-mono text-xs border border-amber-200"
                      >
                        ~ {m}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
