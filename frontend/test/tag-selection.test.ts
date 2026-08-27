import assert from 'node:assert/strict'
import test from 'node:test'
import {
  addUniqueTag,
  canCreateTag,
  keywordEvidenceRetryTarget,
  linkedDocumentCountLabel,
  normalizeTagName,
  resolveSelectedTag,
  selectedTagIdFromSearch,
  sortTagUsage,
  tagDetailPath,
} from '../src/tagSelection.ts'
import type { Tag, TagUsage } from '../src/api/tagApi.ts'

const springBoot: Tag = { tagId: 1, name: 'Spring Boot', source: 'SYSTEM' }
const redis: Tag = { tagId: 2, name: 'Redis', source: 'SYSTEM' }

test('tag normalization collapses spacing, Unicode compatibility forms, and case', () => {
  assert.equal(normalizeTagName('  Spring   BOOT  '), 'spring boot')
  assert.equal(normalizeTagName('Ｔａｕｒｉ'), 'tauri')
})

test('multiple tags can be selected without adding identifier or normalized-name duplicates', () => {
  const selected = addUniqueTag(addUniqueTag([], springBoot), redis)
  assert.deepEqual(selected.map((tag) => tag.name), ['Spring Boot', 'Redis'])
  assert.equal(addUniqueTag(selected, springBoot), selected)
  assert.equal(
    addUniqueTag(selected, { tagId: 99, name: ' spring  boot ', source: 'USER' }),
    selected,
  )
})

test('new tag creation is offered only when no result or selection has the same normalized name', () => {
  assert.equal(canCreateTag('Tauri', [springBoot], [redis]), true)
  assert.equal(canCreateTag(' spring boot ', [springBoot], [redis]), false)
  assert.equal(canCreateTag('REDIS', [], [redis]), false)
  assert.equal(canCreateTag('  ', [], []), false)
})

test('used tags sort by document count and stable name', () => {
  const usage: TagUsage[] = [
    { ...redis, documentCount: 2 },
    { ...springBoot, documentCount: 4 },
    { tagId: 3, name: 'Docker', source: 'SYSTEM', documentCount: 2 },
  ]
  assert.deepEqual(sortTagUsage(usage).map((tag) => tag.name), ['Spring Boot', 'Docker', 'Redis'])
})

test('tag usage labels count linked documents rather than keyword occurrences', () => {
  assert.equal(linkedDocumentCountLabel(0), '0개 연결 문서')
  assert.equal(linkedDocumentCountLabel(1), '1개 연결 문서')
})

test('tag detail URL accepts only a positive integer identifier', () => {
  assert.equal(tagDetailPath(12), '/career-vault/keywords?tagId=12')
  assert.equal(selectedTagIdFromSearch('?tagId=12'), 12)
  assert.equal(selectedTagIdFromSearch('?tagId=0'), null)
  assert.equal(selectedTagIdFromSearch('?tagId=Redis'), null)
})

test('tag evidence deep links resolve loading, failed usage, unknown, and ready states without waiting forever', () => {
  const usage: TagUsage[] = [{ ...springBoot, documentCount: 1 }]
  assert.deepEqual(resolveSelectedTag(null, 'loading', usage), { status: 'idle' })
  assert.deepEqual(resolveSelectedTag(1, 'loading', usage), { status: 'waiting' })
  assert.deepEqual(resolveSelectedTag(1, 'error', []), { status: 'unavailable' })
  assert.deepEqual(resolveSelectedTag(99, 'result', usage), { status: 'unavailable' })
  assert.deepEqual(resolveSelectedTag(1, 'result', usage), { status: 'ready', tag: usage[0] })
})

test('tag evidence retry reloads usage only when the selected tag is unavailable', () => {
  const usage: TagUsage[] = [{ ...springBoot, documentCount: 1 }]
  assert.equal(keywordEvidenceRetryTarget(1, 'error', []), 'usage')
  assert.equal(keywordEvidenceRetryTarget(99, 'result', usage), 'usage')
  assert.equal(keywordEvidenceRetryTarget(1, 'result', usage), 'evidence')
})
