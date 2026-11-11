/**
 * fetch wrapper for the gateway API (docs/plan/09-frontend.md).
 *
 * - Base URL from VITE_GATEWAY_URL (default https://localhost:8443). "/" means same origin: the Docker image
 *   is served by the gateway, so every call is relative.
 * - JSON bodies by default; `form` bodies are sent as application/x-www-form-urlencoded (token endpoint).
 * - Errors are thrown as ApiError, parsed from {error, message, details} or the OAuth2 format.
 * - A 401 on an authenticated call runs the handler registered by the SessionProvider
 *   (clear session, go to /login).
 */
import type { ErrorDetail } from './types';

export const GATEWAY_URL: string = (import.meta.env.VITE_GATEWAY_URL ?? 'https://localhost:8443').replace(
  /\/+$/,
  '',
);

/** Sprite URLs from the API are relative (/images/monsters/001.png): prefix the gateway. */
export function assetUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) return path;
  return `${GATEWAY_URL}${path.startsWith('/') ? '' : '/'}${path}`;
}

/** Sprite URL for a monster id when the payload has no image_url (e.g. battle team members). */
export function spriteUrlFor(monsterId: number): string {
  return assetUrl(`/images/monsters/${String(monsterId).padStart(3, '0')}.png`);
}

export class ApiError extends Error {
  /** HTTP status; 0 when the gateway could not be reached. */
  readonly status: number;
  /** Error code: `error` of the body (e.g. TEAM_INVALID, invalid_grant), NETWORK_ERROR, or HTTP_<status>. */
  readonly code: string;
  readonly details: ErrorDetail[];

  constructor(status: number, code: string, message: string, details: ErrorDetail[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }

  get isNetwork(): boolean {
    return this.status === 0;
  }
  get isNotFound(): boolean {
    return this.status === 404;
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError;
}

/** Human-readable message for any thrown value. */
export function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message;
  if (e instanceof Error) return e.message;
  return 'Something went wrong.';
}

// ---------------------------------------------------------------- token + 401 hooks

let tokenProvider: () => string | null = () => null;
let unauthorizedHandler: () => void = () => {};

/** Registered by the SessionProvider. */
export function configureClient(opts: { getToken: () => string | null; onUnauthorized: () => void }): void {
  tokenProvider = opts.getToken;
  unauthorizedHandler = opts.onUnauthorized;
}

// ---------------------------------------------------------------- request

type Query = Record<string, string | number | boolean | undefined | null | (string | number)[]>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  query?: Query;
  /** JSON body. */
  body?: unknown;
  /** Form body (application/x-www-form-urlencoded). */
  form?: Record<string, string>;
  /** Send the bearer token (default true). */
  auth?: boolean;
  signal?: AbortSignal;
}

function buildUrl(path: string, query?: Query): string {
  // The base is used only when GATEWAY_URL is empty (app served by the gateway itself)
  const url = new URL(`${GATEWAY_URL}${path}`, window.location.origin);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue;
      if (Array.isArray(value)) {
        if (value.length > 0) url.searchParams.set(key, value.join(','));
      } else {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.toString();
}

async function parseError(res: Response): Promise<ApiError> {
  let body: unknown;
  try {
    const text = await res.text();
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null;
  }
  if (body && typeof body === 'object') {
    const b = body as Record<string, unknown>;
    const code = typeof b.error === 'string' ? b.error : `HTTP_${res.status}`;
    // OAuth2 errors carry error_description; the conventions use message.
    const message =
      (typeof b.message === 'string' && b.message) ||
      (typeof b.error_description === 'string' && b.error_description) ||
      defaultMessage(res.status);
    const details = Array.isArray(b.details) ? (b.details as ErrorDetail[]) : [];
    return new ApiError(res.status, code, message, details);
  }
  return new ApiError(res.status, `HTTP_${res.status}`, defaultMessage(res.status));
}

function defaultMessage(status: number): string {
  switch (status) {
    case 400:
      return 'The request was not accepted.';
    case 401:
      return 'Your session has expired. Please log in again.';
    case 403:
      return 'You are not allowed to do this.';
    case 404:
      return 'Not found.';
    case 409:
      return 'This action conflicts with the current state.';
    case 429:
      return 'Too many requests. Please wait a moment and try again.';
    case 503:
      return 'A game service is temporarily unavailable. Please try again.';
    default:
      return status >= 500 ? 'The server had a problem. Please try again.' : `Request failed (${status}).`;
  }
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = 'GET', query, body, form, auth = true, signal } = opts;
  const headers: Record<string, string> = { Accept: 'application/json' };
  let payload: BodyInit | undefined;

  if (form) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded';
    payload = new URLSearchParams(form).toString();
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  const token = auth ? tokenProvider() : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(buildUrl(path, query), { method, headers, body: payload, signal });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError(
      0,
      'NETWORK_ERROR',
      `Cannot reach the game server at ${GATEWAY_URL || window.location.origin}. Is it running, and is its certificate accepted?`,
    );
  }

  if (!res.ok) {
    const err = await parseError(res);
    if (res.status === 401 && auth) unauthorizedHandler();
    throw err;
  }
  if (res.status === 204 || res.status === 202) {
    const text = await res.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }
  return (await res.json()) as T;
}

/**
 * Like request(), but resolves to null on 404. Use for "may not exist yet" resources such as
 * GET /api/runs/current or GET /api/battles/current.
 */
export async function requestOrNull<T>(path: string, opts: RequestOptions = {}): Promise<T | null> {
  try {
    return await request<T>(path, opts);
  } catch (e) {
    if (isApiError(e) && e.status === 404) return null;
    throw e;
  }
}
