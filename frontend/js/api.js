// fetch wrapper: Bearer token, JSON errors, 401 -> logout
const KEY = 'mt_token';
export const getToken = () => localStorage.getItem(KEY);
export const setToken = t => (t ? localStorage.setItem(KEY, t) : localStorage.removeItem(KEY));
// shared state: profile (from /players/me), result (battle result handed to the battle view)
export const state = { profile: null, result: null };
export const bus = new EventTarget();

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `Request failed (${status})`);
    this.status = status;
    this.code = body?.error;
    this.details = body?.details || [];
  }
}
export const errText = e => e.message + (e.details?.length ? ': ' + e.details.join('; ') : '');

export function logout() {
  setToken(null);
  state.profile = null;
  location.hash = '#/login';
}

export async function api(path, { method = 'GET', body, query } = {}) {
  const qs = query ? '?' + new URLSearchParams(Object.entries(query).filter(([, v]) => v !== '' && v != null)) : '';
  const headers = {};
  if (body) headers['Content-Type'] = 'application/json';
  if (getToken()) headers.Authorization = 'Bearer ' + getToken();
  let res;
  try {
    res = await fetch('/api' + path + qs, { method, headers, body: body ? JSON.stringify(body) : undefined });
  } catch {
    throw new ApiError(0, { message: 'Server unreachable, try again later' });
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && !path.startsWith('/auth/login')) logout();
    throw new ApiError(res.status, data);
  }
  return data;
}

export async function loadProfile() {
  state.profile = await api('/players/me');
  return state.profile;
}
