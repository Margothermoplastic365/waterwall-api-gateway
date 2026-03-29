import React from 'react';

export interface StatusBadgeProps {
  status: string;
  size?: 'sm' | 'md' | 'lg';
}

const STATUS_COLORS: Record<string, { dot: string; text: string; bg: string }> = {
  ACTIVE:      { dot: 'bg-green-500',  text: 'text-green-700',  bg: 'bg-green-50' },
  PUBLISHED:   { dot: 'bg-green-500',  text: 'text-green-700',  bg: 'bg-green-50' },
  PENDING:     { dot: 'bg-yellow-500', text: 'text-yellow-700', bg: 'bg-yellow-50' },
  CREATED:     { dot: 'bg-yellow-500', text: 'text-yellow-700', bg: 'bg-yellow-50' },
  SUSPENDED:   { dot: 'bg-orange-500', text: 'text-orange-700', bg: 'bg-orange-50' },
  DEPRECATED:  { dot: 'bg-orange-500', text: 'text-orange-700', bg: 'bg-orange-50' },
  REVOKED:     { dot: 'bg-red-500',    text: 'text-red-700',    bg: 'bg-red-50' },
  RETIRED:     { dot: 'bg-red-500',    text: 'text-red-700',    bg: 'bg-red-50' },
  DELETED:     { dot: 'bg-red-500',    text: 'text-red-700',    bg: 'bg-red-50' },
};

const DEFAULT_COLOR = { dot: 'bg-gray-400', text: 'text-gray-600', bg: 'bg-gray-50' };

const SIZE_MAP = {
  sm: { dot: 'w-1.5 h-1.5', text: 'text-xs', px: 'px-2 py-0.5' },
  md: { dot: 'w-2 h-2',     text: 'text-sm', px: 'px-2.5 py-1' },
  lg: { dot: 'w-2.5 h-2.5', text: 'text-base', px: 'px-3 py-1' },
};

export default function StatusBadge({ status, size = 'md' }: StatusBadgeProps) {
  const upper = status.toUpperCase();
  const color = STATUS_COLORS[upper] ?? DEFAULT_COLOR;
  const sz = SIZE_MAP[size];

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full font-medium ${color.bg} ${color.text} ${sz.text} ${sz.px}`}
    >
      <span className={`inline-block rounded-full ${color.dot} ${sz.dot}`} />
      {status}
    </span>
  );
}
