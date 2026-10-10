#Requires -Version 5.1

[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$AvdName = 'luma_api_36',

    [string]$AndroidSdkRoot,
    [string]$Serial,

    [ValidateSet('auto', 'host', 'angle_indirect', 'swiftshader_indirect', 'swiftshader')]
    [string]$Graphics = 'auto',

    [ValidateRange(1536, 32768)]
    [int]$MemoryMb = 4096,

    [switch]$Headless,
    [switch]$UseSnapshots,
    [string]$LogDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExitCodes = [ordered]@{
    Success                 = 0
    EmulatorUnavailable     = 20
    AvdUnavailable          = 21
    EmulatorStartFailed     = 22
    SerialConflict          = 23
    LogInitializationFailed = 24
    UnexpectedFailure       = 29
}

function Stop-Start {
    param([int]$Code, [string]$Message)
    [Console]::Error.WriteLine("LUMA-EMU-START-E$Code $Message")
    exit $Code
}

function Test-WindowsHost {
    return [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
}

function Invoke-NativeCapture {
    param([string]$FilePath, [string[]]$Arguments = @())
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $FilePath @Arguments 2>&1
        $nativeExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{ Output = @($output); ExitCode = [int]$nativeExitCode }
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

    $userProfilePath = [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::UserProfile)
    if (Test-WindowsHost) {
        if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
            $candidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
        }
        $candidates.Add('C:\Android\Sdk')
    }
    elseif (-not [string]::IsNullOrWhiteSpace($userProfilePath)) {
        $candidates.Add((Join-Path $userProfilePath 'Android/Sdk'))
    }

    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $fullPath = [System.IO.Path]::GetFullPath($candidate)
        if (Test-Path -LiteralPath $fullPath -PathType Container) { return $fullPath }
    }
    Stop-Start $ExitCodes.EmulatorUnavailable 'Android SDK was not found. Pass -AndroidSdkRoot.'
}

function Get-SdkTool {
    param([string]$SdkRoot, [string]$RelativePath)
    $path = Join-Path $SdkRoot $RelativePath
    if (Test-Path -LiteralPath $path -PathType Leaf) { return $path }
    return $null
}

function Get-RunningAvdSerial {
    param([string]$AdbPath, [string]$Name)

    if ($null -eq $AdbPath) { return $null }
    $devicesResult = Invoke-NativeCapture -FilePath $AdbPath -Arguments @('devices')
    $deviceLines = $devicesResult.Output
    if ($devicesResult.ExitCode -ne 0) { return $null }
    foreach ($line in $deviceLines) {
        if ($line -notmatch '^(?<serial>emulator-\d+)\s+device\b') { continue }
        $candidateSerial = $Matches['serial']
        $avdResult = Invoke-NativeCapture -FilePath $AdbPath -Arguments @('-s', $candidateSerial, 'emu', 'avd', 'name')
        $avdOutput = $avdResult.Output
        if ($avdResult.ExitCode -eq 0) {
            $reportedName = $avdOutput | Where-Object { $_ -and $_.Trim() -ne 'OK' } | Select-Object -First 1
            if ($null -ne $reportedName -and $reportedName.Trim() -eq $Name) {
                return $candidateSerial
            }
        }
    }
    return $null
}

