'use client';

import React, { useEffect, useState } from 'react';

const MANAGEMENT_URL = process.env.NEXT_PUBLIC_MANAGEMENT_URL || 'http://localhost:8082';

function publicFetch<T>(path: string): Promise<T> {
  return fetch(`${MANAGEMENT_URL}${path}`).then(async (res) => {
    if (!res.ok) {
      const body = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(body.message || `Request failed (${res.status})`);
    }
    return res.json();
  });
}

interface ServiceStatus {
  name: string;
  status: 'OPERATIONAL' | 'DEGRADED' | 'PARTIAL_OUTAGE' | 'MAJOR_OUTAGE';
  uptimePercentage: number;
}

interface Incident {
  id: string;
  title: string;
  status: string;
  severity: string;
  createdAt: string;
  updatedAt: string;
  message: string;
}

interface MaintenanceWindow {
  id: string;
  title: string;
  scheduledStart: string;
  scheduledEnd: string;
  status: string;
  description: string;
}

const statusColors: Record<string, string> = {
  OPERATIONAL: '#22c55e',
  DEGRADED: '#eab308',
  PARTIAL_OUTAGE: '#f97316',
  MAJOR_OUTAGE: '#ef4444',
};

const statusLabels: Record<string, string> = {
  OPERATIONAL: 'Operational',
  DEGRADED: 'Degraded',
  PARTIAL_OUTAGE: 'Partial Outage',
  MAJOR_OUTAGE: 'Major Outage',
};

