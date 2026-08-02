#Requires -Version 5.1

[CmdletBinding()]
param(
    [string]$RepoRoot,

    [ValidatePattern('^:[A-Za-z0-9_.:-]+$')]
    [string]$ModulePath = ':app',

    [ValidatePattern('^[A-Za-z][A-Za-z0-9]*$')]
    [string]$Variant = 'release',

    [ValidateRange(1, 99)]
    [int]$RequiredTargetSdk = 36,

    [ValidatePattern('^[A-Za-z][A-Za-z0-9_.]+$')]
    [string]$ExpectedPackageName = 'com.orbit.app',

    [string[]]$ExpectedExportedComponents = @('com.orbit.app.MainActivity'),
    [string[]]$AllowedPortraitOrientations = @('portrait', 'sensorPortrait', 'userPortrait', 'reversePortrait'),
    [string]$AndroidSdkRoot,
    [string]$JavaHome,
    [string]$OutputDirectory,
    [switch]$SkipBuild,
    [switch]$RequireSigned,
    [switch]$RequireBaselineProfiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExitCodes = [ordered]@{
    Success                     = 0
    GradleBuildFailed           = 70
    ReleaseApkMissing           = 71
    MergedManifestMissing       = 72
    ReleaseIsDebuggable         = 73
    TargetSdkMismatch           = 74
    PortraitOrientationMissing  = 75
    PortraitOrientationInvalid  = 76
    UnexpectedExportedComponent = 77
    DevelopmentToolingPresent   = 78
    SigningInputCommitted       = 79
    ReleaseBundleMissing        = 80
    ApplicationIdMismatch       = 81
    EmbeddedSecretDetected      = 82
    ArtifactAnalyzerUnavailable = 83
    BaselineProfileMissing      = 84
    SignatureInvalid            = 85
    DependencyInspectionFailed  = 86
    ManifestParseFailed         = 87
    UnexpectedFailure           = 89
}

function Stop-Release {
    param([int]$Code, [string]$Message)
    [Console]::Error.WriteLine("LUMA-RELEASE-E$Code $Message")
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
    if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
        $candidate = Join-Path $PSScriptRoot '..'
    }
    else {
        $candidate = $RepoRoot
    }
    $fullPath = [System.IO.Path]::GetFullPath($candidate)
    if (-not (Test-Path -LiteralPath (Join-Path $fullPath 'settings.gradle.kts') -PathType Leaf) -and
        -not (Test-Path -LiteralPath (Join-Path $fullPath 'settings.gradle') -PathType Leaf)) {
        Stop-Release $ExitCodes.GradleBuildFailed "'$fullPath' is not an Android Gradle repository root."
    }
    return $fullPath
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
        $match = [regex]::Match($versionText, 'version\s+"(?<major>\d+)(?:\.(?<minor>\d+))?')
        if ($match.Success) {
            $major = [int]$match.Groups['major'].Value
            if ($major -eq 1 -and $match.Groups['minor'].Success) { $major = [int]$match.Groups['minor'].Value }
            if ($major -ge 17) { return $fullPath }
        }
    }
    Stop-Release $ExitCodes.GradleBuildFailed 'A JDK 17 or newer is required. Pass -JavaHome or set JAVA_HOME.'
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
    Stop-Release $ExitCodes.ArtifactAnalyzerUnavailable 'Android SDK was not found. Pass -AndroidSdkRoot.'
}

function Get-SdkTool {
    param([string]$SdkRoot, [string]$ToolName)

    $extensions = if (Test-WindowsHost) { @('.bat', '.exe', '') } else { @('', '.sh') }
    $roots = @(
        (Join-Path $SdkRoot 'cmdline-tools/latest/bin'),
        (Join-Path $SdkRoot 'cmdline-tools'),
        (Join-Path $SdkRoot 'build-tools')
    )
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root -PathType Container)) { continue }
        foreach ($extension in $extensions) {
            $directPath = Join-Path $root ($ToolName + $extension)
            if (Test-Path -LiteralPath $directPath -PathType Leaf) { return $directPath }
        }
        $match = Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.BaseName -eq $ToolName } |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($null -ne $match) { return $match.FullName }
    }
    return $null
}

