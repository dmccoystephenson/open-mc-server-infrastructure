# CI Pipeline Documentation

This document details the automated checks performed by the CI/CD pipeline to ensure code quality, security, and functionality.

## Overview

The CI pipeline runs automatically on:
- Every push to `main` and `develop` branches
- Every pull request to `main`

## CI Jobs

The pipeline (`.github/workflows/ci.yml`) is composed of several parallel jobs.
The end-to-end server run lives in a separate workflow
(`.github/workflows/test-server-run.yml`).

### 1. Validate Code and Configuration (`validate`)

The main per-PR validation job. It runs the following steps in order:

#### Shell Script Validation
- **Syntax Checking** (`bash -n`): `up.sh`, `down.sh`, `upgrade.sh`,
  `rollback.sh`, `trigger-backup.sh`, `resources/post-create.sh`.
- **ShellCheck Linting**: the same six scripts plus everything under
  `scripts/*.sh`.

#### Docker Configuration Validation
- **Dockerfile Validation**: verifies the root `Dockerfile` exists and contains
  a `FROM` directive, then builds the `base` stage to confirm syntax.
- **Docker Compose Validation**: writes a temporary `.env` with placeholder
  values, then runs `docker compose config` to verify the compose file parses
  and every service/volume reference resolves.

#### Environment Configuration Validation
Asserts `sample.env` declares each of these keys (`grep -q KEY=`):
`MINECRAFT_VERSION`, `OPERATOR_UUID`, `OPERATOR_NAME`, `SERVER_MOTD`.

#### Java Module Builds and Tests
Sets up JDK 21 (Temurin), then runs `./gradlew build` and `./gradlew test`
for each Spring Boot module that has its own Gradle project:
- `web-app`
- `minecraft-wrapper`
- `agent-manager`
- `alert-manager`
- `backup-manager`

`scripts/ci-local.sh` runs `./gradlew test` for this same list — keep the two
in sync when a module is added or removed.

#### Documentation Validation
- Confirms `README.md` exists and contains the expected H1
  (`# Open Minecraft Server Infrastructure`).
- Confirms `LICENSE` is present.

#### Helm Chart Linting
- Sets up Helm and runs `helm lint helm/omcsi --set secrets.rconPassword=ci
  --set secrets.adminPassword=ci` against the chart. The two `--set` values are
  required: `templates/secret.yaml` wraps them in `required` and they default to
  `""` in `values.yaml`, so lint fails without them.

### 2. Helm Unit Tests (`helm-unit-test`)

