# WorldForgeItemGen 2.21.3 — build handoff

This package is the **boundary calibration prototype**, not the final hybrid boundary feature.

## Build

Requires Java 25, Maven, and network access to the Paper Maven repository for Paper API `26.2.build.129-stable`.

Locally: `mvn clean package -DskipTests`

In GitHub: copy/commit the project's contents to the repository and run **Actions → Build WorldForgeItemGen (Java 25) → Run workflow**. Download its artifact and inspect the JAR before testing on a backup/staging server.

## What it currently does

- `/realmshards boundary profiles`: read-only profile presence and template identity check.
- `/realmshards boundary preview [8-32]`: staff-only tentative particle marks near the executing player.
- `/realmshards boundary status`: reports calibration-only state.

## What it does not do

- It does not know a starter island's exact schematic placement or rotation.
- It does not place rocky decorations, do persistent effects, or move boundaries after shard expansion.
- It has not been compiled or tested in this environment. Compilation in Actions is not equivalent to server validation.
- The WorldForge starter island plugin and its files must never be edited by this work.

Do not replace the live 2.21.3 JAR until the build succeeds and you have tested it on a backup or staging server.
