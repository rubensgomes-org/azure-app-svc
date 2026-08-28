# Add the ACR workflow stubs

Give this repository the same two ACR buttons `azure-acr` already has:
`acr-build-deploy` (build the jar, then `az acr build`) and the destructive,
doubly-guarded `acr-repo-delete`. Both bodies already exist in
`rubensgomes-org/azure-workflows@v1`; only the dispatch stubs were missing.

## Tasks

- [x] Copy `acr-build-deploy.yml` and `acr-repo-delete.yml` from `azure-acr`
- [x] Point the `confirm` example at `dev/azure-app-svc`, the only per-repo edit
- [x] Port the three `RUN true` separators into the `Dockerfile`
- [x] Update `BUILD.md`, `README.md` and `llms.txt` for four workflows

## Review

The stubs are repo-agnostic: the shared workflow resolves the image name from
`artifactId` in `app/gradle.properties` and the default tag from `APP_VERSION`
in the generated `.env`, so nothing here is hardcoded. Everything else --
`@v1` pins, `registry_name: rubensdevacr`, `environment: dev`, the explicit
secret mappings -- is copied verbatim.

The `Dockerfile` change is the part that is not cosmetic. `az acr build` runs
the CLASSIC Docker builder, which corrupts its layer chain on consecutive
`COPY --from` instructions and fails the last one with "layer does not exist"
(moby#38866, Azure/acr#693). A no-op `RUN` between them is the documented
workaround. This never reproduces locally, because Docker Desktop and
`docker compose build` use BuildKit, which does not have the bug.

`secrets: inherit` is deliberately NOT used: it forwards only secrets whose
names match the callee's declarations, so it would not map `RUBENS_PAT_TOKEN`
onto the declared `packages-token`. The four `AZURE_*` secrets are org-level,
as `azure-acr` carries no repository-level secrets of its own.

`ACR.md` was deliberately not copied. It documents the registry itself and
belongs to `azure-acr`; it is not linked from any doc here.
