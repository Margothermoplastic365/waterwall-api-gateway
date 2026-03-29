'use client';

import { useEffect, useState, useMemo } from 'react';
import { DataTable } from '@gateway/shared-ui';
import type { Column } from '@gateway/shared-ui';

const ANALYTICS_URL = process.env.NEXT_PUBLIC_ANALYTICS_URL || 'http://localhost:8083';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface EventStats {
  eventsPerSec: number;
  consumerLag: number;
  avgLatency: number;
}

interface TopicThroughput {
  topic: string;
  eventsPerSec: number;
  totalEvents: number;
  avgSize: number;
  [key: string]: unknown;
}

interface ConsumerLag {
  consumerId: string;
  consumerName: string;
  topic: string;
  lag: number;
  lastOffset: number;
  [key: string]: unknown;
}

const RANGES = ['1h', '6h', '24h', '7d', '30d'] as const;
type Range = (typeof RANGES)[number];

function fetchEvents<T>(path: string): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || '' : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(`${ANALYTICS_URL}${path}`, { headers }).then((r) => {
    if (!r.ok) throw new Error('Failed');
    return r.json();
  });
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function EventAnalyticsPage() {
  const [range, setRange] = useState<Range>('24h');
  const [stats, setStats] = useState<EventStats | null>(null);
  const [throughput, setThroughput] = useState<TopicThroughput[]>([]);
  const [consumers, setConsumers] = useState<ConsumerLag[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError('');
      try {
        const [throughputData, consumerData, latencyData] = await Promise.all([
          fetchEvents<{ eventsPerSec: number; topics: TopicThroughput[] }>(
            `/v1/analytics/events/throughput?range=${range}`,
          ),
          fetchEvents<ConsumerLag[]>(`/v1/analytics/events/consumers?range=${range}`),
          fetchEvents<{ avgLatency: number }>(`/v1/analytics/events/latency?range=${range}`),
        ]);

        const topicList = Array.isArray(throughputData)
          ? throughputData
          : throughputData.topics ?? [];
        const eps =
          typeof throughputData === 'object' && 'eventsPerSec' in throughputData
            ? throughputData.eventsPerSec
            : 0;
        const consumerList = Array.isArray(consumerData) ? consumerData : [];
        const totalLag = consumerList.reduce((sum, c) => sum + (c.lag ?? 0), 0);

        setStats({
          eventsPerSec: eps,
          consumerLag: totalLag,
          avgLatency:
            typeof latencyData === 'object' && 'avgLatency' in latencyData
              ? latencyData.avgLatency
              : 0,
        });
        setThroughput(topicList);
        setConsumers(consumerList);
      } catch {
        setError('Failed to load event analytics data');
        setStats({ eventsPerSec: 0, consumerLag: 0, avgLatency: 0 });
        setThroughput([]);
        setConsumers([]);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [range]);

  const topicColumns: Column<TopicThroughput>[] = useMemo(
    () => [
      { key: 'topic', label: 'Topic' },
      {
        key: 'eventsPerSec',
        label: 'Events/sec',
        render: (row) => row.eventsPerSec.toLocaleString(),
      },
      {
        key: 'totalEvents',
        label: 'Total Events',
        render: (row) => row.totalEvents.toLocaleString(),
      },
      {
        key: 'avgSize',
        label: 'Avg Size',
        render: (row) => `${row.avgSize} B`,
      },
    ],
    [],
  );

  const consumerColumns: Column<ConsumerLag>[] = useMemo(
    () => [
      { key: 'consumerName', label: 'Consumer' },
      { key: 'topic', label: 'Topic' },
      {
        key: 'lag',
        label: 'Lag',
        render: (row) => (
          <span className={`font-medium ${row.lag > 1000 ? 'text-red-600' : row.lag > 100 ? 'text-amber-600' : 'text-emerald-600'}`}>
            {row.lag.toLocaleString()}
          </span>
        ),
      },
      {
        key: 'lastOffset',
        label: 'Last Offset',
        render: (row) => row.lastOffset.toLocaleString(),
      },
    ],
    [],
  );

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
        <div className="mx-auto max-w-7xl">
          <div className="mb-8">
            <div className="h-8 w-56 animate-pulse rounded-lg bg-slate-200" />
            <div className="mt-2 h-4 w-96 animate-pulse rounded-lg bg-slate-200" />
          </div>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-28 animate-pulse rounded-xl bg-white shadow-sm" />
            ))}
          </div>
          <div className="mt-6 h-48 animate-pulse rounded-xl bg-white shadow-sm" />
          <div className="mt-6 h-48 animate-pulse rounded-xl bg-white shadow-sm" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
      <div className="mx-auto max-w-7xl">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Event Analytics</h1>
          <p className="mt-1 text-sm text-slate-500">Event throughput, consumer lag, and latency insights</p>
        </div>

        {error && (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {/* Time Range Selector */}
        <div className="mb-6 inline-flex items-center gap-1 rounded-xl bg-white p-1 shadow-sm ring-1 ring-slate-200">
          {RANGES.map((r) => (
            <button
              key={r}
              className={`rounded-lg px-4 py-2 text-sm font-medium transition-all ${
                range === r
                  ? 'bg-purple-600 text-white shadow-sm'
                  : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
              }`}
              onClick={() => setRange(r)}
            >
              {r}
            </button>
          ))}
        </div>

        {/* Stat Cards */}
        {stats && (
          <div className="mb-8 grid grid-cols-1 gap-6 sm:grid-cols-3">
            <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">Events/sec</p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">{stats.eventsPerSec.toLocaleString()}</p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-xs font-bold text-blue-600">
                  EPS
                </div>
              </div>
            </div>
            <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">Consumer Lag</p>
                  <p
                    className={`mt-2 text-2xl font-bold ${
                      stats.consumerLag > 1000
                        ? 'text-red-600'
                        : stats.consumerLag > 100
                          ? 'text-amber-600'
                          : 'text-emerald-600'
                    }`}
                  >
                    {stats.consumerLag.toLocaleString()}
                  </p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50 text-xs font-bold text-amber-600">
                  LAG
                </div>
              </div>
            </div>
            <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">Avg Latency</p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">{stats.avgLatency}ms</p>
                </div>
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-50 text-xs font-bold text-purple-600">
                  LAT
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Per-topic Throughput */}
        <div className="mb-6 overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          <div className="border-b border-slate-200 px-6 py-4">
            <h3 className="text-base font-semibold text-slate-900">Per-topic Throughput</h3>
          </div>
          <DataTable data={throughput} columns={topicColumns} />
        </div>

        {/* Consumer Lag Table */}
        <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          <div className="border-b border-slate-200 px-6 py-4">
            <h3 className="text-base font-semibold text-slate-900">Consumer Lag</h3>
          </div>
          <DataTable data={consumers} columns={consumerColumns} />
        </div>
      </div>
    </div>
  );
}
