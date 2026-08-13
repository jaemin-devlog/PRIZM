import type {
  ProcessingJobStatus,
  ProcessingProgressStage,
} from './api/documentApi.ts'

const PROCESSING_STAGE_LABELS: Readonly<Record<ProcessingProgressStage, string>> = {
  FILE_READING: '파일 읽기',
  TEXT_EXTRACTION: '텍스트 추출',
  CHUNK_CREATION: '청크 생성',
  EMBEDDING: '임베딩',
  SAVING: '저장',
  COMPLETED: '완료',
}

export function progressSummary(
  status: ProcessingJobStatus,
  stage: ProcessingProgressStage | null,
  completedChunks: number | null,
  totalChunks: number | null,
  progressPercent: number | null,
): string {
  if (status === 'COMPLETED') {
    return '완료 · 100%'
  }
  if (status === 'FAILED') {
    return '처리 실패'
  }
  if (status === 'RETRY_WAIT') {
    return '재시도 대기'
  }
  if (status === 'PROCESSING' && stage === 'SAVING') {
    return '저장 중'
  }
  if (status === 'PROCESSING' && stage === 'EMBEDDING'
      && completedChunks !== null && totalChunks !== null && progressPercent !== null) {
    return `임베딩 ${completedChunks}/${totalChunks} · ${progressPercent}%`
  }
  if (stage === 'COMPLETED') {
    return status === 'PROCESSING' ? '처리 중' : '처리 준비'
  }
  return stage === null ? '처리 준비' : PROCESSING_STAGE_LABELS[stage]
}
