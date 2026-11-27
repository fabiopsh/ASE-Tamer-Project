/** auth service: /api/auth/* (docs/plan/03-auth.md). */
import { request } from './client';
import type { RegisterRequest, RegisterResponse, TokenResponse } from './types';

export function register(body: RegisterRequest): Promise<RegisterResponse> {
  return request<RegisterResponse>('/api/auth/register', { method: 'POST', body, auth: false });
}

/** OAuth2 password grant, form-urlencoded. Wrong credentials: ApiError code `invalid_grant` (400). */
export function login(username: string, password: string): Promise<TokenResponse> {
  return request<TokenResponse>('/api/auth/token', {
    method: 'POST',
    form: { grant_type: 'password', username, password },
    auth: false,
  });
}

/**
 * OAuth2 refresh_token grant, form-urlencoded.
 * Never attaches Authorization and never auto-retries on error.
 */
export function refreshToken(token: string): Promise<TokenResponse> {
  return request<TokenResponse>('/api/auth/token', {
    method: 'POST',
    form: { grant_type: 'refresh_token', refresh_token: token },
    auth: false,
  });
}

/** Server-side refresh token revocation. Never attaches Authorization. */
export function logoutServer(token: string): Promise<void> {
  return request<void>('/api/auth/logout', {
    method: 'POST',
    form: { refresh_token: token },
    auth: false,
  });
}
