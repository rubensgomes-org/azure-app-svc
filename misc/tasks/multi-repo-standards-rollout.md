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

- [ ] Confirm the tree is still clean and on `main`; abort that repo if not.
- [ ] Branch `docs-standards-conformance`, matching this project.
- [ ] Copy this project's `CLAUDE.md` over the existing one.
- [ ] Javadoc removals: the two `private static` methods in
      `GlobalErrorController`, the `HelloWorldRestController` constructor.
- [ ] Delete the `// NOTE:` block duplicating the Javadoc above it.
- [ ] Pattern documentation: observer rationale on both event listeners,
      value-object contract on `ErrorResponse`, centralised-error-handling and
      null-object patterns on `GlobalErrorController`.
- [ ] Wildcard import in `AppTest`, its missing class Javadoc, and the
      redundant `@Autowired` on the sole constructor.
- [ ] `./gradlew spotlessApply build` -- must stay green, tests must not move,
      and the JaCoCo 90% gate must still pass. Abort that repo on failure.
- [ ] Commit in two parts, mirroring this project's history: the `CLAUDE.md`
      revision, then the Javadoc alignment. Push the branch.

`APP_SERVICE.md` is NOT copied anywhere. It documents this project's Azure
resource and belongs to it, the same way `ACR.md` belongs to `azure-acr`.

## Verify

- [ ] All nine builds green, `35 tests, 0 failures` in each.
- [ ] Nine branches pushed; report the PR URL for each.

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

(to be filled in when the work is done)
