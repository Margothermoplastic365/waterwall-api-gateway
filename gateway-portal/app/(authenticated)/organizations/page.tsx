'use client';

import React, { useEffect, useState } from 'react';

const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token') || localStorage.getItem('admin_token') || localStorage.getItem('jwt_token');
}
function authHeaders(): Record<string, string> {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
}

interface Org {
  id: string;
  name: string;
  slug: string;
  description?: string;
  status: string;
  domain?: string;
  createdAt: string;
}

interface Member {
  id: string;
  userId: string;
  orgRole: string;
  joinedAt: string;
  user?: { email: string; displayName?: string };
}

export default function OrganizationsPage() {
  const [orgs, setOrgs] = useState<Org[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expandedOrg, setExpandedOrg] = useState<string | null>(null);
  const [members, setMembers] = useState<Record<string, Member[]>>({});
  const [loadingMembers, setLoadingMembers] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [creating, setCreating] = useState(false);
  const [inviteOrgId, setInviteOrgId] = useState<string | null>(null);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviting, setInviting] = useState(false);

  useEffect(() => {
    async function load() {
      try {
        const res = await fetch(`${IDENTITY_URL}/v1/organizations`, { headers: authHeaders() });
        if (res.ok) {
          const data = await res.json();
          setOrgs(Array.isArray(data) ? data : data.content || []);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load');
      } finally { setLoading(false); }
    }
    load();
  }, []);

  const toggleOrg = async (orgId: string) => {
    if (expandedOrg === orgId) { setExpandedOrg(null); return; }
    setExpandedOrg(orgId);
    if (!members[orgId]) {
      setLoadingMembers(orgId);
      try {
        const res = await fetch(`${IDENTITY_URL}/v1/organizations/${orgId}/members`, { headers: authHeaders() });
        if (res.ok) {
          const data = await res.json();
          setMembers((prev) => ({ ...prev, [orgId]: Array.isArray(data) ? data : data.content || [] }));
        }
      } catch { /* silent */ }
      finally { setLoadingMembers(null); }
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim()) return;
    setCreating(true);
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/organizations`, {
        method: 'POST', headers: authHeaders(),
        body: JSON.stringify({ name: formName, description: formDesc }),
      });
      if (!res.ok) throw new Error('Failed to create');
      setShowCreate(false); setFormName(''); setFormDesc('');
      const data = await res.json();
      setOrgs((prev) => [...prev, data]);
    } catch (err) { setError(err instanceof Error ? err.message : 'Create failed'); }
    finally { setCreating(false); }
  };

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail.trim() || !inviteOrgId) return;
    setInviting(true);
    try {
      await fetch(`${IDENTITY_URL}/v1/organizations/${inviteOrgId}/members/invite`, {
        method: 'POST', headers: authHeaders(), body: JSON.stringify({ email: inviteEmail }),
      });
      setInviteEmail(''); setInviteOrgId(null);
    } catch { /* silent */ }
    finally { setInviting(false); }
  };

  const ROLE_STYLES: Record<string, { bg: string; color: string }> = {
    OWNER: { bg: '#fef3c7', color: '#d97706' }, ADMIN: { bg: '#dbeafe', color: '#2563eb' }, MEMBER: { bg: '#f1f5f9', color: '#64748b' },
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 28 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>Organizations</h1>
          <p style={{ fontSize: 14, color: '#64748b', margin: 0 }}>Manage your teams and organization memberships</p>
        </div>
        <button onClick={() => setShowCreate(true)} style={{
          display: 'flex', alignItems: 'center', gap: 6, padding: '9px 18px', backgroundColor: '#3b82f6', color: '#fff',
          border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer',
        }}><span style={{ fontSize: 18, lineHeight: 1 }}>+</span> New Org</button>
      </div>

      {error && <div style={{ padding: '10px 16px', backgroundColor: '#fef2f2', border: '1px solid #fecaca', borderRadius: 8, color: '#dc2626', fontSize: 14, marginBottom: 20 }}>{error}</div>}

      {loading ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {[1, 2].map((i) => <div key={i} style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: 24, height: 80 }} />)}
        </div>
      ) : orgs.length === 0 ? (
        <div style={{ backgroundColor: '#fff', borderRadius: 12, border: '1px solid #e2e8f0', padding: 64, textAlign: 'center' }}>
          <div style={{ fontSize: 48, marginBottom: 16 }}>{'\u{1F465}'}</div>
          <h2 style={{ fontSize: 20, fontWeight: 600, color: '#0f172a', marginBottom: 8 }}>No organizations yet</h2>
          <p style={{ fontSize: 14, color: '#64748b', marginBottom: 24 }}>Create an organization to collaborate with your team.</p>
          <button onClick={() => setShowCreate(true)} style={{ padding: '10px 24px', backgroundColor: '#3b82f6', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>Create Organization</button>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {orgs.map((org) => (
            <div key={org.id} style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', overflow: 'hidden' }}>
              <div onClick={() => toggleOrg(org.id)} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', cursor: 'pointer' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{ width: 40, height: 40, borderRadius: 10, background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, fontWeight: 700, color: '#fff' }}>
                    {org.name.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 15, color: '#0f172a' }}>{org.name}</div>
                    <div style={{ fontSize: 12, color: '#94a3b8' }}>{org.slug}{org.domain && ` \u00B7 ${org.domain}`}</div>
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <span style={{ fontSize: 11, fontWeight: 600, padding: '3px 10px', borderRadius: 12, backgroundColor: '#dcfce7', color: '#16a34a' }}>{org.status}</span>
                  <span style={{ fontSize: 18, color: '#94a3b8', transition: 'transform 0.2s', transform: expandedOrg === org.id ? 'rotate(180deg)' : 'rotate(0)' }}>&#9662;</span>
                </div>
              </div>
              {expandedOrg === org.id && (
                <div style={{ borderTop: '1px solid #f1f5f9', padding: '16px 20px', backgroundColor: '#fafbff' }}>
                  {org.description && <p style={{ fontSize: 13, color: '#64748b', margin: '0 0 16px' }}>{org.description}</p>}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                    <h3 style={{ fontSize: 14, fontWeight: 600, color: '#334155', margin: 0 }}>Members</h3>
                    <button onClick={() => { setInviteOrgId(org.id); setInviteEmail(''); }} style={{ padding: '5px 14px', fontSize: 12, backgroundColor: '#eff6ff', color: '#3b82f6', border: '1px solid #bfdbfe', borderRadius: 6, cursor: 'pointer', fontWeight: 500 }}>Invite Member</button>
                  </div>
                  {inviteOrgId === org.id && (
                    <form onSubmit={handleInvite} style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                      <input type="email" value={inviteEmail} onChange={(e) => setInviteEmail(e.target.value)} placeholder="email@example.com" required autoFocus style={{ flex: 1, padding: '7px 12px', border: '1px solid #e2e8f0', borderRadius: 6, fontSize: 13, outline: 'none' }} />
                      <button type="submit" disabled={inviting} style={{ padding: '7px 16px', backgroundColor: '#3b82f6', color: '#fff', border: 'none', borderRadius: 6, fontSize: 13, fontWeight: 500, cursor: 'pointer' }}>{inviting ? 'Sending...' : 'Send Invite'}</button>
                      <button type="button" onClick={() => setInviteOrgId(null)} style={{ padding: '7px 12px', backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 6, fontSize: 13, cursor: 'pointer', color: '#64748b' }}>Cancel</button>
                    </form>
                  )}
                  {loadingMembers === org.id ? (
                    <p style={{ fontSize: 13, color: '#94a3b8' }}>Loading members...</p>
                  ) : (members[org.id] || []).length === 0 ? (
                    <p style={{ fontSize: 13, color: '#94a3b8' }}>No members found.</p>
                  ) : (
                    <div style={{ borderRadius: 8, border: '1px solid #e2e8f0', overflow: 'hidden' }}>
                      {(members[org.id] || []).map((m, idx) => {
                        const rs = ROLE_STYLES[m.orgRole] || ROLE_STYLES.MEMBER;
                        return (
                          <div key={m.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 16px', borderTop: idx > 0 ? '1px solid #f1f5f9' : 'none', backgroundColor: '#fff' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                              <div style={{ width: 30, height: 30, borderRadius: '50%', backgroundColor: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 600, color: '#64748b' }}>
                                {(m.user?.displayName || m.user?.email || '?').charAt(0).toUpperCase()}
                              </div>
                              <div>
                                <div style={{ fontSize: 13, fontWeight: 500, color: '#0f172a' }}>{m.user?.displayName || m.user?.email || m.userId.substring(0, 8)}</div>
                                {m.user?.email && <div style={{ fontSize: 11, color: '#94a3b8' }}>{m.user.email}</div>}
                              </div>
                            </div>
                            <span style={{ fontSize: 11, fontWeight: 600, padding: '2px 10px', borderRadius: 12, backgroundColor: rs.bg, color: rs.color }}>{m.orgRole}</span>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {showCreate && (
        <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }} onClick={() => setShowCreate(false)}>
          <div style={{ backgroundColor: '#fff', borderRadius: 12, width: '100%', maxWidth: 460, boxShadow: '0 20px 60px rgba(0,0,0,0.15)' }} onClick={(e) => e.stopPropagation()}>
            <div style={{ padding: '20px 24px', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600, color: '#0f172a' }}>Create Organization</h2>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', fontSize: 20, color: '#94a3b8', cursor: 'pointer' }}>&times;</button>
            </div>
            <form onSubmit={handleCreate} style={{ padding: 24 }}>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: 14, fontWeight: 500, color: '#334155', marginBottom: 6 }}>Name *</label>
                <input type="text" value={formName} onChange={(e) => setFormName(e.target.value)} required autoFocus placeholder="e.g. My Company" style={{ width: '100%', padding: '10px 12px', border: '1px solid #e2e8f0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', outline: 'none' }} />
              </div>
              <div style={{ marginBottom: 24 }}>
                <label style={{ display: 'block', fontSize: 14, fontWeight: 500, color: '#334155', marginBottom: 6 }}>Description</label>
                <textarea value={formDesc} onChange={(e) => setFormDesc(e.target.value)} rows={3} placeholder="What does this organization do?" style={{ width: '100%', padding: '10px 12px', border: '1px solid #e2e8f0', borderRadius: 8, fontSize: 14, resize: 'vertical', boxSizing: 'border-box', outline: 'none' }} />
              </div>
              <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                <button type="button" onClick={() => setShowCreate(false)} style={{ padding: '9px 18px', backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 8, fontSize: 14, cursor: 'pointer', color: '#475569' }}>Cancel</button>
                <button type="submit" disabled={creating} style={{ padding: '9px 18px', backgroundColor: '#3b82f6', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer', opacity: creating ? 0.7 : 1 }}>{creating ? 'Creating...' : 'Create'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
