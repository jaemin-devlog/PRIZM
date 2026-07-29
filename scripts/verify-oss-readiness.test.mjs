import assert from 'node:assert/strict'
import test from 'node:test'

import {
  classifyExternalStatus,
  extractMarkdownLinks,
  markdownFindings,
  sensitiveContentFindings,
} from './verify-oss-readiness.mjs'

test('reports trailing whitespace and an unclosed code fence with file and line', () => {
  const findings = markdownFindings('docs/example.md', '# Example  \n\n```text\nbody\n')

  assert.deepEqual(findings, [
    'docs/example.md:1 has trailing whitespace',
    'docs/example.md:3 has an unclosed ` code fence',
  ])
})

test('accepts balanced backtick and tilde fences', () => {
  const content = '```text\nbody\n```\n\n~~~json\n{}\n~~~\n'

  assert.deepEqual(markdownFindings('docs/example.md', content), [])
})

test('extracts inline, image, and reference Markdown links', () => {
  const links = extractMarkdownLinks([
    '[local](../README.md#start)',
    '![image](assets/example.png)',
    '[external]: https://example.com/docs',
  ].join('\n'))

  assert.deepEqual(links.map(({ target }) => target), [
    '../README.md#start',
    'assets/example.png',
    'https://example.com/docs',
  ])
})

test('only repeated 404 and 410 statuses are permanent link failures', () => {
  assert.equal(classifyExternalStatus(200), 'ok')
  assert.equal(classifyExternalStatus(302), 'ok')
  assert.equal(classifyExternalStatus(403), 'indeterminate')
  assert.equal(classifyExternalStatus(429), 'indeterminate')
  assert.equal(classifyExternalStatus(503), 'indeterminate')
  assert.equal(classifyExternalStatus(404), 'permanent')
  assert.equal(classifyExternalStatus(410), 'permanent')
})

test('GitHub token detection requires a token-shaped value, not only a prefix', () => {
  const declaration = String.raw`/(?:github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,})/`
  const token = ['github', 'pat', 'A'.repeat(24)].join('_')

  assert.deepEqual(sensitiveContentFindings('scripts/check.mjs', declaration), [])
  assert.deepEqual(sensitiveContentFindings('fixture.txt', token), [
    'fixture.txt:1 contains GitHub token',
  ])
})
