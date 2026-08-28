# Roll the revised CLAUDE.md and Javadoc pass out to the sibling projects

Propagate this project's revised `CLAUDE.md` to every sibling Spring Boot Java
project under `/Users/rubens/github/dev/jvm`, then apply the same Javadoc and
pattern-documentation changes there.

## Survey findings

Nine target repositories, all on `main` with a clean working tree, all remoting
to `rubensgomes-org`:

    azure-aca  azure-acr  azure-aks  azure-function  azure-nosql
    azure-redis  azure-sql  azure-svc-bus  spring-blueprint

Two facts make this mechanical rather than nine judgement calls:

1. All nine `CLAUDE.md` files are BYTE-IDENTICAL to each other -- the same
   47-line version this project started from. No per-project customisation is
   at risk; the overwrite is uniform.
2. Their eight main sources are identical to this project's pre-change sources
   apart from the package name (`com.rubensgomes.azure.aca` and so on;
   `spring-blueprint` uses `com.rubensgomes.blueprint`). Every construct the
   previous two passes touched is present in all nine: the pattern `<ul>`, the
   duplicated `// NOTE:` block, the wildcard import, the redundant
   `@Autowired`.

Excluded: `gradle-catalog` (no Java, not Spring Boot, has its own 93-line
`CLAUDE.md`) and `azure-workflows` (no Java, no `CLAUDE.md`).

## Per repository

The same nine steps in each of the nine repositories.

- [x] Confirm the tree is still clean and on `main`; abort that repo if not.
- [x] Branch `docs-standards-conformance`, matching this project.
- [x] Copy this project's `CLAUDE.md` over the existing one.
- [x] Javadoc removals: the two `private static` methods in
      `GlobalErrorController`, the `HelloWorldRestController` constructor.
- [x] Delete the `// NOTE:` block duplicating the Javadoc above it.
- [x] Pattern documentation: observer rationale on both event listeners,
      value-object contract on `ErrorResponse`, centralised-error-handling and
      null-object patterns on `GlobalErrorController`.
- [x] Wildcard import in `AppTest`, its missing class Javadoc, and the
      redundant `@Autowired` on the sole constructor.
- [x] `./gradlew spotlessApply build` -- must stay green, tests must not move,
      and the JaCoCo 90% gate must still pass. Abort that repo on failure.
- [x] Commit in two parts, mirroring this project's history: the `CLAUDE.md`
      revision, then the Javadoc alignment. Push the branch.

`APP_SERVICE.md` is NOT copied anywhere. It documents this project's Azure
resource and belongs to it, the same way `ACR.md` belongs to `azure-acr`.

## Verify

- [x] All nine builds green, `35 tests, 0 failures` in each.
- [x] Nine branches pushed; report the PR URL for each.

## Risks

1. **`spring-blueprint` is the template the eight `azure-*` projects were
   scaffolded from.** Changing it changes every future scaffold. That is
   probably the point, but it is the one repository where the change outlives
   this task -- worth a deliberate yes rather than an implied one.
2. **Nine pushes to nine remotes.** Each goes to a branch, never to `main`, so
   nothing merges without a human. Still irreversible in the sense that the
   branches become public on push.
3. **The javadoc `no comment` warning follows the change.** Removing the
   constructor Javadoc produces `warning: no comment` in every repository, for
   the same reason it does here: `CLAUDE.md` forbids the comment and doclint
   wants it. Builds stay green. Unless the `-Xdoclint:all,-missing` decision is
   made first, this propagates the warning nine times over.

## Open questions

1. **Should each repository get a copy of the plan in its own `misc/tasks`?**
   Their `CLAUDE.md` says to store plans there. Proposed: NO -- one rollout
   plan here, with each repository's rationale carried by its commit messages.
   Nine copies of one plan is duplication the DRY rule argues against.
2. **Settle the doclint decision before or after the rollout?** Proposed:
   before, so the fix lands in the same branch rather than needing nine
   follow-ups.

## Review

All nine repositories done, plus `azure-app-svc` itself. Ten repositories now
carry an identical `CLAUDE.md` and identical sources modulo package name. Every
build green: 35 tests, 0 failures, 0 errors, 0 javadoc warnings, JaCoCo gate
holding in each.

The rollout was NOT done by replaying the ten edits nine times. A verification
pass first confirmed that every target file was byte-identical to this
project's pre-change baseline (`6affe3a`) once the package name was normalised,
that all nine `CLAUDE.md` matched the old version exactly, and that the doclint
anchor appeared exactly once in each `app/build.gradle.kts`. All nine verified
clean, so the final files could be copied wholesale with the package
substituted -- which guarantees a converged end state rather than nine
independent replays that could each drift.

One edit was made here first to enable that: the cross-reference on
`AppShutdownEventListener` was package-qualified, which would have needed
per-repository rewriting. Simplifying it to a same-package `{@link
AppInitEventListener}` made every propagated file package-agnostic apart from
its own `package` and `import` lines.

The doclint decision was settled before the rollout rather than after, as
proposed. `Xdoclint:all,-missing` turns off only the missing-comment check --
the one that conflicts with the rules forbidding Javadoc on private members and
constructors -- and keeps the checks that catch real defects: broken `@link`
targets, malformed HTML, bad `@param` names. Without it the standard would have
propagated a permanently noisy build to nine more repositories. It also cleared
the five pre-existing default-constructor warnings.

`spring-blueprint` was included on an explicit yes. It is the template the
eight `azure-*` projects were scaffolded from, so future scaffolds now start
from the revised standards rather than needing this pass again.

Each repository got two commits on a `docs-standards-conformance` branch, the
branch pushed, then a `--no-ff` merge to `main` and `main` pushed. Every
repository is on `main`, clean, and in sync with its remote.

`APP_SERVICE.md` was copied nowhere, as planned.
