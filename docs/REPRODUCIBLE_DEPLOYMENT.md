# Reproducible API/core deployment

`numen-api` and `minecraft-numen` are separate Gradle builds. The core does not
compile against the API source tree directly: it resolves three Maven artifacts
(`common`, `fabric`, and `neoforge`) and embeds the loader artifact in the final
core jar. Publishing only `common`, or rebuilding core without republishing the
loader jars, can therefore produce a jar whose compile-time API and runtime API
come from different revisions.

## One-command build and parity check

From the repository root, with JDK 21 available:

```powershell
.\scripts\reproducible-build.ps1 -KeepStaging
```

The script creates a unique temporary Maven repository, then runs:

1. `numen-api\gradlew clean test publish` for all three loaders;
2. `minecraft-numen\gradlew clean build --refresh-dependencies` against that
   temporary repository;
3. checks that all three API artifacts have one snapshot publication value;
4. checks SHA-256 equality between the published Fabric API jar, the API
   `build/libs` Fabric jar, and the API jar embedded in the core Fabric jar.

The temporary repository is removed after a successful run. Use
`-KeepStaging` when inspecting the exact artifacts; validation-only runs can be
performed later with:

```powershell
.\scripts\reproducible-build.ps1 -ValidateOnly `
  -MavenRepository C:\path\to\numen-maven-staging
```

The script intentionally builds from a clean output and refreshes changing
dependencies. This is important for `0.0.8-SNAPSHOT`: Gradle's dependency cache
and Fabric Loom's remapped cache are independent, so `--refresh-dependencies`
alone is not a complete stale-cache remedy.

## Targeted Loom cache cleanup

If a previously resolved API snapshot remains in Loom's remapped-mod cache, run
the dry run first:

```powershell
.\scripts\clear-loom-api-cache.ps1
```

Only this project-local path is considered:

```text
minecraft-numen/.gradle/loom-cache/remapped_mods/remapped/com/dwinovo/numen
```

To remove it after reviewing the path, explicitly pass `-Apply` (and keep
`-Confirm` enabled if desired):

```powershell
.\scripts\clear-loom-api-cache.ps1 -Apply -Confirm
```

The cleanup refuses any path outside the exact Numen remapped subtree and does
not touch the global Gradle cache, other Loom caches, saves, or game instances.
After cleanup, rerun `reproducible-build.ps1` so Loom remaps the freshly
published loader artifact.

## Distribution rule

The distributable Fabric/NeoForge core jar already contains its matching API
loader jar. Copy only the corresponding production core jar to the instance's
`mods` directory; do not add a second standalone `numen-api` jar. Keep the
SHA-256 output from the parity check with the deployed jar for diagnosis.
