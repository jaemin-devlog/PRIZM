import type {
  ProcessingJobStatus,
  ProcessingProgressStage,
} from './api/documentApi.ts'

const PROCESSING_STAGE_LABELS: Readonly<Record<ProcessingProgressStage, string>> = {
  FILE_READING: '문서를 읽는 중',
  TEXT_EXTRACTION: '문서 내용을 확인하는 중',
  CHUNK_CREATION: '검색용 내용을 정리하는 중',
  EMBEDDING: '검색할 수 있게 준비하는 중',
  SAVING: '준비 내용을 저장하는 중',
  COMPLETED: '검색 준비 완료',
}

export function progressSummary(
  status: ProcessingJobStatus,
  stage: ProcessingProgressStage | null,
  completedChunks: number | null,
  totalChunks: number | null,
  progressPercent: number | null,
): string {
  if (status === 'COMPLETED') {
    return '검색 준비 완료'
  }
  if (status === 'FAILED') {
    return '준비에 실패했어요'
  }
  if (status === 'RETRY_WAIT') {
    return '잠시 후 다시 준비해요'
  }
  if (status === 'PROCESSING' && stage === 'SAVING') {
    return '준비 내용을 저장하는 중'
  }
  if (status === 'PROCESSING' && stage === 'EMBEDDING'
      && completedChunks !== null && totalChunks !== null && progressPercent !== null) {
    return `검색 준비 중 ${completedChunks}/${totalChunks} · ${progressPercent}%`
  }
  if (stage === 'COMPLETED') {
    return status === 'PROCESSING' ? '검색 준비 중' : '준비 중'
  }
  return stage === null ? '문서를 읽고 검색할 수 있게 준비 중' : PROCESSING_STAGE_LABELS[stage]
}
