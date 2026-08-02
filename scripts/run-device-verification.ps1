#Requires -Version 5.1

[CmdletBinding()]
param(
    [string]$RepoRoot,
    [string]$AndroidSdkRoot,
    [string]$JavaHome,

    [ValidateRange(1, 99)]
    [int]$ApiLevel = 36,

    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$AvdName = 'luma_api_36',

    [ValidatePattern('^[A-Za-z][A-Za-z0-9_.]+$')]
    [string]$PackageName = 'com.orbit.app',

    [string]$MainActivity = '.MainActivity',

    [ValidatePattern('^[A-Za-z][A-Za-z0-9]*$')]
    [string]$CandidateVariant = 'benchmark',

    [ValidateRange(60, 1800)]
    [int]$BootTimeoutSeconds = 600,

    [ValidateSet('auto', 'host', 'angle_indirect', 'swiftshader_indirect', 'swiftshader')]
    [string]$EmulatorGraphics = 'auto',

    [ValidateRange(1536, 32768)]
    [int]$EmulatorMemoryMb = 3072,

    [switch]$AcceptLicenses,
    [switch]$Headless,
    [switch]$SkipSetup,
    [switch]$SkipStart,
    [switch]$SkipInstrumentation,
    [switch]$SkipCandidateInstall,
    [switch]$SkipReleaseVerification,
    [switch]$GenerateBaselineProfile,
    [string]$LogDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExitCodes = [ordered]@{
    Success                         = 0
    GradleUnavailable               = 50
    DebugBuildFailed                = 51
    DebugApkMissing                 = 52
    DebugInstallFailed              = 53
    DebugLaunchFailed               = 54
    InstrumentationFailed           = 55
    CandidateBuildFailed            = 56
    CandidateApkInvalid             = 57
    CandidateInstallOrLaunchFailed  = 58
    PortraitRuntimeFailed           = 59
    BaselineProfileGenerationFailed = 60
    AdbUnavailable                  = 66
    ChildInvocationFailed           = 68
    UnexpectedFailure               = 69
}

function Stop-DeviceVerification {
    param([int]$Code, [string]$Message)
    [Console]::Error.WriteLine("LUMA-DEVICE-E$Code $Message")
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

function Resolve-RepositoryRoot {
    $candidate = if ([string]::IsNullOrWhiteSpace($RepoRoot)) { Join-Path $PSScriptRoot '..' } else { $RepoRoot }
    $fullPath = [System.IO.Path]::GetFullPath($candidate)
    if (-not (Test-Path -LiteralPath (Join-Path $fullPath 'settings.gradle.kts') -PathType Leaf) -and
        -not (Test-Path -LiteralPath (Join-Path $fullPath 'settings.gradle') -PathType Leaf)) {
        Stop-DeviceVerification $ExitCodes.GradleUnavailable "'$fullPath' is not the Gradle repository root."
    }
    return $fullPath
}

function Resolve-SdkRoot {
    param([string]$RepositoryRoot)

    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($AndroidSdkRoot)) { $candidates.Add($AndroidSdkRoot) }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) { $candidates.Add($env:ANDROID_SDK_ROOT) }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) { $candidates.Add($env:ANDROID_HOME) }
    $localProperties = Join-Path $RepositoryRoot 'local.properties'
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
    Stop-DeviceVerification $ExitCodes.AdbUnavailable 'Android SDK was not found. Pass -AndroidSdkRoot.'
}

