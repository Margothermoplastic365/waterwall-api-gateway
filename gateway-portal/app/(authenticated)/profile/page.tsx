'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { apiClient } from '@gateway/shared-ui/lib/api-client';

const IDENTITY_URL = process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081';
const NOTIFICATION_URL = process.env.NEXT_PUBLIC_NOTIFICATION_URL || 'http://localhost:8084';

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token')
    || localStorage.getItem('admin_token')
    || localStorage.getItem('jwt_token');
}

function identityFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(`${IDENTITY_URL}${path}`, { ...options, headers }).then(async (res) => {
    if (!res.ok) {
      const body = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(body.message || `Request failed (${res.status})`);
    }
    if (res.status === 204) return undefined as T;
    return res.json();
  });
}

function notificationFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(`${NOTIFICATION_URL}${path}`, { ...options, headers }).then(async (res) => {
    if (!res.ok) {
      const body = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(body.message || `Request failed (${res.status})`);
    }
    if (res.status === 204) return undefined as T;
    return res.json();
  });
}

interface UserProfile {
  displayName: string;
  email: string;
  phone: string;
  timezone: string;
}

interface MfaStatus {
  totpEnabled: boolean;
  emailOtpEnabled: boolean;
  recoveryCodesRemaining: number;
}

interface TotpSetupResponse {
  qrCodeDataUrl: string;
  secret: string;
  recoveryCodes: string[];
}

interface Webhook {
  id: string;
  url: string;
  active: boolean;
  secret?: string;
  createdAt: string;
}

