import { randomBytes } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const repositoryRoot = resolve(import.meta.dirname, '..')
const defaultExamplePath = resolve(repositoryRoot, '.env.example')
const defaultEnvPath = resolve(repositoryRoot, '.env')

const PORT_KEYS = Object.freeze({
  db: 'PRIZM_DB_PORT',
  backend: 'SERVER_PORT',
  frontend: 'PRIZM_FRONTEND_PORT',
})

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function requiredValue(content, key) {
  const pattern = new RegExp(`^${escapeRegExp(key)}=(.*)$`, 'gm')
  const matches = [...content.matchAll(pattern)]
  if (matches.length !== 1) {
    throw new Error(`${key} must appear exactly once in .env.example`)
  }
  return matches[0][1]
}

function replaceRequiredValue(content, key, value) {
  requiredValue(content, key)
  return content.replace(new RegExp(`^${escapeRegExp(key)}=.*$`, 'm'), `${key}=${value}`)
}

function generatedSecret(randomBytesFunction, bytes = 32) {
  const value = randomBytesFunction(bytes)
  if (!Buffer.isBuffer(value) || value.length !== bytes) {
    throw new Error('The secure random source returned an invalid value')
  }
  return value.toString('base64url')
}

export function validatePort(value, label = 'port') {
  const normalized = String(value).trim()
  if (!/^[0-9]+$/.test(normalized)) {
    throw new Error(`${label} must be an integer between 1 and 65535`)
  }
  const port = Number(normalized)
  if (!Number.isSafeInteger(port) || port < 1 || port > 65_535) {
    throw new Error(`${label} must be an integer between 1 and 65535`)
  }
  return String(port)
}

export function validateComposeProjectName(value) {
  const normalized = String(value).trim()
  if (!/^[a-z0-9][a-z0-9_-]{0,62}$/.test(normalized)) {
    throw new Error('Compose project name must be 1-63 lowercase letters, digits, hyphens, or underscores')
  }
  return normalized
}

export function generateComposeProjectName(randomBytesFunction = randomBytes) {
  return validateComposeProjectName(`prizm-clean-clone-${generatedSecret(randomBytesFunction, 9).toLowerCase()}`)
}

function resolvePorts(content, overrides) {
  const ports = {}
  for (const [name, key] of Object.entries(PORT_KEYS)) {
    const configured = overrides[name] ?? requiredValue(content, key)
    ports[name] = validatePort(configured, key)
  }
  if (new Set(Object.values(ports)).size !== Object.keys(ports).length) {
    throw new Error('Database, backend, and frontend host ports must be different')
  }
  return ports
}

export function prepareCleanCloneEnvironment({
  examplePath = defaultExamplePath,
  envPath = defaultEnvPath,
  projectName,
  portOverrides = {},
  randomBytesFunction = randomBytes,
} = {}) {
  if (existsSync(envPath)) {
    throw new Error('.env already exists; refusing to overwrite local configuration')
  }

  let content = readFileSync(examplePath, 'utf8')
  const selectedProjectName = projectName
    ? validateComposeProjectName(projectName)
    : generateComposeProjectName(randomBytesFunction)
  const ports = resolvePorts(content, portOverrides)

  content = replaceRequiredValue(content, 'COMPOSE_PROJECT_NAME', selectedProjectName)
  content = replaceRequiredValue(content, PORT_KEYS.db, ports.db)
  content = replaceRequiredValue(content, PORT_KEYS.backend, ports.backend)
  content = replaceRequiredValue(content, PORT_KEYS.frontend, ports.frontend)
  content = replaceRequiredValue(
    content,
    'PRIZM_CORS_ALLOWED_ORIGINS',
    new URL(`http://localhost:${ports.frontend}`).origin,
  )
  content = replaceRequiredValue(content, 'PRIZM_JWT_SECRET', generatedSecret(randomBytesFunction, 48))
  content = replaceRequiredValue(content, 'PRIZM_DB_PASSWORD', generatedSecret(randomBytesFunction))
  content = replaceRequiredValue(content, 'PRIZM_FLYWAY_PASSWORD', generatedSecret(randomBytesFunction))

  writeFileSync(envPath, content, {
    encoding: 'utf8',
    flag: 'wx',
    mode: 0o600,
  })

  return Object.freeze({ envPath, projectName: selectedProjectName, ports: Object.freeze(ports) })
}

export function parsePrepareArguments(args) {
  const options = { portOverrides: {} }
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index]
    const value = args[index + 1]
    if (!value) throw new Error(`${argument} requires a value`)
    if (argument === '--project-name') options.projectName = validateComposeProjectName(value)
    else if (argument === '--db-port') options.portOverrides.db = validatePort(value, argument)
    else if (argument === '--backend-port') options.portOverrides.backend = validatePort(value, argument)
    else if (argument === '--frontend-port') options.portOverrides.frontend = validatePort(value, argument)
    else throw new Error(`Unknown option: ${argument}`)
    index += 1
  }
  return options
}

const isMain = process.argv[1]
  && import.meta.url === pathToFileURL(resolve(process.argv[1])).href

if (isMain) {
  try {
    const options = parsePrepareArguments(process.argv.slice(2))
    const result = prepareCleanCloneEnvironment(options)
    console.log(`Prepared an ignored .env for Compose project ${result.projectName}.`)
    console.log(`Host ports: database ${result.ports.db}, backend ${result.ports.backend}, frontend ${result.ports.frontend}.`)
    console.log('Generated server and database secrets were not printed.')
  } catch (error) {
    console.error(error instanceof Error ? error.message : 'Failed to prepare the clean-clone environment')
    process.exitCode = 1
  }
}
