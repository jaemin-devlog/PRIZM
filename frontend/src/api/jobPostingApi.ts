import { getAccessToken } from '../auth/tokenStorage.ts'

export type JobPostingItem = {
  itemId: number
  section: string | null
  text: string
}

export class JobPostingApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super('Job posting segmentation request failed')
    this.status = status
  }
}

export async function segmentJobPosting(
  content: string,
  signal?: AbortSignal,
): Promise<JobPostingItem[]> {
  const accessToken = getAccessToken()

  if (accessToken === null) {
    throw new JobPostingApiError(401)
  }

  const response = await fetch('/api/job-postings/segment', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ content }),
    signal,
  })

  if (!response.ok) {
    throw new JobPostingApiError(response.status)
  }

  return (await response.json()) as JobPostingItem[]
}
