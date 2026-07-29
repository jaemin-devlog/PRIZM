import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, extname, isAbsolute, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'

import { verifyRepository } from './verify-sbom.mjs'

const root = resolve(import.meta.dirname, '..')
const requiredFiles = [
  'LICENSE',
  'NOTICE',
  'docs/contest/2026-license-audit.md',
  'docs/contest/2026-sbom-model-manifest.md',
  'sbom/SHA256SUMS',
  'sbom/prizm-ai-model-manifest.json',
  'sbom/prizm-backend-runtime.cdx.json',
  'sbom/prizm-frontend.cdx.json',
  'sbom/prizm-scope-manifest.json',
]
const generatedSbomFiles = [
  'sbom/prizm-backend-runtime.cdx.json',
  'sbom/prizm-frontend.cdx.json',
]
const forbiddenLicenseIdentifiers = new Set([
  'NONE',
  'NOASSERTION',
  'UNLICENSED',
  'UNKNOWN',
])
const forbiddenBinaryExtensions = new Set([
  '.doc',
  '.docx',
  '.gguf',
  '.gz',
  '.onnx',
  '.pdf',
  '.ppt',
  '.pptx',
  '.pt',
  '.pth',
  '.safetensors',
  '.tar',
  '.zip',
])

function fail(message) {
  throw new Error(`OSS readiness verification failed: ${message}`)
}

function run(command, args, options = {}) {
  console.log(`> ${command} ${args.join(' ')}`)
  const shell = process.platform === 'win32' && /\.(?:bat|cmd)$/i.test(command)
  const result = spawnSync(command, args, {
    cwd: root,
    stdio: 'inherit',
    shell,
    ...options,
  })
  if (result.error) {
    fail(`${command} could not start: ${result.error.message}`)
  }
  if (result.status !== 0) {
    fail(`${command} exited with status ${result.status}`)
  }
}

function gitFileList(args) {
  const result = spawnSync('git', ['-c', 'core.quotepath=false', 'ls-files', '-z', ...args], {
    cwd: root,
    encoding: 'utf8',
    maxBuffer: 10 * 1024 * 1024,
  })
  if (result.error || result.status !== 0) {
    fail(`git ls-files failed: ${result.error?.message ?? result.stderr.trim()}`)
  }
  return result.stdout.split('\0').filter(Boolean)
}

function candidateFiles() {
  return [...new Set([
    ...gitFileList(['--cached']),
    ...gitFileList(['--others', '--exclude-standard']),
  ])].sort()
}

function sha256(relativePath) {
  return createHash('sha256').update(readFileSync(resolve(root, relativePath))).digest('hex')
}

function lineNumberAt(content, index) {
  return content.slice(0, index).split('\n').length
}

