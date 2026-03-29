'use client';

import React, { useEffect, useState, useCallback } from 'react';

const ANALYTICS_URL = process.env.NEXT_PUBLIC_ANALYTICS_URL || 'http://localhost:8083';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface SlaDashboardItem {
  apiId: string;
  apiName: string;
  uptimeTarget: number;
  uptimeActual: number;
  uptimeCompliant: boolean;
  latencyTargetMs: number;
  latencyP95Actual: number;
  latencyCompliant: boolean;
  errorBudgetPct: number;
  errorRateActual: number;
  errorRateCompliant: boolean;
  overallCompliant: boolean;
  activeBreaches: number;
}

interface SlaBreach {
  id: string;
  apiId?: string;
  apiName: string;
  slaConfigId: string;
  metric: string;
  targetValue: number;
  actualValue: number;
  message: string;
  breachedAt: string;
  resolvedAt: string | null;
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token')
    || localStorage.getItem('admin_token')
    || localStorage.getItem('jwt_token');
}

function authHeaders(): Record<string, string> {
  const t = getToken();
  return t
    ? { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' }
    : { 'Content-Type': 'application/json' };
}

function fetchApi<T>(path: string): Promise<T> {
  return fetch(`${ANALYTICS_URL}${path}`, { headers: authHeaders() }).then((r) => {
    if (!r.ok) throw new Error(`Request failed (${r.status})`);
    return r.json();
  });
}

function complianceColor(value: number): string {
  if (value >= 99) return '#10b981';
  if (value >= 95) return '#f59e0b';
  return '#ef4444';
}

/* ------------------------------------------------------------------ */
/*  Compliance Ring (SVG)                                              */
/* ------------------------------------------------------------------ */

function ComplianceRing({ value, size = 56 }: { value: number; size?: number }) {
  const radius = (size - 8) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (value / 100) * circumference;
  const color = complianceColor(value);

  return (
    <svg width={size} height={size} style={{ flexShrink: 0 }}>
      <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="#e2e8f0" strokeWidth={4} />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={color}
        strokeWidth={4}
        strokeDasharray={circumference}
        strokeDashoffset={offset}
        strokeLinecap="round"
        transform={`rotate(-90 ${size / 2} ${size / 2})`}
        style={{ transition: 'stroke-dashoffset 0.5s' }}
      />
      <text
        x="50%"
        y="50%"
        textAnchor="middle"
        dominantBaseline="central"
        style={{ fontSize: 11, fontWeight: 700, fill: '#0f172a' }}
      >
        {value.toFixed(1)}%
      </text>
    </svg>
  );
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function SlaPage() {
  const [dashboard, setDashboard] = useState<SlaDashboardItem[]>([]);
  const [dashboardLoading, setDashboardLoading] = useState(true);
  const [dashboardError, setDashboardError] = useState('');

  const [breaches, setBreaches] = useState<SlaBreach[]>([]);
  const [breachesLoading, setBreachesLoading] = useState(true);
  const [breachesError, setBreachesError] = useState('');

  const [breachRange, setBreachRange] = useState<string>('24h');

  const loadDashboard = useCallback(() => {
    setDashboardLoading(true);
    setDashboardError('');
    fetchApi<SlaDashboardItem[]>('/v1/sla/dashboard')
      .then((data) => setDashboard(Array.isArray(data) ? data : []))
      .catch((err) => setDashboardError(err.message || 'Failed to load SLA dashboard'))
      .finally(() => setDashboardLoading(false));
  }, []);

  const loadBreaches = useCallback((range: string) => {
    setBreachesLoading(true);
    setBreachesError('');
    fetchApi<SlaBreach[]>(`/v1/sla/breaches?range=${range}`)
      .then((data) => setBreaches(Array.isArray(data) ? data : []))
      .catch((err) => setBreachesError(err.message || 'Failed to load breach history'))
      .finally(() => setBreachesLoading(false));
  }, []);

  useEffect(() => {
    loadDashboard();
    loadBreaches(breachRange);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleRangeChange = (range: string) => {
    setBreachRange(range);
    loadBreaches(range);
  };

  const sectionStyle: React.CSSProperties = {
    backgroundColor: '#fff',
    borderRadius: 12,
    border: '1px solid #e2e8f0',
    padding: 24,
    marginBottom: 24,
  };

  const metricLabelStyle: React.CSSProperties = {
    fontSize: 12,
    color: '#64748b',
    marginBottom: 2,
  };

  const rangeButtonStyle = (active: boolean): React.CSSProperties => ({
    padding: '4px 12px',
    fontSize: 13,
    fontWeight: active ? 600 : 400,
    color: active ? '#7c3aed' : '#64748b',
    backgroundColor: active ? '#ede9fe' : 'transparent',
    border: '1px solid',
    borderColor: active ? '#c4b5fd' : '#e2e8f0',
    borderRadius: 6,
    cursor: 'pointer',
    transition: 'all 0.15s',
  });

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto' }}>
      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#0f172a', marginBottom: 4 }}>
          SLA Compliance
        </h1>
        <p style={{ fontSize: 14, color: '#64748b', margin: 0 }}>
          Service level agreement status and breach history for your APIs
        </p>
      </div>

      {/* Dashboard */}
      <div style={sectionStyle}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, color: '#0f172a', margin: 0 }}>
            Compliance Dashboard
          </h2>
          <button
            onClick={loadDashboard}
            style={{
              padding: '4px 12px',
              fontSize: 13,
              color: '#64748b',
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              borderRadius: 6,
              cursor: 'pointer',
            }}
          >
            Refresh
          </button>
        </div>

        {dashboardError && (
          <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{dashboardError}</p>
        )}

        {dashboardLoading ? (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
            {[1, 2, 3].map((i) => (
              <div key={i} style={{ ...sectionStyle, marginBottom: 0, height: 120 }}>
                <div style={{ width: 120, height: 14, backgroundColor: '#f1f5f9', borderRadius: 4, marginBottom: 12 }} />
                <div style={{ width: 56, height: 56, backgroundColor: '#f1f5f9', borderRadius: '50%' }} />
              </div>
            ))}
          </div>
        ) : dashboard.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#94a3b8', fontSize: 14 }}>
            No SLA data available. SLA configurations are managed by your administrator.
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
            {dashboard.map((item) => (
              <div
                key={item.apiId}
                style={{
                  backgroundColor: '#fff',
                  borderRadius: 10,
                  border: '1px solid #e2e8f0',
                  padding: 20,
                  transition: 'box-shadow 0.15s',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 16 }}>
                  <div>
                    <p style={{ fontSize: 14, fontWeight: 600, color: '#0f172a', margin: 0 }}>{item.apiName}</p>
                    <p style={{ fontSize: 12, color: '#94a3b8', margin: '2px 0 0' }}>{item.apiId}</p>
                  </div>
                  <ComplianceRing value={item.uptimeActual ?? 100} />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12, textAlign: 'center' }}>
                  <div>
                    <p style={metricLabelStyle}>Uptime</p>
                    <p style={{ fontSize: 14, fontWeight: 700, color: item.uptimeCompliant ? '#10b981' : '#ef4444', margin: 0 }}>
                      {(item.uptimeActual ?? 0).toFixed(2)}%
                    </p>
                    <p style={{ fontSize: 11, color: '#94a3b8', margin: '2px 0 0' }}>
                      Target: {(item.uptimeTarget ?? 0).toFixed(1)}%
                    </p>
                  </div>
                  <div>
                    <p style={metricLabelStyle}>Latency P95</p>
                    <p style={{ fontSize: 14, fontWeight: 700, color: item.latencyCompliant ? '#10b981' : '#ef4444', margin: 0 }}>
                      {(item.latencyP95Actual ?? 0).toFixed(0)}ms
                    </p>
                    <p style={{ fontSize: 11, color: '#94a3b8', margin: '2px 0 0' }}>
                      Target: {item.latencyTargetMs ?? 0}ms
                    </p>
                  </div>
                  <div>
                    <p style={metricLabelStyle}>Error Rate</p>
                    <p style={{ fontSize: 14, fontWeight: 700, color: item.errorRateCompliant ? '#10b981' : '#ef4444', margin: 0 }}>
                      {(item.errorRateActual ?? 0).toFixed(2)}%
                    </p>
                    <p style={{ fontSize: 11, color: '#94a3b8', margin: '2px 0 0' }}>
                      Budget: {(item.errorBudgetPct ?? 0).toFixed(1)}%
                    </p>
                  </div>
                </div>
                {item.activeBreaches > 0 && (
                  <div style={{
                    marginTop: 12,
                    padding: '6px 10px',
                    backgroundColor: '#fef2f2',
                    borderRadius: 6,
                    fontSize: 12,
                    color: '#991b1b',
                    fontWeight: 500,
                    textAlign: 'center',
                  }}>
                    {item.activeBreaches} active breach{item.activeBreaches > 1 ? 'es' : ''}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Breach History */}
      <div style={sectionStyle}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, color: '#0f172a', margin: 0 }}>
            Breach History
          </h2>
          <div style={{ display: 'flex', gap: 6 }}>
            {(['24h', '7d', '30d'] as const).map((range) => (
              <button
                key={range}
                onClick={() => handleRangeChange(range)}
                style={rangeButtonStyle(breachRange === range)}
              >
                {range === '24h' ? '24 Hours' : range === '7d' ? '7 Days' : '30 Days'}
              </button>
            ))}
          </div>
        </div>

        {breachesError && (
          <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{breachesError}</p>
        )}

        {breachesLoading ? (
          <div style={{ padding: '24px 0' }}>
            {[1, 2, 3].map((i) => (
              <div key={i} style={{ height: 40, backgroundColor: '#f8fafc', borderRadius: 6, marginBottom: 8 }} />
            ))}
          </div>
        ) : breaches.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#94a3b8', fontSize: 14 }}>
            No breaches found in the selected time range.
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #e2e8f0' }}>
                  {['API', 'Metric', 'Target', 'Actual', 'When'].map((h) => (
                    <th
                      key={h}
                      style={{
                        padding: '10px 14px',
                        textAlign: 'left',
                        fontSize: 12,
                        fontWeight: 600,
                        color: '#64748b',
                        textTransform: 'uppercase',
                        letterSpacing: '0.03em',
                      }}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {breaches.map((b) => (
                  <tr key={b.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '10px 14px', color: '#0f172a', fontWeight: 500 }}>{b.apiName || b.apiId}</td>
                    <td style={{ padding: '10px 14px' }}>
                      <span
                        style={{
                          display: 'inline-block',
                          padding: '2px 8px',
                          borderRadius: 4,
                          fontSize: 12,
                          fontWeight: 600,
                          backgroundColor: '#fee2e2',
                          color: '#991b1b',
                        }}
                      >
                        {b.metric}
                      </span>
                    </td>
                    <td style={{ padding: '10px 14px', color: '#475569' }}>{b.targetValue}</td>
                    <td style={{ padding: '10px 14px', color: '#dc2626', fontWeight: 600 }}>{b.actualValue}</td>
                    <td style={{ padding: '10px 14px', color: '#64748b', whiteSpace: 'nowrap' }}>
                      {new Date(b.breachedAt).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
