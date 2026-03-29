'use client';

import React, { useEffect, useRef, useState } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

function getToken(): string {
  if (typeof window === 'undefined') return '';
  return localStorage.getItem('token') || localStorage.getItem('admin_token') || '';
}

export default function DocsPage() {
  const containerRef = useRef<HTMLDivElement>(null);
  const initialized = useRef(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;

    async function loadDocs() {
      try {
        // Fetch spec server-side to avoid CORS
        const headers: Record<string, string> = { 'Content-Type': 'application/json' };
        const token = getToken();
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const res = await fetch(`${API_BASE}/v3/api-docs`, { headers });
        if (!res.ok) throw new Error(`Failed to load API spec (${res.status})`);
        const spec = await res.json();

        // Load Swagger UI CSS
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'https://unpkg.com/swagger-ui-dist@5.18.2/swagger-ui.css';
        document.head.appendChild(link);

        // Load Swagger UI bundle
        const script = document.createElement('script');
        script.src = 'https://unpkg.com/swagger-ui-dist@5.18.2/swagger-ui-bundle.js';
        script.onload = () => {
          const win = window as unknown as Record<string, unknown>;
          if (containerRef.current && win.SwaggerUIBundle) {
            const SwaggerUIBundle = win.SwaggerUIBundle as (config: Record<string, unknown>) => void;
            SwaggerUIBundle({
              spec,
              domNode: containerRef.current,
              deepLinking: true,
              defaultModelsExpandDepth: 1,
              docExpansion: 'list',
              filter: true,
              showExtensions: true,
              showCommonExtensions: true,
              tryItOutEnabled: true,
            });
          }
          setLoading(false);
        };
        script.onerror = () => {
          setError('Failed to load Swagger UI library');
          setLoading(false);
        };
        document.body.appendChild(script);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load API documentation');
        setLoading(false);
      }
    }

    loadDocs();
  }, []);

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>
          API Documentation
        </h1>
        <p style={{ fontSize: 14, color: '#64748b', margin: 0 }}>
          Browse API endpoints, request/response schemas, and authentication details.
          Use &ldquo;Try it out&rdquo; to test endpoints directly.
        </p>
      </div>

      {error && (
        <div style={{
          padding: '14px 18px', backgroundColor: '#fef2f2', border: '1px solid #fecaca',
          borderRadius: 10, color: '#dc2626', fontSize: 14, marginBottom: 20,
          display: 'flex', alignItems: 'center', gap: 10,
        }}>
          <svg width="18" height="18" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
          </svg>
          {error}
        </div>
      )}

      {loading && !error && (
        <div style={{
          backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0',
          padding: '60px 24px', textAlign: 'center',
        }}>
          <div style={{
            width: 36, height: 36, border: '3px solid #e2e8f0', borderTopColor: '#3b82f6',
            borderRadius: '50%', margin: '0 auto 16px',
            animation: 'spin 0.6s linear infinite',
          }} />
          <p style={{ fontSize: 14, color: '#94a3b8', margin: 0 }}>Loading API documentation...</p>
          <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
        </div>
      )}

      <div
        ref={containerRef}
        style={{
          backgroundColor: '#fff',
          borderRadius: 10,
          border: '1px solid #e2e8f0',
          padding: '16px 24px',
          minHeight: loading ? 0 : 400,
          display: loading ? 'none' : 'block',
        }}
      />
    </div>
  );
}
