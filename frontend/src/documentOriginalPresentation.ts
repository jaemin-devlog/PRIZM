export function txtPreviewText(value: string, maxCharacters: number): string {
  if (!Number.isSafeInteger(maxCharacters) || maxCharacters < 1) {
    throw new RangeError('maxCharacters must be a positive integer')
  }

  const normalized = value
    .replace(/\r\n?/g, '\n')
    .split('')
    .filter((character) => {
      const code = character.charCodeAt(0)
      return code === 0x09 || code === 0x0a || (code >= 0x20 && code !== 0x7f)
    })
    .join('')
    .split('\n')
    .map((line) => line.trimEnd())
    .join('\n')
    .trim()

  if (normalized.length <= maxCharacters) {
    return normalized
  }
  return `${normalized.slice(0, maxCharacters).trimEnd()}…`
}