function Invoke-Gradle {
    param(
        [string]$GradlePath,
        [string[]]$Arguments,
        [string]$LogPath,
        [int]$FailureCode,
        [string]$FailureMessage,
        [string]$WorkingDirectory,
        [switch]$SuppressConsoleOutput
    )

    Push-Location $WorkingDirectory
    try {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        if ($SuppressConsoleOutput) {
            & $GradlePath @Arguments 2>&1 |
                Tee-Object -FilePath $LogPath |
                Out-Null
        }
        else {
            & $GradlePath @Arguments 2>&1 |
                Tee-Object -FilePath $LogPath |
                ForEach-Object { Write-Host $_ }
        }
        $gradleExitCode = $LASTEXITCODE
    }
    finally {
        if ($null -ne $previousPreference) { $ErrorActionPreference = $previousPreference }
        Pop-Location
    }
    if ($gradleExitCode -ne 0) {
        Stop-Release $FailureCode "$FailureMessage Gradle exit code: $gradleExitCode."
    }
}

function Get-ModuleDirectory {
    param([string]$RepositoryRoot, [string]$GradleModulePath)
    $relative = $GradleModulePath.TrimStart(':') -replace ':', [System.IO.Path]::DirectorySeparatorChar
    return [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot $relative))
}

function Find-ReleaseArtifact {
    param([string]$Root, [string]$ArtifactExtension, [string]$BuildVariant)

    $outputRoot = Join-Path $Root 'build/outputs'
    if (-not (Test-Path -LiteralPath $outputRoot -PathType Container)) { return $null }
    return Get-ChildItem -LiteralPath $outputRoot -Recurse -File -Filter "*.$ArtifactExtension" -ErrorAction SilentlyContinue |
        Where-Object {
            $_.FullName -match "[\\/]$([regex]::Escape($BuildVariant))[\\/]" -or
            $_.BaseName -match "-$([regex]::Escape($BuildVariant))(?:-|$)"
        } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
}

function Find-MergedManifest {
    param([string]$ModuleDirectory, [string]$BuildVariant)

    $intermediates = Join-Path $ModuleDirectory 'build/intermediates'
    if (-not (Test-Path -LiteralPath $intermediates -PathType Container)) { return $null }
    return Get-ChildItem -LiteralPath $intermediates -Recurse -File -Filter 'AndroidManifest.xml' -ErrorAction SilentlyContinue |
        Where-Object {
            $_.FullName -match '[\\/](merged_manifest|merged_manifests|packaged_manifests)[\\/]' -and
            $_.FullName -match "[\\/]$([regex]::Escape($BuildVariant))[\\/]" -and
            $_.FullName -notmatch '(?i)androidTest|unitTest'
        } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
}

function Resolve-ComponentName {
    param([string]$Name, [string]$PackageName)
    if ($Name.StartsWith('.')) { return $PackageName + $Name }
    if ($Name.Contains('.')) { return $Name }
    return "$PackageName.$Name"
}

function Read-ManifestXml {
    param([string]$XmlText, [string]$Description)
    try {
        [xml]$manifestXml = $XmlText
        return $manifestXml
    }
    catch {
        Stop-Release $ExitCodes.ManifestParseFailed "$Description could not be parsed as XML."
    }
}

