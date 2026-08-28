## Azure App Service

We need somewhere to run this Spring Boot service that does not turn into
infrastructure work. A virtual machine means we own the OS, the patch cadence,
the TLS certificate, the reverse proxy and the restart-on-crash logic. A
Kubernetes cluster means we own all of that plus the cluster itself, for a
single stateless HTTP API that has no siblings to schedule alongside it.

App Service is Azure's managed platform for web applications and HTTP APIs. You
hand it a container image or a code artifact; Azure runs the host, patches it,
terminates TLS, load balances across instances and restarts the workload when
it dies. There is no host to log into and nothing to keep current.

For this project the unit of deployment is the image `az acr build` already
pushes to `rubensdevacr` — App Service pulls it directly from the registry, so
the ACR workflow in `.github/workflows/acr-build-deploy.yml` is the entire
build half of the pipeline.

### Why App Service

- Managed host: no OS, no patching, no daemon, no Nginx in front. Azure owns
  everything below the container.
- HTTPS on creation: every app gets `<name>.azurewebsites.net` with a managed
  certificate. There is no certificate to request, install or renew.
- Pulls straight from ACR: with a managed identity holding `AcrPull`, App
  Service authenticates to `rubensdevacr` with no registry username or password
  stored anywhere in the app configuration.
- Autoscale: instance count follows a metric rule or a schedule, and scaling up
  to a larger SKU is a plan-level change, not a redeploy.
- Deployment slots: deploy to `staging`, verify against a real hostname, then
  swap into production. The swap is atomic and reversible, which is what makes
  rollback a single command rather than a rebuild.
- Health probes: point App Service at `/actuator/health` and unhealthy
  instances are removed from rotation and replaced without intervention.
- Logs and shell without a host: `az webapp log tail` streams container stdout,
  and `az webapp ssh` opens a shell inside the running container.
- Right-sized for one service: an App Service Plan running a single API costs
  and operates far less than an AKS or Container Apps environment doing the
  same job.

The trade-off is worth stating. App Service runs one container per app by
default — sidecars are supported, but it is not a general-purpose orchestrator.
Anything that needs pod-level networking, custom schedulers or a service mesh
belongs on AKS or Container Apps instead.

### App Service Settings

The web app, its plan and its resource group are created by the **azure-iac**
project, not by this repository. This repository only builds and publishes the
image the app runs.

1. Create the web app in the same Azure region as the ACR registry it pulls
   from, so image pulls stay inside Azure.
2. Use a **Linux** plan. The image is a Linux container built on
   `eclipse-temurin`; Windows plans cannot run it.
3. Grant the web app a system-assigned managed identity and give that identity
   the `AcrPull` role on `rubensdevacr`. Do not configure a registry password.
4. Deployment uses a headless service identity (a Service Principal), the same
   four `AZURE_*` secrets the ACR workflow already consumes.

> The names below are **placeholders**. Substitute the real values from
> `azure-iac`; the next section exports them once and every command reads
> them from there.

#### Resources

- resource group: `rubens-dev-rg`
- App Service Plan: `rubens-dev-plan` (Linux)
- web app name: `azure-app-svc`
- default hostname: `azure-app-svc.azurewebsites.net`

#### Container

- image: `rubensdevacr.azurecr.io/dev/azure-app-svc:0.0.1-SNAPSHOT`
- registry auth: system-assigned managed identity with `AcrPull`

#### Required app settings

| Setting                                  | Value              | Why                                                                                                                   |
|------------------------------------------|--------------------|-----------------------------------------------------------------------------------------------------------------------|
| `WEBSITES_PORT`                          | `8080`             | App Service forwards to this port. It defaults to 80, and the `Dockerfile` publishes `EXPOSE 8080`, so it must be set. |
| `SPRING_PROFILES_ACTIVE`                 | `azure`            | Selects `application-azure.yml`. The image defaults to the `docker` profile.                                          |
| `WEBSITES_CONTAINER_START_TIME_LIMIT`    | `240`              | Startup budget in seconds. The default 230 is usually enough for the AOT-cached JVM, but raise it before assuming a crash. |

#### Health check

Set the health check path to `/actuator/health`. The `docker` profile already
web-exposes only `health`, and enables the `liveness` and `readiness` probe
endpoints under it.

### az Commands

All commands below assume `az login` has been done and the subscription holding
the web app is selected. They all take the same names and identifiers, so
export them once and every later example stays short:

```bash
export RG=rubens-dev-rg
export APP=azure-app-svc
export PLAN=rubens-dev-plan

# Read these back from Azure rather than assembling them by hand.
# "<name>.azurewebsites.net" hardcodes the public-cloud suffix, and the
# resource id is what "az monitor" takes as --resource.
export APP_HOST=$(az webapp show --resource-group "$RG" --name "$APP" \
  --query defaultHostName --output tsv)
export RESOURCE_ID=$(az webapp show --resource-group "$RG" --name "$APP" \
  --query id --output tsv)
```

#### View and list

