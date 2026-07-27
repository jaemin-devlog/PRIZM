import assert from 'node:assert/strict'
import test from 'node:test'

import { assertCycloneDxValue, verifyRepository } from './verify-sbom.mjs'

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

test('the checked-in SBOM and manifests pass the repository verifier', () => {
  assert.doesNotThrow(() => verifyRepository())
})
