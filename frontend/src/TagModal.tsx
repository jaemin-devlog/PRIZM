import { type KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react'
import { createTag, searchTags, TagApiError, type Tag } from './api/tagApi'
import { expireSessionIfUnauthorized } from './auth/sessionPolicy'
import { keepFocusWithinModal, restoreModalTrigger } from './modalFocus'
import { TagModalView } from './tagComponents'
import { addUniqueTag, canCreateTag, removeTagById } from './tagSelection'

type TagModalProps = {
  selectedTags: Tag[]
  onSave: (tags: Tag[]) => void | Promise<void>
  onClose: () => void
  onSessionExpired: () => void
}

export function TagModal({ selectedTags, onSave, onClose, onSessionExpired }: TagModalProps) {
  const [query, setQuery] = useState('')
  const [draft, setDraft] = useState<Tag[]>(selectedTags)
  const [results, setResults] = useState<Tag[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [isSearchError, setIsSearchError] = useState(false)
  const [searchReloadKey, setSearchReloadKey] = useState(0)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const panelRef = useRef<HTMLElement>(null)
  const returnFocusRef = useRef<HTMLElement | null>(
    typeof document !== 'undefined' && document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null,
  )
  const isBusy = isCreating || isSaving

  useEffect(() => {
    const controller = new AbortController()
    const timeoutId = window.setTimeout(() => {
      setIsLoading(true)
      setIsSearchError(false)
      setErrorMessage(null)
      void searchTags(query, controller.signal)
        .then(setResults)
        .catch((error: unknown) => {
          if (controller.signal.aborted) return
          if (error instanceof TagApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
            return
          }
          setResults([])
          setIsSearchError(true)
          setErrorMessage('태그를 검색하지 못했습니다.')
        })
        .finally(() => {
          if (!controller.signal.aborted) setIsLoading(false)
        })
    }, 180)
    return () => {
      window.clearTimeout(timeoutId)
      controller.abort()
    }
  }, [onSessionExpired, query, searchReloadKey])

  useEffect(() => () => {
    restoreModalTrigger(returnFocusRef.current)
  }, [])

  const createAvailable = useMemo(
    () => canCreateTag(query, results, draft),
    [draft, query, results],
  )

  const handleCreate = async () => {
    if (!createAvailable || isCreating) return
    setIsCreating(true)
    setIsSearchError(false)
    setErrorMessage(null)
    try {
      const created = await createTag(query)
      setDraft((current) => addUniqueTag(current, created))
      setResults((current) => addUniqueTag(current, created))
      setQuery('')
    } catch (error) {
      if (error instanceof TagApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      setErrorMessage('새 태그를 만들지 못했습니다.')
    } finally {
      setIsCreating(false)
    }
  }

  const handleSave = async () => {
    if (isBusy) return
    setIsSaving(true)
    setIsSearchError(false)
    setErrorMessage(null)
    try {
      await onSave(draft)
    } catch (error) {
      if (error instanceof TagApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      setErrorMessage('선택한 태그를 저장하지 못했습니다.')
    } finally {
      setIsSaving(false)
    }
  }

  const handleClose = () => {
    if (!isBusy) {
      onClose()
    }
  }

  const handleRetrySearch = () => {
    if (isBusy) return
    setIsLoading(true)
    setIsSearchError(false)
    setErrorMessage(null)
    setSearchReloadKey((value) => value + 1)
  }

  const handlePanelKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') {
      event.stopPropagation()
      if (!isBusy) {
        onClose()
      }
      return
    }
    if (event.key !== 'Tab' || panelRef.current === null) {
      return
    }
    const focusableTargets = Array.from(panelRef.current.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), [href], [tabindex]:not([tabindex="-1"])',
    ))
    if (focusableTargets.length === 0) {
      panelRef.current.focus()
      event.preventDefault()
      return
    }
    if (keepFocusWithinModal(focusableTargets, document.activeElement, event.shiftKey)) {
      event.preventDefault()
    }
  }

  return (
    <TagModalView
      query={query}
      selectedTags={draft}
      results={results}
      isLoading={isLoading}
      isCreating={isCreating}
      isSaving={isSaving}
      canCreate={createAvailable}
      canRetrySearch={isSearchError}
      errorMessage={errorMessage}
      panelRef={panelRef}
      onPanelKeyDown={handlePanelKeyDown}
      onQueryChange={setQuery}
      onSelect={(tag) => setDraft((current) => addUniqueTag(current, tag))}
      onCreate={() => void handleCreate()}
      onRetrySearch={handleRetrySearch}
      onRemove={(tagId) => setDraft((current) => removeTagById(current, tagId))}
      onClose={handleClose}
      onSave={() => void handleSave()}
    />
  )
}