```bash
# Every web app the signed-in identity can see, with its state and location.
az webapp list --output table

# Only the ones in this resource group.
az webapp list --resource-group "$RG" --output table

# One app. Fails if it does not exist, so it doubles as an existence check.
az webapp show --resource-group "$RG" --name "$APP" --output table

# The three fields that answer "is it supposed to be up, and is it".
# state is what you asked for; availabilityState is what Azure observes.
az webapp show --resource-group "$RG" --name "$APP" \
  --query "{state:state, availability:availabilityState, https:httpsOnly}" \
  --output table

az webapp config hostname list --webapp-name "$APP" \
  --resource-group "$RG" --output table
```

#### The plan behind the app

The plan owns the SKU, the instance count and the cost. An app that is slow or
throttled is often a plan problem, not an app problem.

```bash
az appservice plan list --output table

# SKU tier, size and current instance count for one plan.
az appservice plan show --resource-group "$RG" --name "$PLAN" \
  --query "{sku:sku.name, tier:sku.tier, workers:sku.capacity, apps:numberOfSites}" \
  --output table

# Which apps share this plan -- they share its CPU and memory too.
az webapp list --resource-group "$RG" \
  --query "[?contains(appServicePlanId, '/$PLAN')].{name:name, state:state}" \
  --output table
```

#### Container and configuration

```bash
# Which image the app is actually running. This is the first thing to check
# when a deploy "worked" but the behaviour did not change.
az webapp config container show --resource-group "$RG" --name "$APP" \
  --output table

# Site configuration: linuxFxVersion holds the image reference, healthCheckPath
# the probe, alwaysOn whether the app is allowed to idle out.
az webapp config show --resource-group "$RG" --name "$APP" \
  --query "{image:linuxFxVersion, health:healthCheckPath, alwaysOn:alwaysOn, acrIdentity:acrUseManagedIdentityCreds}" \
  --output table

# Application settings -- the environment variables the container receives.
# Values are shown, so do not paste this output anywhere public.
az webapp config appsettings list --resource-group "$RG" --name "$APP" \
  --output table

# Confirm the port setting specifically. An app that returns 502 on every
# request while the container logs look healthy is almost always this.
az webapp config appsettings list --resource-group "$RG" --name "$APP" \
  --query "[?name=='WEBSITES_PORT'].value" --output tsv

# Held separately from app settings, so the listing above does not show them.
az webapp config connection-string list --resource-group "$RG" --name "$APP" \
  --output table

# The managed identity, and its principal id -- the object the AcrPull role
# assignment is granted to.
az webapp identity show --resource-group "$RG" --name "$APP" --output table
```

#### Troubleshoot

```bash
# Turn container logging on. Off by default, and "log tail" prints nothing
# useful until it is enabled.
az webapp log config --resource-group "$RG" --name "$APP" \
  --docker-container-logging filesystem --level information

# Stream stdout from the running container. This is where Spring Boot's
# startup banner, the "Started App in Xs" line and any stack trace appear.
az webapp log tail --resource-group "$RG" --name "$APP"

# Download the full log set as a zip when the failure has already happened
# and streaming is too late.
az webapp log download --resource-group "$RG" --name "$APP" \
  --log-file webapp-logs.zip

# Deployment history, newest first.
az webapp log deployment list --resource-group "$RG" --name "$APP" \
  --output table
az webapp log deployment show --resource-group "$RG" --name "$APP"

# A shell inside the running container. Confirms what the JVM actually sees:
# the environment, the working directory, the unpacked jar layout.
az webapp ssh --resource-group "$RG" --name "$APP"

# Restart the app. Recycles the container without changing any configuration.
az webapp restart --resource-group "$RG" --name "$APP"

# Stop and start. Stopping does not save money: the plan bills for its
# instances whether or not an app is running on them.
az webapp stop  --resource-group "$RG" --name "$APP"
az webapp start --resource-group "$RG" --name "$APP"

# Hit the health endpoint through the public hostname, end to end.
curl -sS "https://$APP_HOST/actuator/health"

az webapp browse --resource-group "$RG" --name "$APP"
```

#### Metrics and deeper detail

```bash
az monitor metrics list-definitions --resource "$RESOURCE_ID" --output table

# Server errors and request volume over the last hour, in 5-minute buckets.
az monitor metrics list --resource "$RESOURCE_ID" \
  --metric Http5xx Requests --interval PT5M --offset PT1H --output table

# Memory and CPU, for deciding whether the plan SKU is the constraint.
az monitor metrics list --resource "$RESOURCE_ID" \
  --metric MemoryWorkingSet CpuTime --interval PT5M --offset PT1H \
  --output table

# Deployment slots, if any. The production slot is not listed here; it is
# the app itself.
az webapp deployment slot list --resource-group "$RG" --name "$APP" \
  --output table

# How traffic is split across slots, for a staged rollout.
az webapp traffic-routing show --resource-group "$RG" --name "$APP"

# Everything, unfiltered. Useful when you do not yet know which field holds
# the answer -- pipe it to a pager or into jq.
az webapp show --resource-group "$RG" --name "$APP" --output json
```

One detail is easy to get wrong. `az webapp log tail` streams the **platform
and container** logs, not the application's own file logs. This project logs to
stdout, so the two coincide — but a Spring configuration that redirects logging
to a file inside the container would make the stream go quiet without anything
being wrong.
