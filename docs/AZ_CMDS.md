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
az acr show --name crrgomeslab02 --output table

# Read the login server back from Azure rather than assembling
# "<name>.azurecr.io", which hardcodes the public-cloud suffix.
az acr show --name crrgomeslab02 --query loginServer --output tsv

# Storage consumed against the service tier quota.
az acr show-usage --name crrgomeslab02 --output table
```

### Check and list the repository

```bash
# change the following accordingly:
export REGISTRY='crrgomeslab02'
export REPOSITORY='lab/azure-app-svc'
export TAG='0.0.0-SNAPSHOT'

# Every repository in the registry, namespace included (e.g. lab/azure-acr).
az acr repository list --name "${REGISTRY}" --output tsv

# Tags of one repository.
az acr repository show-tags --name "${REGISTRY}" \
  --repository "${REPOSITORY}" --output tsv

# The last tag pushed. "--orderby" only means anything alongside "--top";
# without it the ordering of the returned page is unspecified. This is what
# app-svc-create-deploy.yml resolves when its image_tag input is left empty.
az acr repository show-tags --name "${REGISTRY}" \
  --repository "${REPOSITORY}" --orderby time_desc --top 1 --output tsv

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
export REGISTRY='crrgomeslab02'
export REPOSITORY='lab/azure-app-svc'
export TAG='0.0.0-SNAPSHOT'

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
export REGISTRY='crrgomeslab02'
# The group name is composed from the CAF workload and environment tokens, so a
# local shell needs both. An azure-iac checkout already exports them for
# Terraform; note the lowercase spelling here, which is what Terraform reads.
# The GitHub Actions variable holding the workload is TF_VAR_WORKLOAD.
: "${TF_VAR_workload:?set TF_VAR_workload to the CAF workload token, e.g. rgomes}"
: "${TF_VAR_env:?set TF_VAR_env to the environment token, e.g. lab}"
export RESOURCE_GROUP="rg-${TF_VAR_workload}app-${TF_VAR_env}"
export REPOSITORY='lab/azure-app-svc'
export APP_NAME='azure-app-svc'
export TAG='0.0.0-SNAPSHOT'

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
az webapp show --resource-group "${RESOURCE_GROUP}" --name "${APP_NAME}" \
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
export REGISTRY='crrgomeslab02'
# The group name is composed from the CAF workload and environment tokens, so a
# local shell needs both. An azure-iac checkout already exports them for
# Terraform; note the lowercase spelling here, which is what Terraform reads.
# The GitHub Actions variable holding the workload is TF_VAR_WORKLOAD.
: "${TF_VAR_workload:?set TF_VAR_workload to the CAF workload token, e.g. rgomes}"
: "${TF_VAR_env:?set TF_VAR_env to the environment token, e.g. lab}"
export RESOURCE_GROUP="rg-${TF_VAR_workload}app-${TF_VAR_env}"
export REPOSITORY='lab/azure-app-svc'
export APP_NAME='azure-app-svc'
export PLAN='asp-biceplab-dev-centralus'
az appservice plan list --output table

# SKU tier, size and current instance count for one plan.
#
# Filtered from a subscription-wide "list", NOT "az appservice plan show
# --resource-group". The plan is named for the "biceplab" workload, not
# "rgomesapp", and does not live in the app's resource group -- "show" would
# report it as missing.
#
# "az webapp create --plan" needs no such care: it tries the resource group,
# then falls back to a subscription-wide search of its own, and raises on a
# name that is absent or ambiguous.
az appservice plan list \
  --query "[?name=='${PLAN}'].{sku:sku.name, tier:sku.tier, workers:sku.capacity, apps:numberOfSites, group:resourceGroup, region:location}" \
  --output table

# The full resource id, which is what "az webapp create --plan" needs when the
# plan is in another group.
az appservice plan list --query "[?name=='${PLAN}'].id" --output tsv

# Which apps share this plan -- they share its CPU and memory too.
az webapp list --resource-group "${RESOURCE_GROUP}" \
  --query "[?contains(appServicePlanId, '/${PLAN}')].{name:name, state:state}" \
  --output table
```

