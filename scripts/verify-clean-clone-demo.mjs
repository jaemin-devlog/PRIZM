import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { basename, dirname, isAbsolute, relative, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const repositoryRoot = resolve(import.meta.dirname, '..')
const defaultManifestPath = resolve(repositoryRoot, 'local', 'clean-clone-demo', 'manifest.json')
const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '::1'])

export function parseEnvFile(content) {
  const values = {}
  content.split(/\r?\n/).forEach((line, index) => {
    const trimmed = line.trim()
    if (trimmed === '' || trimmed.startsWith('#')) return
    const match = /^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/.exec(line)
    if (!match) throw new Error(`Invalid .env entry at line ${index + 1}`)
    if (Object.hasOwn(values, match[1])) throw new Error(`Duplicate .env key at line ${index + 1}`)
    let value = match[2].trim()
    if ((value.startsWith('"') && value.endsWith('"'))
      || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1)
    }
    values[match[1]] = value
  })
  return values
}

export function redactSecrets(message, secrets) {
  return [...new Set(secrets.filter((secret) => typeof secret === 'string' && secret !== ''))]
    .sort((left, right) => right.length - left.length)
    .reduce((result, secret) => result.replaceAll(secret, '[REDACTED]'), String(message))
}

export function normalizeLoopbackBaseUrl(value) {
  const url = new URL(value)
  const hostname = url.hostname.replace(/^\[|\]$/g, '').toLowerCase()
  if (!['http:', 'https:'].includes(url.protocol)
    || !LOOPBACK_HOSTS.has(hostname)
    || url.username !== ''
    || url.password !== ''
    || (url.pathname !== '' && url.pathname !== '/')
    || url.search !== ''
    || url.hash !== '') {
    throw new Error('Demo verification only accepts a credential-free loopback HTTP(S) origin')
  }
  return url.origin
}

function utf8Length(value) {
  return Buffer.byteLength(value, 'utf8')
}

