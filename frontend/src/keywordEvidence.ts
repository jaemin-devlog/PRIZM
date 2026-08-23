import type { DocumentSummary, DocumentType } from './api/documentApi'
import type { CareerEvidenceSearchResult } from './api/searchApi'

export type KeywordEvidenceDependencies = {
  search: (query: string) => Promise<CareerEvidenceSearchResult[]>
  listDocuments: () => Promise<DocumentSummary[]>
}

export type KeywordEvidence = {
  results: CareerEvidenceSearchResult[]
  documentTypes: Map<number, DocumentType>
}

export async function loadKeywordEvidence(
  query: string,
  dependencies: KeywordEvidenceDependencies,
): Promise<KeywordEvidence> {
  const results = await dependencies.search(query)
  if (results.length === 0) {
    return { results, documentTypes: new Map() }
  }

  const resultDocumentIds = new Set(results.map((result) => result.documentId))
  const documents = await dependencies.listDocuments()
  return {
    results,
    documentTypes: new Map(
      documents
        .filter((document) => resultDocumentIds.has(document.documentId))
        .map((document) => [document.documentId, document.documentType as DocumentType]),
    ),
  }
}

export function relatedEvidenceDocumentCount(results: CareerEvidenceSearchResult[]): number {
  return new Set(results.map((result) => result.documentId)).size
}