function Test-ManifestContract {
    param(
        [xml]$ManifestXml,
        [string]$Description,
        [string]$ExpectedPackage,
        [int]$ExpectedTargetSdk
    )

    $androidNamespace = 'http://schemas.android.com/apk/res/android'
    $namespaceManager = [System.Xml.XmlNamespaceManager]::new($ManifestXml.NameTable)
    $namespaceManager.AddNamespace('android', $androidNamespace)
    $manifestNode = $ManifestXml.SelectSingleNode('/manifest')
    $applicationNode = $ManifestXml.SelectSingleNode('/manifest/application')
    if ($null -eq $manifestNode -or $null -eq $applicationNode) {
        Stop-Release $ExitCodes.ManifestParseFailed "$Description has no manifest/application structure."
    }

    $packageName = $manifestNode.GetAttribute('package')
    if ($packageName -ne $ExpectedPackage) {
        Stop-Release $ExitCodes.ApplicationIdMismatch "$Description application id is '$packageName'; expected '$ExpectedPackage'."
    }

    $debuggable = $applicationNode.GetAttribute('debuggable', $androidNamespace)
    if ($debuggable -eq 'true') {
        Stop-Release $ExitCodes.ReleaseIsDebuggable "$Description declares android:debuggable=true."
    }

    $usesSdkNode = $ManifestXml.SelectSingleNode('/manifest/uses-sdk')
    $targetSdkText = if ($null -ne $usesSdkNode) { $usesSdkNode.GetAttribute('targetSdkVersion', $androidNamespace) } else { '' }
    $targetSdk = 0
    if (-not [int]::TryParse($targetSdkText, [ref]$targetSdk) -or $targetSdk -lt $ExpectedTargetSdk) {
        Stop-Release $ExitCodes.TargetSdkMismatch "$Description target SDK is '$targetSdkText'; required minimum is $ExpectedTargetSdk."
    }

    $forbiddenComponentPatterns = @(
        '^androidx\.compose\.ui\.tooling\.preview\.PreviewActivity$',
        '^androidx\.activity\.ComponentActivity$',
        '(?i)(^|\.)leakcanary(\.|$)',
        '(?i)(^|\.)debug(\.|$)',
        '(?i)(^|\.)[^.]*test[^.]*activity$'
    )
    $componentNodes = $ManifestXml.SelectNodes('/manifest/application/activity | /manifest/application/activity-alias | /manifest/application/service | /manifest/application/receiver | /manifest/application/provider')
    foreach ($componentNode in $componentNodes) {
        $rawName = $componentNode.GetAttribute('name', $androidNamespace)
        if ([string]::IsNullOrWhiteSpace($rawName)) { continue }
        $resolvedName = Resolve-ComponentName -Name $rawName -PackageName $packageName
        foreach ($pattern in $forbiddenComponentPatterns) {
            if ($resolvedName -match $pattern) {
                Stop-Release $ExitCodes.DevelopmentToolingPresent "$Description contains a development or test component."
            }
        }

        if ($componentNode.LocalName -eq 'activity' -and $resolvedName.StartsWith("$ExpectedPackage.", [System.StringComparison]::Ordinal)) {
            $orientation = $componentNode.GetAttribute('screenOrientation', $androidNamespace)
            if ([string]::IsNullOrWhiteSpace($orientation)) {
                Stop-Release $ExitCodes.PortraitOrientationMissing "$Description has an application-owned activity without android:screenOrientation."
            }
            # apkanalyzer serializes framework orientation constants numerically in the
            # final APK manifest: portrait=1, sensorPortrait=7, reversePortrait=9,
            # and userPortrait=12.
            $allowedOrientationValues = @($AllowedPortraitOrientations) + @('1', '7', '9', '12')
            if ($orientation -notin $allowedOrientationValues) {
                Stop-Release $ExitCodes.PortraitOrientationInvalid "$Description has an application-owned activity with unsupported orientation '$orientation'."
            }
        }

        $exported = $componentNode.GetAttribute('exported', $androidNamespace)
        if ($exported -eq 'true') {
            $expectedExport = $ExpectedExportedComponents -contains $resolvedName
            $permission = $componentNode.GetAttribute('permission', $androidNamespace)
            $protectedProfileInstaller = $resolvedName -eq 'androidx.profileinstaller.ProfileInstallReceiver' -and
                $permission -eq 'android.permission.DUMP'
            $protectedSystemJobService = $resolvedName -eq 'androidx.work.impl.background.systemjob.SystemJobService' -and
                $permission -eq 'android.permission.BIND_JOB_SERVICE'
            if (-not $expectedExport -and -not $protectedProfileInstaller -and -not $protectedSystemJobService) {
                Stop-Release $ExitCodes.UnexpectedExportedComponent "$Description contains an exported component outside the release allowlist."
            }
        }
    }

    return [ordered]@{
        packageName = $packageName
        targetSdk = $targetSdk
        debuggable = if ([string]::IsNullOrWhiteSpace($debuggable)) { $false } else { [bool]::Parse($debuggable) }
        appOwnedActivityCount = @($componentNodes | Where-Object {
            $_.LocalName -eq 'activity' -and
            (Resolve-ComponentName -Name $_.GetAttribute('name', $androidNamespace) -PackageName $packageName).StartsWith("$ExpectedPackage.")
        }).Count
    }
}

