[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [string]$RepositoryRoot,
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = Split-Path -Parent $PSScriptRoot
}
$RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
$coreRoot = Join-Path $RepositoryRoot 'minecraft-numen'
if (-not (Test-Path -LiteralPath $coreRoot -PathType Container)) {
    throw "Core project directory was not found: $coreRoot"
}

# Keep this target deliberately narrow. Do not remove the whole project .gradle
# directory or the user's global Gradle cache: Loom stores unrelated Minecraft,
# mappings, and loader data there as well.
$target = Join-Path $coreRoot '.gradle/loom-cache/remapped_mods/remapped/com/dwinovo/numen'
if (-not (Test-Path -LiteralPath $target)) {
    Write-Host "No Numen Loom remapped cache exists at $target"
    return
}

$resolvedRoot = [IO.Path]::GetFullPath($coreRoot)
$resolvedTarget = [IO.Path]::GetFullPath($target)
$allowedPrefix = $resolvedRoot.TrimEnd('\') + '\.gradle\loom-cache\remapped_mods\remapped\com\dwinovo\numen'
if (-not $resolvedTarget.Equals($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to remove an unexpected Loom cache path: $resolvedTarget"
}

if (-not $Apply) {
    Write-Host "Dry run: would remove only $resolvedTarget"
    Write-Host 'Pass -Apply (and optionally -Confirm) to perform the cleanup.'
    return
}

if ($PSCmdlet.ShouldProcess($resolvedTarget, 'Remove stale Numen Loom remapped cache')) {
    Remove-Item -LiteralPath $resolvedTarget -Recurse -Force
    Write-Host "Removed $resolvedTarget"
}
