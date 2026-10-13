# Release A — Recovery Test Plan (non-destructive)

NO fault injection on the live Bloom server. Use isolated copies of plugin data and a dedicated local/test Paper environment for all interruption tests.

1. Placement journal: after PREPARING, COMMITTING, COMPLETE, and abort, simulate shutdown on a test server; verify item neither disappears nor duplicates and written terrain matches committed record.
2. Reclaim ledger: simulate failure before and after RECLAIMED and refund enqueue; verify exactly-once recovery or an explicit safe unresolved status.
3. Realm Core Store: invalid/missing anchor or world UUID must deny destructive actions; interrupted Core creation and refund should not create a second Core or item.
4. Cosmetic store: draft-only preview must not grant an unlock; server restart must preserve last committed appearance.
5. WorldForge dependency: missing or mismatched Realm must fail closed, not guess home position.
6. Multiplayer boundaries: verify viewer-only BlockDisplay and normal player ownership/allowlist/public release paths with two actual accounts before enabling public-release.enabled.

Evidence to collect: exact JAR version, Paper version, expected and observed state, sanitized logs, snapshot checksums, replay outcome. Log failures and request separate authorization for any production recovery algorithm fix. The Release A CI checks are deterministic isolated entry points; they do NOT claim to cover the fault-injection matrix above.
