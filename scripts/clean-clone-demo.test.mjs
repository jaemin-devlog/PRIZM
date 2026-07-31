import assert from 'node:assert/strict'
import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { createServer as createHttpServer } from 'node:http'
import { once } from 'node:events'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import test from 'node:test'

import {
  AUDITED_BGE_M3_MANIFEST_SHA256,
  assessRequiredToolVersions,
  executableCandidates,
  findExecutable,
  formatPrerequisiteReport,
  inspectDocker,
  inspectOllama,
  mergeConfiguredPorts,
  parsePrerequisiteArguments,
  readConfiguredPorts,
} from './check-clean-clone-prerequisites.mjs'
import {
  createTextLayerPdf,
  generateDemoFixtures,
} from './generate-clean-clone-demo-fixtures.mjs'
import {
  disableDemoBootstrap,
  parsePrepareArguments,
  prepareDemoEnvironment,
} from './prepare-clean-clone-demo-env.mjs'
import {
  buildCleanCloneComposeInvocation,
  runCleanCloneCompose,
  sanitizedComposeEnvironment,
  validateComposeArguments,
} from './run-clean-clone-compose.mjs'
import {
  parseEnvFile,
  readDemoConfiguration,
  readFixtureManifest,
  redactSecrets,
  verifyCleanCloneDemo,
  waitForActiveVersion,
} from './verify-clean-clone-demo.mjs'

function temporaryDirectory(t) {
  const directory = mkdtempSync(join(tmpdir(), 'prizm-clean-clone-test-'))
  t.after(() => rmSync(directory, { recursive: true, force: true }))
  return directory
}

function environmentTemplate() {
  return [
    'SERVER_PORT=8080',
    'PRIZM_FRONTEND_PORT=5173',
    'PRIZM_JWT_SECRET=',
    'COMPOSE_PROJECT_NAME=prizm',
    'PRIZM_DB_PORT=5432',
    'PRIZM_DB_PASSWORD=replace-runtime',
    'PRIZM_FLYWAY_PASSWORD=replace-owner',
    'PRIZM_BOOTSTRAP_DEMO_USER_ENABLED=false',
    'PRIZM_BOOTSTRAP_DEMO_USER_EMAIL=',
    'PRIZM_BOOTSTRAP_DEMO_USER_PASSWORD=',
  ].join('\n')
}

function deterministicRandom() {
  let call = 0
  return (length) => Buffer.alloc(length, (call += 1))
}

test('generates deterministic first-party TXT, text-layer PDF, and integrity manifest', (t) => {
  const allowedRoot = temporaryDirectory(t)
  const output = join(allowedRoot, 'fixtures')
  const first = generateDemoFixtures(output, { allowedRoot })
  const firstTxt = readFileSync(join(output, 'prizm-clean-clone-synthetic.txt'))
  const firstPdf = readFileSync(join(output, 'prizm-clean-clone-synthetic.pdf'))
  const second = generateDemoFixtures(output, { allowedRoot })

  assert.equal(first.manifest.synthetic, true)
  assert.match(first.manifest.provenance, /no real person/i)
  assert.match(firstTxt.toString('utf8'), /GLASS ORBIT TEXT EVIDENCE 2026/)
  assert.equal(firstPdf.subarray(0, 8).toString('ascii'), '%PDF-1.4')
  assert.match(firstPdf.toString('ascii'), /AMBER PAGE SOURCE EVIDENCE 2026/)
  assert.deepEqual(first.manifest, second.manifest)
  assert.deepEqual(firstPdf, readFileSync(join(output, 'prizm-clean-clone-synthetic.pdf')))
  assert.equal(readFixtureManifest(first.manifestPath).documents.length, 2)
})

test('keeps fixtures under the allowed local root and rejects tampering', (t) => {
  const allowedRoot = temporaryDirectory(t)
  assert.throws(
    () => generateDemoFixtures(resolve(allowedRoot, '..', 'outside'), { allowedRoot }),
    /ignored local directory/,
  )
  const generated = generateDemoFixtures(join(allowedRoot, 'fixtures'), { allowedRoot })
  writeFileSync(join(generated.outputDirectory, 'prizm-clean-clone-synthetic.txt'), 'tampered')
  assert.throws(() => readFixtureManifest(generated.manifestPath), /integrity check failed/)
})

