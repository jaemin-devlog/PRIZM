import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

import { findExecutable, runCommand } from './check-clean-clone-prerequisites.mjs'
import { validateComposeProjectName } from './prepare-clean-clone-demo-env.mjs'
import { parseEnvFile } from './verify-clean-clone-demo.mjs'

const repositoryRoot = resolve(import.meta.dirname, '..')
const defaultEnvPath = resolve(repositoryRoot, '.env')
const GENERATED_PROJECT_PATTERN = /^prizm-clean-clone-[a-z0-9][a-z0-9_-]{5,}$/
const FORBIDDEN_LONG_OVERRIDE = /^(?:--file|--project-name|--env-file|--project-directory)(?:=|$)/

function isForbiddenOverride(argument) {
  return FORBIDDEN_LONG_OVERRIDE.test(argument)
    || argument === '-f'
    || argument.startsWith('-f=')
    || (argument.startsWith('-f') && argument.length > 2)
    || argument === '-p'
    || argument.startsWith('-p=')
    || (argument.startsWith('-p') && argument.length > 2)
}

function deletesVolumes(argument) {
  return argument === '-v'
    || argument.startsWith('-v=')
    || (argument.startsWith('-v') && argument.length > 2)
    || argument === '--volumes'
    || argument.startsWith('--volumes=')
}

export function readCleanCloneProjectName(envPath = defaultEnvPath) {
  const values = parseEnvFile(readFileSync(envPath, 'utf8'))
  if (Object.hasOwn(values, 'COMPOSE_FILE') || Object.hasOwn(values, 'COMPOSE_ENV_FILES')) {
    throw new Error('.env must not override the clean-clone Compose file')
  }
  const projectName = validateComposeProjectName(values.COMPOSE_PROJECT_NAME ?? '')
  if (!GENERATED_PROJECT_PATTERN.test(projectName)) {
    throw new Error('COMPOSE_PROJECT_NAME must be a unique prizm-clean-clone-* name created for this run')
  }
  return projectName
}

export function validateComposeArguments(args) {
  if (!Array.isArray(args) || args.length === 0 || args[0].startsWith('-')) {
    throw new Error('A Docker Compose command is required')
  }
  if (args.some(isForbiddenOverride)) {
    throw new Error('Compose file, env file, and project name overrides are not accepted')
  }
  if (args[0] === 'config' && (args.length !== 2 || args[1] !== '--quiet')) {
    throw new Error('Compose config only accepts --quiet so rendered secrets are not printed')
  }
  if (args[0] === 'down' && args.some(deletesVolumes)) {
    throw new Error('Clean-clone shutdown must preserve volumes; down -v/--volumes is not allowed')
  }
  return Object.freeze([...args])
}

export function sanitizedComposeEnvironment(environment = process.env, envPath = defaultEnvPath) {
  const protectedKeys = new Set([
    ...Object.keys(parseEnvFile(readFileSync(envPath, 'utf8'))),
    'COMPOSE_FILE',
    'COMPOSE_PROJECT_NAME',
    'COMPOSE_ENV_FILES',
  ].map((key) => key.toUpperCase()))
  return Object.fromEntries(
    Object.entries(environment).filter(([key]) => !protectedKeys.has(key.toUpperCase())),
  )
}

export function buildCleanCloneComposeInvocation({
  composeArguments,
  envPath = defaultEnvPath,
  dockerExecutable,
} = {}) {
  const projectName = readCleanCloneProjectName(envPath)
  const userArguments = validateComposeArguments(composeArguments)
  if (!dockerExecutable) throw new Error('Docker executable is required')
  return Object.freeze({
    executable: dockerExecutable,
    projectName,
    arguments: Object.freeze([
      'compose',
      '--file', 'compose.yaml',
      '--env-file', '.env',
      '--project-name', projectName,
      ...userArguments,
    ]),
  })
}

export function runCleanCloneCompose({
  composeArguments,
  envPath = defaultEnvPath,
  environment = process.env,
  platform = process.platform,
  locator = findExecutable,
  runner = runCommand,
} = {}) {
  const docker = locator('docker', { environment, platform })
  if (!docker) throw new Error('Docker CLI was not found in PATH or a known installation location')
  const invocation = buildCleanCloneComposeInvocation({
    composeArguments,
    envPath,
    dockerExecutable: docker.path,
  })
  const result = runner(invocation.executable, invocation.arguments, {
    cwd: repositoryRoot,
    environment: sanitizedComposeEnvironment(environment, envPath),
    stdio: 'inherit',
    timeoutMs: 0,
  })
  if (!result.ok) {
    throw new Error(`Docker Compose command failed with exit code ${result.status ?? 'unknown'}`)
  }
  return Object.freeze({ projectName: invocation.projectName, status: result.status ?? 0 })
}

const isMain = process.argv[1]
  && import.meta.url === pathToFileURL(resolve(process.argv[1])).href

if (isMain) {
  try {
    runCleanCloneCompose({ composeArguments: process.argv.slice(2) })
  } catch (error) {
    console.error(error instanceof Error ? error.message : 'Docker Compose command failed')
    process.exitCode = 1
  }
}
