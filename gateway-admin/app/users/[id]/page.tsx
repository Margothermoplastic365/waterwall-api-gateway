'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';

const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';

interface UserDetail {
  id: string;
  firstName?: string;
  lastName?: string;
  name?: string;
  email: string;
  phone?: string;
  status: string;
  createdAt?: string;
  created?: string;
  lastLogin?: string;
  lastLoginAt?: string;
}

interface RoleAssignment {
  id?: string;
  role: string;
  roleName?: string;
  scope?: string;
  scopeId?: string;
  assignedAt?: string;
}

function getToken(): string {
  if (typeof window !== 'undefined') {
    return localStorage.getItem('admin_token') || '';
  }
  return '';
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  return token
    ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
    : { 'Content-Type': 'application/json' };
}

function getUserName(user: UserDetail): string {
  if (user.name) return user.name;
  if (user.firstName || user.lastName) {
    return [user.firstName, user.lastName].filter(Boolean).join(' ');
  }
  return user.email.split('@')[0];
}

function getStatusBadgeClass(status: string): string {
  switch (status?.toUpperCase()) {
    case 'ACTIVE':
      return 'badge badge-green';
    case 'SUSPENDED':
      return 'badge badge-yellow';
    case 'LOCKED':
      return 'badge badge-red';
    default:
      return 'badge badge-gray';
  }
}

