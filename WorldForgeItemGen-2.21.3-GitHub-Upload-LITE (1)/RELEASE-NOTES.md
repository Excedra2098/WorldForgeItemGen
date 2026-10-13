# WorldForgeItemGen 2.21.4-rc1 — Release A

Scope: build verification, release metadata, and documentation only. No gameplay, data migrations, resources, commands, permissions, or Realm generation changes.

## Files changed
- .github/workflows/build-boundary.yml — compiles verification classes and gates uploaded artifact on PlacementVerification, RealmweaverVerification and plugin.yml version checks.
- pom.xml — Maven release version set to 2.21.4-rc1; Java 25 and Paper API 26.2.build.129-stable retained.
- src/main/resources/plugin.yml — plugin version 2.21.4-rc1; no command/permission changes.
- src/main/java/dev/excedra/itemgen/WorldForgeItemGen.java — startup log uses plugin metadata version rather than stale hardcoded text.
- RELEASE-NOTES.md and RECOVERY-TEST-PLAN.md — documentation only.

## Test limitations
PlacementVerification and RealmweaverVerification execute off-server. They do not validate live Realm Core inventory, player persistence, crash recovery, multiplayer entity visibility or Bloom startup. Remaining verification mains require additional fixture/configuration assessment; they are not silently promoted to CI gates.

## Installation / rollback
Back up plugins/WorldForgeItemGen/ and current plugin JAR while Bloom is stopped. Record the prior Git commit and JAR. Install only after CI success. Verify /version WorldForgeItemGen (2.21.4-rc1), player inventory, Realm Core, RealmWeaver, shard placement/reclaim and boundary on test Realm. On failure stop server, restore prior JAR and its backed-up plugin data if necessary, then restart. No data migration is implemented.
