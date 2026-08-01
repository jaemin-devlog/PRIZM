import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync, statSync } from 'node:fs'
import { createServer } from 'node:net'
import { dirname, join, posix, resolve, win32 } from 'node:path'
import { pathToFileURL } from 'node:url'

import { validatePort } from './prepare-clean-clone-demo-env.mjs'
import { normalizeLoopbackBaseUrl } from './verify-clean-clone-demo.mjs'

const repositoryRoot = resolve(import.meta.dirname, '..')
export const AUDITED_BGE_M3_MANIFEST_SHA256 = '7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab'
const EXPECTED_MODEL_NAME = 'bge-m3:latest'
const EXPECTED_EMBEDDING_DIMENSIONS = 1024
const REQUIRED_JAVA_MAJOR = 17
const REQUIRED_NODE_VERSION = 'v22.17.0'
const REQUIRED_NPM_VERSION = '10.9.2'

function isFile(path, exists = existsSync) {
  try {
    return exists(path) && statSync(path).isFile()
  } catch {
    return false
  }
}

function pathEnvironment(environment) {
  return environment.PATH ?? environment.Path ?? environment.path ?? ''
}

export function executableCandidates(name, {
  environment = process.env,
  platform = process.platform,
} = {}) {
  const windows = platform === 'win32'
  const pathImplementation = windows ? win32 : posix
  const extensions = windows
    ? (environment.PATHEXT ?? '.COM;.EXE;.BAT;.CMD').split(';').filter(Boolean)
    : ['']
  const hasExtension = windows && /\.[A-Za-z0-9]+$/.test(name)
  const names = hasExtension ? [name] : extensions.map((extension) => `${name}${extension.toLowerCase()}`)
  const candidates = []
  for (const directory of pathEnvironment(environment).split(pathImplementation.delimiter).filter(Boolean)) {
    for (const candidateName of names) candidates.push(pathImplementation.join(directory, candidateName))
  }

  if (windows && name.toLowerCase() === 'docker') {
    const local = environment.LOCALAPPDATA
    const programs = environment.ProgramFiles
    if (local) {
      candidates.push(pathImplementation.join(local, 'Docker', 'resources', 'bin', 'docker.exe'))
      candidates.push(pathImplementation.join(local, 'Docker', 'Docker', 'resources', 'bin', 'docker.exe'))
      candidates.push(pathImplementation.join(local, 'Programs', 'DockerDesktop', 'resources', 'bin', 'docker.exe'))
    }
    if (programs) {
      candidates.push(pathImplementation.join(programs, 'Docker', 'Docker', 'resources', 'bin', 'docker.exe'))
    }
  }
  if (windows && name.toLowerCase() === 'ollama' && environment.LOCALAPPDATA) {
    candidates.push(pathImplementation.join(environment.LOCALAPPDATA, 'Programs', 'Ollama', 'ollama.exe'))
  }
  return [...new Set(candidates)]
}

export function findExecutable(name, options = {}) {
  const environment = options.environment ?? process.env
  const platform = options.platform ?? process.platform
  const pathImplementation = platform === 'win32' ? win32 : posix
  const exists = options.fileExists ?? ((path) => isFile(path))
  const candidates = executableCandidates(name, { ...options, environment, platform })
  const pathDirectories = new Set(pathEnvironment(environment)
    .split(pathImplementation.delimiter)
    .filter(Boolean)
    .map((value) => pathImplementation.resolve(value).toLowerCase()))
  const executablePath = candidates.find((candidate) => exists(candidate))
  if (!executablePath) return null
  return Object.freeze({
    path: executablePath,
    source: pathDirectories.has(
      pathImplementation.dirname(pathImplementation.resolve(executablePath)).toLowerCase(),
    )
      ? 'PATH'
      : 'known installation location',
  })
}

export function runCommand(executable, args, {
  cwd,
  environment,
  stdio = 'pipe',
  timeoutMs = 20_000,
} = {}) {
  const result = spawnSync(executable, args, {
    cwd,
    env: environment,
    encoding: stdio === 'pipe' ? 'utf8' : undefined,
    stdio,
    windowsHide: true,
    timeout: timeoutMs > 0 ? timeoutMs : undefined,
  })
  return Object.freeze({
    ok: result.status === 0 && !result.error,
    status: result.status,
    output: `${result.stdout ?? ''}\n${result.stderr ?? ''}`.trim(),
  })
}

