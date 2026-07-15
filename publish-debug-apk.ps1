param(
    [switch]$Build,
    [switch]$Upload,
    [switch]$ServeLocal,
    [int]$Port = 8765,
    [string]$ApkPath = "app/build/outputs/apk/debug/app-debug.apk",
    [string]$OutputPath = "orbit-debug.apk"
)

$ErrorActionPreference = "Stop"

function Resolve-DebugApk {
    param([string]$PreferredPath)

    $preferred = Resolve-Path -LiteralPath $PreferredPath -ErrorAction SilentlyContinue
    if ($preferred) {
        return $preferred.Path
    }

    $debugOutputDir = Join-Path (Get-Location) "app/build/outputs/apk/debug"
    $candidate = Get-ChildItem -LiteralPath $debugOutputDir -Filter "*.apk" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $candidate) {
        throw "No debug APK found. Run .\gradlew.bat :app:assembleDebug first, or pass -Build."
    }

    return $candidate.FullName
}

function Convert-TmpFilesUrlToDownloadUrl {
    param([string]$Url)

    if ($Url -match "^https://tmpfiles\.org/([^/].*)$" -and $Url -notmatch "^https://tmpfiles\.org/dl/") {
        return "https://tmpfiles.org/dl/$($Matches[1])"
    }

    return $Url
}

if ($Build) {
    $jbrPath = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path -LiteralPath $jbrPath) {
        $env:JAVA_HOME = $jbrPath
        $env:Path = (Join-Path $jbrPath "bin") + ";" + $env:Path
    }

    & .\gradlew.bat :app:assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Debug build failed with exit code $LASTEXITCODE."
    }
}

$sourceApk = Resolve-DebugApk -PreferredPath $ApkPath
$outputFullPath = if ([System.IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath
} else {
    Join-Path (Get-Location) $OutputPath
}

Copy-Item -LiteralPath $sourceApk -Destination $outputFullPath -Force
$outputItem = Get-Item -LiteralPath $outputFullPath

Write-Output "Debug APK: $($outputItem.FullName)"
Write-Output "Size: $([Math]::Round($outputItem.Length / 1MB, 2)) MB"

if ($Upload) {
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if (-not $curl) {
        throw "curl.exe is required for temporary upload but was not found."
    }

    $uploadJson = & $curl.Source -sS -F "file=@$($outputItem.FullName)" "https://tmpfiles.org/api/v1/upload"
    if ($LASTEXITCODE -ne 0) {
        throw "Temporary upload failed with exit code $LASTEXITCODE."
    }

    $uploadResponse = $uploadJson | ConvertFrom-Json
    $pageUrl = $uploadResponse.data.url
    if (-not $pageUrl) {
        throw "Temporary upload response did not include a file URL: $uploadJson"
    }

    $downloadUrl = Convert-TmpFilesUrlToDownloadUrl -Url $pageUrl
    Write-Output "Temporary debug build download: $downloadUrl"
}

if ($ServeLocal) {
    $serverScript = Join-Path $PSScriptRoot "serve-apk.ps1"
    if (-not (Test-Path -LiteralPath $serverScript)) {
        throw "Local server script not found: $serverScript"
    }

    $serverCommand = "& '$serverScript' -Port $Port -ApkPath '$($outputItem.FullName)'"
    $encodedServerCommand = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($serverCommand))

    Start-Process powershell.exe -WindowStyle Hidden -ArgumentList @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-EncodedCommand", $encodedServerCommand
    )

    Write-Output "Local debug build download: http://localhost:$Port/orbit-debug.apk"
}
