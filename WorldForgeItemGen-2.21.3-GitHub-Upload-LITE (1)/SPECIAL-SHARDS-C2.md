# Release C2 — Offline package inspection and immutable staging

C2 is developer-only library code. It is not registered with WorldForgeItemGen.onEnable, RealmShards, Bukkit commands or the resource-pack pipeline. No player items, worlds, data, or assets change during plugin startup.

## Files
New: SpecialShardPackageManager, SpecialShardPackageValidator, SpecialShardArchiveReader, SpecialShardSchematicInspector, SpecialShardAssetInspector, SpecialShardStagingStore, SpecialShardPackageVerification.
Modified: SpecialShardManifest, SpecialShardValidationReport, SPECIAL-SHARDS-CODEX-GUIDE.md and .github/workflows/build-boundary.yml.
New documentation: this file.

## Security and limits
Only explicitly called offline operations inspect or stage. They verify disabled distribution, schema, safe relative paths, basic file signatures, declared file existence, size caps, duplicate archive entries and traversal. Validated files move into unique revision directories with SHA-256 records. Previous revisions never get overwritten. Failures clean temporary staging but do not affect existing revisions.

## Critical limitations
Schematic inspection checks gzip signature and file bounds only, NOT NBT correctness, dimensions, block palette, or FAWE compatibility. Model JSON/animation correctness and real Asset Vault references are NOT verified. ZIP UNIX symlink metadata is not inspected, although extraction creates regular files and existing folder symlinks are forbidden. This library is a foundation for later deeper validation, not a security-certified archive service. Never import untrusted packages directly on the live server.

## Tests and acceptance
The C2 CI job runs existing placement, wand, Core, C1 registry checks plus isolated C2 staging and unsafe archive tests. It does not test fault-injected failures or gameplay. Version remains 2.21.4-rc1 because plugin.yml and pom.xml are outside C2 approval. Return to C1 JAR to roll back; no gameplay data migration is necessary.
