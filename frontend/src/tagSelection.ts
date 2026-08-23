import type { Tag, TagUsage } from './api/tagApi'

export function normalizeTagName(value: string): string {
  return value.normalize('NFKC').trim().replace(/\s+/g, ' ').toLocaleLowerCase('en-US')
}

export function addUniqueTag(selected: Tag[], candidate: Tag): Tag[] {
  const normalized = normalizeTagName(candidate.name)
  if (selected.some((tag) => tag.tagId === candidate.tagId || normalizeTagName(tag.name) === normalized)) {
    return selected
  }
  return [...selected, candidate]
}

export function removeTagById(selected: Tag[], tagId: number): Tag[] {
  return selected.filter((tag) => tag.tagId !== tagId)
}

export function canCreateTag(query: string, results: Tag[], selected: Tag[]): boolean {
  const normalized = normalizeTagName(query)
  return normalized !== ''
    && ![...results, ...selected].some((tag) => normalizeTagName(tag.name) === normalized)
}

export function sortTagUsage(tags: TagUsage[]): TagUsage[] {
  return [...tags].sort(
    (left, right) => right.documentCount - left.documentCount || left.name.localeCompare(right.name, 'ko'),
  )
}

export function tagDetailPath(tagId: number | null): string {
  return tagId === null ? '/career-vault/keywords' : `/career-vault/keywords?tagId=${tagId}`
}

export function selectedTagIdFromSearch(search: string): number | null {
  const raw = new URLSearchParams(search).get('tagId')
  if (raw === null || !/^\d+$/.test(raw)) {
    return null
  }
  const value = Number(raw)
  return Number.isSafeInteger(value) && value > 0 ? value : null
}

export type TagUsageLoadState = 'idle' | 'loading' | 'result' | 'empty' | 'error'

export type SelectedTagResolution =
  | { status: 'idle' }
  | { status: 'waiting' }
  | { status: 'unavailable' }
  | { status: 'ready'; tag: TagUsage }

export function resolveSelectedTag(
  selectedTagId: number | null,
  tagState: TagUsageLoadState,
  tags: TagUsage[],
): SelectedTagResolution {
  if (selectedTagId === null) {
    return { status: 'idle' }
  }
  if (tagState === 'idle' || tagState === 'loading') {
    return { status: 'waiting' }
  }
  if (tagState !== 'result') {
    return { status: 'unavailable' }
  }
  const tag = tags.find((candidate) => candidate.tagId === selectedTagId)
  return tag === undefined ? { status: 'unavailable' } : { status: 'ready', tag }
}

export function keywordEvidenceRetryTarget(
  selectedTagId: number | null,
  tagState: TagUsageLoadState,
  tags: TagUsage[],
): 'usage' | 'evidence' {
  return resolveSelectedTag(selectedTagId, tagState, tags).status === 'ready'
    ? 'evidence'
    : 'usage'
}
