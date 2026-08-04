# billing-service — Hoja de Ruta de Implementación

## Decisiones de diseño

- Precios base del Portfolio permanecen en `clients-service`; billing-service gestiona manuales y overrides.
- Integración con la **DIAN** aplazada para fase futura (campos reservados en Invoice).
- **RIPS** irán en `reports-service` (microservicio separado aún por crear).
- Puerto: **8082** · Base de datos: **MySQL billing-db (3308)**

## Metodología fija para cada módulo

> Siempre en este orden — nunca saltar pasos:
>
> 1. Tablas SQL (migración Flyway)
> 2. Entidades JPA (`@Entity`)
> 3. Repositorios (`JpaRepository`)
> 4. DTOs + Mappers (MapStruct)
> 5. Servicios (lógica de negocio)
> 6. Controladores REST + seguridad (`@PreAuthorize`)
> 7. Feign clients necesarios

---

## Setup inicial

- [x] Crear BILLING_ROADMAP.md
- [x] Habilitar `billing-service` en `pom.xml` raíz
- [x] Habilitar `billing-db` en `docker-compose.yml`
- [x] Agregar dependencia `spring-cloud-starter-netflix-eureka-client` al `pom.xml` del servicio
- [x] Corregir `application-dev.properties` (datasource vacía)
- [x] Limpiar migración `V1.0` (tablas incorrectas del diseño original)

---

## Fase 1 — Core de Cobro

> Prerequisito: Setup inicial completo

### Módulo `config` — Configuración de facturación

**Tablas SQL**
- [x] `V2.0` — Crear tablas: `billing_configuration`, `tax_configuration`, `dian_resolution`

**Entidades JPA**
- [x] `BillingConfiguration`
- [x] `TaxConfiguration`
- [x] `DianResolution`

**Repositorios**
- [x] `BillingConfigurationRepository`
- [x] `TaxConfigurationRepository`
- [x] `DianResolutionRepository`

**DTOs + Mappers**
- [x] `BillingConfigurationRequestDto` / `BillingConfigurationResponseDto` + mapper
- [x] `TaxConfigurationRequestDto` / `TaxConfigurationResponseDto` + mapper
- [x] `DianResolutionRequestDto` / `DianResolutionResponseDto` + mapper

**Servicios**
- [x] `BillingConfigurationService` (CRUD + get activo)
- [x] `TaxConfigurationService`
- [x] `DianResolutionService` (incrementar consecutivo)

**Controladores**
- [x] `BillingConfigurationController` — solo `SUPER_ADMIN`
- [x] `TaxConfigurationController` — solo `SUPER_ADMIN`
- [x] `DianResolutionController` — solo `SUPER_ADMIN`

**Seguridad + infraestructura**
- [x] Agregar `spring-boot-starter-security` al pom.xml (SecurityConfig permisivo temporal)
- [x] Agregar `io.jsonwebtoken:jjwt` + implementar `JwtTokenProvider` + `JwtAuthenticationFilter` + `CustomUserDetails` + `@EnableMethodSecurity`
      (se usa jjwt, no `com.auth0:java-jwt`, por consistencia con los otros cinco servicios consumidores)

---

### Módulo `pricing` — Manuales y tarifas de cobro

**Tablas SQL**
- [x] `V3.0` — Crear tablas: `price_manual`, `price_manual_item`, `client_price_override`

**Entidades JPA**
- [x] `PriceManual`
- [x] `PriceManualItem`
- [x] `ClientPriceOverride`

**Repositorios**
- [x] `PriceManualRepository`
- [x] `PriceManualItemRepository`
- [x] `ClientPriceOverrideRepository`

**DTOs + Mappers**
- [x] DTOs request/response para PriceManual, PriceManualItem, ClientPriceOverride + mappers

**Servicios**
- [x] `PriceManualService` (CRUD)
- [x] `PriceManualItemService`
- [x] `ClientPriceOverrideService`
- [x] `PriceResolverService` — lógica: override → manual → agreedTariff → precio base Portfolio

**Controladores**
- [x] `PriceManualController` — `SUPER_ADMIN`, `ADMIN`
- [x] `ClientPriceOverrideController` — `SUPER_ADMIN`, `ADMIN`

**Feign Clients**
- [x] Agregar `spring-cloud-starter-openfeign` al pom.xml
- [x] `ClientsPortfolioClient` → clients-service (leer Portfolio.price, codeCups)
- [x] `ClientsContractClient` → clients-service (leer Contract.agreedTariff)

---

### Módulo `sale` — Ventas / Cuentas de cobro

