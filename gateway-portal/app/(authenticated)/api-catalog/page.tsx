'use client';

import { useEffect, useState, useMemo } from 'react';

const MGMT_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token') || localStorage.getItem('admin_token') || localStorage.getItem('jwt_token');
}

interface ApiDefinition {
  id: string;
  name: string;
  version: string;
  description: string;
  category: string;
  status: string;
  visibility: string;
  protocolType: string;
  tags: string[];
  orgId: string;
  sensitivity?: string;
  versionStatus?: string;
  apiGroupName?: string;
  deprecatedMessage?: string;
}

const STATUS_COLORS: Record<string, { bg: string; color: string }> = {
  PUBLISHED: { bg: '#dcfce7', color: '#16a34a' },
  DRAFT: { bg: '#fef3c7', color: '#d97706' },
  DEPRECATED: { bg: '#fef2f2', color: '#dc2626' },
};

const PROTOCOL_COLORS: Record<string, { bg: string; color: string }> = {
  REST: { bg: '#dcfce7', color: '#16a34a' },
  GRAPHQL: { bg: '#f3e8ff', color: '#7c3aed' },
  GRPC: { bg: '#fef3c7', color: '#d97706' },
  WEBSOCKET: { bg: '#e0f2fe', color: '#0284c7' },
  SOAP: { bg: '#ffedd5', color: '#ea580c' },
};

