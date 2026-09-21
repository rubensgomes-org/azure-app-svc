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
pushes to `crrgomeslab02` — App Service pulls it directly from the registry, so
the ACR workflow in `.github/workflows/acr-build-push.yml` is the entire
build half of the pipeline.

### Why App Service

1. Managed host: no OS, no patching, no daemon, no Nginx in front. Azure owns
   everything below the container.
2. HTTPS on creation: every app gets `<name>.azurewebsites.net` with a managed
   certificate. There is no certificate to request, install or renew.
3. Pulls straight from ACR: with a managed identity holding `AcrPull`, App
   Service authenticates to `crrgomeslab02` passwordless.
4. Autoscale: instance count follows a metric rule or a schedule, and
   scaling up to a larger SKU is a plan-level change, not a redeploy.
5. Deployment slots: deploy to `staging`, verify against a real hostname, then
   swap into production. The swap is atomic and reversible, which is what makes
   rollback a single command rather than a rebuild.
6. Health probes: point App Service at `/actuator/health` and unhealthy
   instances are removed from rotation and replaced without intervention.
7. Right-sized for one service: an App Service Plan running a single API costs
   and operates far less than an AKS or Container Apps environment.

### App Service Settings

`.github/workflows/app-svc-create-deploy.yml` is the supported way to create
the app and to deploy a new image to it. It resolves the managed identity to a
resource id, resolves the image tag from the registry, and reads the result
back before reporting success.

The portal walkthrough below is the manual fallback, and is also the list of
what the workflow sets. Two of its values are resolved at run time rather than
typed: the image tag comes from the registry, and the managed identity is
looked up by name across the subscription, because it lives in the platform
group rather than the app's and `--assign-identity` needs its resource id.

The plan must be a **Linux** plan. Nothing verifies that: `az webapp create`
accepts a Windows plan, silently discards the container image, and creates an
App Service that runs nothing.

- Subscription: rubens-pay-as-go-subscription
- Region: Central US

- Resource Group: `rg-rgomesapp-lab`
- Name: `appsvc`
- Linux Plan (Central US): asp-biceplab-dev-centralus (F1)
- Image:Tag `crrgomeslab02.azurecr.io/lab/azure-app-svc:<tag>`
- Identity: `id-rgomesapp-lab`

- Publish: Container
- O.S.: Linux
- Zone redundancy: disabled
- Managed instance: disabled
- Create database: none
- Sidecar support: disabled
- Image source: Azure Container Registry
- Registry: crrgomeslab02
- Authentication: managed identity
- Image: lab/azure-app-svc
- Tag: the last tag pushed to the repository
- Port: 8080 -- NOT 80. The `Dockerfile` publishes `EXPOSE 8080`

- Start up command: (keep it blank)
- Enable public access: On
- Enable virtual networking integration: Off
- Enable Application Insights: No
- Enable Defender for App Service: (leeave unchecked)
- Tags:
  owner: rubens.s.gomes@gmail.com
  env: lab

- [azure-app-svc Swagger UI](https://appsvc-gnbahshnd8eqf7hs.centralus-01.azurewebsites.net/swagger-ui.html)
- [azure-app-svc hello world URL](https://appsvc-gnbahshnd8eqf7hs.centralus-01.azurewebsites.net/api/v1/helloworld)

### Required app settings

| Setting                  | Value  | Why                                                                                                                                                                       |
|--------------------------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `WEBSITES_PORT`          | `8080` | App Service forwards to port 80 by default, while the `Dockerfile` publishes `EXPOSE 8080`. Without this every request returns 502 while the container logs look healthy. |
| `SPRING_PROFILES_ACTIVE` | `lab`  | Selects `application-lab.yml`. The image defaults to the `docker` profile.                                                                                                |

Profiles apply left to right, so `docker,lab` would additionally keep the
container settings that live only in `application-docker.yml` -- ANSI output
disabled, and `management.endpoint.health.probes.enabled`, which is what backs
`/actuator/health/liveness` and `/readiness`. `application-lab.yml` does not
duplicate them, so plain `lab` leaves `/actuator/health` working and those two
children absent.

### Health check

`app-svc-create-deploy` sets the health check path to `/actuator/health` when
it creates the app. Note the plan tier: App Service health check requires
**Basic or higher**, and the plan recorded above is **F1 (Free)**. On F1 the
path is stored on the site configuration and never acted on, so the workflow
emits a build warning rather than letting the run imply a probe that does not
exist. Upgrading the plan is what turns it on; nothing needs to change here.

### Creating and deploying

```bash
# Create the app if it is absent, otherwise deploy the last image pushed to
# ACR onto the existing one. Every input has a default; the dispatch form
# shows them.
gh workflow run app-svc-create-deploy.yml

# Pin a specific tag instead of resolving the newest.
gh workflow run app-svc-create-deploy.yml -f image_tag=0.0.3-SNAPSHOT

# Keep application-docker.yml's container settings alongside the lab overrides.
gh workflow run app-svc-create-deploy.yml -f spring_profiles_active=docker,lab
```

An App Service that already exists is only ever pointed at the new image. Its
plan, identity, tags and application settings are left untouched, and any that
differ from what a fresh create would have produced are reported as warnings at
the end of the run.

### az Commands

All commands below assume `az login` has been done and the subscription holding
the web app is selected.

```bash
export APP_HOST=$(
    az webapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_NAME" \
    --query defaultHostName \
    --output tsv
  )
export RESOURCE_ID=$(
    az webapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_NAME" \
    --query id \
    --output tsv
  )
```

#### Container and configuration

```bash
# Which image the app is actually running. This is the first thing to check
# when a deploy "worked" but the behaviour did not change.
az webapp config container show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_NAME" \
  --output table

# Site configuration: linuxFxVersion holds the image reference, healthCheckPath
# the probe, alwaysOn whether the app is allowed to idle out.
az webapp config show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_NAME" \
  --query "{image:linuxFxVersion, health:healthCheckPath, alwaysOn:alwaysOn, acrIdentity:acrUseManagedIdentityCreds}" \
  --output table

# Application settings -- the environment variables the container receives.
# Values are shown, so do not paste this output anywhere public.
az webapp config appsettings list \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_NAME" \
  --output table

# Confirm the port setting specifically. An app that returns 502 on every
# request while the container logs look healthy is almost always this.
az webapp config appsettings list \
  --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
  --query "[?name=='WEBSITES_PORT'].value" \
  --output tsv

# Held separately from app settings, so the listing above does not show them.
az webapp config connection-string list \
  --resource-group "$RESOURCE_GROUP"  \
  --name "$APP_NAME" \
  --output table

# The resource id of the user-assigned identity the app pulls with. Resolved
# across the subscription because it lives in the platform group, not the app
# group -- and "--assign-identity" reads a bare NAME as the SYSTEM-assigned
# identity, so the full id is not optional.
az identity list --query "[?name=='id-rgomesapp-lab'].id" --output tsv

# The managed identity, and its principal id -- the object the AcrPull role
# assignment is granted to.
az webapp identity show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_NAME" --output table
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
