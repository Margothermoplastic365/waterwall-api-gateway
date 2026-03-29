'use client';

import React, { useEffect, useState } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token') || localStorage.getItem('admin_token') || localStorage.getItem('jwt_token');
}
function authHeaders(): Record<string, string> {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
}

interface Plan {
  id: string;
  name: string;
  description: string;
  rateLimits: string;
  quota: string;
  enforcement: string;
  status: string;
}

interface Subscription {
  id: string;
  applicationId: string;
  apiId: string;
  planId: string;
  environmentSlug?: string;
  status: string;
  createdAt: string;
}

const STATUS_STYLES: Record<string, { bg: string; color: string }> = {
  ACTIVE: { bg: '#dcfce7', color: '#16a34a' },
  PENDING: { bg: '#fef3c7', color: '#d97706' },
  APPROVED: { bg: '#dbeafe', color: '#2563eb' },
  REJECTED: { bg: '#fef2f2', color: '#dc2626' },
  REVOKED: { bg: '#fef2f2', color: '#dc2626' },
};

function parseJson(str: string): Record<string, string | number | boolean | null> {
  try { return JSON.parse(str); } catch { return {}; }
}

export default function SubscriptionsPage() {
  const [plans, setPlans] = useState<Plan[]>([]);
  const [subs, setSubs] = useState<Subscription[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

  useEffect(() => {
    async function load() {
      try {
        // Fetch plans, user's apps, and all subscriptions
        const [plansRes, appsRes, subsRes] = await Promise.all([
          fetch(`${API_BASE}/v1/plans`, { headers: authHeaders() }),
          fetch(`${IDENTITY_URL}/v1/applications`, { headers: authHeaders() }),
          fetch(`${API_BASE}/v1/subscriptions`, { headers: authHeaders() }),
        ]);
        if (plansRes.ok) {
          const pd = await plansRes.json();
          setPlans(Array.isArray(pd) ? pd : pd.content || []);
        }
        // Get user's app IDs to filter subscriptions
        let myAppIds: string[] = [];
        if (appsRes.ok) {
          const ad = await appsRes.json();
          const appList = Array.isArray(ad) ? ad : ad.content || [];
          myAppIds = appList.map((a: { id: string }) => a.id);
        }
        if (subsRes.ok) {
          const sd = await subsRes.json();
          const allSubs = Array.isArray(sd) ? sd : sd.content || [];
          // Only show subscriptions for the user's own applications
          const mySubs = myAppIds.length > 0
            ? allSubs.filter((s: Subscription) => myAppIds.includes(s.applicationId))
            : [];
          setSubs(mySubs);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const PLAN_ICONS: Record<string, string> = { Free: '\u{1F381}', Starter: '\u{1F680}', Pro: '\u{26A1}', Enterprise: '\u{1F3E2}' };
  const PLAN_COLORS: Record<string, string> = { Free: '#64748b', Starter: '#3b82f6', Pro: '#8b5cf6', Enterprise: '#f59e0b' };

  return (
    <div>
      <div style={{ marginBottom: 28 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>Subscriptions & Plans</h1>
        <p style={{ fontSize: 14, color: '#64748b', margin: 0 }}>Browse available plans and manage your API subscriptions</p>
      </div>

      {error && <div style={{ padding: '10px 16px', backgroundColor: '#fef2f2', border: '1px solid #fecaca', borderRadius: 8, color: '#dc2626', fontSize: 14, marginBottom: 20 }}>{error}</div>}

      {/* Plans Grid */}
      <h2 style={{ fontSize: 17, fontWeight: 600, color: '#1e293b', marginBottom: 16 }}>Available Plans</h2>
      {loading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 16, marginBottom: 36 }}>
          {[1, 2, 3, 4].map((i) => (
            <div key={i} style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: 24, height: 180 }} />
          ))}
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 16, marginBottom: 36 }}>
          {plans.map((plan) => {
            const rl = parseJson(plan.rateLimits);
            const qt = parseJson(plan.quota);
            const accent = PLAN_COLORS[plan.name] || '#3b82f6';
            return (
              <div key={plan.id} style={{
                backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0',
                padding: 24, position: 'relative', overflow: 'hidden',
              }}>
                <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 3, backgroundColor: accent }} />
                <div style={{ fontSize: 28, marginBottom: 8 }}>{PLAN_ICONS[plan.name] || '\u{1F4CB}'}</div>
                <h3 style={{ fontSize: 18, fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>{plan.name}</h3>
                <p style={{ fontSize: 13, color: '#64748b', margin: '0 0 16px', lineHeight: 1.5 }}>{plan.description}</p>
                <div style={{ fontSize: 12, color: '#475569', display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {rl.requestsPerMinute != null && (
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span style={{ color: '#94a3b8' }}>Rate limit</span>
                      <span style={{ fontWeight: 600 }}>{String(rl.requestsPerMinute)} req/min</span>
                    </div>
                  )}
                  {qt.monthlyRequests != null && (
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span style={{ color: '#94a3b8' }}>Monthly quota</span>
                      <span style={{ fontWeight: 600 }}>{Number(qt.monthlyRequests).toLocaleString()}</span>
                    </div>
                  )}
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: '#94a3b8' }}>Enforcement</span>
                    <span style={{ fontWeight: 600 }}>{plan.enforcement}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Active Subscriptions */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={{ fontSize: 17, fontWeight: 600, color: '#1e293b', margin: 0 }}>My Subscriptions</h2>
        <span style={{ fontSize: 13, color: '#64748b' }}>{subs.length} subscription{subs.length !== 1 ? 's' : ''}</span>
      </div>

      {!loading && subs.length === 0 ? (
        <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: 48, textAlign: 'center' }}>
          <div style={{ fontSize: 40, marginBottom: 12 }}>{'\u{2B50}'}</div>
          <h3 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginBottom: 6 }}>No subscriptions yet</h3>
          <p style={{ fontSize: 13, color: '#94a3b8', margin: 0 }}>
            Subscribe to an API from the <a href="/catalog" style={{ color: '#3b82f6' }}>catalog</a> to get started.
          </p>
        </div>
      ) : (
        <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', overflow: 'hidden' }}>
          {subs.map((sub, idx) => {
            const st = STATUS_STYLES[sub.status] || STATUS_STYLES.PENDING;
            const plan = plans.find((p) => p.id === sub.planId);
            return (
              <div key={sub.id} style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                padding: '14px 20px', borderTop: idx > 0 ? '1px solid #f1f5f9' : 'none',
              }}>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 14, color: '#0f172a' }}>
                    API: {sub.apiId.substring(0, 8)}...
                  </div>
                  <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>
                    Plan: {plan?.name || sub.planId.substring(0, 8)}
                    {sub.environmentSlug && <> &middot; <strong style={{ color: '#475569' }}>{sub.environmentSlug.toUpperCase()}</strong></>}
                    {' '}&middot; Since {new Date(sub.createdAt).toLocaleDateString()}
                  </div>
                </div>
                <span style={{
                  fontSize: 11, fontWeight: 600, padding: '3px 10px', borderRadius: 12,
                  backgroundColor: st.bg, color: st.color,
                }}>
                  {sub.status}
                </span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