export function markdownFindings(fileName, content) {
  const findings = []
  const lines = content.split('\n')
  let fence

  lines.forEach((line, index) => {
    if (/[ \t]+$/.test(line)) {
      findings.push(`${fileName}:${index + 1} has trailing whitespace`)
    }

    const match = line.match(/^\s{0,3}(`{3,}|~{3,})(.*)$/)
    if (!match) return
    const marker = match[1]
    if (!fence) {
      fence = { character: marker[0], length: marker.length, line: index + 1 }
      return
    }
    if (
      marker[0] === fence.character
      && marker.length >= fence.length
      && match[2].trim() === ''
    ) {
      fence = undefined
    }
  })

  if (fence) {
    findings.push(`${fileName}:${fence.line} has an unclosed ${fence.character} code fence`)
  }
  return findings
}

export function extractMarkdownLinks(content) {
  const targets = []
  const inline = /!?\[[^\]]*]\(\s*(?:<([^>]+)>|([^\s)]+))(?:\s+["'][^"']*["'])?\s*\)/g
  const reference = /^\s*\[[^\]]+]:\s*(?:<([^>]+)>|(\S+))/gm

  for (const pattern of [inline, reference]) {
    for (const match of content.matchAll(pattern)) {
      targets.push({
        target: match[1] ?? match[2],
        line: lineNumberAt(content, match.index),
      })
    }
  }
  return targets
}

function isExternalLink(target) {
  return /^https?:\/\//i.test(target)
}

function assertMarkdown() {
  const files = candidateFiles().filter((file) => file.toLowerCase().endsWith('.md'))
  const findings = []
  const externalLinks = new Set()
  let localLinkCount = 0

  for (const fileName of files) {
    const fullPath = resolve(root, fileName)
    const content = readFileSync(fullPath, 'utf8')
    findings.push(...markdownFindings(fileName, content))

    for (const { target: rawTarget, line } of extractMarkdownLinks(content)) {
      if (isExternalLink(rawTarget)) {
        externalLinks.add(rawTarget)
        continue
      }
      if (/^(?:mailto:|data:|#)/i.test(rawTarget)) continue

      const withoutFragment = rawTarget.split('#', 1)[0].split('?', 1)[0]
      if (!withoutFragment) continue
      let decoded
      try {
        decoded = decodeURIComponent(withoutFragment)
      } catch {
        findings.push(`${fileName}:${line} has an invalid encoded link: ${rawTarget}`)
        continue
      }

      localLinkCount += 1
      const targetPath = isAbsolute(decoded)
        ? resolve(root, decoded.replace(/^[/\\]+/, ''))
        : resolve(dirname(fullPath), decoded)
      if (!existsSync(targetPath)) {
        findings.push(`${fileName}:${line} points to missing local target: ${rawTarget}`)
      }
    }
  }

  if (findings.length > 0) {
    fail(`Markdown checks found ${findings.length} problem(s):\n- ${findings.join('\n- ')}`)
  }
  console.log(`Markdown checks passed: ${files.length} files, ${localLinkCount} local links.`)
  return [...externalLinks].sort()
}

function isBinary(buffer) {
  const sample = buffer.subarray(0, Math.min(buffer.length, 8192))
  return sample.includes(0)
}

export function sensitiveContentFindings(fileName, content) {
  const findings = []
  const sensitivePatterns = [
    { label: 'Windows user absolute path', pattern: /[A-Za-z]:[\\/]+Users[\\/]+[A-Za-z0-9._-]+[\\/]+/ },
    { label: 'Unix user absolute path', pattern: /\/(?:Users|home)\/[A-Za-z0-9._-]+\// },
    { label: 'private key', pattern: /-----BEGIN [A-Z ]*PRIVATE KEY-----/ },
    { label: 'GitHub token', pattern: /(?:github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,})/ },
    { label: 'AWS access key', pattern: /AKIA[0-9A-Z]{16}/ },
  ]
  for (const { label, pattern } of sensitivePatterns) {
    const match = pattern.exec(content)
    if (match) {
      findings.push(`${fileName}:${lineNumberAt(content, match.index)} contains ${label}`)
    }
  }
  return findings
}

function assertTrackedSafety() {
  const tracked = gitFileList(['--cached'])
  const pathFindings = []
  const contentFindings = []

  for (const fileName of tracked) {
    const normalized = fileName.replaceAll('\\', '/')
    const lower = normalized.toLowerCase()
    const extension = extname(lower)
    const forbiddenPath = (
      lower === '.env'
      || (/^\.env\./.test(lower) && lower !== '.env.example')
      || /^(?:build|local|outputs|uploads|models)\//.test(lower)
      || /^frontend\/(?:node_modules|dist)\//.test(lower)
      || /(?:^|\/)(?:model-cache|\.ollama)(?:\/|$)/.test(lower)
    )
    const forbiddenBinary = forbiddenBinaryExtensions.has(extension)
    if (forbiddenPath || forbiddenBinary) {
      pathFindings.push(fileName)
    }

    const buffer = readFileSync(resolve(root, fileName))
    if (isBinary(buffer)) continue
    const content = buffer.toString('utf8')
    contentFindings.push(...sensitiveContentFindings(fileName, content))
  }

  if (pathFindings.length > 0 || contentFindings.length > 0) {
    fail([
      pathFindings.length > 0 ? `forbidden tracked paths: ${pathFindings.join(', ')}` : '',
      contentFindings.length > 0 ? `sensitive tracked content:\n- ${contentFindings.join('\n- ')}` : '',
    ].filter(Boolean).join('\n'))
  }
  console.log(`Tracked-file safety checks passed: ${tracked.length} files.`)
}

function assertLicensePolicy() {
  const licenseHash = sha256('LICENSE')
  const canonicalApache20Hash = 'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30'
  if (licenseHash !== canonicalApache20Hash) {
    fail(`LICENSE SHA-256 is ${licenseHash}, expected canonical Apache-2.0 ${canonicalApache20Hash}`)
  }

  const notice = readFileSync(resolve(root, 'NOTICE'), 'utf8')
  if (!notice.includes('Copyright 2026 Jaemin Jeong')) {
    fail('NOTICE is missing the approved copyright statement')
  }

  const scopeManifest = JSON.parse(readFileSync(resolve(root, 'sbom/prizm-scope-manifest.json'), 'utf8'))
  const gate = scopeManifest.sourceOnlyLicenseGate
  if (gate?.status !== 'PASS' || !Array.isArray(gate.blockers) || gate.blockers.length !== 0) {
    fail('source-only license Gate must be PASS with an empty blockers array')
  }
  for (const status of ['UNKNOWN', 'CONFLICT', 'BLOCKED']) {
    if (!gate.blockingStatuses?.includes(status)) {
      fail(`source-only license Gate does not block ${status}`)
    }
  }

  const lockfile = JSON.parse(readFileSync(resolve(root, 'frontend/package-lock.json'), 'utf8'))
  const invalid = Object.entries(lockfile.packages ?? {})
    .filter(([path, entry]) => path && entry.version)
    .filter(([, entry]) => (
      typeof entry.license !== 'string'
      || forbiddenLicenseIdentifiers.has(entry.license.toUpperCase())
    ))
    .map(([path, entry]) => `${path}: ${entry.license ?? '<missing>'}`)
  if (invalid.length > 0) {
    fail(`frontend lockfile has forbidden or unknown license identifiers:\n- ${invalid.join('\n- ')}`)
  }
  console.log('LICENSE, NOTICE, and source-only license policy checks passed.')
}

export function classifyExternalStatus(status) {
  if (status >= 200 && status < 400) return 'ok'
  if (status === 404 || status === 410) return 'permanent'
  return 'indeterminate'
}

async function requestExternalLink(url) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 10_000)
  try {
    const response = await fetch(url, {
      redirect: 'follow',
      signal: controller.signal,
      headers: {
        'Range': 'bytes=0-0',
        'User-Agent': 'PRIZM-OSS-Link-Check/1.0',
      },
    })
    await response.body?.cancel()
    return { status: response.status, classification: classifyExternalStatus(response.status) }
  } catch (error) {
    return { classification: 'indeterminate', detail: error.name === 'AbortError' ? 'timeout' : error.message }
  } finally {
    clearTimeout(timeout)
  }
}

async function verifyExternalLinks(urls) {
  const results = new Array(urls.length)
  let cursor = 0
  const workers = Array.from({ length: Math.min(8, urls.length) }, async () => {
    while (cursor < urls.length) {
      const index = cursor
      cursor += 1
      const first = await requestExternalLink(urls[index])
      results[index] = first.classification === 'permanent'
        ? await requestExternalLink(urls[index])
        : first
    }
  })
  await Promise.all(workers)

  const permanent = []
  const indeterminate = []
  results.forEach((result, index) => {
    if (result.classification === 'permanent') {
      permanent.push(`${urls[index]} (${result.status})`)
    } else if (result.classification === 'indeterminate') {
      indeterminate.push(`${urls[index]} (${result.status ?? result.detail})`)
    }
  })

  indeterminate.forEach((entry) => console.warn(`External link indeterminate: ${entry}`))
  console.log(`External links: ${urls.length - permanent.length - indeterminate.length} OK, ${indeterminate.length} indeterminate, ${permanent.length} permanent failure(s).`)
  if (permanent.length > 0) {
    fail(`external links returned 404/410 twice:\n- ${permanent.join('\n- ')}`)
  }
}

function assertRequiredFiles() {
  const missing = requiredFiles.filter((fileName) => !existsSync(resolve(root, fileName)))
  if (missing.length > 0) {
    fail(`required OSS files are missing: ${missing.join(', ')}`)
  }
  console.log(`Required OSS files exist: ${requiredFiles.length}.`)
}

function regenerateAndVerifySbom() {
  const before = new Map(generatedSbomFiles.map((fileName) => [fileName, sha256(fileName)]))
  const gradle = process.platform === 'win32'
    ? resolve(root, 'gradlew.bat')
    : resolve(root, 'gradlew')
  const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm'

  run(gradle, ['generateBackendSbom', '--no-daemon', '--dependency-verification=strict'])
  run(npm, ['--prefix', 'frontend', 'run', 'sbom'])

  const drift = generatedSbomFiles.filter((fileName) => before.get(fileName) !== sha256(fileName))
  if (drift.length > 0) {
    fail(`SBOM regeneration drift detected in ${drift.join(', ')}; review the generated diff and update SHA256SUMS intentionally`)
  }

  verifyRepository()
  run(process.execPath, ['--test', 'scripts/verify-sbom.test.mjs', 'scripts/verify-oss-readiness.test.mjs'])
  console.log('SBOM regeneration, structure, checksum, and regression checks passed.')
}

export async function verifyOssReadiness() {
  assertRequiredFiles()
  const externalLinks = assertMarkdown()
  assertTrackedSafety()
  assertLicensePolicy()
  regenerateAndVerifySbom()
  run('git', ['diff', '--check'])
  await verifyExternalLinks(externalLinks)
  console.log('PRIZM OSS readiness verification passed.')
}

const isMain = process.argv[1]
  && import.meta.url === pathToFileURL(resolve(process.argv[1])).href

if (isMain) {
  verifyOssReadiness().catch((error) => {
    console.error(error.message)
    process.exitCode = 1
  })
}
