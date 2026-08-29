import assert from 'node:assert/strict'
import test from 'node:test'

import {
  assertTrackedSafety,
  classifyExternalStatus,
  extractMarkdownLinks,
  isApprovedBinaryFixture,
  markdownHeadingAnchors,
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

test('builds GitHub-style anchors for Korean headings, punctuation, duplicates, and explicit ids', () => {
  const anchors = markdownHeadingAnchors([
    '## 1. 검색 품질·일반화',
    '## 검색 품질·일반화',
    '```markdown',
    '## 코드 블록 제목',
    '```',
    '<a id="kept-anchor"></a>',
  ].join('\n'))

  assert.deepEqual([...anchors], [
    '1-검색-품질일반화',
    '검색-품질일반화',
    'kept-anchor',
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

test('allows only an exact SHA-256 match for an approved synthetic PDF fixture', () => {
  const path = 'specs/PRZ-016-search-performance-v2/fixture.pdf'
  const allowed = new Map([[path, 'a'.repeat(64)]])

  assert.equal(isApprovedBinaryFixture(path, 'a'.repeat(64), allowed), true)
  assert.equal(isApprovedBinaryFixture(path, 'b'.repeat(64), allowed), false)
  assert.equal(isApprovedBinaryFixture('specs/other.pdf', 'a'.repeat(64), allowed), false)
})

test('allows only the verified frozen synthetic PDF fixtures in the repository', () => {
  assert.doesNotThrow(() => assertTrackedSafety())
})
