'use client';

import { useEffect, useState, useRef } from 'react';
import { get } from '@gateway/shared-ui';

interface ClusterNode {
  hostname: string;
  ip: string;
  port: number;
  configVersion: string;
  status: string;
  lastHeartbeat: string;
  uptime: string;
}

export default function ClusterPage() {
  const [nodes, setNodes] = useState<ClusterNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const loadNodes = async () => {
    try {
      const data = await get<ClusterNode[]>('/v1/cluster/nodes');
      setNodes(Array.isArray(data) ? data : []);
      setError('');
    } catch {
      setError('Failed to load cluster nodes');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadNodes();
    intervalRef.current = setInterval(loadNodes, 30000);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, []);

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
        <div className="mx-auto max-w-7xl">
          <div className="mb-8">
            <div className="h-8 w-56 animate-pulse rounded-lg bg-slate-200" />
            <div className="mt-2 h-4 w-80 animate-pulse rounded-lg bg-slate-200" />
          </div>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-56 animate-pulse rounded-xl bg-white shadow-sm" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 p-6 lg:p-8">
      <div className="mx-auto max-w-7xl">
        {/* Header */}
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Gateway Cluster</h1>
            <p className="mt-1 text-sm text-slate-500">Cluster node status (auto-refreshes every 30s)</p>
          </div>
          <button
            className="inline-flex items-center gap-2 rounded-xl bg-white px-4 py-2.5 text-sm font-medium text-slate-700 shadow-sm ring-1 ring-slate-200 transition-colors hover:bg-slate-50"
            onClick={loadNodes}
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182" />
            </svg>
            Refresh Now
          </button>
        </div>

        {error && (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {nodes.map((node, i) => {
            const isUp = node.status?.toUpperCase() === 'UP';
            return (
              <div key={i} className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
                <div className="mb-4 flex items-center justify-between">
                  <span className="text-base font-semibold text-slate-900">{node.hostname}</span>
                  <span
                    className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                      isUp
                        ? 'bg-emerald-100 text-emerald-700'
                        : 'bg-red-100 text-red-700'
                    }`}
                  >
                    <span className={`mr-1.5 inline-block h-1.5 w-1.5 rounded-full ${isUp ? 'bg-emerald-500' : 'bg-red-500'}`} />
                    {node.status}
                  </span>
                </div>

                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-slate-500">IP</span>
                    <span className="text-sm font-medium text-slate-900">{node.ip}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-slate-500">Port</span>
                    <span className="text-sm font-medium text-slate-900">{node.port}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-slate-500">Config Version</span>
                    <span className="rounded bg-slate-100 px-2 py-0.5 font-mono text-xs text-slate-700">
                      {node.configVersion}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-slate-500">Last Heartbeat</span>
                    <span className="text-xs text-slate-700">
                      {new Date(node.lastHeartbeat).toLocaleString()}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-slate-500">Uptime</span>
                    <span className="text-sm text-slate-700">{node.uptime}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {nodes.length === 0 && !error && (
          <div className="rounded-xl bg-white p-12 text-center shadow-sm ring-1 ring-slate-200">
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-slate-100">
              <svg className="h-6 w-6 text-slate-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M5.25 14.25h13.5m-13.5 0a3 3 0 01-3-3m3 3a3 3 0 100 6h13.5a3 3 0 100-6m-16.5-3a3 3 0 013-3h13.5a3 3 0 013 3m-19.5 0a4.5 4.5 0 01.9-2.7L5.737 5.1a3.375 3.375 0 012.7-1.35h7.126c1.062 0 2.062.5 2.7 1.35l2.587 3.45a4.5 4.5 0 01.9 2.7m0 0a3 3 0 01-3 3m0 3h.008v.008h-.008v-.008zm0-6h.008v.008h-.008v-.008zm-3 6h.008v.008h-.008v-.008zm0-6h.008v.008h-.008v-.008z" />
              </svg>
            </div>
            <p className="text-sm text-slate-500">No cluster nodes found.</p>
          </div>
        )}
      </div>
    </div>
  );
}
