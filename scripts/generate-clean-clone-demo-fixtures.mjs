import { createHash } from 'node:crypto'
import { mkdirSync, writeFileSync } from 'node:fs'
import { isAbsolute, join, relative, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const repositoryRoot = resolve(import.meta.dirname, '..')
const defaultLocalRoot = resolve(repositoryRoot, 'local')
const defaultOutputDirectory = resolve(defaultLocalRoot, 'clean-clone-demo')

export const demoDocuments = Object.freeze([
  Object.freeze({
    key: 'txt',
    fileName: 'prizm-clean-clone-synthetic.txt',
    title: 'PRIZM Clean Clone Synthetic TXT',
    documentType: 'PROJECT_REPORT',
    contentType: 'text/plain',
    expectedSourceType: 'TEXT_CHUNK',
    marker: 'GLASS ORBIT TEXT EVIDENCE 2026',
    query: 'Find the exact synthetic marker GLASS ORBIT TEXT EVIDENCE 2026.',
    lines: Object.freeze([
      'PRIZM FIRST-PARTY SYNTHETIC TXT FIXTURE',
      '',
      'This fictional test record is not about a real person, employer, project, or achievement.',
      'A sample indexing exercise keeps the previously active version when processing is incomplete.',
      'Its only purpose is to verify an owner-scoped clean-clone search flow.',
      '',
      'SEARCH MARKER: GLASS ORBIT TEXT EVIDENCE 2026',
    ]),
  }),
  Object.freeze({
    key: 'pdf',
    fileName: 'prizm-clean-clone-synthetic.pdf',
    title: 'PRIZM Clean Clone Synthetic PDF',
    documentType: 'CAREER_REVIEW',
    contentType: 'application/pdf',
    expectedSourceType: 'PAGE',
    expectedSourceIndexMinimum: 1,
    marker: 'AMBER PAGE SOURCE EVIDENCE 2026',
    query: 'Find the exact synthetic marker AMBER PAGE SOURCE EVIDENCE 2026.',
    lines: Object.freeze([
      'PRIZM FIRST-PARTY SYNTHETIC PDF FIXTURE',
      '',
      'This fictional test record is not about a real person, employer, project, or achievement.',
      'A sample source review preserves the original PDF page reference in every result.',
      'Its only purpose is to verify a clean-clone page source.',
      '',
      'SEARCH MARKER: AMBER PAGE SOURCE EVIDENCE 2026',
    ]),
  }),
])

function assertContainedPath(outputDirectory, allowedRoot) {
  const output = resolve(outputDirectory)
  const root = resolve(allowedRoot)
  const relativePath = relative(root, output)
  if (relativePath === '..' || relativePath.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`)
    || isAbsolute(relativePath)) {
    throw new Error('Fixture output must remain inside the ignored local directory')
  }
  return output
}

function escapePdfText(value) {
  if (!/^[\x20-\x7e]*$/.test(value)) {
    throw new Error('Synthetic PDF text must use printable ASCII characters')
  }
  return value.replaceAll('\\', '\\\\').replaceAll('(', '\\(').replaceAll(')', '\\)')
}

export function createTextLayerPdf(lines) {
  if (!Array.isArray(lines) || lines.length === 0) throw new Error('PDF fixture needs at least one line')
  const drawing = ['BT', '/F1 11 Tf', '72 720 Td', '16 TL']
  lines.forEach((line, index) => {
    if (index > 0) drawing.push('T*')
    drawing.push(`(${escapePdfText(line)}) Tj`)
  })
  drawing.push('ET')
  const stream = `${drawing.join('\n')}\n`
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',
    `<< /Length ${Buffer.byteLength(stream, 'ascii')} >>\nstream\n${stream}endstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ]

  let pdf = '%PDF-1.4\n%PRIZM-SYNTHETIC\n'
  const offsets = [0]
  objects.forEach((object, index) => {
    offsets.push(Buffer.byteLength(pdf, 'ascii'))
    pdf += `${index + 1} 0 obj\n${object}\nendobj\n`
  })
  const xrefOffset = Buffer.byteLength(pdf, 'ascii')
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
  for (const offset of offsets.slice(1)) {
    pdf += `${String(offset).padStart(10, '0')} 00000 n \n`
  }
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\n`
  pdf += `startxref\n${xrefOffset}\n%%EOF\n`
  return Buffer.from(pdf, 'ascii')
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

export function generateDemoFixtures(
  outputDirectory = defaultOutputDirectory,
  { allowedRoot = defaultLocalRoot } = {},
) {
  const output = assertContainedPath(outputDirectory, allowedRoot)
  mkdirSync(output, { recursive: true })

  const documents = demoDocuments.map((document) => {
    const contents = document.key === 'pdf'
      ? createTextLayerPdf(document.lines)
      : Buffer.from(`${document.lines.join('\n')}\n`, 'utf8')
    writeFileSync(join(output, document.fileName), contents)
    return Object.freeze({
      key: document.key,
      fileName: document.fileName,
      title: document.title,
      documentType: document.documentType,
      contentType: document.contentType,
      expectedSourceType: document.expectedSourceType,
      expectedSourceIndexMinimum: document.expectedSourceIndexMinimum ?? 1,
      marker: document.marker,
      query: document.query,
      sha256: sha256(contents),
    })
  })

  const manifest = Object.freeze({
    schemaVersion: 1,
    synthetic: true,
    provenance: 'First-party PRIZM synthetic test data; no real person, employer, project, or achievement.',
    documents,
  })
  const manifestPath = join(output, 'manifest.json')
  writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
  return Object.freeze({ outputDirectory: output, manifestPath, manifest })
}

function parseOutputArgument(args) {
  if (args.length === 0) return defaultOutputDirectory
  if (args.length === 2 && args[0] === '--output') return resolve(repositoryRoot, args[1])
  throw new Error('Usage: node scripts/generate-clean-clone-demo-fixtures.mjs [--output local/<directory>]')
}

const isMain = process.argv[1]
  && import.meta.url === pathToFileURL(resolve(process.argv[1])).href

if (isMain) {
  try {
    const result = generateDemoFixtures(parseOutputArgument(process.argv.slice(2)))
    console.log(`Generated ${result.manifest.documents.length} deterministic synthetic fixtures under local/.`)
  } catch (error) {
    console.error(error instanceof Error ? error.message : 'Failed to generate clean-clone fixtures')
    process.exitCode = 1
  }
}
