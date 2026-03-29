'use client';

import { usePathname } from 'next/navigation';

export default function PublicLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isAuthPage = pathname.startsWith('/auth');

  // Auth pages get no chrome — they render their own full-screen layout
  if (isAuthPage) {
    return <>{children}</>;
  }

  // Public pages (landing, catalog, status) get a simple top navbar
  return (
    <div className="min-h-screen bg-slate-50">
      <nav className="sticky top-0 z-50 bg-white border-b border-slate-200">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <a href="/" className="flex items-center gap-2.5 no-underline">
            <img src="/logo.svg" alt="Waterwall" className="w-8 h-8 rounded-lg" />
            <span className="font-semibold text-slate-900 text-sm">Waterwall</span>
          </a>
          <div className="flex items-center gap-6">
            <a href="/catalog" className="text-sm text-slate-600 hover:text-blue-600 font-medium no-underline transition-colors">
              Catalog
            </a>
            <a href="/auth/login" className="text-sm text-slate-600 hover:text-blue-600 font-medium no-underline transition-colors">
              Sign In
            </a>
            <a href="/auth/register" className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors no-underline">
              Get Started
            </a>
          </div>
        </div>
      </nav>
      {children}
    </div>
  );
}
