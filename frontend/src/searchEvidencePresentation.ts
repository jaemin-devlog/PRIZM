import type { CareerEvidenceSearchResult } from './api/searchApi'

export function getEvidenceSourceLabel(
  result: Pick<CareerEvidenceSearchResult, 'evidenceSourceLabel'>,
): string {
  return result.evidenceSourceLabel
}

const MAX_CONTEXT_SENTENCES = 3

export function getEvidenceContext(content: string, snippet: string): string {
  const evidence = snippet.trim()
  if (evidence === '') {
    return ''
  }

  const sentences = segmentSentences(content)
  const normalizedEvidence = normalizeText(evidence)
  const anchorIndex = sentences.findIndex((sentence) => {
    const normalizedSentence = normalizeText(sentence)
    return normalizedEvidence.includes(normalizedSentence)
      || normalizedSentence.includes(normalizedEvidence)
  })
  if (anchorIndex < 0) {
    return evidence
  }

  const selected = [sentences[anchorIndex]]
  for (const index of [anchorIndex - 1, anchorIndex + 1]) {
    if (index >= 0 && index < sentences.length && !isMetadataLike(sentences[index])) {
      selected.push(sentences[index])
    }
  }

  return selected
    .slice(0, MAX_CONTEXT_SENTENCES)
    .sort((left, right) => sentences.indexOf(left) - sentences.indexOf(right))
    .join('\n')
}

function segmentSentences(value: string): string[] {
  const segmenter = new Intl.Segmenter('ko', { granularity: 'sentence' })
  return value.split(/\r?\n/).flatMap((line) =>
    Array.from(segmenter.segment(line), ({ segment }) => segment.trim()).filter(Boolean),
  )
}

function normalizeText(value: string): string {
  return value.toLocaleLowerCase('ko').replace(/\s+/g, '')
}

function isMetadataLike(value: string): boolean {
  return /(?:[\w.%+-]+@[\w.-]+\.[a-z]{2,}|https?:\/\/|www\.|github\.com|(?:\+?82[- .]?)?0(?:10|2|[3-6][1-5])[- .]?\d{3,4}[- .]?\d{4})/i.test(value)
    || /^(?:contact|email|phone|github|education|gpa|name|school|major|status)\b/i.test(value.trim())
}