function firstNonEmptyLine(value) {
  return String(value).split(/\r?\n/).map((line) => line.trim()).find(Boolean) ?? ''
}

function parseAllowedEnvValues(content, keys) {
  const values = {}
  for (const line of content.split(/\r?\n/)) {
    const match = /^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/.exec(line)
    if (match && keys.has(match[1])) values[match[1]] = match[2].trim()
  }
  return values
}

export function readConfiguredPorts({
  envPath = resolve(repositoryRoot, '.env'),
  fileExists = existsSync,
  readFile = readFileSync,
} = {}) {
  const defaults = { database: 5432, backend: 8080, frontend: 5173 }
  if (!fileExists(envPath)) return Object.freeze(defaults)
  const values = parseAllowedEnvValues(
    readFile(envPath, 'utf8'),
    new Set(['PRIZM_DB_PORT', 'SERVER_PORT', 'PRIZM_FRONTEND_PORT']),
  )
  const parsed = {
    database: Number(values.PRIZM_DB_PORT ?? defaults.database),
    backend: Number(values.SERVER_PORT ?? defaults.backend),
    frontend: Number(values.PRIZM_FRONTEND_PORT ?? defaults.frontend),
  }
  for (const [name, port] of Object.entries(parsed)) {
    if (!Number.isInteger(port) || port < 1 || port > 65_535) {
      throw new Error(`Configured ${name} port is invalid`)
    }
  }
  return Object.freeze(parsed)
}

export function mergeConfiguredPorts(configured, overrides = {}) {
  const ports = { ...configured, ...overrides }
  if (Object.keys(ports).length !== 3
    || new Set(Object.values(ports)).size !== Object.keys(ports).length) {
    throw new Error('Database, backend, and frontend host ports must be different')
  }
  return Object.freeze(ports)
}

export function checkPortAvailable(port, host = '127.0.0.1') {
  return new Promise((resolveCheck) => {
    const server = createServer()
    server.unref()
    server.once('error', () => resolveCheck(false))
    server.listen({ host, port, exclusive: true }, () => {
      server.close(() => resolveCheck(true))
    })
  })
}

function parseJsonOutput(output) {
  if (output.trim() === '') return []
  try {
    const parsed = JSON.parse(output)
    return Array.isArray(parsed) ? parsed : [parsed]
  } catch {
    return output.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line))
  }
}

function prizmResourceNames(entries) {
  const names = []
  for (const entry of entries) {
    const serialized = JSON.stringify(entry)
    if (!/prizm/i.test(serialized)) continue
    const name = entry.Name ?? entry.Names ?? entry.name
    if (typeof name === 'string' && name !== '') names.push(name)
  }
  return [...new Set(names)].sort()
}

export function inspectDocker(docker, runner = runCommand) {
  if (!docker) return Object.freeze({ ok: false, reason: 'Docker CLI was not found' })
  const cli = runner(docker.path, ['--version'])
  const compose = runner(docker.path, ['compose', 'version'])
  const server = runner(docker.path, ['info', '--format', '{{.ServerVersion}}'])
  if (!cli.ok || !compose.ok || !server.ok) {
    return Object.freeze({ ok: false, reason: 'Docker CLI, Compose, or Docker server is unavailable' })
  }

  const projectsResult = runner(docker.path, ['compose', 'ls', '--format', 'json'])
  const containersResult = runner(docker.path, [
    'container', 'ls', '--all', '--filter', 'label=com.docker.compose.project', '--format', '{{json .}}',
  ])
  const volumesResult = runner(docker.path, [
    'volume', 'ls', '--filter', 'label=com.docker.compose.project', '--format', '{{json .}}',
  ])
  const projects = projectsResult.ok ? prizmResourceNames(parseJsonOutput(projectsResult.output)) : []
  const containers = containersResult.ok ? prizmResourceNames(parseJsonOutput(containersResult.output)) : []
  const volumes = volumesResult.ok ? prizmResourceNames(parseJsonOutput(volumesResult.output)) : []
  return Object.freeze({
    ok: true,
    cliVersion: firstNonEmptyLine(cli.output),
    composeVersion: firstNonEmptyLine(compose.output),
    serverVersion: firstNonEmptyLine(server.output),
    projects: Object.freeze(projects),
    containers: Object.freeze(containers),
    volumes: Object.freeze(volumes),
  })
}

