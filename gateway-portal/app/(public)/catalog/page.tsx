'use client';

import { useEffect, useState, useMemo } from 'react';
import Link from 'next/link';

const MGMT_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

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
}

export default function CatalogPage() {
  const [apis, setApis] = useState<ApiDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');

  useEffect(() => {
    async function load() {
      try {
        const res = await fetch(`${MGMT_URL}/v1/apis?status=PUBLISHED&size=100`, {
          cache: 'no-store',
        });
        if (res.ok) {
          const data = await res.json();
          setApis(data.content || []);
        }
      } catch {
        // silent fail — empty catalog
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

  const protocolColor = (protocol: string) => {
    switch (protocol?.toUpperCase()) {
      case 'GRAPHQL': return 'bg-purple-100 text-purple-700';
      case 'GRPC': return 'bg-amber-100 text-amber-700';
      case 'WEBSOCKET': return 'bg-cyan-100 text-cyan-700';
      case 'SOAP': return 'bg-orange-100 text-orange-700';
      default: return 'bg-emerald-100 text-emerald-700';
    }
  };

  return (
    <main className="min-h-screen bg-slate-50">
      {/* Hero Header */}
      <section className="bg-gradient-to-br from-slate-900 via-blue-900 to-slate-800 px-6 pt-12 pb-10">
        <div className="max-w-6xl mx-auto">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-blue-500/20 flex items-center justify-center">
              <svg className="w-5 h-5 text-blue-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 6.375c0 2.278-3.694 4.125-8.25 4.125S3.75 8.653 3.75 6.375m16.5 0c0-2.278-3.694-4.125-8.25-4.125S3.75 4.097 3.75 6.375m16.5 0v11.25c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125V6.375m16.5 0v3.75m-16.5-3.75v3.75m16.5 0v3.75C20.25 16.153 16.556 18 12 18s-8.25-1.847-8.25-4.125v-3.75m16.5 0c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125" />
              </svg>
            </div>
            <h1 className="text-2xl font-bold text-white">API Catalog</h1>
          </div>
          <p className="text-blue-200/70 text-sm max-w-lg">
            Browse and discover APIs available on the platform. Subscribe to get started with integration.
          </p>
        </div>
      </section>

      <section className="max-w-6xl mx-auto px-6 -mt-5">
        {/* Search & Filter Bar */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 mb-6">
          <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center">
            <div className="relative flex-1 min-w-0 w-full sm:max-w-md">
              <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
              </svg>
              <input
                type="text"
                placeholder="Search APIs by name, description, or tag..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-10 pr-4 py-2.5 rounded-lg border border-slate-200 text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
              />
            </div>
            <div className="flex gap-1.5 flex-wrap">
              {categories.map((cat) => (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`px-3.5 py-1.5 rounded-full text-xs font-medium transition-all ${
                    cat === selectedCategory
                      ? 'bg-blue-600 text-white shadow-sm'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Results count */}
        <p className="text-xs text-slate-500 mb-4 font-medium">
          {loading ? 'Loading...' : `${filtered.length} API${filtered.length !== 1 ? 's' : ''} available`}
        </p>

        {/* API Grid */}
        {loading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div
                key={i}
                className="bg-white rounded-xl border border-slate-200 p-6 animate-pulse"
              >
                <div className="flex justify-between items-center mb-4">
                  <div className="h-5 w-16 bg-slate-100 rounded-full" />
                  <div className="h-5 w-12 bg-slate-100 rounded-full" />
                </div>
                <div className="h-5 w-3/5 bg-slate-100 rounded mb-2" />
                <div className="h-3 w-1/4 bg-slate-50 rounded mb-4" />
                <div className="space-y-2">
                  <div className="h-3.5 w-full bg-slate-50 rounded" />
                  <div className="h-3.5 w-4/5 bg-slate-50 rounded" />
                </div>
              </div>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-20">
            <div className="w-16 h-16 rounded-2xl bg-slate-100 flex items-center justify-center mx-auto mb-4">
              <svg className="w-8 h-8 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
              </svg>
            </div>
            <p className="text-base font-medium text-slate-600 mb-1">
              {apis.length === 0 ? 'No APIs published yet' : 'No APIs match your search'}
            </p>
            <p className="text-sm text-slate-400">
              {apis.length === 0
                ? 'APIs will appear here once they are published to the catalog.'
                : 'Try adjusting your search or category filter.'}
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 pb-10">
            {filtered.map((api) => (
              <Link
                key={api.id}
                href={`/catalog/${api.id}`}
                className="group block"
              >
                <div className="h-full bg-white rounded-xl border border-slate-200 p-6 transition-all duration-150 hover:shadow-lg hover:shadow-slate-200/50 hover:border-blue-200 hover:-translate-y-0.5 flex flex-col">
                  <div className="flex justify-between items-center mb-3">
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700">
                      {api.category || 'General'}
                    </span>
                    <div className="flex gap-1.5">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${protocolColor(api.protocolType)}`}>
                        {api.protocolType || 'REST'}
                      </span>
                      {api.visibility === 'PUBLIC' && (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-50 text-green-700">
                          Public
                        </span>
                      )}
                    </div>
                  </div>
                  <h3 className="text-base font-semibold text-slate-900 mb-1 group-hover:text-blue-600 transition-colors">
                    {api.name}
                  </h3>
                  <p className="text-xs text-slate-400 mb-2.5">{api.version}</p>
                  <p className="text-sm text-slate-500 leading-relaxed flex-1 line-clamp-3">
                    {api.description || 'No description available.'}
                  </p>
                  {api.tags && api.tags.length > 0 && (
                    <div className="flex gap-1.5 mt-4 flex-wrap pt-3 border-t border-slate-100">
                      {api.tags.map((tag) => (
                        <span
                          key={tag}
                          className="text-[11px] px-2 py-0.5 rounded bg-slate-100 text-slate-500"
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
