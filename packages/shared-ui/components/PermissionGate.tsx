'use client';

import React, { type ReactNode } from 'react';
import { useAuth } from '../lib/auth';
import { checkPermission } from '../lib/permissions';

export interface PermissionGateProps {
  permission: string;
  children: ReactNode;
  fallback?: ReactNode;
}

export default function PermissionGate({
  permission,
  children,
  fallback = null,
}: PermissionGateProps) {
  const { permissions } = useAuth();

  if (!checkPermission(permissions, permission)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
