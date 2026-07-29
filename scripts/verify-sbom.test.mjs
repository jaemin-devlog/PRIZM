import assert from 'node:assert/strict'
import test from 'node:test'

import {
  assertCanonicalLf,
  assertCycloneDxValue,
  assertScopeManifestValue,
  verifyRepository,
} from './verify-sbom.mjs'

function minimalBom(componentOverrides = {}) {
  return {
    bomFormat: 'CycloneDX',
    specVersion: '1.6',
    version: 1,
    metadata: {
      component: {
        type: 'application',
        'bom-ref': 'pkg:generic/prizm@0.0.1',
        name: 'prizm',
        version: '0.0.1',
      },
    },
    components: [
      {
        type: 'library',
        'bom-ref': 'pkg:maven/example/library@1.0.0',
        name: 'library',
        version: '1.0.0',
        hashes: [{ alg: 'SHA-512', content: '00' }],
        ...componentOverrides,
      },
    ],
  }
}

test('accepts canonical CycloneDX hash algorithms and unique bom-ref values', () => {
  assert.doesNotThrow(() => assertCycloneDxValue('fixture.cdx.json', 'prizm', minimalBom()))
})

test('rejects a non-canonical CycloneDX hash algorithm', () => {
  const bom = minimalBom({ hashes: [{ alg: 'SHA512', content: '00' }] })

  assert.throws(
    () => assertCycloneDxValue('fixture.cdx.json', 'prizm', bom),
    /unsupported CycloneDX hash algorithm SHA512/,
  )
})

test('rejects duplicate bom-ref values', () => {
  const bom = minimalBom()
  bom.components.push({
    type: 'library',
    'bom-ref': bom.components[0]['bom-ref'],
    name: 'classifier-variant',
    version: '1.0.0',
  })

  assert.throws(
    () => assertCycloneDxValue('fixture.cdx.json', 'prizm', bom),
    /duplicate bom-ref pkg:maven\/example\/library@1\.0\.0/,
  )
})

test('rejects malformed component fields and hash content', () => {
  assert.throws(
    () => assertCycloneDxValue('fixture.cdx.json', 'prizm', minimalBom({ name: '' })),
    /component 0 is missing name/,
  )
  assert.throws(
    () => assertCycloneDxValue('fixture.cdx.json', 'prizm', minimalBom({
      hashes: [{ alg: 'SHA-512', content: 'not-hex' }],
    })),
    /non-hexadecimal hash/,
  )
})

test('rejects a source-only license Gate with blockers', () => {
  assert.throws(
    () => assertScopeManifestValue({
      format: 'PRIZM-SBOM-SCOPE-MANIFEST',
      formatVersion: '1.0',
      scopeRecords: [
        { id: 'backend-runtime' },
        { id: 'backend-test-build' },
        { id: 'frontend-runtime-dev-optional' },
        { id: 'ci-and-github-actions' },
        { id: 'containers-and-database' },
        { id: 'ai-model-runtime' },
        { id: 'fixtures-and-assets' },
      ],
      sourceOnlyLicenseGate: {
        status: 'BLOCKED',
        blockingStatuses: ['UNKNOWN', 'CONFLICT', 'BLOCKED'],
        blockers: ['example'],
      },
    }),
    /source-only license Gate must be PASS with no blockers/,
  )
})

test('rejects CRLF output and a missing terminal LF', () => {
  assert.throws(
    () => assertCanonicalLf('fixture.cdx.json', '{}\r\n'),
    /must use LF line endings/,
  )
  assert.throws(
    () => assertCanonicalLf('fixture.cdx.json', '{}'),
    /must end with LF/,
  )
})

test('the checked-in SBOM and manifests pass the repository verifier', () => {
  assert.doesNotThrow(() => verifyRepository())
})
