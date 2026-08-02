# NeoForge game-test fixtures

NeoForge 21.11 uses registry-backed `GameTestInstance` data. The suite currently
contains 47 test methods: 45 are registered by the new bridge, while two remain
disabled because their captured-world fixtures are absent from the repository:

- `mine_spruce_forest` needs `real_spruce_forest.snbt`.
- `mine_diamond_pocket` needs `real_diamond_pocket.snbt`.

`runGameTestServer` defaults to two bounded, known-good tests. Select another
comma-separated set with `-PnumenGameTests=test_one,test_two`, or register the
whole migrated suite with `-PnumenGameTests=all`. An unknown or pending test id
fails registration so the vanilla `always_pass` test cannot produce a false-green run.

The full suite is not yet green: `goto_through_closed_door` registers and runs,
but currently exposes a path execution failure at the closed door. Keep it as a
regression test; do not weaken its assertion as part of the registry migration.
