/** history service: /api/history (docs/plan/08-history.md, backend/history/.../api/HistoryResource.java). */
import { request } from './client';
import type { HistoryQuery, HistoryRecord, HistorySummary, Page, Uuid } from './types';

export function listHistory(q: HistoryQuery = {}, signal?: AbortSignal): Promise<Page<HistorySummary>> {
  return request<Page<HistorySummary>>('/api/history', {
    query: { run_id: q.run_id, outcome: q.outcome, page: q.page, size: q.size },
    signal,
  });
}

export function getHistoryRecord(encounterId: Uuid, signal?: AbortSignal): Promise<HistoryRecord> {
  return request<HistoryRecord>(`/api/history/${encodeURIComponent(encounterId)}`, { signal });
}

export interface HistoryVerification {
  valid: boolean;
  records: number;
  first_invalid?: Uuid;
}

/** P2 (US-25). */
export function verifyHistory(signal?: AbortSignal): Promise<HistoryVerification> {
  return request<HistoryVerification>('/api/history/verify', { signal });
}
