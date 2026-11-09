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
