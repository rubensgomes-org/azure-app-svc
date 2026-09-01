# Append TF_VAR_rg_suffix to every resource group name

Every resource group name this repository names must become
`<base>-${TF_VAR_rg_suffix}`, where `TF_VAR_rg_suffix` is a GitHub
**organization variable** shared across `rubensgomes-org`. The one base in use
is `rg-dev-app`, so with a suffix of `rsg` the real group is `rg-dev-app-rsg`.

`azure-iac` creates the groups and owns the naming; this repository only
consumes it. Nothing here provisions a resource group.

## Design decisions

**Expressions are NOT evaluated in `workflow_dispatch` input defaults.** GitHub
resolves the `on:` block before the `vars` context exists, so
`default: rg-dev-app-${{ vars.TF_VAR_rg_suffix }}` would be dispatched as that
literal string. The suffix therefore has to be applied at job level, and the
input has to carry the BASE name only.

**The input is renamed `resource_group` -> `resource_group_base`.** On a
destructive workflow a field labelled `resource_group` that is not the resource
group is a trap. The rename makes the operator read the description before
typing. Only `workflow_dispatch` consumes it; no caller breaks.

**A missing suffix fails the run, loudly.** An unset or misspelled org variable
expands to empty, making the group `rg-dev-app-` -- which `az webapp list`
rejects with a message about a group that does not exist, indistinguishable
from a genuinely wrong group. The guard fires BEFORE `azure-login`, alongside
the existing safeguards, and says which variable is missing.

**`misc/tasks/app-svc-delete-workflow.md` is left alone.** It is the completed
record of a shipped task; its checked boxes describe what was built then, not
what is true now. This file supersedes it.

## Workflow - .github/workflows/app-svc-delete.yml

- [x] Rename input `resource_group` -> `resource_group_base`, default stays `rg-dev-app`
- [x] Reword its description: base name, org suffix appended, resolved name shown in the safeguard banner
- [x] Job `env`: `RESOURCE_GROUP: ${{ inputs.resource_group_base }}-${{ vars.TF_VAR_rg_suffix }}`
- [x] Job `env`: add `RG_SUFFIX: ${{ vars.TF_VAR_rg_suffix }}` for the guard to test
- [x] Rename step "Resolve the repository coordinates" -> "Resolve the deployment coordinates"; add the empty-`RG_SUFFIX` guard beside the existing empty-`ENVIRONMENT` guard, and echo the resolved group
- [x] Header comment: record that the group name is composed, and why the suffix is not in the input default
- [x] `app_name` input description re-read after the rename -- "Its plan and resource group are left alone" is still correct, left unchanged

Untouched: the `az webapp list` / `delete` / `show` steps already read
`$RESOURCE_GROUP` and need no edit. The `delete-acr-repository` job takes no
resource group.

## Docs

`export RESOURCE_GROUP='rg-dev-app'` must become
`export RESOURCE_GROUP="rg-dev-app-${TF_VAR_rg_suffix}"` -- note the DOUBLE
quotes; the current single quotes would not expand it. `TF_VAR_rg_suffix` is a
GitHub org variable, so a local shell does not have it automatically; each
export block gets a one-line note to set it (the `TF_VAR_` prefix means an
`azure-iac` user already exports it for Terraform).

- [x] `AZ_CMDS.md:107` - App Service Commands export block
- [x] `AZ_CMDS.md:164` - "The plan behind the app" export block
- [x] `APP_SERVICE.md:113` - az Commands export block
- [x] `APP_SERVICE.md:49` - "Resource Group: rg-dev-app" in App Service Settings
- [x] `APP_SERVICE.md:82` - "resource group: `rg-dev-app`" under Resources
- [x] `llms.txt:79` - the `app-svc-delete.yml` entry: new input name, composed group, org variable

## Verification

- [x] `actionlint` on `.github/workflows/` (the `rhysd/actionlint:1.7.7` image `lint.yml` uses)
- [x] Confirm no `${{` survives inside any `workflow_dispatch` default
- [x] Re-grep the tree for `rg-dev-app` and confirm every remaining hit is a documented base name, not a full group name
- [x] Shell-parse the edited doc blocks to confirm the quoting expands

## Review

Done. One base name, `rg-dev-app`, now composes to
`rg-dev-app-${TF_VAR_rg_suffix}` in all six places. No Java, Gradle, Docker or
other workflow touches a resource group, so the blast radius was four files.

**The input rename was the load-bearing decision.** `resource_group` ->
`resource_group_base` was not cosmetic: with the suffix applied at job level,
an operator who read the old label and typed a full group name would have got
`rg-dev-app-rsg-rsg`. On a workflow that permanently deletes an App Service the
label has to match what the field means. The default is unchanged, and the
resolve step now echoes the composed name before the confirmation phrase is
checked, so the operator sees the real target while there is still time to
cancel.

**Two failure modes were closed rather than left to Azure.** An unset
`TF_VAR_rg_suffix` composes `rg-dev-app-`, and Azure reports that as a group
that does not exist -- the same message a genuinely wrong group gives. The
guard in the resolve step fails first and names the variable, and it sits
before `azure-login`, so the existing promise that a denial means Azure was
never contacted still holds. In the docs the old `export RESOURCE_GROUP=
'rg-dev-app'` used SINGLE quotes, which would have shipped the literal string
`rg-dev-app-${TF_VAR_rg_suffix}` to `az`; they are double-quoted now, and each
block opens with a `: "${TF_VAR_rg_suffix:?...}"` guard because a GitHub
organization variable is not present in a local shell.

**Verification.** actionlint 1.7.7 -- the version `lint.yml` pins -- linted all
five workflow files with 0 errors. Confirmed it was actually exercising the
changed file rather than passing vacuously: reverting `inputs.resource_group_base`
to `inputs.resource_group` was caught at line 90 (`property "resource_group" is
not defined in object type ...`), and the file was restored. Docker was not
running, so the binary was fetched to a scratchpad rather than the image; same
version. Asserted via YAML parse that no `${{` survives in any
`workflow_dispatch` default -- the constraint this whole design exists for. The
doc export block was executed both ways: with the suffix set it yields
`rg-dev-app-rsg`; unset, it exits 1 rather than composing a trailing hyphen.

**Deliberately untouched.** `misc/tasks/app-svc-delete-workflow.md` still says
`resource_group` and `rg-dev-app` in two places. It is the completed record of
a shipped task and its checked boxes describe what was built then; rewriting
them would falsify the history. This file supersedes it.

**Left for a human.** Nothing is committed or pushed. The organization variable
`TF_VAR_rg_suffix` must exist on `rubensgomes-org` as an Actions VARIABLE, not
a secret -- a secret resolves to empty through `vars.` and every run would stop
at the guard. Its value must also match what `azure-iac` actually provisioned;
that repository is the authority on the naming and was not available to check
against, so the hyphen join is per your instruction, not verified against a
real group.