function Resolve-JavaHome {
    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($JavaHome)) { $candidates.Add($JavaHome) }
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { $candidates.Add($env:JAVA_HOME) }
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        $candidates.Add((Split-Path -Parent (Split-Path -Parent $javaCommand.Source)))
    }
    if (Test-WindowsHost) {
        foreach ($root in @(
            (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
            (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
            (Join-Path $env:ProgramFiles 'Microsoft'),
            (Join-Path $env:ProgramFiles 'Java')
        )) {
            if ([string]::IsNullOrWhiteSpace($root) -or -not (Test-Path -LiteralPath $root -PathType Container)) { continue }
            if (Test-Path -LiteralPath (Join-Path $root 'bin\java.exe') -PathType Leaf) {
                $candidates.Add($root)
            }
            else {
                Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
                    Sort-Object Name -Descending |
                    ForEach-Object { $candidates.Add($_.FullName) }
            }
        }
    }
    $javaName = if (Test-WindowsHost) { 'java.exe' } else { 'java' }
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $fullPath = [System.IO.Path]::GetFullPath($candidate)
        $javaPath = Join-Path $fullPath "bin/$javaName"
        if (-not (Test-Path -LiteralPath $javaPath -PathType Leaf)) { continue }
        $versionResult = Invoke-NativeCapture -FilePath $javaPath -Arguments @('-version')
        $versionText = ($versionResult.Output | Out-String)
        if ($versionResult.ExitCode -ne 0) { continue }
        $versionMatch = [regex]::Match($versionText, 'version\s+"(?<major>\d+)(?:\.(?<minor>\d+))?')
        if ($versionMatch.Success) {
            $major = [int]$versionMatch.Groups['major'].Value
            if ($major -eq 1 -and $versionMatch.Groups['minor'].Success) { $major = [int]$versionMatch.Groups['minor'].Value }
            if ($major -ge 17) { return $fullPath }
        }
    }
    Stop-DeviceVerification $ExitCodes.GradleUnavailable 'A JDK 17 or newer is required. Pass -JavaHome or set JAVA_HOME.'
}

function Invoke-ChildScript {
    param([string]$ScriptPath, [string[]]$Arguments)

    if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) {
        Stop-DeviceVerification $ExitCodes.ChildInvocationFailed "Required script '$ScriptPath' is missing."
    }
    $hostExecutable = (Get-Process -Id $PID).Path
    $hostArguments = New-Object System.Collections.Generic.List[string]
    $hostArguments.Add('-NoLogo')
    $hostArguments.Add('-NoProfile')
    if (Test-WindowsHost) {
        $hostArguments.Add('-ExecutionPolicy')
        $hostArguments.Add('Bypass')
    }
    $hostArguments.Add('-File')
    $hostArguments.Add($ScriptPath)
    foreach ($argument in $Arguments) { $hostArguments.Add($argument) }
    $childResult = Invoke-NativeCapture -FilePath $hostExecutable -Arguments $hostArguments.ToArray()
    $childResult.Output | ForEach-Object { Write-Host $_ }
    return $childResult.ExitCode
}

function Invoke-AdbText {
    param([string]$AdbPath, [string[]]$Arguments, [switch]$IgnoreFailure)
    $result = Invoke-NativeCapture -FilePath $AdbPath -Arguments $Arguments
    $output = $result.Output
    $exitCode = $result.ExitCode
    if ($exitCode -ne 0 -and -not $IgnoreFailure) {
        throw "adb $($Arguments -join ' ') failed with exit code $exitCode."
    }
    return (($output | Out-String).Trim())
}

function Find-AvdSerial {
    param([string]$AdbPath, [string]$ExpectedAvd)

    $devicesText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('devices')
    foreach ($line in ($devicesText -split "`r?`n")) {
        if ($line -notmatch '^(?<serial>emulator-\d+)\s+device\b') { continue }
        $candidate = $Matches['serial']
        $avdText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $candidate, 'emu', 'avd', 'name') -IgnoreFailure
        $reportedName = $avdText -split "`r?`n" |
            Where-Object { $_ -and $_.Trim() -ne 'OK' } |
            Select-Object -First 1
        if ($null -ne $reportedName -and $reportedName.Trim() -eq $ExpectedAvd) { return $candidate }
    }
    return $null
}

