# Release C1 — Inert Special Shard Registry
 
Approved scope: five new package-private production Java classes, one disabled sample YAML resource, one isolated test class, one CI workflow edit, and two documentation files. No existing gameplay Java, plugin.yml, data stores, server resource packs, or external WorldForge files changed.
 
## Architecture
 
SpecialShardCategory, SpecialShardDefinition, SpecialShardManifest, SpecialShardRegistry and SpecialShardValidationReport are design-time infrastructure. None is created or referenced from WorldForgeItemGen.onEnable, RealmShards, RealmweaverWand, or the player/server runtime. Configuration is a bundled resource, not written or loaded into the live plugin data directory. No special item IDs are minted.
 
## Limits
 
Registry validation checks schema version, distribution disabled, ShardSpec land/tier/variant, identity category, model namespace syntax, relative asset paths, and duplicate IDs for in-memory collections. It does not inspect external schematic files, verify model existence, import packages, generate terrain, enable events, or guarantee content compatibility.
 
## Testing
 
CI executes SpecialShardRegistryVerification using Maven exec Java test classpath, after the existing Release A/B regressions. No live gameplay changes or migrations are required. The current plugin version remains 2.21.4-rc1 and the artifact should be treated as C1 testing.
 
## Later phases (require approval)
 
C2 package import and safe content staging; C3 item identity, placement/refund integration; C4 owner controls, permissions and distribution. Existing Realm ownership, Core, Shard generation, wand, protections, resource packs and saved data are protected in C1.
 
## Rollback
 
Retain the last tested Release B JAR and original configuration backup. Switch back to the previous artifact if build or runtime issues appear. C1 creates no persistent player data and makes no schema migration.
