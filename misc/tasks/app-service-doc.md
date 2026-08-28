# Write APP_SERVICE.md

`APP_SERVICE.md` exists but is empty. Give this project the same kind of
standalone service document `azure-acr` has in `ACR.md`: what the service is,
why we use it, how this project's instance is named, and the `az` commands used
to view, troubleshoot and inspect it.

Documentation only. No workflow, `Dockerfile`, build or source change.

## Structure

Mirrors `ACR.md`: prose rationale first, settings second, commands last.

- [x] **Azure App Service** — what it is: managed PaaS for web apps and APIs,
      the platform runs the OS, patching, TLS and scaling; we ship a container
      image and nothing else.
- [x] **Why App Service** — why this over a VM or over AKS/ACA for a single
      Spring Boot service: no cluster to operate, HTTPS and a hostname on
      creation, built-in autoscale, deployment slots for swap-and-rollback,
      managed identity to pull from ACR with no registry password, and direct
      integration with the `rubensdevacr` images this repo already publishes.
- [x] **App Service settings** — how this project's instance is named and
      configured: Linux container plan, the `dev/azure-app-svc` ACR image,
      `WEBSITES_PORT=8080` matching the `EXPOSE`, the health check path
      `/actuator/health`, `SPRING_PROFILES_ACTIVE`, and the note that the
      resources are created by `azure-iac`, not by this repo.
- [x] **az Commands** — grouped the way `ACR.md` groups them, each command
      commented with why you would reach for it:
    - [x] View and list — `az webapp list`, `az webapp show`, the default
          hostname, `az appservice plan show`, `az webapp config show`
    - [x] Container and settings — `az webapp config container show`,
          `az webapp config appsettings list`, `az webapp identity show`
    - [x] Troubleshoot — `az webapp log tail`, `az webapp log download`,
          `az webapp log deployment show`, `az webapp ssh`, restart/stop/start,
          and reading state/availability back from Azure
    - [x] Deeper detail — metrics via `az monitor metrics list`, deployment
          slots, hostnames, and `az webapp list-runtimes` style discovery
- [x] Keep every command runnable as written against this project's names,
      with `az login` and subscription selection assumed once up front, exactly
      as `ACR.md` states it.

## Open question

`ACR.md` is not linked from `README.md` or `llms.txt` in `azure-acr`; it stands
alone. This plan follows that precedent and leaves `APP_SERVICE.md` unlinked.

## Review

`APP_SERVICE.md` is written, following `ACR.md`'s shape: rationale, then this
project's settings, then a commented `az` command reference.

The rationale argues App Service against the two alternatives that were real
choices here -- a VM and AKS/Container Apps -- rather than listing features in
the abstract, and states the trade-off explicitly: App Service is not a
general-purpose orchestrator, so anything needing pod-level networking or a
service mesh does not belong on it.

Three settings are called out as required because each one has a failure mode
that looks like something else. `WEBSITES_PORT=8080` must match the
`Dockerfile`'s `EXPOSE`, or every request returns 502 while the container logs
look perfectly healthy. `SPRING_PROFILES_ACTIVE=azure` is needed because the
image defaults to the `docker` profile. `WEBSITES_CONTAINER_START_TIME_LIMIT`
is worth knowing before a slow JVM start gets mistaken for a crash loop.

The resource names -- `rubens-dev-rg`, `rubens-dev-plan`, `azure-app-svc` --
are PLACEHOLDERS, flagged as such in a blockquote above the table. `azure-iac`
was not available to read, and it owns the real values. The command section
exports `$RG`, `$APP` and `$PLAN` once at the top and derives `$APP_HOST` and
`$RESOURCE_ID` from them, so substituting the real names is three edits rather
than forty.

`az webapp log config` is documented before `az webapp log tail`, deliberately.
Container logging is off by default and the stream prints nothing useful until
it is enabled, which reads as a broken app rather than a missing setting.

Following the `azure-acr` precedent, `APP_SERVICE.md` stands alone: no link was
added to `README.md` or `llms.txt`. Documentation only -- no workflow,
`Dockerfile`, build or source file was touched.

### Review pass against the revised CLAUDE.md

A second pass caught four defects and one DRY problem, all since fixed.

The `az webapp list` query meant to show which apps share a plan filtered on
`appServicePlanId!=null`, which is true for every web app -- it listed all of
them. It now filters on `contains(appServicePlanId, '/$PLAN')`. The plan name
was also the one hardcoded value left in the command section, and the
placeholder blockquote promised a `--plan` flag that appears nowhere; both are
gone. The stop/start comment claimed a stopped app "still" bills for its plan
"unlike restart", implying restart differs -- it does not, so the comment now
says plainly that stopping saves nothing.

`defaultHostName` and the resource id were each derived twice. Both moved into
the export block as `$APP_HOST` and `$RESOURCE_ID` (not `$HOSTNAME`, which bash
already sets), taking the "read it back rather than assemble it by hand"
rationale with them.

Five comments that only restated the command they sat above were deleted, per
the Documentation Standards rule against comments that restate the code. Every
comment carrying real intent stayed -- the 502/`WEBSITES_PORT` link, the
`log config`-before-`log tail` ordering, and the state-versus-availabilityState
distinction are the reason the reference is worth reading over `az --help`.