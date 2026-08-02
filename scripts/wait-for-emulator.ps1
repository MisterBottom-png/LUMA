#Requires -Version 5.1

[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$AvdName = 'luma_api_36',

    [string]$AndroidSdkRoot,
    [string]$Serial,

    [ValidateRange(30, 1800)]
    [int]$TimeoutSeconds = 480,

    [ValidateRange(1, 15)]
    [int]$PollSeconds = 2,

    [switch]$KeepAnimations,
    [string]$LogDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExitCodes = [ordered]@{
    Success                = 0
    AdbUnavailable         = 30
    DeviceConnectionTimeout = 31
    BootTimeout            = 32
    AnimationSetupFailed   = 34
    UnexpectedFailure      = 39
}

function Stop-Wait {
    param([int]$Code, [string]$Message)
    [Console]::Error.WriteLine("LUMA-EMU-WAIT-E$Code $Message")
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
    Stop-Wait $ExitCodes.AdbUnavailable 'Android SDK was not found. Pass -AndroidSdkRoot.'
}

function Invoke-AdbText {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$IgnoreFailure
    )

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

    $devicesText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('devices') -IgnoreFailure
    $deviceCandidates = New-Object System.Collections.Generic.List[string]
    foreach ($line in ($devicesText -split "`r?`n")) {
        if ($line -match '^(?<serial>\S+)\s+(?<state>device|offline|unauthorized)\b') {
            $candidateSerial = $Matches['serial']
            if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) {
                if ($candidateSerial -eq $RequestedSerial) { return $RequestedSerial }
            }
            elseif ($candidateSerial -match '^emulator-\d+$') {
                $deviceCandidates.Add($candidateSerial)
            }
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) { return $null }

    foreach ($candidate in $deviceCandidates) {
        $reportedAvd = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $candidate, 'emu', 'avd', 'name') -IgnoreFailure
        $reportedName = $reportedAvd -split "`r?`n" |
            Where-Object { $_ -and $_.Trim() -ne 'OK' } |
            Select-Object -First 1
        if ($null -ne $reportedName -and $reportedName.Trim() -eq $ExpectedAvd) {
            return $candidate
        }
    }
    return $null
}

function Save-Diagnostics {
    param([string]$AdbPath, [string]$DeviceSerial, [string]$Directory)

    try {
        New-Item -ItemType Directory -Path $Directory -Force | Out-Null
        (Invoke-AdbText -AdbPath $AdbPath -Arguments @('devices', '-l') -IgnoreFailure) |
            Set-Content -LiteralPath (Join-Path $Directory 'adb-devices.txt') -Encoding UTF8
        if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
            (Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $DeviceSerial, 'shell', 'getprop') -IgnoreFailure) |
                Set-Content -LiteralPath (Join-Path $Directory 'boot-getprop.txt') -Encoding UTF8
            (Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $DeviceSerial, 'logcat', '-d', '-t', '2000') -IgnoreFailure) |
                Set-Content -LiteralPath (Join-Path $Directory 'boot-logcat.txt') -Encoding UTF8
        }
    }
    catch {
        [Console]::Error.WriteLine("[emulator-wait] Diagnostic capture failed: $($_.Exception.Message)")
    }
}

try {
    $sdkRoot = Resolve-SdkRoot
    $adbName = if (Test-WindowsHost) { 'adb.exe' } else { 'adb' }
    $adbPath = Join-Path $sdkRoot "platform-tools/$adbName"
    if (-not (Test-Path -LiteralPath $adbPath -PathType Leaf)) {
        Stop-Wait $ExitCodes.AdbUnavailable "adb is missing under '$sdkRoot'."
    }
    if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
        $LogDirectory = Join-Path ([System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))) 'artifacts/emulator'
    }
    $LogDirectory = [System.IO.Path]::GetFullPath($LogDirectory)

    [void](Invoke-AdbText -AdbPath $adbPath -Arguments @('start-server'))
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $selectedSerial = $null
    $everConnected = $false
    $fullyBooted = $false
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if ([string]::IsNullOrWhiteSpace($selectedSerial)) {
            $selectedSerial = Find-AvdSerial -AdbPath $adbPath -ExpectedAvd $AvdName -RequestedSerial $Serial
        }
        if (-not [string]::IsNullOrWhiteSpace($selectedSerial)) {
            $state = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'get-state') -IgnoreFailure
            if ($state -eq 'device') {
                $everConnected = $true
                $bootCompleted = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'getprop', 'sys.boot_completed') -IgnoreFailure
                $bootAnimation = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'getprop', 'init.svc.bootanim') -IgnoreFailure
                $packageManager = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'cmd', 'package', 'list', 'packages', 'android') -IgnoreFailure
                # API 36 images may no longer expose init.svc.bootanim after boot.
                # sys.boot_completed and Package Manager readiness remain the authoritative checks.
                $bootAnimationReady = $bootAnimation -eq 'stopped' -or [string]::IsNullOrWhiteSpace($bootAnimation)
                if ($bootCompleted -eq '1' -and $bootAnimationReady -and $packageManager -match 'package:android') {
                    $fullyBooted = $true
                    break
                }
            }
        }
        Start-Sleep -Seconds $PollSeconds
    }

    if ([string]::IsNullOrWhiteSpace($selectedSerial) -or -not $everConnected) {
        Save-Diagnostics -AdbPath $adbPath -DeviceSerial $selectedSerial -Directory $LogDirectory
        Stop-Wait $ExitCodes.DeviceConnectionTimeout "AVD '$AvdName' did not establish an adb device transport within $TimeoutSeconds seconds."
    }

    if (-not $fullyBooted) {
        Save-Diagnostics -AdbPath $adbPath -DeviceSerial $selectedSerial -Directory $LogDirectory
        Stop-Wait $ExitCodes.BootTimeout "AVD '$AvdName' connected as '$selectedSerial' but did not complete boot within $TimeoutSeconds seconds."
    }

    if (-not $KeepAnimations) {
        foreach ($setting in @('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale')) {
            [void](Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'settings', 'put', 'global', $setting, '0'))
            $actual = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'settings', 'get', 'global', $setting)
            if ($actual -notin @('0', '0.0')) {
                Save-Diagnostics -AdbPath $adbPath -DeviceSerial $selectedSerial -Directory $LogDirectory
                Stop-Wait $ExitCodes.AnimationSetupFailed "Unable to disable '$setting' on '$selectedSerial'."
            }
        }
    }

    [void](Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'input', 'keyevent', '82') -IgnoreFailure)
    [void](Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $selectedSerial, 'shell', 'svc', 'power', 'stayon', 'true') -IgnoreFailure)
    Write-Host "[emulator-wait] Boot complete: AVD=$AvdName serial=$selectedSerial animationsDisabled=$(-not $KeepAnimations)"
    Write-Output "EMULATOR_SERIAL=$selectedSerial"
    exit $ExitCodes.Success
}
catch {
    Stop-Wait $ExitCodes.UnexpectedFailure $_.Exception.Message
}
