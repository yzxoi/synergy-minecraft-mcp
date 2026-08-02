[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $CoreJar,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $ApiJar,

    # Optional instance mods directory. This is read-only and is intended to
    # catch stale duplicate runtime jars before starting Minecraft.
    [string] $InstanceMods,

    [switch] $AllowDuplicateRuntimeJars
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-ExistingFile([string] $Path, [string] $Label) {
    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        throw "$Label is not a file: $Path"
    }
    return $resolved.Path
}

function Get-Sha256([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

$corePath = Resolve-ExistingFile $CoreJar 'Core jar'
$apiPath = Resolve-ExistingFile $ApiJar 'API jar'

foreach ($candidate in @($corePath, $apiPath)) {
    $leaf = Split-Path -Leaf $candidate
    if ($leaf -match '(?i)(-sources|-javadoc|-api)(\.jar)?$') {
        throw "Deployment input must be a runtime jar, not '$leaf'."
    }
}

$coreSha = Get-Sha256 $corePath
$apiSha = Get-Sha256 $apiPath
Write-Host "core jar : $corePath"
Write-Host "core sha : $coreSha"
Write-Host "api jar  : $apiPath"
Write-Host "api sha  : $apiSha"

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($corePath)
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("numen-api-parity-" + [guid]::NewGuid().ToString('N'))
try {
    # Fabric uses META-INF/jars while NeoForge's jarJar task uses
    # META-INF/jarjar. Both entries must resolve to the same published API.
    $nested = @($zip.Entries | Where-Object {
        $_.FullName -like 'META-INF/jars/numen-api*.jar' -or
        $_.FullName -like 'META-INF/jarjar/numen-api*.jar'
    })
    if ($nested.Count -ne 1) {
        throw "Expected exactly one embedded API jar under META-INF/jars; found $($nested.Count)."
    }
    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    $nestedPath = Join-Path $tempRoot (Split-Path -Leaf $nested[0].FullName)
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($nested[0], $nestedPath)
    $nestedSha = Get-Sha256 $nestedPath
    Write-Host "embedded api: $($nested[0].FullName)"
    Write-Host "embedded sha: $nestedSha"
    if ($nestedSha -ne $apiSha) {
        throw "API/core SHA mismatch. Publish the API first, rebuild the core with refreshed dependencies, and redeploy both jars together."
    }
}
finally {
    $zip.Dispose()
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}

if ($InstanceMods) {
    $modsPath = Resolve-Path -LiteralPath $InstanceMods -ErrorAction Stop
    if (-not (Test-Path -LiteralPath $modsPath -PathType Container)) {
        throw "InstanceMods is not a directory: $InstanceMods"
    }
    $runtimeJars = @(
        Get-ChildItem -LiteralPath $modsPath -File -Filter '*.jar' |
            Where-Object { $_.Name -match '(?i)numen' -and $_.Name -notmatch '(?i)(sources|javadoc)' }
    )
    Write-Host "instance runtime jars: $($runtimeJars.Count)"
    foreach ($jar in $runtimeJars) {
        Write-Host ("  {0}  {1}" -f $jar.Name, (Get-Sha256 $jar.FullName))
    }
    if (-not $AllowDuplicateRuntimeJars -and $runtimeJars.Count -ne 1) {
        throw "Expected exactly one Numen runtime loader jar in InstanceMods; found $($runtimeJars.Count). Remove stale/duplicate jars before testing."
    }
}

Write-Host 'Numen deployment parity check passed.'