test('creates valid deterministic PDF syntax and rejects non-ASCII PDF text', () => {
  const pdf = createTextLayerPdf(['Synthetic page', 'Marker'])
  assert.match(pdf.toString('ascii'), /xref\n0 6/)
  assert.match(pdf.toString('ascii'), /startxref\n\d+\n%%EOF/)
  assert.throws(() => createTextLayerPdf(['한글']), /printable ASCII/)
})

test('prepares unique isolated env files without overwriting or printing secrets', (t) => {
  const directory = temporaryDirectory(t)
  const examplePath = join(directory, '.env.example')
  const firstPath = join(directory, '.env.first')
  const secondPath = join(directory, '.env.second')
  writeFileSync(examplePath, environmentTemplate())
  const randomBytesFunction = deterministicRandom()

  const first = prepareDemoEnvironment({
    examplePath,
    envPath: firstPath,
    portOverrides: { db: 15433, backend: 18081, frontend: 15174 },
    randomBytesFunction,
  })
  const second = prepareDemoEnvironment({
    examplePath,
    envPath: secondPath,
    portOverrides: { db: 15434, backend: 18082, frontend: 15175 },
    randomBytesFunction,
  })
  const firstValues = parseEnvFile(readFileSync(firstPath, 'utf8'))
  const secondValues = parseEnvFile(readFileSync(secondPath, 'utf8'))

  assert.match(first.projectName, /^prizm-clean-clone-/)
  assert.notEqual(first.projectName, second.projectName)
  assert.equal(firstValues.PRIZM_DB_PORT, '15433')
  assert.equal(firstValues.SERVER_PORT, '18081')
  assert.equal(firstValues.PRIZM_FRONTEND_PORT, '15174')
  assert.equal(firstValues.PRIZM_BOOTSTRAP_DEMO_USER_ENABLED, 'true')
  assert.equal(firstValues.PRIZM_BOOTSTRAP_DEMO_USER_EMAIL, 'demo@prizm.local')
  assert.ok(firstValues.PRIZM_JWT_SECRET.length >= 32)
  assert.ok(firstValues.PRIZM_DB_PASSWORD.length >= 32)
  assert.ok(Buffer.byteLength(firstValues.PRIZM_BOOTSTRAP_DEMO_USER_PASSWORD, 'utf8') <= 72)
  assert.notEqual(firstValues.PRIZM_DB_PASSWORD, secondValues.PRIZM_DB_PASSWORD)
  assert.throws(
    () => prepareDemoEnvironment({ examplePath, envPath: firstPath, randomBytesFunction }),
    /refusing to overwrite/,
  )
})

test('creates .env with POSIX 0600 mode', { skip: process.platform === 'win32' }, (t) => {
  const directory = temporaryDirectory(t)
  const examplePath = join(directory, '.env.example')
  const envPath = join(directory, '.env')
  writeFileSync(examplePath, environmentTemplate())
  prepareDemoEnvironment({ examplePath, envPath, randomBytesFunction: deterministicRandom() })
  assert.equal(statSync(envPath).mode & 0o777, 0o600)
})

test('disables bootstrap explicitly and validates safe CLI overrides', (t) => {
  const directory = temporaryDirectory(t)
  const examplePath = join(directory, '.env.example')
  const envPath = join(directory, '.env')
  writeFileSync(examplePath, environmentTemplate())
  prepareDemoEnvironment({ examplePath, envPath, randomBytesFunction: deterministicRandom() })
  disableDemoBootstrap(envPath)
  assert.equal(parseEnvFile(readFileSync(envPath, 'utf8')).PRIZM_BOOTSTRAP_DEMO_USER_ENABLED, 'false')
  assert.deepEqual(parsePrepareArguments([
    '--project-name', 'prizm-clean-clone-manual',
    '--db-port', '15433',
    '--backend-port', '18081',
    '--frontend-port', '15174',
  ]), {
    projectName: 'prizm-clean-clone-manual',
    portOverrides: { db: '15433', backend: '18081', frontend: '15174' },
  })
  assert.throws(() => parsePrepareArguments(['--db-port', '70000']), /between 1 and 65535/)
})

