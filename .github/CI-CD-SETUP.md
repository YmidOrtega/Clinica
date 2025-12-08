# CI/CD Configuration Guide

This document explains how to enable optional CI/CD features that are currently disabled.

## Currently Disabled Features

### 1. Docker Hub Image Building

**Status**: Disabled  
**Location**: `.github/workflows/backend-ci.yml` - `docker-build` job

**To Enable**:

1. Create Docker Hub account at https://hub.docker.com
2. Add secrets to your GitHub repository:
   - Go to: Settings → Secrets and variables → Actions
   - Add `DOCKER_USERNAME` (your Docker Hub username)
   - Add `DOCKER_PASSWORD` (Docker Hub access token)
3. Edit workflow file:
   ```yaml
   if: github.event_name == 'push' && github.ref == 'refs/heads/main'
   ```

### 2. SonarCloud Code Quality Analysis

**Status**: Disabled  
**Location**: `.github/workflows/backend-ci.yml` - `code-quality` job

**To Enable**:

1. Create account at https://sonarcloud.io
2. Import your repository
3. Generate a token from SonarCloud
4. Add `SONAR_TOKEN` secret to GitHub repository
5. Update organization and project key in workflow:
   ```yaml
   -Dsonar.projectKey=YourOrg_YourProject
   -Dsonar.organization=yourorg
   ```
6. Change job condition to:
   ```yaml
   if: true  # or remove the line
   ```

### 3. Security Vulnerability Scanning

**Status**: Disabled  
**Location**: `.github/workflows/backend-ci.yml` - `security-scan` job

**To Enable**:

1. Ensure GitHub Advanced Security is enabled for your repository
2. Change job condition to:
   ```yaml
   if: true  # or remove the line
   ```

The workflow already has the required permissions configured:
```yaml
permissions:
  contents: read
  security-events: write
  actions: read
```

## Currently Active Features

✅ **Build and Test** - Compiles and tests all microservices  
✅ **Test Coverage Report** - Generates JaCoCo coverage reports  
✅ **Matrix Strategy** - Tests all 8 active microservices in parallel

## Adding New Services to CI/CD

When a new service is ready (e.g., billing-service):

1. **Build & Test Matrix** (line ~25):
   ```yaml
   strategy:
     matrix:
       service:
         - existing-services...
         - new-service-name
   ```

2. **Docker Build Matrix** (line ~120):
   ```yaml
   strategy:
     matrix:
       service:
         - existing-services...
         - new-service-name
   ```

## Troubleshooting

### Job Failing: "Username and password required"
- Docker Hub secrets are not configured
- Follow steps in section 1 above

### Job Failing: "Resource not accessible by integration"
- Security scanning requires Advanced Security
- Or disable the job by setting `if: false`

### Job Failing: "SONAR_TOKEN not found"
- SonarCloud token not configured
- Follow steps in section 2 above

## Pipeline Performance

Current setup:
- **Parallel execution**: All 8 services build simultaneously
- **Caching**: Maven dependencies cached across runs
- **Selective runs**: Only triggers on BackEnd-Clinica changes

Expected run time:
- Build & Test: ~3-5 minutes per service (parallel)
- Total pipeline: ~5-7 minutes (if all jobs enabled)

---

**Last Updated**: December 2025  
**Maintained by**: Development Team