export default function AuthenticatedCatalogPage() {
  const [apis, setApis] = useState<ApiDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');

  useEffect(() => {
    async function load() {
      try {
        const token = getToken();
        const headers: Record<string, string> = { 'Content-Type': 'application/json' };
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const res = await fetch(`${MGMT_URL}/v1/apis?page=0&size=100&status=PUBLISHED`, { headers });
        if (res.ok) {
          const data = await res.json();
          setApis(data.content || []);
        }
      } catch {
        // silent
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const categories = useMemo(() => {
    const cats = new Set(apis.map((a) => a.category).filter(Boolean));
    return ['All', ...Array.from(cats).sort()];
  }, [apis]);

  const filtered = useMemo(() => {
    return apis.filter((api) => {
      const matchesSearch =
        !search ||
        api.name.toLowerCase().includes(search.toLowerCase()) ||
        (api.description || '').toLowerCase().includes(search.toLowerCase()) ||
        (api.tags || []).some((t) => t.toLowerCase().includes(search.toLowerCase()));
      const matchesCategory =
        selectedCategory === 'All' || api.category === selectedCategory;
      return matchesSearch && matchesCategory;
    });
  }, [apis, search, selectedCategory]);

  return (
    <div>
      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>API Catalog</h1>
        <p style={{ fontSize: 14, color: '#64748b', margin: 0 }}>
          Browse all available APIs, subscribe, and start integrating
        </p>
      </div>

      {/* Search & Filters */}
      <div style={{
        backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0',
        padding: 16, marginBottom: 20, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center',
      }}>
        <div style={{ position: 'relative', flex: '1 1 300px', maxWidth: 400 }}>
          <span style={{
            position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)',
            color: '#94a3b8', fontSize: 15, pointerEvents: 'none',
          }}>
            &#128269;
          </span>
          <input
            type="text"
            placeholder="Search by name, description, or tag..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{
              width: '100%', padding: '9px 12px 9px 36px', borderRadius: 8,
              border: '1px solid #e2e8f0', fontSize: 14, outline: 'none', backgroundColor: '#fff',
            }}
          />
        </div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {categories.map((cat) => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              style={{
                padding: '5px 14px', borderRadius: 20, border: 'none', fontSize: 13, fontWeight: 500,
                cursor: 'pointer', transition: 'all 0.15s',
                backgroundColor: cat === selectedCategory ? '#3b82f6' : '#f1f5f9',
                color: cat === selectedCategory ? '#fff' : '#475569',
              }}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {/* Count */}
      <p style={{ fontSize: 13, color: '#64748b', marginBottom: 16 }}>
        {loading ? 'Loading...' : `${filtered.length} API${filtered.length !== 1 ? 's' : ''} available`}
      </p>

      {/* Loading */}
      {loading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div key={i} style={{
              backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: 20, height: 180,
            }}>
              <div style={{ height: 20, width: 80, backgroundColor: '#f1f5f9', borderRadius: 4, marginBottom: 14 }} />
              <div style={{ height: 16, width: '60%', backgroundColor: '#f1f5f9', borderRadius: 4, marginBottom: 8 }} />
              <div style={{ height: 13, width: '100%', backgroundColor: '#f8fafc', borderRadius: 4, marginBottom: 6 }} />
              <div style={{ height: 13, width: '80%', backgroundColor: '#f8fafc', borderRadius: 4 }} />
            </div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        /* Empty */
        <div style={{
          backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0',
          padding: 64, textAlign: 'center',
        }}>
          <div style={{ fontSize: 48, marginBottom: 12, color: '#cbd5e1' }}>&#128270;</div>
          <p style={{ fontSize: 16, color: '#475569', marginBottom: 4 }}>
            {apis.length === 0 ? 'No APIs available yet' : 'No APIs match your search'}
          </p>
          <p style={{ fontSize: 13, color: '#94a3b8' }}>
            {apis.length === 0
              ? 'APIs will appear here once they are published.'
              : 'Try adjusting your search or category filter.'}
          </p>
        </div>
      ) : (
        /* API Grid */
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
          {filtered.map((api) => {
            const st = STATUS_COLORS[api.status] || { bg: '#f1f5f9', color: '#64748b' };
            const pt = PROTOCOL_COLORS[api.protocolType] || PROTOCOL_COLORS.REST;
            return (
              <a
                key={api.id}
                href={`/api-catalog/${api.id}`}
                style={{
                  display: 'flex', flexDirection: 'column', textDecoration: 'none', color: 'inherit',
                  backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0',
                  padding: 20, transition: 'border-color 0.15s, box-shadow 0.15s',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.borderColor = '#93c5fd'; e.currentTarget.style.boxShadow = '0 4px 12px rgba(59,130,246,0.08)'; }}
                onMouseLeave={(e) => { e.currentTarget.style.borderColor = '#e2e8f0'; e.currentTarget.style.boxShadow = 'none'; }}
              >
                {/* Top row: badges */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <span style={{
                    fontSize: 11, fontWeight: 600, padding: '3px 10px', borderRadius: 12,
                    backgroundColor: '#eff6ff', color: '#3b82f6',
                  }}>
                    {api.category || 'General'}
                  </span>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <span style={{
                      fontSize: 11, fontWeight: 600, padding: '3px 8px', borderRadius: 12,
                      backgroundColor: pt.bg, color: pt.color,
                    }}>
                      {api.protocolType || 'REST'}
                    </span>
                    <span style={{
                      fontSize: 11, fontWeight: 600, padding: '3px 8px', borderRadius: 12,
                      backgroundColor: st.bg, color: st.color,
                    }}>
                      {api.status}
                    </span>
                  </div>
                </div>

                {/* Name + version */}
                <h3 style={{ fontSize: 16, fontWeight: 600, color: '#0f172a', margin: '0 0 2px' }}>{api.name}</h3>
                <p style={{ fontSize: 12, color: '#94a3b8', margin: '0 0 10px' }}>{api.version}</p>

                {/* Description */}
                <p style={{ fontSize: 13, color: '#64748b', lineHeight: 1.5, flex: 1, margin: 0 }}>
                  {api.description || 'No description available.'}
                </p>

                {/* Tags */}
                {api.tags && api.tags.length > 0 && (
                  <div style={{ display: 'flex', gap: 6, marginTop: 14, flexWrap: 'wrap' }}>
                    {api.tags.slice(0, 4).map((tag) => (
                      <span
                        key={tag}
                        style={{
                          fontSize: 11, padding: '2px 8px', borderRadius: 4,
                          backgroundColor: '#f1f5f9', color: '#64748b',
                        }}
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                )}

                {/* Footer */}
                <div style={{ marginTop: 14, paddingTop: 12, borderTop: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                    <span style={{ fontSize: 12, color: '#94a3b8' }}>
                      {api.visibility === 'PUBLIC' ? 'Public' : api.visibility}
                    </span>
                    {api.sensitivity && api.sensitivity !== 'LOW' && (
                      <span style={{
                        fontSize: 10, fontWeight: 600, padding: '1px 6px', borderRadius: 4,
                        backgroundColor: api.sensitivity === 'HIGH' || api.sensitivity === 'CRITICAL' ? '#fef2f2' : '#fef3c7',
                        color: api.sensitivity === 'HIGH' || api.sensitivity === 'CRITICAL' ? '#dc2626' : '#d97706',
                      }}>
                        {api.sensitivity}
                      </span>
                    )}
                    {api.versionStatus === 'DEPRECATED' && (
                      <span style={{ fontSize: 10, fontWeight: 600, padding: '1px 6px', borderRadius: 4, backgroundColor: '#fef3c7', color: '#d97706' }}>
                        Deprecated
                      </span>
                    )}
                  </div>
                  <span style={{ fontSize: 12, color: '#3b82f6', fontWeight: 500 }}>
                    View details &rarr;
                  </span>
                </div>
              </a>
            );
          })}
        </div>
      )}
    </div>
  );
}
