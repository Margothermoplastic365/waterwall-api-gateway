'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { apiClient } from '@gateway/shared-ui/lib/api-client';

interface ApiSummary {
  id: string;
  name: string;
}

interface ChangelogEntry {
  id: string;
  versionFrom: string;
  versionTo: string;
  date: string;
  changes: string[];
  breakingChanges: string[];
  migrationGuide?: string;
}

export default function ChangelogsPage() {
  const [apis, setApis] = useState<ApiSummary[]>([]);
  const [selectedApiId, setSelectedApiId] = useState('');
  const [changelogs, setChangelogs] = useState<ChangelogEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [apisLoading, setApisLoading] = useState(true);
  const [error, setError] = useState('');
  const [expandedGuides, setExpandedGuides] = useState<Set<string>>(new Set());

  useEffect(() => {
    async function fetchApis() {
      try {
        const data = await apiClient<ApiSummary[] | { content: ApiSummary[] }>('/v1/apis?size=200');
        setApis(Array.isArray(data) ? data : (data as { content: ApiSummary[] }).content ?? []);
      } catch {
        setApis([]);
      } finally {
        setApisLoading(false);
      }
    }
    fetchApis();
  }, []);

  const fetchChangelogs = useCallback(async (apiId: string) => {
    if (!apiId) return;
    setLoading(true);
    setError('');
    try {
      const data = await apiClient<ChangelogEntry[]>(`/v1/changelogs/${apiId}`);
      setChangelogs(Array.isArray(data) ? data : []);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load changelogs');
      setChangelogs([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const handleApiChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const apiId = e.target.value;
    setSelectedApiId(apiId);
    if (apiId) fetchChangelogs(apiId);
    else setChangelogs([]);
  };

  const toggleGuide = (id: string) => {
    setExpandedGuides((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const sectionStyle: React.CSSProperties = {
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e2e8f0',
    padding: 24,
    marginBottom: 20,
  };

  return (
    <div style={{ maxWidth: 800 }}>
      <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', marginBottom: 24 }}>API Changelogs</h1>

      {/* API Selector */}
      <div style={{ marginBottom: 24 }}>
        <label style={{ display: 'block', fontSize: 14, fontWeight: 500, marginBottom: 6, color: '#334155' }}>
          Select API
        </label>
        <select
          value={selectedApiId}
          onChange={handleApiChange}
          disabled={apisLoading}
          style={{
            width: '100%',
            maxWidth: 400,
            padding: '10px 14px',
            border: '1px solid #cbd5e1',
            borderRadius: 6,
            fontSize: 14,
            color: '#0f172a',
            backgroundColor: '#fff',
          }}
        >
          <option value="">{apisLoading ? 'Loading APIs...' : '-- Select an API --'}</option>
          {apis.map((api) => (
            <option key={api.id} value={api.id}>
              {api.name}
            </option>
          ))}
        </select>
      </div>

      {error && <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{error}</p>}

      {loading && <p style={{ color: '#64748b', fontSize: 14 }}>Loading changelogs...</p>}

      {!loading && selectedApiId && changelogs.length === 0 && !error && (
        <p style={{ color: '#64748b', fontSize: 14 }}>No changelogs found for this API.</p>
      )}

      {/* Timeline */}
      {changelogs.length > 0 && (
        <div style={{ position: 'relative', paddingLeft: 28 }}>
          {/* Timeline line */}
          <div
            style={{
              position: 'absolute',
              left: 8,
              top: 6,
              bottom: 6,
              width: 2,
              backgroundColor: '#e2e8f0',
            }}
          />

          {changelogs.map((entry) => (
            <div key={entry.id} style={{ position: 'relative', marginBottom: 24 }}>
              {/* Timeline dot */}
              <div
                style={{
                  position: 'absolute',
                  left: -24,
                  top: 6,
                  width: 12,
                  height: 12,
                  borderRadius: '50%',
                  backgroundColor: entry.breakingChanges && entry.breakingChanges.length > 0 ? '#ef4444' : '#3b82f6',
                  border: '2px solid #fff',
                  boxShadow: '0 0 0 2px #e2e8f0',
                }}
              />

              <div style={sectionStyle}>
                {/* Header */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 15, fontWeight: 600, color: '#0f172a' }}>
                    {entry.versionFrom} &rarr; {entry.versionTo}
                  </span>
                  <span style={{ fontSize: 13, color: '#94a3b8' }}>
                    {new Date(entry.date).toLocaleDateString()}
                  </span>
                  {entry.breakingChanges && entry.breakingChanges.length > 0 && (
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 4,
                        padding: '2px 10px',
                        borderRadius: 9999,
                        fontSize: 11,
                        fontWeight: 700,
                        backgroundColor: '#fee2e2',
                        color: '#991b1b',
                        textTransform: 'uppercase',
                        letterSpacing: '0.025em',
                      }}
                    >
                      Breaking Changes
                    </span>
                  )}
                </div>

                {/* Changes */}
                {entry.changes && entry.changes.length > 0 && (
                  <div style={{ marginBottom: 12 }}>
                    <h4 style={{ fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6, marginTop: 0 }}>
                      Changes
                    </h4>
                    <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, color: '#475569', lineHeight: 1.8 }}>
                      {entry.changes.map((change, i) => (
                        <li key={i}>{change}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* Breaking Changes */}
                {entry.breakingChanges && entry.breakingChanges.length > 0 && (
                  <div
                    style={{
                      marginBottom: 12,
                      padding: 12,
                      backgroundColor: '#fef2f2',
                      borderRadius: 6,
                      border: '1px solid #fecaca',
                    }}
                  >
                    <h4 style={{ fontSize: 13, fontWeight: 600, color: '#991b1b', marginBottom: 6, marginTop: 0 }}>
                      Breaking Changes
                    </h4>
                    <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, color: '#991b1b', lineHeight: 1.8 }}>
                      {entry.breakingChanges.map((bc, i) => (
                        <li key={i}>{bc}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* Migration Guide */}
                {entry.migrationGuide && (
                  <div>
                    <button
                      onClick={() => toggleGuide(entry.id)}
                      style={{
                        padding: '4px 0',
                        fontSize: 13,
                        fontWeight: 500,
                        color: '#3b82f6',
                        background: 'none',
                        border: 'none',
                        cursor: 'pointer',
                        textDecoration: 'underline',
                      }}
                    >
                      {expandedGuides.has(entry.id) ? 'Hide Migration Guide' : 'Migration Guide'}
                    </button>
                    {expandedGuides.has(entry.id) && (
                      <div
                        style={{
                          marginTop: 8,
                          padding: 14,
                          backgroundColor: '#f8fafc',
                          borderRadius: 6,
                          border: '1px solid #e2e8f0',
                          fontSize: 13,
                          color: '#334155',
                          lineHeight: 1.7,
                          whiteSpace: 'pre-wrap',
                        }}
                      >
                        {entry.migrationGuide}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
