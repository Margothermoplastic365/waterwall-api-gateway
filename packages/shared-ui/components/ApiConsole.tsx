'use client';

import React, { useState, useCallback } from 'react';

export interface EndpointDef {
  method: string;
  path: string;
  description: string;
}

export interface ApiConsoleProps {
  apiBaseUrl: string;
  endpoints: EndpointDef[];
  authToken?: string;
}

interface KeyValue {
  key: string;
  value: string;
}

interface ApiResponse {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body: string;
}

const METHOD_COLORS: Record<string, string> = {
  GET: 'bg-green-100 text-green-800',
  POST: 'bg-blue-100 text-blue-800',
  PUT: 'bg-yellow-100 text-yellow-800',
  PATCH: 'bg-orange-100 text-orange-800',
  DELETE: 'bg-red-100 text-red-800',
};

export default function ApiConsole({ apiBaseUrl, endpoints, authToken }: ApiConsoleProps) {
  const [selectedIdx, setSelectedIdx] = useState(0);
  const [url, setUrl] = useState(endpoints[0]?.path ?? '');
  const [method, setMethod] = useState(endpoints[0]?.method ?? 'GET');
  const [headers, setHeaders] = useState<KeyValue[]>([{ key: '', value: '' }]);
  const [body, setBody] = useState('');
  const [response, setResponse] = useState<ApiResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleEndpointChange = (idx: number) => {
    setSelectedIdx(idx);
    const ep = endpoints[idx];
    if (ep) {
      setUrl(ep.path);
      setMethod(ep.method);
    }
    setResponse(null);
    setError(null);
  };

  const updateHeader = (index: number, field: 'key' | 'value', val: string) => {
    setHeaders((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: val };
      return next;
    });
  };

  const addHeader = () => setHeaders((prev) => [...prev, { key: '', value: '' }]);

  const removeHeader = (index: number) =>
    setHeaders((prev) => prev.filter((_, i) => i !== index));

  const send = useCallback(async () => {
    setLoading(true);
    setError(null);
    setResponse(null);
    try {
      const reqHeaders: Record<string, string> = {
        'Content-Type': 'application/json',
      };
      if (authToken) {
        reqHeaders['Authorization'] = `Bearer ${authToken}`;
      }
      headers.forEach((h) => {
        if (h.key.trim()) reqHeaders[h.key.trim()] = h.value;
      });

      const fullUrl = `${apiBaseUrl}${url.startsWith('/') ? url : `/${url}`}`;
      const init: RequestInit = {
        method,
        headers: reqHeaders,
      };
      if (body.trim() && method !== 'GET' && method !== 'HEAD') {
        init.body = body;
      }

      const res = await fetch(fullUrl, init);
      const resHeaders: Record<string, string> = {};
      res.headers.forEach((v, k) => {
        resHeaders[k] = v;
      });

      let resBody: string;
      const contentType = res.headers.get('content-type') ?? '';
      if (contentType.includes('application/json')) {
        const json = await res.json();
        resBody = JSON.stringify(json, null, 2);
      } else {
        resBody = await res.text();
      }

      setResponse({
        status: res.status,
        statusText: res.statusText,
        headers: resHeaders,
        body: resBody,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Request failed');
    } finally {
      setLoading(false);
    }
  }, [apiBaseUrl, url, method, headers, body, authToken]);

  const methodColor = METHOD_COLORS[method.toUpperCase()] ?? 'bg-gray-100 text-gray-800';

  return (
    <div className="w-full space-y-4 p-4 bg-white border border-gray-200 rounded-lg">
      {/* Endpoint selector */}
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Endpoint</label>
        <select
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          value={selectedIdx}
          onChange={(e) => handleEndpointChange(Number(e.target.value))}
        >
          {endpoints.map((ep, i) => (
            <option key={i} value={i}>
              {ep.method} {ep.path} &mdash; {ep.description}
            </option>
          ))}
        </select>
      </div>

      {/* Method + URL */}
      <div className="flex gap-2">
        <span className={`inline-flex items-center px-3 py-2 text-sm font-bold rounded-md ${methodColor}`}>
          {method}
        </span>
        <input
          className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="/api/v1/..."
        />
        <button
          className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          onClick={send}
          disabled={loading}
        >
          {loading ? 'Sending...' : 'Send'}
        </button>
      </div>

      {/* Headers editor */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-sm font-medium text-gray-700">Headers</label>
          <button
            className="text-xs text-blue-600 hover:text-blue-800"
            onClick={addHeader}
          >
            + Add Header
          </button>
        </div>
        <div className="space-y-1">
          {headers.map((h, i) => (
            <div key={i} className="flex gap-2">
              <input
                className="flex-1 px-2 py-1 border border-gray-300 rounded text-sm font-mono focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Header name"
                value={h.key}
                onChange={(e) => updateHeader(i, 'key', e.target.value)}
              />
              <input
                className="flex-1 px-2 py-1 border border-gray-300 rounded text-sm font-mono focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Value"
                value={h.value}
                onChange={(e) => updateHeader(i, 'value', e.target.value)}
              />
              <button
                className="px-2 text-gray-400 hover:text-red-500 text-sm"
                onClick={() => removeHeader(i)}
              >
                &times;
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Body editor */}
      {method !== 'GET' && method !== 'HEAD' && (
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Body (JSON)</label>
          <textarea
            className="w-full h-32 px-3 py-2 border border-gray-300 rounded-md text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder='{"key": "value"}'
          />
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="px-4 py-3 text-sm text-red-700 bg-red-50 border border-red-200 rounded-md">
          {error}
        </div>
      )}

      {/* Response panel */}
      {response && (
        <div className="border border-gray-200 rounded-md overflow-hidden">
          <div className="flex items-center gap-3 px-4 py-2 bg-gray-50 border-b border-gray-200">
            <span
              className={`text-sm font-bold ${
                response.status < 300
                  ? 'text-green-600'
                  : response.status < 400
                    ? 'text-yellow-600'
                    : 'text-red-600'
              }`}
            >
              {response.status} {response.statusText}
            </span>
          </div>
          <details className="border-b border-gray-200">
            <summary className="px-4 py-2 text-sm font-medium text-gray-600 cursor-pointer hover:bg-gray-50">
              Response Headers
            </summary>
            <div className="px-4 py-2 text-xs font-mono text-gray-600 bg-gray-50">
              {Object.entries(response.headers).map(([k, v]) => (
                <div key={k}>
                  <span className="text-gray-800">{k}:</span> {v}
                </div>
              ))}
            </div>
          </details>
          <pre className="px-4 py-3 text-sm font-mono text-gray-800 bg-white overflow-x-auto max-h-96 overflow-y-auto">
            {response.body}
          </pre>
        </div>
      )}
    </div>
  );
}
