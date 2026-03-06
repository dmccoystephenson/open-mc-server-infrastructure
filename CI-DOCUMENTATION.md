# CI Pipeline Documentation

This document details the automated checks performed by the CI/CD pipeline to ensure code quality, security, and functionality.

## Overview

The CI pipeline runs automatically on:
- Every push to `main` and `develop` branches
- Every pull request to `main`

## CI Jobs

### 1. Validate Code and Configuration

This is the main validation job that performs comprehensive checks across multiple areas:

#### Shell Script Validation
- **Syntax Checking**: Validates that all shell scripts have correct bash syntax using `bash -n`
  - Checks: `up.sh`, `down.sh`, `resources/post-create.sh`, `resources/minecraft-wrapper.sh`
- **ShellCheck Linting**: Runs static analysis on all shell scripts to catch common issues
  - Validates code quality, potential bugs, and best practices
  - Checks all `.sh` files in `resources/` and `scripts/` directories

#### Docker Configuration Validation
- **Dockerfile Validation**: 
  - Verifies Dockerfile exists and contains required `FROM` directive
  - Tests Docker build process up to the `base` stage
- **Docker Compose Validation**:
  - Creates test environment file with valid placeholder values
  - Validates compose configuration syntax using `docker compose config`
  - Ensures all required services and volumes are properly defined

#### Environment Configuration Validation
- **Sample Environment File**: Validates `sample.env` contains all required variables:
  - `MINECRAFT_VERSION`
  - `OPERATOR_UUID`
  - `OPERATOR_NAME`
  - `SERVER_MOTD`

#### Documentation Validation
- **README.md**: Verifies main documentation exists and has correct structure
- **LICENSE**: Ensures license file is present

#### Graceful Shutdown Testing
- **Functionality Test**: Executes comprehensive test of the graceful shutdown mechanism
  - Tests SIGTERM signal handling
  - Verifies proper stop command transmission via FIFO
  - Validates plugin data preservation during shutdown
  - Confirms clean server termination

### 2. Security Scanning

Performs security vulnerability scanning using Trivy:

#### Trivy File System Scan
- **Vulnerability Detection**: Scans entire repository for known security vulnerabilities
- **SARIF Report Generation**: Creates standardized security report format
- **GitHub Security Integration**: Uploads results to GitHub Security tab for review
- **Non-blocking**: Continues pipeline execution even if vulnerabilities are found (informational)

### 3. Test Server Run

Performs end-to-end testing by actually running the Minecraft server in a containerized environment:

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

This test provides real-world verification that the server can actually start, run, and stop correctly in a production-like environment.

## Local Testing

You can run the same validation checks locally using:

```bash
./scripts/ci-local.sh
```

This script mirrors the CI pipeline checks and helps catch issues before submitting changes.

## What Gets Checked

### ✅ Code Quality
- Shell script syntax correctness
- ShellCheck linting compliance
- File permission validation

### ✅ Configuration Integrity
- Docker build process validation
- Docker Compose configuration syntax
- Environment variable completeness

### ✅ Functionality Verification
- Graceful shutdown mechanism testing
- Server wrapper script reliability
- Plugin data preservation
- Full server run testing with actual Minecraft server
- Port availability and network functionality
- Server file system initialization

### ✅ Security Assessment
- Vulnerability scanning
- Security best practices

### ✅ Documentation Standards
- Required documentation presence
- Structure validation

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
| `DEPLOY_AUTH_TOKEN` | Shared secret used to authenticate deploy requests. Leave empty to disable the endpoint entirely. |
| `PLUGINS_DIRECTORY` | Absolute path to the Minecraft plugins directory inside the container (default: `/mcserver/plugins`). |

### Reference Workflow for Spigot Plugin Repositories

A ready-to-use workflow is provided at `.github/workflows/deploy-plugin.yml`. Copy it into your plugin repository and configure the following:

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

### Graceful Shutdown Test Failures
- Check that the wrapper script handles signals correctly
- Verify FIFO communication works properly
- Test shutdown sequence manually

### Environment Configuration Failures
- Ensure all required variables are defined in `sample.env`
- Check variable naming consistency

### Server Run Test Failures
- Check Docker logs for server startup errors
- Verify server initialization completes within timeout (10 minutes)
- Ensure sufficient resources are available for server build
- Check for port conflicts or networking issues
- Review server configuration in test environment

## Performance

The CI pipeline includes multiple workflows:
- **Validation Pipeline**: Typically completes in under 5 minutes
- **Server Run Test**: Takes 15-25 minutes due to Spigot build time
  - First-time Spigot compilation: ~10-15 minutes
  - Server initialization: ~2-5 minutes
  - Testing and cleanup: ~2-3 minutes

The server run test runs in parallel with other checks to provide comprehensive validation without significantly increasing total CI time.