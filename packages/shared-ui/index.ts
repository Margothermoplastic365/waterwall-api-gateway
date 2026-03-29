// @gateway/shared-ui — Shared components and utilities
// Used by both gateway-portal and gateway-admin

// ─── Components ──────────────────────────────────────────────
export { default as DataTable } from './components/DataTable';
export type { DataTableProps, Column, Pagination } from './components/DataTable';

export { default as FormModal } from './components/FormModal';
export type { FormModalProps } from './components/FormModal';

export { default as StatusBadge } from './components/StatusBadge';
export type { StatusBadgeProps } from './components/StatusBadge';

export { default as PermissionGate } from './components/PermissionGate';
export type { PermissionGateProps } from './components/PermissionGate';

export { default as ApiConsole } from './components/ApiConsole';
export type { ApiConsoleProps, EndpointDef } from './components/ApiConsole';

export { default as Sidebar } from './components/Sidebar';
export type { SidebarProps, SidebarItem } from './components/Sidebar';

export { default as Header } from './components/Header';
export type { HeaderProps, HeaderUser } from './components/Header';

// ─── Lib: API Client ────────────────────────────────────────
export {
  apiClient,
  authFetch,
  handle401,
  handle403,
  get,
  post,
  put,
  del,
  ApiError,
  fetchApis,
  fetchApi,
  createApp,
  listMyApps,
  generateApiKey,
  listApiKeys,
  revokeApiKey,
  subscribe,
  listSubscriptions,
} from './lib/api-client';
export type {
  ApiErrorResponse,
  PageResponse,
  ApiDefinition,
  Application,
  ApiKey,
  Subscription,
} from './lib/api-client';

// ─── Lib: Auth ──────────────────────────────────────────────
export {
  login,
  register,
  logout,
  getToken,
  getUser,
  isAuthenticated,
  useAuth,
} from './lib/auth';
export type { AuthUser, UseAuthReturn } from './lib/auth';

// ─── Lib: Permissions ───────────────────────────────────────
export {
  checkPermission,
  hasAnyPermission,
  usePermissions,
} from './lib/permissions';