async function responseJson(response, description) {
  if (!response.ok) throw new Error(`${description} returned HTTP ${response.status}`)
  try {
    return await response.json()
  } catch {
    throw new Error(`${description} returned invalid JSON`)
  }
}

export async function inspectOllama({
  baseUrl = 'http://127.0.0.1:11434',
  fetchImpl = fetch,
  expectedDigest = AUDITED_BGE_M3_MANIFEST_SHA256,
} = {}) {
  const origin = normalizeLoopbackBaseUrl(baseUrl)
  const request = (path, init = {}) => fetchImpl(`${origin}${path}`, {
    ...init,
    redirect: 'error',
    signal: init.signal ?? AbortSignal.timeout(30_000),
  })
  const version = await responseJson(await request('/api/version'), 'Ollama version API')
  const tags = await responseJson(await request('/api/tags'), 'Ollama tags API')
  const model = tags.models?.find((candidate) => (
    candidate.name === EXPECTED_MODEL_NAME || candidate.model === EXPECTED_MODEL_NAME
  ))
  if (!model) throw new Error(`${EXPECTED_MODEL_NAME} is not installed in Ollama`)
  const digest = String(model.digest ?? '').replace(/^sha256:/, '').toLowerCase()
  if (digest !== expectedDigest) {
    throw new Error(`${EXPECTED_MODEL_NAME} digest differs from the audited manifest`)
  }
  const embedding = await responseJson(await request('/api/embed', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ model: EXPECTED_MODEL_NAME, input: 'PRIZM clean-clone dimension check' }),
  }), 'Ollama embedding API')
  const dimensions = embedding.embeddings?.[0]?.length
  if (dimensions !== EXPECTED_EMBEDDING_DIMENSIONS) {
    throw new Error(`Expected ${EXPECTED_EMBEDDING_DIMENSIONS} embedding dimensions, received ${dimensions ?? 'none'}`)
  }
  return Object.freeze({
    ok: true,
    version: String(version.version ?? ''),
    model: EXPECTED_MODEL_NAME,
    digest,
    dimensions,
  })
}

function toolVersion(tool, args, runner) {
  if (!tool) return null
  const result = runner(tool.path, args)
  return result.ok ? firstNonEmptyLine(result.output) : null
}

function npmVersion(tool, runner) {
  if (!tool) return null
  if (process.platform === 'win32' && /npm\.cmd$/i.test(tool.path)) {
    const cli = join(dirname(tool.path), 'node_modules', 'npm', 'bin', 'npm-cli.js')
    if (isFile(cli)) return toolVersion({ path: process.execPath }, [cli, '--version'], runner)
  }
  return toolVersion(tool, ['--version'], runner)
}