test('builds explicit isolated Compose invocations for two different env files', (t) => {
  const directory = temporaryDirectory(t)
  const firstEnv = join(directory, '.env.first')
  const secondEnv = join(directory, '.env.second')
  writeFileSync(firstEnv, 'COMPOSE_PROJECT_NAME=prizm-clean-clone-first01\n')
  writeFileSync(secondEnv, 'COMPOSE_PROJECT_NAME=prizm-clean-clone-second02\n')

  const first = buildCleanCloneComposeInvocation({
    composeArguments: ['config', '--quiet'],
    envPath: firstEnv,
    dockerExecutable: 'docker-test',
  })
  const second = buildCleanCloneComposeInvocation({
    composeArguments: ['up', '--detach'],
    envPath: secondEnv,
    dockerExecutable: 'docker-test',
  })

  assert.notEqual(first.projectName, second.projectName)
  assert.deepEqual(first.arguments, [
    'compose',
    '--file', 'compose.yaml',
    '--env-file', '.env',
    '--project-name', 'prizm-clean-clone-first01',
    'config', '--quiet',
  ])
  assert.equal(second.arguments.at(6), 'prizm-clean-clone-second02')
})

test('uses a Docker executable outside PATH and removes Compose environment overrides', (t) => {
  const directory = temporaryDirectory(t)
  const envPath = join(directory, '.env')
  writeFileSync(envPath, 'COMPOSE_PROJECT_NAME=prizm-clean-clone-fallback01\n')
  let captured
  const result = runCleanCloneCompose({
    composeArguments: ['config', '--quiet'],
    envPath,
    environment: {
      PATH: '',
      COMPOSE_FILE: 'untrusted.yaml',
      compose_project_name: 'untrusted-project',
      COMPOSE_ENV_FILES: 'untrusted.env',
      SAFE_VALUE: 'kept',
    },
    platform: 'win32',
    locator: () => ({ path: 'X:\\DockerDesktop\\docker.exe', source: 'known installation location' }),
    runner: (executable, args, options) => {
      captured = { executable, args, options }
      return { ok: true, status: 0, output: '' }
    },
  })

  assert.equal(result.projectName, 'prizm-clean-clone-fallback01')
  assert.equal(captured.executable, 'X:\\DockerDesktop\\docker.exe')
  assert.equal(captured.options.cwd.endsWith('PRIZM-clean-clone-rebuild'), true)
  assert.equal(captured.options.environment.SAFE_VALUE, 'kept')
  assert.equal(Object.keys(captured.options.environment).some((key) => key.toUpperCase() === 'COMPOSE_FILE'), false)
  assert.equal(Object.keys(captured.options.environment).some((key) => key.toUpperCase() === 'COMPOSE_PROJECT_NAME'), false)
  assert.equal(Object.keys(captured.options.environment).some((key) => key.toUpperCase() === 'COMPOSE_ENV_FILES'), false)
})

test('rejects secret-rendering config, Compose overrides, and volume deletion', (t) => {
  assert.throws(() => validateComposeArguments(['config']), /only accepts --quiet/)
  assert.throws(() => validateComposeArguments(['config', '--quiet', '--environment']), /only accepts --quiet/)
  assert.throws(() => validateComposeArguments(['down', '--volumes']), /preserve volumes/)
  assert.throws(() => validateComposeArguments(['down', '-v']), /preserve volumes/)
  assert.throws(() => validateComposeArguments(['up', '--file=other.yaml']), /overrides are not accepted/)
  assert.throws(() => validateComposeArguments(['up', '-fother.yaml']), /overrides are not accepted/)
  assert.throws(() => validateComposeArguments(['up', '--project-name', 'other']), /overrides are not accepted/)
  const directory = temporaryDirectory(t)
  const envPath = join(directory, '.env')
  writeFileSync(envPath, [
    'COMPOSE_PROJECT_NAME=prizm-clean-clone-safe001',
    'COMPOSE_FILE=other.yaml',
  ].join('\n'))
  assert.throws(
    () => buildCleanCloneComposeInvocation({
      composeArguments: ['config', '--quiet'],
      envPath,
      dockerExecutable: 'docker-test',
    }),
    /must not override/,
  )
  const cleaned = sanitizedComposeEnvironment({ COMPOSE_FILE: 'other.yaml', SAFE: 'yes' })
  assert.deepEqual(cleaned, { SAFE: 'yes' })
})

