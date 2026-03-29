'use client';

import React from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useAuth } from '@gateway/shared-ui/lib/auth';

interface SidebarItem {
  label: string;
  href: string;
  icon: string;
}

interface SidebarGroup {
  title: string;
  items: SidebarItem[];
}

const sidebarGroups: SidebarGroup[] = [
  {
    title: 'Overview',
    items: [
      { label: 'Dashboard', href: '/dashboard', icon: '\u{1F3E0}' },
      { label: 'Notifications', href: '/notifications', icon: '\u{1F514}' },
    ],
  },
  {
    title: 'APIs & Apps',
    items: [
      { label: 'API Catalog', href: '/api-catalog', icon: '\u{1F50D}' },
      { label: 'My Apps', href: '/apps', icon: '\u{1F4F1}' },
      { label: 'Subscriptions', href: '/subscriptions', icon: '\u{2B50}' },
      { label: 'SLA', href: '/sla', icon: '\u{1F6E1}' },
      { label: 'Changelogs', href: '/changelogs', icon: '\u{1F4DD}' },
    ],
  },
  {
    title: 'Account',
    items: [
      { label: 'Profile', href: '/profile', icon: '\u{1F464}' },
      { label: 'Billing', href: '/billing', icon: '\u{1F4B3}' },
    ],
  },
];

export default function AuthenticatedLayout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const handleLogout = () => {
    logout();
    router.push('/auth/login');
  };

  const isActive = (href: string) => {
    if (href === '/dashboard') return pathname === '/dashboard';
    return pathname.startsWith(href);
  };

  return (
    <div style={{ display: 'flex', minHeight: '100vh', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      {/* Sidebar */}
      <aside
        style={{
          width: 248,
          backgroundColor: '#0f172a',
          color: '#e2e8f0',
          display: 'flex',
          flexDirection: 'column',
          flexShrink: 0,
          borderRight: '1px solid #1e293b',
        }}
      >
        {/* Logo */}
        <a
          href="/dashboard"
          style={{
            padding: '20px 20px 16px',
            borderBottom: '1px solid #1e293b',
            textDecoration: 'none',
            display: 'block',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src="/logo.svg" alt="Waterwall" style={{ width: 32, height: 32, borderRadius: 8 }} />
            <div>
              <div style={{ fontSize: 15, fontWeight: 700, color: '#f8fafc', lineHeight: 1.2 }}>
                Waterwall
              </div>
              <div style={{ fontSize: 11, color: '#64748b', fontWeight: 500 }}>Developer Portal</div>
            </div>
          </div>
        </a>

        {/* Navigation Groups */}
        <nav style={{ flex: 1, padding: '8px 0', overflowY: 'auto' }}>
          {sidebarGroups.map((group, gi) => (
            <div key={group.title} style={{ marginTop: gi === 0 ? 0 : 8 }}>
              <div
                style={{
                  padding: '8px 20px 4px',
                  fontSize: 11,
                  fontWeight: 600,
                  color: '#475569',
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em',
                }}
              >
                {group.title}
              </div>
              {group.items.map((item) => {
                const active = isActive(item.href);
                return (
                  <a
                    key={item.href}
                    href={item.href}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      padding: '8px 20px',
                      margin: '1px 8px',
                      borderRadius: 6,
                      color: active ? '#f8fafc' : '#94a3b8',
                      backgroundColor: active ? '#1e293b' : 'transparent',
                      textDecoration: 'none',
                      fontSize: 13,
                      fontWeight: active ? 600 : 400,
                      transition: 'all 0.15s',
                    }}
                    onMouseEnter={(e) => {
                      if (!active) {
                        e.currentTarget.style.backgroundColor = '#1e293b';
                        e.currentTarget.style.color = '#cbd5e1';
                      }
                    }}
                    onMouseLeave={(e) => {
                      if (!active) {
                        e.currentTarget.style.backgroundColor = 'transparent';
                        e.currentTarget.style.color = '#94a3b8';
                      }
                    }}
                  >
                    <span style={{ fontSize: 15, width: 20, textAlign: 'center' }}>{item.icon}</span>
                    {item.label}
                  </a>
                );
              })}
            </div>
          ))}
        </nav>

        {/* User section at bottom */}
        <div
          style={{
            padding: '12px 16px',
            borderTop: '1px solid #1e293b',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
          }}
        >
          <div
            style={{
              width: 32,
              height: 32,
              borderRadius: '50%',
              backgroundColor: '#334155',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 13,
              fontWeight: 600,
              color: '#94a3b8',
              flexShrink: 0,
            }}
          >
            {(user?.displayName || user?.email || 'U').charAt(0).toUpperCase()}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div
              style={{
                fontSize: 13,
                fontWeight: 500,
                color: '#e2e8f0',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {user?.displayName || user?.email?.split('@')[0] || 'User'}
            </div>
            <div
              style={{
                fontSize: 11,
                color: '#64748b',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {user?.email || ''}
            </div>
          </div>
        </div>
      </aside>

      {/* Main area */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        {/* Header */}
        <header
          style={{
            height: 52,
            backgroundColor: '#fff',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            padding: '0 24px',
            gap: 12,
            flexShrink: 0,
          }}
        >
          <a
            href="/catalog"
            style={{
              fontSize: 13,
              color: '#3b82f6',
              textDecoration: 'none',
              fontWeight: 500,
              marginRight: 'auto',
            }}
          >
            Browse Catalog
          </a>
          <button
            onClick={handleLogout}
            style={{
              padding: '6px 14px',
              fontSize: 13,
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              borderRadius: 6,
              cursor: 'pointer',
              color: '#64748b',
              fontWeight: 500,
              transition: 'all 0.15s',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = '#f1f5f9';
              e.currentTarget.style.borderColor = '#cbd5e1';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = '#f8fafc';
              e.currentTarget.style.borderColor = '#e2e8f0';
            }}
          >
            Sign Out
          </button>
        </header>

        {/* Content */}
        <main style={{ flex: 1, padding: 24, backgroundColor: '#f8fafc', overflowY: 'auto' }}>
          {children}
        </main>
      </div>
    </div>
  );
}
