[CmdletBinding()]
param(
    [string]$RepositoryRoot,
    [string]$MavenRepository,
    [switch]$ValidateOnly,
    [switch]$KeepStaging
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = Split-Path -Parent $PSScriptRoot
}
$RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
$apiRoot = Join-Path $RepositoryRoot 'numen-api'
$coreRoot = Join-Path $RepositoryRoot 'minecraft-numen'
foreach ($required in @($apiRoot, $coreRoot)) {
    if (-not (Test-Path -LiteralPath $required -PathType Container)) {
        throw "Repository is missing required directory: $required"
    }
}

function Get-GradleProperty([string]$file, [string]$name) {
    $line = Get-Content -LiteralPath $file | Where-Object {
        $_ -match "^\s*$([regex]::Escape($name))\s*="
    } | Select-Object -First 1
    if ($null -eq $line) { throw "Property '$name' was not found in $file" }
    return (($line -split '=', 2)[1]).Trim()
}

function Invoke-Gradle([string]$project, [string[]]$arguments) {
    $wrapper = Join-Path $project 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
        throw "Gradle wrapper not found: $wrapper"
    }
    Write-Host ("[{0}] gradlew {1}" -f (Split-Path -Leaf $project), ($arguments -join ' '))
    Push-Location $project
    try {
        & $wrapper @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed in $project (exit code $LASTEXITCODE)"
        }
    } finally {
        Pop-Location
    }
}

function Get-SnapshotJar([string]$repository, [string]$artifactId, [string]$version) {
    $moduleDir = Join-Path $repository ('com/dwinovo/numen/' + $artifactId)
    $snapshotDir = Join-Path $moduleDir $version
    $metadataPath = Join-Path $snapshotDir 'maven-metadata.xml'
    if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
        throw "Published Maven metadata is missing: $metadataPath"
    }
    [xml]$metadata = Get-Content -LiteralPath $metadataPath
    $snapshot = @($metadata.metadata.versioning.snapshotVersions.snapshotVersion |
        Where-Object {
            $_.extension -eq 'jar' -and
            (-not $_.PSObject.Properties['classifier'] -or [string]::IsNullOrEmpty($_.classifier))
        }) |
        Select-Object -Last 1
    if ($null -eq $snapshot -or [string]::IsNullOrWhiteSpace($snapshot.value)) {
        throw "No main snapshot jar is listed in $metadataPath"
    }
    $jar = Join-Path $snapshotDir ("{0}-{1}.jar" -f $artifactId, $snapshot.value)
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "Metadata points to a missing snapshot jar: $jar"
    }
    return [pscustomobject]@{ Path = $jar; Value = $snapshot.value }
}

function Get-ProductionJar([string]$directory, [string]$version) {
    $matches = @(Get-ChildItem -LiteralPath $directory -Filter "*-$version.jar" -File |
        Where-Object { $_.Name -notmatch '-(api|sources|javadoc)\.jar$' })
    if ($matches.Count -ne 1) {
        throw "Expected exactly one production jar ending '-$version.jar' in $directory; found $($matches.Count)"
    }
    return $matches[0].FullName
}

function Assert-HashEqual([string]$left, [string]$right, [string]$label) {
    $leftHash = (Get-FileHash -LiteralPath $left -Algorithm SHA256).Hash
    $rightHash = (Get-FileHash -LiteralPath $right -Algorithm SHA256).Hash
    if ($leftHash -ne $rightHash) {
        throw "$label SHA-256 mismatch:`n  $left`n  $leftHash`n  $right`n  $rightHash"
    }
    Write-Host ("PASS {0}: {1}" -f $label, $leftHash)
}

$apiVersion = Get-GradleProperty (Join-Path $apiRoot 'gradle.properties') 'version'
$minecraftVersion = Get-GradleProperty (Join-Path $apiRoot 'gradle.properties') 'minecraft_version'
$coreVersion = Get-GradleProperty (Join-Path $coreRoot 'gradle.properties') 'version'
$ownsStaging = -not $ValidateOnly
if ($ValidateOnly) {
    if ([string]::IsNullOrWhiteSpace($MavenRepository)) {
        throw '-MavenRepository is required with -ValidateOnly'
    }
    $staging = [IO.Path]::GetFullPath($MavenRepository)
    if (-not (Test-Path -LiteralPath $staging -PathType Container)) {
        throw "Maven repository was not found: $staging"
    }
} else {
    $staging = Join-Path ([IO.Path]::GetTempPath()) ("numen-maven-{0}" -f ([guid]::NewGuid().ToString('N')))
    New-Item -ItemType Directory -Path $staging | Out-Null
    $staging = (Resolve-Path -LiteralPath $staging).Path
}
$stagingUri = ([Uri]::new($staging)).AbsoluteUri

try {
    if (-not $ValidateOnly) {
        Invoke-Gradle $apiRoot @('clean', 'test', 'publish', '--no-daemon', "-Plocal_maven_url=$stagingUri")
        Invoke-Gradle $coreRoot @('clean', 'build', '--no-daemon', '--refresh-dependencies', "-Plocal_maven_url=$stagingUri")
    }

    $published = @{}
    $apiBuilds = @{}
    foreach ($loader in @('common', 'fabric', 'neoforge')) {
        $artifactId = "numen-api-$loader-$minecraftVersion"
        $published[$loader] = Get-SnapshotJar $staging $artifactId $apiVersion
        Write-Host ("PASS published {0}: {1}" -f $artifactId, $published[$loader].Value)
        $apiBuilds[$loader] = Get-ProductionJar (Join-Path $apiRoot "$loader/build/libs") $apiVersion
        Assert-HashEqual $apiBuilds[$loader] $published[$loader].Path "API $loader build/libs ↔ published API $loader jar"
    }

    # The loader jars are the runtime API artifacts.  They must all come from
    # one publication, rather than a common-only or stale mixed publication.
    $values = @($published.Values | ForEach-Object { $_.Value })
    if (($values | Select-Object -Unique).Count -ne 1) {
        throw "API loader artifacts do not share one snapshot publication: $($values -join ', ')"
    }

    $coreFabric = Get-ProductionJar (Join-Path $coreRoot 'fabric/build/libs') $coreVersion
    $extract = Join-Path $staging 'core-inspect'
    New-Item -ItemType Directory -Path $extract | Out-Null
    $nested = @(& jar tf $coreFabric | Where-Object { $_ -like 'META-INF/jars/*.jar' })
    if ($nested.Count -ne 1) {
        throw "Expected exactly one embedded API jar in $coreFabric; found $($nested.Count)"
    }
    $nestedName = $nested[0].Trim()
    Push-Location $extract
    try { & jar xf $coreFabric $nestedName } finally { Pop-Location }
    $embedded = Join-Path $extract $nestedName.Replace('/', [IO.Path]::DirectorySeparatorChar)
    Assert-HashEqual $embedded $published['fabric'].Path 'core Fabric jar ↔ published API Fabric jar'

    Write-Host 'Reproducible build and artifact parity checks passed.'
} finally {
    if ($KeepStaging) {
        Write-Host "Keeping Maven staging repository: $staging"
    } elseif ($ownsStaging -and (Test-Path -LiteralPath $staging -PathType Container)) {
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        $resolved = [IO.Path]::GetFullPath($staging)
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            (Split-Path -Leaf $resolved) -notmatch '^numen-maven-[0-9a-f]{32}$') {
            throw "Refusing to remove an unexpected staging path: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