export default function StatusPage() {
  const [services, setServices] = useState<ServiceStatus[]>([]);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [maintenance, setMaintenance] = useState<MaintenanceWindow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function fetchAll() {
      try {
        const [svc, inc, maint] = await Promise.all([
          publicFetch<ServiceStatus[]>('/v1/status/services'),
          publicFetch<Incident[]>('/v1/status/incidents'),
          publicFetch<MaintenanceWindow[]>('/v1/status/maintenance'),
        ]);
        setServices(Array.isArray(svc) ? svc : []);
        setIncidents(Array.isArray(inc) ? inc : []);
        setMaintenance(Array.isArray(maint) ? maint : []);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Failed to load status');
      } finally {
        setLoading(false);
      }
    }
    fetchAll();
  }, []);

  const allOperational = services.length > 0 && services.every((s) => s.status === 'OPERATIONAL');

  const sectionStyle: React.CSSProperties = {
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e2e8f0',
    padding: 24,
    marginBottom: 24,
  };

  if (loading) {
    return (
      <div style={{ maxWidth: 800, margin: '0 auto', padding: '40px 24px' }}>
        <p style={{ textAlign: 'center', color: '#64748b' }}>Loading status...</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 800, margin: '0 auto', padding: '40px 24px' }}>
      <h1 style={{ fontSize: 28, fontWeight: 700, color: '#0f172a', marginBottom: 8, textAlign: 'center' }}>
        System Status
      </h1>
      <p style={{ textAlign: 'center', color: '#64748b', fontSize: 15, marginBottom: 32 }}>
        Current status of Waterwall API Gateway services
      </p>

      {error && <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 16, textAlign: 'center' }}>{error}</p>}

      {/* Overall Status Banner */}
      <div
        style={{
          ...sectionStyle,
          textAlign: 'center',
          backgroundColor: allOperational ? '#f0fdf4' : '#fef2f2',
          borderColor: allOperational ? '#bbf7d0' : '#fecaca',
        }}
      >
        <span
          style={{
            display: 'inline-block',
            width: 12,
            height: 12,
            borderRadius: '50%',
            backgroundColor: allOperational ? '#22c55e' : '#ef4444',
            marginRight: 10,
            verticalAlign: 'middle',
          }}
        />
        <span style={{ fontSize: 18, fontWeight: 600, color: allOperational ? '#166534' : '#991b1b' }}>
          {allOperational ? 'All Systems Operational' : 'Some Systems Are Experiencing Issues'}
        </span>
      </div>

      {/* Services */}
      <div style={sectionStyle}>
        <h2 style={{ fontSize: 18, fontWeight: 600, color: '#0f172a', marginTop: 0, marginBottom: 16 }}>Services</h2>
        {services.length === 0 ? (
          <p style={{ color: '#64748b', fontSize: 14, margin: 0 }}>No service data available.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
            {services.map((svc, i) => (
              <div
                key={svc.name}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '14px 0',
                  borderTop: i > 0 ? '1px solid #f1f5f9' : 'none',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span
                    style={{
                      display: 'inline-block',
                      width: 10,
                      height: 10,
                      borderRadius: '50%',
                      backgroundColor: statusColors[svc.status] || '#94a3b8',
                      flexShrink: 0,
                    }}
                  />
                  <span style={{ fontSize: 15, fontWeight: 500, color: '#0f172a' }}>{svc.name}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                  <span style={{ fontSize: 13, color: '#64748b' }}>
                    {svc.uptimePercentage.toFixed(2)}% uptime
                  </span>
                  <span
                    style={{
                      padding: '2px 10px',
                      borderRadius: 9999,
                      fontSize: 12,
                      fontWeight: 600,
                      backgroundColor:
                        svc.status === 'OPERATIONAL'
                          ? '#dcfce7'
                          : svc.status === 'DEGRADED'
                          ? '#fef3c7'
                          : svc.status === 'PARTIAL_OUTAGE'
                          ? '#ffedd5'
                          : '#fee2e2',
                      color:
                        svc.status === 'OPERATIONAL'
                          ? '#166534'
                          : svc.status === 'DEGRADED'
                          ? '#92400e'
                          : svc.status === 'PARTIAL_OUTAGE'
                          ? '#9a3412'
                          : '#991b1b',
                    }}
                  >
                    {statusLabels[svc.status] || svc.status}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Current Incidents */}
      <div style={sectionStyle}>
        <h2 style={{ fontSize: 18, fontWeight: 600, color: '#0f172a', marginTop: 0, marginBottom: 16 }}>
          Current Incidents
        </h2>
        {incidents.length === 0 ? (
          <p style={{ color: '#64748b', fontSize: 14, margin: 0 }}>No active incidents.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {incidents.map((inc) => (
              <div
                key={inc.id}
                style={{
                  padding: 14,
                  backgroundColor: '#fef2f2',
                  border: '1px solid #fecaca',
                  borderRadius: 6,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                  <span style={{ fontSize: 14, fontWeight: 600, color: '#991b1b' }}>{inc.title}</span>
                  <span
                    style={{
                      padding: '1px 8px',
                      borderRadius: 9999,
                      fontSize: 11,
                      fontWeight: 600,
                      backgroundColor: '#fee2e2',
                      color: '#991b1b',
                      textTransform: 'uppercase',
                    }}
                  >
                    {inc.severity}
                  </span>
                </div>
                <p style={{ fontSize: 13, color: '#475569', margin: '0 0 4px 0', lineHeight: 1.6 }}>{inc.message}</p>
                <span style={{ fontSize: 12, color: '#94a3b8' }}>
                  Updated {new Date(inc.updatedAt).toLocaleString()}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Scheduled Maintenance */}
      <div style={sectionStyle}>
        <h2 style={{ fontSize: 18, fontWeight: 600, color: '#0f172a', marginTop: 0, marginBottom: 16 }}>
          Scheduled Maintenance
        </h2>
        {maintenance.length === 0 ? (
          <p style={{ color: '#64748b', fontSize: 14, margin: 0 }}>No upcoming maintenance windows.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {maintenance.map((m) => (
              <div
                key={m.id}
                style={{
                  padding: 14,
                  backgroundColor: '#eff6ff',
                  border: '1px solid #bfdbfe',
                  borderRadius: 6,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                  <span style={{ fontSize: 14, fontWeight: 600, color: '#1e40af' }}>{m.title}</span>
                  <span
                    style={{
                      padding: '1px 8px',
                      borderRadius: 9999,
                      fontSize: 11,
                      fontWeight: 600,
                      backgroundColor: '#dbeafe',
                      color: '#1e40af',
                      textTransform: 'uppercase',
                    }}
                  >
                    {m.status}
                  </span>
                </div>
                <p style={{ fontSize: 13, color: '#475569', margin: '0 0 6px 0', lineHeight: 1.6 }}>
                  {m.description}
                </p>
                <div style={{ fontSize: 12, color: '#64748b' }}>
                  <span>{new Date(m.scheduledStart).toLocaleString()}</span>
                  <span style={{ margin: '0 6px' }}>&mdash;</span>
                  <span>{new Date(m.scheduledEnd).toLocaleString()}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Footer */}
      <div style={{ textAlign: 'center', color: '#94a3b8', fontSize: 13, paddingTop: 16 }}>
        Last 30 days uptime &bull; Updated every 60 seconds
      </div>
    </div>
  );
}
