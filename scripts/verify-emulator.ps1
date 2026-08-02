#Requires -Version 5.1

[CmdletBinding()]
param(
    [ValidateRange(1, 99)]
    [int]$ExpectedApiLevel = 36,

    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$AvdName = 'luma_api_36',

    [string]$AndroidSdkRoot,
    [string]$Serial,
    [string]$PackageName = 'com.orbit.app',
    [switch]$RequirePackage,
    [string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExitCodes = [ordered]@{
    Success                  = 0
    AdbUnavailable           = 40
    EmulatorNotFound         = 41
    DeviceTransportInvalid   = 42
    ApiLevelMismatch         = 43
    AvdNameMismatch          = 44
    PropertyCollectionFailed = 45
    PackageMissing           = 46
    OutputWriteFailed        = 47
    UnexpectedFailure        = 49
}

function Stop-Verify {
    param([int]$Code, [string]$Message)
    [Console]::Error.WriteLine("LUMA-EMU-VERIFY-E$Code $Message")
    exit $Code
}

function Test-WindowsHost {
    return [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
}

function Convert-LocalPropertiesPath {
    param([string]$Value)
    $decoded = $Value.Trim() -replace '\\:', ':'
    $decoded = $decoded -replace '\\\\', '\'
    return ($decoded -replace '\\/', '/')
}

function Resolve-SdkRoot {
    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($AndroidSdkRoot)) { $candidates.Add($AndroidSdkRoot) }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) { $candidates.Add($env:ANDROID_SDK_ROOT) }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) { $candidates.Add($env:ANDROID_HOME) }

    $localProperties = Join-Path ([System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))) 'local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
            Select-Object -First 1
        if ($null -ne $sdkLine) {
            $candidates.Add((Convert-LocalPropertiesPath (($sdkLine -split '=', 2)[1])))
        }
    }

    if (Test-WindowsHost) {
        if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
            $candidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
        }
        $candidates.Add('C:\Android\Sdk')
    }
    else {
        $profilePath = [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::UserProfile)
        if (-not [string]::IsNullOrWhiteSpace($profilePath)) {
            $candidates.Add((Join-Path $profilePath 'Android/Sdk'))
        }
    }

    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $fullPath = [System.IO.Path]::GetFullPath($candidate)
        if (Test-Path -LiteralPath $fullPath -PathType Container) { return $fullPath }
    }
    Stop-Verify $ExitCodes.AdbUnavailable 'Android SDK was not found. Pass -AndroidSdkRoot.'
}

function Invoke-AdbText {
    param([string]$AdbPath, [string[]]$Arguments, [switch]$IgnoreFailure)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $AdbPath @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0 -and -not $IgnoreFailure) {
        throw "adb $($Arguments -join ' ') failed with exit code $exitCode."
    }
    return (($output | Out-String).Trim())
}

function Find-AvdSerial {
    param([string]$AdbPath, [string]$ExpectedAvd, [string]$RequestedSerial)

    $devices = Invoke-AdbText -AdbPath $AdbPath -Arguments @('devices')
    $candidateSerials = New-Object System.Collections.Generic.List[string]
    foreach ($line in ($devices -split "`r?`n")) {
        if ($line -match '^(?<serial>\S+)\s+device\b') {
            $candidateSerial = $Matches['serial']
            if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) {
                if ($candidateSerial -eq $RequestedSerial) { return $RequestedSerial }
            }
            elseif ($candidateSerial -match '^emulator-\d+$') {
                $candidateSerials.Add($candidateSerial)
            }
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) { return $null }

    foreach ($candidate in $candidateSerials) {
        $avdOutput = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $candidate, 'emu', 'avd', 'name') -IgnoreFailure
        $candidateName = $avdOutput -split "`r?`n" |
            Where-Object { $_ -and $_.Trim() -ne 'OK' } |
            Select-Object -First 1
        if ($null -ne $candidateName -and $candidateName.Trim() -eq $ExpectedAvd) { return $candidate }
    }
    return $null
}

