param(
    [string]$BaseUrl = 'http://127.0.0.1:29081'
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$dataset = Join-Path $repo 'specs\PRZ-016-search-performance-v2\p7-cross-document-generalization-v2\dataset'
$password = 'P7Trace-' + [Guid]::NewGuid().ToString('N') + '!'

function Invoke-JsonPost([string]$Uri, [hashtable]$Body) {
    Invoke-RestMethod -Method Post -Uri $Uri -ContentType 'application/json' `
        -Body ($Body | ConvertTo-Json -Compress)
}

function New-TraceUser([string]$UserKey) {
    $email = ('p7-stage-trace-' + $UserKey.ToLowerInvariant() + '@example.invalid')
    Invoke-JsonPost "$BaseUrl/api/auth/signup" @{ email = $email; password = $password } | Out-Null
    $login = Invoke-JsonPost "$BaseUrl/api/auth/login" @{ email = $email; password = $password }
    return @{ userKey = $UserKey; token = $login.accessToken; userId = $login.user.id }
}

function Wait-Active([hashtable]$Account, [long]$DocumentId, [long]$VersionId) {
    $headers = @{ Authorization = 'Bearer ' + $Account.token }
    $deadline = (Get-Date).AddMinutes(4)
    do {
        $detail = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/documents/$DocumentId" -Headers $headers
        $version = $detail.versions | Where-Object versionId -eq $VersionId
        if ($null -ne $version -and $version.processingStatus -eq 'COMPLETED' -and
            $detail.activeVersionId -eq $VersionId) {
            return
        }
        if ($null -ne $version -and $version.processingStatus -eq 'FAILED') {
            throw "Processing failed for document $DocumentId version $VersionId"
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for document $DocumentId version $VersionId"
}

function Add-Document(
    [hashtable]$Account,
    [string]$Title,
    [string]$DocumentType,
    [string]$RelativePath
) {
    $headers = @{ Authorization = 'Bearer ' + $Account.token }
    $file = Get-Item -LiteralPath (Join-Path $dataset $RelativePath)
    $upload = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/documents" -Headers $headers -Form @{
        title = $Title
        documentType = $DocumentType
        file = $file
    }
    Wait-Active $Account $upload.documentId $upload.versionId
    return $upload
}

function Add-Version([hashtable]$Account, [long]$DocumentId, [string]$RelativePath) {
    $headers = @{ Authorization = 'Bearer ' + $Account.token }
    $file = Get-Item -LiteralPath (Join-Path $dataset $RelativePath)
    $upload = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/documents/$DocumentId/versions" `
        -Headers $headers -Form @{ file = $file }
    Wait-Active $Account $upload.documentId $upload.versionId
    return $upload
}

$accounts = @{}
foreach ($key in @('SYN2-U01', 'SYN2-U02', 'SYN2-U03', 'SYN2-U04')) {
    $accounts[$key] = New-TraceUser $key
}

# Preserve the original P7-B insertion order so document/version/chunk IDs remain comparable.
$inactive = Add-Document $accounts['SYN2-U03'] 'SYN2-U03 Resume' 'RESUME' `
    'inactive-versions\syn2-u03-resume-v0.txt'
Add-Version $accounts['SYN2-U03'] $inactive.documentId 'documents\syn2-u03-resume-v1.pdf' | Out-Null

Add-Document $accounts['SYN2-U01'] 'SYN2-U01-RESUME' 'RESUME' `
    'documents\syn2-u01-resume-v1.pdf' | Out-Null
Add-Document $accounts['SYN2-U01'] 'SYN2-U01-PORTFOLIO' 'PORTFOLIO' `
    'documents\syn2-u01-portfolio-v1.txt' | Out-Null
Add-Document $accounts['SYN2-U02'] 'SYN2-U02-RESUME' 'RESUME' `
    'documents\syn2-u02-resume-v1.pdf' | Out-Null
Add-Document $accounts['SYN2-U02'] 'SYN2-U02-PORTFOLIO' 'PORTFOLIO' `
    'documents\syn2-u02-portfolio-v1.txt' | Out-Null
Add-Document $accounts['SYN2-U03'] 'SYN2-U03-PORTFOLIO' 'PORTFOLIO' `
    'documents\syn2-u03-portfolio-v1.txt' | Out-Null
Add-Document $accounts['SYN2-U04'] 'SYN2-U04-RESUME' 'RESUME' `
    'documents\syn2-u04-resume-v1.pdf' | Out-Null
Add-Document $accounts['SYN2-U04'] 'SYN2-U04-PORTFOLIO' 'PORTFOLIO' `
    'documents\syn2-u04-portfolio-v1.txt' | Out-Null

$env:PRIZM_DB_HOST = '127.0.0.1'
$env:PRIZM_OLLAMA_BASE_URL = 'http://127.0.0.1:11434'
$env:SERVER_PORT = '0'
& (Join-Path $repo 'gradlew.bat') --no-daemon `
    -I (Join-Path $repo 'scripts\evaluation\p7-stage-trace-14.init.gradle') `
    runP7StageTrace14
if ($LASTEXITCODE -ne 0) {
    throw "Stage tracer failed with exit code $LASTEXITCODE"
}
