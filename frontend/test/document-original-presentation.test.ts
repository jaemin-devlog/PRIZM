import assert from 'node:assert/strict'
import test from 'node:test'

import {
  txtPreviewText,
} from '../src/documentOriginalPresentation.ts'

test('TXT preview preserves useful layout while removing controls and limiting visible content', () => {
  assert.equal(
    txtPreviewText('  Java\r\nSpring Boot\u0000\r\n  ', 100),
    'Java\nSpring Boot',
  )
  assert.equal(txtPreviewText('0123456789', 5), '01234…')
  assert.equal(txtPreviewText(' \r\n\t ', 20), '')
})

test('TXT preview rejects invalid length limits', () => {
  assert.throws(() => txtPreviewText('text', 0), RangeError)
})
