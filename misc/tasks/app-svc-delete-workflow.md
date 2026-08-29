# Task: `app-svc-delete.yml` workflow

Tear down the App Service and the ACR repository it ran from, in one dispatch,
with the same safeguards the repo already uses for destructive ACR operations.

## Tasks

- [x] Create `.github/workflows/app-svc-delete.yml`
  - [x] House-style header: SPDX, AI disclosure, boxed prose, `@author Rubens Gomes`
  - [x] `workflow_dispatch` inputs: `resource_group`, `app_name`, `environment`,
        `registry_name`, `confirm` (required)
  - [x] `permissions: contents: read`; concurrency group `app-svc-delete`
  - [x] Job `delete-app-service`: checkout, resolve coordinates, SAFEGUARD,
        Azure sign-in, existence check, `az webapp delete`, verify gone
  - [x] Job `delete-acr-repository`: `needs` job 1, calls
        `azure-workflows/.github/workflows/acr-repo-delete.yml@v1`
- [x] Fix the `azure-app-svc` typo in `AZ_CMDS.md` (was `auzre-app-svc`)
- [x] Add the workflow to `llms.txt` and correct its "all four workflows are stubs" claim
- [x] Same correction in `BUILD.md`: workflow table, count, `azure-login` consumers
- [x] Reconcile `APP_SERVICE.md`: restore the export block, rename `RG`/`APP` to
      `RESOURCE_GROUP`/`APP_NAME`, resource group `rg-dev-app`
- [x] Validate: YAML parses, every `run:` body passes `bash -n`, no `${{ }}`
      interpolated into a shell body

## Review

`app-svc-delete.yml` is the first workflow here that is not a pure stub. The
`az webapp` steps are inline because `azure-workflows` has no App Service
reusable workflow; only the second job delegates. Promoting the first job into
`azure-workflows` later is a move, not a rewrite.

Three decisions worth recording:

- **One typed phrase, both jobs.** `confirm` must equal
  `DELETE REPO <environment>/<artifactId>`. It gates the App Service delete here
  and is passed unchanged to `acr-repo-delete.yml@v1`, which validates it again
  against its own resolved repository. The operator types one thing; both halves
  are guarded, and the duplicate check is deliberate defence in depth.
- **`az webapp list`, not `show`, for the existence check.** `show` cannot
  distinguish "absent" from "the subscription could not be reached", so an
  outage would look like a successful no-op and the run would go green having
  deleted nothing. Same reasoning the shared ACR workflow gives for using
  `az acr repository list`.
- **The verify step inverts `az webapp show`, then cross-checks.** A successful
  `show` fails the run -- the delete did not take effect. A failed `show` is only
  believed once `az webapp list` succeeds and does not contain the app, because a
  non-zero `show` is also what an expired token looks like.

The existence check was not in the original request. It makes a re-run idempotent
(skip the delete, report "already absent", green) rather than erroring on an
app that is already gone. Remove the step and its `if:` guard if a missing app
should fail loudly instead.

`actionlint` was not available and could not be downloaded in this environment,
so validation was: YAML parse of every workflow, `bash -n` on each `run:` body,
and a check that no `${{ }}` reaches a shell body directly -- every dynamic value
arrives through `env:`, as elsewhere in this repo.

Follow-up, done in the same pass: `APP_SERVICE.md` had lost the `export` block
that defined `$RG` and `$APP` while 19 later commands still read them, and its
settings section said `rg-dev-app` while the examples said `rubens-dev-rg`. The
block is restored with the real values and the AZ_CMDS.md names -- `RESOURCE_GROUP`
and `APP_NAME` -- and every command renamed to match. `PLAN` was dropped: the
plan name is a portal-provisioning TODO and nothing referenced it any more. The
same edit had also left registry auth described as system-assigned in one place
and user-assigned (`id-dev-app`) in two others; reconciled to user-assigned.
