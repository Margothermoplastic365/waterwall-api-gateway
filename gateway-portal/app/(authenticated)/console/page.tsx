'use client';

import React, { useEffect, useState } from 'react';
import { apiClient } from '@gateway/shared-ui/lib/api-client';

interface ApiInfo {
  id: string;
  name: string;
  basePath: string;
  version: string;
  protocol?: string;
}

interface Route {
  method: string;
  path: string;
  description: string;
}

interface AppOption {
  id: string;
  name: string;
}

interface ApiKeyOption {
  id: string;
  name: string;
  prefix: string;
}

interface ResponseData {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body: string;
}

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8082';

const methodColors: Record<string, string> = {
  GET: 'bg-emerald-100 text-emerald-700',
  POST: 'bg-blue-100 text-blue-700',
  PUT: 'bg-amber-100 text-amber-700',
  PATCH: 'bg-orange-100 text-orange-700',
  DELETE: 'bg-red-100 text-red-700',
};

export default function ConsolePage() {
  const [apis, setApis] = useState<ApiInfo[]>([]);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [apps, setApps] = useState<AppOption[]>([]);
  const [apiKeys, setApiKeys] = useState<ApiKeyOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Selections
  const [selectedApi, setSelectedApi] = useState('');
  const [selectedRoute, setSelectedRoute] = useState('');
  const [selectedApp, setSelectedApp] = useState('');
  const [selectedKey, setSelectedKey] = useState('');
  const [customHeaders, setCustomHeaders] = useState('');
  const [requestBody, setRequestBody] = useState('');

  // Response
  const [response, setResponse] = useState<ResponseData | null>(null);
  const [executing, setExecuting] = useState(false);

  // Protocol-specific state
  const [graphqlQuery, setGraphqlQuery] = useState('{\n  \n}');
  const [graphqlVariables, setGraphqlVariables] = useState('');
  const [soapBody, setSoapBody] = useState('<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">\n  <soapenv:Header/>\n  <soapenv:Body>\n  </soapenv:Body>\n</soapenv:Envelope>');
  const [soapAction, setSoapAction] = useState('');
  const [wsConnected, setWsConnected] = useState(false);
  const [wsMessages, setWsMessages] = useState<{ direction: 'sent' | 'received'; data: string; time: string }[]>([]);
  const [wsSendText, setWsSendText] = useState('');
  const [wsRef, setWsRef] = useState<WebSocket | null>(null);
  const [sseConnected, setSseConnected] = useState(false);
  const [sseEvents, setSseEvents] = useState<{ type: string; data: string; time: string }[]>([]);
  const [sseRef, setSseRef] = useState<EventSource | null>(null);

  // Response tab
  const [responseTab, setResponseTab] = useState<'body' | 'headers'>('body');

  useEffect(() => {
    async function init() {
      try {
        const [apisData, appsData] = await Promise.all([
          apiClient<ApiInfo[] | { content: ApiInfo[] }>('/v1/apis?status=PUBLISHED&size=100'),
          apiClient<AppOption[] | { content: AppOption[] }>('/v1/applications?size=100'),
        ]);
        setApis(Array.isArray(apisData) ? apisData : apisData.content ?? []);
        setApps(Array.isArray(appsData) ? appsData : appsData.content ?? []);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Failed to load data');
      } finally {
        setLoading(false);
      }
    }
    init();
  }, []);

  // Load routes when API is selected
  useEffect(() => {
    if (!selectedApi) {
      setRoutes([]);
      return;
    }
    apiClient<Route[] | { routes: Route[] } | { content: Route[] }>(`/v1/apis/${selectedApi}/routes`)
      .then((data) => setRoutes(Array.isArray(data) ? data : (data as { routes: Route[] }).routes ?? (data as { content: Route[] }).content ?? []))
      .catch(() => setRoutes([]));
  }, [selectedApi]);

  // Load API keys when app is selected
  useEffect(() => {
    if (!selectedApp) {
      setApiKeys([]);
      return;
    }
    apiClient<ApiKeyOption[] | { content: ApiKeyOption[] }>(`/v1/applications/${selectedApp}/api-keys`)
      .then((data) => setApiKeys(Array.isArray(data) ? data : data.content ?? []))
      .catch(() => setApiKeys([]));
  }, [selectedApp]);

  const getSelectedRoute = (): Route | undefined => {
    const idx = parseInt(selectedRoute, 10);
    return routes[idx];
  };

  const handleExecute = async () => {
    const route = getSelectedRoute();
    if (!route || !selectedApi) return;

    const api = apis.find((a) => a.id === selectedApi);
    if (!api) return;

    setExecuting(true);
    setResponse(null);
    setError('');

    try {
      const url = `${GATEWAY_URL}${api.basePath}${route.path}`;
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      };

      if (selectedKey) {
        headers['X-API-Key'] = selectedKey;
      }

      // Parse custom headers
      if (customHeaders.trim()) {
        customHeaders.split('\n').forEach((line) => {
          const colonIdx = line.indexOf(':');
          if (colonIdx > 0) {
            headers[line.substring(0, colonIdx).trim()] = line.substring(colonIdx + 1).trim();
          }
        });
      }

      const fetchOptions: RequestInit = {
        method: route.method,
        headers,
      };

      if (['POST', 'PUT', 'PATCH'].includes(route.method) && requestBody.trim()) {
        fetchOptions.body = requestBody;
      }

      const res = await fetch(url, fetchOptions);
      const bodyText = await res.text();
      const respHeaders: Record<string, string> = {};
      res.headers.forEach((v, k) => {
        respHeaders[k] = v;
      });

      setResponse({
        status: res.status,
        statusText: res.statusText,
        headers: respHeaders,
        body: bodyText,
      });
      setResponseTab('body');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Request failed');
    } finally {
      setExecuting(false);
    }
  };

  const formatJson = (text: string): string => {
    try {
      return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
      return text;
    }
  };

  const getSelectedApiProtocol = (): string => {
    if (!selectedApi) return 'REST';
    const api = apis.find((a) => a.id === selectedApi);
    return api?.protocol?.toUpperCase() || 'REST';
  };

  const handleGraphqlExecute = async () => {
    const api = apis.find((a) => a.id === selectedApi);
    if (!api) return;
    setExecuting(true);
    setResponse(null);
    setError('');
    try {
      const url = `${GATEWAY_URL}${api.basePath}`;
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (selectedKey) headers['X-API-Key'] = selectedKey;
      if (customHeaders.trim()) {
        customHeaders.split('\n').forEach((line) => {
          const colonIdx = line.indexOf(':');
          if (colonIdx > 0) headers[line.substring(0, colonIdx).trim()] = line.substring(colonIdx + 1).trim();
        });
      }
      const body: Record<string, unknown> = { query: graphqlQuery };
      if (graphqlVariables.trim()) {
        try { body.variables = JSON.parse(graphqlVariables); } catch { /* ignore */ }
      }
      const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) });
      const bodyText = await res.text();
      const respHeaders: Record<string, string> = {};
      res.headers.forEach((v, k) => { respHeaders[k] = v; });
      setResponse({ status: res.status, statusText: res.statusText, headers: respHeaders, body: bodyText });
      setResponseTab('body');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Request failed');
    } finally {
      setExecuting(false);
    }
  };

  const handleSoapExecute = async () => {
    const api = apis.find((a) => a.id === selectedApi);
    if (!api) return;
    setExecuting(true);
    setResponse(null);
    setError('');
    try {
      const url = `${GATEWAY_URL}${api.basePath}`;
      const headers: Record<string, string> = { 'Content-Type': 'text/xml;charset=UTF-8' };
      if (soapAction) headers['SOAPAction'] = soapAction;
      if (selectedKey) headers['X-API-Key'] = selectedKey;
      if (customHeaders.trim()) {
        customHeaders.split('\n').forEach((line) => {
          const colonIdx = line.indexOf(':');
          if (colonIdx > 0) headers[line.substring(0, colonIdx).trim()] = line.substring(colonIdx + 1).trim();
        });
      }
      const res = await fetch(url, { method: 'POST', headers, body: soapBody });
      const bodyText = await res.text();
      const respHeaders: Record<string, string> = {};
      res.headers.forEach((v, k) => { respHeaders[k] = v; });
      setResponse({ status: res.status, statusText: res.statusText, headers: respHeaders, body: bodyText });
      setResponseTab('body');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Request failed');
    } finally {
      setExecuting(false);
    }
  };

  const handleWsConnect = () => {
    const api = apis.find((a) => a.id === selectedApi);
    if (!api) return;
    const wsUrl = `${GATEWAY_URL.replace(/^http/, 'ws')}${api.basePath}`;
    const ws = new WebSocket(wsUrl);
    ws.onopen = () => {
      setWsConnected(true);
      setWsMessages((prev) => [...prev, { direction: 'received', data: '[Connected]', time: new Date().toLocaleTimeString() }]);
    };
    ws.onmessage = (event) => {
      setWsMessages((prev) => [...prev, { direction: 'received', data: String(event.data), time: new Date().toLocaleTimeString() }]);
    };
    ws.onclose = () => {
      setWsConnected(false);
      setWsMessages((prev) => [...prev, { direction: 'received', data: '[Disconnected]', time: new Date().toLocaleTimeString() }]);
    };
    ws.onerror = () => {
      setWsMessages((prev) => [...prev, { direction: 'received', data: '[Error]', time: new Date().toLocaleTimeString() }]);
    };
    setWsRef(ws);
  };

  const handleWsDisconnect = () => {
    if (wsRef) {
      wsRef.close();
      setWsRef(null);
    }
  };

  const handleWsSend = () => {
    if (wsRef && wsSendText.trim()) {
      wsRef.send(wsSendText);
      setWsMessages((prev) => [...prev, { direction: 'sent', data: wsSendText, time: new Date().toLocaleTimeString() }]);
      setWsSendText('');
    }
  };

  const handleSseConnect = () => {
    const api = apis.find((a) => a.id === selectedApi);
    if (!api) return;
    const url = `${GATEWAY_URL}${api.basePath}`;
    const es = new EventSource(url);
    es.onopen = () => {
      setSseConnected(true);
      setSseEvents((prev) => [...prev, { type: 'system', data: '[Connected]', time: new Date().toLocaleTimeString() }]);
    };
    es.onmessage = (event) => {
      setSseEvents((prev) => [...prev, { type: event.type, data: String(event.data), time: new Date().toLocaleTimeString() }]);
    };
    es.onerror = () => {
      setSseConnected(false);
      setSseEvents((prev) => [...prev, { type: 'system', data: '[Connection closed]', time: new Date().toLocaleTimeString() }]);
      es.close();
      setSseRef(null);
    };
    setSseRef(es);
  };

  const handleSseDisconnect = () => {
    if (sseRef) {
      sseRef.close();
      setSseRef(null);
      setSseConnected(false);
      setSseEvents((prev) => [...prev, { type: 'system', data: '[Disconnected]', time: new Date().toLocaleTimeString() }]);
    }
  };

  const protocol = getSelectedApiProtocol();

  const protocolBadge = (p: string) => {
    switch (p) {
      case 'GRAPHQL': return 'bg-purple-100 text-purple-700 ring-purple-200';
      case 'SOAP': return 'bg-amber-100 text-amber-700 ring-amber-200';
      case 'WEBSOCKET': return 'bg-cyan-100 text-cyan-700 ring-cyan-200';
      case 'SSE': return 'bg-blue-100 text-blue-700 ring-blue-200';
      default: return 'bg-emerald-100 text-emerald-700 ring-emerald-200';
    }
  };

  if (loading) {
    return (
      <div className="animate-pulse">
        <div className="h-7 w-40 bg-slate-100 rounded-lg mb-2" />
        <div className="h-4 w-64 bg-slate-50 rounded mb-6" />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="space-y-4">
            <div className="bg-white rounded-xl border border-slate-200 p-5 h-40" />
            <div className="bg-white rounded-xl border border-slate-200 p-5 h-32" />
          </div>
          <div className="bg-white rounded-xl border border-slate-200 p-5 h-80" />
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-xl font-bold text-slate-900 mb-1">API Console</h1>
        <p className="text-sm text-slate-500">Test and debug API requests interactively</p>
      </div>

      {error && (
        <div className="mb-5 px-4 py-3 bg-red-50 border border-red-200 text-red-600 rounded-lg text-sm flex items-start gap-2">
          <svg className="w-4 h-4 mt-0.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
          </svg>
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left: Request Builder */}
        <div className="flex flex-col gap-4">
          {/* API Selection */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
            <div className="flex items-center gap-2 mb-4">
              <svg className="w-4 h-4 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 6.375c0 2.278-3.694 4.125-8.25 4.125S3.75 8.653 3.75 6.375m16.5 0c0-2.278-3.694-4.125-8.25-4.125S3.75 4.097 3.75 6.375m16.5 0v11.25c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125V6.375" />
              </svg>
              <h3 className="text-sm font-semibold text-slate-700">Select API</h3>
            </div>
            <select
              value={selectedApi}
              onChange={(e) => {
                setSelectedApi(e.target.value);
                setSelectedRoute('');
              }}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all mb-3"
            >
              <option value="">-- Select an API --</option>
              {apis.map((api) => (
                <option key={api.id} value={api.id}>
                  {api.name} ({api.version})
                </option>
              ))}
            </select>

            {selectedApi && (
              <div className="mb-3">
                <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ring-1 ring-inset ${protocolBadge(protocol)}`}>
                  {protocol}
                </span>
              </div>
            )}

            {protocol === 'REST' && routes.length > 0 && (
              <select
                value={selectedRoute}
                onChange={(e) => setSelectedRoute(e.target.value)}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
              >
                <option value="">-- Select Endpoint --</option>
                {routes.map((r, i) => (
                  <option key={i} value={i}>
                    {r.method} {r.path} - {r.description}
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Auth */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
            <div className="flex items-center gap-2 mb-4">
              <svg className="w-4 h-4 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z" />
              </svg>
              <h3 className="text-sm font-semibold text-slate-700">Authentication</h3>
            </div>
            <label className="block text-xs font-medium text-slate-500 mb-1.5">Application</label>
            <select
              value={selectedApp}
              onChange={(e) => {
                setSelectedApp(e.target.value);
                setSelectedKey('');
              }}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all mb-3"
            >
              <option value="">-- Select App --</option>
              {apps.map((a) => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>

            {apiKeys.length > 0 && (
              <>
                <label className="block text-xs font-medium text-slate-500 mb-1.5">API Key</label>
                <select
                  value={selectedKey}
                  onChange={(e) => setSelectedKey(e.target.value)}
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                >
                  <option value="">-- Select Key --</option>
                  {apiKeys.map((k) => (
                    <option key={k.id} value={k.prefix}>
                      {k.name} ({k.prefix}...)
                    </option>
                  ))}
                </select>
              </>
            )}
          </div>

          {/* Custom Headers */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
            <div className="flex items-center gap-2 mb-4">
              <svg className="w-4 h-4 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9.568 3H5.25A2.25 2.25 0 003 5.25v4.318c0 .597.237 1.17.659 1.591l9.581 9.581c.699.699 1.78.872 2.607.33a18.095 18.095 0 005.223-5.223c.542-.827.369-1.908-.33-2.607L11.16 3.66A2.25 2.25 0 009.568 3z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 6h.008v.008H6V6z" />
              </svg>
              <h3 className="text-sm font-semibold text-slate-700">Custom Headers</h3>
            </div>
            <textarea
              value={customHeaders}
              onChange={(e) => setCustomHeaders(e.target.value)}
              placeholder="Header-Name: value (one per line)"
              rows={3}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-xs font-mono text-slate-700 placeholder-slate-400 resize-y focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
            />
          </div>

          {/* Protocol-specific editors */}
          {protocol === 'GRAPHQL' && selectedApi && (
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
              <div className="flex items-center gap-2 mb-4">
                <div className="w-4 h-4 rounded bg-purple-500 flex items-center justify-center">
                  <span className="text-white text-[9px] font-bold">G</span>
                </div>
                <h3 className="text-sm font-semibold text-slate-700">GraphQL Query</h3>
              </div>
              <textarea
                value={graphqlQuery}
                onChange={(e) => setGraphqlQuery(e.target.value)}
                placeholder={'query {\n  users {\n    id\n    name\n  }\n}'}
                rows={8}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-xs font-mono text-slate-700 placeholder-slate-400 resize-y focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all mb-3"
              />
              <label className="block text-xs font-medium text-slate-500 mb-1.5">Variables (JSON, optional)</label>
              <textarea
                value={graphqlVariables}
                onChange={(e) => setGraphqlVariables(e.target.value)}
                placeholder='{"id": "123"}'
                rows={3}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-xs font-mono text-slate-700 placeholder-slate-400 resize-y focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
              />
            </div>
          )}

          {protocol === 'SOAP' && selectedApi && (
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
              <div className="flex items-center gap-2 mb-4">
                <div className="w-4 h-4 rounded bg-amber-500 flex items-center justify-center">
                  <span className="text-white text-[9px] font-bold">S</span>
                </div>
                <h3 className="text-sm font-semibold text-slate-700">SOAP Request</h3>
              </div>
              <div className="mb-3">
                <label className="block text-xs font-medium text-slate-500 mb-1.5">SOAPAction Header</label>
                <input
                  type="text"
                  value={soapAction}
                  onChange={(e) => setSoapAction(e.target.value)}
                  placeholder="urn:example:action"
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                />
              </div>
              <label className="block text-xs font-medium text-slate-500 mb-1.5">XML Body</label>
              <textarea
                value={soapBody}
                onChange={(e) => setSoapBody(e.target.value)}
                rows={10}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-xs font-mono text-slate-700 placeholder-slate-400 resize-y focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
              />
            </div>
          )}

          {protocol === 'WEBSOCKET' && selectedApi && (
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
              <div className="flex items-center gap-2 mb-4">
                <div className="w-4 h-4 rounded bg-cyan-500 flex items-center justify-center">
                  <span className="text-white text-[9px] font-bold">W</span>
                </div>
                <h3 className="text-sm font-semibold text-slate-700">WebSocket</h3>
              </div>
              <div className="flex gap-2 items-center mb-4">
                {!wsConnected ? (
                  <button
                    onClick={handleWsConnect}
                    className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-medium rounded-lg transition-colors"
                  >
                    Connect
                  </button>
                ) : (
                  <button
                    onClick={handleWsDisconnect}
                    className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm font-medium rounded-lg transition-colors"
                  >
                    Disconnect
                  </button>
                )}
                <div className="flex items-center gap-1.5">
                  <div className={`w-2 h-2 rounded-full ${wsConnected ? 'bg-emerald-500' : 'bg-slate-300'}`} />
                  <span className={`text-xs font-medium ${wsConnected ? 'text-emerald-600' : 'text-slate-400'}`}>
                    {wsConnected ? 'Connected' : 'Disconnected'}
                  </span>
                </div>
              </div>
              {wsConnected && (
                <div className="flex gap-2 mb-3">
                  <input
                    type="text"
                    value={wsSendText}
                    onChange={(e) => setWsSendText(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter') handleWsSend(); }}
                    placeholder="Message to send..."
                    className="flex-1 px-3 py-2 border border-slate-200 rounded-lg text-sm text-slate-700 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                  />
                  <button
                    onClick={handleWsSend}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
                  >
                    Send
                  </button>
                </div>
              )}
              <div className="bg-slate-900 rounded-lg p-3 max-h-72 overflow-auto font-mono text-xs">
                {wsMessages.length === 0 ? (
                  <span className="text-slate-500">Messages will appear here...</span>
                ) : (
                  wsMessages.map((msg, i) => (
                    <div key={i} className="mb-1 leading-relaxed">
                      <span className="text-slate-500">[{msg.time}] </span>
                      <span className={msg.direction === 'sent' ? 'text-blue-400' : 'text-emerald-400'}>
                        {msg.direction === 'sent' ? '\u25B6 ' : '\u25C0 '}
                      </span>
                      <span className="text-slate-200">{msg.data}</span>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {protocol === 'SSE' && selectedApi && (
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
              <div className="flex items-center gap-2 mb-4">
                <div className="w-4 h-4 rounded bg-blue-500 flex items-center justify-center">
                  <span className="text-white text-[9px] font-bold">E</span>
                </div>
                <h3 className="text-sm font-semibold text-slate-700">Server-Sent Events</h3>
              </div>
              <div className="flex gap-2 items-center mb-4">
                {!sseConnected ? (
                  <button
                    onClick={handleSseConnect}
                    className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-medium rounded-lg transition-colors"
                  >
                    Connect to Stream
                  </button>
                ) : (
                  <button
                    onClick={handleSseDisconnect}
                    className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm font-medium rounded-lg transition-colors"
                  >
                    Disconnect
                  </button>
                )}
                <div className="flex items-center gap-1.5">
                  <div className={`w-2 h-2 rounded-full ${sseConnected ? 'bg-emerald-500 animate-pulse' : 'bg-slate-300'}`} />
                  <span className={`text-xs font-medium ${sseConnected ? 'text-emerald-600' : 'text-slate-400'}`}>
                    {sseConnected ? 'Streaming...' : 'Disconnected'}
                  </span>
                </div>
              </div>
              <div className="bg-slate-900 rounded-lg p-3 max-h-72 overflow-auto font-mono text-xs">
                {sseEvents.length === 0 ? (
                  <span className="text-slate-500">Events will appear here...</span>
                ) : (
                  sseEvents.map((evt, i) => (
                    <div key={i} className="mb-1 leading-relaxed">
                      <span className="text-slate-500">[{evt.time}] </span>
                      <span className="text-amber-400">{evt.type}: </span>
                      <span className="text-slate-200">{evt.data}</span>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {/* REST Request Body */}
          {protocol === 'REST' && getSelectedRoute() && ['POST', 'PUT', 'PATCH'].includes(getSelectedRoute()!.method) && (
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
              <div className="flex items-center gap-2 mb-4">
                <span className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold ${methodColors[getSelectedRoute()!.method] || 'bg-slate-100 text-slate-700'}`}>
                  {getSelectedRoute()!.method}
                </span>
                <h3 className="text-sm font-semibold text-slate-700">Request Body</h3>
              </div>
              <textarea
                value={requestBody}
                onChange={(e) => setRequestBody(e.target.value)}
                placeholder='{"key": "value"}'
                rows={6}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-xs font-mono text-slate-700 placeholder-slate-400 resize-y focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
              />
            </div>
          )}

          {/* Execute Button */}
          {(protocol === 'REST' || protocol === 'GRAPHQL' || protocol === 'SOAP') && (
            <button
              onClick={
                protocol === 'GRAPHQL' ? handleGraphqlExecute :
                protocol === 'SOAP' ? handleSoapExecute :
                handleExecute
              }
              disabled={
                protocol === 'REST' ? (!selectedApi || selectedRoute === '' || executing) :
                (!selectedApi || executing)
              }
              className={`w-full py-3 px-6 rounded-xl text-sm font-semibold transition-all flex items-center justify-center gap-2 ${
                executing
                  ? 'bg-slate-300 text-slate-500 cursor-not-allowed'
                  : 'bg-blue-600 hover:bg-blue-700 text-white shadow-sm hover:shadow-md'
              } disabled:bg-slate-200 disabled:text-slate-400 disabled:cursor-not-allowed disabled:shadow-none`}
            >
              {executing ? (
                <>
                  <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  Executing...
                </>
              ) : (
                <>
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5.25 5.653c0-.856.917-1.398 1.667-.986l11.54 6.348a1.125 1.125 0 010 1.971l-11.54 6.347a1.125 1.125 0 01-1.667-.985V5.653z" />
                  </svg>
                  Send Request
                </>
              )}
            </button>
          )}
        </div>

        {/* Right: Response */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
          <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-700">Response</h3>
            {response && (
              <span
                className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-bold ${
                  response.status < 300 ? 'bg-emerald-100 text-emerald-700' :
                  response.status < 400 ? 'bg-amber-100 text-amber-700' :
                  'bg-red-100 text-red-700'
                }`}
              >
                {response.status} {response.statusText}
              </span>
            )}
          </div>

          <div className="flex-1 p-5 overflow-auto">
            {!response && !executing && (
              <div className="flex flex-col items-center justify-center text-center py-16">
                <div className="w-14 h-14 rounded-2xl bg-slate-100 flex items-center justify-center mb-4">
                  <svg className="w-7 h-7 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 7.5l3 2.25-3 2.25m4.5 0h3m-9 8.25h13.5A2.25 2.25 0 0021 18V6a2.25 2.25 0 00-2.25-2.25H5.25A2.25 2.25 0 003 6v12a2.25 2.25 0 002.25 2.25z" />
                  </svg>
                </div>
                <p className="text-sm text-slate-500 font-medium mb-1">No response yet</p>
                <p className="text-xs text-slate-400">Send a request to see the response here.</p>
              </div>
            )}

            {executing && (
              <div className="flex flex-col items-center justify-center text-center py-16">
                <svg className="w-8 h-8 animate-spin text-blue-500 mb-4" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                <p className="text-sm text-slate-500">Executing request...</p>
              </div>
            )}

            {response && (
              <div className="flex flex-col gap-4">
                {/* Tabs */}
                <div className="flex gap-0 border-b border-slate-200">
                  <button
                    onClick={() => setResponseTab('body')}
                    className={`px-4 py-2 text-xs font-medium border-b-2 transition-colors -mb-px ${
                      responseTab === 'body'
                        ? 'border-blue-600 text-blue-600'
                        : 'border-transparent text-slate-400 hover:text-slate-600'
                    }`}
                  >
                    Body
                  </button>
                  <button
                    onClick={() => setResponseTab('headers')}
                    className={`px-4 py-2 text-xs font-medium border-b-2 transition-colors -mb-px ${
                      responseTab === 'headers'
                        ? 'border-blue-600 text-blue-600'
                        : 'border-transparent text-slate-400 hover:text-slate-600'
                    }`}
                  >
                    Headers ({Object.keys(response.headers).length})
                  </button>
                </div>

                {responseTab === 'headers' && (
                  <div className="bg-slate-50 rounded-lg border border-slate-200 overflow-hidden">
                    <table className="w-full text-xs">
                      <tbody>
                        {Object.entries(response.headers).map(([k, v]) => (
                          <tr key={k} className="border-b border-slate-100 last:border-b-0">
                            <td className="px-3 py-2 font-mono font-semibold text-slate-600 whitespace-nowrap align-top">{k}</td>
                            <td className="px-3 py-2 font-mono text-slate-500 break-all">{v}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

                {responseTab === 'body' && (
                  <pre className="text-xs font-mono bg-slate-900 text-slate-200 p-4 rounded-lg overflow-auto max-h-[500px] whitespace-pre-wrap break-words leading-relaxed">
                    {formatJson(response.body)}
                  </pre>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
