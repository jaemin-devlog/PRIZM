import assert from 'node:assert/strict'
import test from 'node:test'

import {
  JobPostingApiError,
  segmentJobPosting,
} from '../src/api/jobPostingApi.ts'

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

test('segmentation helper sends the pasted content to the authenticated deterministic endpoint', async () => {
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
    return new Response(JSON.stringify([
      { itemId: 1, section: '자격요건', text: '임의 기술 운영 경험' },
    ]), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }

  try {
    const result = await segmentJobPosting('자격요건\n- 임의 기술 운영 경험')
    assert.equal(requestedPath, '/api/job-postings/segment')
    assert.equal(requestedInit?.method, 'POST')
    assert.equal(new Headers(requestedInit?.headers).get('Authorization'), 'Bearer user-token')
    assert.deepEqual(JSON.parse(String(requestedInit?.body)), {
      content: '자격요건\n- 임의 기술 운영 경험',
    })
    assert.deepEqual(result, [
      { itemId: 1, section: '자격요건', text: '임의 기술 운영 경험' },
    ])
  } finally {
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: originalLocalStorage,
    })
    globalThis.fetch = originalFetch
  }
})

test('segmentation helper rejects a missing session before making a request', async () => {
  const originalLocalStorage = globalThis.localStorage
  const originalFetch = globalThis.fetch
  let fetchRequests = 0
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: tokenStorage(null),
  })
  globalThis.fetch = async () => {
    fetchRequests += 1
    return new Response(null, { status: 500 })
  }

  try {
    await assert.rejects(
      segmentJobPosting('채용공고'),
      (error: unknown) => error instanceof JobPostingApiError && error.status === 401,
    )
    assert.equal(fetchRequests, 0)
  } finally {
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: originalLocalStorage,
    })
    globalThis.fetch = originalFetch
  }
})
