'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';

// ─── Types ───────────────────────────────────────────────────
export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
  permissions: string[];
}

interface JwtPayload {
  sub: string;
  email: string;
  displayName?: string;
  roles?: string[];
  permissions?: string[];
  exp: number;
  iat: number;
}

interface LoginResponse {
  token: string;
  refreshToken?: string;
  user: AuthUser;
}

const TOKEN_KEY = 'token';

// ─── Token management ────────────────────────────────────────
export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY)
    || localStorage.getItem('admin_token')
    || localStorage.getItem('jwt_token');
}

function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

function clearToken(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem('admin_token');
  localStorage.removeItem('jwt_token');
}

// ─── JWT decoding ────────────────────────────────────────────
function decodeJwt(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const payload = parts[1];
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decoded) as JwtPayload;
  } catch {
    return null;
  }
}

// ─── Public functions ────────────────────────────────────────
export function getUser(): AuthUser | null {
  const token = getToken();
  if (!token) return null;
  const payload = decodeJwt(token);
  if (!payload) return null;
  return {
    id: payload.sub,
    email: payload.email,
    displayName: payload.displayName ?? payload.email,
    roles: payload.roles ?? [],
    permissions: payload.permissions ?? [],
  };
}

export function isAuthenticated(): boolean {
  const token = getToken();
  if (!token) return false;
  const payload = decodeJwt(token);
  if (!payload) return false;
  // Check expiration (exp is in seconds)
  return payload.exp * 1000 > Date.now();
}

const IDENTITY_URL = typeof window !== 'undefined'
  ? (process.env.NEXT_PUBLIC_IDENTITY_URL || 'http://localhost:8081')
  : 'http://localhost:8081';

export async function login(email: string, password: string): Promise<AuthUser> {
  const res = await fetch(`${IDENTITY_URL}/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message || `Login failed (${res.status})`);
  }
  const data = await res.json();
  const jwt = data.accessToken || data.token;
  if (jwt) setToken(jwt);
  return data.user ?? getUser()!;
}

export async function register(
  email: string,
  password: string,
  displayName: string,
): Promise<AuthUser> {
  const res = await fetch(`${IDENTITY_URL}/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, displayName }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message || `Registration failed (${res.status})`);
  }
  const data = await res.json();
  const jwt = data.accessToken || data.token;
  if (jwt) setToken(jwt);
  return data.user ?? getUser()!;
}

export function logout(): void {
  clearToken();
  if (typeof window !== 'undefined') {
    window.location.href = '/auth/login';
  }
}

// ─── React hook ──────────────────────────────────────────────
export interface UseAuthReturn {
  user: AuthUser | null;
  token: string | null;
  permissions: string[];
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => void;
}

export function useAuth(): UseAuthReturn {
  const [token, setTokenState] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);

  // Initialize from localStorage on mount
  useEffect(() => {
    const storedToken = getToken();
    if (storedToken && isAuthenticated()) {
      setTokenState(storedToken);
      setUser(getUser());
    }
  }, []);

  const permissions = useMemo(
    () => user?.permissions ?? [],
    [user],
  );

  const authenticated = useMemo(
    () => !!token && !!user,
    [token, user],
  );

  const doLogin = useCallback(async (email: string, password: string) => {
    const loggedInUser = await login(email, password);
    setTokenState(getToken());
    setUser(loggedInUser);
    return loggedInUser;
  }, []);

  const doLogout = useCallback(() => {
    logout();
    setTokenState(null);
    setUser(null);
  }, []);

  return {
    user,
    token,
    permissions,
    isAuthenticated: authenticated,
    login: doLogin,
    logout: doLogout,
  };
}
