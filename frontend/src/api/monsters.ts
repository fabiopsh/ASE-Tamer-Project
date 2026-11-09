/** monsters service: catalogue, abilities, elements, collection (docs/plan/05-monsters.md). */
import { request } from './client';
import type {
  Ability,
  CatalogueQuery,
  ElementChart,
  MonsterDetail,
  MonsterSummary,
  OwnedMonster,
  Page,
  Uuid,
} from './types';

export function listMonsters(q: CatalogueQuery = {}, signal?: AbortSignal): Promise<Page<MonsterSummary>> {
  return request<Page<MonsterSummary>>('/api/monsters', {
    query: { element: q.element, rarity: q.rarity, ids: q.ids, page: q.page, size: q.size },
    signal,
  });
}

/** Details for a set of ids (max 50), e.g. the starter pool. Items come back ordered by monster_id. */
export async function getMonstersByIds(ids: number[], signal?: AbortSignal): Promise<MonsterSummary[]> {
  if (ids.length === 0) return [];
  const page = await listMonsters({ ids, size: 50 }, signal);
  return page.items;
}

export function getMonster(id: number, signal?: AbortSignal): Promise<MonsterDetail> {
  return request<MonsterDetail>(`/api/monsters/${id}`, { signal });
}

export function listAbilities(signal?: AbortSignal): Promise<Ability[]> {
  return request<Ability[]>('/api/abilities', { signal });
}

export function getAbility(id: number, signal?: AbortSignal): Promise<Ability> {
  return request<Ability>(`/api/abilities/${id}`, { signal });
}

export function getElements(signal?: AbortSignal): Promise<ElementChart> {
  return request<ElementChart>('/api/elements', { signal });
}

export interface CollectionQuery {
  /** Omit for the current run (active instances); set it for an archived past run. */
  run_id?: Uuid;
  page?: number;
  size?: number;
}

export function listCollection(q: CollectionQuery = {}, signal?: AbortSignal): Promise<Page<OwnedMonster>> {
  return request<Page<OwnedMonster>>('/api/collection', {
    query: { run_id: q.run_id, page: q.page, size: q.size },
    signal,
  });
}

export function getOwnedMonster(ownedId: Uuid, signal?: AbortSignal): Promise<OwnedMonster> {
  return request<OwnedMonster>(`/api/collection/${encodeURIComponent(ownedId)}`, { signal });
}
