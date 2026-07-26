import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const lockfilePath = resolve(root, 'frontend', 'package-lock.json')
const outputPath = resolve(root, 'sbom', 'prizm-frontend.cdx.json')

function fail(message) {
  throw new Error(`Frontend SBOM generation failed: ${message}`)
}

function packageNameFromLockPath(lockPath) {
  const marker = 'node_modules/'
  const start = lockPath.lastIndexOf(marker)
  if (start < 0) {
    fail(`unsupported package-lock path: ${lockPath}`)
  }

  const segments = lockPath.slice(start + marker.length).split('/')
  if (segments[0].startsWith('@')) {
    if (segments.length < 2) {
      fail(`unsupported scoped package-lock path: ${lockPath}`)
    }
    return `${segments[0]}/${segments[1]}`
  }
  return segments[0]
}

function npmPurl(name, version) {
  return `pkg:npm/${name.split('/').map(encodeURIComponent).join('/')}@${encodeURIComponent(version)}`
}

function integrityHash(integrity) {
  const separator = integrity?.indexOf('-') ?? -1
  if (separator < 1) {
    return undefined
  }

  const algorithm = integrity.slice(0, separator).toUpperCase()
  const encoded = integrity.slice(separator + 1)
  if (!['SHA256', 'SHA384', 'SHA512'].includes(algorithm) || !encoded) {
    return undefined
  }
  return { alg: algorithm, content: Buffer.from(encoded, 'base64').toString('hex') }
}

function dependencyScope(entry) {
  if (entry.dev && entry.optional) return 'development-optional'
  if (entry.dev) return 'development'
  if (entry.optional) return 'optional'
  return 'required'
}

const lockfile = JSON.parse(readFileSync(lockfilePath, 'utf8'))
if (lockfile.lockfileVersion !== 3 || !lockfile.packages?.['']) {
  fail('frontend/package-lock.json must be lockfileVersion 3 with a root package entry')
}

const rootPackage = lockfile.packages['']
const components = Object.entries(lockfile.packages)
  .filter(([lockPath, entry]) => lockPath && entry.version)
  .map(([lockPath, entry]) => {
    const name = packageNameFromLockPath(lockPath)
    const purl = npmPurl(name, entry.version)
    const component = {
      type: 'library',
      'bom-ref': purl,
      name,
      version: entry.version,
      purl,
      properties: [
        { name: 'prizm:package-lock-path', value: lockPath },
        { name: 'prizm:dependency-scope', value: dependencyScope(entry) },
      ],
    }

    const hash = integrityHash(entry.integrity)
    if (hash) component.hashes = [hash]
    if (entry.resolved) component.externalReferences = [{ type: 'distribution', url: entry.resolved }]
    if (entry.license) component.licenses = [{ license: { id: entry.license } }]
    return component
  })
  .sort((left, right) => left.purl.localeCompare(right.purl))

const rootPurl = npmPurl(rootPackage.name, rootPackage.version)
const bom = {
  bomFormat: 'CycloneDX',
  specVersion: '1.6',
  version: 1,
  metadata: {
    component: {
      type: 'application',
      'bom-ref': rootPurl,
      name: rootPackage.name,
      version: rootPackage.version,
      purl: rootPurl,
    },
    properties: [
      { name: 'prizm:source', value: 'frontend/package-lock.json' },
      { name: 'prizm:lockfile-version', value: String(lockfile.lockfileVersion) },
      { name: 'prizm:generator', value: 'scripts/generate-frontend-sbom.mjs' },
    ],
  },
  components,
}

writeFileSync(outputPath, `${JSON.stringify(bom, null, 2)}\n`, 'utf8')
const sourceHash = createHash('sha256').update(readFileSync(lockfilePath)).digest('hex')
console.log(`Generated ${outputPath} from package-lock SHA-256 ${sourceHash} (${components.length} components).`)
