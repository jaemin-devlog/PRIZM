export type FocusableTarget = {
  focus: () => void
}

export type RestorableFocusTarget = FocusableTarget & {
  isConnected: boolean
}

export function focusModalEntry(target: FocusableTarget | null): boolean {
  if (target === null) {
    return false
  }
  target.focus()
  return true
}

export function restoreModalTrigger(target: RestorableFocusTarget | null): boolean {
  if (target === null || !target.isConnected) {
    return false
  }
  target.focus()
  return true
}

export function keepFocusWithinModal(
  focusableTargets: FocusableTarget[],
  activeTarget: unknown,
  reverse: boolean,
): boolean {
  if (focusableTargets.length === 0) {
    return false
  }

  const activeIndex = focusableTargets.indexOf(activeTarget as FocusableTarget)
  if (activeIndex < 0) {
    focusableTargets[reverse ? focusableTargets.length - 1 : 0].focus()
    return true
  }
  if (reverse && activeIndex === 0) {
    focusableTargets[focusableTargets.length - 1].focus()
    return true
  }
  if (!reverse && activeIndex === focusableTargets.length - 1) {
    focusableTargets[0].focus()
    return true
  }
  return false
}