function Test-TrackedRepositoryInputs {
    param([string]$RepositoryRoot)

    $gitCommand = Get-Command git -ErrorAction SilentlyContinue
    if ($null -eq $gitCommand) {
        Write-Warning 'git is unavailable; committed signing-input inspection is not applicable to this source copy.'
        return $false
    }
    $workTreeResult = Invoke-NativeCapture -FilePath $gitCommand.Source -Arguments @('-C', $RepositoryRoot, 'rev-parse', '--is-inside-work-tree')
    $isWorkTree = ($workTreeResult.Output | Out-String).Trim()
    if ($workTreeResult.ExitCode -ne 0 -or $isWorkTree -ne 'true') {
        Write-Warning 'The source copy has no readable Git index; committed signing-input inspection is not applicable.'
        return $false
    }

    $trackedResult = Invoke-NativeCapture -FilePath $gitCommand.Source -Arguments @('-C', $RepositoryRoot, 'ls-files')
    $trackedPaths = $trackedResult.Output
    if ($trackedResult.ExitCode -ne 0) {
        Stop-Release $ExitCodes.SigningInputCommitted 'Unable to inspect tracked repository paths.'
    }
    $rootPrefix = $RepositoryRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    $textExtensions = @('.gradle', '.kts', '.properties', '.xml', '.json', '.yml', '.yaml', '.toml', '.kt', '.java', '.md', '.txt', '.ps1', '.sh')
    foreach ($trackedPath in $trackedPaths) {
        $normalizedTrackedPath = $trackedPath -replace '\\', '/'
        if ($normalizedTrackedPath -match '(?i)\.(jks|keystore|p12|pfx)$' -or
            $normalizedTrackedPath -match '(?i)(^|/)(key|keystore|signing)\.properties$') {
            Stop-Release $ExitCodes.SigningInputCommitted 'A signing key or signing credential file is tracked by Git.'
        }

        $fullPath = [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot $trackedPath))
        if (-not $fullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            continue
        }
        if ([System.IO.Path]::GetExtension($fullPath) -notin $textExtensions -or (Get-Item -LiteralPath $fullPath).Length -gt 5MB) {
            continue
        }
        $content = Get-Content -LiteralPath $fullPath -Raw
        if ($content -match 'AIza[0-9A-Za-z_-]{35}') {
            Stop-Release $ExitCodes.EmbeddedSecretDetected 'A Google API-key-shaped value is present in a tracked text file.'
        }
        foreach ($line in ($content -split "`r?`n")) {
            $passwordMatch = [regex]::Match($line, '(?i)^\s*(storePassword|keyPassword)\s*[=:]\s*(?<value>.+?)\s*$')
            if (-not $passwordMatch.Success) { continue }
            $value = $passwordMatch.Groups['value'].Value.Trim().Trim('"', "'")
            $isIndirection = $value -match '(?i)environmentVariable|System\.getenv|providers\.environmentVariable|\$\{|<[^>]+>|placeholder|redacted|not[_-]?set|\*{3,}'
            if (-not [string]::IsNullOrWhiteSpace($value) -and -not $isIndirection) {
                Stop-Release $ExitCodes.SigningInputCommitted 'A literal signing password is tracked by Git.'
            }
        }
    }
    return $true
}