function Save-DeviceDiagnostics {
    param([string]$AdbPath, [string]$Serial, [string]$Directory, [string]$Prefix)
    try {
        New-Item -ItemType Directory -Path $Directory -Force | Out-Null
        (Invoke-AdbText -AdbPath $AdbPath -Arguments @('devices', '-l') -IgnoreFailure) |
            Set-Content -LiteralPath (Join-Path $Directory "$Prefix-adb-devices.txt") -Encoding UTF8
        if (-not [string]::IsNullOrWhiteSpace($Serial)) {
            (Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'logcat', '-d', '-t', '4000') -IgnoreFailure) |
                Set-Content -LiteralPath (Join-Path $Directory "$Prefix-logcat.txt") -Encoding UTF8
            (Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'activity', 'top') -IgnoreFailure) |
                Set-Content -LiteralPath (Join-Path $Directory "$Prefix-activity-top.txt") -Encoding UTF8
            (Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'window', 'windows') -IgnoreFailure) |
                Set-Content -LiteralPath (Join-Path $Directory "$Prefix-window.txt") -Encoding UTF8
            (Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'package', $PackageName) -IgnoreFailure) |
                Set-Content -LiteralPath (Join-Path $Directory "$Prefix-package.txt") -Encoding UTF8
        }
    }
    catch {
        [Console]::Error.WriteLine("[device-verify] Diagnostic capture failed: $($_.Exception.Message)")
    }
}

function Invoke-Gradle {
    param([string]$GradlePath, [string[]]$Arguments, [string]$LogPath, [int]$FailureCode, [string]$FailureMessage, [string]$WorkingDirectory)
    Push-Location $WorkingDirectory
    try {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $GradlePath @Arguments 2>&1 |
            Tee-Object -FilePath $LogPath |
            ForEach-Object { Write-Host $_ }
        $gradleExitCode = $LASTEXITCODE
    }
    finally {
        if ($null -ne $previousPreference) { $ErrorActionPreference = $previousPreference }
        Pop-Location
    }
    if ($gradleExitCode -ne 0) {
        Stop-DeviceVerification $FailureCode "$FailureMessage Gradle exit code: $gradleExitCode."
    }
}

function Find-VariantApk {
    param([string]$ModuleDirectory, [string]$VariantName)
    $apkRoot = Join-Path $ModuleDirectory 'build/outputs/apk'
    if (-not (Test-Path -LiteralPath $apkRoot -PathType Container)) { return $null }
    return Get-ChildItem -LiteralPath $apkRoot -Recurse -File -Filter '*.apk' -ErrorAction SilentlyContinue |
        Where-Object {
            $_.FullName -match "[\\/]$([regex]::Escape($VariantName))[\\/]" -and
            $_.FullName -notmatch '(?i)androidTest'
        } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
}

function Install-Apk {
    param([string]$AdbPath, [string]$Serial, [string]$ApkPath, [int]$FailureCode, [string]$Label)
    $installText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'install', '-r', '-t', $ApkPath) -IgnoreFailure
    if ($installText -notmatch '(?m)^Success\s*$') {
        Save-DeviceDiagnostics -AdbPath $AdbPath -Serial $Serial -Directory $LogDirectory -Prefix "$Label-install-failure"
        Stop-DeviceVerification $FailureCode "$Label APK installation failed."
    }
    $installedPath = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'pm', 'path', $PackageName) -IgnoreFailure
    if ($installedPath -notmatch '^package:') {
        Save-DeviceDiagnostics -AdbPath $AdbPath -Serial $Serial -Directory $LogDirectory -Prefix "$Label-package-missing"
        Stop-DeviceVerification $FailureCode "$Label package verification failed after install."
    }
}

