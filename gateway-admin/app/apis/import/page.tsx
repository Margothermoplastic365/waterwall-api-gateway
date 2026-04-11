'use client';

import React, { useState, useRef } from 'react';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

function getToken(): string {
  return (typeof window !== 'undefined'
    ? localStorage.getItem('admin_token') || localStorage.getItem('token') || '' : '');
}
function authHeaders(): Record<string, string> {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
}

interface ImportedRoute { path: string; method: string; description: string; authTypes?: string[]; }
interface ImportedChannel { name: string; protocol: string; description: string; }
interface PreviewResult {
  name: string; version: string; description: string; protocolType: string;
  detectedFormat: string; routes: ImportedRoute[]; channels: ImportedChannel[];
  authSchemes: string[]; servers: string[]; tags: string[]; warnings: string[];
}

const FORMATS = [
  { value: 'AUTO', label: 'Auto-detect' },
  { value: 'OPENAPI_3', label: 'OpenAPI 3.x', group: 'REST' },
  { value: 'SWAGGER_2', label: 'Swagger 2.0', group: 'REST' },
  { value: 'POSTMAN', label: 'Postman Collection', group: 'REST' },
  { value: 'ASYNCAPI', label: 'AsyncAPI (Kafka, MQTT, WS, SSE)', group: 'Async' },
  { value: 'GRAPHQL_SDL', label: 'GraphQL SDL', group: 'Query' },
  { value: 'PROTOBUF', label: 'gRPC / Protobuf', group: 'RPC' },
  { value: 'WSDL', label: 'WSDL (SOAP)', group: 'RPC' },
  { value: 'OPENRPC', label: 'OpenRPC (JSON-RPC)', group: 'RPC' },
  { value: 'ODATA_EDMX', label: 'OData $metadata', group: 'Other' },
  { value: 'HAR', label: 'HAR (HTTP Archive)', group: 'Other' },
];

const SENSITIVITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

const METHOD_COLORS: Record<string, string> = {
  GET: 'bg-emerald-100 text-emerald-700', POST: 'bg-blue-100 text-blue-700',
  PUT: 'bg-amber-100 text-amber-700', DELETE: 'bg-red-100 text-red-700',
  PATCH: 'bg-orange-100 text-orange-700', WS: 'bg-cyan-100 text-cyan-700',
};

type SourceTab = 'file' | 'url' | 'paste';

