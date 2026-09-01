# Azure CLI Commands

## az login

All commands below assume `az login` as displayed has been done and the 
subscription holding the registry is selected.

- The following shell environment variables should be configured:

    ```bash
    ARM_CLIENT_ID=<SECRET>
    ARM_TENANT_ID=<SECRET>
    ARM_CLIENT_SECRET=<SECRET>
    ARM_SUBSCRIPTION_ID=<SECRET>
    ARM_SUBSCRIPTION_NAME=<SECRET>
    ```

- Sign in to Azure using a `Service Principal` account:

    ```bash
    az login --service-principal \
      --username "${ARM_CLIENT_ID}" \
      --password "${ARM_CLIENT_SECRET}" \
      --tenant "${ARM_TENANT_ID}" \ 
      --allow-no-subscriptions
    ```

## ACR commands

### Check and list the registry

```bash
# Every registry the signed-in identity can see, with its login server.
az acr list --output table

# One registry. Fails if it does not exist, so it doubles as an existence check.
az acr show --name rubensdevacr --output table

# Read the login server back from Azure rather than assembling
# "<name>.azurecr.io", which hardcodes the public-cloud suffix.
az acr show --name rubensdevacr --query loginServer --output tsv

# Storage consumed against the service tier quota.
az acr show-usage --name rubensdevacr --output table
```

### Check and list the repository

```bash
# change the following accordingly:
export REGISTRY='rubensdevacr'
export REPOSITORY='dev/azure-app-svc'
export TAG='0.0.1-SNAPSHOT'

# Every repository in the registry, namespace included (e.g. dev/azure-acr).
az acr repository list --name "${REGISTRY}" --output tsv

# Tags of one repository.
az acr repository show-tags --name "${REGISTRY}" \
  --repository "${REPOSITORY}" --output tsv

# The digest a tag currently points at. Use this to confirm a push landed:
# "az acr build" has reported success while the push did not.
az acr repository show --name "${REGISTRY}" \
  --image "${REPOSITORY}:${TAG}" --query digest --output tsv

# Manifests, including the untagged ones left behind by re-pushing a tag.
# Here --name is the REPOSITORY; the registry is --registry. Preview command.
az acr manifest list-metadata --registry "${REGISTRY}" \
  --name "${REPOSITORY}" --output table
```

### Smoke test an image with using using an ACR task agent

`az acr run` runs a one-off task in the registry, so the image is pulled and
started by ACR itself. That proves the layers are pullable and the JVM starts,
with no Docker on the machine issuing the command.

```bash
# change the following accordingly:
export REGISTRY='rubensdevacr'
export REPOSITORY='dev/azure-app-svc'
export TAG='0.0.1-SNAPSHOT'

# see note about the use of --entrypoint below
az acr run \
  --registry "${REGISTRY}" \
  --cmd "--entrypoint java \$Registry/${REPOSITORY}:${TAG} --version" \
  /dev/null
```

Three details are easy to get wrong:

- `$Registry` is substituted by ACR Tasks, not by the shell. Quote it so bash
  leaves it alone.
- `/dev/null` is the source location. The task needs no build context, and
  passing `.` would upload the whole project for nothing.
- `--entrypoint java` is required. The image ENTRYPOINT is
  `java -XX:AOTCache=app.aot -jar application.jar`, so without the override the
  smoke test boots the entire application instead of answering `--version`.

## App Service Commands

```bash
# change the following accordingly:
export REGISTRY='rubensdevacr'
# TF_VAR_rg_suffix is a GitHub organization variable, so a local shell does not
# have it. Export it here (an azure-iac checkout already exports it for
# Terraform) -- the group name cannot be composed without it.
: "${TF_VAR_rg_suffix:?set TF_VAR_rg_suffix to the organization resource-group suffix}"
export RESOURCE_GROUP="rg-dev-app-${TF_VAR_rg_suffix}"
export REPOSITORY='dev/azure-app-svc'
export APP_NAME='azure-app-svc'
export TAG='0.0.1-SNAPSHOT'

# Every web app with its state and location.
az webapp list --output table

# list all app services and FQDN
az webapp list \
  --query "[].{App:name,URL:defaultHostName,RG:resourceGroup}" \
  --output table

# Only the ones in this resource group.
az webapp list --resource-group "${RESOURCE_GROUP}" --output table

# The three fields that answer "is it supposed to be up, and is it".
# state is what you asked for; availabilityState is what Azure observes.
az webapp show --resource-group "${RESOURCE_GROUP}" --name "${APP_APP_NAME}" \
  --query "{state:state, availability:availabilityState, https:httpsOnly}" \
  --output table

az webapp config hostname list --webapp-name "$APP" \
  --resource-group "$RG" --output table

# display the app service FQDN
az webapp show \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${APP_NAME}" \
  --query defaultHostName \
  --output tsv

# display the app service FQDN and the status
az webapp show \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${APP_NAME}" \
  --query "{URL: defaultHostName, State: state}"
 
 # delete the app service
 az webapp delete \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${APP_NAME}"

# show if app exists
az webapp show \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${APP_NAME}"
```

#### The plan behind the app

The plan owns the SKU, the instance count and the cost. An app that is slow or
throttled is often a plan problem, not an app problem.

```bash
# change the following accordingly:
export REGISTRY='rubensdevacr'
# TF_VAR_rg_suffix is a GitHub organization variable, so a local shell does not
# have it. Export it here (an azure-iac checkout already exports it for
# Terraform) -- the group name cannot be composed without it.
: "${TF_VAR_rg_suffix:?set TF_VAR_rg_suffix to the organization resource-group suffix}"
export RESOURCE_GROUP="rg-dev-app-${TF_VAR_rg_suffix}"
export REPOSITORY='dev/azure-app-svc'
export APP_NAME='azure-app-svc'
export PLAN=<TODO: provisioned in Azure Portal>
az appservice plan list --output table

# SKU tier, size and current instance count for one plan.
az appservice plan show --resource-group "${RESOURCE_GROUP}" --name "${PLAN}" \
  --query "{sku:sku.name, tier:sku.tier, workers:sku.capacity, apps:numberOfSites}" \
  --output table

# Which apps share this plan -- they share its CPU and memory too.
az webapp list --resource-group "${RESOURCE_GROUP}" \
  --query "[?contains(appServicePlanId, '/${PLAN}')].{name:name, state:state}" \
  --output table
```