function Start-And-VerifyApp {
    param([string]$AdbPath, [string]$Serial, [int]$FailureCode, [string]$Label)

    $component = if ($MainActivity.StartsWith('.')) { "$PackageName/$MainActivity" } else { "$PackageName/$MainActivity" }
    [void](Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'logcat', '-c') -IgnoreFailure)
    [void](Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'am', 'force-stop', $PackageName) -IgnoreFailure)
    $launchText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'am', 'start', '-W', '-n', $component) -IgnoreFailure
    if ($launchText -notmatch '(?m)^Status:\s*ok\s*$') {
        Save-DeviceDiagnostics -AdbPath $AdbPath -Serial $Serial -Directory $LogDirectory -Prefix "$Label-launch-failure"
        Stop-DeviceVerification $FailureCode "$Label activity launch failed."
    }
    Start-Sleep -Seconds 3
    $pidText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'pidof', $PackageName) -IgnoreFailure
    $activityText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'activity', 'activities') -IgnoreFailure
    $windowText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'window', 'windows') -IgnoreFailure
    $logcatText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'logcat', '-d', '-t', '2000') -IgnoreFailure
    $crashPattern = '(?s)FATAL EXCEPTION.*Process:\s*' + [regex]::Escape($PackageName)
    $appCrash = $logcatText -match $crashPattern
    $focusedPattern = '(?im)^\s*(mCurrentFocus|mFocusedApp|topResumedActivity).*' + [regex]::Escape($PackageName)
    # API 36 may omit mCurrentFocus from dumpsys window while still reporting the
    # target window. ActivityManager is authoritative for focus; WindowManager
    # confirms that a window belonging to the target package exists.
    $targetWindowPattern = [regex]::Escape("$PackageName/")
    if ([string]::IsNullOrWhiteSpace($pidText) -or $activityText -notmatch $focusedPattern -or $windowText -notmatch $targetWindowPattern -or $appCrash) {
        Save-DeviceDiagnostics -AdbPath $AdbPath -Serial $Serial -Directory $LogDirectory -Prefix "$Label-runtime-failure"
        Stop-DeviceVerification $FailureCode "$Label did not remain alive and focused after launch."
    }
}

function Test-PortraitRuntime {
    param([string]$AdbPath, [string]$Serial, [string]$Label)

    $originalAccelerometer = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'settings', 'get', 'system', 'accelerometer_rotation') -IgnoreFailure
    $originalRotation = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'settings', 'get', 'system', 'user_rotation') -IgnoreFailure
    try {
        [void](Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0'))
        foreach ($rotation in @('1', '3')) {
            [void](Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'settings', 'put', 'system', 'user_rotation', $rotation))
            Start-Sleep -Seconds 3
            $activityText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'activity', 'activities') -IgnoreFailure
            $windowText = Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'window', 'windows') -IgnoreFailure
            # dumpsys wraps long configuration records on API 36, so allow the
            # portrait token to appear on a continuation line near its config key.
            $portraitReported = $activityText -match '(?is)(mCurrentConfig|mLastReportedConfiguration|mConfiguration).{0,2000}\bport\b'
            $focusedPattern = '(?im)^\s*(mCurrentFocus|mFocusedApp|topResumedActivity).*' + [regex]::Escape($PackageName)
            $targetWindowPattern = [regex]::Escape("$PackageName/")
            $appRemainsFocused = $activityText -match $focusedPattern -and $windowText -match $targetWindowPattern
            if (-not $portraitReported -or -not $appRemainsFocused) {
                Save-DeviceDiagnostics -AdbPath $AdbPath -Serial $Serial -Directory $LogDirectory -Prefix "$Label-rotation-$rotation-failure"
                Stop-DeviceVerification $ExitCodes.PortraitRuntimeFailed "$Label did not remain in a reported portrait configuration after rotation request $rotation."
            }
        }
    }
    finally {
        if ($originalRotation -match '^\d+$') {
            [void](Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'settings', 'put', 'system', 'user_rotation', $originalRotation) -IgnoreFailure)
        }
        if ($originalAccelerometer -match '^[01]$') {
            [void](Invoke-AdbText -AdbPath $AdbPath -Arguments @('-s', $Serial, 'shell', 'settings', 'put', 'system', 'accelerometer_rotation', $originalAccelerometer) -IgnoreFailure)
        }
    }
}

