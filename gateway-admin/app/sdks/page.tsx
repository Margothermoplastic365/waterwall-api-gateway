'use client';

import { useEffect, useState, useCallback } from 'react';
import { get, post } from '@gateway/shared-ui';

interface ApiOption {
  id: string;
  name: string;
}

interface SdkLanguage {
  id: string;
  name: string;
  description: string;
}

const LANGUAGE_META: Record<string, { name: string; description: string }> = {
  curl: { name: 'cURL', description: 'Shell-based HTTP commands for quick API testing' },
  postman: { name: 'Postman', description: 'Importable Postman collection with pre-configured requests' },
  javascript: { name: 'JavaScript', description: 'Node.js / browser SDK with fetch-based HTTP client' },
  python: { name: 'Python', description: 'Python SDK using the requests library' },
};

interface GenerationResult {
  language: string;
  downloadUrl: string;
  generatedAt: string;
}

const QUICK_LANGUAGES = [
  { id: 'curl', name: 'cURL', icon: 'cURL', bg: 'bg-slate-800', color: 'text-white' },
  { id: 'postman', name: 'Postman', icon: 'POST', bg: 'bg-orange-500', color: 'text-white' },
  { id: 'javascript', name: 'JavaScript', icon: 'JS', bg: 'bg-yellow-400', color: 'text-slate-900' },
  { id: 'python', name: 'Python', icon: 'PY', bg: 'bg-blue-500', color: 'text-white' },
];

export default function SdksPage() {
  const [apis, setApis] = useState<ApiOption[]>([]);
  const [selectedApi, setSelectedApi] = useState('');
  const [languages, setLanguages] = useState<SdkLanguage[]>([]);
  const [results, setResults] = useState<Record<string, GenerationResult>>({});
  const [generating, setGenerating] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  useEffect(() => {
    get<ApiOption[] | { content: ApiOption[] }>('/v1/apis?size=200')
      .then((data) => setApis(Array.isArray(data) ? data : data.content ?? []))
      .catch(() => setApis([]));

    get<string[] | SdkLanguage[]>('/v1/sdks/languages')
      .then((data) => {
        if (!Array.isArray(data)) { setLanguages([]); return; }
        const mapped: SdkLanguage[] = data.map((item) => {
          if (typeof item === 'string') {
            const meta = LANGUAGE_META[item] || { name: item, description: '' };
            return { id: item, name: meta.name, description: meta.description };
          }
          return item as SdkLanguage;
        });
        setLanguages(mapped);
      })
      .catch(() => setLanguages([]));
  }, []);

  const handleGenerate = useCallback(
    async (language: string) => {
      if (!selectedApi) {
        showToast('Please select an API first', 'error');
        return;
      }
      setGenerating(language);
      try {
        const result = await post<GenerationResult>(
          `/v1/sdks/generate/${selectedApi}?language=${language}`,
          {},
        );
        setResults((prev) => ({ ...prev, [language]: result }));
      } catch {
        showToast(`Failed to generate ${language} SDK`, 'error');
      } finally {
        setGenerating(null);
      }
    },
    [selectedApi],
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
        <div className="mb-8">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">SDK Generation</h1>
          <p className="mt-1 text-sm text-slate-500">Generate client SDKs and code snippets</p>
        </div>

        {/* API Selector */}
        <div className="mb-8 rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="max-w-sm">
            <label className="mb-1.5 block text-sm font-medium text-slate-700">Select API</label>
            <select
              className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm focus:border-purple-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20"
              value={selectedApi}
              onChange={(e) => setSelectedApi(e.target.value)}
            >
              <option value="">-- Select API --</option>
              {apis.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Language Cards */}
        <h3 className="mb-4 text-base font-semibold text-slate-900">Quick Generate</h3>
        <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {QUICK_LANGUAGES.map((lang) => (
            <div key={lang.id} className="flex flex-col items-center rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 transition-shadow hover:shadow-md">
              <div
                className={`mb-4 flex h-14 w-14 items-center justify-center rounded-xl text-sm font-bold ${lang.bg} ${lang.color}`}
              >
                {lang.icon}
              </div>
              <p className="mb-4 text-sm font-semibold text-slate-900">{lang.name}</p>
              <button
                className="w-full rounded-lg bg-purple-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-purple-700 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={!selectedApi || generating === lang.id}
                onClick={() => handleGenerate(lang.id)}
              >
                {generating === lang.id ? (
                  <span className="inline-flex items-center">
                    <svg className="mr-2 h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Generating...
                  </span>
                ) : (
                  'Generate'
                )}
              </button>
              {results[lang.id] && (
                <a
                  href={results[lang.id].downloadUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-3 inline-flex items-center gap-1 text-sm font-medium text-purple-600 transition-colors hover:text-purple-700"
                >
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" />
                  </svg>
                  Download SDK
                </a>
              )}
            </div>
          ))}
        </div>

        {/* Supported Languages */}
        {languages.length > 0 && (
          <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
            <div className="border-b border-slate-200 px-6 py-4">
              <h3 className="text-base font-semibold text-slate-900">All Supported Languages</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50/50">
                    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500">Language</th>
                    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500">Description</th>
                    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {languages.map((lang) => (
                    <tr key={lang.id} className="transition-colors hover:bg-slate-50">
                      <td className="whitespace-nowrap px-6 py-3.5 text-sm font-medium text-slate-900">{lang.name}</td>
                      <td className="px-6 py-3.5 text-sm text-slate-500">{lang.description}</td>
                      <td className="whitespace-nowrap px-6 py-3.5">
                        <div className="flex items-center gap-3">
                          <button
                            className="inline-flex items-center rounded-lg bg-purple-600 px-3.5 py-1.5 text-xs font-medium text-white transition-colors hover:bg-purple-700 disabled:cursor-not-allowed disabled:opacity-50"
                            disabled={!selectedApi || generating === lang.id}
                            onClick={() => handleGenerate(lang.id)}
                          >
                            {generating === lang.id ? '...' : 'Generate'}
                          </button>
                          {results[lang.id] && (
                            <a
                              href={results[lang.id].downloadUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-sm font-medium text-purple-600 transition-colors hover:text-purple-700"
                            >
                              Download
                            </a>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {languages.length === 0 && (
          <div className="rounded-xl bg-white p-12 text-center shadow-sm ring-1 ring-slate-200">
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-slate-100">
              <svg className="h-6 w-6 text-slate-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5" />
              </svg>
            </div>
            <p className="text-sm text-slate-500">No additional languages available.</p>
          </div>
        )}
      </div>
    </div>
  );
}
