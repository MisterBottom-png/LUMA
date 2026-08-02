#Requires -Version 5.1

[CmdletBinding()]
param(
    [ValidateRange(1, 99)]
    [int]$ApiLevel = 36,

    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$AvdName = 'luma_api_36',

    [string]$AndroidSdkRoot,
    [string]$JavaHome,
    [string]$BuildToolsVersion = '36.0.0',
    [string]$SystemImagePackage,
    [string]$DeviceProfile = 'pixel_6',

    [ValidateRange(1536, 32768)]
    [int]$MemoryMb = 4096,

    [ValidateRange(1, 16)]
    [int]$CpuCores = 4,

    [ValidateRange(320, 7680)]
    [int]$ScreenWidth = 1080,

    [ValidateRange(480, 7680)]
    [int]$ScreenHeight = 2400,

    [ValidateRange(120, 1000)]
    [int]$ScreenDensity = 420,

    [switch]$AcceptLicenses,
    [switch]$SkipPackageInstall,
    [switch]$NoCommandLineToolsDownload,
    [string]$CommandLineToolsUrl,
    [string]$CommandLineToolsSha256
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExitCodes = [ordered]@{
    Success                    = 0
    JavaUnavailable            = 10
    SdkBootstrapFailed         = 11
    LicenseAcceptanceRequired  = 12
    PackageProvisioningFailed  = 13
    AvdManagerUnavailable      = 14
    AvdCreationFailed          = 15
    ExistingAvdMismatch        = 16
    AvdConfigurationFailed     = 17
    InvalidPackageSelection    = 18
    UnexpectedFailure          = 19
}

function Stop-Setup {
    param(
        [Parameter(Mandatory = $true)][int]$Code,
        [Parameter(Mandatory = $true)][string]$Message
    )

    [Console]::Error.WriteLine("LUMA-EMU-SETUP-E$Code $Message")
    exit $Code
}

function Write-Step {
    param([string]$Message)
    Write-Host "[emulator-setup] $Message"
}

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @(),
        [string[]]$InputLines
    )

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($PSBoundParameters.ContainsKey('InputLines')) {
            $output = $InputLines | & $FilePath @Arguments 2>&1
        }
        else {
            $output = & $FilePath @Arguments 2>&1
        }
        $nativeExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{
        Output = @($output)
        ExitCode = [int]$nativeExitCode
    }
}

function Test-WindowsHost {
    return [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
}

function Convert-LocalPropertiesPath {
    param([string]$Value)

    $decoded = $Value.Trim()
    $decoded = $decoded -replace '\\:', ':'
    $decoded = $decoded -replace '\\\\', '\'
    $decoded = $decoded -replace '\\/', '/'
    return $decoded
}

function Get-RepositoryRoot {
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}

function Resolve-SdkRoot {
    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($AndroidSdkRoot)) {
        $candidates.Add($AndroidSdkRoot)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidates.Add($env:ANDROID_SDK_ROOT)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidates.Add($env:ANDROID_HOME)
    }

    $localProperties = Join-Path (Get-RepositoryRoot) 'local.properties'
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
        $candidates.Add((Join-Path $userProfilePath 'android-sdk'))
    }

    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $fullPath = [System.IO.Path]::GetFullPath($candidate)
        if (Test-Path -LiteralPath $fullPath -PathType Container) {
            return $fullPath
        }
    }

    if ($candidates.Count -gt 0) {
        return [System.IO.Path]::GetFullPath($candidates[0])
    }

    Stop-Setup $ExitCodes.SdkBootstrapFailed 'No Android SDK location could be derived. Pass -AndroidSdkRoot.'
}