function Test-FileForApiKeyShape {
    param([string]$Path)

    $stream = [System.IO.File]::OpenRead($Path)
    $builder = [System.Text.StringBuilder]::new()
    $buffer = New-Object byte[] 65536
    try {
        while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            for ($index = 0; $index -lt $read; $index++) {
                $value = $buffer[$index]
                if ($value -ge 32 -and $value -le 126) {
                    [void]$builder.Append([char]$value)
                    if ($builder.Length -gt 256) {
                        $candidate = $builder.ToString()
                        if ($candidate -match 'AIza[0-9A-Za-z_-]{35}') { return $true }
                        [void]$builder.Remove(0, $builder.Length - 64)
                    }
                }
                else {
                    if ($builder.Length -ge 39 -and $builder.ToString() -match 'AIza[0-9A-Za-z_-]{35}') { return $true }
                    [void]$builder.Clear()
                }
            }
        }
        return $builder.Length -ge 39 -and $builder.ToString() -match 'AIza[0-9A-Za-z_-]{35}'
    }
    finally {
        $stream.Dispose()
    }
}

function Test-BaselineProfiles {
    param([string]$ModuleDirectory, [string]$ApkPath)

    $baselineText = Get-ChildItem -LiteralPath $ModuleDirectory -Recurse -File -Filter 'baseline-prof.txt' -ErrorAction SilentlyContinue |
        Where-Object { $_.Length -gt 0 } |
        Select-Object -First 1
    $startupText = Get-ChildItem -LiteralPath $ModuleDirectory -Recurse -File -Filter 'startup-prof.txt' -ErrorAction SilentlyContinue |
        Where-Object { $_.Length -gt 0 } |
        Select-Object -First 1
    if ($null -eq $baselineText -or $null -eq $startupText) { return $false }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        $hasPackagedBaseline = $entryNames -contains 'assets/dexopt/baseline.prof'
        $hasPackagedMetadata = $entryNames -contains 'assets/dexopt/baseline.profm'
        return $hasPackagedBaseline -and $hasPackagedMetadata
    }
    finally {
        $archive.Dispose()
    }
}