export default function UserDetailPage() {
  const params = useParams();
  const userId = params.id as string;

  const [user, setUser] = useState<UserDetail | null>(null);
  const [roles, setRoles] = useState<RoleAssignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState<'profile' | 'roles' | 'sessions'>('profile');
  const [statusLoading, setStatusLoading] = useState('');
  const [showRoleModal, setShowRoleModal] = useState(false);
  const [roleForm, setRoleForm] = useState({ role: 'DEVELOPER', scope: 'PLATFORM', scopeId: '' });
  const [roleLoading, setRoleLoading] = useState(false);
  const [roleError, setRoleError] = useState('');

  async function fetchUser() {
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/users/${userId}`, { headers: authHeaders() });
      if (!res.ok) throw new Error('Failed to fetch user');
      const data = await res.json();
      setUser(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load user');
    }
  }

  async function fetchRoles() {
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/users/${userId}/roles`, { headers: authHeaders() });
      if (!res.ok) return;
      const data = await res.json();
      const list = Array.isArray(data) ? data : data.content || data.data || [];
      setRoles(list);
    } catch {
      // Roles endpoint may not exist yet
    }
  }

  useEffect(() => {
    async function load() {
      setLoading(true);
      await Promise.all([fetchUser(), fetchRoles()]);
      setLoading(false);
    }
    load();
  }, [userId]);

  async function changeStatus(newStatus: string) {
    if (!user) return;
    setStatusLoading(newStatus);
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/users/${userId}/status`, {
        method: 'PUT',
        headers: authHeaders(),
        body: JSON.stringify({ status: newStatus }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => null);
        throw new Error(data?.message || 'Failed to update status');
      }
      setUser({ ...user, status: newStatus });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update status');
    } finally {
      setStatusLoading('');
    }
  }

  async function handleAssignRole(e: React.FormEvent) {
    e.preventDefault();
    setRoleLoading(true);
    setRoleError('');
    try {
      const res = await fetch(`${IDENTITY_URL}/v1/users/${userId}/roles`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(roleForm),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => null);
        throw new Error(data?.message || 'Failed to assign role');
      }
      setShowRoleModal(false);
      setRoleForm({ role: 'DEVELOPER', scope: 'PLATFORM', scopeId: '' });
      fetchRoles();
    } catch (err) {
      setRoleError(err instanceof Error ? err.message : 'Failed to assign role');
    } finally {
      setRoleLoading(false);
    }
  }

  if (loading) {
    return (
      <div className="container p-xl">
        <div className="flex-center" style={{ minHeight: '400px' }}>
          <div className="spinner spinner-dark" style={{ width: 40, height: 40 }} />
        </div>
      </div>
    );
  }

  if (error && !user) {
    return (
      <div className="container p-xl">
        <div className="alert alert-error">{error}</div>
        <a href="/users" className="btn btn-secondary mt-md">Back to Users</a>
      </div>
    );
  }

  if (!user) return null;

  return (
    <div className="container p-xl">
      {/* Header */}
      <div className="flex-between mb-lg">
        <div>
          <div className="flex gap-md" style={{ alignItems: 'center' }}>
            <h1>{getUserName(user)}</h1>
            <span className={getStatusBadgeClass(user.status)}>{user.status}</span>
          </div>
          <p className="text-muted mt-sm">{user.email}</p>
        </div>
        <a href="/users" className="btn btn-secondary">Back to Users</a>
      </div>

      {error && <div className="alert alert-error mb-md">{error}</div>}

      {/* Tabs */}
      <div className="tabs">
        <button
          className={`tab ${activeTab === 'profile' ? 'tab-active' : ''}`}
          onClick={() => setActiveTab('profile')}
        >
          Profile
        </button>
        <button
          className={`tab ${activeTab === 'roles' ? 'tab-active' : ''}`}
          onClick={() => setActiveTab('roles')}
        >
          Roles
        </button>
        <button
          className={`tab ${activeTab === 'sessions' ? 'tab-active' : ''}`}
          onClick={() => setActiveTab('sessions')}
        >
          Sessions
        </button>
      </div>

      {/* Profile Tab */}
      {activeTab === 'profile' && (
        <div className="card">
          <h3 className="mb-lg">User Profile</h3>
          <div className="detail-grid">
            <span className="detail-label">Name</span>
            <span className="detail-value">{getUserName(user)}</span>

            <span className="detail-label">Email</span>
            <span className="detail-value">{user.email}</span>

            <span className="detail-label">Phone</span>
            <span className="detail-value">{user.phone || '-'}</span>

            <span className="detail-label">Status</span>
            <span className="detail-value">
              <span className={getStatusBadgeClass(user.status)}>{user.status}</span>
            </span>

            <span className="detail-label">Created</span>
            <span className="detail-value">
              {user.createdAt || user.created
                ? new Date(user.createdAt || user.created!).toLocaleString()
                : '-'}
            </span>

            <span className="detail-label">Last Login</span>
            <span className="detail-value">
              {user.lastLogin || user.lastLoginAt
                ? new Date(user.lastLogin || user.lastLoginAt!).toLocaleString()
                : 'Never'}
            </span>
          </div>

          {/* Status Change Buttons */}
          <div className="flex gap-md mt-xl">
            <button
              className="btn btn-success btn-sm"
              disabled={user.status === 'ACTIVE' || statusLoading !== ''}
              onClick={() => changeStatus('ACTIVE')}
            >
              {statusLoading === 'ACTIVE' ? <span className="spinner" /> : null}
              Activate
            </button>
            <button
              className="btn btn-warning btn-sm"
              disabled={user.status === 'SUSPENDED' || statusLoading !== ''}
              onClick={() => changeStatus('SUSPENDED')}
            >
              {statusLoading === 'SUSPENDED' ? <span className="spinner" /> : null}
              Suspend
            </button>
            <button
              className="btn btn-danger btn-sm"
              disabled={user.status === 'LOCKED' || statusLoading !== ''}
              onClick={() => changeStatus('LOCKED')}
            >
              {statusLoading === 'LOCKED' ? <span className="spinner" /> : null}
              Lock
            </button>
          </div>
        </div>
      )}

      {/* Roles Tab */}
      {activeTab === 'roles' && (
        <div className="card">
          <div className="flex-between mb-lg">
            <h3>Role Assignments</h3>
            <button className="btn btn-primary btn-sm" onClick={() => setShowRoleModal(true)}>
              Assign Role
            </button>
          </div>

          {roles.length === 0 ? (
            <div className="text-muted text-center" style={{ padding: '40px 0' }}>
              No roles assigned to this user.
            </div>
          ) : (
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Role</th>
                    <th>Scope</th>
                    <th>Assigned</th>
                  </tr>
                </thead>
                <tbody>
                  {roles.map((ra, idx) => (
                    <tr key={ra.id || idx}>
                      <td>
                        <span className="badge badge-purple">
                          {ra.roleName || ra.role}
                        </span>
                      </td>
                      <td>{ra.scope || 'PLATFORM'}{ra.scopeId ? ` (${ra.scopeId})` : ''}</td>
                      <td className="text-muted text-sm">
                        {ra.assignedAt ? new Date(ra.assignedAt).toLocaleDateString() : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Sessions Tab */}
      {activeTab === 'sessions' && (
        <div className="card">
          <h3 className="mb-lg">Active Sessions</h3>
          <div className="text-muted text-center" style={{ padding: '40px 0' }}>
            <p>Active sessions will be displayed here.</p>
            <p className="mt-sm text-sm">Session management is coming soon.</p>
          </div>
        </div>
      )}

      {/* Assign Role Modal */}
      {showRoleModal && (
        <div className="modal-overlay" onClick={() => setShowRoleModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Assign Role</h3>
              <button className="modal-close" onClick={() => setShowRoleModal(false)}>
                &times;
              </button>
            </div>

            {roleError && <div className="alert alert-error">{roleError}</div>}

            <form onSubmit={handleAssignRole}>
              <div className="form-group">
                <label className="form-label">Role</label>
                <select
                  className="form-select w-full"
                  value={roleForm.role}
                  onChange={(e) => setRoleForm({ ...roleForm, role: e.target.value })}
                >
                  <option value="DEVELOPER">Developer</option>
                  <option value="API_PUBLISHER">API Publisher</option>
                  <option value="ORG_ADMIN">Org Admin</option>
                  <option value="PLATFORM_ADMIN">Platform Admin</option>
                  <option value="SUPER_ADMIN">Super Admin</option>
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Scope</label>
                <select
                  className="form-select w-full"
                  value={roleForm.scope}
                  onChange={(e) => setRoleForm({ ...roleForm, scope: e.target.value })}
                >
                  <option value="PLATFORM">Platform</option>
                  <option value="ORGANIZATION">Organization</option>
                  <option value="API">API</option>
                </select>
              </div>

              {roleForm.scope !== 'PLATFORM' && (
                <div className="form-group">
                  <label className="form-label">Scope ID</label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder={`Enter ${roleForm.scope.toLowerCase()} ID`}
                    value={roleForm.scopeId}
                    onChange={(e) => setRoleForm({ ...roleForm, scopeId: e.target.value })}
                    required
                  />
                </div>
              )}

              <div className="flex gap-md" style={{ justifyContent: 'flex-end' }}>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setShowRoleModal(false)}
                >
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={roleLoading}>
                  {roleLoading ? <span className="spinner" /> : null}
                  {roleLoading ? 'Assigning...' : 'Assign Role'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
