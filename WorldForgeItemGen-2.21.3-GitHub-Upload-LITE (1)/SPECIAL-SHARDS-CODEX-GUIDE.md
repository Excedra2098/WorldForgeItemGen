# Codex authoring contract — Special RealmShards (C1)
 
This is a DESIGN-TIME format only. The live plugin does not load special-shards.yml, manifests, or packages in C1.
 
## Recommended package layout
 
```
special-shards/<stable-id>/
  manifest.yml
  island.schem
  models/
  textures/
  animations/
  sounds/
  documentation.md
```
 
## manifest.yml template
 
```yaml
schema-version: 1
identity:
  id: winter_frostbound_2026
  name: Frostbound Fragment
  category: SEASONAL
  event-id: winter_2026
shard:
  base-land: SNOW
  tier: 3
  variant: STANDARD
island:
  schematic: island.schem
  dimensions:
    width: 16
    length: 16
    height: 24
  placement-anchor: CENTER
assets:
  item-model: realmshards:special/frostbound
  models: []
  textures: []
  animations: []
  sounds: []
future-features:
  discoveries: []
  reactions: []
  special-effects: []
distribution:
  enabled: false
  method: OWNER_ONLY
```
 
Sizes and extra fields are authoring hints only. In C1, dimensions/anchor/method are not validated against a schematic or server. Actual content import, terrain validation, asset publish, item identity and distribution are for separately approved later phases.
 
## Validation contract
 
IDs are lowercase and stable; model references use namespaced IDs; relative asset paths must not contain traversal, absolute paths, or drive prefixes. base-land must be an existing ShardSpec.Land, tier 1–5, variant STANDARD, SHINY, or RAINBOW (RAINBOW only tier III+). Category is an uppercase identifier, and nine initial names are defined in SpecialShardCategory. C1 requires distribution.enabled=false.
 
Codex should produce drafts and a list of dependencies and assumptions; it must not edit live plugin data, publish the resource pack, or enable content. Store new package files outside the plugin until a separate import workflow is approved.

## C2 offline package staging
A developer may call SpecialShardPackageManager.inspect(Path) or stage(input,stagingRoot) in an isolated Java test/harness using the test/runtime dependencies. There is **no public server command or startup importer** in C2. Codex should supply a folder or ZIP containing manifest.yml and island.schem; optional referenced assets are required to exist.
ZIP entry count is capped at 512; entry bytes at 32 MiB; total extracted bytes at 128 MiB; archive size at 64 MiB. No nested archives, symbolic links in staged folders, path traversal, or overwriting revisions. The staged revision has CHECKSUMS.sha256.
C2 schematic precheck only verifies .schem extension, file size, and gzip signature; it **does not** yet parse NBT palettes/dimensions or prove that the schematic is valid for FAWE. JSON/model animation contents and Asset Vault existence remain unverified. Treat these as warnings requiring subsequent approval and specialist validation, not as playable content.