export function assessRequiredToolVersions({ java, node, npm }) {
  const javaMajor = Number(/(?:java|openjdk) version "?(\d+)/i.exec(java ?? '')?.[1])
  return Object.freeze({
    java: javaMajor === REQUIRED_JAVA_MAJOR,
    node: node === REQUIRED_NODE_VERSION,
    npm: npm === REQUIRED_NPM_VERSION,
  })
}

export async function inspectCleanClonePrerequisites({
  environment = process.env,
  platform = process.platform,
  runner = runCommand,
  fetchImpl = fetch,
  portChecker = checkPortAvailable,
  locator = findExecutable,
  portOverrides = {},
} = {}) {
  const locate = (name) => locator(name, { environment, platform })
  const java = locate('java')
  const npm = locate(platform === 'win32' ? 'npm.cmd' : 'npm')
  const docker = locate('docker')
  const ollama = locate('ollama')
  const versions = {
    node: process.version,
    java: toolVersion(java, ['-version'], runner),
    npm: npmVersion(npm, runner),
  }
  const tools = Object.freeze({
    ...versions,
    compatible: assessRequiredToolVersions(versions),
    dockerLocation: docker ? `${docker.path} (${docker.source})` : null,
    ollamaLocation: ollama ? `${ollama.path} (${ollama.source})` : null,
  })
  const dockerState = inspectDocker(docker, runner)
  let ollamaState
  try {
    ollamaState = await inspectOllama({
      baseUrl: environment.PRIZM_OLLAMA_BASE_URL ?? 'http://127.0.0.1:11434',
      fetchImpl,
    })
  } catch (error) {
    ollamaState = Object.freeze({ ok: false, reason: error instanceof Error ? error.message : 'Ollama check failed' })
  }
  const ports = mergeConfiguredPorts(readConfiguredPorts(), portOverrides)
  const portStates = Object.fromEntries(await Promise.all(
    Object.entries(ports).map(async ([name, port]) => [name, Object.freeze({ port, available: await portChecker(port) })]),
  ))
  const ok = Boolean(Object.values(tools.compatible).every(Boolean) && dockerState.ok && ollamaState.ok
    && Object.values(portStates).every((state) => state.available))
  return Object.freeze({ ok, tools, docker: dockerState, ollama: ollamaState, ports: Object.freeze(portStates) })
}

export function parsePrerequisiteArguments(args) {
  const portOverrides = {}
  for (let index = 0; index < args.length; index += 2) {
    const option = args[index]
    const value = args[index + 1]
    if (!value) throw new Error(`${option} requires a value`)
    const port = Number(validatePort(value, option))
    if (option === '--db-port') portOverrides.database = port
    else if (option === '--backend-port') portOverrides.backend = port
    else if (option === '--frontend-port') portOverrides.frontend = port
    else throw new Error(`Unknown option: ${option}`)
  }
  if (new Set(Object.values(portOverrides)).size !== Object.keys(portOverrides).length) {
    throw new Error('Overridden host ports must be different')
  }
  return Object.freeze({ portOverrides: Object.freeze(portOverrides) })
}

export function formatPrerequisiteReport(report) {
  const lines = [
    `${report.ok ? '[PASS]' : '[FAIL]'} PRIZM clean-clone prerequisites`,
    `[${report.tools.compatible.java ? 'PASS' : 'FAIL'}] Java 17: ${report.tools.java ?? 'not found'}`,
    `[${report.tools.compatible.node ? 'PASS' : 'FAIL'}] Node 22.17.0: ${report.tools.node}`,
    `[${report.tools.compatible.npm ? 'PASS' : 'FAIL'}] npm 10.9.2: ${report.tools.npm ?? 'not found'}`,
    `[${report.docker.ok ? 'PASS' : 'FAIL'}] Docker: ${report.docker.ok ? `${report.docker.cliVersion}; Compose ${report.docker.composeVersion}; server ${report.docker.serverVersion}` : report.docker.reason}`,
    `[${report.ollama.ok ? 'PASS' : 'FAIL'}] Ollama: ${report.ollama.ok ? `${report.ollama.version}; ${report.ollama.model}; digest ${report.ollama.digest}; ${report.ollama.dimensions} dimensions` : report.ollama.reason}`,
  ]
  if (report.tools.dockerLocation) lines.push(`[INFO] Docker executable: ${report.tools.dockerLocation}`)
  if (report.tools.ollamaLocation) lines.push(`[INFO] Ollama executable: ${report.tools.ollamaLocation}`)
  if (report.tools.dockerLocation?.endsWith('(known installation location)')) {
    lines.push('[ACTION] Docker is outside PATH; invoke Compose with the executable path shown above.')
  }
  for (const [name, state] of Object.entries(report.ports)) {
    lines.push(`[${state.available ? 'PASS' : 'FAIL'}] ${name} host port ${state.port}: ${state.available ? 'available' : 'already in use'}`)
  }
  if (Object.values(report.ports).some((state) => !state.available)) {
    lines.push('[ACTION] Choose unused ports and rerun with --db-port, --backend-port, and --frontend-port.')
  }
  if (report.docker.ok) {
    lines.push(`[INFO] Existing PRIZM Compose projects: ${report.docker.projects.join(', ') || 'none'}`)
    lines.push(`[INFO] Existing PRIZM Compose containers: ${report.docker.containers.join(', ') || 'none'}`)
    lines.push(`[INFO] Existing PRIZM Compose volumes: ${report.docker.volumes.join(', ') || 'none'}`)
  }
  lines.push('[INFO] This check did not install software, change PATH, pull a model, or delete Docker data.')
  return `${lines.join('\n')}\n`
}

const isMain = process.argv[1]
  && import.meta.url === pathToFileURL(resolve(process.argv[1])).href

if (isMain) {
  try {
    const options = parsePrerequisiteArguments(process.argv.slice(2))
    const report = await inspectCleanClonePrerequisites(options)
    process.stdout.write(formatPrerequisiteReport(report))
    if (!report.ok) process.exitCode = 1
  } catch (error) {
    console.error(error instanceof Error ? error.message : 'Prerequisite inspection failed')
    process.exitCode = 1
  }
}
