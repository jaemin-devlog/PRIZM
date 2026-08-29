import { createHash } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

const output = process.argv[2]
if (!output) throw new Error('Usage: node verify-prz022-backend-contracts.mjs <output.json>')
const root = process.cwd()
const sha256 = value => createHash('sha256').update(value).digest('hex')

const contracts = {
  worker: {
    'src/main/java/com/prizm/ingestion/repository/ProcessingJobClaimRepository.java': [
      'FOR UPDATE OF job SKIP LOCKED', 'now()', 'claim_version = claim_version + 1',
    ],
    'src/main/java/com/prizm/ingestion/service/WorkerLeaseHeartbeat.java': ['ProcessingJobLeaseService'],
    'src/main/java/com/prizm/ingestion/service/ProcessingJobLeaseService.java': ['claimVersion'],
    'src/main/java/com/prizm/ingestion/service/ProcessingJobRecoveryService.java': ['recoverNext'],
    'src/main/java/com/prizm/ingestion/service/IndexingCompletionService.java': ['@Transactional', 'claimVersion'],
  },
  ownerIsolation: {
    'src/main/java/com/prizm/document/service/DocumentQueryService.java': ['ownerUserId'],
    'src/main/java/com/prizm/document/service/DocumentManagementService.java': ['findByIdAndOwnerUserIdForUpdate'],
    'src/main/java/com/prizm/document/service/DocumentUploadService.java': ['findByIdAndOwnerUserIdForUpdate'],
    'src/main/java/com/prizm/document/service/DocumentThumbnailService.java': ['findByIdAndOwnerUserIdAndDocumentId'],
    'src/main/java/com/prizm/search/repository/VectorSearchRepository.java': ['owner_user_id', 'active_version_id'],
    'src/main/java/com/prizm/mcp/CareerEvidenceMcpTool.java': ['currentUserProvider.userId()', 'searchCareerEvidenceV2'],
  },
  cleanup: {
    'src/main/java/com/prizm/document/service/DocumentUploadService.java': ['STATUS_ROLLED_BACK', 'STATUS_UNKNOWN'],
    'src/main/java/com/prizm/cleanup/service/FileCleanupJobService.java': ['Propagation.REQUIRES_NEW'],
    'src/main/java/com/prizm/cleanup/repository/FileCleanupJobRepository.java': ['FOR UPDATE SKIP LOCKED', 'claim_version'],
    'src/main/java/com/prizm/cleanup/service/FileCleanupJobRecoveryService.java': ['recoverNext'],
    'src/main/java/com/prizm/infrastructure/storage/LocalFileStorage.java': ['SecureDirectoryStream', 'LinkOption.NOFOLLOW_LINKS', 'requireSecureDirectoryStream'],
  },
}

const testContracts = {
  'src/integrationTest/java/com/prizm/infrastructure/PgVectorInfrastructureTest.java': [
    'allowsOnlyOneIndependentTransactionToClaimTheSamePendingJob',
    'recoversExpiredLeaseAndRejectsCompletionFromTheOldWorker',
    'renewsHeartbeatLeaseBeforeRecoveryWithoutChangingTheClaimVersion',
    'cleanupClaimSkipsLockedFirstJobAndClaimsNextJobBeforeLockRelease',
    'cleanupWorkerConvergesToCompletedAfterCompletionUpdateFailureAndLeaseRecovery',
    'staleCleanupClaimCannotOverwriteRetryOrFailureStateOwnedByNewWorker',
    'keepsExistingActiveVersionSearchableWhenReplacementVersionFails',
    'recordsPrz022WorkerReliabilityEvidence',
    'recordsPrz022CleanupReliabilityEvidence',
  ],
  'src/integrationTest/java/com/prizm/infrastructure/AuthenticationIntegrationTest.java': [
    'isolatesDocumentListAndDetailByAuthenticatedUser',
    'managesOnlyOwnersDocumentsAndQueuesCleanupAfterTerminalDeletion',
    'isolatesVectorSearchCandidatesByAuthenticatedUser',
    'protectsPdfThumbnailByAuthenticationAndDocumentOwnership',
    'recordsPrz022UserOnlyOwnerIsolationMatrix',
  ],
  'src/test/java/com/prizm/mcp/CareerEvidenceMcpToolTest.java': ['UsesTheAuthenticatedUser'],
  'src/test/java/com/prizm/infrastructure/storage/LocalFileStorageTest.java': [
    'failsClosedWithoutUnsafeFallbackWhenSecureDirectoryStreamIsUnavailable',
  ],
}

async function audit(group) {
  const result = {}
  for (const [file, patterns] of Object.entries(group)) {
    const text = await readFile(path.join(root, file), 'utf8')
    const checks = Object.fromEntries(patterns.map(pattern => [pattern, text.includes(pattern)]))
    if (Object.values(checks).some(value => !value)) throw new Error(`Contract mismatch: ${file}`)
    result[file] = { sha256: sha256(text), checks }
  }
  return result
}

const report = {
  schemaVersion: 1,
  baselineMain: '3af4db05f5f1b2d9802335de5eac9ad7b98555fa',
  generatedAt: new Date().toISOString(),
  scope: 'current-source and executable-test contract presence; not runtime evidence',
  contracts: {},
  tests: await audit(testContracts),
  status: 'PASS',
}
for (const [axis, files] of Object.entries(contracts)) report.contracts[axis] = await audit(files)
await writeFile(path.join(root, output), `${JSON.stringify(report, null, 2)}\n`, 'utf8')
console.log(`PRZ-022 backend contract audit PASS: ${output}`)
