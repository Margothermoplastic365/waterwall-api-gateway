'use client';

import React, { useEffect, useState, useCallback } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token') || localStorage.getItem('admin_token') || localStorage.getItem('jwt_token');
}
function authHeaders(): Record<string, string> {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
}

interface Application {
  id: string;
  name: string;
  description?: string;
  status: string;
  callbackUrls?: string[];
  createdAt: string;
  updatedAt: string;
}

const STATUS_STYLES: Record<string, { bg: string; color: string }> = {
  ACTIVE: { bg: '#dcfce7', color: '#16a34a' },
  INACTIVE: { bg: '#f1f5f9', color: '#64748b' },
  REVOKED: { bg: '#fef2f2', color: '#dc2626' },
};

const MGMT_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface Subscription {
  id: string;
  applicationId: string;
  status: string;
}

export default function AppsPage() {
  const [apps, setApps] = useState<Application[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<string | null>(null);
  const [appSubs, setAppSubs] = useState<Record<string, Subscription[]>>({});

  const fetchApps = useCallback(async () => {
    setLoading(true);
    try {
      const [appsRes, subsRes] = await Promise.all([
        fetch(`${API_BASE}/v1/applications?page=0&size=50`, { headers: authHeaders() }),
        fetch(`${MGMT_URL}/v1/subscriptions?page=0&size=200`, { headers: authHeaders() }),
      ]);
      if (!appsRes.ok) throw new Error('Failed to load applications');
      const appsData = await appsRes.json();
      setApps(Array.isArray(appsData) ? appsData : appsData.content || []);

      if (subsRes.ok) {
        const subsData = await subsRes.json();
        const allSubs: Subscription[] = subsData.content || [];
        const grouped: Record<string, Subscription[]> = {};
        allSubs.forEach((s) => {
          if (s.status === 'ACTIVE' || s.status === 'PENDING' || s.status === 'APPROVED') {
            if (!grouped[s.applicationId]) grouped[s.applicationId] = [];
            grouped[s.applicationId].push(s);
          }
        });
        setAppSubs(grouped);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchApps(); }, [fetchApps]);

  useEffect(() => {
    if (typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('action') === 'create') {
      setShowCreate(true);
    }
  }, []);

  const handleDelete = async (appId: string, appName: string) => {
    if (!confirm(`Delete "${appName}"? This cannot be undone.`)) return;
    setDeleting(appId);
    setError('');
    try {
      const res = await fetch(`${API_BASE}/v1/applications/${appId}`, {
        method: 'DELETE',
        headers: authHeaders(),
      });
      if (!res.ok && res.status !== 204) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message || `Delete failed (${res.status})`);
      }
      setApps((prev) => prev.filter((a) => a.id !== appId));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    } finally {
      setDeleting(null);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim()) return;
    setCreating(true);
    try {
      const res = await fetch(`${API_BASE}/v1/applications`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ name: formName, description: formDesc }),
      });
      if (!res.ok) throw new Error('Failed to create application');
      setShowCreate(false);
      setFormName('');
      setFormDesc('');
      fetchApps();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setCreating(false);
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 28 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>My Applications</h1>
          <p style={{ fontSize: 14, color: '#64748b', margin: 0 }}>
            Create applications to get API keys and manage subscriptions
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          style={{
            display: 'flex', alignItems: 'center', gap: 6,
            padding: '9px 18px', backgroundColor: '#3b82f6', color: '#fff',
            border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer',
          }}
        >
          <span style={{ fontSize: 18, lineHeight: 1 }}>+</span> New App
        </button>
      </div>

      {error && (
        <div style={{ padding: '10px 16px', backgroundColor: '#fef2f2', border: '1px solid #fecaca', borderRadius: 8, color: '#dc2626', fontSize: 14, marginBottom: 20 }}>
          {error}
        </div>
      )}

      {/* Loading */}
      {loading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
          {[1, 2, 3].map((i) => (
            <div key={i} style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: 24, height: 140 }}>
              <div style={{ height: 18, width: '50%', backgroundColor: '#f1f5f9', borderRadius: 4, marginBottom: 12 }} />
              <div style={{ height: 14, width: '80%', backgroundColor: '#f8fafc', borderRadius: 4, marginBottom: 8 }} />
              <div style={{ height: 14, width: '40%', backgroundColor: '#f8fafc', borderRadius: 4 }} />
            </div>
          ))}
        </div>
      ) : apps.length === 0 ? (
        /* Empty State */
        <div style={{ backgroundColor: '#fff', borderRadius: 12, border: '1px solid #e2e8f0', padding: 64, textAlign: 'center' }}>
          <div style={{ fontSize: 48, marginBottom: 16 }}>{'\u{1F4F1}'}</div>
          <h2 style={{ fontSize: 20, fontWeight: 600, color: '#0f172a', marginBottom: 8 }}>No applications yet</h2>
          <p style={{ fontSize: 14, color: '#64748b', marginBottom: 24, maxWidth: 400, margin: '0 auto 24px' }}>
            Create your first application to generate API keys and start integrating with our APIs.
          </p>
          <button
            onClick={() => setShowCreate(true)}
            style={{
              padding: '10px 24px', backgroundColor: '#3b82f6', color: '#fff',
              border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer',
            }}
          >
            Create Your First App
          </button>
        </div>
      ) : (
        /* App Cards */
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
          {apps.map((app) => {
            const st = STATUS_STYLES[app.status] || STATUS_STYLES.INACTIVE;
            const activeSubs = appSubs[app.id] || [];
            const canDelete = activeSubs.length === 0;
            const isDeleting = deleting === app.id;
            return (
              <div
                key={app.id}
                style={{
                  backgroundColor: isDeleting ? '#fafafa' : '#fff', borderRadius: 10, border: '1px solid #e2e8f0',
                  padding: 20, transition: 'border-color 0.15s, box-shadow 0.15s, opacity 0.2s',
                  opacity: isDeleting ? 0.6 : 1,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
                  <a href={`/apps/${app.id}`} style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none', color: 'inherit' }}>
                    <div style={{
                      width: 38, height: 38, borderRadius: 8, backgroundColor: '#eff6ff',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: 16, fontWeight: 700, color: '#3b82f6',
                    }}>
                      {app.name.charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 15, color: '#0f172a' }}>{app.name}</div>
                      <div style={{ fontSize: 12, color: '#94a3b8' }}>
                        Created {new Date(app.createdAt).toLocaleDateString()}
                      </div>
                    </div>
                  </a>
                  <span style={{
                    fontSize: 11, fontWeight: 600, padding: '3px 10px', borderRadius: 12,
                    backgroundColor: st.bg, color: st.color,
                  }}>
                    {app.status}
                  </span>
                </div>

                {app.description && (
                  <p style={{ fontSize: 13, color: '#64748b', margin: '0 0 12px', lineHeight: 1.5 }}>
                    {app.description}
                  </p>
                )}

                {/* Subscription count */}
                <div style={{ fontSize: 12, color: '#94a3b8', marginBottom: 14 }}>
                  {activeSubs.length > 0 ? (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                      <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#16a34a', display: 'inline-block' }} />
                      {activeSubs.length} active subscription{activeSubs.length !== 1 ? 's' : ''}
                    </span>
                  ) : (
                    <span>No active subscriptions</span>
                  )}
                </div>

                {/* Actions */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 12, borderTop: '1px solid #f1f5f9' }}>
                  <a href={`/apps/${app.id}`} style={{ fontSize: 13, color: '#3b82f6', textDecoration: 'none', fontWeight: 500 }}>
                    Manage &rarr;
                  </a>
                  {canDelete ? (
                    <button
                      onClick={() => handleDelete(app.id, app.name)}
                      disabled={isDeleting}
                      style={{
                        padding: '5px 12px', fontSize: 12, fontWeight: 500, cursor: isDeleting ? 'not-allowed' : 'pointer',
                        backgroundColor: '#fff', color: '#dc2626', border: '1px solid #fecaca',
                        borderRadius: 6, transition: 'all 0.15s',
                      }}
                      onMouseEnter={(e) => { if (!isDeleting) { e.currentTarget.style.backgroundColor = '#fef2f2'; } }}
                      onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#fff'; }}
                    >
                      {isDeleting ? 'Deleting...' : 'Delete'}
                    </button>
                  ) : (
                    <span style={{ fontSize: 11, color: '#94a3b8', fontStyle: 'italic' }}
                      title="Remove all subscriptions before deleting">
                      Has active subscriptions
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Create Modal */}
      {showCreate && (
        <div
          style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}
          onClick={() => setShowCreate(false)}
        >
          <div
            style={{ backgroundColor: '#fff', borderRadius: 12, width: '100%', maxWidth: 460, boxShadow: '0 20px 60px rgba(0,0,0,0.15)' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ padding: '20px 24px', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600, color: '#0f172a' }}>Create Application</h2>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', fontSize: 20, color: '#94a3b8', cursor: 'pointer' }}>&times;</button>
            </div>
            <form onSubmit={handleCreate} style={{ padding: 24 }}>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: 14, fontWeight: 500, color: '#334155', marginBottom: 6 }}>App Name *</label>
                <input
                  type="text" value={formName} onChange={(e) => setFormName(e.target.value)} required autoFocus
                  placeholder="e.g. My Mobile App"
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e2e8f0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', outline: 'none' }}
                />
              </div>
              <div style={{ marginBottom: 24 }}>
                <label style={{ display: 'block', fontSize: 14, fontWeight: 500, color: '#334155', marginBottom: 6 }}>Description</label>
                <textarea
                  value={formDesc} onChange={(e) => setFormDesc(e.target.value)} rows={3}
                  placeholder="What does this application do?"
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e2e8f0', borderRadius: 8, fontSize: 14, resize: 'vertical', boxSizing: 'border-box', outline: 'none' }}
                />
              </div>
              <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                <button type="button" onClick={() => setShowCreate(false)}
                  style={{ padding: '9px 18px', backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 8, fontSize: 14, cursor: 'pointer', color: '#475569' }}>
                  Cancel
                </button>
                <button type="submit" disabled={creating}
                  style={{ padding: '9px 18px', backgroundColor: '#3b82f6', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer', opacity: creating ? 0.7 : 1 }}>
                  {creating ? 'Creating...' : 'Create App'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
