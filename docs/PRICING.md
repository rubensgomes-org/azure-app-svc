# Pricing

Idle-provisioning cost of this project's Azure resources: no traffic, no
reservations, Central US, pay-as-you-go USD retail rates.

Scope: resources specific to `azure-app-svc`. The shared platform estate
(VNet, Key Vault, Log Analytics, other apps' Container Apps) is provisioned
by `azure-iac` for multiple workloads and is not priced here.

## App Service Plan (Linux)

Billed hourly whether or not an app is deployed or receiving traffic.

| SKU | Hourly  | Daily (×24) | Weekly (×168) | Monthly (×720) |
|-----|---------|-------------|---------------|-----------------|
| F1  | $0.000  | $0.00       | $0.00         | $0.00           |
| B1  | $0.018  | $0.43       | $3.02         | $12.96          |
| B2  | $0.035  | $0.84       | $5.88         | $25.20          |
| B3  | $0.070  | $1.68       | $11.76        | $50.40          |
| S1  | $0.095  | $2.28       | $15.96        | $68.40          |
| S2  | $0.190  | $4.56       | $31.92        | $136.80         |
| S3  | $0.380  | $9.12       | $63.84        | $273.60         |

`plan-create.yml` defaults to F1, which costs nothing. Trade-off: no
health-check enforcement, no Always On, and the app sleeps after ~20 minutes
idle -- irrelevant with no traffic.

## Azure Container Registry

`crrgomesdev01` (Basic tier, `rg-rgomesplatform-dev`) is required for image
pulls regardless of app traffic.

| Meter                | Daily   | Weekly | Monthly |
|-----------------------|---------|--------|---------|
| Basic registry unit  | $0.1666 | $1.17  | ~$5.00  |

Includes 10 GB storage; overage is $0.10/GB-month.

## No incremental cost

- The App Service (web app) -- billed through its plan, not separately.
- The managed identity (`id-rgomesapp-dev`).
- Resource groups.

## Bottom line

With the F1 default, an idle plan costs $0/month. The only recurring charge
tied to this project is the ~$5/month ACR Basic registry, which already
exists and is untouched by `plan-delete.yml`. Moving to a paid SKU applies
its hourly rate above whether or not the app ever serves a request.
