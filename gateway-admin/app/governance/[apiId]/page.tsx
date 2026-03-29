'use client';

import { useEffect, useState, useCallback } from 'react';
import { get, post } from '@gateway/shared-ui';

type Tab = 'lint' | 'score' | 'editor';

interface Violation {
  severity: string;
  rule: string;
  path: string;
  message: string;
}

interface LintReport {
  apiId: string;
  violations: Violation[];
  lintedAt?: string;
}

interface CategoryScore {
  category: string;
  score: number;
}

interface QualityScore {
  apiId: string;
  overallScore: number;
  categories: CategoryScore[];
  recommendations: string[];
}

interface ApiSpec {
  apiId: string;
  content: string;
  format: string;
}

function severityColor(sev: string): string {
  switch (sev.toUpperCase()) {
    case 'ERROR': return '#dc2626';
    case 'WARNING': return '#d97706';
    case 'INFO': return '#0891b2';
    default: return '#64748b';
  }
}

function severityBg(sev: string): string {
  switch (sev.toUpperCase()) {
    case 'ERROR': return '#fee2e2';
    case 'WARNING': return '#fef3c7';
    case 'INFO': return '#cffafe';
    default: return '#f1f5f9';
  }
}

function scoreColor(score: number): string {
  if (score >= 80) return '#16a34a';
  if (score >= 60) return '#d97706';
  return '#dc2626';
}

