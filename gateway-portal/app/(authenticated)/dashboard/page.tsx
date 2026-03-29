'use client';

import React, { useEffect, useState } from 'react';
import { useAuth } from '@gateway/shared-ui/lib/auth';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';
const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token')
    || localStorage.getItem('admin_token')
    || localStorage.getItem('jwt_token');
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  return token
    ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
    : { 'Content-Type': 'application/json' };
}

interface DashboardData {
  appsCount: number;
  subscriptionsCount: number;
  apisCount: number;
  plansCount: number;
  recentApis: Array<{
    id: string;
    name: string;
    version: string;
    status: string;
    category: string;
  }>;
}

interface UsageData {
  requestsToday: number;
  requestsThisWeek: number;
  requestsThisMonth: number;
  averageLatencyMs: number;
  errorRate: number;
  activeSubscriptions: number;
  topApis: Array<{ apiId: string; apiName: string; requestCount: number }>;
}

interface CostData {
  estimatedCost: number;
  currency: string;
  totalRequests: number;
  includedRequests: number;
  overageRequests: number;
  planName: string;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [data, setData] = useState<DashboardData>({
    appsCount: 0,
    subscriptionsCount: 0,
    apisCount: 0,
    plansCount: 0,
    recentApis: [],
  });
  const [usage, setUsage] = useState<UsageData | null>(null);
  const [cost, setCost] = useState<CostData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function fetchDashboard() {
      const headers = authHeaders();
      try {
        const results = await Promise.allSettled([
          fetch(`${API_BASE}/v1/apis?page=0&size=5&status=PUBLISHED`, { headers }).then((r) => r.ok ? r.json() : null),
          fetch(`${API_BASE}/v1/plans`, { headers }).then((r) => r.ok ? r.json() : null),
          fetch(`${API_BASE}/v1/subscriptions`, { headers }).then((r) => r.ok ? r.json() : null),
          fetch(`${IDENTITY_URL}/v1/applications`, { headers }).then((r) => r.ok ? r.json() : null),
          fetch(`${API_BASE}/v1/consumer/usage/summary`, { headers }).then((r) => r.ok ? r.json() : null),
          fetch(`${API_BASE}/v1/consumer/usage/cost`, { headers }).then((r) => r.ok ? r.json() : null),
        ]);

        const apisData = results[0].status === 'fulfilled' ? results[0].value : null;
        const plansData = results[1].status === 'fulfilled' ? results[1].value : null;
        const subsData = results[2].status === 'fulfilled' ? results[2].value : null;
        const appsData = results[3].status === 'fulfilled' ? results[3].value : null;
        const usageData = results[4].status === 'fulfilled' ? results[4].value : null;
        const costData = results[5].status === 'fulfilled' ? results[5].value : null;

        const appsList = Array.isArray(appsData) ? appsData : appsData?.content || [];
        const subsList = Array.isArray(subsData) ? subsData : subsData?.content || [];

        // Filter subscriptions to only count those belonging to the user's apps
        const myAppIds = new Set(appsList.map((a: { id: string }) => a.id));
        const mySubs = subsList.filter((s: { applicationId: string }) => myAppIds.has(s.applicationId));

        setData({
          apisCount: apisData?.totalElements ?? apisData?.content?.length ?? 0,
          plansCount: Array.isArray(plansData) ? plansData.length : 0,
          subscriptionsCount: mySubs.length,
          appsCount: appsList.length,
          recentApis: (apisData?.content || []).slice(0, 5),
        });
        if (usageData) setUsage(usageData);
        if (costData) setCost(costData);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load dashboard');
      } finally {
        setLoading(false);
      }
    }
    fetchDashboard();
  }, []);

  const greeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  };

  const statCards = [
    { label: 'API Calls This Month', value: usage?.requestsThisMonth?.toLocaleString() ?? '0', color: 'blue', href: '/billing',
      icon: (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
        </svg>
      ),
    },
    { label: 'My Applications', value: data.appsCount, color: 'violet', href: '/apps',
      icon: (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 1.5H8.25A2.25 2.25 0 006 3.75v16.5a2.25 2.25 0 002.25 2.25h7.5A2.25 2.25 0 0018 20.25V3.75a2.25 2.25 0 00-2.25-2.25H13.5m-3 0V3h3V1.5m-3 0h3m-3 18.75h3" />
        </svg>
      ),
    },
    { label: 'Subscriptions', value: data.subscriptionsCount, color: 'emerald', href: '/subscriptions',
      icon: (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.563.563 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.563.563 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z" />
        </svg>
      ),
    },
    { label: 'Estimated Cost', value: cost ? `${cost.currency === 'USD' ? '$' : cost.currency}${cost.estimatedCost?.toFixed(2) ?? '0.00'}` : '$0.00', color: 'amber', href: '/billing',
      icon: (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 18.75a60.07 60.07 0 0115.797 2.101c.727.198 1.453-.342 1.453-1.096V18.75M3.75 4.5v.75A.75.75 0 013 6h-.75m0 0v-.375c0-.621.504-1.125 1.125-1.125H20.25M2.25 6v9m18-10.5v.75c0 .414.336.75.75.75h.75m-1.5-1.5h.375c.621 0 1.125.504 1.125 1.125v9.75c0 .621-.504 1.125-1.125 1.125h-.375m1.5-1.5H21a.75.75 0 00-.75.75v.75m0 0H3.75m0 0h-.375a1.125 1.125 0 01-1.125-1.125V15m1.5 1.5v-.75A.75.75 0 003 15h-.75M15 10.5a3 3 0 11-6 0 3 3 0 016 0zm3 0h.008v.008H18V10.5zm-12 0h.008v.008H6V10.5z" />
        </svg>
      ),
    },
  ];

  const colorMap: Record<string, { bg: string; text: string; iconBg: string }> = {
    blue: { bg: 'bg-blue-50', text: 'text-blue-600', iconBg: 'bg-blue-100' },
    violet: { bg: 'bg-violet-50', text: 'text-violet-600', iconBg: 'bg-violet-100' },
    emerald: { bg: 'bg-emerald-50', text: 'text-emerald-600', iconBg: 'bg-emerald-100' },
    amber: { bg: 'bg-amber-50', text: 'text-amber-600', iconBg: 'bg-amber-100' },
  };

  const quickActions = [
    { label: 'Browse Catalog', desc: 'Find and subscribe to APIs', href: '/catalog', color: 'bg-blue-500',
      icon: (
        <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
        </svg>
      ),
    },
    { label: 'My Applications', desc: 'Manage apps and API keys', href: '/apps', color: 'bg-violet-500',
      icon: (
        <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 1.5H8.25A2.25 2.25 0 006 3.75v16.5a2.25 2.25 0 002.25 2.25h7.5A2.25 2.25 0 0018 20.25V3.75a2.25 2.25 0 00-2.25-2.25H13.5m-3 0V3h3V1.5m-3 0h3m-3 18.75h3" />
        </svg>
      ),
    },
    { label: 'API Console', desc: 'Test APIs interactively', href: '/console', color: 'bg-emerald-500',
      icon: (
        <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 7.5l3 2.25-3 2.25m4.5 0h3m-9 8.25h13.5A2.25 2.25 0 0021 18V6a2.25 2.25 0 00-2.25-2.25H5.25A2.25 2.25 0 003 6v12a2.25 2.25 0 002.25 2.25z" />
        </svg>
      ),
    },
    { label: 'Documentation', desc: 'Search API docs', href: '/docs', color: 'bg-amber-500',
      icon: (
        <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.042A8.967 8.967 0 006 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 016 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 016-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0018 18a8.967 8.967 0 00-6 2.292m0-14.25v14.25" />
        </svg>
      ),
    },
  ];

  if (loading) {
    return (
      <div className="animate-pulse">
        <div className="h-8 w-64 bg-slate-100 rounded-lg mb-2" />
        <div className="h-4 w-48 bg-slate-50 rounded mb-8" />
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-5 mb-8">
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-white rounded-xl border border-slate-200 p-6 h-28" />
          ))}
        </div>
        <div className="h-5 w-32 bg-slate-100 rounded mb-4" />
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="bg-white rounded-xl border border-slate-200 p-5 h-20" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div>
      {/* Welcome Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-900 mb-1">
          {greeting()}, {user?.displayName || user?.email?.split('@')[0] || 'Developer'}
        </h1>
        <p className="text-sm text-slate-500">
          Here&apos;s what&apos;s happening on the API platform today.
        </p>
      </div>

      {error && (
        <div className="mb-6 px-4 py-3 bg-red-50 border border-red-200 text-red-600 rounded-lg text-sm flex items-start gap-2">
          <svg className="w-4 h-4 mt-0.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
          </svg>
          {error}
        </div>
      )}

      {/* Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-9">
        {statCards.map((card) => {
          const colors = colorMap[card.color];
          return (
            <a
              key={card.label}
              href={card.href}
              className="group bg-white rounded-xl border border-slate-200 p-5 shadow-sm hover:shadow-md hover:border-blue-200 transition-all duration-150 block"
            >
              <div className="flex justify-between items-start">
                <div>
                  <p className="text-xs font-medium text-slate-500 uppercase tracking-wide mb-2">{card.label}</p>
                  <p className={`text-3xl font-bold ${colors.text}`}>{card.value}</p>
                </div>
                <div className={`w-10 h-10 rounded-lg ${colors.iconBg} ${colors.text} flex items-center justify-center shrink-0`}>
                  {card.icon}
                </div>
              </div>
            </a>
          );
        })}
      </div>

      {/* Usage Summary */}
      {usage && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 mb-9">
          {/* Quick Stats */}
          <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-slate-700">Usage Summary</h3>
              <a href="/billing" className="text-xs font-medium text-blue-600 hover:text-blue-700">View details &rarr;</a>
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div className="text-center p-3 bg-slate-50 rounded-lg">
                <p className="text-xl font-bold text-slate-800">{usage.requestsToday?.toLocaleString() ?? 0}</p>
                <p className="text-[11px] text-slate-500 mt-1">Today</p>
              </div>
              <div className="text-center p-3 bg-slate-50 rounded-lg">
                <p className="text-xl font-bold text-slate-800">{usage.requestsThisWeek?.toLocaleString() ?? 0}</p>
                <p className="text-[11px] text-slate-500 mt-1">This Week</p>
              </div>
              <div className="text-center p-3 bg-slate-50 rounded-lg">
                <p className="text-xl font-bold text-slate-800">{usage.averageLatencyMs ? `${Math.round(usage.averageLatencyMs)}ms` : '-'}</p>
                <p className="text-[11px] text-slate-500 mt-1">Avg Latency</p>
              </div>
            </div>
            {usage.errorRate > 0 && (
              <div className={`mt-3 px-3 py-2 rounded-lg text-xs font-medium flex items-center gap-2 ${usage.errorRate > 5 ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-700'}`}>
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126z" /></svg>
                Error rate: {usage.errorRate.toFixed(1)}%
              </div>
            )}
          </div>

          {/* Top APIs */}
          <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
            <h3 className="text-sm font-semibold text-slate-700 mb-4">Top APIs by Usage</h3>
            {usage.topApis && usage.topApis.length > 0 ? (
              <div className="space-y-3">
                {usage.topApis.slice(0, 5).map((api, i) => {
                  const maxCount = usage.topApis[0]?.requestCount || 1;
                  const pct = Math.round((api.requestCount / maxCount) * 100);
                  return (
                    <div key={api.apiId || i}>
                      <div className="flex justify-between text-xs mb-1">
                        <span className="font-medium text-slate-700 truncate">{api.apiName || api.apiId}</span>
                        <span className="text-slate-500 shrink-0 ml-2">{api.requestCount?.toLocaleString()}</span>
                      </div>
                      <div className="h-1.5 bg-slate-100 rounded-full overflow-hidden">
                        <div className="h-full bg-blue-500 rounded-full" style={{ width: `${pct}%` }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="text-sm text-slate-400 text-center py-6">No API usage data yet</p>
            )}
          </div>
        </div>
      )}

      {/* Quick Actions */}
      <h2 className="text-base font-semibold text-slate-800 mb-4">Quick Actions</h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-9">
        {quickActions.map((action) => (
          <a
            key={action.label}
            href={action.href}
            className="group flex items-center gap-3.5 p-4 bg-white rounded-xl border border-slate-200 hover:border-blue-200 hover:shadow-sm transition-all duration-150"
          >
            <div className={`w-10 h-10 rounded-lg ${action.color} flex items-center justify-center shrink-0 shadow-sm`}>
              {action.icon}
            </div>
            <div className="min-w-0">
              <p className="text-sm font-semibold text-slate-800 group-hover:text-blue-600 transition-colors">{action.label}</p>
              <p className="text-xs text-slate-400 mt-0.5">{action.desc}</p>
            </div>
          </a>
        ))}
      </div>

      {/* Recent APIs */}
      {data.recentApis.length > 0 && (
        <>
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-base font-semibold text-slate-800">Available APIs</h2>
            <a href="/catalog" className="text-xs font-medium text-blue-600 hover:text-blue-700 transition-colors">
              View all &rarr;
            </a>
          </div>
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            {data.recentApis.map((api, idx) => (
              <a
                key={api.id}
                href={`/catalog/${api.id}`}
                className={`flex items-center justify-between px-5 py-3.5 hover:bg-slate-50 transition-colors ${
                  idx > 0 ? 'border-t border-slate-100' : ''
                }`}
              >
                <div className="flex items-center gap-3.5 min-w-0">
                  <div className="w-9 h-9 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center text-sm font-bold shrink-0">
                    {api.name.charAt(0)}
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-slate-800 truncate">{api.name}</p>
                    <p className="text-xs text-slate-400">
                      {api.version} &middot; {api.category || 'General'}
                    </p>
                  </div>
                </div>
                <span
                  className={`text-[11px] font-semibold px-2.5 py-1 rounded-full shrink-0 ${
                    api.status === 'PUBLISHED' ? 'bg-green-50 text-green-700' :
                    api.status === 'DEPRECATED' ? 'bg-amber-50 text-amber-700' :
                    'bg-slate-100 text-slate-500'
                  }`}
                >
                  {api.status}
                </span>
              </a>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
