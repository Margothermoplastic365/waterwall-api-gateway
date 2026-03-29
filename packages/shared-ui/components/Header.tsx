'use client';

import React, { useState, useRef, useEffect } from 'react';

export interface HeaderUser {
  displayName: string;
  email: string;
  avatarUrl?: string;
}

export interface HeaderProps {
  user?: HeaderUser;
  onLogout: () => void;
  title?: string;
}

export default function Header({ user, onLogout, title = 'API Gateway' }: HeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // Close dropdown on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const initials = user?.displayName
    ? user.displayName
        .split(' ')
        .map((n) => n[0])
        .join('')
        .toUpperCase()
        .slice(0, 2)
    : '?';

  return (
    <header className="h-14 bg-white border-b border-gray-200 flex items-center justify-between px-6 shrink-0">
      {/* App title */}
      <h1 className="text-lg font-semibold text-gray-900">{title}</h1>

      {/* Spacer */}
      <div className="flex-1" />

      {/* User menu */}
      {user ? (
        <div className="relative" ref={menuRef}>
          <button
            className="flex items-center gap-2 hover:bg-gray-50 rounded-md px-2 py-1 transition-colors"
            onClick={() => setMenuOpen((v) => !v)}
          >
            {user.avatarUrl ? (
              <img
                src={user.avatarUrl}
                alt={user.displayName}
                className="w-8 h-8 rounded-full object-cover"
              />
            ) : (
              <div className="w-8 h-8 rounded-full bg-blue-600 text-white flex items-center justify-center text-xs font-bold">
                {initials}
              </div>
            )}
            <span className="text-sm text-gray-700 hidden sm:block">{user.displayName}</span>
            <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          {menuOpen && (
            <div className="absolute right-0 mt-1 w-56 bg-white border border-gray-200 rounded-md shadow-lg z-50">
              <div className="px-4 py-3 border-b border-gray-100">
                <p className="text-sm font-medium text-gray-900">{user.displayName}</p>
                <p className="text-xs text-gray-500 truncate">{user.email}</p>
              </div>
              <button
                className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors"
                onClick={() => {
                  setMenuOpen(false);
                  onLogout();
                }}
              >
                Sign out
              </button>
            </div>
          )}
        </div>
      ) : (
        <a
          href="/auth/login"
          className="text-sm text-blue-600 hover:text-blue-800 font-medium"
        >
          Sign in
        </a>
      )}
    </header>
  );
}
