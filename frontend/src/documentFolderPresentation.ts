import type { DocumentSummary, DocumentType } from './api/documentApi'

export type DocumentFolder = {
  documentType: DocumentType
  documents: DocumentSummary[]
}

export function groupDocumentsByType(documents: DocumentSummary[]): DocumentFolder[] {
  const grouped = new Map<DocumentType, DocumentSummary[]>()
  for (const document of documents) {
    const type = document.documentType as DocumentType
    grouped.set(type, [...(grouped.get(type) ?? []), document])
  }
  return [...grouped.entries()]
    .map(([documentType, groupedDocuments]) => ({ documentType, documents: groupedDocuments }))
    .sort((left, right) => left.documentType.localeCompare(right.documentType))
}

export function documentFolderPath(documentType: DocumentType | undefined): string {
  return documentType === undefined
    ? '/career-vault/documents'
    : `/career-vault/documents?type=${encodeURIComponent(documentType)}`
}

export function documentDetailPath(documentId: number): string {
  return `/career-vault/documents?documentId=${documentId}`
}

export function documentListPathAfterDetailClose(search: string): string {
  const params = new URLSearchParams(search)
  params.delete('documentId')
  const remaining = params.toString()
  return `/career-vault/documents${remaining === '' ? '' : `?${remaining}`}`
}

export function selectedDocumentFolderFromSearch(search: string): DocumentType | undefined {
  const value = new URLSearchParams(search).get('type')
  return value === null || value.trim() === '' ? undefined : value as DocumentType
}

export function selectedDocumentIdFromSearch(search: string): number | null {
  const value = Number(new URLSearchParams(search).get('documentId'))
  return Number.isSafeInteger(value) && value > 0 ? value : null
}
