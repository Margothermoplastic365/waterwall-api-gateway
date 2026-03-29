'use client';

import { useEffect, useState, useMemo } from 'react';
import { DataTable, StatusBadge } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8082';

interface SecurityPosture {
  score: number;
  issues: SecurityIssue[];
}

interface SecurityIssue {
  id: string;
  severity: string;
  title: string;
  description: string;
  [key: string]: unknown;
}

interface BotStats {
  goodBots: number;
  badBotsBlocked: number;
  unknownChallenged: number;
}

interface AbuseRisk {
  consumerId: string;
  consumerName: string;
  riskScore: number;
  reason: string;
  lastActivity: string;
  [key: string]: unknown;
}

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

function scoreColor(score: number): string {
  if (score >= 80) return 'text-green-600';
  if (score >= 60) return 'text-amber-600';
  return 'text-red-600';
}

function scoreBg(score: number): string {
  if (score >= 80) return 'bg-green-100 text-green-600';
  if (score >= 60) return 'bg-amber-100 text-amber-600';
  return 'bg-red-100 text-red-600';
}

export default function SecurityDashboardPage() {
  const [posture, setPosture] = useState<SecurityPosture | null>(null);
  const [botStats, setBotStats] = useState<BotStats | null>(null);
  const [risks, setRisks] = useState<AbuseRisk[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError('');
      try {
        const [postureRes, botsRes, risksRes] = await Promise.all([
          fetch(`${GATEWAY_URL}/v1/gateway/security/posture`, { headers: authHeaders() }).catch(() => null),
          fetch(`${GATEWAY_URL}/v1/gateway/security/bots/stats`, { headers: authHeaders() }).catch(() => null),
          fetch(`${GATEWAY_URL}/v1/gateway/security/abuse/risk-scores`, { headers: authHeaders() }).catch(() => null),
        ]);
        if (postureRes?.ok) {
          setPosture(await postureRes.json());
        }
        if (botsRes?.ok) {
          setBotStats(await botsRes.json());
        }
        if (risksRes?.ok) {
          const data = await risksRes.json();
          setRisks(Array.isArray(data) ? data : data.content || data.data || []);
        }
      } catch {
        setError('Failed to load security data');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const severityBadge = (severity: string) => {
    const colors: Record<string, string> = {
      CRITICAL: 'bg-red-100 text-red-700',
      HIGH: 'bg-red-100 text-red-700',
      MEDIUM: 'bg-amber-100 text-amber-700',
      LOW: 'bg-blue-100 text-blue-700',
      INFO: 'bg-slate-100 text-slate-600',
    };
    return <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${colors[severity?.toUpperCase()] || 'bg-slate-100 text-slate-600'}`}>{severity}</span>;
  };

  const issueColumns: Column<SecurityIssue>[] = useMemo(
    () => [
      { key: 'severity', label: 'Severity', render: (row) => severityBadge(row.severity) },
      { key: 'title', label: 'Title' },
      { key: 'description', label: 'Description' },
    ],
    [],
  );

  const riskColumns: Column<AbuseRisk>[] = useMemo(
    () => [
      { key: 'consumerName', label: 'Consumer' },
      {
        key: 'riskScore',
        label: 'Risk Score',
        render: (row) => (
          <div className="flex items-center gap-2">
            <div className="w-16 h-2 rounded-full bg-slate-100 overflow-hidden">
              <div
                className={`h-full rounded-full ${row.riskScore >= 80 ? 'bg-red-500' : row.riskScore >= 50 ? 'bg-amber-500' : 'bg-green-500'}`}
                style={{ width: `${Math.min(row.riskScore, 100)}%` }}
              />
            </div>
            <span className={`font-bold text-sm ${row.riskScore >= 80 ? 'text-red-600' : row.riskScore >= 50 ? 'text-amber-600' : 'text-green-600'}`}>
              {row.riskScore}
            </span>
          </div>
        ),
      },
      { key: 'reason', label: 'Reason' },
      {
        key: 'lastActivity',
        label: 'Last Activity',
        render: (row) => <span className="text-sm text-slate-500">{row.lastActivity ? new Date(row.lastActivity).toLocaleString() : '-'}</span>,
      },
    ],
    [],
  );

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
        <div className="max-w-7xl mx-auto space-y-6">
          <div className="space-y-2">
            <div className="h-7 w-52 bg-slate-200 rounded-lg animate-pulse" />
            <div className="h-4 w-80 bg-slate-100 rounded-lg animate-pulse" />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="bg-white rounded-xl border border-slate-200 p-5 space-y-3">
                <div className="h-4 w-28 bg-slate-100 rounded animate-pulse" />
                <div className="h-8 w-20 bg-slate-200 rounded animate-pulse" />
              </div>
            ))}
          </div>
          {[...Array(2)].map((_, i) => (
            <div key={i} className="bg-white rounded-xl border border-slate-200 p-6 space-y-3">
              <div className="h-5 w-40 bg-slate-200 rounded animate-pulse" />
              {[...Array(3)].map((_, j) => (
                <div key={j} className="h-10 bg-slate-50 rounded-lg animate-pulse" />
              ))}
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50/50 p-6 lg:p-8">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Security Dashboard</h1>
          <p className="mt-1 text-sm text-slate-500">Security posture, bot detection, and abuse risk monitoring</p>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        {/* Security Posture Score */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Security Posture</p>
                <p className={`mt-1 text-2xl font-bold ${posture ? scoreColor(posture.score) : 'text-slate-400'}`}>
                  {posture ? posture.score : '-'}/100
                </p>
              </div>
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-xs font-bold ${posture ? scoreBg(posture.score) : 'bg-slate-100 text-slate-400'}`}>
                SEC
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Good Bots</p>
                <p className="mt-1 text-2xl font-bold text-slate-900">{botStats?.goodBots ?? 0}</p>
              </div>
              <div className="w-10 h-10 rounded-xl bg-green-100 text-green-600 flex items-center justify-center text-xs font-bold">
                BOT
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Bad Bots Blocked</p>
                <p className="mt-1 text-2xl font-bold text-red-600">
                  {botStats?.badBotsBlocked ?? 0}
                </p>
              </div>
              <div className="w-10 h-10 rounded-xl bg-red-100 text-red-600 flex items-center justify-center text-xs font-bold">
                BLK
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Unknown Challenged</p>
                <p className="mt-1 text-2xl font-bold text-amber-600">
                  {botStats?.unknownChallenged ?? 0}
                </p>
              </div>
              <div className="w-10 h-10 rounded-xl bg-amber-100 text-amber-600 flex items-center justify-center text-xs font-bold">
                CHK
              </div>
            </div>
          </div>
        </div>

        {/* Security Issues */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-6 pt-5 pb-3">
            <h3 className="text-base font-semibold text-slate-900">Security Issues</h3>
            <p className="text-sm text-slate-500 mt-1">Detected vulnerabilities and configuration issues</p>
          </div>
          <DataTable data={posture?.issues || []} columns={issueColumns} />
        </div>

        {/* Abuse Risk Scores */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="px-6 pt-5 pb-3">
            <h3 className="text-base font-semibold text-slate-900">High-Risk Consumers</h3>
            <p className="text-sm text-slate-500 mt-1">Consumers with elevated abuse risk scores</p>
          </div>
          <DataTable data={risks} columns={riskColumns} />
        </div>
      </div>
    </div>
  );
}
