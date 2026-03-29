'use client';

import React, { useEffect, useState } from 'react';

const NOTIFICATION_URL = process.env.NEXT_PUBLIC_NOTIFICATION_URL || 'http://localhost:8084';

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token')
    || localStorage.getItem('admin_token')
    || localStorage.getItem('jwt_token');
}

interface Notification {
  id: number;
  title: string;
  body: string;
  type: 'INFO' | 'WARNING' | 'ERROR' | 'ALERT';
  read: boolean;
  createdAt: string;
}

const TYPE_STYLES: Record<string, { bg: string; color: string; icon: string }> = {
  INFO:    { bg: '#eff6ff', color: '#3b82f6', icon: '\u2139\uFE0F' },
  WARNING: { bg: '#fffbeb', color: '#d97706', icon: '\u26A0\uFE0F' },
  ERROR:   { bg: '#fef2f2', color: '#dc2626', icon: '\u274C' },
  ALERT:   { bg: '#faf5ff', color: '#7c3aed', icon: '\u{1F514}' },
};

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'all' | 'unread'>('all');

  useEffect(() => {
    async function load() {
      const token = getToken();
      if (!token) {
        setLoading(false);
        return;
      }
      try {
        const res = await fetch(`${NOTIFICATION_URL}/v1/notifications?size=50`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (res.ok) {
          const data = await res.json();
          setNotifications(Array.isArray(data) ? data : data.content || []);
        }
      } catch {
        // silent — show empty state
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  async function markAsRead(id: number) {
    const token = getToken();
    if (!token) return;
    try {
      await fetch(`${NOTIFICATION_URL}/v1/notifications/${id}/read`, {
        method: 'PUT',
        headers: { Authorization: `Bearer ${token}` },
      });
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
    } catch {
      // silent
    }
  }

  const filtered = filter === 'unread'
    ? notifications.filter((n) => !n.read)
    : notifications;

  const unreadCount = notifications.filter((n) => !n.read).length;

  if (loading) {
    return (
      <div>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', marginBottom: 24 }}>Notifications</h1>
        <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0' }}>
          {[1, 2, 3, 4].map((i) => (
            <div key={i} style={{ padding: '16px 20px', borderBottom: '1px solid #f1f5f9' }}>
              <div style={{ height: 16, width: '40%', backgroundColor: '#f1f5f9', borderRadius: 4, marginBottom: 8 }} />
              <div style={{ height: 13, width: '70%', backgroundColor: '#f8fafc', borderRadius: 4 }} />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', marginBottom: 4 }}>Notifications</h1>
          <p style={{ fontSize: 14, color: '#64748b', margin: 0 }}>
            {unreadCount > 0 ? `${unreadCount} unread notification${unreadCount !== 1 ? 's' : ''}` : 'All caught up'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: 4, backgroundColor: '#f1f5f9', borderRadius: 8, padding: 3 }}>
          {(['all', 'unread'] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              style={{
                padding: '6px 16px',
                borderRadius: 6,
                border: 'none',
                fontSize: 13,
                fontWeight: 500,
                cursor: 'pointer',
                backgroundColor: filter === f ? '#fff' : 'transparent',
                color: filter === f ? '#0f172a' : '#64748b',
                boxShadow: filter === f ? '0 1px 2px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.15s',
              }}
            >
              {f === 'all' ? 'All' : `Unread (${unreadCount})`}
            </button>
          ))}
        </div>
      </div>

      {filtered.length === 0 ? (
        <div
          style={{
            backgroundColor: '#fff',
            borderRadius: 10,
            border: '1px solid #e2e8f0',
            padding: 64,
            textAlign: 'center',
          }}
        >
          <div style={{ fontSize: 48, marginBottom: 16, color: '#cbd5e1' }}>{filter === 'unread' ? '\u2705' : '\u{1F4EC}'}</div>
          <h2 style={{ fontSize: 18, fontWeight: 600, color: '#334155', marginBottom: 8 }}>
            {filter === 'unread' ? 'All caught up!' : 'No notifications yet'}
          </h2>
          <p style={{ color: '#94a3b8', fontSize: 14, margin: 0 }}>
            {filter === 'unread'
              ? 'You have no unread notifications.'
              : 'Notifications about your APIs, subscriptions, and account will appear here.'}
          </p>
        </div>
      ) : (
        <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', overflow: 'hidden' }}>
          {filtered.map((notif, idx) => {
            const style = TYPE_STYLES[notif.type] || TYPE_STYLES.INFO;
            return (
              <div
                key={notif.id}
                onClick={() => !notif.read && markAsRead(notif.id)}
                style={{
                  display: 'flex',
                  gap: 14,
                  padding: '16px 20px',
                  borderTop: idx > 0 ? '1px solid #f1f5f9' : 'none',
                  backgroundColor: notif.read ? '#fff' : '#fafbff',
                  cursor: notif.read ? 'default' : 'pointer',
                  transition: 'background 0.1s',
                }}
              >
                <div
                  style={{
                    width: 36,
                    height: 36,
                    borderRadius: 8,
                    backgroundColor: style.bg,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 18,
                    flexShrink: 0,
                    marginTop: 2,
                  }}
                >
                  {style.icon}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
                    <div style={{ fontWeight: notif.read ? 500 : 600, fontSize: 14, color: '#0f172a' }}>
                      {notif.title}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                      <span style={{ fontSize: 12, color: '#94a3b8', whiteSpace: 'nowrap' }}>
                        {timeAgo(notif.createdAt)}
                      </span>
                      {!notif.read && (
                        <span
                          style={{
                            width: 8,
                            height: 8,
                            borderRadius: '50%',
                            backgroundColor: '#3b82f6',
                            display: 'inline-block',
                          }}
                        />
                      )}
                    </div>
                  </div>
                  <p style={{ fontSize: 13, color: '#64748b', margin: '4px 0 0', lineHeight: 1.5 }}>
                    {notif.body}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