export default function ImportApiPage() {
  const [sourceTab, setSourceTab] = useState<SourceTab>('file');
  const [format, setFormat] = useState('AUTO');
  const [sensitivity, setSensitivity] = useState('LOW');
  const [category, setCategory] = useState('');
  const [contextPath, setContextPath] = useState('');

  // File upload
  const fileRef = useRef<HTMLInputElement>(null);
  const [fileName, setFileName] = useState('');
  const [fileContent, setFileContent] = useState('');
  const [dragOver, setDragOver] = useState(false);

  // URL
  const [url, setUrl] = useState('');

  // Paste
  const [pasteContent, setPasteContent] = useState('');

  // Preview
  const [preview, setPreview] = useState<PreviewResult | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [previewError, setPreviewError] = useState('');

  // Import
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState('');

  const handleFileSelect = (file: File) => {
    setFileName(file.name);
    const reader = new FileReader();
    reader.onload = (e) => {
      const text = e.target?.result as string;
      setFileContent(text);
    };
    reader.readAsText(file);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer.files.length > 0) handleFileSelect(e.dataTransfer.files[0]);
  };

  const getContent = (): string => {
    switch (sourceTab) {
      case 'file': return fileContent;
      case 'url': return ''; // URL resolved server-side
      case 'paste': return pasteContent;
    }
  };

  const handlePreview = async () => {
    setPreviewing(true);
    setPreviewError('');
    setPreview(null);
    try {
      const body: Record<string, string> = { format };
      if (sourceTab === 'url') body.url = url;
      else body.content = getContent();

      const res = await fetch(`${API_URL}/v1/import/preview`, {
        method: 'POST', headers: authHeaders(), body: JSON.stringify(body),
      });
      if (!res.ok) { const b = await res.json().catch(() => null); throw new Error(b?.message || `Preview failed (${res.status})`); }
      const data = await res.json();
      setPreview(data);
    } catch (err) {
      setPreviewError(err instanceof Error ? err.message : 'Preview failed');
    } finally { setPreviewing(false); }
  };

  const handleImport = async () => {
    setImporting(true);
    setImportError('');
    try {
      const body: Record<string, string> = { format, sensitivity, category };
      if (contextPath.trim()) body.contextPath = contextPath.trim().toLowerCase().replace(/[^a-z0-9-]/g, '');
      if (sourceTab === 'url') body.url = url;
      else body.content = getContent();

      const res = await fetch(`${API_URL}/v1/import`, {
        method: 'POST', headers: authHeaders(), body: JSON.stringify(body),
      });
      if (!res.ok) { const b = await res.json().catch(() => null); throw new Error(b?.message || `Import failed (${res.status})`); }
      const data = await res.json();
      window.location.href = `/apis/${data.id}`;
    } catch (err) {
      setImportError(err instanceof Error ? err.message : 'Import failed');
    } finally { setImporting(false); }
  };

  const hasContent = sourceTab === 'file' ? !!fileContent : sourceTab === 'url' ? !!url.trim() : !!pasteContent.trim();

  return (
    <main className="p-6 max-w-4xl mx-auto">
      <a href="/apis" className="text-sm text-blue-600 hover:text-blue-700 font-medium no-underline">&larr; Back to APIs</a>

      <h1 className="text-2xl font-bold text-gray-900 mt-4 mb-1">Import API</h1>
      <p className="text-sm text-gray-500 mb-6">Import an API from a spec file, URL, or paste content directly.</p>

      <div className="grid grid-cols-[1fr_340px] gap-6">
        {/* Left: Source + Format */}
        <div className="space-y-5">
          {/* Format selector */}
          <div className="bg-white border border-gray-200 rounded-lg p-5">
            <label className="block text-sm font-semibold text-gray-700 mb-2">Format</label>
            <select value={format} onChange={(e) => setFormat(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
              {FORMATS.map((f) => <option key={f.value} value={f.value}>{f.group ? `[${f.group}] ${f.label}` : f.label}</option>)}
            </select>
          </div>

          {/* Source tabs */}
          <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
            <div className="flex border-b border-gray-200">
              {(['file', 'url', 'paste'] as const).map((tab) => (
                <button key={tab} onClick={() => setSourceTab(tab)}
                  className={`flex-1 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors ${
                    sourceTab === tab ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-400 hover:text-gray-600'
                  }`}>
                  {tab === 'file' ? 'Upload File' : tab === 'url' ? 'From URL' : 'Paste Content'}
                </button>
              ))}
            </div>

            <div className="p-5">
              {sourceTab === 'file' && (
                <div
                  onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
                  onDragLeave={() => setDragOver(false)}
                  onDrop={handleDrop}
                  onClick={() => fileRef.current?.click()}
                  className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
                    dragOver ? 'border-blue-400 bg-blue-50' : fileName ? 'border-emerald-300 bg-emerald-50' : 'border-gray-300 hover:border-blue-300 hover:bg-blue-50/50'
                  }`}>
                  <input ref={fileRef} type="file" className="hidden"
                    accept=".yaml,.yml,.json,.graphql,.gql,.proto,.wsdl,.xml,.har"
                    onChange={(e) => { if (e.target.files?.[0]) handleFileSelect(e.target.files[0]); }} />
                  {fileName ? (
                    <div>
                      <div className="text-2xl mb-2">{'\u{2705}'}</div>
                      <p className="text-sm font-medium text-emerald-700">{fileName}</p>
                      <p className="text-xs text-gray-400 mt-1">Click or drop to replace</p>
                    </div>
                  ) : (
                    <div>
                      <div className="text-2xl mb-2">{'\u{1F4C1}'}</div>
                      <p className="text-sm font-medium text-gray-600">Drop file here or click to browse</p>
                      <p className="text-xs text-gray-400 mt-1">.yaml, .json, .graphql, .proto, .wsdl, .har</p>
                    </div>
                  )}
                </div>
              )}

              {sourceTab === 'url' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Spec URL</label>
                  <input type="url" value={url} onChange={(e) => setUrl(e.target.value)}
                    placeholder="https://petstore.swagger.io/v3/api-docs"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  <p className="text-xs text-gray-400 mt-1">The URL will be fetched server-side</p>
                </div>
              )}

              {sourceTab === 'paste' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Spec Content</label>
                  <textarea value={pasteContent} onChange={(e) => setPasteContent(e.target.value)}
                    rows={12} placeholder='Paste your OpenAPI, AsyncAPI, GraphQL SDL, Protobuf, WSDL, or Postman JSON here...'
                    className="w-full px-3 py-2 border border-gray-300 rounded-md text-xs font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 resize-vertical" />
                </div>
              )}
            </div>
          </div>

          {/* Preview button */}
          <button onClick={handlePreview} disabled={previewing || !hasContent}
            className="w-full py-2.5 bg-gray-800 hover:bg-gray-900 disabled:bg-gray-200 disabled:text-gray-400 text-white text-sm font-medium rounded-lg transition-colors">
            {previewing ? 'Analyzing...' : 'Preview Import'}
          </button>

          {previewError && <div className="p-3 bg-red-50 text-red-700 rounded-lg text-sm">{previewError}</div>}
        </div>

        {/* Right: Settings + Preview */}
        <div className="space-y-5">
          {/* Settings */}
          <div className="bg-white border border-gray-200 rounded-lg p-5 space-y-4">
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">Sensitivity</label>
              <div className="flex gap-1.5">
                {SENSITIVITIES.map((s) => (
                  <button key={s} onClick={() => setSensitivity(s)}
                    className={`flex-1 py-1.5 text-xs font-semibold rounded transition-colors ${
                      sensitivity === s
                        ? s === 'CRITICAL' || s === 'HIGH' ? 'bg-red-100 text-red-700' : s === 'MEDIUM' ? 'bg-amber-100 text-amber-700' : 'bg-gray-200 text-gray-700'
                        : 'bg-gray-50 text-gray-400 hover:bg-gray-100'
                    }`}>
                    {s}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">Category</label>
              <input type="text" value={category} onChange={(e) => setCategory(e.target.value)}
                placeholder="e.g. Finance, Logistics"
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">Context Path</label>
              <div className="flex items-center gap-1">
                <span className="text-gray-400 text-sm font-mono">/</span>
                <input
                  type="text"
                  value={contextPath}
                  onChange={(e) => setContextPath(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))}
                  placeholder="auto-generated-from-name"
                  className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
                />
              </div>
              <p className="text-xs text-gray-400 mt-1">URL prefix on the gateway. Leave blank to auto-generate.</p>
            </div>
          </div>

          {/* Preview Result */}
          {preview && (
            <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
              <div className="px-4 py-3 bg-gray-50 border-b border-gray-200">
                <h3 className="text-sm font-semibold text-gray-700">Preview</h3>
              </div>
              <div className="p-4 space-y-3 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-500">Format</span>
                  <span className="font-mono text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded">{preview.detectedFormat}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Protocol</span>
                  <span className="font-semibold text-gray-800">{preview.protocolType}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Name</span>
                  <span className="font-semibold text-gray-800 truncate ml-2">{preview.name}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Version</span>
                  <span className="text-gray-800">{preview.version}</span>
                </div>
                {preview.authSchemes.length > 0 && (
                  <div className="flex justify-between items-start">
                    <span className="text-gray-500">Auth</span>
                    <div className="flex flex-wrap gap-1 justify-end">
                      {preview.authSchemes.map((a) => (
                        <span key={a} className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-purple-50 text-purple-700">{a}</span>
                      ))}
                    </div>
                  </div>
                )}
                {preview.servers.length > 0 && (
                  <div>
                    <span className="text-gray-500 block mb-1">Servers</span>
                    {preview.servers.map((s) => (
                      <div key={s} className="text-[11px] font-mono text-gray-600 truncate">{s}</div>
                    ))}
                  </div>
                )}

                {/* Routes */}
                {preview.routes.length > 0 && (
                  <div>
                    <span className="text-gray-500 block mb-1">Endpoints ({preview.routes.length})</span>
                    <div className="max-h-48 overflow-y-auto space-y-1">
                      {preview.routes.map((r, i) => (
                        <div key={i} className="flex items-center gap-2 text-xs">
                          <span className={`px-1.5 py-0.5 rounded font-bold text-[10px] ${METHOD_COLORS[r.method] || 'bg-gray-100 text-gray-600'}`}>{r.method}</span>
                          <span className="font-mono text-gray-700 truncate">{r.path}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Channels (async) */}
                {preview.channels.length > 0 && (
                  <div>
                    <span className="text-gray-500 block mb-1">Channels ({preview.channels.length})</span>
                    <div className="max-h-32 overflow-y-auto space-y-1">
                      {preview.channels.map((c, i) => (
                        <div key={i} className="text-xs">
                          <span className="font-mono text-gray-700">{c.name}</span>
                          <span className="text-gray-400 ml-1">({c.protocol})</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Warnings */}
                {preview.warnings.length > 0 && (
                  <div className="pt-2 border-t border-gray-100">
                    {preview.warnings.map((w, i) => (
                      <div key={i} className="flex items-start gap-1.5 text-xs text-amber-700 mb-1">
                        <span className="shrink-0">{'\u26A0\uFE0F'}</span>
                        <span>{w}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Import button */}
              <div className="px-4 py-3 bg-gray-50 border-t border-gray-200">
                {importError && <div className="text-xs text-red-600 mb-2">{importError}</div>}
                <button onClick={handleImport} disabled={importing}
                  className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white text-sm font-semibold rounded-lg transition-colors">
                  {importing ? 'Importing...' : 'Import as DRAFT'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
