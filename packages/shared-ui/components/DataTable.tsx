'use client';

import React, { type ReactNode } from 'react';

export interface Column<T> {
  key: string;
  label: string;
  sortable?: boolean;
  render?: (item: T) => ReactNode;
}

export interface Pagination {
  page: number;
  size: number;
  total: number;
  onPageChange: (page: number) => void;
}

export interface DataTableProps<T> {
  data: T[];
  columns: Column<T>[];
  pagination?: Pagination;
  onSort?: (key: string, dir: 'asc' | 'desc') => void;
  loading?: boolean;
}

interface SortState {
  key: string;
  dir: 'asc' | 'desc';
}

function SortArrow({ active, dir }: { active: boolean; dir: 'asc' | 'desc' }) {
  if (!active) {
    return (
      <span className="ml-1 text-gray-300 inline-flex flex-col text-[10px] leading-none">
        <span>&#9650;</span>
        <span>&#9660;</span>
      </span>
    );
  }
  return (
    <span className="ml-1 text-blue-500 text-xs">
      {dir === 'asc' ? '\u25B2' : '\u25BC'}
    </span>
  );
}

function SkeletonRow({ cols }: { cols: number }) {
  return (
    <tr className="animate-pulse">
      {Array.from({ length: cols }).map((_, i) => (
        <td key={i} className="px-4 py-3">
          <div className="h-4 bg-gray-200 rounded w-3/4" />
        </td>
      ))}
    </tr>
  );
}

export default function DataTable<T extends Record<string, unknown>>({
  data,
  columns,
  pagination,
  onSort,
  loading = false,
}: DataTableProps<T>) {
  const [sort, setSort] = React.useState<SortState | null>(null);

  const handleSort = (key: string) => {
    const newDir: 'asc' | 'desc' =
      sort?.key === key && sort.dir === 'asc' ? 'desc' : 'asc';
    setSort({ key, dir: newDir });
    onSort?.(key, newDir);
  };

  const totalPages = pagination
    ? Math.max(1, Math.ceil(pagination.total / pagination.size))
    : 1;

  const pageNumbers = React.useMemo(() => {
    if (!pagination) return [];
    const pages: number[] = [];
    const start = Math.max(1, pagination.page - 2);
    const end = Math.min(totalPages, pagination.page + 2);
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  }, [pagination, totalPages]);

  return (
    <div className="w-full overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-200 bg-white">
        <thead className="bg-gray-50">
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                className={`px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider ${
                  col.sortable ? 'cursor-pointer select-none hover:bg-gray-100' : ''
                }`}
                onClick={col.sortable ? () => handleSort(col.key) : undefined}
              >
                <span className="inline-flex items-center">
                  {col.label}
                  {col.sortable && (
                    <SortArrow
                      active={sort?.key === col.key}
                      dir={sort?.key === col.key ? sort.dir : 'asc'}
                    />
                  )}
                </span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200">
          {loading
            ? Array.from({ length: pagination?.size ?? 5 }).map((_, i) => (
                <SkeletonRow key={i} cols={columns.length} />
              ))
            : data.length === 0
              ? (
                <tr>
                  <td
                    colSpan={columns.length}
                    className="px-4 py-8 text-center text-gray-400"
                  >
                    No data available
                  </td>
                </tr>
              )
              : data.map((item, rowIdx) => (
                <tr key={rowIdx} className="hover:bg-gray-50 transition-colors">
                  {columns.map((col) => (
                    <td key={col.key} className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">
                      {col.render
                        ? col.render(item)
                        : String(item[col.key] ?? '')}
                    </td>
                  ))}
                </tr>
              ))}
        </tbody>
      </table>

      {pagination && (
        <div className="flex items-center justify-between px-4 py-3 bg-white border-t border-gray-200">
          <span className="text-sm text-gray-500">
            Showing {Math.min((pagination.page - 1) * pagination.size + 1, pagination.total)}
            {' '}&ndash;{' '}
            {Math.min(pagination.page * pagination.size, pagination.total)}
            {' '}of {pagination.total}
          </span>
          <div className="flex items-center gap-1">
            <button
              className="px-3 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              disabled={pagination.page <= 1}
              onClick={() => pagination.onPageChange(pagination.page - 1)}
            >
              Prev
            </button>
            {pageNumbers.map((p) => (
              <button
                key={p}
                className={`px-3 py-1 text-sm rounded border ${
                  p === pagination.page
                    ? 'bg-blue-600 text-white border-blue-600'
                    : 'border-gray-300 hover:bg-gray-100'
                }`}
                onClick={() => pagination.onPageChange(p)}
              >
                {p}
              </button>
            ))}
            <button
              className="px-3 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              disabled={pagination.page >= totalPages}
              onClick={() => pagination.onPageChange(pagination.page + 1)}
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