try {
    $sdkRoot = Resolve-SdkRoot
    $emulatorName = if (Test-WindowsHost) { 'emulator.exe' } else { 'emulator' }
    $adbName = if (Test-WindowsHost) { 'adb.exe' } else { 'adb' }
    $emulatorPath = Get-SdkTool -SdkRoot $sdkRoot -RelativePath "emulator/$emulatorName"
    $adbPath = Get-SdkTool -SdkRoot $sdkRoot -RelativePath "platform-tools/$adbName"
    if ($null -eq $emulatorPath) {
        Stop-Start $ExitCodes.EmulatorUnavailable "Android Emulator is missing under '$sdkRoot'."
    }

    $avdListResult = Invoke-NativeCapture -FilePath $emulatorPath -Arguments @('-list-avds')
    $avds = $avdListResult.Output
    if ($avdListResult.ExitCode -ne 0) {
        Stop-Start $ExitCodes.EmulatorUnavailable 'The emulator could not enumerate AVDs.'
    }
    if (@($avds | Where-Object { $_.Trim() -eq $AvdName }).Count -eq 0) {
        Stop-Start $ExitCodes.AvdUnavailable "AVD '$AvdName' is not configured. Run setup-android-emulator.ps1 first."
    }

    if (-not [string]::IsNullOrWhiteSpace($Serial) -and $null -ne $adbPath) {
        $stateResult = Invoke-NativeCapture -FilePath $adbPath -Arguments @('-s', $Serial, 'get-state')
        $serialState = ($stateResult.Output | Out-String).Trim()
        if ($stateResult.ExitCode -eq 0 -and $serialState -eq 'device') {
            $nameResult = Invoke-NativeCapture -FilePath $adbPath -Arguments @('-s', $Serial, 'emu', 'avd', 'name')
            $reportedName = $nameResult.Output |
                Where-Object { $_ -and $_.Trim() -ne 'OK' } |
                Select-Object -First 1
            if ($null -eq $reportedName -or $reportedName.Trim() -ne $AvdName) {
                Stop-Start $ExitCodes.SerialConflict "Serial '$Serial' is already attached to a different device or AVD."
            }
            Write-Host "[emulator-start] AVD '$AvdName' is already running as $Serial."
            Write-Output "EMULATOR_SERIAL=$Serial"
            exit $ExitCodes.Success
        }
    }

    $runningSerial = Get-RunningAvdSerial -AdbPath $adbPath -Name $AvdName
    if ($null -ne $runningSerial) {
        Write-Host "[emulator-start] AVD '$AvdName' is already running as $runningSerial."
        Write-Output "EMULATOR_SERIAL=$runningSerial"
        exit $ExitCodes.Success
    }

    if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
        $LogDirectory = Join-Path ([System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))) 'artifacts/emulator'
    }
    try {
        $LogDirectory = [System.IO.Path]::GetFullPath($LogDirectory)
        New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
    }
    catch {
        Stop-Start $ExitCodes.LogInitializationFailed "Unable to initialize '$LogDirectory': $($_.Exception.Message)"
    }

    $accelerationResult = Invoke-NativeCapture -FilePath $emulatorPath -Arguments @('-accel-check')
    $accelerationOutput = $accelerationResult.Output
    $hardwareAcceleration = $accelerationResult.ExitCode -eq 0 -and
        (($accelerationOutput | Out-String) -notmatch '(?i)not installed|not usable|does not support|failed')
    $selectedGraphics = $Graphics
    if ($selectedGraphics -eq 'auto' -and -not $hardwareAcceleration) {
        $selectedGraphics = 'swiftshader_indirect'
    }

    $arguments = New-Object System.Collections.Generic.List[string]
    $arguments.Add('-avd')
    $arguments.Add($AvdName)
    $arguments.Add('-no-boot-anim')
    $arguments.Add('-no-audio')
    $arguments.Add('-no-metrics')
    $arguments.Add('-camera-back')
    $arguments.Add('none')
    $arguments.Add('-camera-front')
    $arguments.Add('none')
    $arguments.Add('-memory')
    $arguments.Add([string]$MemoryMb)
    $arguments.Add('-gpu')
    $arguments.Add($selectedGraphics)
    if (-not $UseSnapshots) {
        $arguments.Add('-no-snapshot')
        $arguments.Add('-no-snapshot-save')
    }
    if (-not $hardwareAcceleration) {
        $arguments.Add('-accel')
        $arguments.Add('off')
    }
    if ($Headless) {
        $arguments.Add('-no-window')
    }

    $stdoutPath = Join-Path $LogDirectory 'emulator.stdout.log'
    $stderrPath = Join-Path $LogDirectory 'emulator.stderr.log'
    $startParameters = @{
        FilePath = $emulatorPath
        ArgumentList = $arguments.ToArray()
        PassThru = $true
        RedirectStandardOutput = $stdoutPath
        RedirectStandardError = $stderrPath
    }
    if (Test-WindowsHost) {
        $startParameters['WindowStyle'] = 'Hidden'
    }

    Write-Host "[emulator-start] Starting '$AvdName' (graphics=$selectedGraphics, hardwareAcceleration=$hardwareAcceleration)."
    $process = Start-Process @startParameters
    Start-Sleep -Seconds 2
    if ($process.HasExited) {
        $stderrTail = if (Test-Path -LiteralPath $stderrPath) {
            (Get-Content -LiteralPath $stderrPath -Tail 30 | Out-String).Trim()
        }
        else { 'No emulator stderr was captured.' }
        Stop-Start $ExitCodes.EmulatorStartFailed "The emulator exited during startup. $stderrTail"
    }

    Write-Output "EMULATOR_PID=$($process.Id)"
    Write-Output "EMULATOR_LOG_DIRECTORY=$LogDirectory"
    exit $ExitCodes.Success
}
catch {
    Stop-Start $ExitCodes.UnexpectedFailure $_.Exception.Message
}
