import assert from 'node:assert/strict'
import test from 'node:test'

import {
  DocumentApiError,
  getDocumentOriginal,
} from '../src/api/documentApi.ts'

function tokenStorage(accessToken: string | null): Storage {
  return {
    length: accessToken === null ? 0 : 1,
    clear: () => undefined,
    getItem: () => accessToken,
    key: () => null,
    removeItem: () => undefined,
    setItem: () => undefined,
  }
}

test('original helper loads an authenticated UTF-8 TXT document without exposing a storage path', async () => {
  const originalLocalStorage = globalThis.localStorage
  const originalFetch = globalThis.fetch
  let requestedPath = ''
  let requestedInit: RequestInit | undefined
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: tokenStorage('user-token'),
  })
  globalThis.fetch = async (input, init) => {
    requestedPath = String(input)
    requestedInit = init
    return new Response('Java와 Spring Boot 경험', {
      status: 200,
      headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
    })
  }

  try {
    const original = await getDocumentOriginal(11, 22)
    assert.equal(requestedPath, '/api/documents/11/versions/22/original')
    assert.equal(new Headers(requestedInit?.headers).get('Authorization'), 'Bearer user-token')
    assert.equal(new Headers(requestedInit?.headers).get('Accept'), 'application/pdf, text/plain, application/json')
    assert.equal(original.fileType, 'TXT')
    assert.equal(await original.blob.text(), 'Java와 Spring Boot 경험')
  } finally {
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: originalLocalStorage,
    })
    globalThis.fetch = originalFetch
  }
})

test('original helper rejects an unexpected response type', async () => {
  const originalLocalStorage = globalThis.localStorage
  const originalFetch = globalThis.fetch
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: tokenStorage('user-token'),
  })
  globalThis.fetch = async () => new Response('{}', {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

  try {
    await assert.rejects(
      getDocumentOriginal(11, 22),
      (error: unknown) => error instanceof DocumentApiError
        && error.code === 'INVALID_ORIGINAL_RESPONSE',
    )
  } finally {
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: originalLocalStorage,
    })
    globalThis.fetch = originalFetch
  }
})