test('parses only unambiguous env data, requires disabled bootstrap, and enforces BCrypt bytes', (t) => {
  assert.deepEqual(parseEnvFile('A=one\nB="two"\n# comment\n'), { A: 'one', B: 'two' })
  assert.throws(() => parseEnvFile('A=one\nA=two\n'), /Duplicate/)
  const directory = temporaryDirectory(t)
  const envPath = join(directory, '.env')
  writeFileSync(envPath, [
    'SERVER_PORT=8181',
    'PRIZM_BOOTSTRAP_DEMO_USER_ENABLED=false',
    'PRIZM_BOOTSTRAP_DEMO_USER_EMAIL=DEMO@Prizm.Local',
    'PRIZM_BOOTSTRAP_DEMO_USER_PASSWORD=private-demo-password',
  ].join('\n'))
  assert.deepEqual(readDemoConfiguration(envPath, {}), {
    email: 'demo@prizm.local',
    password: 'private-demo-password',
    baseUrl: 'http://127.0.0.1:8181',
    bootstrapEnabled: false,
  })
  writeFileSync(envPath, readFileSync(envPath, 'utf8').replace('ENABLED=false', 'ENABLED=true'))
  assert.throws(() => readDemoConfiguration(envPath, {}), /Disable the one-time demo bootstrap/)
  writeFileSync(envPath, readFileSync(envPath, 'utf8')
    .replace('ENABLED=true', 'ENABLED=false')
    .replace('private-demo-password', '가'.repeat(25)))
  assert.throws(() => readDemoConfiguration(envPath, {}), /72 UTF-8 bytes/)
})

test('rejects every non-loopback credential destination before calling fetch', async (t) => {
  const allowedRoot = temporaryDirectory(t)
  const { manifestPath } = generateDemoFixtures(join(allowedRoot, 'fixtures'), { allowedRoot })
  let called = false
  await assert.rejects(
    verifyCleanCloneDemo({
      baseUrl: 'https://example.invalid',
      email: 'demo@prizm.local',
      password: 'private-demo-password',
      bootstrapEnabled: false,
      manifestPath,
      fetchImpl: async () => { called = true; return Response.json({}) },
    }),
    /loopback/,
  )
  assert.equal(called, false)
})

test('requires an explicit disabled-bootstrap precondition', async (t) => {
  const allowedRoot = temporaryDirectory(t)
  const { manifestPath } = generateDemoFixtures(join(allowedRoot, 'fixtures'), { allowedRoot })
  await assert.rejects(
    verifyCleanCloneDemo({
      baseUrl: 'http://127.0.0.1:8080',
      email: 'demo@prizm.local',
      password: 'private-demo-password',
      bootstrapEnabled: true,
      manifestPath,
      fetchImpl: async () => Response.json({}),
    }),
    /explicitly disabled/,
  )
})

test('never follows a credential-bearing HTTP redirect', async (t) => {
  const allowedRoot = temporaryDirectory(t)
  const { manifestPath } = generateDemoFixtures(join(allowedRoot, 'fixtures'), { allowedRoot })
  let redirectedRequests = 0
  const target = createHttpServer((_request, response) => {
    redirectedRequests += 1
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end('{}')
  })
  target.listen(0, '127.0.0.1')
  await once(target, 'listening')
  t.after(() => target.close())
  const targetPort = target.address().port

  const redirector = createHttpServer((_request, response) => {
    response.writeHead(307, { Location: `http://127.0.0.1:${targetPort}/credential-target` })
    response.end()
  })
  redirector.listen(0, '127.0.0.1')
  await once(redirector, 'listening')
  t.after(() => redirector.close())
  const redirectorPort = redirector.address().port

  await assert.rejects(
    verifyCleanCloneDemo({
      baseUrl: `http://127.0.0.1:${redirectorPort}`,
      email: 'demo@prizm.local',
      password: 'private-demo-password',
      bootstrapEnabled: false,
      manifestPath,
    }),
    /fetch failed|redirect/i,
  )
  assert.equal(redirectedRequests, 0)
})

