import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, extname, isAbsolute, relative, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'

import { verifyRepository } from './verify-sbom.mjs'

const root = resolve(import.meta.dirname, '..')
const requiredFiles = [
  '.github/ISSUE_TEMPLATE/bug.yml',
  '.github/ISSUE_TEMPLATE/config.yml',
  '.github/ISSUE_TEMPLATE/documentation.yml',
  '.github/ISSUE_TEMPLATE/feature.yml',
  '.github/pull_request_template.md',
  'CODE_OF_CONDUCT.md',
  'CONTRIBUTING.md',
  'LICENSE',
  'MAINTAINERS.md',
  'NOTICE',
  'SECURITY.md',
  'SUPPORT.md',
  'sbom/README.md',
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
const syntheticBinaryFixtureAllowlist = 'scripts/oss-readiness-binary-fixtures.json'
const syntheticFixtureDocumentKey = /^SYN(?:\d+)?-U\d{2}-(?:PORTFOLIO|RESUME)$/
const sha256Pattern = /^[a-f0-9]{64}$/
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

export function releaseMetadataFindings(metadata) {
  const entries = Object.entries(metadata)
  const findings = entries
    .filter(([, value]) => typeof value !== 'string' || value.length === 0)
    .map(([name]) => `${name} has no release version`)
  if (findings.length > 0) {
    return findings
  }

  const versions = new Set(entries.map(([, value]) => value))
  if (versions.size > 1) {
    findings.push(`release versions differ: ${entries.map(([name, value]) => `${name}=${value}`).join(', ')}`)
  }
  return findings
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

function normalizedRepositoryPath(path, fieldName) {
  if (
    typeof path !== 'string'
    || path.length === 0
    || isAbsolute(path)
    || /[*?\[\]{}]/.test(path)
  ) {
    fail(`${fieldName} must be an exact relative path without wildcard characters`)
  }
  const normalized = relative(root, resolve(root, path)).replaceAll('\\', '/')
  if (
    normalized === ''
    || normalized === '..'
    || normalized.startsWith('../')
    || normalized !== path
  ) {
    fail(`${fieldName} must be a canonical path within the repository`)
  }
  return normalized
}

function loadSyntheticBinaryFixtures(tracked) {
  if (!existsSync(resolve(root, syntheticBinaryFixtureAllowlist))) {
    fail(`synthetic binary fixture allowlist is missing: ${syntheticBinaryFixtureAllowlist}`)
  }
  if (!tracked.includes(syntheticBinaryFixtureAllowlist)) {
    fail(`synthetic binary fixture allowlist must be tracked: ${syntheticBinaryFixtureAllowlist}`)
  }

  let allowlist
  try {
    allowlist = JSON.parse(readFileSync(resolve(root, syntheticBinaryFixtureAllowlist), 'utf8'))
  } catch (error) {
    fail(`synthetic binary fixture allowlist is invalid: ${error.message}`)
  }
  if (
    allowlist.format !== 'PRIZM-OSS-SYNTHETIC-BINARY-FIXTURE-ALLOWLIST'
    || allowlist.formatVersion !== 1
    || !Array.isArray(allowlist.fixtures)
    || allowlist.fixtures.length !== 8
  ) {
    fail('synthetic binary fixture allowlist must contain exactly 8 version-1 entries')
  }

  const approved = new Map()
  for (const fixture of allowlist.fixtures) {
    const fileName = normalizedRepositoryPath(fixture.path, 'synthetic fixture path')
    const manifestName = normalizedRepositoryPath(fixture.freezeManifest, 'synthetic fixture manifest')
    if (
      !fileName.startsWith('specs/')
      || extname(fileName).toLowerCase() !== '.pdf'
      || !manifestName.startsWith('specs/')
      || !manifestName.endsWith('/freeze-manifest.json')
      || !sha256Pattern.test(fixture.sha256 ?? '')
      || approved.has(fileName)
    ) {
      fail(`invalid synthetic binary fixture allowlist entry: ${fixture.path ?? '<missing>'}`)
    }
    if (!tracked.includes(fileName) || !tracked.includes(manifestName)) {
      fail(`synthetic binary fixture and manifest must both be tracked: ${fileName}`)
    }
    if (sha256(fileName) !== fixture.sha256) {
      fail(`synthetic binary fixture SHA-256 mismatch: ${fileName}`)
    }

    let manifest
    try {
      manifest = JSON.parse(readFileSync(resolve(root, manifestName), 'utf8'))
    } catch (error) {
      fail(`synthetic fixture freeze manifest is invalid: ${manifestName}: ${error.message}`)
    }
    const manifestDirectory = dirname(resolve(root, manifestName))
    const sourceRecord = manifest.activeDocumentHashes?.find((entry) => {
      const sourcePath = relative(root, resolve(manifestDirectory, entry.path)).replaceAll('\\', '/')
      return sourcePath === fileName
    })
    if (
      sourceRecord?.sha256 !== fixture.sha256
      || !syntheticFixtureDocumentKey.test(sourceRecord?.documentKey ?? '')
      || manifest.validation?.sensitiveDataFindings !== 0
    ) {
      fail(`synthetic fixture is not a verified frozen synthetic document: ${fileName}`)
    }
    approved.set(fileName, fixture.sha256)
  }
  return approved
}

export function isApprovedBinaryFixture(fileName, actualSha256, approvedFixtures) {
  return approvedFixtures.get(fileName) === actualSha256
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

function githubHeadingSlug(heading) {
  return heading
    .replace(/!?(?:\[([^\]]*)])\([^)]*\)/g, '$1')
    .replace(/<[^>]+>/g, '')
    .replace(/[`*_~]/g, '')
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{M}\p{N}_\- ]/gu, '')
    .replace(/ /g, '-')
}

export function markdownHeadingAnchors(content) {
  const anchors = new Set()
  const duplicateCounts = new Map()
  const lines = content.split('\n')
  let fence

  for (const line of lines) {
    const fenceMatch = line.match(/^\s{0,3}(`{3,}|~{3,})(.*)$/)
    if (fenceMatch) {
      const marker = fenceMatch[1]
      if (!fence) {
        fence = { character: marker[0], length: marker.length }
      } else if (
        marker[0] === fence.character
        && marker.length >= fence.length
        && fenceMatch[2].trim() === ''
      ) {
        fence = undefined
      }
      continue
    }
    if (fence) continue

    const headingMatch = line.match(/^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$/)
    if (headingMatch) {
      const base = githubHeadingSlug(headingMatch[1])
      if (base) {
        const duplicateCount = duplicateCounts.get(base) ?? 0
        anchors.add(duplicateCount === 0 ? base : `${base}-${duplicateCount}`)
        duplicateCounts.set(base, duplicateCount + 1)
      }
    }

    for (const match of line.matchAll(/<(?:a|span)\b[^>]*(?:id|name)=["']([^"']+)["'][^>]*>/gi)) {
      anchors.add(match[1])
    }
  }

  return anchors
}

