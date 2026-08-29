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

- Subscription: rubens-pay-as-go-subscription
- Resource Group: rg-dev-app
- Instance Name: azure-app-svc
- Region: Central US
- Registry: rubensdevacr
- Authentication: managed identity
- Identity: id-dev-app
- Image: dev/azure-app-svc
- Tag: 0.0.1-SNAPSHOT
- Port: 80
- Enable public access: On
- Enable virtual networking integration: Off
- Tag: ai-200-training=azure-app-svc
- Image:Tag rubensdevacr.azurecr.io/dev/azure-app-svc:0.0.1-SNAPSHOT

The web app, and its plan are created manually from within the Azure Portal 
App Services - > crete, not by this repository. This repository only builds and 
publishes the image the app runs.

1. Create the web app in the same Azure region as the ACR registry it pulls
   from, so image pulls stay inside Azure.
2. Use a **Linux** plan. The image is a Linux container built on
   `eclipse-temurin`; Windows plans cannot run it.
3. Grant the web app a user-assigned managed identity provisioned in the 
   `azure-iac` repo project. Do not configure a registry password.
4. Deployment uses a headless service identity (a Service Principal), the same
   four `AZURE_*` secrets the ACR workflow already consumes.

> These are the real values, and they match the `export` block in the next
> section and the App Service Settings above. Every command below reads them
> from there.

#### Resources

- resource group: `rg-dev-app`
- App Service Plan: <TODO: Azure Portal manual provisioning> (Linux)
- web app name: `azure-app-svc`
- default hostname: <TODO: to be resolved after app service is provisioned>

#### Container

- image: `rubensdevacr.azurecr.io/dev/azure-app-svc:0.0.1-SNAPSHOT`
- registry auth: the user-assigned managed identity `id-dev-app`, with `AcrPull`

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
export RESOURCE_GROUP='rg-dev-app'
export APP_NAME='azure-app-svc'

# Read these back from Azure rather than assembling them by hand.
# "<name>.azurewebsites.net" hardcodes the public-cloud suffix, and the
# resource id is what "az monitor" takes as --resource.
export APP_HOST=$(az webapp show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --query defaultHostName --output tsv)
export RESOURCE_ID=$(az webapp show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --query id --output tsv)
```

#### Container and configuration

```bash
# Which image the app is actually running. This is the first thing to check
# when a deploy "worked" but the behaviour did not change.
az webapp config container show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --output table

# Site configuration: linuxFxVersion holds the image reference, healthCheckPath
# the probe, alwaysOn whether the app is allowed to idle out.
az webapp config show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --query "{image:linuxFxVersion, health:healthCheckPath, alwaysOn:alwaysOn, acrIdentity:acrUseManagedIdentityCreds}" \
  --output table

# Application settings -- the environment variables the container receives.
# Values are shown, so do not paste this output anywhere public.
az webapp config appsettings list --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --output table

# Confirm the port setting specifically. An app that returns 502 on every
# request while the container logs look healthy is almost always this.
az webapp config appsettings list --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --query "[?name=='WEBSITES_PORT'].value" --output tsv

# Held separately from app settings, so the listing above does not show them.
az webapp config connection-string list --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --output table

# The managed identity, and its principal id -- the object the AcrPull role
# assignment is granted to.
az webapp identity show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" --output table
```

#### Troubleshoot

```bash
# Turn container logging on. Off by default, and "log tail" prints nothing
# useful until it is enabled.
az webapp log config --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --docker-container-logging filesystem --level information

# Stream stdout from the running container. This is where Spring Boot's
# startup banner, the "Started App in Xs" line and any stack trace appear.
az webapp log tail --resource-group "$RESOURCE_GROUP" --name "$APP_NAME"

# Download the full log set as a zip when the failure has already happened
# and streaming is too late.
az webapp log download --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --log-file webapp-logs.zip

# Deployment history, newest first.
az webapp log deployment list --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --output table
az webapp log deployment show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME"

# A shell inside the running container. Confirms what the JVM actually sees:
# the environment, the working directory, the unpacked jar layout.
az webapp ssh --resource-group "$RESOURCE_GROUP" --name "$APP_NAME"

# Restart the app. Recycles the container without changing any configuration.
az webapp restart --resource-group "$RESOURCE_GROUP" --name "$APP_NAME"

# Stop and start. Stopping does not save money: the plan bills for its
# instances whether or not an app is running on them.
az webapp stop  --resource-group "$RESOURCE_GROUP" --name "$APP_NAME"
az webapp start --resource-group "$RESOURCE_GROUP" --name "$APP_NAME"

# Hit the health endpoint through the public hostname, end to end.
curl -sS "https://$APP_HOST/actuator/health"

az webapp browse --resource-group "$RESOURCE_GROUP" --name "$APP_NAME"
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
az webapp deployment slot list --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --output table

# How traffic is split across slots, for a staged rollout.
az webapp traffic-routing show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME"

# Everything, unfiltered. Useful when you do not yet know which field holds
# the answer -- pipe it to a pager or into jq.
az webapp show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" --output json
```

One detail is easy to get wrong. `az webapp log tail` streams the **platform
and container** logs, not the application's own file logs. This project logs to
stdout, so the two coincide — but a Spring configuration that redirects logging
to a file inside the container would make the stream go quiet without anything
being wrong.
