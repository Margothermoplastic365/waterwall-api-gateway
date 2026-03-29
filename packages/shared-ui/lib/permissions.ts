'use client';

import { useMemo } from 'react';
import { useAuth } from './auth';

/**
 * Check if the user has a specific permission.
 * Supports wildcard '*' for superadmin access.
 */
export function checkPermission(permissions: string[], required: string): boolean {
  if (!required) return true;
  return permissions.includes(required) || permissions.includes('*');
}

/**
 * Check if the user has any of the specified permissions.
 */
export function hasAnyPermission(permissions: string[], ...required: string[]): boolean {
  if (required.length === 0) return true;
  if (permissions.includes('*')) return true;
  return required.some((perm) => permissions.includes(perm));
}

/**
 * React hook to access current user permissions.
 */
export function usePermissions() {
  const { permissions } = useAuth();

  return useMemo(
    () => ({
      permissions,
      check: (required: string) => checkPermission(permissions, required),
      hasAny: (...required: string[]) => hasAnyPermission(permissions, ...required),
    }),
    [permissions],
  );
}