export default function GovernanceDetailPage({ params }: { params: { apiId: string } }) {
  const { apiId } = params;
  const [tab, setTab] = useState<Tab>('lint');

  /* Lint state */
  const [report, setReport] = useState<LintReport | null>(null);
  const [lintLoading, setLintLoading] = useState(false);
  const [relinting, setRelinting] = useState(false);

  /* Score state */
  const [quality, setQuality] = useState<QualityScore | null>(null);
  const [scoreLoading, setScoreLoading] = useState(false);

  /* Spec state */
  const [spec, setSpec] = useState('');
  const [specLoading, setSpecLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  /* Load lint report */
  useEffect(() => {
    if (tab !== 'lint') return;
    setLintLoading(true);
    get<LintReport>(`/v1/governance/lint/${apiId}/report`)
      .then(setReport)
      .catch(() => setReport(null))
      .finally(() => setLintLoading(false));
  }, [tab, apiId]);

  /* Load quality score */
  useEffect(() => {
    if (tab !== 'score') return;
    setScoreLoading(true);
    get<QualityScore>(`/v1/governance/score/${apiId}`)
      .then(setQuality)
      .catch(() => setQuality(null))
      .finally(() => setScoreLoading(false));
  }, [tab, apiId]);

  /* Load spec */
  useEffect(() => {
    if (tab !== 'editor') return;
    setSpecLoading(true);
    get<ApiSpec>(`/v1/governance/specs/${apiId}`)
      .then((s) => setSpec(s.content || ''))
      .catch(() => setSpec(''))
      .finally(() => setSpecLoading(false));
  }, [tab, apiId]);

  const handleRelint = useCallback(async () => {
    setRelinting(true);
    try {
      const r = await post<LintReport>(`/v1/governance/lint/${apiId}`, {});
      setReport(r);
    } catch {
      showToast('Failed to re-lint', 'error');
    } finally {
      setRelinting(false);
    }
  }, [apiId]);

  const handleUploadSpec = useCallback(async () => {
    setUploading(true);
    try {
      await post(`/v1/governance/specs/${apiId}`, { content: spec });
      showToast('Spec uploaded successfully');
    } catch {
      showToast('Failed to upload spec', 'error');
    } finally {
      setUploading(false);
    }
  }, [apiId, spec]);

  const handleLintAfterUpload = useCallback(async () => {
    try {
      await post(`/v1/governance/lint/${apiId}`, {});
      setTab('lint');
    } catch {
      showToast('Failed to lint', 'error');
    }
  }, [apiId]);

  const TabBtn = ({ t, label }: { t: Tab; label: string }) => (
    <button
      onClick={() => setTab(t)}
      className={`tab ${tab === t ? 'tab-active' : ''}`}
    >
      {label}
    </button>
  );

  return (
    <div className="container p-xl">
      {toast && (<div className={`fixed top-4 right-4 z-50 flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg border max-w-sm ${toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-emerald-50 border-emerald-200 text-emerald-800'}`}>{toast.type === 'error' ? (<svg className="w-5 h-5 shrink-0 text-red-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>) : (<svg className="w-5 h-5 shrink-0 text-emerald-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>)}<p className="text-sm font-medium flex-1">{toast.message}</p><button onClick={() => setToast(null)} className="shrink-0 opacity-50 hover:opacity-100"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg></button></div>)}
      <button
        onClick={() => { window.location.href = '/governance'; }}
        style={{ fontSize: '0.875rem', color: 'var(--color-primary)', background: 'none', border: 'none', cursor: 'pointer', marginBottom: 16, display: 'inline-block' }}
      >
        &larr; Back to Governance
      </button>

      <div className="flex-between mb-lg">
        <div>
          <h1>Governance Detail</h1>
          <p className="text-muted mt-sm">API ID: {apiId}</p>
        </div>
      </div>

      <div className="tabs">
        <TabBtn t="lint" label="Lint Report" />
        <TabBtn t="score" label="Quality Score" />
        <TabBtn t="editor" label="Spec Editor" />
      </div>

      {/* Lint Report Tab */}
      {tab === 'lint' && (
        <div>
          <div className="flex-between mb-md">
            <h3>Lint Violations</h3>
            <button className="btn btn-primary btn-sm" onClick={handleRelint} disabled={relinting}>
              {relinting ? 'Linting...' : 'Re-lint'}
            </button>
          </div>

          {lintLoading ? (
            <div className="flex-center" style={{ padding: 40 }}>
              <div className="spinner spinner-dark" style={{ width: 32, height: 32 }} />
            </div>
          ) : !report || report.violations.length === 0 ? (
            <div className="card">
              <div className="text-muted text-sm" style={{ padding: '40px 0', textAlign: 'center' }}>
                No violations found. API spec is clean.
              </div>
            </div>
          ) : (
            <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
              <table>
                <thead>
                  <tr>
                    <th>Severity</th>
                    <th>Rule</th>
                    <th>Path</th>
                    <th>Message</th>
                  </tr>
                </thead>
                <tbody>
                  {(report.violations || []).map((v, i) => (
                    <tr key={i}>
                      <td>
                        <span
                          className="badge"
                          style={{ background: severityBg(v.severity), color: severityColor(v.severity) }}
                        >
                          {v.severity}
                        </span>
                      </td>
                      <td style={{ fontSize: '0.875rem', fontFamily: 'monospace' }}>{v.rule}</td>
                      <td style={{ fontSize: '0.875rem', fontFamily: 'monospace' }}>{v.path}</td>
                      <td style={{ fontSize: '0.875rem' }}>{v.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Quality Score Tab */}
      {tab === 'score' && (
        <div>
          {scoreLoading ? (
            <div className="flex-center" style={{ padding: 40 }}>
              <div className="spinner spinner-dark" style={{ width: 32, height: 32 }} />
            </div>
          ) : !quality ? (
            <div className="card">
              <div className="text-muted text-sm" style={{ padding: '40px 0', textAlign: 'center' }}>
                No quality score available.
              </div>
            </div>
          ) : (
            <div>
              {/* Overall Score */}
              <div className="card mb-lg" style={{ textAlign: 'center' }}>
                <div style={{ fontSize: '0.875rem', color: 'var(--color-text-muted)', marginBottom: 8 }}>
                  Overall Quality Score
                </div>
                <div
                  style={{
                    width: 120,
                    height: 120,
                    borderRadius: '50%',
                    border: `8px solid ${scoreColor(quality.overallScore)}`,
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '2rem',
                    fontWeight: 700,
                    color: scoreColor(quality.overallScore),
                  }}
                >
                  {quality.overallScore}
                </div>
              </div>

              {/* Category Breakdown */}
              <div className="card mb-lg">
                <h3 className="mb-md">Category Breakdown</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  {(quality.categories || []).map((cat) => (
                    <div key={cat.category} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <span style={{ width: 160, fontSize: '0.875rem', color: 'var(--color-text-muted)' }}>
                        {cat.category}
                      </span>
                      <div
                        style={{
                          flex: 1,
                          height: 8,
                          borderRadius: 4,
                          background: '#e2e8f0',
                          overflow: 'hidden',
                        }}
                      >
                        <div
                          style={{
                            width: `${cat.score}%`,
                            height: '100%',
                            borderRadius: 4,
                            background: scoreColor(cat.score),
                          }}
                        />
                      </div>
                      <span
                        style={{
                          width: 40,
                          textAlign: 'right',
                          fontSize: '0.875rem',
                          fontWeight: 600,
                          color: scoreColor(cat.score),
                        }}
                      >
                        {cat.score}
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Recommendations */}
              {quality.recommendations.length > 0 && (
                <div className="card">
                  <h3 className="mb-md">Recommendations</h3>
                  <ul style={{ paddingLeft: 20 }}>
                    {(quality.recommendations || []).map((r, i) => (
                      <li key={i} style={{ fontSize: '0.875rem', marginBottom: 8, color: 'var(--color-text-muted)' }}>
                        {r}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Spec Editor Tab */}
      {tab === 'editor' && (
        <div>
          {specLoading ? (
            <div className="flex-center" style={{ padding: 40 }}>
              <div className="spinner spinner-dark" style={{ width: 32, height: 32 }} />
            </div>
          ) : (
            <div className="card">
              <h3 className="mb-md">OpenAPI Specification</h3>
              <textarea
                className="form-input"
                style={{
                  fontFamily: "'SF Mono', 'Fira Code', 'Fira Mono', Menlo, Consolas, monospace",
                  fontSize: '0.8125rem',
                  minHeight: 400,
                  resize: 'vertical',
                }}
                value={spec}
                onChange={(e) => setSpec(e.target.value)}
              />
              <div className="flex gap-sm mt-md">
                <button className="btn btn-primary" onClick={handleUploadSpec} disabled={uploading}>
                  {uploading ? 'Uploading...' : 'Upload Spec'}
                </button>
                <button className="btn btn-secondary" onClick={handleLintAfterUpload}>
                  Lint
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
