# Azure App Service

[![jdk](https://img.shields.io/badge/jdk-25-0969da?logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![gradle](https://img.shields.io/badge/gradle-9.8%2B-0969da?logo=gradle)](https://gradle.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1%2B-0969da?logo=spring)](https://spring.io/projects/spring-boot#overview)
[![GitHub](https://img.shields.io/badge/GitHub-Actions-0969da?logo=github+actions)](https://github.com/features/actions)
[![Microsoft](https://img.shields.io/badge/Microsoft-Azure-0969da)](https://azure.microsoft.com/en-us)
[![AI](https://img.shields.io/badge/AI-Assisted-d29922?logo=claude+code)](https://github.com/rubensgomes-org/azure-app-svc/blob/main/AI_DISCLAIMER.md)
[![license](https://img.shields.io/badge/license-MIT-1a7f37)](https://github.com/rubensgomes-org/azure-app-svc/blob/main/LICENSE)

Spring Boot demo deployed to Azure App Service.

---

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

## License

This project is licensed under the
[MIT License](https://github.com/rubensgomes-org/azure-app-svc/blob/main/LICENSE).

## Links

- [GitHub Project](https://github.com/rubensgomes-org/azure-app-svc)
- [Azure App Service](docs/APP_SERVICE.md)
- [Azure Commands](docs/AZ_CMDS.md)
- [Azure Consumption Pricing](docs/PRICING.md)
- [Development Workflow](docs/DEVELOPMENT_WORKFLOW.md)
- [Initial Setup](docs/INITIAL_SETUP.md)

---
Author:  [Rubens Gomes](https://rubensgomes.com/)