test('verifies USER login, TXT/PDF ACTIVE sources, and logged-out 401', async (t) => {
  const allowedRoot = temporaryDirectory(t)
  const { manifestPath } = generateDemoFixtures(join(allowedRoot, 'fixtures'), { allowedRoot })
  const polls = new Map()
  const records = new Map([
    ['PRIZM Clean Clone Synthetic TXT', { documentId: 11, versionId: 101, sourceType: 'TEXT_CHUNK', marker: 'GLASS ORBIT TEXT EVIDENCE 2026' }],
    ['PRIZM Clean Clone Synthetic PDF', { documentId: 12, versionId: 102, sourceType: 'PAGE', marker: 'AMBER PAGE SOURCE EVIDENCE 2026' }],
  ])
  const fetchImpl = async (url, init) => {
    const { pathname } = new URL(url)
    if (pathname === '/api/auth/login') {
      assert.equal(init.redirect, 'error')
      assert.equal(JSON.parse(init.body).password, 'private-demo-password')
      return Response.json({
        accessToken: 'temporary-token',
        tokenType: 'Bearer',
        user: { id: 7, email: 'demo@prizm.local', role: 'USER' },
      })
    }
    if (pathname === '/api/documents' && init.method === 'POST') {
      assert.equal(init.headers.Authorization, 'Bearer temporary-token')
      const record = records.get(init.body.get('title'))
      assert.ok(record)
      return Response.json(record, { status: 201 })
    }
    if (pathname === '/api/documents' && init.method === 'GET' && init.headers?.Authorization) {
      assert.equal(init.headers.Authorization, 'Bearer temporary-token')
      return Response.json([])
    }
    if (pathname === '/api/documents' && init.method === 'GET') {
      assert.equal(init.headers, undefined)
      return Response.json({}, { status: 401 })
    }
    const detail = /^\/api\/documents\/(\d+)$/.exec(pathname)
    if (detail) {
      assert.equal(init.headers.Authorization, 'Bearer temporary-token')
      const documentId = Number(detail[1])
      const record = [...records.values()].find((candidate) => candidate.documentId === documentId)
      const count = (polls.get(documentId) ?? 0) + 1
      polls.set(documentId, count)
      const active = count > 1
      return Response.json({
        activeVersionId: active ? record.versionId : null,
        versions: [{
          versionId: record.versionId,
          status: active ? 'ACTIVE' : 'QUARANTINED',
          processingStatus: active ? 'COMPLETED' : 'PROCESSING',
        }],
      })
    }
    if (pathname === '/api/career-evidence/search') {
      assert.equal(init.headers.Authorization, 'Bearer temporary-token')
      const query = JSON.parse(init.body).query
      const record = query.includes('GLASS ORBIT')
        ? records.get('PRIZM Clean Clone Synthetic TXT')
        : records.get('PRIZM Clean Clone Synthetic PDF')
      return Response.json([{
        documentId: record.documentId,
        documentVersionId: record.versionId,
        sourceType: record.sourceType,
        sourceIndex: 1,
        content: `SEARCH MARKER: ${record.marker}`,
      }])
    }
    return Response.json({}, { status: 404 })
  }
  let clock = 0
  const result = await verifyCleanCloneDemo({
    baseUrl: 'http://127.0.0.1:8080',
    email: 'demo@prizm.local',
    password: 'private-demo-password',
    bootstrapEnabled: false,
    manifestPath,
    fetchImpl,
    timeoutMs: 10,
    pollIntervalMs: 1,
    now: () => clock,
    sleep: async (duration) => { clock += duration },
  })
  assert.equal(result.documentsVerified, 2)
  assert.equal(result.unauthenticatedAccessRejected, true)
  assert.deepEqual(result.uploads.map((upload) => upload.sourceType), ['TEXT_CHUNK', 'PAGE'])
})

