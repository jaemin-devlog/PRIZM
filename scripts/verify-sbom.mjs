import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const root = resolve(import.meta.dirname, '..')
const sbomDirectory = resolve(root, 'sbom')
const checksumPath = resolve(sbomDirectory, 'SHA256SUMS')
const generatedFiles = [
  'prizm-backend-runtime.cdx.json',
  'prizm-frontend.cdx.json',
  'prizm-ai-model-manifest.json',
  'prizm-scope-manifest.json',
]

const cycloneDxHashAlgorithms = new Set([
  'MD5',
  'SHA-1',
  'SHA-256',
  'SHA-384',
  'SHA-512',
  'SHA3-256',
  'SHA3-384',
  'SHA3-512',
  'BLAKE2b-256',
  'BLAKE2b-384',
  'BLAKE2b-512',
  'BLAKE3',
])

function fail(message) {
  throw new Error(`SBOM verification failed: ${message}`)
}

function readJson(fileName) {
  const content = readFileSync(resolve(sbomDirectory, fileName), 'utf8')

  try {
    return { content, value: JSON.parse(content) }
  } catch {
    fail(`${fileName} is not valid JSON`)
  }
}

function assertNoSensitiveLocalData(fileName, content) {
  const forbidden = [
    /(?:[A-Za-z]:\\(?:Users|home)\\|\/(?:Users|home)\/)/,
    /jdbc:(?:postgresql|mysql|mariadb|sqlserver):/i,
    /"(?:password|authorization|access[_-]?token|refresh[_-]?token)"\s*:/i,
  ]

  if (forbidden.some((pattern) => pattern.test(content))) {
    fail(`${fileName} contains a local path, JDBC URL, or credential-shaped field`)
  }
}

function visitObjects(value, visitor, path = '$') {
  if (Array.isArray(value)) {
    value.forEach((entry, index) => visitObjects(entry, visitor, `${path}[${index}]`))
    return
  }
  if (!value || typeof value !== 'object') {
    return
  }

  visitor(value, path)
  for (const [key, entry] of Object.entries(value)) {
    visitObjects(entry, visitor, `${path}.${key}`)
  }
}

export function assertCycloneDxValue(fileName, expectedName, value) {
  if (value.bomFormat !== 'CycloneDX' || value.specVersion !== '1.6') {
    fail(`${fileName} must be a CycloneDX 1.6 BOM`)
  }
  if (value.metadata?.component?.name !== expectedName) {
    fail(`${fileName} has an unexpected primary component`)
  }
  if (!Array.isArray(value.components) || value.components.length === 0) {
    fail(`${fileName} has no components`)
  }
  if (value.serialNumber || value.metadata?.timestamp) {
    fail(`${fileName} must not contain a non-reproducible serial number or timestamp`)
  }

  const references = new Map()
  visitObjects(value, (entry, path) => {
    if (Object.hasOwn(entry, 'bom-ref')) {
      const reference = entry['bom-ref']
      if (typeof reference !== 'string' || !reference) {
        fail(`${fileName} contains an empty bom-ref at ${path}`)
      }
      if (references.has(reference)) {
        fail(`${fileName} contains duplicate bom-ref ${reference}`)
      }
      references.set(reference, path)
    }

    if (Array.isArray(entry.hashes)) {
      for (const hash of entry.hashes) {
        if (!cycloneDxHashAlgorithms.has(hash?.alg)) {
          fail(`${fileName} contains unsupported CycloneDX hash algorithm ${hash?.alg}`)
        }
      }
    }
  })
}

function assertCycloneDx(fileName, expectedName) {
  const { content, value } = readJson(fileName)

  assertNoSensitiveLocalData(fileName, content)
  assertCycloneDxValue(fileName, expectedName, value)
}

function assertModelManifest() {
  const fileName = 'prizm-ai-model-manifest.json'
  const { content, value } = readJson(fileName)

  assertNoSensitiveLocalData(fileName, content)
  if (value.format !== 'PRIZM-AI-MODEL-MANIFEST' || value.formatVersion !== '1.0') {
    fail('the AI model manifest format is invalid')
  }
  if (value.project?.sourceLicense !== 'Apache-2.0') {
    fail('the AI model manifest must keep the PRIZM source license explicit')
  }
  if (!value.distributionBoundary?.notDistributed?.includes('bge-m3 model weights')) {
    fail('the AI model manifest must state that bge-m3 weights are not distributed')
  }

  const registryModel = value.components?.find((component) => component.id === 'ollama-bge-m3-registry-artifact')
  if (!registryModel || registryModel.upstreamToRegistryLineage !== 'UNVERIFIED_LINEAGE') {
    fail('the AI model manifest must not overstate the BAAI-to-Ollama lineage')
  }
}

function assertScopeManifest() {
  const fileName = 'prizm-scope-manifest.json'
  const { content, value } = readJson(fileName)

  assertNoSensitiveLocalData(fileName, content)
  if (value.format !== 'PRIZM-SBOM-SCOPE-MANIFEST' || value.formatVersion !== '1.0') {
    fail('the SBOM scope manifest format is invalid')
  }

  const ids = new Set(value.scopeRecords?.map((record) => record.id))
  for (const required of ['backend-runtime', 'backend-test-build', 'frontend-runtime-dev-optional', 'ci-and-github-actions', 'containers-and-database', 'ai-model-runtime', 'fixtures-and-assets']) {
    if (!ids.has(required)) {
      fail(`the SBOM scope manifest is missing ${required}`)
    }
  }
}

function sha256(fileName) {
  return createHash('sha256').update(readFileSync(resolve(sbomDirectory, fileName))).digest('hex')
}

function expectedChecksums() {
  return generatedFiles.map((fileName) => `${sha256(fileName)}  ${fileName}`).join('\n') + '\n'
}

export function verifyRepository({ writeChecksums = false } = {}) {
  assertCycloneDx('prizm-backend-runtime.cdx.json', 'prizm')
  assertCycloneDx('prizm-frontend.cdx.json', 'prizm-frontend')
  assertModelManifest()
  assertScopeManifest()

  if (writeChecksums) {
    writeFileSync(checksumPath, expectedChecksums(), 'utf8')
    return { checksumPath }
  }
  if (readFileSync(checksumPath, 'utf8') !== expectedChecksums()) {
    fail('SHA256SUMS does not match the generated SBOM or AI model manifest')
  }
  return { checksumPath }
}

const isMain = process.argv[1]
  && import.meta.url === pathToFileURL(resolve(process.argv[1])).href

if (isMain) {
  const writeChecksums = process.argv.includes('--write-checksums')
  const result = verifyRepository({ writeChecksums })
  if (writeChecksums) {
    console.log(`Updated ${result.checksumPath}`)
  }
  console.log('SBOM and AI model manifest structural checks passed.')
}
