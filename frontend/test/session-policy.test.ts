import assert from 'node:assert/strict'
import test from 'node:test'

import {
  expireSessionIfUnauthorized,
  isSessionExpiredError,
} from '../src/auth/sessionPolicy.ts'

test('only an authentication-required response expires the frontend session', () => {
  assert.equal(isSessionExpiredError({ status: 401 }), true)
  assert.equal(isSessionExpiredError({ status: 403 }), false)
  assert.equal(isSessionExpiredError({ status: 404 }), false)
  assert.equal(isSessionExpiredError(new Error('network failure')), false)
})

test('the shared API catch policy logs out on 401 but keeps the session on authorization failure', () => {
  let expirations = 0
  assert.equal(expireSessionIfUnauthorized({ status: 401 }, () => { expirations += 1 }), true)
  assert.equal(expireSessionIfUnauthorized({ status: 403 }, () => { expirations += 1 }), false)
  assert.equal(expireSessionIfUnauthorized({ status: 500 }, () => { expirations += 1 }), false)
  assert.equal(expirations, 1)
})
