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

### ✅ Security Assessment
- Vulnerability scanning
- Security best practices

### ✅ Documentation Standards
- Required documentation presence
- Structure validation

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

## Performance

The simplified CI pipeline typically completes in under 5 minutes, providing fast feedback while maintaining comprehensive validation coverage.