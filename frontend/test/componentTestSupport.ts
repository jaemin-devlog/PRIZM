import type { ReactNode } from 'react'

type TestElement = {
  props?: Record<string, unknown> & { children?: ReactNode }
}

export function findElements(
  node: ReactNode,
  predicate: (element: TestElement) => boolean,
): TestElement[] {
  if (node === null || node === undefined || typeof node === 'boolean') {
    return []
  }
  if (typeof node === 'string' || typeof node === 'number') {
    return []
  }
  if (Array.isArray(node)) {
    return node.flatMap((child) => findElements(child, predicate))
  }
  if (typeof node === 'object' && 'props' in node) {
    const element = node as TestElement
    return [
      ...(predicate(element) ? [element] : []),
      ...findElements(element.props?.children, predicate),
    ]
  }
  return []
}

export function elementText(node: ReactNode): string {
  if (node === null || node === undefined || typeof node === 'boolean') {
    return ''
  }
  if (typeof node === 'string' || typeof node === 'number') {
    return String(node)
  }
  if (Array.isArray(node)) {
    return node.map(elementText).join('')
  }
  if (typeof node === 'object' && 'props' in node) {
    return elementText((node as TestElement).props?.children)
  }
  return ''
}