try {
    $repositoryRoot = Resolve-RepositoryRoot
    $moduleDirectory = Get-ModuleDirectory -RepositoryRoot $repositoryRoot -GradleModulePath $ModulePath
    if (-not (Test-Path -LiteralPath $moduleDirectory -PathType Container)) {
        Stop-Release $ExitCodes.GradleBuildFailed "Module '$ModulePath' was not found."
    }
    if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
        $OutputDirectory = Join-Path $repositoryRoot 'artifacts/release'
    }
    $OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

    $resolvedJavaHome = Resolve-JavaHome
    $sdkRoot = Resolve-SdkRoot -RepositoryRoot $repositoryRoot
    $env:JAVA_HOME = $resolvedJavaHome
    $env:ANDROID_SDK_ROOT = $sdkRoot
    $env:ANDROID_HOME = $sdkRoot
    $env:Path = (Join-Path $resolvedJavaHome 'bin') + [System.IO.Path]::PathSeparator + $env:Path

    $wrapperName = if (Test-WindowsHost) { 'gradlew.bat' } else { 'gradlew' }
    $gradlePath = Join-Path $repositoryRoot $wrapperName
    if (-not (Test-Path -LiteralPath $gradlePath -PathType Leaf)) {
        Stop-Release $ExitCodes.GradleBuildFailed 'The Gradle wrapper is missing.'
    }
    $variantTitle = $Variant.Substring(0, 1).ToUpperInvariant() + $Variant.Substring(1)
    if (-not $SkipBuild) {
        Invoke-Gradle -GradlePath $gradlePath `
            -Arguments @('--no-daemon', '--stacktrace', "${ModulePath}:assemble$variantTitle", "${ModulePath}:bundle$variantTitle") `
            -LogPath (Join-Path $OutputDirectory 'release-build.log') `
            -FailureCode $ExitCodes.GradleBuildFailed `
            -FailureMessage 'Release APK/AAB build failed.' `
            -WorkingDirectory $repositoryRoot
    }

    $dependencyConfiguration = "${Variant}RuntimeClasspath"
    Invoke-Gradle -GradlePath $gradlePath `
        -Arguments @('--no-daemon', "${ModulePath}:dependencies", '--configuration', $dependencyConfiguration) `
        -LogPath (Join-Path $OutputDirectory 'release-dependencies.log') `
        -FailureCode $ExitCodes.DependencyInspectionFailed `
        -FailureMessage 'Release runtime dependency inspection failed.' `
        -WorkingDirectory $repositoryRoot `
        -SuppressConsoleOutput
    $dependencyText = Get-Content -LiteralPath (Join-Path $OutputDirectory 'release-dependencies.log') -Raw
    foreach ($forbiddenDependency in @(
        'androidx\.compose\.ui:ui-tooling:',
        'androidx\.compose\.ui:ui-test-manifest:',
        'androidx\.test:',
        'junit:junit:',
        '(?i)leakcanary',
        'androidx\.benchmark:'
    )) {
        if ($dependencyText -match $forbiddenDependency) {
            Stop-Release $ExitCodes.DevelopmentToolingPresent 'The release runtime classpath contains a debug, test, or benchmark dependency.'
        }
    }

    $releaseApk = Find-ReleaseArtifact -Root $moduleDirectory -ArtifactExtension 'apk' -BuildVariant $Variant
    if ($null -eq $releaseApk) {
        Stop-Release $ExitCodes.ReleaseApkMissing "No $Variant APK was found under the module build outputs."
    }
    $releaseBundle = Find-ReleaseArtifact -Root $moduleDirectory -ArtifactExtension 'aab' -BuildVariant $Variant
    if ($null -eq $releaseBundle) {
        Stop-Release $ExitCodes.ReleaseBundleMissing "No $Variant AAB was found under the module build outputs."
    }
    $mergedManifest = Find-MergedManifest -ModuleDirectory $moduleDirectory -BuildVariant $Variant
    if ($null -eq $mergedManifest) {
        Stop-Release $ExitCodes.MergedManifestMissing "No merged $Variant manifest was found."
    }

    $apkAnalyzer = Get-SdkTool -SdkRoot $sdkRoot -ToolName 'apkanalyzer'
    if ($null -eq $apkAnalyzer) {
        Stop-Release $ExitCodes.ArtifactAnalyzerUnavailable 'apkanalyzer is required to inspect the built release APK.'
    }
    $analyzerResult = Invoke-NativeCapture -FilePath $apkAnalyzer -Arguments @('manifest', 'print', $releaseApk.FullName)
    if ($analyzerResult.ExitCode -ne 0 -or $analyzerResult.Output.Count -eq 0) {
        Stop-Release $ExitCodes.ArtifactAnalyzerUnavailable 'apkanalyzer could not extract the final release manifest.'
    }
    $allAnalyzerLines = @($analyzerResult.Output | ForEach-Object { $_.ToString() })
    $manifestStart = -1
    for ($lineIndex = 0; $lineIndex -lt $allAnalyzerLines.Count; $lineIndex++) {
        if ($allAnalyzerLines[$lineIndex] -match '^\s*(?:<\?xml\b|<manifest\b)') {
            $manifestStart = $lineIndex
            break
        }
    }
    if ($manifestStart -lt 0) {
        Stop-Release $ExitCodes.ManifestParseFailed 'apkanalyzer output did not contain a manifest XML document.'
    }
    if ($manifestStart -gt 0) {
        $allAnalyzerLines[0..($manifestStart - 1)] | Set-Content -LiteralPath (Join-Path $OutputDirectory 'apkanalyzer.stderr.log') -Encoding UTF8
    }
    $finalManifestLines = $allAnalyzerLines[$manifestStart..($allAnalyzerLines.Count - 1)]
    $finalManifestText = $finalManifestLines | Out-String
    $finalManifestPath = Join-Path $OutputDirectory 'final-release-manifest.xml'
    $finalManifestText | Set-Content -LiteralPath $finalManifestPath -Encoding UTF8

    $mergedManifestXml = Read-ManifestXml -XmlText (Get-Content -LiteralPath $mergedManifest.FullName -Raw) -Description 'Merged release manifest'
    $finalManifestXml = Read-ManifestXml -XmlText $finalManifestText -Description 'Final APK manifest'
    $mergedResult = Test-ManifestContract -ManifestXml $mergedManifestXml -Description 'Merged release manifest' -ExpectedPackage $ExpectedPackageName -ExpectedTargetSdk $RequiredTargetSdk
    $finalResult = Test-ManifestContract -ManifestXml $finalManifestXml -Description 'Final APK manifest' -ExpectedPackage $ExpectedPackageName -ExpectedTargetSdk $RequiredTargetSdk

    $trackedInputsVerified = Test-TrackedRepositoryInputs -RepositoryRoot $repositoryRoot
    if ((Test-FileForApiKeyShape -Path $releaseApk.FullName) -or (Test-FileForApiKeyShape -Path $releaseBundle.FullName)) {
        Stop-Release $ExitCodes.EmbeddedSecretDetected 'A Google API-key-shaped value is embedded in a release artifact.'
    }

    $baselineProfilesVerified = $false
    if ($RequireBaselineProfiles) {
        $baselineProfilesVerified = Test-BaselineProfiles -ModuleDirectory $moduleDirectory -ApkPath $releaseApk.FullName
        if (-not $baselineProfilesVerified) {
            Stop-Release $ExitCodes.BaselineProfileMissing 'Required source and packaged Baseline/Startup Profile evidence is incomplete.'
        }
    }

    $signatureVerified = $false
    if ($RequireSigned) {
        $apkSigner = Get-SdkTool -SdkRoot $sdkRoot -ToolName 'apksigner'
        if ($null -eq $apkSigner) {
            Stop-Release $ExitCodes.SignatureInvalid 'apksigner is required when -RequireSigned is set.'
        }
        $signatureResult = Invoke-NativeCapture -FilePath $apkSigner -Arguments @('verify', '--verbose', $releaseApk.FullName)
        if ($signatureResult.ExitCode -ne 0) {
            Stop-Release $ExitCodes.SignatureInvalid 'apksigner rejected the release APK.'
        }
        $signatureVerified = $true
    }

    $result = [ordered]@{
        verifiedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
        variant = $Variant
        applicationId = $finalResult.packageName
        targetSdk = $finalResult.targetSdk
        debuggable = $finalResult.debuggable
        appOwnedActivityCount = $finalResult.appOwnedActivityCount
        mergedManifest = $mergedManifest.FullName
        finalManifest = $finalManifestPath
        apk = $releaseApk.FullName
        apkSha256 = (Get-FileHash -LiteralPath $releaseApk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        bundle = $releaseBundle.FullName
        bundleSha256 = (Get-FileHash -LiteralPath $releaseBundle.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        releaseRuntimeDependenciesVerified = $true
        trackedSigningInputsVerified = $trackedInputsVerified
        baselineProfilesRequired = [bool]$RequireBaselineProfiles
        baselineProfilesVerified = $baselineProfilesVerified
        signatureRequired = [bool]$RequireSigned
        signatureVerified = $signatureVerified
    }
    $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'release-verification.json') -Encoding UTF8

    Write-Host "[release-verify] PASS applicationId=$($finalResult.packageName) targetSdk=$($finalResult.targetSdk) debuggable=$($finalResult.debuggable)"
    Write-Output "RELEASE_APK=$($releaseApk.FullName)"
    Write-Output "RELEASE_BUNDLE=$($releaseBundle.FullName)"
    Write-Output "RELEASE_VERIFICATION=$(Join-Path $OutputDirectory 'release-verification.json')"
    exit $ExitCodes.Success
}
catch {
    Stop-Release $ExitCodes.UnexpectedFailure $_.Exception.Message
}
