import assert from 'node:assert/strict'
import test from 'node:test'
import {
  documentDetailPath,
  documentFolderPath,
  documentListPathAfterDetailClose,
  groupDocumentsByType,
  resolveDocumentPreviewVersionId,
  selectedDocumentFolderFromSearch,
  selectedDocumentIdFromSearch,
  selectedDocumentVersionIdFromSearch,
} from '../src/documentFolderPresentation.ts'
import type { DocumentSummary } from '../src/api/documentApi.ts'

const documents = [
  { documentId: 1, documentType: 'RESUME' },
  { documentId: 2, documentType: 'PORTFOLIO' },
  { documentId: 3, documentType: 'RESUME' },
] as DocumentSummary[]

test('groups existing documents by type and omits empty types', () => {
  assert.deepEqual(groupDocumentsByType(documents).map((folder) => [folder.documentType, folder.documents.length]), [
    ['PORTFOLIO', 1], ['RESUME', 2],
  ])
})

test('folder URL round-trips for browser back and forward navigation', () => {
  assert.equal(documentFolderPath('RESUME'), '/career-vault/documents?type=RESUME')
  assert.equal(selectedDocumentFolderFromSearch('?type=RESUME'), 'RESUME')
  assert.equal(selectedDocumentFolderFromSearch(''), undefined)
})

test('document detail URL accepts only a positive integer identifier', () => {
  assert.equal(documentDetailPath(42), '/career-vault/documents?documentId=42')
  assert.equal(
    documentDetailPath(42, '?type=RESUME'),
    '/career-vault/documents?type=RESUME&documentId=42',
  )
  assert.equal(
    documentDetailPath(42, '?type=RESUME', 84),
    '/career-vault/documents?type=RESUME&documentId=42&versionId=84',
  )
  assert.equal(selectedDocumentIdFromSearch('?documentId=42'), 42)
  assert.equal(selectedDocumentIdFromSearch('?documentId=0'), null)
  assert.equal(selectedDocumentIdFromSearch('?documentId=resume'), null)
  assert.equal(selectedDocumentVersionIdFromSearch('?documentId=42&versionId=84'), 84)
  assert.equal(selectedDocumentVersionIdFromSearch('?versionId=0'), null)
  assert.equal(selectedDocumentVersionIdFromSearch('?versionId=1.5'), null)
})

test('closing TXT document detail removes only the deep-link identifier', () => {
  assert.equal(
    documentListPathAfterDetailClose('?documentId=42'),
    '/career-vault/documents',
  )
  assert.equal(
    documentListPathAfterDetailClose('?type=RESUME&documentId=42&versionId=84'),
    '/career-vault/documents?type=RESUME',
  )
})

test('document detail selects only a requested version present in the owner-scoped detail', () => {
  const versions = [{ versionId: 84 }, { versionId: 85 }]
  assert.equal(resolveDocumentPreviewVersionId(84, 85, versions), 84)
  assert.equal(resolveDocumentPreviewVersionId(999, 85, versions), 85)
  assert.equal(resolveDocumentPreviewVersionId(null, 85, versions), 85)
  assert.equal(resolveDocumentPreviewVersionId(null, null, versions), 84)
  assert.equal(resolveDocumentPreviewVersionId(999, null, []), null)
})
