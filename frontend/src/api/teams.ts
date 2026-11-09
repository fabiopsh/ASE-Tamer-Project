/** teams service: /api/teams (docs/plan/06-teams.md). */
import { request } from './client';
import type { Team, TeamRequest, TeamValidation, Uuid } from './types';

export function listTeams(signal?: AbortSignal): Promise<Team[]> {
  return request<Team[]>('/api/teams', { signal });
}

export function getTeam(teamId: Uuid, signal?: AbortSignal): Promise<Team> {
  return request<Team>(`/api/teams/${encodeURIComponent(teamId)}`, { signal });
}

/** Invalid: ApiError 400 TEAM_INVALID with details. At most 5 teams per run (TEAM_LIMIT). */
export function createTeam(body: TeamRequest): Promise<Team> {
  return request<Team>('/api/teams', { method: 'POST', body });
}

/** Replaces name, members and order. */
export function updateTeam(teamId: Uuid, body: TeamRequest): Promise<Team> {
  return request<Team>(`/api/teams/${encodeURIComponent(teamId)}`, { method: 'PUT', body });
}

export function deleteTeam(teamId: Uuid): Promise<void> {
  return request<void>(`/api/teams/${encodeURIComponent(teamId)}`, { method: 'DELETE' });
}

/** Never saves; answers 200 even when invalid. Call it on every editor change. */
export function validateTeam(members: Uuid[], signal?: AbortSignal): Promise<TeamValidation> {
  return request<TeamValidation>('/api/teams/validate', { method: 'POST', body: { members }, signal });
}
