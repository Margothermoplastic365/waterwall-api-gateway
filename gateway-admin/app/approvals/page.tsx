'use client';

import { useEffect, useState, useCallback } from 'react';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface Approval {
  id: string;
  type: string;
  resourceId: string;
  resource?: string;
  status: string;
  requestedBy?: string;
  submittedBy?: string;
  approvedBy?: string;
  rejectedReason?: string;
  currentLevel?: number;
  maxLevel?: number;
  cooldownUntil?: string;
  requestedAt: string;
  resolvedAt?: string;
}

type Tab = 'pending' | 'all';

function getToken(): string {
  if (typeof window !== 'undefined') {
    return localStorage.getItem('admin_token') || localStorage.getItem('token') || '';
  }
  return '';
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  return token
    ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
    : { 'Content-Type': 'application/json' };
}

const STATUS_CLASSES: Record<string, string> = {
  PENDING: 'bg-amber-100 text-amber-700',
  APPROVED: 'bg-emerald-100 text-emerald-700',
  REJECTED: 'bg-red-100 text-red-700',
};

const TYPE_LABELS: Record<string, string> = {
  VERSION_PUBLISH: 'Version Publish',
  API_PUBLISH: 'API Publish',
  POLICY_CHANGE: 'Policy Change',
  DEPLOYMENT: 'Deployment',
  MIGRATION: 'Migration',
};

