# Mint-enabled runServer Design

## Goal

Allow the local Paper `runServer` task to load the Mint server plugin when an explicit GitHub release asset is supplied, without guessing private repository metadata or changing the existing squaremap setup.

## Problem

The Paper module already uses a pinned Mint API dependency, but `paper:runServer` currently downloads only Paper and squaremap. Mint runtime services therefore cannot bind when the plugin is configured for Mint mode unless an operator manually installs the Mint server plugin.

## Decision

Add optional Gradle properties for the Mint plugin GitHub release coordinates:

- `mintPluginOwner`
- `mintPluginRepository`
- `mintPluginTag`
- `mintPluginAsset`

`tasks.runServer.downloadPlugins` will retain the existing pinned squaremap download and add the Mint GitHub download only when all four properties are present. No repository, tag, or asset defaults will be invented because the Mint repository is not publicly discoverable from this checkout.

The command will be documented as:

```bash
./gradlew :paper:runServer \\
  -PmintPluginOwner=... \\
  -PmintPluginRepository=... \\
  -PmintPluginTag=... \\
  -PmintPluginAsset=...
```

If the Mint coordinates are incomplete, the task will fail with a clear configuration error rather than downloading an incorrect file. Existing non-Mint runServer behavior remains unchanged.

## Files and behavior

- `paper/build.gradle.kts`: read the four optional properties, validate them as a complete group, retain squaremap download, and conditionally add Mint download.
- `README.md`: explain that the Mint API dependency is not the runtime plugin and document the explicit GitHub asset arguments.
- Focused test source: verify the build configuration is property-driven, does not contain fabricated Mint coordinates, and preserves squaremap configuration.

## Failure handling

- All four Mint properties absent: do not download Mint; preserve the current server path.
- Some but not all Mint properties present: fail during Gradle configuration with a message naming the required properties.
- GitHub download failure: propagate the run-paper download failure; do not silently continue without Mint.

## Verification

Run the focused Mint wiring/configuration test. Run the Gradle task configuration path with no Mint properties and with an intentionally incomplete property set. If valid Mint coordinates become available, run the local server and confirm the Mint plugin is present under `paper/run/plugins/`; otherwise verify the existing non-Mint server path only.
