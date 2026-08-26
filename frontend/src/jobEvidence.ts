import type { DocumentSummary, DocumentType } from './api/documentApi'
import type { JobPostingItem } from './api/jobPostingApi'
import type { CareerEvidenceSearchResult } from './api/searchApi'
import { isSessionExpiredError } from './auth/sessionPolicy.ts'

const MAX_CONCURRENT_SEARCHES = 3
const MAX_QUERY_VARIANTS = 5
const MAX_RESULTS_PER_ITEM = 5
const EXPLICIT_ALTERNATIVE_SEPARATOR = /\s+(?:또는|\bor\b)\s+/iu
const COMMA_SEPARATOR = /\s*[,，]\s*/u
const COUNTED_ALTERNATIVE_SELECTION = /\s+중\s+(?:\d{1,2}\s*개|하나)\s*이상(?:\s+.*)?$/u
const ENUMERATED_ALTERNATIVE_SELECTION = /\s+등(?:의)?(?:\s+.*)?$/u
const SPACED_SLASH_SEPARATOR = /\s+\/\s+/u
const LETTER = /\p{L}/u
const LOWERCASE_PATH_SEGMENT = /^[a-z][a-z0-9._-]*$/u
const MEANINGFUL_QUERY_CHARACTER = /[\p{L}\p{N}+#]/gu
const SINGLE_LETTER_IDENTIFIER = /^[A-Z]$/u
const DIRECT_IDENTIFIER_VARIANT = /^(?:\.[A-Za-z0-9]|[A-Za-z0-9])[A-Za-z0-9+#._-]*(?:\s+[A-Za-z0-9][A-Za-z0-9+#._-]*)?$/u

export type JobEvidenceGroupState = 'loading' | 'result' | 'empty' | 'error'

export type JobEvidenceGroup = {
  item: JobPostingItem
  state: JobEvidenceGroupState
  candidates: JobEvidenceCandidate[]
  error: unknown | null
}

export type JobEvidenceCandidate = {
  result: CareerEvidenceSearchResult
  matchedQueries: string[]
  displayQuery: string
  displayQueryIsDirectIdentifier: boolean
}

export type JobEvidenceQueryResultSet = {
  query: string
  original: boolean
  directIdentifier: boolean
  results: readonly CareerEvidenceSearchResult[]
}

type PlannedJobEvidenceQuery = Omit<JobEvidenceQueryResultSet, 'results'>

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
  return jobEvidenceSearchPlan(text).map(({ query }) => query)
}

function jobEvidenceSearchPlan(text: string): PlannedJobEvidenceQuery[] {
  const normalizedText = normalizeQueryVariant(text)
  const countedAlternativeParts = splitCountedAlternatives(normalizedText)
  const enumeratedAlternativeParts = splitEnumeratedAlternatives(normalizedText)
  const explicitAlternativeParts = normalizedText.split(EXPLICIT_ALTERNATIVE_SEPARATOR)
  const fragments = countedAlternativeParts.length > 1
    ? countedAlternativeParts
    : enumeratedAlternativeParts.length > 1
      ? enumeratedAlternativeParts
      : explicitAlternativeParts.length > 1
        ? splitExplicitAlternatives(explicitAlternativeParts)
        : splitSlashAlternatives(normalizedText)

  if (fragments.length <= 1) {
    return [{ query: text, original: true, directIdentifier: false }]
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
  return [
    { query: text, original: true, directIdentifier: false },
    ...variants.map((query) => ({
      query,
      original: false,
      directIdentifier: isDirectIdentifierVariant(query),
    })),
  ]
}

export function mergeJobEvidenceResults(
  resultSets: readonly JobEvidenceQueryResultSet[],
): JobEvidenceCandidate[] {
  const merged: JobEvidenceCandidate[] = []
  const candidatesByIdentity = new Map<string, JobEvidenceCandidate>()
  for (const resultSet of resultSets) {
    for (const result of resultSet.results) {
      if (resultSet.directIdentifier && !hasDirectIdentifierEvidence(resultSet.query, result)) {
        continue
      }
      const identity = `${result.documentId}:${result.documentVersionId}:${result.chunkId}`
      const existing = candidatesByIdentity.get(identity)
      if (existing !== undefined) {
        if (!existing.matchedQueries.includes(resultSet.query)) {
          existing.matchedQueries.push(resultSet.query)
        }
        if (resultSet.directIdentifier && !existing.displayQueryIsDirectIdentifier) {
          existing.displayQuery = resultSet.query
          existing.displayQueryIsDirectIdentifier = true
        }
        continue
      }
      if (merged.length < MAX_RESULTS_PER_ITEM) {
        const candidate = {
          result,
          matchedQueries: [resultSet.query],
          displayQuery: resultSet.query,
          displayQueryIsDirectIdentifier: resultSet.directIdentifier,
        }
        candidatesByIdentity.set(identity, candidate)
        merged.push(candidate)
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
    candidates: [],
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
    groups.flatMap((group) => group.candidates.map(({ result }) => result.documentId)),
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
        const candidates = await searchJobEvidenceItem(
          item.text,
          search,
          () => authenticationError !== null,
        )
        groups[index] = {
          item,
          state: candidates.length === 0 ? 'empty' : 'result',
          candidates,
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
          candidates: [],
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
): Promise<JobEvidenceCandidate[]> {
  const resultSets: JobEvidenceQueryResultSet[] = []
  for (const plannedQuery of jobEvidenceSearchPlan(text)) {
    if (shouldStop()) {
      break
    }
    resultSets.push({
      ...plannedQuery,
      results: await search(plannedQuery.query),
    })
  }
  if (resultSets.length === 1 && isStandaloneIdentifierQuery(text)) {
    const original = resultSets[0]
    if (original !== undefined) {
      return mergeJobEvidenceResults([
        { ...original, directIdentifier: true },
        original,
      ])
    }
  }
  return mergeJobEvidenceResults(resultSets)
}

function splitCountedAlternatives(value: string): string[] {
  return splitCommaAlternativesBefore(value, COUNTED_ALTERNATIVE_SELECTION)
}

function splitEnumeratedAlternatives(value: string): string[] {
  const commaAlternatives = splitCommaAlternativesBefore(value, ENUMERATED_ALTERNATIVE_SELECTION)
  return commaAlternatives.length > 1
    ? commaAlternatives
    : splitCompactSlashAlternativesBefore(value, ENUMERATED_ALTERNATIVE_SELECTION)
}

function splitCommaAlternativesBefore(value: string, suffix: RegExp): string[] {
  const selection = value.match(suffix)
  if (selection?.index === undefined) {
    return []
  }
  const optionList = value.slice(0, selection.index)
  if (/\p{N}[,，]\p{N}/u.test(optionList)) {
    return []
  }
  const parts = optionList.split(COMMA_SEPARATOR)
  return parts.length > 1 && isClearCommaAlternativeList(parts) ? parts : []
}

function splitCompactSlashAlternativesBefore(value: string, suffix: RegExp): string[] {
  const selection = value.match(suffix)
  if (selection?.index === undefined) {
    return []
  }
  const optionList = normalizeQueryVariant(value.slice(0, selection.index))
  if (optionList.includes('://') || optionList.includes(' ')) {
    return []
  }
  const parts = optionList.split('/')
  const clearIdentifierPair = parts.length === 2
    && parts.every((part) => DIRECT_IDENTIFIER_VARIANT.test(part))
  return clearIdentifierPair ? parts : []
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

function isDirectIdentifierVariant(value: string): boolean {
  return [...value].length <= 80 && DIRECT_IDENTIFIER_VARIANT.test(value)
}

function isStandaloneIdentifierQuery(value: string): boolean {
  return !/\s/u.test(value) && isDirectIdentifierVariant(value)
}

function hasDirectIdentifierEvidence(
  identifier: string,
  result: Pick<CareerEvidenceSearchResult, 'content' | 'snippet'>,
): boolean {
  const identifierPattern = identifier
    .trim()
    .split(/\s+/u)
    .map(escapeRegularExpression)
    .join('\\s+')
  const tokenBoundary = 'A-Za-z0-9+#._-'
  const directIdentifier = new RegExp(
    `(^|[^${tokenBoundary}])${identifierPattern}(?=$|[^${tokenBoundary}])`,
    'iu',
  )
  return directIdentifier.test(result.snippet) || directIdentifier.test(result.content)
}

function escapeRegularExpression(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&')
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