export function readDemoConfiguration(
  envFile = resolve(repositoryRoot, '.env'),
  environment = process.env,
) {
  const fileValues = parseEnvFile(readFileSync(envFile, 'utf8'))
  const value = (key, fallback = '') => environment[key] ?? fileValues[key] ?? fallback
  const email = value('PRIZM_BOOTSTRAP_DEMO_USER_EMAIL').trim().toLowerCase()
  const password = value('PRIZM_BOOTSTRAP_DEMO_USER_PASSWORD')
  const enabled = value('PRIZM_BOOTSTRAP_DEMO_USER_ENABLED', 'false').trim().toLowerCase()
  if (email === '') throw new Error('PRIZM_BOOTSTRAP_DEMO_USER_EMAIL is required')
  if (password.length < 12 || utf8Length(password) > 72) {
    throw new Error('Demo password must contain at least 12 characters and at most 72 UTF-8 bytes')
  }
  if (enabled !== 'false') {
    throw new Error('Disable the one-time demo bootstrap and recreate the backend before smoke verification')
  }

  const configuredBaseUrl = value('PRIZM_DEMO_BASE_URL')
  const baseUrl = normalizeLoopbackBaseUrl(
    configuredBaseUrl || `http://127.0.0.1:${value('SERVER_PORT', '8080')}`,
  )
  return Object.freeze({ email, password, baseUrl, bootstrapEnabled: false })
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

export function readFixtureManifest(manifestPath = defaultManifestPath) {
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
  if (manifest?.schemaVersion !== 1 || manifest.synthetic !== true
    || !Array.isArray(manifest.documents) || manifest.documents.length !== 2) {
    throw new Error('Clean-clone fixture manifest is invalid')
  }
  const fixtureRoot = resolve(dirname(manifestPath))
  const documents = manifest.documents.map((document) => {
    if (!document.key || !document.fileName || basename(document.fileName) !== document.fileName
      || !document.title || !document.documentType || !document.query || !document.marker
      || !document.expectedSourceType || !document.contentType || !document.sha256) {
      throw new Error('Clean-clone fixture manifest has an incomplete document')
    }
    const filePath = resolve(fixtureRoot, document.fileName)
    const relativePath = relative(fixtureRoot, filePath)
    if (relativePath.startsWith('..') || isAbsolute(relativePath)) {
      throw new Error('Fixture file must remain inside the manifest directory')
    }
    const contents = readFileSync(filePath)
    if (sha256(contents) !== document.sha256) {
      throw new Error(`Synthetic fixture integrity check failed for ${document.key}`)
    }
    return Object.freeze({ ...document, filePath })
  })
  const keys = new Set(documents.map((document) => document.key))
  if (!keys.has('txt') || !keys.has('pdf')) throw new Error('Manifest must contain one TXT and one PDF fixture')
  return Object.freeze({ ...manifest, documents: Object.freeze(documents) })
}

async function fetchWithTimeout(fetchImpl, url, init = {}) {
  return fetchImpl(url, {
    ...init,
    redirect: 'error',
    signal: init.signal ?? AbortSignal.timeout(30_000),
  })
}

async function requestJson(fetchImpl, url, init, expectedStatus) {
  const response = await fetchWithTimeout(fetchImpl, url, init)
  if (response.status !== expectedStatus) {
    throw new Error(`${init.method ?? 'GET'} ${new URL(url).pathname} returned HTTP ${response.status}`)
  }
  try {
    return await response.json()
  } catch {
    throw new Error(`${init.method ?? 'GET'} ${new URL(url).pathname} returned invalid JSON`)
  }
}

async function login(fetchImpl, baseUrl, email, password) {
  const response = await requestJson(fetchImpl, `${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  }, 200)
  if (typeof response.accessToken !== 'string' || response.accessToken === ''
    || response.tokenType !== 'Bearer'
    || !Number.isInteger(response.user?.id)
    || response.user?.role !== 'USER'
    || response.user?.email?.trim().toLowerCase() !== email) {
    throw new Error('Login response did not contain the configured active demo USER')
  }
  return response.accessToken
}

async function uploadDocument(fetchImpl, baseUrl, token, document) {
  const form = new FormData()
  form.set('title', document.title)
  form.set('documentType', document.documentType)
  form.set('file', new Blob([readFileSync(document.filePath)], { type: document.contentType }), document.fileName)
  const response = await requestJson(fetchImpl, `${baseUrl}/api/documents`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  }, 201)
  if (!Number.isInteger(response.documentId) || response.documentId <= 0
    || !Number.isInteger(response.versionId) || response.versionId <= 0) {
    throw new Error(`Upload response for ${document.key} did not contain valid document and version IDs`)
  }
  return Object.freeze({ document, documentId: response.documentId, versionId: response.versionId })
}

async function verifyEmptyOwnerDocumentList(fetchImpl, baseUrl, token) {
  const documents = await requestJson(fetchImpl, `${baseUrl}/api/documents`, {
    method: 'GET',
    headers: { Authorization: `Bearer ${token}` },
  }, 200)
  if (!Array.isArray(documents) || documents.length !== 0) {
    throw new Error('Demo USER already has documents; this is not an isolated clean-clone database')
  }
}

export async function waitForActiveVersion({
  fetchImpl,
  baseUrl,
  token,
  upload,
  timeoutMs = 180_000,
  pollIntervalMs = 1_000,
  now = Date.now,
  sleep = (duration) => new Promise((resolveSleep) => setTimeout(resolveSleep, duration)),
}) {
  const deadline = now() + timeoutMs
  while (now() <= deadline) {
    const detail = await requestJson(fetchImpl, `${baseUrl}/api/documents/${upload.documentId}`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` },
    }, 200)
    const version = detail.versions?.find((candidate) => candidate.versionId === upload.versionId)
    if (!version) throw new Error(`Uploaded ${upload.document.key} version is missing from document detail`)
    if (version.status === 'FAILED' || version.processingStatus === 'FAILED') {
      throw new Error(`Uploaded ${upload.document.key} processing failed with ${version.processingErrorCode ?? 'UNKNOWN'}`)
    }
    if (detail.activeVersionId === upload.versionId && version.status === 'ACTIVE') return detail
    await sleep(pollIntervalMs)
  }
  throw new Error(`Timed out waiting for uploaded ${upload.document.key} version to become ACTIVE`)
}