test('rejects a reused database that already contains demo USER documents', async (t) => {
  const allowedRoot = temporaryDirectory(t)
  const { manifestPath } = generateDemoFixtures(join(allowedRoot, 'fixtures'), { allowedRoot })
  const fetchImpl = async (url) => {
    const { pathname } = new URL(url)
    if (pathname === '/api/auth/login') {
      return Response.json({
        accessToken: 'temporary-token',
        tokenType: 'Bearer',
        user: { id: 7, email: 'demo@prizm.local', role: 'USER' },
      })
    }
    if (pathname === '/api/documents') return Response.json([{ documentId: 999 }])
    return Response.json({}, { status: 404 })
  }
  await assert.rejects(
    verifyCleanCloneDemo({
      baseUrl: 'http://localhost:8080',
      email: 'demo@prizm.local',
      password: 'private-demo-password',
      bootstrapEnabled: false,
      manifestPath,
      fetchImpl,
    }),
    /not an isolated clean-clone database/,
  )
})

test('distinguishes failed processing from polling timeout', async () => {
  const upload = { document: { key: 'txt' }, documentId: 11, versionId: 101 }
  await assert.rejects(
    waitForActiveVersion({
      fetchImpl: async () => Response.json({
        activeVersionId: null,
        versions: [{ versionId: 101, status: 'FAILED', processingStatus: 'FAILED', processingErrorCode: 'EMBEDDING_FAILED' }],
      }),
      baseUrl: 'http://127.0.0.1:8080',
      token: 'temporary-token',
      upload,
    }),
    /EMBEDDING_FAILED/,
  )
  let clock = 0
  await assert.rejects(
    waitForActiveVersion({
      fetchImpl: async () => Response.json({
        activeVersionId: null,
        versions: [{ versionId: 101, status: 'QUARANTINED', processingStatus: 'PROCESSING' }],
      }),
      baseUrl: 'http://127.0.0.1:8080',
      token: 'temporary-token',
      upload,
      timeoutMs: 2,
      pollIntervalMs: 1,
      now: () => clock,
      sleep: async (duration) => { clock += duration },
    }),
    /Timed out/,
  )
})

test('redacts secrets from diagnostic messages', () => {
  assert.equal(
    redactSecrets('password private-demo-password token temporary-token', [
      'private-demo-password',
      'temporary-token',
    ]),
    'password [REDACTED] token [REDACTED]',
  )
})

test('finds the Windows per-user Ollama fallback without changing PATH', () => {
  const environment = { PATH: '', LOCALAPPDATA: 'C:\\Users\\person\\AppData\\Local' }
  const expected = 'C:\\Users\\person\\AppData\\Local\\Programs\\Ollama\\ollama.exe'
  assert.ok(executableCandidates('ollama', { environment, platform: 'win32' }).includes(expected))
  assert.deepEqual(findExecutable('ollama', {
    environment,
    platform: 'win32',
    fileExists: (path) => path === expected,
  }), { path: expected, source: 'known installation location' })
})

test('includes the Windows per-user Docker Desktop fallback and validates port overrides', () => {
  const environment = { PATH: '', LOCALAPPDATA: 'C:\\Users\\person\\AppData\\Local' }
  const expected = 'C:\\Users\\person\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe'
  assert.ok(executableCandidates('docker', { environment, platform: 'win32' }).includes(expected))
  assert.deepEqual(parsePrerequisiteArguments([
    '--db-port', '15433',
    '--backend-port', '18081',
    '--frontend-port', '15174',
  ]), { portOverrides: { database: 15433, backend: 18081, frontend: 15174 } })
  assert.throws(
    () => parsePrerequisiteArguments(['--db-port', '15433', '--backend-port', '15433']),
    /must be different/,
  )
})

