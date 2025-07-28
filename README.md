Sistema de Gestión de Clínica Médica

📋 Descripción del Proyecto

Este es un sistema completo de gestión de clínica médica construido con arquitectura de microservicios. El sistema incluye un backend desarrollado en Spring Boot con múltiples microservicios y un frontend desarrollado en Astro.

Backend - Microservicios

El backend está compuesto por los siguientes microservicios:

    eureka-service: Servidor de descubrimiento de servicios
    api-gateway: Gateway principal para enrutamiento de peticiones
    auth-service: Servicio de autenticación y autorización
    patient-service: Gestión de pacientes
    billing-service: Gestión de facturación
    admissions-service: Gestión de admisiones hospitalarias
    ai-assistant-service: Asistente inteligente con IA
    suppliers-service: Gestión de proveedores
    clients-service: Gestión de clientes/aseguradoras

Frontend

    Astro Framework: Interface de usuario moderna y reactiva

Bases de Datos

El sistema utiliza múltiples bases de datos especializadas:

    MySQL 8.0: Para patient-db, billing-db, suppliers-db, auth-db, clients-db
    PostgreSQL 16: Para admissions-db, ai-assistant-db
    Redis 7: Para caché y sesiones

🛠️ Tecnologías Utilizadas
Backend

    Java 21
    Spring Boot 3.5.0
    Spring Cloud 2025.0.0
    Maven: Gestión de dependencias
    Lombok: Reducción de código boilerplate
    MapStruct: Mapeo de objetos
    SpringDoc OpenAPI: Documentación de APIs
    Docker: Containerización

Frontend

    Astro: Framework de desarrollo web
    pnpm: Gestor de paquetes

⚙️ Variables de Entorno Requeridas
Bases de Datos MySQL

Patient Database:

PATIENT_DB_ROOT_PASSWORD=tu_password_root  
PATIENT_DB_NAME=patient_db  
PATIENT_DB_USER=patient_user  
PATIENT_DB_PASSWORD=tu_password

Billing Database:

BILLING_DB_ROOT_PASSWORD=tu_password_root  
BILLING_DB_NAME=billing_db  
BILLING_DB_USER=billing_user  
BILLING_DB_PASSWORD=tu_password

Suppliers Database:

SUPPLIERS_DB_ROOT_PASSWORD=tu_password_root  
SUPPLIERS_DB_NAME=suppliers_db  
SUPPLIERS_DB_USER=suppliers_user  
SUPPLIERS_DB_PASSWORD=tu_password

Auth Database:

AUTH_DB_ROOT_PASSWORD=tu_password_root  
AUTH_DB_NAME=auth_db  
AUTH_DB_USER=auth_user  
AUTH_DB_PASSWORD=tu_password

Clients Database:

CLIENTS_DB_ROOT_PASSWORD=tu_password_root  
CLIENTS_DB_NAME=clients_db  
CLIENTS_DB_USER=clients_user  
CLIENTS_DB_PASSWORD=tu_password

Bases de Datos PostgreSQL

Admissions Database:

ADMISSIONS_DB_NAME=admissions_db  
ADMISSIONS_DB_USER=admissions_user  
ADMISSIONS_DB_PASSWORD=tu_password

AI Assistant Database:

AI_ASSISTANT_DB_NAME=ai_assistant_db  
AI_ASSISTANT_DB_USER=ai_user  
AI_ASSISTANT_DB_PASSWORD=tu_password

Redis y JWT

Redis:

REDIS_PASSWORD=tu_redis_password

JWT Configuration:

JWT_SECRET=tu_jwt_secret_key  
JWT_EXPIRATION=86400000

🚀 Instalación y Ejecución
Prerrequisitos

    Docker y Docker Compose
    Java 21 (para desarrollo local)
    Maven (para desarrollo local)
    Node.js y pnpm (para el frontend)

Pasos de Instalación

    Clonar el repositorio:

    git clone https://github.com/YmidOrtega/Clinica.git  
    cd Clinica

    Configurar variables de entorno:
    Crear un archivo .env en la carpeta BackEnd-Clinica con todas las variables mencionadas anteriormente.

    Ejecutar con Docker Compose:

    cd BackEnd-Clinica  
    docker-compose up -d

    Configurar Frontend:

    cd FrontEnd-Clinica  
    pnpm install  
    pnpm dev

🌐 Puertos y Endpoints
Servicios Backend

    Eureka Server: http://localhost:8761
    API Gateway: http://localhost:8080
    Patient Service: http://localhost:8081
    Suppliers Service: http://localhost:8085
    Auth Service: http://localhost:8086
    Clients Service: http://localhost:8087

Bases de Datos

    Patient DB: localhost:3307
    Billing DB: localhost:3308
    Admissions DB: localhost:3309
    AI Assistant DB: localhost:3310
    Suppliers DB: localhost:3311
    Auth DB: localhost:3312
    Clients DB: localhost:3313
    Redis: localhost:6379
    Redis Insight: http://localhost:8001

Frontend

    Astro Dev Server: http://localhost:4321

📚 Documentación de APIs

El sistema incluye documentación automática de APIs con Swagger/OpenAPI:

    Swagger UI: http://localhost:{port}/swagger-ui.html
    API Docs: http://localhost:{port}/v3/api-docs
    Documentación centralizada de todos los microservicios disponible a través del API Gateway

Endpoints de Monitoreo

Cada servicio expone endpoints de Actuator para monitoreo:

    Health: /actuator/{service}/health
    Metrics: /actuator/{service}/metrics
    Info: /actuator/{service}/info

🔧 Desarrollo
Backend

Para desarrollo local de los microservicios:

cd BackEnd-Clinica  
mvn clean install

Frontend README.md:35-44

cd FrontEnd-Clinica  
pnpm install          # Instalar dependencias  
pnpm dev              # Servidor de desarrollo  
pnpm build            # Build de producción  
pnpm preview          # Preview del build

🏥 Funcionalidades del Sistema

    Gestión de Pacientes: Registro, consulta y administración de pacientes
    Sistema de Facturación: Gestión completa de facturación médica
    Control de Admisiones: Gestión de ingresos y egresos hospitalarios
    Asistente IA: Asistente inteligente para apoyo médico
    Gestión de Proveedores: Administración de proveedores médicos
    Gestión de Aseguradoras: Control de clientes y aseguradoras
    Autenticación y Autorización: Sistema de seguridad robusto
    Arquitectura de Microservicios: Escalabilidad y mantenibilidad
    Caché Redis: Optimización de rendimiento
    Service Discovery: Descubrimiento automático de servicios

Notes

Este proyecto implementa una arquitectura de microservicios completa para un sistema hospitalario, utilizando tecnologías modernas como Spring Boot 3.5.0, Java 21, y Astro para el frontend. El sistema está diseñado para ser escalable y mantenible, con cada servicio especializado en una funcionalidad específica del dominio médico. La configuración con Docker Compose facilita el despliegue y desarrollo del sistema completo. Algunos servicios (billing, admissions, ai-assistant) están comentados en el docker-compose, ya que están en desarrollo o deshabilitados temporalmente.

Wiki pages you might want to explore:

    Database Architecture (YmidOrtega/Clinica)
    Configuration and Operations (YmidOrtega/Clinica)
    [![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/YmidOrtega/Clinica)