export default function ProfilePage() {
  const [profile, setProfile] = useState<UserProfile>({ displayName: '', email: '', phone: '', timezone: '' });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Password change
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [changingPassword, setChangingPassword] = useState(false);
  const [passwordError, setPasswordError] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState('');

  // MFA
  const [mfaStatus, setMfaStatus] = useState<MfaStatus | null>(null);
  const [mfaError, setMfaError] = useState('');
  const [mfaSuccess, setMfaSuccess] = useState('');
  const [totpSetup, setTotpSetup] = useState<TotpSetupResponse | null>(null);
  const [totpCode, setTotpCode] = useState('');
  const [verifyingTotp, setVerifyingTotp] = useState(false);
  const [settingUpTotp, setSettingUpTotp] = useState(false);

  // Webhooks
  const [webhooks, setWebhooks] = useState<Webhook[]>([]);
  const [webhookError, setWebhookError] = useState('');
  const [webhookSuccess, setWebhookSuccess] = useState('');
  const [newWebhookUrl, setNewWebhookUrl] = useState('');
  const [addingWebhook, setAddingWebhook] = useState(false);
  const [showWebhookForm, setShowWebhookForm] = useState(false);
  const [newWebhookSecret, setNewWebhookSecret] = useState('');

  const fetchMfaStatus = useCallback(async () => {
    try {
      const data = await identityFetch<MfaStatus>('/v1/mfa/status');
      setMfaStatus(data);
    } catch {
      // MFA status may not be available
    }
  }, []);

  const fetchWebhooks = useCallback(async () => {
    try {
      const data = await notificationFetch<Webhook[]>('/v1/webhooks');
      setWebhooks(Array.isArray(data) ? data : []);
    } catch {
      // Webhooks may not be available
    }
  }, []);

  useEffect(() => {
    async function fetchProfile() {
      try {
        const data = await apiClient<UserProfile>('/v1/users/me');
        setProfile(data);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Failed to load profile');
      } finally {
        setLoading(false);
      }
    }
    fetchProfile();
    fetchMfaStatus();
    fetchWebhooks();
  }, [fetchMfaStatus, fetchWebhooks]);

  const handleSaveProfile = async () => {
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      await apiClient('/v1/users/me', {
        method: 'PUT',
        body: JSON.stringify(profile),
      });
      setSuccess('Profile updated successfully');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update profile');
    } finally {
      setSaving(false);
    }
  };

  const handleChangePassword = async () => {
    setPasswordError('');
    setPasswordSuccess('');

    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match');
      return;
    }
    if (newPassword.length < 8) {
      setPasswordError('Password must be at least 8 characters');
      return;
    }

    setChangingPassword(true);
    try {
      await apiClient('/v1/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify({ oldPassword, newPassword }),
      });
      setPasswordSuccess('Password changed successfully');
      setOldPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: unknown) {
      setPasswordError(err instanceof Error ? err.message : 'Failed to change password');
    } finally {
      setChangingPassword(false);
    }
  };

  if (loading) return <p>Loading...</p>;

  const inputStyle: React.CSSProperties = {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #cbd5e1',
    borderRadius: 6,
    fontSize: 14,
    boxSizing: 'border-box',
  };

  const labelStyle: React.CSSProperties = {
    display: 'block',
    fontSize: 14,
    fontWeight: 500,
    marginBottom: 4,
    color: '#334155',
  };

  const sectionStyle: React.CSSProperties = {
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e2e8f0',
    padding: 24,
    marginBottom: 20,
  };

  return (
    <div style={{ maxWidth: 640 }}>
      <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', marginBottom: 24 }}>Profile</h1>

      {/* Profile Info */}
      <div style={sectionStyle}>
        <h2 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 16 }}>
          Profile Information
        </h2>

        {error && <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{error}</p>}
        {success && <p style={{ color: '#10b981', fontSize: 14, marginBottom: 12 }}>{success}</p>}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={labelStyle}>Display Name</label>
            <input
              type="text"
              value={profile.displayName}
              onChange={(e) => setProfile({ ...profile, displayName: e.target.value })}
              style={inputStyle}
            />
          </div>
          <div>
            <label style={labelStyle}>Email</label>
            <input
              type="email"
              value={profile.email}
              onChange={(e) => setProfile({ ...profile, email: e.target.value })}
              style={inputStyle}
            />
          </div>
          <div>
            <label style={labelStyle}>Phone</label>
            <input
              type="tel"
              value={profile.phone}
              onChange={(e) => setProfile({ ...profile, phone: e.target.value })}
              style={inputStyle}
            />
          </div>
          <div>
            <label style={labelStyle}>Timezone</label>
            <input
              type="text"
              value={profile.timezone}
              onChange={(e) => setProfile({ ...profile, timezone: e.target.value })}
              placeholder="e.g. America/New_York"
              style={inputStyle}
            />
          </div>
          <div>
            <button
              onClick={handleSaveProfile}
              disabled={saving}
              style={{
                padding: '8px 20px',
                backgroundColor: saving ? '#94a3b8' : '#3b82f6',
                color: '#fff',
                border: 'none',
                borderRadius: 6,
                fontSize: 14,
                fontWeight: 500,
                cursor: saving ? 'not-allowed' : 'pointer',
              }}
            >
              {saving ? 'Saving...' : 'Save Profile'}
            </button>
          </div>
        </div>
      </div>

      {/* Change Password */}
      <div style={sectionStyle}>
        <h2 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 16 }}>
          Change Password
        </h2>

        {passwordError && <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{passwordError}</p>}
        {passwordSuccess && <p style={{ color: '#10b981', fontSize: 14, marginBottom: 12 }}>{passwordSuccess}</p>}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={labelStyle}>Current Password</label>
            <input
              type="password"
              value={oldPassword}
              onChange={(e) => setOldPassword(e.target.value)}
              style={inputStyle}
            />
          </div>
          <div>
            <label style={labelStyle}>New Password</label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              style={inputStyle}
            />
          </div>
          <div>
            <label style={labelStyle}>Confirm New Password</label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              style={inputStyle}
            />
          </div>
          <div>
            <button
              onClick={handleChangePassword}
              disabled={changingPassword}
              style={{
                padding: '8px 20px',
                backgroundColor: changingPassword ? '#94a3b8' : '#3b82f6',
                color: '#fff',
                border: 'none',
                borderRadius: 6,
                fontSize: 14,
                fontWeight: 500,
                cursor: changingPassword ? 'not-allowed' : 'pointer',
              }}
            >
              {changingPassword ? 'Changing...' : 'Change Password'}
            </button>
          </div>
        </div>
      </div>

      {/* Two-Factor Authentication */}
      <div style={sectionStyle}>
        <h2 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 16 }}>
          Two-Factor Authentication
        </h2>

        {mfaError && <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{mfaError}</p>}
        {mfaSuccess && <p style={{ color: '#10b981', fontSize: 14, marginBottom: 12 }}>{mfaSuccess}</p>}

        {mfaStatus && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {/* TOTP Status */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <span style={{ fontSize: 14, fontWeight: 500, color: '#334155' }}>Authenticator App (TOTP)</span>
                {mfaStatus.totpEnabled ? (
                  <span
                    style={{
                      display: 'inline-block',
                      marginLeft: 10,
                      padding: '2px 10px',
                      borderRadius: 9999,
                      fontSize: 12,
                      fontWeight: 600,
                      backgroundColor: '#dcfce7',
                      color: '#166534',
                    }}
                  >
                    Enabled
                  </span>
                ) : (
                  <span
                    style={{
                      display: 'inline-block',
                      marginLeft: 10,
                      padding: '2px 10px',
                      borderRadius: 9999,
                      fontSize: 12,
                      fontWeight: 600,
                      backgroundColor: '#f1f5f9',
                      color: '#475569',
                    }}
                  >
                    Disabled
                  </span>
                )}
              </div>
              {mfaStatus.totpEnabled ? (
                <button
                  onClick={async () => {
                    setMfaError('');
                    setMfaSuccess('');
                    try {
                      await identityFetch('/v1/mfa/totp/disable', { method: 'POST' });
                      setMfaSuccess('TOTP disabled successfully');
                      fetchMfaStatus();
                      setTotpSetup(null);
                    } catch (err: unknown) {
                      setMfaError(err instanceof Error ? err.message : 'Failed to disable TOTP');
                    }
                  }}
                  style={{
                    padding: '6px 14px',
                    fontSize: 13,
                    backgroundColor: '#ef4444',
                    color: '#fff',
                    border: 'none',
                    borderRadius: 6,
                    cursor: 'pointer',
                  }}
                >
                  Disable
                </button>
              ) : (
                <button
                  onClick={async () => {
                    setMfaError('');
                    setMfaSuccess('');
                    setSettingUpTotp(true);
                    try {
                      const data = await identityFetch<TotpSetupResponse>('/v1/mfa/totp/setup', { method: 'POST' });
                      setTotpSetup(data);
                    } catch (err: unknown) {
                      setMfaError(err instanceof Error ? err.message : 'Failed to start TOTP setup');
                    } finally {
                      setSettingUpTotp(false);
                    }
                  }}
                  disabled={settingUpTotp}
                  style={{
                    padding: '6px 14px',
                    fontSize: 13,
                    backgroundColor: settingUpTotp ? '#94a3b8' : '#3b82f6',
                    color: '#fff',
                    border: 'none',
                    borderRadius: 6,
                    cursor: settingUpTotp ? 'not-allowed' : 'pointer',
                  }}
                >
                  {settingUpTotp ? 'Setting up...' : 'Setup Authenticator'}
                </button>
              )}
            </div>

            {/* TOTP Setup Flow */}
            {totpSetup && !mfaStatus.totpEnabled && (
              <div
                style={{
                  backgroundColor: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: 8,
                  padding: 20,
                }}
              >
                <h4 style={{ fontSize: 14, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 12 }}>
                  Setup Authenticator App
                </h4>
                <p style={{ fontSize: 13, color: '#64748b', marginBottom: 12 }}>
                  Scan this QR code with your authenticator app (Google Authenticator, Authy, etc.)
                </p>
                <div style={{ textAlign: 'center', marginBottom: 16 }}>
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={totpSetup.qrCodeDataUrl}
                    alt="TOTP QR Code"
                    style={{ width: 200, height: 200, border: '1px solid #e2e8f0', borderRadius: 8 }}
                  />
                </div>
                <div style={{ marginBottom: 12 }}>
                  <label style={labelStyle}>Secret Key (manual entry)</label>
                  <code
                    style={{
                      display: 'block',
                      padding: '8px 12px',
                      backgroundColor: '#fff',
                      border: '1px solid #e2e8f0',
                      borderRadius: 6,
                      fontSize: 13,
                      fontFamily: 'monospace',
                      wordBreak: 'break-all',
                    }}
                  >
                    {totpSetup.secret}
                  </code>
                </div>
                {totpSetup.recoveryCodes && totpSetup.recoveryCodes.length > 0 && (
                  <div style={{ marginBottom: 12 }}>
                    <label style={labelStyle}>Recovery Codes (save these securely)</label>
                    <div
                      style={{
                        padding: 12,
                        backgroundColor: '#fff',
                        border: '1px solid #e2e8f0',
                        borderRadius: 6,
                        fontFamily: 'monospace',
                        fontSize: 13,
                        display: 'grid',
                        gridTemplateColumns: '1fr 1fr',
                        gap: 4,
                      }}
                    >
                      {totpSetup.recoveryCodes.map((code, i) => (
                        <span key={i}>{code}</span>
                      ))}
                    </div>
                  </div>
                )}
                <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
                  <div style={{ flex: 1 }}>
                    <label style={labelStyle}>Enter 6-digit code to verify</label>
                    <input
                      type="text"
                      value={totpCode}
                      onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                      placeholder="000000"
                      maxLength={6}
                      style={inputStyle}
                    />
                  </div>
                  <button
                    onClick={async () => {
                      setMfaError('');
                      setVerifyingTotp(true);
                      try {
                        await identityFetch('/v1/mfa/totp/verify', {
                          method: 'POST',
                          body: JSON.stringify({ code: totpCode }),
                        });
                        setMfaSuccess('Authenticator enabled successfully');
                        setTotpSetup(null);
                        setTotpCode('');
                        fetchMfaStatus();
                      } catch (err: unknown) {
                        setMfaError(err instanceof Error ? err.message : 'Invalid code');
                      } finally {
                        setVerifyingTotp(false);
                      }
                    }}
                    disabled={totpCode.length !== 6 || verifyingTotp}
                    style={{
                      padding: '8px 20px',
                      backgroundColor: totpCode.length !== 6 || verifyingTotp ? '#94a3b8' : '#3b82f6',
                      color: '#fff',
                      border: 'none',
                      borderRadius: 6,
                      fontSize: 14,
                      fontWeight: 500,
                      cursor: totpCode.length !== 6 || verifyingTotp ? 'not-allowed' : 'pointer',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {verifyingTotp ? 'Verifying...' : 'Verify & Enable'}
                  </button>
                </div>
              </div>
            )}

            {/* Recovery Codes */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <span style={{ fontSize: 14, fontWeight: 500, color: '#334155' }}>Recovery Codes</span>
                <span style={{ fontSize: 13, color: '#64748b', marginLeft: 8 }}>
                  {mfaStatus.recoveryCodesRemaining} remaining
                </span>
              </div>
              <button
                onClick={async () => {
                  setMfaError('');
                  setMfaSuccess('');
                  try {
                    const data = await identityFetch<{ recoveryCodes: string[] }>('/v1/mfa/recovery-codes/regenerate', {
                      method: 'POST',
                    });
                    setMfaSuccess(`Recovery codes regenerated: ${data.recoveryCodes.join(', ')}`);
                    fetchMfaStatus();
                  } catch (err: unknown) {
                    setMfaError(err instanceof Error ? err.message : 'Failed to regenerate codes');
                  }
                }}
                style={{
                  padding: '6px 14px',
                  fontSize: 13,
                  backgroundColor: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  borderRadius: 6,
                  cursor: 'pointer',
                  color: '#475569',
                }}
              >
                Regenerate
              </button>
            </div>

            {/* Email OTP */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <span style={{ fontSize: 14, fontWeight: 500, color: '#334155' }}>Email OTP</span>
                {mfaStatus.emailOtpEnabled ? (
                  <span
                    style={{
                      display: 'inline-block',
                      marginLeft: 10,
                      padding: '2px 10px',
                      borderRadius: 9999,
                      fontSize: 12,
                      fontWeight: 600,
                      backgroundColor: '#dcfce7',
                      color: '#166534',
                    }}
                  >
                    Enabled
                  </span>
                ) : (
                  <span
                    style={{
                      display: 'inline-block',
                      marginLeft: 10,
                      padding: '2px 10px',
                      borderRadius: 9999,
                      fontSize: 12,
                      fontWeight: 600,
                      backgroundColor: '#f1f5f9',
                      color: '#475569',
                    }}
                  >
                    Disabled
                  </span>
                )}
              </div>
              <button
                onClick={async () => {
                  setMfaError('');
                  setMfaSuccess('');
                  try {
                    await identityFetch('/v1/mfa/email/send', { method: 'POST' });
                    setMfaSuccess('Test email OTP sent successfully');
                  } catch (err: unknown) {
                    setMfaError(err instanceof Error ? err.message : 'Failed to send test code');
                  }
                }}
                style={{
                  padding: '6px 14px',
                  fontSize: 13,
                  backgroundColor: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  borderRadius: 6,
                  cursor: 'pointer',
                  color: '#475569',
                }}
              >
                Send test code
              </button>
            </div>
          </div>
        )}

        {!mfaStatus && (
          <p style={{ color: '#64748b', fontSize: 14, margin: 0 }}>Loading MFA status...</p>
        )}
      </div>

      {/* Webhook Endpoints */}
      <div style={sectionStyle}>
        <h2 style={{ fontSize: 16, fontWeight: 600, color: '#334155', marginTop: 0, marginBottom: 16 }}>
          Webhook Endpoints
        </h2>

        {webhookError && <p style={{ color: '#dc2626', fontSize: 14, marginBottom: 12 }}>{webhookError}</p>}
        {webhookSuccess && <p style={{ color: '#10b981', fontSize: 14, marginBottom: 12 }}>{webhookSuccess}</p>}

        {webhooks.length > 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
            {webhooks.map((wh) => (
              <div
                key={wh.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '10px 14px',
                  backgroundColor: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: 6,
                }}
              >
                <div>
                  <code style={{ fontSize: 13, color: '#0f172a', wordBreak: 'break-all' }}>{wh.url}</code>
                  <div style={{ display: 'flex', gap: 8, marginTop: 4, alignItems: 'center' }}>
                    <span
                      style={{
                        display: 'inline-block',
                        padding: '1px 8px',
                        borderRadius: 9999,
                        fontSize: 11,
                        fontWeight: 600,
                        backgroundColor: wh.active ? '#dcfce7' : '#fee2e2',
                        color: wh.active ? '#166534' : '#991b1b',
                      }}
                    >
                      {wh.active ? 'Active' : 'Inactive'}
                    </span>
                    <span style={{ fontSize: 12, color: '#94a3b8' }}>
                      Created {new Date(wh.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>
                <button
                  onClick={async () => {
                    setWebhookError('');
                    setWebhookSuccess('');
                    try {
                      await notificationFetch(`/v1/webhooks/${wh.id}`, { method: 'DELETE' });
                      setWebhookSuccess('Webhook deleted');
                      fetchWebhooks();
                    } catch (err: unknown) {
                      setWebhookError(err instanceof Error ? err.message : 'Failed to delete webhook');
                    }
                  }}
                  style={{
                    padding: '4px 10px',
                    fontSize: 12,
                    backgroundColor: '#ef4444',
                    color: '#fff',
                    border: 'none',
                    borderRadius: 4,
                    cursor: 'pointer',
                    flexShrink: 0,
                  }}
                >
                  Delete
                </button>
              </div>
            ))}
          </div>
        ) : (
          <p style={{ color: '#64748b', fontSize: 14, marginBottom: 16 }}>No webhooks configured.</p>
        )}

        {showWebhookForm ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div>
              <label style={labelStyle}>Webhook URL</label>
              <input
                type="url"
                value={newWebhookUrl}
                onChange={(e) => setNewWebhookUrl(e.target.value)}
                placeholder="https://example.com/webhook"
                style={inputStyle}
              />
            </div>
            {newWebhookSecret && (
              <div>
                <label style={labelStyle}>Webhook Secret (save this, shown only once)</label>
                <code
                  style={{
                    display: 'block',
                    padding: '8px 12px',
                    backgroundColor: '#f8fafc',
                    border: '1px solid #e2e8f0',
                    borderRadius: 6,
                    fontSize: 13,
                    fontFamily: 'monospace',
                    wordBreak: 'break-all',
                  }}
                >
                  {newWebhookSecret}
                </code>
              </div>
            )}
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                onClick={async () => {
                  if (!newWebhookUrl.trim()) return;
                  setWebhookError('');
                  setWebhookSuccess('');
                  setAddingWebhook(true);
                  try {
                    const data = await notificationFetch<Webhook>('/v1/webhooks', {
                      method: 'POST',
                      body: JSON.stringify({ url: newWebhookUrl }),
                    });
                    if (data.secret) {
                      setNewWebhookSecret(data.secret);
                      setWebhookSuccess('Webhook created. Save the secret below.');
                    } else {
                      setWebhookSuccess('Webhook created successfully');
                      setShowWebhookForm(false);
                      setNewWebhookUrl('');
                    }
                    fetchWebhooks();
                  } catch (err: unknown) {
                    setWebhookError(err instanceof Error ? err.message : 'Failed to add webhook');
                  } finally {
                    setAddingWebhook(false);
                  }
                }}
                disabled={addingWebhook || !newWebhookUrl.trim()}
                style={{
                  padding: '8px 20px',
                  backgroundColor: addingWebhook || !newWebhookUrl.trim() ? '#94a3b8' : '#3b82f6',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 6,
                  fontSize: 14,
                  fontWeight: 500,
                  cursor: addingWebhook || !newWebhookUrl.trim() ? 'not-allowed' : 'pointer',
                }}
              >
                {addingWebhook ? 'Adding...' : 'Add Webhook'}
              </button>
              <button
                onClick={() => {
                  setShowWebhookForm(false);
                  setNewWebhookUrl('');
                  setNewWebhookSecret('');
                }}
                style={{
                  padding: '8px 20px',
                  backgroundColor: '#f1f5f9',
                  color: '#475569',
                  border: '1px solid #cbd5e1',
                  borderRadius: 6,
                  fontSize: 14,
                  fontWeight: 500,
                  cursor: 'pointer',
                }}
              >
                Cancel
              </button>
            </div>
          </div>
        ) : (
          <button
            onClick={() => {
              setShowWebhookForm(true);
              setNewWebhookSecret('');
            }}
            style={{
              padding: '8px 20px',
              backgroundColor: '#3b82f6',
              color: '#fff',
              border: 'none',
              borderRadius: 6,
              fontSize: 14,
              fontWeight: 500,
              cursor: 'pointer',
            }}
          >
            Add Webhook
          </button>
        )}
      </div>
    </div>
  );
}
