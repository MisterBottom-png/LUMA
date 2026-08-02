param(
    [switch]$Build,
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
