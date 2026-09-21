[![AI Assisted](https://img.shields.io/badge/AI--Assisted-Development-007ACC?logo=openai&logoColor=white)](./AI_DISCLAIMER.md)

# azure-app-svc

Spring Boot demo deployed to Azure App Service.

## AI Disclaimer

This project includes code and documentation created with the assistance of AI
tools. For details on usage, limits, and review practices, please see
the [AI_DISCLAIMER](./AI_DISCLAIMER.md).

## Installation

To use this project, ensure that your environment is properly configured and
that the required tools are installed.

## Prerequisites

The following prerequisites are required:

- Microsoft Azure account
- An active Azure subscription
- An Azure RBAC role that allows you to create the resources, such as resource
  groups, container registry, container apps, and databases.
- GitHub account
- UNIX-based operating system (for example, AIX, Linux, macOS, or Solaris)
- Azure CLI 2.90+
- GitHub CLI (`gh`) 2.99+
- Git 2.55+
- GNU Make 3.8+
- gradle 9.7.1+
- java 25+
- Spring Boot 4.1+
- Docker Desktop 4.87+

### Configuration

Follow the instructions in [INITIAL_SETUP](docs/INITIAL_SETUP.md).

## GitHub Actions

| Workflow                    | Purpose                                                         |
|-----------------------------|-----------------------------------------------------------------|
| `build-verify.yml`          | Build, test and check the project, then block on the Sonar gate |
| `release.yml`               | Cut a release: tag it and publish the GitHub Release            |
| `acr-build-push.yml`        | Build the container image and push it to ACR                    |
| `acr-repo-delete.yml`       | Delete an ACR repository, every tag and manifest with it        |
| `plan-create.yml`           | Create the Linux App Service Plan                               |
| `plan-delete.yml`           | Delete the App Service Plan, leaving its resource group alone   |
| `app-svc-create-deploy.yml` | Create the App Service, or deploy the newest image to it        |
| `app-svc-delete.yml`        | Delete the App Service, leaving its ACR repository alone        |

## Development Workflow

See [DEVELOPMENT_WORKFLOW](./docs/DEVELOPMENT_WORKFLOW.md) for guidance on
developing, and cutting a release on this project.

---
Author:  [Rubens Gomes](https://rubensgomes.com/)
