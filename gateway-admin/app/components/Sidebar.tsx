'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState } from 'react';

interface NavItem {
  label: string;
  href: string;
  icon: React.ReactNode;
}

interface NavGroup {
  title: string;
  icon: React.ReactNode;
  items: NavItem[];
}

/* ── Tiny reusable SVG icon components ─────────────────────────────── */
const Icon = ({ d, className = 'w-[18px] h-[18px]' }: { d: string; className?: string }) => (
  <svg className={`${className} shrink-0`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
    <path strokeLinecap="round" strokeLinejoin="round" d={d} />
  </svg>
);

// Group icons
const DashboardIcon = () => <Icon d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-4 0h4" />;
const ApiIcon = () => <Icon d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />;
const UsersIcon = () => <Icon d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />;
const AiIcon = () => <Icon d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />;
const EventIcon = () => <Icon d="M13 10V3L4 14h7v7l9-11h-7z" />;
const ShieldIcon = () => <Icon d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />;
const ServerIcon = () => <Icon d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01" />;
const OpsIcon = () => <Icon d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />;
const ObsIcon = () => <Icon d="M15 12a3 3 0 11-6 0 3 3 0 016 0z M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />;
const MoneyIcon = () => <Icon d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />;
const ToolsIcon = () => <Icon d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />;

// Item icons (smaller)
const Dot = () => <span className="w-1.5 h-1.5 rounded-full bg-current opacity-40 shrink-0" />;

const navigation: NavGroup[] = [
  {
    title: 'Overview',
    icon: <DashboardIcon />,
    items: [
      { label: 'Dashboard', href: '/', icon: <Dot /> },
    ],
  },
  {
    title: 'API Management',
    icon: <ApiIcon />,
    items: [
      { label: 'APIs', href: '/apis', icon: <Dot /> },
      { label: 'Gateway', href: '/gateway', icon: <Dot /> },
      { label: 'Plans', href: '/plans', icon: <Dot /> },
      { label: 'Subscriptions', href: '/subscriptions', icon: <Dot /> },
      { label: 'Approvals', href: '/approvals', icon: <Dot /> },

      { label: 'Mocking', href: '/mocking', icon: <Dot /> },
    ],
  },
  {
    title: 'Users & Access',
    icon: <UsersIcon />,
    items: [
      { label: 'Users', href: '/users', icon: <Dot /> },
      { label: 'Organizations', href: '/orgs', icon: <Dot /> },
      { label: 'Roles', href: '/roles', icon: <Dot /> },
      { label: 'Audit Log', href: '/audit', icon: <Dot /> },
    ],
  },
  {
    title: 'Events & Streaming',
    icon: <EventIcon />,
    items: [
      { label: 'Event APIs', href: '/event-apis', icon: <Dot /> },
      { label: 'Event Analytics', href: '/event-analytics', icon: <Dot /> },
    ],
  },
  {
    title: 'Governance',
    icon: <ShieldIcon />,
    items: [
      { label: 'Policies', href: '/policies', icon: <Dot /> },
      { label: 'Governance', href: '/governance', icon: <Dot /> },
      { label: 'Security', href: '/security-dashboard', icon: <Dot /> },
      { label: 'Compliance', href: '/compliance', icon: <Dot /> },
    ],
  },
  {
    title: 'Infrastructure',
    icon: <ServerIcon />,
    items: [
      { label: 'Environments', href: '/environments', icon: <Dot /> },
      { label: 'Cluster', href: '/cluster', icon: <Dot /> },
      { label: 'Configuration', href: '/config', icon: <Dot /> },
      { label: 'Connectors', href: '/connectors', icon: <Dot /> },
      { label: 'Regions', href: '/regions', icon: <Dot /> },
      { label: 'Federation', href: '/federation', icon: <Dot /> },
    ],
  },
  {
    title: 'Operations',
    icon: <OpsIcon />,
    items: [
      { label: 'Incidents', href: '/incidents', icon: <Dot /> },
      { label: 'DR Drills', href: '/dr-drills', icon: <Dot /> },
      { label: 'Migrations', href: '/migrations', icon: <Dot /> },
    ],
  },
  {
    title: 'Observability',
    icon: <ObsIcon />,
    items: [
      { label: 'Monitoring', href: '/monitoring', icon: <Dot /> },
      { label: 'Analytics', href: '/analytics', icon: <Dot /> },
      { label: 'SLA', href: '/sla', icon: <Dot /> },
      { label: 'Reports', href: '/reports', icon: <Dot /> },
      { label: 'Alerts', href: '/alerts', icon: <Dot /> },
    ],
  },
  {
    title: 'Business',
    icon: <MoneyIcon />,
    items: [
      { label: 'Monetization', href: '/monetization', icon: <Dot /> },
      { label: 'Payment Gateways', href: '/payment-settings', icon: <Dot /> },
    ],
  },
];

export function Sidebar() {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {};
    navigation.forEach((g) => {
      // Auto-expand the group that contains the current page
      const hasActive = g.items.some((item) =>
        item.href === '/' ? pathname === '/' : pathname === item.href || pathname.startsWith(item.href + '/')
      );
      initial[g.title] = !hasActive;
    });
    return initial;
  });

  const toggle = (title: string) => {
    setCollapsed((prev) => ({ ...prev, [title]: !prev[title] }));
  };

  const isActive = (href: string) => {
    if (href === '/') return pathname === '/';
    return pathname === href || pathname.startsWith(href + '/');
  };

  if (pathname.startsWith('/auth')) return null;

  return (
    <aside className="fixed top-0 left-0 h-screen w-64 bg-gradient-to-b from-slate-900 to-slate-950 text-slate-300 flex flex-col z-40">
      {/* ── Brand ──────────────────────────────────────────────── */}
      <div className="px-5 h-16 flex items-center gap-3 border-b border-white/[0.06]">
        <Link href="/" className="flex items-center gap-3 no-underline hover:no-underline group">
          <img src="/logo.svg" alt="Waterwall" className="w-9 h-9 rounded-xl shadow-lg shadow-purple-900/30 group-hover:shadow-purple-700/40 transition-shadow" />
          <div className="leading-tight">
            <div className="text-white font-semibold text-[13px] tracking-tight">Waterwall</div>
            <div className="text-purple-400/80 text-[11px] font-medium tracking-wide uppercase">Admin Console</div>
          </div>
        </Link>
      </div>

      {/* ── Navigation ─────────────────────────────────────────── */}
      <nav className="flex-1 overflow-y-auto py-4 px-3 sidebar-scroll">
        {navigation.map((group) => {
          const isGroupCollapsed = collapsed[group.title] ?? false;
          const hasActiveChild = group.items.some((item) => isActive(item.href));

          return (
            <div key={group.title} className="mb-1.5">
              {/* Group header */}
              <button
                onClick={() => toggle(group.title)}
                className={`
                  w-full flex items-center gap-2.5 px-3 py-[9px] rounded-lg text-[13px] font-medium
                  transition-all duration-150 group
                  ${hasActiveChild
                    ? 'text-white bg-white/[0.06]'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.04]'
                  }
                `}
              >
                <span className={`transition-colors ${hasActiveChild ? 'text-purple-400' : 'text-slate-500 group-hover:text-slate-400'}`}>
                  {group.icon}
                </span>
                <span className="flex-1 text-left">{group.title}</span>
                <svg
                  className={`w-3.5 h-3.5 text-slate-600 transition-transform duration-200 ${isGroupCollapsed ? '-rotate-90' : ''}`}
                  fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
                >
                  <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                </svg>
              </button>

              {/* Group items */}
              <div
                className={`overflow-hidden transition-all duration-200 ease-in-out ${
                  isGroupCollapsed ? 'max-h-0 opacity-0' : 'max-h-[500px] opacity-100'
                }`}
              >
                <div className="ml-[18px] pl-3.5 border-l border-white/[0.06] mt-1 mb-1 space-y-0.5">
                  {group.items.map((item) => {
                    const active = isActive(item.href);
                    return (
                      <Link
                        key={item.href}
                        href={item.href}
                        className={`
                          relative flex items-center gap-2.5 px-3 py-[7px] text-[13px] rounded-md
                          transition-all duration-150 no-underline hover:no-underline
                          ${active
                            ? 'text-white bg-purple-600/20 font-medium before:absolute before:-left-[15px] before:top-1/2 before:-translate-y-1/2 before:w-[3px] before:h-4 before:rounded-full before:bg-purple-400'
                            : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.04]'
                          }
                        `}
                      >
                        {item.label}
                      </Link>
                    );
                  })}
                </div>
              </div>
            </div>
          );
        })}
      </nav>

      {/* ── Footer ─────────────────────────────────────────────── */}
      <div className="px-3 py-3 border-t border-white/[0.06] space-y-0.5">
        <a
          href={process.env.NEXT_PUBLIC_PORTAL_URL || 'http://localhost:3000'}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2.5 px-3 py-2 text-[13px] text-slate-400 hover:text-slate-200 hover:bg-white/[0.04] rounded-lg transition-all no-underline hover:no-underline"
        >
          <svg className="w-[18px] h-[18px] shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
          </svg>
          Developer Portal
        </a>
        <Link
          href="/auth/login"
          className="flex items-center gap-2.5 px-3 py-2 text-[13px] text-slate-400 hover:text-red-400 hover:bg-red-500/[0.08] rounded-lg transition-all no-underline hover:no-underline"
        >
          <svg className="w-[18px] h-[18px] shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
          Sign Out
        </Link>
      </div>
    </aside>
  );
}
