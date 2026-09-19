# CLAUDE.md

## Code Quality Standards

### Before Every Commit

1. Always run `./gradlew spotlessCheck` before creating any commit
2. If spotless fails, run `./gradlew spotlessApply` to auto-fix, then verify with spotlessCheck again
