'use client';

import React from 'react';
import PermissionGate from './PermissionGate';

export interface SidebarItem {
  label: string;
  href: string;
  icon?: string;
  permission?: string;
}

export interface SidebarProps {
  items: SidebarItem[];
  currentPath: string;
}

const ICON_MAP: Record<string, string> = {
  home: '\uD83C\uDFE0',
  api: '\uD83D\uDD17',
  apps: '\uD83D\uDCE6',
  keys: '\uD83D\uDD11',
  users: '\uD83D\uDC65',
  settings: '\u2699\uFE0F',
  analytics: '\uD83D\uDCCA',
  docs: '\uD83D\uDCC4',
  gateway: '\uD83C\uDF10',
};

function NavItem({
  item,
  active,
}: {
  item: SidebarItem;
  active: boolean;
}) {
  const icon = item.icon ? ICON_MAP[item.icon] ?? item.icon : null;

  return (
    <a
      href={item.href}
      className={`flex items-center gap-3 px-4 py-2.5 text-sm rounded-md transition-colors ${
        active
          ? 'bg-blue-50 text-blue-700 font-medium'
          : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
      }`}
    >
      {icon && <span className="text-base w-5 text-center">{icon}</span>}
      <span>{item.label}</span>
    </a>
  );
}

export default function Sidebar({ items, currentPath }: SidebarProps) {
  return (
    <nav className="w-60 min-h-screen bg-white border-r border-gray-200 py-4 flex flex-col gap-1 px-2">
      {items.map((item) => {
        const active =
          currentPath === item.href ||
          (item.href !== '/' && currentPath.startsWith(item.href));

        const navItem = <NavItem key={item.href} item={item} active={active} />;

        if (item.permission) {
          return (
            <PermissionGate key={item.href} permission={item.permission}>
              {navItem}
            </PermissionGate>
          );
        }

        return navItem;
      })}
    </nav>
  );
}