function Resolve-JavaHome {
    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
        $candidates.Add($JavaHome)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates.Add($env:JAVA_HOME)
    }

    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        $javaExecutable = $javaCommand.Source
        $candidates.Add((Split-Path -Parent (Split-Path -Parent $javaExecutable)))
    }

    if (Test-WindowsHost) {
        $commonRoots = @(
            (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
            (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
            (Join-Path $env:ProgramFiles 'Microsoft'),
            (Join-Path $env:ProgramFiles 'Java')
        )
        foreach ($root in $commonRoots) {
            if ([string]::IsNullOrWhiteSpace($root) -or -not (Test-Path -LiteralPath $root -PathType Container)) {
                continue
            }
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
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $fullPath = [System.IO.Path]::GetFullPath($candidate)
        $javaPath = Join-Path $fullPath "bin/$javaName"
        if (-not (Test-Path -LiteralPath $javaPath -PathType Leaf)) {
            continue
        }
        $versionResult = Invoke-NativeCapture -FilePath $javaPath -Arguments @('-version')
        $versionOutput = ($versionResult.Output | Out-String)
        if ($versionResult.ExitCode -ne 0) {
            continue
        }
        $versionMatch = [regex]::Match($versionOutput, 'version\s+"(?<major>\d+)(?:\.(?<minor>\d+))?')
        if ($versionMatch.Success) {
            $major = [int]$versionMatch.Groups['major'].Value
            if ($major -eq 1 -and $versionMatch.Groups['minor'].Success) {
                $major = [int]$versionMatch.Groups['minor'].Value
            }
            if ($major -ge 17) {
                return $fullPath
            }
        }
    }

    Stop-Setup $ExitCodes.JavaUnavailable 'A JDK 17 or newer is required. Pass -JavaHome or set JAVA_HOME.'
}

function Get-ToolPath {
    param(
        [Parameter(Mandatory = $true)][string]$SdkRoot,
        [Parameter(Mandatory = $true)][string]$ToolName
    )

    $extensions = if (Test-WindowsHost) { @('.bat', '.exe', '') } else { @('', '.sh') }
    $searchRoots = @(
        (Join-Path $SdkRoot 'cmdline-tools/latest/bin'),
        (Join-Path $SdkRoot 'cmdline-tools')
    )

    foreach ($root in $searchRoots) {
        if (-not (Test-Path -LiteralPath $root -PathType Container)) {
            continue
        }
        foreach ($extension in $extensions) {
            $direct = Join-Path $root ($ToolName + $extension)
            if (Test-Path -LiteralPath $direct -PathType Leaf) {
                return $direct
            }
        }
        $match = Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.BaseName -eq $ToolName -and $_.DirectoryName -match '[\\/]bin$' } |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($null -ne $match) {
            return $match.FullName
        }
    }
    return $null
}

function Get-CommandLineToolsDownload {
    if (-not [string]::IsNullOrWhiteSpace($CommandLineToolsUrl) -and
        -not [string]::IsNullOrWhiteSpace($CommandLineToolsSha256)) {
        return @{
            Url = $CommandLineToolsUrl
            Sha256 = $CommandLineToolsSha256.ToLowerInvariant()
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($CommandLineToolsUrl) -or
        -not [string]::IsNullOrWhiteSpace($CommandLineToolsSha256)) {
        Stop-Setup $ExitCodes.SdkBootstrapFailed 'Pass both -CommandLineToolsUrl and -CommandLineToolsSha256 when overriding the pinned download.'
    }

    $architecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
    if (Test-WindowsHost) {
        return @{
            Url = 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip'
            Sha256 = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
        }
    }
    if ([System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Linux)) {
        return @{
            Url = 'https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip'
            Sha256 = '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583'
        }
    }
    if ([System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::OSX)) {
        if ($architecture -eq 'arm64') {
            return @{
                Url = 'https://dl.google.com/android/repository/commandlinetools-mac_arm64-15859902_latest.zip'
                Sha256 = '835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e'
            }
        }
        return @{
            Url = 'https://dl.google.com/android/repository/commandlinetools-mac_x86_64-15859902_latest.zip'
            Sha256 = 'c5a6378ab5cf7e0d5701921405115befff13e9ff7417fb588389338f8bd050f3'
        }
    }
    Stop-Setup $ExitCodes.SdkBootstrapFailed 'Automatic command-line tools installation is unsupported on this host.'
}

function Install-CommandLineTools {
    param([Parameter(Mandatory = $true)][string]$SdkRoot)

    if ($NoCommandLineToolsDownload) {
        Stop-Setup $ExitCodes.SdkBootstrapFailed 'sdkmanager is missing and command-line tools download was disabled.'
    }
    if (-not $AcceptLicenses) {
        Stop-Setup $ExitCodes.LicenseAcceptanceRequired 'sdkmanager is missing. Re-run with -AcceptLicenses to download the licensed Android command-line tools.'
    }

    $download = Get-CommandLineToolsDownload
    $taskTempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("luma-android-tools-" + [guid]::NewGuid().ToString('N'))
    $archivePath = Join-Path $taskTempRoot 'command-line-tools.zip'
    $extractPath = Join-Path $taskTempRoot 'expanded'
    $latestPath = Join-Path $SdkRoot 'cmdline-tools/latest'

    try {
        New-Item -ItemType Directory -Path $taskTempRoot -Force | Out-Null
        New-Item -ItemType Directory -Path $extractPath -Force | Out-Null
        New-Item -ItemType Directory -Path $SdkRoot -Force | Out-Null

        if (Test-Path -LiteralPath $latestPath) {
            Stop-Setup $ExitCodes.SdkBootstrapFailed "The incomplete command-line tools directory '$latestPath' already exists. Move it aside and rerun."
        }

        Write-Step "Downloading pinned Android command-line tools from $($download.Url)."
        Invoke-WebRequest -Uri $download.Url -OutFile $archivePath -UseBasicParsing
        $actualHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $download.Sha256) {
            Stop-Setup $ExitCodes.SdkBootstrapFailed 'The command-line tools archive checksum did not match the pinned checksum.'
        }

        Expand-Archive -LiteralPath $archivePath -DestinationPath $extractPath
        $expandedTools = Join-Path $extractPath 'cmdline-tools'
        if (-not (Test-Path -LiteralPath (Join-Path $expandedTools 'bin') -PathType Container)) {
            Stop-Setup $ExitCodes.SdkBootstrapFailed 'The command-line tools archive had an unexpected layout.'
        }
        New-Item -ItemType Directory -Path (Split-Path -Parent $latestPath) -Force | Out-Null
        Move-Item -LiteralPath $expandedTools -Destination $latestPath
    }
    catch {
        if ($_.Exception.Message -like 'LUMA-EMU-SETUP-*') {
            throw
        }
        Stop-Setup $ExitCodes.SdkBootstrapFailed "Unable to install Android command-line tools: $($_.Exception.Message)"
    }
    finally {
        $fullTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
        $fullTaskTemp = [System.IO.Path]::GetFullPath($taskTempRoot)
        if ($fullTaskTemp.StartsWith($fullTempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
            (Test-Path -LiteralPath $fullTaskTemp)) {
            Remove-Item -LiteralPath $fullTaskTemp -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

function Get-InstalledPackages {
    param(
        [Parameter(Mandatory = $true)][string]$SdkManager,
        [Parameter(Mandatory = $true)][string]$SdkRoot
    )

    $listResult = Invoke-NativeCapture -FilePath $SdkManager -Arguments @("--sdk_root=$SdkRoot", '--list_installed')
    $output = $listResult.Output
    if ($listResult.ExitCode -ne 0) {
        Stop-Setup $ExitCodes.PackageProvisioningFailed 'sdkmanager could not list installed packages.'
    }
    $installed = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($line in $output) {
        if ($line -match '^\s*(?<package>[^|]+?)\s+\|') {
            [void]$installed.Add($Matches['package'].Trim())
        }
    }
    return ,$installed
}

function Get-AvdConfigPath {
    param([Parameter(Mandatory = $true)][string]$Name)

    $avdHome = $env:ANDROID_AVD_HOME
    if ([string]::IsNullOrWhiteSpace($avdHome)) {
        $androidUserHome = $env:ANDROID_USER_HOME
        if ([string]::IsNullOrWhiteSpace($androidUserHome)) {
            $androidUserHome = Join-Path ([System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::UserProfile)) '.android'
        }
        $avdHome = Join-Path $androidUserHome 'avd'
    }

    $iniPath = Join-Path $avdHome "$Name.ini"
    if (Test-Path -LiteralPath $iniPath -PathType Leaf) {
        $pathLine = Get-Content -LiteralPath $iniPath |
            Where-Object { $_ -match '^path=' } |
            Select-Object -First 1
        if ($null -ne $pathLine) {
            $avdPath = ($pathLine -split '=', 2)[1].Trim()
            return Join-Path $avdPath 'config.ini'
        }
    }
    return Join-Path (Join-Path $avdHome "$Name.avd") 'config.ini'
}

function Set-IniValues {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][hashtable]$Values
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Stop-Setup $ExitCodes.AvdConfigurationFailed "AVD configuration was not found at '$Path'."
    }
    $backupPath = "$Path.luma.bak"
    if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
        Copy-Item -LiteralPath $Path -Destination $backupPath
    }

    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        $lines.Add($line)
    }
    foreach ($entry in $Values.GetEnumerator()) {
        $keyPattern = '^' + [regex]::Escape([string]$entry.Key) + '='
        $replacement = "{0}={1}" -f $entry.Key, $entry.Value
        $replaced = $false
        for ($index = 0; $index -lt $lines.Count; $index++) {
            if ($lines[$index] -match $keyPattern) {
                $lines[$index] = $replacement
                $replaced = $true
                break
            }
        }
        if (-not $replaced) {
            $lines.Add($replacement)
        }
    }

    $temporaryPath = "$Path.luma.tmp"
    [System.IO.File]::WriteAllLines($temporaryPath, $lines, [System.Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporaryPath -Destination $Path -Force
}

try {
    if ([string]::IsNullOrWhiteSpace($SystemImagePackage)) {
        $SystemImagePackage = "system-images;android-$ApiLevel;google_apis;x86_64"
    }
    if ($SystemImagePackage -notmatch "^system-images;android-$ApiLevel;") {
        Stop-Setup $ExitCodes.InvalidPackageSelection "System image '$SystemImagePackage' does not target API $ApiLevel."
    }

    $resolvedJavaHome = Resolve-JavaHome
    $resolvedSdkRoot = Resolve-SdkRoot
    $env:JAVA_HOME = $resolvedJavaHome
    $env:ANDROID_SDK_ROOT = $resolvedSdkRoot
    $env:ANDROID_HOME = $resolvedSdkRoot
    $env:Path = (Join-Path $resolvedJavaHome 'bin') + [System.IO.Path]::PathSeparator + $env:Path

    $sdkManager = Get-ToolPath -SdkRoot $resolvedSdkRoot -ToolName 'sdkmanager'
    if ($null -eq $sdkManager) {
        Install-CommandLineTools -SdkRoot $resolvedSdkRoot
        $sdkManager = Get-ToolPath -SdkRoot $resolvedSdkRoot -ToolName 'sdkmanager'
    }
    if ($null -eq $sdkManager) {
        Stop-Setup $ExitCodes.SdkBootstrapFailed 'sdkmanager is unavailable after command-line tools setup.'
    }

    $requiredPackages = @(
        'platform-tools',
        'emulator',
        "platforms;android-$ApiLevel",
        "build-tools;$BuildToolsVersion",
        $SystemImagePackage
    )
    $installedPackages = Get-InstalledPackages -SdkManager $sdkManager -SdkRoot $resolvedSdkRoot
    $missingPackages = @($requiredPackages | Where-Object { -not $installedPackages.Contains($_) })
    if ($missingPackages.Count -gt 0) {
        if ($SkipPackageInstall) {
            Stop-Setup $ExitCodes.PackageProvisioningFailed ("Required SDK packages are missing: " + ($missingPackages -join ', '))
        }
        if (-not $AcceptLicenses) {
            Stop-Setup $ExitCodes.LicenseAcceptanceRequired ("SDK packages are missing. Re-run with -AcceptLicenses: " + ($missingPackages -join ', '))
        }

        Write-Step 'Accepting Android SDK licenses for unattended provisioning.'
        $licenseResponses = 1..200 | ForEach-Object { 'y' }
        $licenseResult = Invoke-NativeCapture -FilePath $sdkManager -Arguments @("--sdk_root=$resolvedSdkRoot", '--licenses') -InputLines $licenseResponses
        $licenseOutput = $licenseResult.Output
        if ($licenseResult.ExitCode -ne 0) {
            $licenseOutput | Write-Host
            Stop-Setup $ExitCodes.LicenseAcceptanceRequired 'Android SDK license acceptance failed.'
        }

        Write-Step ("Installing SDK packages: " + ($missingPackages -join ', '))
        $installResult = Invoke-NativeCapture -FilePath $sdkManager -Arguments (@("--sdk_root=$resolvedSdkRoot") + $missingPackages)
        $installOutput = $installResult.Output
        if ($installResult.ExitCode -ne 0) {
            $installOutput | Write-Host
            Stop-Setup $ExitCodes.PackageProvisioningFailed 'sdkmanager failed to install one or more required packages.'
        }
        $installedPackages = Get-InstalledPackages -SdkManager $sdkManager -SdkRoot $resolvedSdkRoot
        $stillMissing = @($requiredPackages | Where-Object { -not $installedPackages.Contains($_) })
        if ($stillMissing.Count -gt 0) {
            Stop-Setup $ExitCodes.PackageProvisioningFailed ("Package verification failed after installation: " + ($stillMissing -join ', '))
        }
    }
    else {
        Write-Step 'All required Android SDK packages are already installed.'
    }

    $avdManager = Get-ToolPath -SdkRoot $resolvedSdkRoot -ToolName 'avdmanager'
    if ($null -eq $avdManager) {
        Stop-Setup $ExitCodes.AvdManagerUnavailable 'avdmanager was not found in the Android command-line tools.'
    }

    $avdListResult = Invoke-NativeCapture -FilePath $avdManager -Arguments @('list', 'avd')
    $avdList = $avdListResult.Output
    if ($avdListResult.ExitCode -ne 0) {
        Stop-Setup $ExitCodes.AvdManagerUnavailable 'avdmanager could not list AVDs.'
    }
    $avdExists = ($avdList | Out-String) -match "(?m)^\s*Name:\s*$([regex]::Escape($AvdName))\s*$"
    if (-not $avdExists) {
        Write-Step "Creating AVD '$AvdName' from '$SystemImagePackage'."
        $createResult = Invoke-NativeCapture -FilePath $avdManager -Arguments @('create', 'avd', '--name', $AvdName, '--package', $SystemImagePackage, '--device', $DeviceProfile) -InputLines @('no')
        $createOutput = $createResult.Output
        if ($createResult.ExitCode -ne 0) {
            $createOutput | Write-Host
            Stop-Setup $ExitCodes.AvdCreationFailed "Unable to create AVD '$AvdName'."
        }
    }
    else {
        Write-Step "AVD '$AvdName' already exists."
    }

    $avdConfigPath = Get-AvdConfigPath -Name $AvdName
    if (-not (Test-Path -LiteralPath $avdConfigPath -PathType Leaf)) {
        Stop-Setup $ExitCodes.AvdConfigurationFailed "AVD '$AvdName' has no readable config.ini."
    }
    $configText = Get-Content -LiteralPath $avdConfigPath -Raw
    $normalizedConfig = $configText -replace '/', [System.IO.Path]::DirectorySeparatorChar
    if ($normalizedConfig -notmatch [regex]::Escape(($SystemImagePackage -replace ';', [System.IO.Path]::DirectorySeparatorChar))) {
        Stop-Setup $ExitCodes.ExistingAvdMismatch "AVD '$AvdName' does not use '$SystemImagePackage'. Use a different -AvdName or remove the mismatched AVD explicitly."
    }

    Set-IniValues -Path $avdConfigPath -Values @{
        'disk.dataPartition.size' = '8G'
        'fastboot.forceColdBoot' = 'yes'
        'fastboot.forceFastBoot' = 'no'
        'hw.cpu.ncore' = [string]$CpuCores
        'hw.gpu.enabled' = 'yes'
        'hw.gpu.mode' = 'auto'
        'hw.initialOrientation' = 'portrait'
        'hw.keyboard' = 'yes'
        'hw.lcd.density' = [string]$ScreenDensity
        'hw.lcd.height' = [string]$ScreenHeight
        'hw.lcd.width' = [string]$ScreenWidth
        'hw.ramSize' = [string]$MemoryMb
        'showDeviceFrame' = 'no'
    }

    Write-Step "Ready: AVD=$AvdName API=$ApiLevel SDK=$resolvedSdkRoot JDK=$resolvedJavaHome"
    exit $ExitCodes.Success
}
catch {
    Stop-Setup $ExitCodes.UnexpectedFailure $_.Exception.Message
}
