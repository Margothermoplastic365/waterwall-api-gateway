const MGMT_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

interface ApiSummary {
  id: string;
  name: string;
  description: string;
  category: string;
  version: string;
  status: string;
  visibility: string;
  protocolType: string;
  tags: string[];
}

async function fetchFeaturedApis(): Promise<ApiSummary[]> {
  try {
    const res = await fetch(`${MGMT_URL}/v1/apis?status=PUBLISHED&size=6`, {
      cache: 'no-store',
    });
    if (!res.ok) return [];
    const data = await res.json();
    return data.content || [];
  } catch {
    return [];
  }
}

const PLATFORM_STATS = [
  { label: 'APIs Available', value: '7+', icon: '\u{1F4E1}' },
  { label: 'Organizations', value: '3', icon: '\u{1F3E2}' },
  { label: 'Uptime SLA', value: '99.9%', icon: '\u{2705}' },
  { label: 'Avg Latency', value: '<100ms', icon: '\u{26A1}' },
];

export default async function HomePage() {
  const apis = await fetchFeaturedApis();

  return (
    <main>
      {/* Hero Section */}
      <section className="hero">
        <div className="container">
          <div style={{ maxWidth: 720, margin: '0 auto', textAlign: 'center' }}>
            <span
              style={{
                display: 'inline-block',
                padding: '6px 16px',
                borderRadius: 20,
                backgroundColor: 'rgba(255,255,255,0.15)',
                fontSize: '0.8125rem',
                fontWeight: 500,
                marginBottom: 20,
                backdropFilter: 'blur(4px)',
              }}
            >
              Trusted by developers worldwide
            </span>
            <h1 style={{ fontSize: '2.75rem', lineHeight: 1.15, marginBottom: 16 }}>
              Build Faster with
              <br />
              Production-Ready APIs
            </h1>
            <p style={{ fontSize: '1.15rem', opacity: 0.85, maxWidth: 560, margin: '0 auto 32px' }}>
              Discover, subscribe, and integrate with our catalog of managed APIs.
              Get your keys and start building in minutes — not days.
            </p>
            <div className="flex-center gap-md">
              <a href="/catalog" className="btn btn-primary btn-lg">
                Browse Catalog
              </a>
              <a href="/auth/register" className="btn btn-secondary btn-lg">
                Create Free Account
              </a>
            </div>
          </div>
        </div>
      </section>

      {/* Stats Bar */}
      <section style={{ padding: '0 24px', marginTop: -40, position: 'relative', zIndex: 1 }}>
        <div
          className="container"
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',
            gap: 1,
            backgroundColor: '#e2e8f0',
            borderRadius: 12,
            overflow: 'hidden',
            boxShadow: '0 4px 24px rgba(0,0,0,0.08)',
          }}
        >
          {PLATFORM_STATS.map((stat) => (
            <div
              key={stat.label}
              style={{
                backgroundColor: '#fff',
                padding: '24px 20px',
                textAlign: 'center',
              }}
            >
              <div style={{ fontSize: 24, marginBottom: 4 }}>{stat.icon}</div>
              <div style={{ fontSize: 24, fontWeight: 700, color: '#0f172a' }}>{stat.value}</div>
              <div style={{ fontSize: 13, color: '#64748b', marginTop: 2 }}>{stat.label}</div>
            </div>
          ))}
        </div>
      </section>

      {/* How It Works */}
      <section className="section">
        <div className="container">
          <h2 className="text-center mb-sm">How It Works</h2>
          <p className="text-center text-muted" style={{ maxWidth: 480, margin: '0 auto 40px' }}>
            Three simple steps to start integrating APIs into your application.
          </p>
          <div className="grid grid-cols-3 gap-lg">
            {[
              {
                step: '1',
                title: 'Browse & Subscribe',
                desc: 'Explore our catalog, find the APIs you need, and subscribe to a plan.',
                icon: '\u{1F50D}',
              },
              {
                step: '2',
                title: 'Get Your API Key',
                desc: 'Create an application, generate API keys, and configure authentication.',
                icon: '\u{1F511}',
              },
              {
                step: '3',
                title: 'Start Building',
                desc: 'Use our interactive console, docs, and SDKs to integrate in minutes.',
                icon: '\u{1F680}',
              },
            ].map((item) => (
              <div
                key={item.step}
                className="card"
                style={{ textAlign: 'center', padding: '32px 24px' }}
              >
                <div
                  style={{
                    width: 56,
                    height: 56,
                    borderRadius: '50%',
                    backgroundColor: '#eff6ff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    margin: '0 auto 16px',
                    fontSize: 28,
                  }}
                >
                  {item.icon}
                </div>
                <div
                  style={{
                    fontSize: 12,
                    fontWeight: 600,
                    color: '#3b82f6',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                    marginBottom: 8,
                  }}
                >
                  Step {item.step}
                </div>
                <h3 style={{ marginBottom: 8, fontSize: '1.05rem' }}>{item.title}</h3>
                <p className="text-muted text-sm">{item.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Featured APIs */}
      <section className="section section-alt">
        <div className="container">
          <h2 className="text-center mb-sm">Featured APIs</h2>
          <p className="text-center text-muted" style={{ maxWidth: 520, margin: '0 auto 40px' }}>
            Our most popular APIs, trusted by teams across the globe.
          </p>
          <div className="grid grid-cols-3 gap-lg">
            {(apis.length > 0
              ? apis
              : [
                  { id: '1', name: 'Payment Processing API', description: 'Process credit card payments, refunds, and chargebacks.', category: 'Finance', version: 'v2.1.0', protocolType: 'REST', tags: ['payments'], status: 'PUBLISHED', visibility: 'PUBLIC' },
                  { id: '2', name: 'Shipment Tracking API', description: 'Real-time shipment tracking with webhooks for status updates.', category: 'Logistics', version: 'v1.3.0', protocolType: 'REST', tags: ['shipping'], status: 'PUBLISHED', visibility: 'PUBLIC' },
                  { id: '3', name: 'Weather Forecast API', description: 'Global weather data and forecasts powered by satellite imagery.', category: 'Weather', version: 'v3.0.0', protocolType: 'REST', tags: ['weather'], status: 'PUBLISHED', visibility: 'PUBLIC' },
                ]
            ).map((api) => (
              <a
                key={api.id}
                href={`/catalog/${api.id}`}
                style={{ textDecoration: 'none', color: 'inherit' }}
              >
                <div className="card card-clickable" style={{ height: '100%' }}>
                  <div className="flex-between mb-sm">
                    <span className="badge badge-blue">{api.category || 'General'}</span>
                    <span className="badge badge-purple">{api.protocolType || 'REST'}</span>
                  </div>
                  <h3 style={{ marginBottom: 6, fontSize: '1rem' }}>{api.name}</h3>
                  <p className="text-sm text-light" style={{ marginBottom: 8 }}>{api.version}</p>
                  <p className="text-muted text-sm" style={{ flex: 1 }}>
                    {api.description || 'No description available.'}
                  </p>
                  {api.tags && api.tags.length > 0 && (
                    <div className="flex gap-sm" style={{ marginTop: 12, flexWrap: 'wrap' }}>
                      {api.tags.slice(0, 3).map((tag: string) => (
                        <span
                          key={tag}
                          style={{
                            fontSize: 11,
                            padding: '2px 8px',
                            borderRadius: 4,
                            backgroundColor: '#f1f5f9',
                            color: '#64748b',
                          }}
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </a>
            ))}
          </div>
          <div className="text-center mt-xl">
            <a href="/catalog" className="btn btn-secondary btn-lg">
              View All APIs &rarr;
            </a>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="section text-center">
        <div className="container">
          <h2 style={{ marginBottom: 12, fontSize: '1.75rem' }}>Ready to build something great?</h2>
          <p className="text-muted" style={{ maxWidth: 480, margin: '0 auto 28px' }}>
            Create a free account to subscribe to APIs, get your API keys, and start integrating today.
          </p>
          <div className="flex-center gap-md">
            <a href="/auth/register" className="btn btn-primary btn-lg">
              Create Free Account
            </a>
            <a href="/status" className="btn btn-secondary btn-lg">
              System Status
            </a>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer style={{ padding: '32px 24px', textAlign: 'center', borderTop: '1px solid var(--color-border)' }}>
        <p className="text-sm text-muted">
          &copy; {new Date().getFullYear()} Waterwall API Gateway. All rights reserved.
        </p>
      </footer>
    </main>
  );
}