A separate job that installs the
[`helm-unittest`](https://github.com/helm-unittest/helm-unittest) plugin and
runs `helm unittest helm/omcsi`. The test suites live under
`helm/omcsi/tests/` and cover NetworkPolicies, PodDisruptionBudgets,
ServiceMonitors, security contexts, and per-service Deployment/Service
templates.

### 3. Helm Deploy Smoke Test (`helm-deploy-test`)

Spins up a `kind` cluster, installs the chart with minimal required values,
and verifies the release creates the expected Kubernetes objects and key
deployment fields. Because CI does not build/load the service images into
`kind` and installs with `--wait=false`, this job does **not** prove the
workloads become Ready/healthy; it catches install-time/template regressions
that lint and unit tests miss (for example selector mismatches, missing PVCs,
or probe/init-container wiring mistakes).

### 4. Terraform Validate (`terraform-validate`)

Runs `terraform fmt -check`, `terraform init -backend=false`, and
`terraform validate` for each Terraform stack under `terraform/`
(`aws`, `linode`, `existing-cluster`, and `hetzner`) to catch
formatting issues, syntax errors, and provider errors before they
hit a real deployment.

### 5. Security Scanning (`security-scan`)

Performs security vulnerability scanning using Trivy:

- **Vulnerability Detection**: Scans entire repository for known security vulnerabilities
- **SARIF Report Generation**: Creates standardized security report format
- **GitHub Security Integration**: Uploads results to GitHub Security tab for review
- **Non-blocking**: Continues pipeline execution even if vulnerabilities are found (informational)

### 6. End-to-End Server Run (`test-server-run.yml`)

A separate workflow that performs end-to-end testing by actually running
the Minecraft server in a containerized environment.

#### Server Build and Startup
- **Docker Image Build**: Builds the complete Minecraft server Docker image with Spigot
- **Server Initialization**: Starts the server with test configuration and waits for full initialization
- **Startup Verification**: Monitors server logs to confirm successful startup sequence completion

#### Functionality Testing
- **Port Verification**: Confirms server is listening on Minecraft port (25565) and RCON port (25575)
- **File System Check**: Verifies critical server files are created (server.properties, world directory, etc.)
- **Container Health**: Validates the Docker container is running properly

#### Graceful Shutdown Testing
- **Shutdown Sequence**: Tests proper server shutdown using Docker Compose
- **Log Validation**: Confirms graceful shutdown signals are processed correctly
- **Resource Cleanup**: Ensures all resources are properly cleaned up after testing

This workflow provides real-world verification that the server can actually
start, run, and stop correctly in a production-like environment.

## Local Testing

You can run the same validation checks locally using:

```bash
./scripts/ci-local.sh
```

This script mirrors the CI pipeline checks and helps catch issues before submitting changes.
It validates the compose file against a `mktemp` copy of `sample.env` rather than your
`.env`, so your real credentials are left untouched. Because it now runs the test suite
for all five Gradle modules, expect it to take several minutes on a cold Gradle cache.

## What Gets Checked

### ✅ Code Quality
- Shell script syntax (`bash -n`)
- ShellCheck linting compliance
- Java module compilation and unit tests for `web-app`,
  `minecraft-wrapper`, `agent-manager`, `alert-manager`, and `backup-manager`

### ✅ Configuration Integrity
- Dockerfile parses and the `base` stage builds
- `docker compose config` resolves the full stack
- `sample.env` declares all required keys
- Terraform stacks pass `terraform validate`

### ✅ Kubernetes / Helm
- `helm lint` against `helm/omcsi`
- `helm unittest` covering NetworkPolicies, PDBs, ServiceMonitors,
  security contexts, and per-service templates
- Smoke deploy of the chart into a `kind` cluster

### ✅ End-to-End (separate workflow)
- Real Spigot build + server boot
- Port availability (25565, 25575) and file-system initialization
- Graceful shutdown via Compose

### ✅ Security Assessment
- Trivy vulnerability scanning
- Results uploaded to the GitHub Security tab

### ✅ Documentation Standards
- `README.md` exists with the expected H1
- `LICENSE` is present

## CI/CD Plugin Deployment

The `minecraft-wrapper` exposes a secure HTTP endpoint that allows automated deployment of Spigot/Paper plugin JARs from GitHub Actions directly into a running omcsi instance.

### Endpoint

```
POST /api/plugins/deploy
Authorization: Bearer <DEPLOY_AUTH_TOKEN>
Content-Type: multipart/form-data

pluginName=<existing-plugin-filename.jar>
file=@<path-to-new-jar>
```

- **`pluginName`** – The filename of the plugin JAR already installed on the server (e.g. `MyPlugin.jar`).  The new file is written under that same name in the configured plugins directory.
- **`file`** – The new JAR file to deploy.

#### HTTP Responses

| Code | Meaning |
|------|---------|
| `200 OK` | Plugin deployed successfully |
| `400 Bad Request` | Invalid `pluginName` or non-JAR file |
| `401 Unauthorized` | Missing, wrong, or unconfigured token |
| `500 Internal Server Error` | I/O failure during file replacement |

### Configuration

Set the following variables in your omcsi `.env` file (see `sample.env` for reference):

| Variable | Description |
|----------|-------------|
| `DEPLOY_AUTH_TOKEN` | Shared secret used to authenticate deploy requests. Leave empty to disable the endpoint entirely. The web app is given the same value and presents it when forwarding a dashboard world upload to the wrapper, so leaving it empty also disables world upload from the dashboard. |
| `PLUGINS_DIRECTORY` | Absolute path to the Minecraft plugins directory inside the container (default: `/mcserver/plugins`). |

### Reference Workflow for Spigot Plugin Repositories

A ready-to-use workflow is provided at `docs/github-actions/deploy-plugin.yml`. Copy it into your plugin repository at `.github/workflows/deploy-plugin.yml` and configure the following:

**Repository Secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|--------|-------|
| `OMCSI_URL` | Base URL of your omcsi instance, e.g. `https://mc.example.com:8092` |
| `OMCSI_DEPLOY_TOKEN` | The same value as `DEPLOY_AUTH_TOKEN` in your omcsi `.env` |

**Repository Variables** (Settings → Secrets and variables → Actions):

| Variable | Example | Description |
|----------|---------|-------------|
| `PLUGIN_JAR_NAME` | `MyPlugin.jar` | Filename of the plugin to replace on the server |
| `DEPLOY_BRANCH` | `main` | Branch that triggers a deployment |
| `BUILD_COMMAND` | `./gradlew build` | Command used to build the JAR (optional) |
| `JAR_PATH` | `build/libs/*.jar` | Glob path to the built JAR (optional) |

The workflow:
1. Builds the plugin JAR using Gradle (or a custom build command)
2. Locates the built JAR (excluding `-sources` / `-javadoc` artefacts)
3. Sends it to the omcsi deploy endpoint via `curl`
4. Fails the workflow if deployment is rejected

## CI Pipeline Benefits

1. **Early Issue Detection**: Catches problems before they reach production
2. **Consistent Quality**: Ensures all code meets the same standards
3. **Security Awareness**: Identifies potential vulnerabilities
4. **Functionality Assurance**: Validates core features work correctly
5. **Documentation Compliance**: Maintains documentation standards

## Troubleshooting CI Failures

### ShellCheck Failures
- Review ShellCheck warnings and fix syntax issues
- Use `shellcheck <filename>` locally to debug

### Docker Build Failures
- Ensure Dockerfile syntax is correct
- Verify all required files are present
- Test `docker build` locally

### Java Module Build / Test Failures
- Run `./gradlew build` and `./gradlew test` locally inside the affected
  module (`web-app`, `minecraft-wrapper`, `agent-manager`, `alert-manager`,
  or `backup-manager`) to reproduce.
- Confirm you're on JDK 21 — older or newer JDKs may produce class-version
  errors that look unrelated.

### Helm Lint / Unit Test Failures
- `helm lint helm/omcsi --set secrets.rconPassword=ci --set secrets.adminPassword=ci`
  reproduces lint output locally. Omitting the `--set` flags produces a
  `secrets.rconPassword is required` error rather than real lint findings.
- `helm unittest helm/omcsi` reproduces template assertions; look at
  `helm/omcsi/tests/*.yaml` for which template a failing assertion belongs to.

### Helm Deploy Smoke Test Failures
- Usually a selector mismatch, missing required value, or PVC binding
  failure on the kind cluster. Render with `helm template omcsi helm/omcsi
  --values <your-overrides>.yaml` to see what would be applied.

### Environment Configuration Failures
- Ensure all required variables are defined in `sample.env`
- Check variable naming consistency

### Server Run Test Failures
- Check Docker logs for server startup errors
- Verify server initialization completes within timeout (10 minutes)
- Ensure sufficient resources are available for server build
- Check for port conflicts or networking issues
- Review server configuration in test environment

### Graceful Shutdown Test Failures (end-to-end workflow)
- Check that `minecraft-wrapper` handles signals correctly
- Verify FIFO communication works properly between the wrapper and the
  Spigot process
- Test the shutdown sequence manually with
  `scripts/test-docker-graceful-shutdown.sh`

## Performance

The CI pipeline includes multiple workflows:
- **Validation Pipeline**: Typically completes in under 5 minutes
- **Server Run Test**: Takes 15-25 minutes due to Spigot build time
  - First-time Spigot compilation: ~10-15 minutes
  - Server initialization: ~2-5 minutes
  - Testing and cleanup: ~2-3 minutes

The server run test runs in parallel with other checks to provide comprehensive validation without significantly increasing total CI time.