**Tablas SQL**
- [x] `V4.0` — Crear tablas: `sale_order`, `sale_order_item`

**Entidades JPA**
- [x] `SaleOrder`
- [x] `SaleOrderItem`

**Repositorios**
- [x] `SaleOrderRepository`
- [x] `SaleOrderItemRepository`

**DTOs + Mappers**
- [x] `SaleOrderRequestDto` / `SaleOrderResponseDto` + mapper
- [x] `SaleOrderItemRequestDto` / `SaleOrderItemResponseDto` + mapper

**Servicios**
- [x] `SaleOrderService` (crear, confirmar, cancelar)
- [x] `SaleOrderItemService` (agregar, eliminar ítems)

**Controladores**
- [x] `SaleOrderController` — `ADMIN`, `RECEPTIONIST` (crear/confirmar); `SUPER_ADMIN` (cancelar)

**Feign Clients**
- [x] `AdmissionsAttentionClient` → admissions-service (marcar invoiced=true al confirmar)
- [x] `PatientClient` → patient-service (datos del paciente)
- [x] `SuppliersDocClient` → suppliers-service (datos del médico)

---

## Fase 2 — Facturación y Pagos

> Prerequisito: Fase 1 completa

### Módulo `invoice` — Factura

**Tablas SQL**
- [ ] `V5.0` — Crear tablas: `invoice`, `invoice_item`

**Entidades JPA**
- [ ] `Invoice`
- [ ] `InvoiceItem`

**Repositorios**
- [ ] `InvoiceRepository`
- [ ] `InvoiceItemRepository`

**DTOs + Mappers**
- [ ] DTOs request/response para Invoice, InvoiceItem + mappers

**Servicios**
- [ ] `InvoiceService` (generar, cancelar; gestión del consecutivo DIAN)
- [ ] `InvoiceItemService`

**Controladores**
- [ ] `InvoiceController` — `ADMIN`, `SUPER_ADMIN`

---

### Módulo `payment` — Pagos

**Tablas SQL**
- [ ] `V6.0` — Crear tablas: `payment`, `copayment_record`

**Entidades JPA**
- [ ] `Payment`
- [ ] `CopaymentRecord`

**Repositorios**
- [ ] `PaymentRepository`
- [ ] `CopaymentRecordRepository`

**DTOs + Mappers**
- [ ] DTOs request/response para Payment, CopaymentRecord + mappers

**Servicios**
- [ ] `PaymentService` (registrar pago, calcular saldo pendiente)
- [ ] `CopaymentService`

**Controladores**
- [ ] `PaymentController` — `ADMIN`, `RECEPTIONIST`, `SUPER_ADMIN`

---

## Fase 3 — Radicación y Glosas

> Prerequisito: Fase 2 completa

### Módulo `filing` — Radicación de cuentas y glosas

**Tablas SQL**
- [ ] `V7.0` — Crear tablas: `account_filing`, `account_filing_item`, `gloss`, `gloss_response`

**Entidades JPA**
- [ ] `AccountFiling`
- [ ] `AccountFilingItem`
- [ ] `Gloss`
- [ ] `GlossResponse`

**Repositorios**
- [ ] `AccountFilingRepository`
- [ ] `AccountFilingItemRepository`
- [ ] `GlossRepository`
- [ ] `GlossResponseRepository`

**DTOs + Mappers**
- [ ] DTOs request/response para AccountFiling, Gloss, GlossResponse + mappers

**Servicios**
- [ ] `AccountFilingService` (preparar, radicar, actualizar estado)
- [ ] `GlossService` (registrar glosa, responder)

**Controladores**
- [ ] `AccountFilingController` — `ADMIN`, `SUPER_ADMIN`
- [ ] `GlossController` — `ADMIN`, `SUPER_ADMIN`

**Feign Clients**
- [ ] `ClientsHealthProviderClient` → clients-service (datos del HealthProvider)

---

## Fase futura — Integración DIAN

> No planificada aún. Campos reservados en `Invoice` (cufe, xmlContent, qrCode = null por ahora).

- [ ] Implementar firma digital (certificado .p12)
- [ ] Generar XML UBL 2.1
- [ ] Integrar con operador autorizado (Siigo/Carvajal) o DIAN directo
- [ ] Agregar estados `SENT_TO_DIAN`, `ACCEPTED_BY_DIAN`, `REJECTED_BY_DIAN` al enum de Invoice

---

## Fuera de alcance

- **RIPS** → `reports-service` (por crear)
- **Portafolio de servicios** → permanece en `clients-service`
