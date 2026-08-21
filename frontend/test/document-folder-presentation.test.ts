import assert from 'node:assert/strict'
import test from 'node:test'
import { documentFolderPath, groupDocumentsByType, selectedDocumentFolderFromSearch } from '../src/documentFolderPresentation.ts'
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
