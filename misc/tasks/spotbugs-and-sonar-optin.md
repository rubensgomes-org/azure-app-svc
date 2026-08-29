# Wire up SpotBugs and make SonarCloud opt-in

Finish the SpotBugs wiring that the `gradle-catalog` 0.2.9 -> 0.2.10 bump enabled,
and flip the shared `gradle-build-verify.yml` Sonar gate from on-by-default to
opt-in.

SpotBugs stays attached to the `check` lifecycle task — the plugin wires
`spotbugsMain`/`spotbugsTest` into `check` itself, so no CI step and no
`run-spotbugs` input are added. The reusable workflow documents that instead.

## Blocking prerequisite

- [x] Regenerate `app/buildscript-gradle.lockfile` (STRICT lock mode; build fails until then)

## Part A - azure-app-svc

- [x] `app/build.gradle.kts`: move `alias(libs.plugins.spotbugs)` into alphabetical order and write its comment
- [x] `app/build.gradle.kts`: add the `com.github.spotbugs` plugin section (main only, plugin defaults, HTML + XML reports)
- [x] `BUILD.md`: document SpotBugs in the static-analysis and lockfile sections
- [x] `llms.txt`: add SpotBugs

## Part B - azure-workflows

- [x] `gradle-build-verify.yml`: `run-sonar` default `true` -> `false`
- [x] `gradle-build-verify.yml`: update header comment (Sonar opt-in, SpotBugs runs inside `check`)
- [x] `gradle-build-verify.yml`: reword `sonar-token` description and the in-step error message
- [x] `.github/actions/gradle-build/action.yml`: fix the stale "adds only spotlessCheck and jacocoTestCoverageVerification" comment
- [x] `README.md`: show `run-sonar: true` in the caller stub

## Verification

- [x] `./gradlew :app:spotbugsMain` runs and its findings are read
- [x] `./gradlew :app:check taskTree --no-repeat` shows `spotbugsMain` and `spotbugsTest` under `check`; `spotbugsTest` is SKIPPED at execution
- [x] `app/build/reports/spotbugs/main.{html,xml}` both exist
- [x] `./gradlew :app:build` still produces the `-spring-boot.jar` and root `.env`
- [x] actionlint passes on the azure-workflows edits

## Notes

Accepted blast radius: all ten consumer repos pin `gradle-build-verify.yml@v1` with
no `with:` block, so SonarCloud stops running everywhere once the `v1` tag moves.
No consumer `build-verify.yml` is edited.

## Review

**Outcome:** done and verified end to end. `./gradlew :app:build` is green;
`spotbugsMain` runs inside `check`, `spotbugsTest` is SKIPPED, and the eight
main classes produce **zero SpotBugs findings**.

**Lockfile.** `./gradlew :app:dependencies --write-locks` added exactly two
entries to `app/buildscript-gradle.lockfile`
(`spotbugs-gradle-plugin:6.5.11`, `com.github.spotbugs.gradle.plugin:6.5.11`).
`app/gradle.lockfile` was untouched, as predicted: the plugin's `spotbugs` tool
configuration is not in the locked set.

**Deviation from the plan.** The plan showed an empty `spotbugs { }` extension
block holding only a comment. It was dropped -- an empty block that configures
nothing is noise, and the explanation belongs in the section header comment,
which is where it now lives. Everything else the block was meant to express
(`main` only, plugin defaults, HTML + XML) is expressed by the two task
configurations.

**Finding worth keeping.** The first run reported `total_classes="0"` in
`main.xml`, which reads exactly like "SpotBugs silently analyzed nothing."
It is not that. Tracked down by running the SpotBugs CLI directly with the
Gradle-generated class list and auxclasspath:

- Java 25 bytecode (major version 69) is analyzed correctly by SpotBugs 4.10.4
  -- the initial suspicion that the toolchain was too new was wrong.
- `-analyzeFromFile` and `-auxclasspathFromFile` both behave correctly.
- The cause is requesting **two report formats at once**. Run statistics go to
  the first reporter only, so the XML summary reports zero classes while bug
  instances are still written to both reports. Confirmed with a class carrying
  planted defects (`ES_COMPARING_PARAMETER_STRING_WITH_EQ`, `NP_ALWAYS_NULL`):
  both bugs appear in the XML even though `total_classes` stays `0`.
- Independent confirmation the codebase is genuinely clean: an XML-only run
  over the same eight classes with the same auxclasspath reported
  `total_classes=8`, `missingClasses=0`, zero bugs.

This is documented in `app/build.gradle.kts` and `BUILD.md` so the next reader
does not repeat the investigation.

**Workflow validation.** `rhysd/actionlint:1.7.7` -- the same image
`lint.yml` uses -- was run against `azure-workflows`: 6 workflow files linted,
0 errors. Confirmed the linter was actually exercising the changed file rather
than passing vacuously, by planting `default: not-a-boolean` on `run-sonar` and
watching it fail with `its default value must be true or false`; the file was
then restored. actionlint scans `.github/workflows/` only, so
`.github/actions/gradle-build/action.yml` was validated separately by YAML
parse (its four steps -- compile, test, check, assemble -- are intact).
Post-change structure confirmed: `run-sonar` default `False`, type `boolean`;
no `run-spotbugs` input; job steps still checkout / setup / Build / sonar with
no SpotBugs step.

**Left for a human.** The `v1` tag has not been moved and nothing was
committed or pushed. SonarCloud goes dark in all ten consumer repos when it is
moved -- that was the accepted decision, and re-enabling is a per-repo `with:
run-sonar: true` whenever wanted.