try {
    $sdkRoot = Resolve-SdkRoot
    $adbName = if (Test-WindowsHost) { 'adb.exe' } else { 'adb' }
    $emulatorName = if (Test-WindowsHost) { 'emulator.exe' } else { 'emulator' }
    $adbPath = Join-Path $sdkRoot "platform-tools/$adbName"
    $emulatorPath = Join-Path $sdkRoot "emulator/$emulatorName"
    if (-not (Test-Path -LiteralPath $adbPath -PathType Leaf)) {
        Stop-Verify $ExitCodes.AdbUnavailable "adb is missing under '$sdkRoot'."
    }

    $selectedSerial = Find-AvdSerial -AdbPath $adbPath -ExpectedAvd $AvdName -RequestedSerial $Serial
    if ([string]::IsNullOrWhiteSpace($selectedSerial)) {
        Stop-Verify $ExitCodes.EmulatorNotFound "No booted adb device matches AVD '$AvdName'."
    }
    $deviceState = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'get-state') -IgnoreFailure
    if ($deviceState -ne 'device') {
        Stop-Verify $ExitCodes.DeviceTransportInvalid "Device '$selectedSerial' is in state '$deviceState'."
    }

    $reportedAvdOutput = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'emu', 'avd', 'name')
    $reportedAvd = $reportedAvdOutput -split "`r?`n" |
        Where-Object { $_ -and $_.Trim() -ne 'OK' } |
        Select-Object -First 1
    if ($null -eq $reportedAvd -or $reportedAvd.Trim() -ne $AvdName) {
        Stop-Verify $ExitCodes.AvdNameMismatch "Device '$selectedSerial' reports a different AVD name."
    }

    $apiText = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'getprop', 'ro.build.version.sdk')
    $reportedApi = 0
    if (-not [int]::TryParse($apiText.Trim(), [ref]$reportedApi) -or $reportedApi -ne $ExpectedApiLevel) {
        Stop-Verify $ExitCodes.ApiLevelMismatch "Device '$selectedSerial' reports API '$apiText'; expected API $ExpectedApiLevel."
    }

    if ($RequirePackage) {
        $packagePath = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'pm', 'path', $PackageName) -IgnoreFailure
        if ($packagePath -notmatch '^package:') {
            Stop-Verify $ExitCodes.PackageMissing "Package '$PackageName' is not installed on '$selectedSerial'."
        }
    }

    try {
        $propertiesText = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'getprop')
        $screenText = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'wm', 'size')
        $densityText = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'wm', 'density')
        $memoryText = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'cat', '/proc/meminfo')
        $dataDiskText = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'df', '-k', '/data') -IgnoreFailure
        $surfaceFlingerText = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'dumpsys', 'SurfaceFlinger') -IgnoreFailure
        $displayText = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'dumpsys', 'display') -IgnoreFailure

        $model = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'getprop', 'ro.product.model')
        $abi = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'getprop', 'ro.product.cpu.abi')
        $release = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'getprop', 'ro.build.version.release')
        $fingerprint = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'getprop', 'ro.build.fingerprint')
        $rendererLine = $surfaceFlingerText -split "`r?`n" |
            Where-Object { $_ -match '(?i)GLES:|OpenGL ES|Vulkan' } |
            Select-Object -First 1
        if ([string]::IsNullOrWhiteSpace($rendererLine)) {
            $rendererLine = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'getprop', 'ro.hardware.egl') -IgnoreFailure
        }

        $screenMatch = [regex]::Matches($screenText, '(?<width>\d+)x(?<height>\d+)') |
            Select-Object -Last 1
        $densityMatch = [regex]::Matches($densityText, '(?<density>\d+)') |
            Select-Object -Last 1
        $memoryMatch = [regex]::Match($memoryText, '(?m)^MemTotal:\s+(?<kb>\d+)\s+kB')
        $screenWidth = if ($null -ne $screenMatch) { [int]$screenMatch.Groups['width'].Value } else { 0 }
        $screenHeight = if ($null -ne $screenMatch) { [int]$screenMatch.Groups['height'].Value } else { 0 }
        $density = if ($null -ne $densityMatch) { [int]$densityMatch.Groups['density'].Value } else { 0 }
        $memoryKb = if ($memoryMatch.Success) { [long]$memoryMatch.Groups['kb'].Value } else { 0 }

        $emulatorVersion = 'unavailable'
        $acceleration = 'unavailable'
        if (Test-Path -LiteralPath $emulatorPath -PathType Leaf) {
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $versionOutput = & $emulatorPath -version 2>&1
                $emulatorVersion = ($versionOutput | Select-Object -First 1 | Out-String).Trim()
                $accelerationOutput = & $emulatorPath -accel-check 2>&1
                $acceleration = ($accelerationOutput | Out-String).Trim()
            }
            finally {
                $ErrorActionPreference = $previousPreference
            }
        }
    }
    catch {
        Stop-Verify $ExitCodes.PropertyCollectionFailed "Unable to collect emulator properties: $($_.Exception.Message)"
    }

    if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
        $OutputDirectory = Join-Path ([System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))) 'artifacts/emulator'
    }
    try {
        $OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
        New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
        $record = [ordered]@{
            capturedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
            avdName = $AvdName
            serial = $selectedSerial
            apiLevel = $reportedApi
            androidRelease = $release
            systemImageFingerprint = $fingerprint
            abi = $abi
            model = $model
            screenWidthPx = $screenWidth
            screenHeightPx = $screenHeight
            densityDpi = $density
            memoryKb = $memoryKb
            dataFilesystem = $dataDiskText
            renderer = ([string]$rendererLine).Trim()
            emulatorVersion = $emulatorVersion
            acceleration = $acceleration
            packageChecked = [bool]$RequirePackage
            packageName = if ($RequirePackage) { $PackageName } else { $null }
        }
        $record | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'emulator-info.json') -Encoding UTF8
        $propertiesText | Set-Content -LiteralPath (Join-Path $OutputDirectory 'device-properties.txt') -Encoding UTF8
        @($screenText, $densityText, $displayText) | Set-Content -LiteralPath (Join-Path $OutputDirectory 'display-info.txt') -Encoding UTF8
    }
    catch {
        Stop-Verify $ExitCodes.OutputWriteFailed "Unable to write emulator evidence: $($_.Exception.Message)"
    }

    Write-Host "[emulator-verify] PASS AVD=$AvdName serial=$selectedSerial API=$reportedApi ABI=$abi screen=${screenWidth}x${screenHeight} density=$density"
    Write-Output "EMULATOR_SERIAL=$selectedSerial"
    Write-Output "EMULATOR_INFO=$(Join-Path $OutputDirectory 'emulator-info.json')"
    exit $ExitCodes.Success
}
catch {
    Stop-Verify $ExitCodes.UnexpectedFailure $_.Exception.Message
}
