# 🏥 Clínica - Healthcare Management System

> **Complete clinic management system** built with microservices architecture, featuring patient management, appointments, medical records, billing, and AI-powered assistance.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)]()
[![Status](https://img.shields.io/badge/Status-Active-blue)]()

---

## 📖 Overview

**Clínica** is a modern healthcare management platform built with microservices architecture, designed for comprehensive clinic operations including patient care, appointments scheduling, medical records management, and billing.

**Key Benefits:**
- 🏗️ Scalable microservices architecture with service discovery
- 🔐 Enterprise-grade security with JWT authentication
- 🤖 AI-powered medical assistant
- 📊 Real-time monitoring and health checks
- 🚀 Containerized deployment with Docker

> ⚠️ **Note**: This is a development/educational project. Not certified for production healthcare environments without proper compliance review.

---

## ✨ Key Features

- 👥 **Patient Registry**: Patient identity, contact, affiliation and status with full change history, optimistic concurrency and least-privilege database access
- 📅 **Appointments**: Multi-professional scheduling system with conflict detection
- 🏥 **Admissions**: Patient admission and discharge workflow management
- 🤖 **AI Assistant**: Gemini-powered medical assistance and consultation support
- 👔 **Clients Management**: Healthcare providers and insurance companies integration
- 📦 **Suppliers**: Medical supplies and pharmaceutical inventory management
- 💳 **Billing**: Invoice generation and payment processing (🚧 In Development)
- 🔐 **Security**: JWT-based authentication with RSA-256 encryption
- 📊 **Monitoring**: Real-time health checks with Eureka service discovery
- 🗄️ **Multi-Database**: PostgreSQL and MySQL support with Flyway migrations

---

## 🚀 Quick Start

### Prerequisites
- ☕ **Java 21** or higher
- 🔧 **Maven 3.6+**
- 🐳 **Docker & Docker Compose**
- 🔑 **Git**

### Installation

```bash
# Clone repository
git clone https://github.com/YmidOrtega/Clinica.git
cd Clinica/BackEnd-Clinica

# Configure environment variables
cp .env.example .env
# Edit .env with your configurations (JWT secrets, database passwords, etc.)

# Build all microservices
mvn clean install -DskipTests

# Start services with Docker Compose
docker-compose up -d

# Verify services are running
docker-compose ps
```

### Access Points
- 🌐 **API Gateway**: http://localhost:8080
- 🔍 **Eureka Dashboard**: http://localhost:8761
- 📊 **Redis Insight**: http://localhost:8002
- 📚 **Swagger UI**: http://localhost:{service-port}/swagger-ui.html

---

## 📦 Microservices

| Service | Port | Database | Technology | Status |
|---------|------|----------|------------|--------|
| **Eureka Server** | 8761 | - | Service Discovery | ✅ Active |
| **API Gateway** | 8080 | PostgreSQL | Spring Cloud Gateway | ✅ Active |
| **Auth Service** | 8086 | MySQL | Spring Security + JWT | ✅ Active |
| **Patient Service** | 8081 | MySQL | Spring Boot | ✅ Active |
| **Admissions Service** | 8083 | PostgreSQL | Spring Boot | ✅ Active |
| **Clients Service** | 8087 | MySQL | Spring Boot | ✅ Active |
| **Suppliers Service** | 8085 | MySQL | Spring Boot | ✅ Active |
| **AI Assistant Service** | 8084 | PostgreSQL | Spring AI + Gemini | ✅ Active |
| **Billing Service** | 8082 | MySQL | Spring Boot | 🚧 In Development |

---

## 🌐 API Examples

```bash
# Check system health
curl http://localhost:8080/actuator/health

# Authenticate (get JWT token)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "your-password"}'

# Search patients by document (requires authentication)
curl -X POST http://localhost:8080/api/v1/patients/search \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"document": {"type": "CEDULA_DE_CIUDADANIA", "number": "1098765432"}}'
```

**API Documentation**: Each service exposes Swagger UI at `http://localhost:{port}/swagger-ui.html`

---

## 🏗️ Architecture

```
┌─────────────────┐
│  External Apps  │
└────────┬────────┘
         │
┌────────▼────────┐
│  API Gateway    │ :8080
│  (Rate Limit,   │
│   Load Balance) │
└────────┬────────┘
         │
┌────────▼────────┐
│ Eureka Server   │ :8761
│ (Discovery)     │
└────────┬────────┘
         │
    ┌────┴─────────────────────┐
    │                          │
┌───▼───────┐          ┌───────▼─────┐
│ Services  │◄────────►│   Redis     │
│  Mesh     │          │   Cache     │
└───┬───────┘          └─────────────┘
    │
┌───▼──────────┐
│  Databases   │
│  MySQL +     │
│  PostgreSQL  │
└──────────────┘
```

---

## 📁 Project Structure

```
Clinica/
├── BackEnd-Clinica/
│   ├── admissions-service/
│   ├── ai-assistant-service/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── billing-service/      # 🚧 In Development
│   ├── clients-service/
│   ├── eureka-service/
│   ├── patient-service/
│   ├── suppliers-service/
│   ├── docker-compose.yml
│   ├── pom.xml
│   └── .env
└── FrontEnd-Clinica/          # (Future frontend application)
```

---

## 🔧 Tech Stack

### Backend
- ☕ **Java 21**
- 🍃 **Spring Boot 3.5.7**
- ☁️ **Spring Cloud 2025.0.0**
- 🔐 **Spring Security + JWT (RSA-256)**
- 🤖 **Spring AI + Google Gemini**

### Databases
- 🐘 **PostgreSQL 16** (Gateway, AI Assistant, Admissions)
- 🐬 **MySQL 8.0** (Auth, Patients, Clients, Suppliers)
- 🗄️ **Flyway** (Database migrations)

### Infrastructure
- 🔍 **Netflix Eureka** (Service Discovery)
- 🚪 **Spring Cloud Gateway** (API Gateway)
- 🔴 **Redis 7** (Distributed Cache)
- 🐳 **Docker & Docker Compose**
- 📊 **Spring Boot Actuator** (Monitoring)

### Tools & Libraries
- 🔨 **Lombok** (Boilerplate reduction)
- 🗺️ **MapStruct** (Object mapping)
- 💪 **Resilience4j** (Circuit breaker)
- 📝 **OpenAPI/Swagger** (API documentation)
- 🧪 **JUnit 5 + Mockito** (Testing)

---

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run tests for specific service (shared libraries must be installed first)
cd BackEnd-Clinica
mvn install -f libs/clinica-commons-web/pom.xml
mvn install -f libs/clinica-commons-security/pom.xml
cd patient-service
mvn verify   # unit tests + integration tests with Testcontainers (requires Docker)

# Run with coverage report
mvn clean test jacoco:report

# Run specific test class
mvn -Dtest=PatientTest test
```

---

## 🐳 Docker Commands

```bash
# Start all services
docker-compose up -d

# Stop services
docker-compose down

# View logs
docker-compose logs -f [service-name]

# Rebuild specific service
docker-compose build [service-name]
docker-compose up -d [service-name]

# Check services status
docker-compose ps
```

---

## 🔐 Security Features

- ✅ JWT authentication with RSA-256 encryption
- ✅ Public/Private key infrastructure
- ✅ Role-based access control (RBAC)
- ✅ Secure actuator endpoints
- ✅ Password encryption with BCrypt
- ✅ Token refresh mechanism
- ✅ CORS configuration
- ✅ Request rate limiting

---

## 📊 Monitoring & Health

- **Actuator Endpoints**: `/actuator/health`, `/actuator/info`, `/actuator/metrics`
- **Eureka Dashboard**: Real-time service registry at http://localhost:8761
- **Redis Insight**: Cache monitoring at http://localhost:8002
- **Service Discovery**: Automatic registration and health checks

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

**Commit Convention**: Follow [Conventional Commits](https://www.conventionalcommits.org/)
- `feat:` New features
- `fix:` Bug fixes
- `docs:` Documentation changes
- `refactor:` Code refactoring
- `test:` Test additions/updates

---

## 📄 License

This project is proprietary software. All rights reserved.

---

## 🙏 Acknowledgments

- Spring Framework community
- Netflix OSS for Eureka
- Google for Gemini AI
- Open source contributors

---

<div align="center">

**Built with ☕ Java and 📈 Financial Engineering**

**by [Ymid Ortega](https://github.com/YmidOrtega)**

[![GitHub](https://img.shields.io/badge/GitHub-YmidOrtega-181717?logo=github)](https://github.com/YmidOrtega)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-Connect-0077B5?logo=linkedin)](https://linkedin.com/in/ymidortega)

*If you found this project useful, consider giving it a ⭐!*

**© 2024 Ymid Ortega. All Rights Reserved.**

</div>
