# 2.21.3 Hybrid Realm Boundary — Phase 1

Development baseline: WorldForgeItemGen 2.21.3 core-test source.

## Staff commands
- `/realmshards boundary profiles` — show the live Worldforge island template identity and check four read-only schematic file paths.
- `/realmshards boundary preview [8-32]` — briefly mark possible exposed terrain edges with particles in loaded chunks near the staff member.
- `/realmshards boundary status` — report that only calibration is implemented.

## Important limits
- This is a calibration preview, NOT an accurate, persisted exterior-footprint implementation.
- Scanning is centered on the staff member's position, and gaps/caves can create false positive edges.
- No server terrain, Core, island ownership records, schematic files, or external plugin data are modified.
- Hybrid fractured terrain, tracked decorations, exact schematic transform matching, and updates on shard commits/removals are NOT implemented.
- Source compilation and live server testing remain required before installing a replacement JAR.
