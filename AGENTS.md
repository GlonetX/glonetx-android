# Agent Instructions

## Mandatory rules for every code change

For every code change in this repository, the agent must complete all of the following before finishing:

1. **Update the version**
   - Update `versionName` and `versionCode` in `build.gradle.kts`.
   - Use `major.minor.patch` for `versionName`.
   - Compute `versionCode` with the release workflow formula: `major` + two-digit `minor` + three-digit `patch` + two-digit iteration (default `01`). Example: `12.13.0` -> `121300001`.
   - Update any version references in release notes or comments that would otherwise become stale.

2. **Update the changelog**
   - Add a concise entry to `CHANGELOG.md` under `Unreleased`, or under the version heading when preparing a release.
   - Describe user-visible changes and important fixes. Do not leave the changelog unchanged after a code change.

3. **Validate the change**
   - Run the relevant unit tests and lint checks.
   - Use Java 21 for Gradle commands, for example:
     ```sh
     JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testAlphaDebugUnitTest :app:testMetaDebugUnitTest
     ```

4. **Commit to git**
   - Stage and commit all intended changes with a Conventional Commit message.
   - Keep generated files, local secrets, and unrelated user work out of the commit.
   - Never add `Co-Authored-By` lines or other agent signatures to commit messages.

5. **Push when requested**
   - Push the current branch to `origin` after the commit is verified.
   - Confirm that local and remote branch heads match.
