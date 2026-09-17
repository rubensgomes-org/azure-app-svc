# Changelog

All notable changes to this project are documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/).

## How this file is used

`.github/workflows/release.yml` extracts the section matching the version being
released and uses it verbatim as the GitHub Release notes. An empty or missing
section fails the run, so the section must exist **before** the release is
cut.

The `net.researchgate.release` plugin commits the release version, tags *that*
commit, then bumps to the next snapshot. So the order is:

1. Rename `[Unreleased]` to the version being released, and add a fresh empty
   `[Unreleased]` above it.
2. Write what changed under it.
3. Commit and push that change to `main`.
4. Run the `release` workflow.

The workflow's `plan` job enforces this before anything is written, so getting
it wrong costs a failed run and nothing else. It rejects both a missing section
and one that still holds only the empty `### Added` / `### Changed` /
`### Fixed` skeleton.

## [Unreleased]

### Added

### Changed

- `acr-repo-delete.yml` now passes `artifact-id: azure-app-svc` to the
  reusable `acr-repo-delete` workflow, which requires it as an input instead
  of reading `app/gradle.properties`.

### Fixed

## [0.0.3]

### Added

- `scripts/initvars.sh`: resets this repository's GitHub Actions variables
  and secrets from the current shell environment.
- `docs/INITIAL_SETUP.md`: documented the previously-missing `SONAR_TOKEN`
  secret and `TF_VAR_OWNER` variable.

### Changed

- Released `v0.0.2`.

### Fixed

- `app:spotlessJavaCheck` failed on the AI-disclaimer Javadoc: `googleJavaFormat()`
  reflows Javadoc to its own ~100-column width, fighting the project's
  80-column wrapping. Added `.skipJavadocFormatting()` to the Spotless
  `googleJavaFormat()` step in `app/build.gradle.kts`.

## [0.0.2]

### Added

### Changed

### Fixed

- `plan-create` workflow: "Verify the plan converged" queried `reserved` at
  the top level of `az appservice plan show`, which this CLI version never
  populates there; the Linux flag only appears under `properties.reserved`.
  The query now reads `properties.reserved`, and the plan already converges
  on the first read.
- `app-svc-create-deploy` workflow: "Verify the App Service converged" had
  the same bug for `state`, `httpsOnly`, `defaultHostName`,
  `linuxFxVersion`, `healthCheckPath`, and `acrUseManagedIdentityCreds`, and
  queried a nonexistent `appServicePlanId` field instead of
  `properties.serverFarmId`. All six now read from `properties`.

## [0.0.1]

### Added

- `plan-create` workflow: creates the Linux App Service Plan
  (`plan-<workload>-<environment>`) if absent, with `sku` and `location`
  inputs.
- `plan-delete` workflow: deletes the App Service Plan, guarded by the actor
  allowlist and `confirm` checkbox, matching `app-svc-delete`.
- `docs/PRICING.md`: idle-provisioning cost reference for the project's
  Azure resources.

### Changed

- `app-svc-create-deploy` workflow: `plan_name` default changed to
  `plan-rgomes-dev`, matching the naming convention introduced by
  `plan-create`.

### Fixed

- `plan-create` workflow: "Verify the plan converged" now polls
  `az appservice plan show` for up to two minutes instead of reading once.
  `reserved` (the Linux flag) was observed to still read back `null` well
  after `create` reported success, while `sku` and `location` were already
  correct, failing the run against a plan that had, in fact, converged.

## [0.0.0]

### Added

### Changed

- `app-svc-delete` workflow: `confirm` input is now a checkbox (boolean)
  instead of a typed confirmation phrase, matching `acr-repo-delete`.

### Fixed