async function verifySearch(fetchImpl, baseUrl, token, upload) {
  const results = await requestJson(fetchImpl, `${baseUrl}/api/career-evidence/search`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query: upload.document.query }),
  }, 200)
  if (!Array.isArray(results)) throw new Error('Career Evidence response was not an array')
  const marker = upload.document.marker.toLowerCase()
  const match = results.find((result) => (
    result.documentId === upload.documentId
    && result.documentVersionId === upload.versionId
    && result.sourceType === upload.document.expectedSourceType
    && Number.isInteger(result.sourceIndex)
    && result.sourceIndex >= upload.document.expectedSourceIndexMinimum
    && typeof result.content === 'string'
    && result.content.toLowerCase().includes(marker)
  ))
  if (!match) {
    throw new Error(`Search did not return the uploaded ${upload.document.key} document with the expected source`)
  }
  return match
}

async function verifyLoggedOutBoundary(fetchImpl, baseUrl) {
  const response = await fetchWithTimeout(fetchImpl, `${baseUrl}/api/documents`, { method: 'GET' })
  if (response.status !== 401) {
    throw new Error(`Unauthenticated protected request returned HTTP ${response.status} instead of 401`)
  }
}

export async function verifyCleanCloneDemo({
  baseUrl,
  email,
  password,
  bootstrapEnabled,
  manifestPath = defaultManifestPath,
  fetchImpl = fetch,
  timeoutMs,
  pollIntervalMs,
  now,
  sleep,
}) {
  const safeBaseUrl = normalizeLoopbackBaseUrl(baseUrl)
  if (bootstrapEnabled !== false) {
    throw new Error('Smoke verification requires an explicitly disabled demo bootstrap')
  }
  const manifest = readFixtureManifest(manifestPath)
  const token = await login(fetchImpl, safeBaseUrl, email, password)
  await verifyEmptyOwnerDocumentList(fetchImpl, safeBaseUrl, token)
  const uploads = []
  for (const document of manifest.documents) {
    const upload = await uploadDocument(fetchImpl, safeBaseUrl, token, document)
    await waitForActiveVersion({
      fetchImpl,
      baseUrl: safeBaseUrl,
      token,
      upload,
      timeoutMs,
      pollIntervalMs,
      now,
      sleep,
    })
    const source = await verifySearch(fetchImpl, safeBaseUrl, token, upload)
    uploads.push(Object.freeze({
      key: document.key,
      documentId: upload.documentId,
      versionId: upload.versionId,
      sourceType: source.sourceType,
      sourceIndex: source.sourceIndex,
    }))
  }
  await verifyLoggedOutBoundary(fetchImpl, safeBaseUrl)
  return Object.freeze({
    documentsVerified: uploads.length,
    unauthenticatedAccessRejected: true,
    uploads: Object.freeze(uploads),
  })
}

const isMain = process.argv[1]
  && import.meta.url === pathToFileURL(resolve(process.argv[1])).href

if (isMain) {
  let password = ''
  try {
    const configuration = readDemoConfiguration()
    password = configuration.password
    const result = await verifyCleanCloneDemo(configuration)
    console.log(`Verified ${result.documentsVerified} synthetic documents and the logged-out 401 boundary.`)
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Clean-clone verification failed'
    console.error(redactSecrets(message, [password]))
    process.exitCode = 1
  }
}
