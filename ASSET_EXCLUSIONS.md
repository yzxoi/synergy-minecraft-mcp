# Restricted upstream assets excluded from this repository

The upstream `LICENSE-ASSETS` reserves all rights in original visual/audio assets and branding and prohibits copying them into derivative projects without permission. To keep this monorepo redistributable, the import removes the following paths from the entire imported Git history.

## numen-api

- `common/src/main/resources/assets/numen_api/textures/`
- `common/src/main/resources/assets/numen_api/persona/`
- `common/src/main/resources/numen_api.png`
- `docs/branding/`
- `tools/ui-textures/`

The persona directory is conservatively excluded because it contains upstream-authored creative character content rather than executable source code.

## minecraft-numen

- `common/src/main/resources/numen.png`
- `docs/branding/`
- `tools/ui-textures/`

## Consequences

- The source history and gameplay/MCP implementation remain available for audit and development.
- UI screens may show missing-resource placeholders until independently created, license-compatible replacement assets are supplied.
- Mod metadata may refer to an icon that is absent from this repository.
- Do not retrieve the excluded files from upstream and redistribute them inside a modified build unless the copyright holder gives prior written permission.

If additional upstream-original visual/audio assets are discovered, they must be removed before public distribution and added to this list.
