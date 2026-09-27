# GitHub Actions Workflows

Este directorio contiene los workflows de CI/CD del proyecto.

## Workflows Disponibles

### 1. Backend CI/CD (`backend-ci.yml`)
Ejecuta en push o PR a `main`/`develop` cuando hay cambios en `BackEnd-Clinica/`:
- `libraries`: prueba e instala `clinica-commons-web`, `clinica-commons-openbao`, `clinica-commons-security` y `clinica-commons-documents`, en ese orden, y las comparte como artefacto con los demás jobs
- `services`: `mvn verify` (unitarias e integración con Testcontainers) de los servicios rehechos (`auth`, `patient`, `clinical-history`, `contracting`, `practitioners`, `admissions`, `billing`, `ai-assistant`) `api-gateway` y `eureka-service`; un fallo rompe el build
- `e2e`: cada noche y a mano (`workflow_dispatch`) levanta el stack de Docker Compose con una réplica por servicio y corre los E2E de cada servicio (auth, clínica, contratación, profesionales, admisiones, facturación con simuladores de DIAN y MUV, y el asistente con el simulador del modelo local), `openbao-e2e.sh` y, con un step-up de 20 s, `gateway-e2e.sh`; si falla sube los logs del stack
- SonarCloud, imágenes Docker y Trivy siguen deshabilitados hasta configurar sus secretos

### 2. MinIO image (`minio-image.yml`)
MinIO dejó de publicar imágenes en quay.io y Docker Hub, así que el almacenamiento de adjuntos de la historia clínica usa una imagen propia:
- compila `minio` y `mc` desde las fuentes AGPL en las versiones fijadas en `BackEnd-Clinica/platform/minio/Dockerfile`
- publica `ghcr.io/ymidortega/clinica-minio:<release>` para amd64 y arm64
- corre al cambiar ese directorio en `main` o a mano; `backend-ci.yml` inicia sesión en GHCR con `GITHUB_TOKEN` para bajarla
- para usarla en local sin iniciar sesión, el paquete debe ser público en GitHub

### 3. Frontend CI/CD (`frontend-ci.yml`)
Ejecuta automáticamente en push o PR a `main`/`develop` cuando hay cambios en `FrontEnd-Clinica/`:
- ✅ Build con pnpm
- ✅ Tests (cuando estén configurados)
- ✅ Auditoría de performance con Lighthouse
- ✅ Deploy preview en Netlify (PRs)
- ✅ Deploy production en Netlify (main)

### 4. CodeQL Analysis (`codeql.yml`)
Análisis de seguridad del código:
- Ejecuta semanalmente los lunes
- Escanea Java y JavaScript
- Detecta vulnerabilidades de seguridad

### 5. Dependency Review (`dependency-review.yml`)
Revisa dependencias en Pull Requests:
- Detecta vulnerabilidades en dependencias
- Verifica licencias incompatibles

## Secretos Requeridos

Para que los workflows funcionen completamente, configura estos secretos en GitHub:

### Backend
- `SONAR_TOKEN`: Token de SonarCloud
- `DOCKER_USERNAME`: Usuario de Docker Hub
- `DOCKER_PASSWORD`: Password/Token de Docker Hub

### Frontend
- `NETLIFY_AUTH_TOKEN`: Token de autenticación de Netlify
- `NETLIFY_SITE_ID`: ID del sitio en Netlify

## Configuración Inicial

1. **SonarCloud** (opcional):
   - Crear cuenta en https://sonarcloud.io
   - Vincular repositorio
   - Generar token y añadir a GitHub Secrets

2. **Docker Hub** (opcional):
   - Crear cuenta en https://hub.docker.com
   - Crear token de acceso
   - Añadir credenciales a GitHub Secrets

3. **Netlify** (opcional):
   - Crear cuenta en https://netlify.com
   - Crear nuevo sitio
   - Generar token y obtener Site ID
   - Añadir a GitHub Secrets

## Deshabilitando Jobs Opcionales

Si no deseas usar algún servicio, los workflows tienen `continue-on-error: true` en:
- SonarCloud analysis
- Docker builds
- Netlify deploys
- Lighthouse audits

Estos fallarán silenciosamente sin afectar el build principal.
