import type { DocumentSummary, DocumentType } from './api/documentApi'
import type { JobPostingItem } from './api/jobPostingApi'
import type { CareerEvidenceSearchResult } from './api/searchApi'
import { isSessionExpiredError } from './auth/sessionPolicy.ts'

const MAX_CONCURRENT_SEARCHES = 3
const MAX_QUERY_VARIANTS = 5
const MAX_RESULTS_PER_ITEM = 5
const EXPLICIT_ALTERNATIVE_SEPARATOR = /\s+(?:또는|\bor\b)\s+/iu
const COMMA_SEPARATOR = /\s*[,，]\s*/u
const SPACED_SLASH_SEPARATOR = /\s+\/\s+/u
const LETTER = /\p{L}/u
const LOWERCASE_PATH_SEGMENT = /^[a-z][a-z0-9._-]*$/u
const MEANINGFUL_QUERY_CHARACTER = /[\p{L}\p{N}+#]/gu
const SINGLE_LETTER_IDENTIFIER = /^[A-Z]$/u

export type JobEvidenceGroupState = 'loading' | 'result' | 'empty' | 'error'

export type JobEvidenceGroup = {
  item: JobPostingItem
  state: JobEvidenceGroupState
  results: CareerEvidenceSearchResult[]
  error: unknown | null
}

export type JobEvidenceSearchDependencies = {
  search: (query: string) => Promise<CareerEvidenceSearchResult[]>
  listDocuments: () => Promise<DocumentSummary[]>
}

export type JobEvidenceSearch = {
  groups: JobEvidenceGroup[]
  documentTypes: Map<number, DocumentType>
  metadataError: unknown | null
}

export type JobPostingItemGroup = {
  section: string | null
  items: JobPostingItem[]
}

export function groupJobPostingItems(
  items: readonly JobPostingItem[],
): JobPostingItemGroup[] {
  const groups: JobPostingItemGroup[] = []
  for (const item of items) {
    const currentGroup = groups.at(-1)
    if (currentGroup !== undefined && currentGroup.section === item.section) {
      currentGroup.items.push(item)
    } else {
      groups.push({ section: item.section, items: [item] })
    }
  }
  return groups
}

export function selectAllJobPostingItems(items: readonly JobPostingItem[]): Set<number> {
  return new Set(items.map((item) => item.itemId))
}

export function clearJobPostingItemSelection(): Set<number> {
  return new Set()
}

export function toggleJobPostingItemSelection(
  selectedItemIds: ReadonlySet<number>,
  itemId: number,
): Set<number> {
  const nextSelection = new Set(selectedItemIds)
  if (nextSelection.has(itemId)) {
    nextSelection.delete(itemId)
  } else {
    nextSelection.add(itemId)
  }
  return nextSelection
}

export function selectedJobPostingItems(
  items: readonly JobPostingItem[],
  selectedItemIds: ReadonlySet<number>,
): JobPostingItem[] {
  return items.filter((item) => selectedItemIds.has(item.itemId))
}

export function selectedJobPostingItemCount(
  items: readonly JobPostingItem[],
  selectedItemIds: ReadonlySet<number>,
): number {
  return selectedJobPostingItems(items, selectedItemIds).length
}

export function jobEvidenceSearchQueries(text: string): string[] {
  const normalizedText = normalizeQueryVariant(text)
  const explicitAlternativeParts = normalizedText.split(EXPLICIT_ALTERNATIVE_SEPARATOR)
  const fragments = explicitAlternativeParts.length > 1
    ? splitExplicitAlternatives(explicitAlternativeParts)
    : splitSlashAlternatives(normalizedText)

  if (fragments.length <= 1) {
    return [text]
  }

  const originalKey = normalizedQueryKey(text)
  const seen = new Set([originalKey])
  const variants: string[] = []
  for (const fragment of fragments) {
    const variant = normalizeQueryVariant(fragment)
    const key = normalizedQueryKey(variant)
    if (!isMeaningfulQueryVariant(variant) || seen.has(key)) {
      continue
    }
    seen.add(key)
    variants.push(variant)
    if (variants.length === MAX_QUERY_VARIANTS) {
      break
    }
  }
  return [text, ...variants]
}

export function mergeJobEvidenceResults(
  resultSets: readonly (readonly CareerEvidenceSearchResult[])[],
): CareerEvidenceSearchResult[] {
  const merged: CareerEvidenceSearchResult[] = []
  const seen = new Set<string>()
  for (const results of resultSets) {
    for (const result of results) {
      const identity = `${result.documentId}:${result.documentVersionId}:${result.chunkId}`
      if (seen.has(identity)) {
        continue
      }
      seen.add(identity)
      merged.push(result)
      if (merged.length === MAX_RESULTS_PER_ITEM) {
        return merged
      }
    }
  }
  return merged
}

export function loadingJobEvidenceGroups(
  items: readonly JobPostingItem[],
  selectedItemIds: ReadonlySet<number>,
): JobEvidenceGroup[] {
  return selectedJobPostingItems(items, selectedItemIds).map((item) => ({
    item,
    state: 'loading',
    results: [],
    error: null,
  }))
}

export async function findJobEvidence(
  items: readonly JobPostingItem[],
  selectedItemIds: ReadonlySet<number>,
  dependencies: JobEvidenceSearchDependencies,
): Promise<JobEvidenceSearch> {
  const selectedItems = selectedJobPostingItems(items, selectedItemIds)
  const groups = await searchSelectedItems(selectedItems, dependencies.search)

  const resultDocumentIds = new Set(
    groups.flatMap((group) => group.results.map((result) => result.documentId)),
  )
  if (resultDocumentIds.size === 0) {
    return {
      groups,
      documentTypes: new Map(),
      metadataError: null,
    }
  }

  try {
    const documents = await dependencies.listDocuments()
    return {
      groups,
      documentTypes: documentTypesForResults(documents, resultDocumentIds),
      metadataError: null,
    }
  } catch (error) {
    if (isSessionExpiredError(error)) {
      throw error
    }
    return {
      groups,
      documentTypes: new Map(),
      metadataError: error,
    }
  }
}

async function searchSelectedItems(
  selectedItems: readonly JobPostingItem[],
  search: JobEvidenceSearchDependencies['search'],
): Promise<JobEvidenceGroup[]> {
  const groups = new Array<JobEvidenceGroup>(selectedItems.length)
  let nextIndex = 0
  let authenticationError: unknown | null = null
  const workerCount = Math.min(MAX_CONCURRENT_SEARCHES, selectedItems.length)

  const workers = Array.from({ length: workerCount }, async () => {
    while (authenticationError === null) {
      const index = nextIndex
      nextIndex += 1
      const item = selectedItems[index]
      if (item === undefined) {
        return
      }

      try {
        const results = await searchJobEvidenceItem(
          item.text,
          search,
          () => authenticationError !== null,
        )
        groups[index] = {
          item,
          state: results.length === 0 ? 'empty' : 'result',
          results,
          error: null,
        }
      } catch (error) {
        if (isSessionExpiredError(error)) {
          authenticationError = error
          throw error
        }
        groups[index] = {
          item,
          state: 'error',
          results: [],
          error,
        }
      }
    }
  })

  await Promise.all(workers)
  return groups
}

async function searchJobEvidenceItem(
  text: string,
  search: JobEvidenceSearchDependencies['search'],
  shouldStop: () => boolean,
): Promise<CareerEvidenceSearchResult[]> {
  const resultSets: CareerEvidenceSearchResult[][] = []
  for (const query of jobEvidenceSearchQueries(text)) {
    if (shouldStop()) {
      break
    }
    resultSets.push(await search(query))
  }
  return mergeJobEvidenceResults(resultSets)
}

function splitExplicitAlternatives(parts: readonly string[]): string[] {
  const fragments: string[] = []
  for (let index = 0; index < parts.length; index += 1) {
    const part = parts[index] ?? ''
    const hasComma = part.includes(',') || part.includes('，')
    const commaParts = index < parts.length - 1 && hasComma
      ? part.split(COMMA_SEPARATOR)
      : [part]
    if (commaParts.length > 1 && !isClearCommaAlternativeList(commaParts)) {
      return []
    }
    fragments.push(...commaParts.flatMap(splitSlashAlternatives))
  }
  return fragments
}

function isClearCommaAlternativeList(parts: readonly string[]): boolean {
  return parts.every((part) => {
    const normalizedPart = normalizeQueryVariant(part)
    return normalizedPart.length > 0
      && [...normalizedPart].length <= 40
      && normalizedPart.split(' ').length <= 2
      && LETTER.test(normalizedPart)
  })
}

function splitSlashAlternatives(value: string): string[] {
  if (!value.includes('/') || value.includes('://')) {
    return [value]
  }
  const spacedParts = value.split(SPACED_SLASH_SEPARATOR)
  if (spacedParts.length > 1) {
    return spacedParts.every((part) => LETTER.test(part)) ? spacedParts : [value]
  }

  const compactParts = value.split('/')
  return compactParts.length >= 3
      && compactParts.every((part) => LETTER.test(part))
      && !looksLikeCompactPath(value, compactParts)
    ? compactParts
    : [value]
}

function looksLikeCompactPath(value: string, parts: readonly string[]): boolean {
  if (/^[A-Za-z]:\//u.test(value)) {
    return true
  }
  const leadingSegments = parts.slice(0, -1).map((part) => (
    normalizeQueryVariant(part).split(' ')[0] ?? ''
  ))
  return leadingSegments.length >= 2
    && leadingSegments.every((segment) => LOWERCASE_PATH_SEGMENT.test(segment))
}

function normalizeQueryVariant(value: string): string {
  return value.trim().replace(/\s+/gu, ' ')
}

function normalizedQueryKey(value: string): string {
  return normalizeQueryVariant(value).toLowerCase()
}

function isMeaningfulQueryVariant(value: string): boolean {
  return (value.match(MEANINGFUL_QUERY_CHARACTER)?.length ?? 0) >= 2
    || SINGLE_LETTER_IDENTIFIER.test(value)
}

function documentTypesForResults(
  documents: readonly DocumentSummary[],
  resultDocumentIds: ReadonlySet<number>,
): Map<number, DocumentType> {
  return new Map(
    documents
      .filter((document) => resultDocumentIds.has(document.documentId))
      .map((document) => [document.documentId, document.documentType as DocumentType]),
  )
}