test('reads only safe configured port keys', (t) => {
  const directory = temporaryDirectory(t)
  const envPath = join(directory, '.env')
  writeFileSync(envPath, [
    'SERVER_PORT=18081',
    'PRIZM_FRONTEND_PORT=15174',
    'PRIZM_DB_PORT=15433',
    'PRIZM_DB_PASSWORD=must-not-be-returned',
  ].join('\n'))
  assert.deepEqual(readConfiguredPorts({ envPath }), {
    database: 15433,
    backend: 18081,
    frontend: 15174,
  })
  assert.throws(
    () => mergeConfiguredPorts(
      { database: 5432, backend: 8080, frontend: 5173 },
      { database: 8080 },
    ),
    /must be different/,
  )
})

test('requires the project Java, Node, and npm versions', () => {
  assert.deepEqual(assessRequiredToolVersions({
    java: 'openjdk version "17.0.12" 2024-07-16',
    node: 'v22.17.0',
    npm: '10.9.2',
  }), { java: true, node: true, npm: true })
  assert.deepEqual(assessRequiredToolVersions({
    java: 'java version "1.8.0_401"',
    node: 'v20.19.0',
    npm: '10.8.0',
  }), { java: false, node: false, npm: false })
})

test('confirms audited Ollama model digest and 1024-dimensional embedding', async () => {
  const fetchImpl = async (url) => {
    const { pathname } = new URL(url)
    if (pathname === '/api/version') return Response.json({ version: '0.32.3' })
    if (pathname === '/api/tags') {
      return Response.json({ models: [{ name: 'bge-m3:latest', digest: `sha256:${AUDITED_BGE_M3_MANIFEST_SHA256}` }] })
    }
    if (pathname === '/api/embed') return Response.json({ embeddings: [Array(1024).fill(0.01)] })
    return Response.json({}, { status: 404 })
  }
  assert.deepEqual(await inspectOllama({ fetchImpl }), {
    ok: true,
    version: '0.32.3',
    model: 'bge-m3:latest',
    digest: AUDITED_BGE_M3_MANIFEST_SHA256,
    dimensions: 1024,
  })
  await assert.rejects(
    inspectOllama({ fetchImpl, expectedDigest: '0'.repeat(64) }),
    /differs from the audited manifest/,
  )
})

test('reports Docker versions and existing PRIZM resources without secret values', () => {
  const runner = (_path, args) => {
    const command = args.join(' ')
    if (command === '--version') return { ok: true, output: 'Docker version 29.0.0' }
    if (command === 'compose version') return { ok: true, output: 'Docker Compose version v5.0.0' }
    if (command.startsWith('info ')) return { ok: true, output: '29.0.0' }
    if (command.startsWith('compose ls')) return { ok: true, output: '[{"Name":"prizm-existing"}]' }
    if (command.startsWith('container ls')) return { ok: true, output: '{"Names":"prizm-existing-backend-1","Labels":"com.docker.compose.project=prizm-existing"}' }
    if (command.startsWith('volume ls')) return { ok: true, output: '{"Name":"prizm-existing_data","Labels":"com.docker.compose.project=prizm-existing"}' }
    return { ok: false, output: '' }
  }
  const state = inspectDocker({ path: 'docker', source: 'PATH' }, runner)
  assert.equal(state.ok, true)
  assert.deepEqual(state.projects, ['prizm-existing'])
  assert.deepEqual(state.containers, ['prizm-existing-backend-1'])
  assert.deepEqual(state.volumes, ['prizm-existing_data'])
  const report = formatPrerequisiteReport({
    ok: true,
    tools: {
      java: 'openjdk version "17.0.12"',
      node: 'v22.17.0',
      npm: '10.9.2',
      compatible: { java: true, node: true, npm: true },
      dockerLocation: 'docker.exe (PATH)',
      ollamaLocation: null,
    },
    docker: state,
    ollama: { ok: true, version: '0.32.3', model: 'bge-m3:latest', digest: AUDITED_BGE_M3_MANIFEST_SHA256, dimensions: 1024 },
    ports: { backend: { port: 18081, available: true } },
  })
  assert.doesNotMatch(report, /password|token/i)
})