try {
    $repositoryRoot = Resolve-RepositoryRoot
    if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
        $LogDirectory = Join-Path $repositoryRoot 'artifacts/device-verification'
    }
    $LogDirectory = [System.IO.Path]::GetFullPath($LogDirectory)
    New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null

    $setupScript = Join-Path $PSScriptRoot 'setup-android-emulator.ps1'
    $startScript = Join-Path $PSScriptRoot 'start-android-emulator.ps1'
    $waitScript = Join-Path $PSScriptRoot 'wait-for-emulator.ps1'
    $verifyEmulatorScript = Join-Path $PSScriptRoot 'verify-emulator.ps1'
    $verifyReleaseScript = Join-Path $PSScriptRoot 'verify-release.ps1'

    if (-not $SkipSetup) {
        $setupArguments = @('-ApiLevel', [string]$ApiLevel, '-AvdName', $AvdName, '-MemoryMb', [string]$EmulatorMemoryMb)
        if (-not [string]::IsNullOrWhiteSpace($AndroidSdkRoot)) { $setupArguments += @('-AndroidSdkRoot', $AndroidSdkRoot) }
        if (-not [string]::IsNullOrWhiteSpace($JavaHome)) { $setupArguments += @('-JavaHome', $JavaHome) }
        if ($AcceptLicenses) { $setupArguments += '-AcceptLicenses' }
        $setupExit = Invoke-ChildScript -ScriptPath $setupScript -Arguments $setupArguments
        if ($setupExit -ne 0) { exit $setupExit }
    }

    if (-not $SkipStart) {
        $startArguments = @(
            '-AvdName', $AvdName,
            '-Graphics', $EmulatorGraphics,
            '-MemoryMb', [string]$EmulatorMemoryMb,
            '-LogDirectory', $LogDirectory
        )
        if (-not [string]::IsNullOrWhiteSpace($AndroidSdkRoot)) { $startArguments += @('-AndroidSdkRoot', $AndroidSdkRoot) }
        if ($Headless) { $startArguments += '-Headless' }
        $startExit = Invoke-ChildScript -ScriptPath $startScript -Arguments $startArguments
        if ($startExit -ne 0) { exit $startExit }
    }

    $waitArguments = @('-AvdName', $AvdName, '-TimeoutSeconds', [string]$BootTimeoutSeconds, '-LogDirectory', $LogDirectory)
    if (-not [string]::IsNullOrWhiteSpace($AndroidSdkRoot)) { $waitArguments += @('-AndroidSdkRoot', $AndroidSdkRoot) }
    $waitExit = Invoke-ChildScript -ScriptPath $waitScript -Arguments $waitArguments
    if ($waitExit -ne 0) { exit $waitExit }

    $verifyArguments = @('-AvdName', $AvdName, '-ExpectedApiLevel', [string]$ApiLevel, '-OutputDirectory', $LogDirectory)
    if (-not [string]::IsNullOrWhiteSpace($AndroidSdkRoot)) { $verifyArguments += @('-AndroidSdkRoot', $AndroidSdkRoot) }
    $verifyExit = Invoke-ChildScript -ScriptPath $verifyEmulatorScript -Arguments $verifyArguments
    if ($verifyExit -ne 0) { exit $verifyExit }

    $sdkRoot = Resolve-SdkRoot -RepositoryRoot $repositoryRoot
    $resolvedJavaHome = Resolve-JavaHome
    $env:ANDROID_SDK_ROOT = $sdkRoot
    $env:ANDROID_HOME = $sdkRoot
    $env:JAVA_HOME = $resolvedJavaHome
    $env:Path = (Join-Path $resolvedJavaHome 'bin') + [System.IO.Path]::PathSeparator + $env:Path
    $adbName = if (Test-WindowsHost) { 'adb.exe' } else { 'adb' }
    $adbPath = Join-Path $sdkRoot "platform-tools/$adbName"
    if (-not (Test-Path -LiteralPath $adbPath -PathType Leaf)) {
        Stop-DeviceVerification $ExitCodes.AdbUnavailable "adb is missing under '$sdkRoot'."
    }
    $serial = Find-AvdSerial -AdbPath $adbPath -ExpectedAvd $AvdName
    if ([string]::IsNullOrWhiteSpace($serial)) {
        Stop-DeviceVerification $ExitCodes.AdbUnavailable "No running adb device matches AVD '$AvdName'."
    }

    $wrapperName = if (Test-WindowsHost) { 'gradlew.bat' } else { 'gradlew' }
    $gradlePath = Join-Path $repositoryRoot $wrapperName
    if (-not (Test-Path -LiteralPath $gradlePath -PathType Leaf)) {
        Stop-DeviceVerification $ExitCodes.GradleUnavailable 'The Gradle wrapper is missing.'
    }
    $appDirectory = Join-Path $repositoryRoot 'app'

    Invoke-Gradle -GradlePath $gradlePath `
        -Arguments @('--no-daemon', '--stacktrace', ':app:assembleDebug', ':app:assembleDebugAndroidTest') `
        -LogPath (Join-Path $LogDirectory 'debug-build.log') `
        -FailureCode $ExitCodes.DebugBuildFailed `
        -FailureMessage 'Debug APK build failed.' `
        -WorkingDirectory $repositoryRoot
    $debugApk = Find-VariantApk -ModuleDirectory $appDirectory -VariantName 'debug'
    if ($null -eq $debugApk) {
        Stop-DeviceVerification $ExitCodes.DebugApkMissing 'The debug APK was not found after a successful Gradle build.'
    }
    Install-Apk -AdbPath $adbPath -Serial $serial -ApkPath $debugApk.FullName -FailureCode $ExitCodes.DebugInstallFailed -Label 'debug'
    Start-And-VerifyApp -AdbPath $adbPath -Serial $serial -FailureCode $ExitCodes.DebugLaunchFailed -Label 'debug'
    Test-PortraitRuntime -AdbPath $adbPath -Serial $serial -Label 'debug'

    if (-not $SkipInstrumentation) {
        Push-Location $repositoryRoot
        try {
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & $gradlePath --no-daemon --stacktrace :app:connectedDebugAndroidTest 2>&1 |
                Tee-Object -FilePath (Join-Path $LogDirectory 'connected-debug-android-test.log') |
                ForEach-Object { Write-Host $_ }
            $instrumentationExit = $LASTEXITCODE
        }
        finally {
            if ($null -ne $previousPreference) { $ErrorActionPreference = $previousPreference }
            Pop-Location
        }
        if ($instrumentationExit -ne 0) {
            Save-DeviceDiagnostics -AdbPath $adbPath -Serial $serial -Directory $LogDirectory -Prefix 'instrumentation-failure'
            Stop-DeviceVerification $ExitCodes.InstrumentationFailed "Connected instrumentation tests failed with Gradle exit code $instrumentationExit."
        }
    }

    if ($GenerateBaselineProfile) {
        Push-Location $repositoryRoot
        try {
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & $gradlePath --no-daemon --stacktrace :app:generateBaselineProfile 2>&1 |
                Tee-Object -FilePath (Join-Path $LogDirectory 'generate-baseline-profile.log') |
                ForEach-Object { Write-Host $_ }
            $profileExit = $LASTEXITCODE
        }
        finally {
            if ($null -ne $previousPreference) { $ErrorActionPreference = $previousPreference }
            Pop-Location
        }
        if ($profileExit -ne 0) {
            Save-DeviceDiagnostics -AdbPath $adbPath -Serial $serial -Directory $LogDirectory -Prefix 'baseline-profile-failure'
            Stop-DeviceVerification $ExitCodes.BaselineProfileGenerationFailed "Baseline Profile generation failed with Gradle exit code $profileExit."
        }
    }

    if (-not $SkipCandidateInstall) {
        $candidateTitle = $CandidateVariant.Substring(0, 1).ToUpperInvariant() + $CandidateVariant.Substring(1)
        Invoke-Gradle -GradlePath $gradlePath `
            -Arguments @('--no-daemon', '--stacktrace', ":app:assemble$candidateTitle") `
            -LogPath (Join-Path $LogDirectory 'candidate-build.log') `
            -FailureCode $ExitCodes.CandidateBuildFailed `
            -FailureMessage "Candidate variant '$CandidateVariant' build failed." `
            -WorkingDirectory $repositoryRoot
        $candidateApk = Find-VariantApk -ModuleDirectory $appDirectory -VariantName $CandidateVariant
        if ($null -eq $candidateApk) {
            Stop-DeviceVerification $ExitCodes.CandidateApkInvalid "The '$CandidateVariant' APK was not found."
        }
        $apkSignerName = if (Test-WindowsHost) { 'apksigner.bat' } else { 'apksigner' }
        $apkSigner = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Recurse -File -Filter $apkSignerName -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($null -eq $apkSigner) {
            Stop-DeviceVerification $ExitCodes.CandidateApkInvalid 'apksigner is unavailable for candidate verification.'
        }
        $signatureResult = Invoke-NativeCapture -FilePath $apkSigner.FullName -Arguments @('verify', '--verbose', $candidateApk.FullName)
        if ($signatureResult.ExitCode -ne 0) {
            Stop-DeviceVerification $ExitCodes.CandidateApkInvalid "The '$CandidateVariant' APK is not installably signed."
        }
        Install-Apk -AdbPath $adbPath -Serial $serial -ApkPath $candidateApk.FullName -FailureCode $ExitCodes.CandidateInstallOrLaunchFailed -Label 'candidate'
        Start-And-VerifyApp -AdbPath $adbPath -Serial $serial -FailureCode $ExitCodes.CandidateInstallOrLaunchFailed -Label 'candidate'
        $packageDump = Invoke-AdbText -AdbPath $adbPath -Arguments @('-s', $serial, 'shell', 'dumpsys', 'package', $PackageName) -IgnoreFailure
        if ($packageDump -match '(?im)^\s*(?:pkgFlags|flags)=\[[^\]]*\bDEBUGGABLE\b') {
            Save-DeviceDiagnostics -AdbPath $adbPath -Serial $serial -Directory $LogDirectory -Prefix 'candidate-debuggable-failure'
            Stop-DeviceVerification $ExitCodes.CandidateInstallOrLaunchFailed "The installed '$CandidateVariant' candidate is debuggable."
        }
        Test-PortraitRuntime -AdbPath $adbPath -Serial $serial -Label 'candidate'
    }

    if (-not $SkipReleaseVerification) {
        $releaseArguments = @('-RepoRoot', $repositoryRoot, '-RequiredTargetSdk', [string]$ApiLevel, '-ExpectedPackageName', $PackageName, '-RequireBaselineProfiles')
        if (-not [string]::IsNullOrWhiteSpace($AndroidSdkRoot)) { $releaseArguments += @('-AndroidSdkRoot', $AndroidSdkRoot) }
        if (-not [string]::IsNullOrWhiteSpace($JavaHome)) { $releaseArguments += @('-JavaHome', $JavaHome) }
        $releaseExit = Invoke-ChildScript -ScriptPath $verifyReleaseScript -Arguments $releaseArguments
        if ($releaseExit -ne 0) { exit $releaseExit }
    }

    $finalVerifyArguments = @(
        '-AvdName', $AvdName,
        '-ExpectedApiLevel', [string]$ApiLevel,
        '-PackageName', $PackageName,
        '-RequirePackage',
        '-OutputDirectory', $LogDirectory
    )
    if (-not [string]::IsNullOrWhiteSpace($AndroidSdkRoot)) { $finalVerifyArguments += @('-AndroidSdkRoot', $AndroidSdkRoot) }
    $finalVerifyExit = Invoke-ChildScript -ScriptPath $verifyEmulatorScript -Arguments $finalVerifyArguments
    if ($finalVerifyExit -ne 0) { exit $finalVerifyExit }

    Write-Host "[device-verify] PASS AVD=$AvdName API=$ApiLevel serial=$serial package=$PackageName"
    Write-Output "DEVICE_VERIFICATION_LOGS=$LogDirectory"
    exit $ExitCodes.Success
}
catch {
    Stop-DeviceVerification $ExitCodes.UnexpectedFailure $_.Exception.Message
}