export default function ApprovalsPage() {
  const [approvals, setApprovals] = useState<Approval[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<Tab>('pending');
  const [rejectId, setRejectId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [acting, setActing] = useState<string | null>(null);

  const fetchApprovals = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const endpoint = tab === 'pending' ? '/v1/approvals/pending' : '/v1/approvals';
      const res = await fetch(`${API_URL}${endpoint}`, { headers: authHeaders() });
      if (!res.ok) throw new Error('Failed to load');
      const data = await res.json();
      setApprovals(Array.isArray(data) ? data : data.content || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load approvals');
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => { fetchApprovals(); }, [fetchApprovals]);

  const handleApprove = async (id: string) => {
    setActing(id);
    try {
      // Try version review endpoint first (for VERSION_PUBLISH type)
      const approval = approvals.find((a) => a.id === id);
      if (approval?.type === 'VERSION_PUBLISH') {
        const res = await fetch(`${API_URL}/v1/versions/review/${id}`, {
          method: 'POST', headers: authHeaders(),
          body: JSON.stringify({ approved: true }),
        });
        if (!res.ok) { const b = await res.json().catch(() => null); throw new Error(b?.message || 'Failed'); }
      } else {
        const res = await fetch(`${API_URL}/v1/approvals/${id}/approve`, {
          method: 'POST', headers: authHeaders(),
        });
        if (!res.ok) throw new Error('Failed');
      }
      fetchApprovals();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Approve failed');
    } finally { setActing(null); }
  };

  const handleReject = async (id: string) => {
    if (!rejectReason.trim()) return;
    setActing(id);
    try {
      const approval = approvals.find((a) => a.id === id);
      if (approval?.type === 'VERSION_PUBLISH') {
        const res = await fetch(`${API_URL}/v1/versions/review/${id}`, {
          method: 'POST', headers: authHeaders(),
          body: JSON.stringify({ approved: false, rejectionReason: rejectReason }),
        });
        if (!res.ok) { const b = await res.json().catch(() => null); throw new Error(b?.message || 'Failed'); }
      } else {
        const res = await fetch(`${API_URL}/v1/approvals/${id}/reject`, {
          method: 'POST', headers: authHeaders(),
          body: JSON.stringify({ reason: rejectReason }),
        });
        if (!res.ok) throw new Error('Failed');
      }
      setRejectId(null);
      setRejectReason('');
      fetchApprovals();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Reject failed');
    } finally { setActing(null); }
  };

  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length;

  return (
    <main className="p-6 max-w-5xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Approval Queue</h1>
        <p className="text-sm text-gray-500 mt-1">Review and approve version publishes, deployments, and policy changes</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-0 border-b border-gray-200 mb-6">
        {(['pending', 'all'] as const).map((t) => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-5 py-3 text-sm font-medium border-b-2 -mb-px transition-colors ${
              tab === t ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-400 hover:text-gray-600'
            }`}>
            {t === 'pending' ? `Pending (${pendingCount})` : 'All'}
          </button>
        ))}
      </div>

      {error && <div className="mb-4 p-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>}

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-5 animate-pulse">
              <div className="h-4 w-48 bg-gray-100 rounded mb-3" />
              <div className="h-3 w-32 bg-gray-50 rounded" />
            </div>
          ))}
        </div>
      ) : approvals.length === 0 ? (
        <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
          <div className="text-4xl mb-3">{tab === 'pending' ? '\u{2705}' : '\u{1F4CB}'}</div>
          <h3 className="text-base font-semibold text-gray-700 mb-1">
            {tab === 'pending' ? 'No pending approvals' : 'No approvals yet'}
          </h3>
          <p className="text-sm text-gray-400">
            {tab === 'pending' ? 'All caught up! Nothing needs your review.' : 'Approval requests will appear here.'}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {approvals.map((a) => (
            <div key={a.id} className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="p-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                      <span className="text-sm font-semibold text-gray-900">
                        {TYPE_LABELS[a.type] || a.type}
                      </span>
                      <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-full ${STATUS_CLASSES[a.status] || STATUS_CLASSES.PENDING}`}>
                        {a.status}
                      </span>
                      {a.currentLevel && a.maxLevel && a.maxLevel > 1 && (
                        <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-blue-50 text-blue-700">
                          Level {a.currentLevel}/{a.maxLevel}
                        </span>
                      )}
                    </div>
                    <div className="text-xs text-gray-500 space-y-0.5">
                      <div>Resource: <span className="font-mono text-gray-600">{(a.resourceId || a.resource || '').substring(0, 12)}...</span></div>
                      <div>Requested: {new Date(a.requestedAt).toLocaleString()}</div>
                      {a.submittedBy && <div>Submitted by: <span className="font-mono text-gray-600">{a.submittedBy.substring(0, 8)}...</span></div>}
                      {a.rejectedReason && <div className="text-red-600">Reason: {a.rejectedReason}</div>}
                      {a.cooldownUntil && new Date(a.cooldownUntil) > new Date() && (
                        <div className="text-amber-600 font-medium">Cooldown until: {new Date(a.cooldownUntil).toLocaleString()}</div>
                      )}
                    </div>

                    {/* Approval level progress */}
                    {a.maxLevel && a.maxLevel > 1 && (
                      <div className="flex items-center gap-1 mt-3">
                        {Array.from({ length: a.maxLevel }, (_, i) => i + 1).map((level) => (
                          <div key={level} className="flex items-center gap-1">
                            <div className={`w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold ${
                              level < (a.currentLevel || 1) ? 'bg-emerald-100 text-emerald-700' :
                              level === (a.currentLevel || 1) && a.status === 'PENDING' ? 'bg-blue-100 text-blue-700 ring-2 ring-blue-300' :
                              'bg-gray-100 text-gray-400'
                            }`}>
                              {level < (a.currentLevel || 1) ? '\u2713' : level}
                            </div>
                            {level < (a.maxLevel || 1) && (
                              <div className={`w-6 h-0.5 ${level < (a.currentLevel || 1) ? 'bg-emerald-300' : 'bg-gray-200'}`} />
                            )}
                          </div>
                        ))}
                        <span className="text-[10px] text-gray-400 ml-2">
                          {['', 'Technical', 'Compliance', 'Platform'][a.currentLevel || 1] || `Level ${a.currentLevel}`}
                        </span>
                      </div>
                    )}
                  </div>

                  {/* Action buttons */}
                  {a.status === 'PENDING' && tab === 'pending' && (
                    <div className="flex gap-2 shrink-0">
                      {rejectId === a.id ? (
                        <div className="flex gap-2 items-end">
                          <div>
                            <input type="text" value={rejectReason} onChange={(e) => setRejectReason(e.target.value)}
                              placeholder="Rejection reason..." autoFocus
                              className="px-3 py-1.5 border border-gray-300 rounded text-sm w-48 focus:outline-none focus:ring-2 focus:ring-red-500/20" />
                          </div>
                          <button onClick={() => handleReject(a.id)} disabled={acting === a.id || !rejectReason.trim()}
                            className="px-3 py-1.5 text-xs font-medium bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50">
                            {acting === a.id ? '...' : 'Confirm'}
                          </button>
                          <button onClick={() => { setRejectId(null); setRejectReason(''); }}
                            className="px-3 py-1.5 text-xs font-medium border border-gray-300 rounded hover:bg-gray-50">
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <>
                          <button onClick={() => handleApprove(a.id)} disabled={acting === a.id}
                            className="px-3 py-1.5 text-xs font-medium bg-emerald-600 text-white rounded hover:bg-emerald-700 disabled:opacity-50">
                            {acting === a.id ? 'Approving...' : 'Approve'}
                          </button>
                          <button onClick={() => setRejectId(a.id)}
                            className="px-3 py-1.5 text-xs font-medium bg-red-50 text-red-700 border border-red-200 rounded hover:bg-red-100">
                            Reject
                          </button>
                        </>
                      )}
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </main>
  );
}
