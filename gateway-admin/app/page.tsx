'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';
const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

interface Stats {
  totalApis: number;
  totalUsers: number;
  activeSubscriptions: number;
  trafficToday: number;
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

async function fetchCount(url: string): Promise<number> {
  try {
    const res = await fetch(url, { headers: authHeaders() });
    if (!res.ok) return 0;
    const data = await res.json();
    if (Array.isArray(data)) return data.length;
    if (typeof data.totalElements === 'number') return data.totalElements;
    if (typeof data.total === 'number') return data.total;
    if (Array.isArray(data.content)) return data.totalElements ?? data.content.length;
    if (Array.isArray(data.data)) return data.total ?? data.data.length;
    return 0;
  } catch {
    return 0;
  }
}

const statCards = [
  { key: 'totalApis', label: 'Total APIs', icon: (
    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
  ), color: 'bg-blue-50 text-blue-600', href: '/apis' },
  { key: 'totalUsers', label: 'Total Users', icon: (
    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" /></svg>
  ), color: 'bg-purple-50 text-purple-600', href: '/users' },
  { key: 'activeSubscriptions', label: 'Subscriptions', icon: (
    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M13.19 8.688a4.5 4.5 0 011.242 7.244l-4.5 4.5a4.5 4.5 0 01-6.364-6.364l1.757-1.757m9.86-4.122a4.5 4.5 0 00-6.364-6.364L4.5 6.375l1.757 1.757" /></svg>
  ), color: 'bg-green-50 text-green-600', href: '/subscriptions' },
  { key: 'trafficToday', label: 'Traffic Today', icon: (
    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M3.75 3v11.25A2.25 2.25 0 006 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0118 16.5h-2.25m-7.5 0h7.5m-7.5 0l-1 3m8.5-3l1 3m0 0l.5 1.5m-.5-1.5h-9.5m0 0l-.5 1.5" /></svg>
  ), color: 'bg-amber-50 text-amber-600', href: '/analytics' },
];

const quickLinks = [
  { label: 'Create API', href: '/apis/new', desc: 'Register a new API endpoint', icon: (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" /></svg>
  )},
  { label: 'Manage Plans', href: '/plans', desc: 'Configure rate limits & quotas', icon: (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6zm0 9.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25zM13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25A2.25 2.25 0 0118 10.5h-2.25a2.25 2.25 0 01-2.25-2.25V6z" /></svg>
  )},
  { label: 'Approvals', href: '/approvals', desc: 'Review pending requests', icon: (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
  )},
  { label: 'Audit Log', href: '/audit', desc: 'View system activity trail', icon: (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" /></svg>
  )},
  { label: 'Gateway Health', href: '/gateway', desc: 'Monitor runtime status', icon: (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M21 7.5l-9-5.25L3 7.5m18 0l-9 5.25m9-5.25v9l-9 5.25M3 7.5l9 5.25M3 7.5v9l9 5.25" /></svg>
  )},
  { label: 'Policies', href: '/policies', desc: 'Manage security & rate policies', icon: (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" /></svg>
  )},
];

export default function AdminDashboard() {
  const [stats, setStats] = useState<Stats>({
    totalApis: 0,
    totalUsers: 0,
    activeSubscriptions: 0,
    trafficToday: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function loadStats() {
      setLoading(true);
      setError('');
      try {
        const [apis, users, subscriptions] = await Promise.all([
          fetchCount(`${API_URL}/v1/apis`),
          fetchCount(`${IDENTITY_URL}/v1/users`),
          fetchCount(`${API_URL}/v1/subscriptions`),
        ]);
        setStats({ totalApis: apis, totalUsers: users, activeSubscriptions: subscriptions, trafficToday: 0 });
      } catch {
        setError('Failed to load dashboard statistics');
      } finally {
        setLoading(false);
      }
    }
    loadStats();
  }, []);

  return (
    <div className="max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-900">Dashboard</h1>
        <p className="text-slate-500 mt-1">Platform overview and quick actions</p>
      </div>

      {error && <div className="mb-6 px-4 py-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">{error}</div>}

      {/* Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-8">
        {statCards.map((card) => (
          <Link
            key={card.key}
            href={card.href}
            className="bg-white border border-slate-200 rounded-xl p-5 hover:shadow-md hover:border-slate-300 transition-all group no-underline"
          >
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">{card.label}</p>
                <p className="text-3xl font-bold text-slate-900 mt-1.5">
                  {loading ? (
                    <span className="inline-block w-12 h-8 bg-slate-100 rounded animate-pulse" />
                  ) : (
                    (stats[card.key as keyof Stats] ?? 0).toLocaleString()
                  )}
                </p>
              </div>
              <div className={`w-11 h-11 rounded-lg ${card.color} flex items-center justify-center group-hover:scale-110 transition-transform`}>
                {card.icon}
              </div>
            </div>
          </Link>
        ))}
      </div>

      {/* Quick Actions */}
      <div className="mb-8">
        <h2 className="text-lg font-semibold text-slate-900 mb-4">Quick Actions</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {quickLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="flex items-center gap-4 bg-white border border-slate-200 rounded-xl px-5 py-4 hover:shadow-md hover:border-purple-200 hover:bg-purple-50/30 transition-all group no-underline"
            >
              <div className="w-10 h-10 rounded-lg bg-slate-100 text-slate-500 flex items-center justify-center group-hover:bg-purple-100 group-hover:text-purple-600 transition-colors shrink-0">
                {link.icon}
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-800 group-hover:text-purple-700 transition-colors">{link.label}</p>
                <p className="text-xs text-slate-400 mt-0.5">{link.desc}</p>
              </div>
            </Link>
          ))}
        </div>
      </div>

      {/* Activity placeholder */}
      <div className="bg-white border border-slate-200 rounded-xl p-6">
        <h2 className="text-lg font-semibold text-slate-900 mb-4">Recent Activity</h2>
        <div className="text-center py-12">
          <svg className="w-12 h-12 mx-auto text-slate-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <p className="text-sm text-slate-400">Activity feed will show recent API registrations, user signups, and system events.</p>
        </div>
      </div>
    </div>
  );
}