function isExternalLink(target) {
  return /^https?:\/\//i.test(target)
}

function assertMarkdown() {
  const files = candidateFiles().filter((file) => file.toLowerCase().endsWith('.md'))
  const findings = []
  const externalLinks = new Set()
  const anchorCache = new Map()
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
      if (/^(?:mailto:|data:)/i.test(rawTarget)) continue

      const fragmentIndex = rawTarget.indexOf('#')
      const pathAndQuery = fragmentIndex >= 0 ? rawTarget.slice(0, fragmentIndex) : rawTarget
      const rawFragment = fragmentIndex >= 0 ? rawTarget.slice(fragmentIndex + 1) : ''
      const withoutFragment = pathAndQuery.split('?', 1)[0]
      let decodedPath
      let decodedFragment
      try {
        decodedPath = decodeURIComponent(withoutFragment)
        decodedFragment = decodeURIComponent(rawFragment)
      } catch {
        findings.push(`${fileName}:${line} has an invalid encoded link: ${rawTarget}`)
        continue
      }

      localLinkCount += 1
      const targetPath = decodedPath === ''
        ? fullPath
        : isAbsolute(decodedPath)
          ? resolve(root, decodedPath.replace(/^[/\\]+/, ''))
          : resolve(dirname(fullPath), decodedPath)
      if (!existsSync(targetPath)) {
        findings.push(`${fileName}:${line} points to missing local target: ${rawTarget}`)
        continue
      }

      if (decodedFragment && extname(targetPath).toLowerCase() === '.md') {
        let anchors = anchorCache.get(targetPath)
        if (!anchors) {
          anchors = markdownHeadingAnchors(readFileSync(targetPath, 'utf8'))
          anchorCache.set(targetPath, anchors)
        }
        if (!anchors.has(decodedFragment)) {
          findings.push(`${fileName}:${line} points to missing Markdown anchor: ${rawTarget}`)
        }
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

export function assertTrackedSafety() {
  const tracked = gitFileList(['--cached'])
  const approvedFixtures = loadSyntheticBinaryFixtures(tracked)
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
      && !isApprovedBinaryFixture(fileName, sha256(fileName), approvedFixtures)
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

function assertReleaseMetadata() {
  const gradleContent = readFileSync(resolve(root, 'build.gradle'), 'utf8')
  const gradleMatch = gradleContent.match(/^version = '([^']+)'$/m)
  const frontendPackage = JSON.parse(readFileSync(resolve(root, 'frontend/package.json'), 'utf8'))
  const frontendLock = JSON.parse(readFileSync(resolve(root, 'frontend/package-lock.json'), 'utf8'))
  const backendSbom = JSON.parse(readFileSync(resolve(root, 'sbom/prizm-backend-runtime.cdx.json'), 'utf8'))
  const frontendSbom = JSON.parse(readFileSync(resolve(root, 'sbom/prizm-frontend.cdx.json'), 'utf8'))
  const metadata = {
    gradle: gradleMatch?.[1],
    frontendPackage: frontendPackage.version,
    frontendLock: frontendLock.version,
    frontendLockRoot: frontendLock.packages?.['']?.version,
    backendSbom: backendSbom.metadata?.component?.version,
    frontendSbom: frontendSbom.metadata?.component?.version,
  }
  const findings = releaseMetadataFindings(metadata)
  if (findings.length > 0) {
    fail(`release metadata is inconsistent:\n- ${findings.join('\n- ')}`)
  }
  console.log(`Release metadata version checks passed: ${metadata.gradle}.`)
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
  assertReleaseMetadata()
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
