'use client';

import React, { useEffect, useState, useCallback } from 'react';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface DeploymentEnvInfo {
  deployed: boolean;
  version?: string;
  deployedAt?: string;
}

interface Environment {
  id: string;
  name: string;
  status: string;
  apiCount: number;
  lastDeployment?: string;
}

interface Deployment {
  apiId: string;
  apiName: string;
  environments: Record<string, DeploymentEnvInfo>;
}

interface ApiOption {
  id: string;
  name: string;
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

const ENV_ORDER = ['DEV', 'UAT', 'PRE-PROD', 'PROD'] as const;

const DEFAULT_ENVS: Environment[] = ENV_ORDER.map(function (name) {
  return { id: name.toLowerCase(), name: name, status: 'ACTIVE', apiCount: 0 };
});

const ENV_COLORS: Record<string, { border: string; bg: string; text: string; icon: string }> = {
  DEV: { border: 'border-blue-500', bg: 'bg-blue-50', text: 'text-blue-700', icon: 'bg-blue-100 text-blue-600' },
  UAT: { border: 'border-amber-500', bg: 'bg-amber-50', text: 'text-amber-700', icon: 'bg-amber-100 text-amber-600' },
  'PRE-PROD': { border: 'border-purple-500', bg: 'bg-purple-50', text: 'text-purple-700', icon: 'bg-purple-100 text-purple-600' },
  PROD: { border: 'border-green-500', bg: 'bg-green-50', text: 'text-green-700', icon: 'bg-green-100 text-green-600' },
};

export default function EnvironmentsPage() {
  const [environments, setEnvironments] = useState<Environment[]>([]);
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [apis, setApis] = useState<ApiOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [showDeployModal, setShowDeployModal] = useState(false);
  const [deployApiId, setDeployApiId] = useState('');
  const [deployTargetEnv, setDeployTargetEnv] = useState('');
  const [deployLoading, setDeployLoading] = useState(false);
  const [deployError, setDeployError] = useState('');

  const [showPromoteModal, setShowPromoteModal] = useState(false);
  const [promoteApiId, setPromoteApiId] = useState('');
  const [promoteApiName, setPromoteApiName] = useState('');
  const [promoteTarget, setPromoteTarget] = useState('');
  const [promoteLoading, setPromoteLoading] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 5000);
  };

  const fetchData = useCallback(async function () {
    setLoading(true);
    setError('');
    try {
      const envRes = await fetch(API_URL + '/v1/environments', { headers: authHeaders() });
      const deployRes = await fetch(API_URL + '/v1/environments/deployments?apiId=all', { headers: authHeaders() });
      const apiRes = await fetch(API_URL + '/v1/apis', { headers: authHeaders() });

      if (envRes.ok) {
        const data = await envRes.json();
        const list = Array.isArray(data) ? data : (data.content || data.data || []);
        setEnvironments(list);
      }
      if (deployRes.ok) {
        const data = await deployRes.json();
        const list = Array.isArray(data) ? data : (data.content || data.data || []);
        setDeployments(list);
      }
      if (apiRes.ok) {
        const data = await apiRes.json();
        const list = Array.isArray(data) ? data : (data.content || data.data || []);
        setApis(list.map(function (a: ApiOption) { return { id: a.id, name: a.name }; }));
      }
    } catch (_e) {
      setError('Failed to load environment data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(function () {
    fetchData();
  }, [fetchData]);

  const envCards = environments.length > 0 ? environments : DEFAULT_ENVS;

  async function handleDeploy(e: React.FormEvent) {
    e.preventDefault();
    setDeployLoading(true);
    setDeployError('');
    try {
      const res = await fetch(API_URL + '/v1/environments/deploy', {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ apiId: deployApiId, targetEnvironment: deployTargetEnv }),
      });
      if (!res.ok) {
        const data = await res.json().catch(function () { return null; });
        throw new Error((data && data.message) || 'Deployment failed');
      }
      setShowDeployModal(false);
      setDeployApiId('');
      setDeployTargetEnv('');
      fetchData();
    } catch (err) {
      setDeployError(err instanceof Error ? err.message : 'Deployment failed');
    } finally {
      setDeployLoading(false);
    }
  }

  async function handlePromote() {
    if (!promoteApiId || !promoteTarget) return;
    setPromoteLoading(true);
    try {
      const res = await fetch(API_URL + '/v1/migrations', {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ apiId: promoteApiId, targetEnvironment: promoteTarget }),
      });
      if (!res.ok) throw new Error('Promotion failed');
      setShowPromoteModal(false);
      setPromoteApiId('');
      setPromoteApiName('');
      setPromoteTarget('');
      fetchData();
    } catch (_e) {
      showToast('Promotion failed', 'error');
    } finally {
      setPromoteLoading(false);
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-7xl px-6 py-10">
        {/* Header skeleton */}
        <div className="mb-8">
          <div className="h-8 w-48 animate-pulse rounded-lg bg-gray-200" />
          <div className="mt-2 h-4 w-80 animate-pulse rounded bg-gray-200" />
        </div>
        {/* Cards skeleton */}
        <div className="mb-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {[0, 1, 2, 3].map(function (i) {
            return (
              <div key={i} className="rounded-xl border-t-4 border-gray-200 bg-white p-6 shadow-sm">
                <div className="h-4 w-16 animate-pulse rounded bg-gray-200" />
                <div className="mt-4 h-10 w-20 animate-pulse rounded bg-gray-200" />
                <div className="mt-3 h-3 w-32 animate-pulse rounded bg-gray-200" />
              </div>
            );
          })}
        </div>
        {/* Table skeleton */}
        <div className="rounded-xl bg-white p-6 shadow-sm">
          <div className="h-6 w-52 animate-pulse rounded bg-gray-200" />
          <div className="mt-2 h-4 w-72 animate-pulse rounded bg-gray-200" />
          <div className="mt-6 space-y-3">
            {[0, 1, 2].map(function (i) {
              return <div key={i} className="h-12 w-full animate-pulse rounded bg-gray-100" />;
            })}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-6 py-10">
      {toast && (
        <div className={`fixed top-4 right-4 z-50 flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg border max-w-sm ${
          toast.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-emerald-50 border-emerald-200 text-emerald-800'
        }`}>
          {toast.type === 'error' ? (
            <svg className="w-5 h-5 shrink-0 text-red-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>
          ) : (
            <svg className="w-5 h-5 shrink-0 text-emerald-500 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
          )}
          <p className="text-sm font-medium flex-1">{toast.message}</p>
          <button onClick={() => setToast(null)} className="shrink-0 opacity-50 hover:opacity-100">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
          </button>
        </div>
      )}
      {/* Header */}
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">
            Environments
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            Manage deployment environments and API promotions
          </p>
        </div>
        <button
          className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
          onClick={function () { setShowDeployModal(true); }}
        >
          <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
          </svg>
          Deploy API
        </button>
      </div>

      {/* Error banner */}
      {error && (
        <div className="mb-6 flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 flex-shrink-0 text-red-500" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
          </svg>
          {error}
        </div>
      )}

      {/* Environment stat cards */}
      <div className="mb-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {envCards.map(function (env) {
          const colors = ENV_COLORS[env.name] || ENV_COLORS['DEV'];
          return (
            <div
              key={env.id}
              className={`relative overflow-hidden rounded-xl border border-gray-100 bg-white p-6 shadow-sm transition hover:shadow-md ${colors.border} border-t-4`}
            >
              <div className="flex items-start justify-between">
                <div>
                  <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-gray-500">
                    <span
                      className={`inline-block h-2 w-2 rounded-full ${
                        env.status === 'ACTIVE' ? 'bg-green-500' : 'bg-amber-500'
                      }`}
                    />
                    {env.name}
                  </div>
                  <div className="mt-3 text-3xl font-bold text-gray-900">
                    {env.apiCount}
                  </div>
                  <div className="mt-1 text-xs text-gray-400">
                    {env.lastDeployment
                      ? 'Last: ' + new Date(env.lastDeployment).toLocaleDateString()
                      : 'No deployments'}
                  </div>
                </div>
                <div
                  className={`flex h-10 w-10 items-center justify-center rounded-lg text-xs font-bold ${colors.icon}`}
                >
                  {env.name.slice(0, 3)}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Deployment matrix */}
      <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-gray-200">
        <div className="border-b border-gray-100 px-6 py-5">
          <h3 className="text-base font-semibold text-gray-900">API Deployment Matrix</h3>
          <p className="mt-1 text-sm text-gray-500">Which APIs are deployed to each environment</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50/60">
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500">
                  API Name
                </th>
                {ENV_ORDER.map(function (env) {
                  return (
                    <th key={env} className="px-6 py-3 text-center text-xs font-semibold uppercase tracking-wider text-gray-500">
                      {env}
                    </th>
                  );
                })}
                <th className="px-6 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {deployments.length === 0 ? (
                <tr>
                  <td colSpan={ENV_ORDER.length + 2} className="px-6 py-16 text-center">
                    <div className="flex flex-col items-center gap-3">
                      <svg xmlns="http://www.w3.org/2000/svg" className="h-12 w-12 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                      </svg>
                      <p className="text-sm font-medium text-gray-400">No deployment data available</p>
                      <p className="text-xs text-gray-300">Deploy an API to see it here</p>
                    </div>
                  </td>
                </tr>
              ) : (
                deployments.map(function (dep) {
                  return (
                    <tr key={dep.apiId} className="transition hover:bg-gray-50/50">
                      <td className="whitespace-nowrap px-6 py-4 font-medium text-gray-900">
                        {dep.apiName}
                      </td>
                      {ENV_ORDER.map(function (env) {
                        const info = dep.environments ? dep.environments[env] : undefined;
                        return (
                          <td key={env} className="px-6 py-4 text-center">
                            {info && info.deployed ? (
                              <span className="inline-flex items-center rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20">
                                {info.version || 'Deployed'}
                              </span>
                            ) : (
                              <span className="inline-flex items-center rounded-full bg-gray-50 px-2.5 py-0.5 text-xs font-medium text-gray-400 ring-1 ring-inset ring-gray-200">
                                --
                              </span>
                            )}
                          </td>
                        );
                      })}
                      <td className="whitespace-nowrap px-6 py-4 text-right">
                        <button
                          className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 hover:text-indigo-600 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1"
                          onClick={function () {
                            setPromoteApiId(dep.apiId);
                            setPromoteApiName(dep.apiName);
                            setShowPromoteModal(true);
                          }}
                        >
                          <svg xmlns="http://www.w3.org/2000/svg" className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                            <path fillRule="evenodd" d="M3.293 9.707a1 1 0 010-1.414l6-6a1 1 0 011.414 0l6 6a1 1 0 01-1.414 1.414L11 5.414V17a1 1 0 11-2 0V5.414L4.707 9.707a1 1 0 01-1.414 0z" clipRule="evenodd" />
                          </svg>
                          Promote
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Deploy modal */}
      {showDeployModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
          onClick={function () { setShowDeployModal(false); }}
        >
          <div
            className="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl"
            onClick={function (e) { e.stopPropagation(); }}
          >
            <div className="mb-5 flex items-center justify-between">
              <div>
                <h3 className="text-lg font-semibold text-gray-900">Deploy API</h3>
                <p className="mt-0.5 text-xs text-gray-500">Select an API and target environment</p>
              </div>
              <button
                className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
                onClick={function () { setShowDeployModal(false); }}
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                </svg>
              </button>
            </div>

            {deployError && (
              <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">
                {deployError}
              </div>
            )}

            <form onSubmit={handleDeploy}>
              <div className="mb-4">
                <label className="mb-1.5 block text-sm font-medium text-gray-700">API</label>
                <select
                  className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  value={deployApiId}
                  onChange={function (e) { setDeployApiId(e.target.value); }}
                  required
                >
                  <option value="">Select API...</option>
                  {apis.map(function (a) {
                    return <option key={a.id} value={a.id}>{a.name}</option>;
                  })}
                </select>
              </div>

              <div className="mb-6">
                <label className="mb-1.5 block text-sm font-medium text-gray-700">Target Environment</label>
                <select
                  className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  value={deployTargetEnv}
                  onChange={function (e) { setDeployTargetEnv(e.target.value); }}
                  required
                >
                  <option value="">Select Environment...</option>
                  {ENV_ORDER.map(function (env) {
                    return <option key={env} value={env}>{env}</option>;
                  })}
                </select>
              </div>

              <div className="flex items-center justify-end gap-3">
                <button
                  type="button"
                  className="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1"
                  onClick={function () { setShowDeployModal(false); }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={deployLoading}
                >
                  {deployLoading ? (
                    <span className="flex items-center gap-2">
                      <svg className="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                      Deploying...
                    </span>
                  ) : 'Deploy'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Promote modal */}
      {showPromoteModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
          onClick={function () { setShowPromoteModal(false); }}
        >
          <div
            className="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl"
            onClick={function (e) { e.stopPropagation(); }}
          >
            <div className="mb-5 flex items-center justify-between">
              <div>
                <h3 className="text-lg font-semibold text-gray-900">Promote {promoteApiName}</h3>
                <p className="mt-0.5 text-xs text-gray-500">Select the target environment for promotion</p>
              </div>
              <button
                className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
                onClick={function () { setShowPromoteModal(false); }}
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                </svg>
              </button>
            </div>

            <div className="mb-6">
              <label className="mb-1.5 block text-sm font-medium text-gray-700">Target Environment</label>
              <select
                className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                value={promoteTarget}
                onChange={function (e) { setPromoteTarget(e.target.value); }}
              >
                <option value="">Select target...</option>
                {ENV_ORDER.map(function (env) {
                  return <option key={env} value={env}>{env}</option>;
                })}
              </select>
            </div>

            <div className="flex items-center justify-end gap-3">
              <button
                className="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1"
                onClick={function () { setShowPromoteModal(false); }}
              >
                Cancel
              </button>
              <button
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={!promoteTarget || promoteLoading}
                onClick={handlePromote}
              >
                {promoteLoading ? (
                  <span className="flex items-center gap-2">
                    <svg className="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Promoting...
                  </span>
                ) : 'Promote'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
