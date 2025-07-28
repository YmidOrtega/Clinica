-- =====================================================
-- SCRIPT UNIFICADO POSTGRESQL - ADMISSIONS SERVICE
-- =====================================================

-- Eliminar tablas en orden correcto para evitar errores de claves foráneas
DROP TABLE IF EXISTS attention_user_history CASCADE;
DROP TABLE IF EXISTS authorization_portfolio_items CASCADE;
DROP TABLE IF EXISTS attention_diagnostic_codes CASCADE;
DROP TABLE IF EXISTS attention_health_providers CASCADE;
DROP TABLE IF EXISTS authorizations CASCADE;
DROP TABLE IF EXISTS attention_movements CASCADE;
DROP TABLE IF EXISTS attentions CASCADE;
DROP TABLE IF EXISTS configuration_services CASCADE;
DROP TABLE IF EXISTS care_types CASCADE;
DROP TABLE IF EXISTS service_types CASCADE;
DROP TABLE IF EXISTS locations CASCADE;

-- Crear tipos ENUM para PostgreSQL
CREATE TYPE attention_status AS ENUM ('CREATED', 'IN_PROGRESS', 'DISCHARGED', 'CANCELLED');
CREATE TYPE triage_level AS ENUM ('RED', 'ORANGE', 'YELLOW', 'GREEN', 'BLUE');
CREATE TYPE cause_type AS ENUM ('ILLNESS', 'ACCIDENT', 'WORK_ACCIDENT', 'TRAFFIC_ACCIDENT', 'VIOLENCE', 'MATERNITY', 'PREVENTION', 'CONTROL', 'EMERGENCY', 'ROUTINE_CHECKUP', 'VACCINATION', 'OTHER');
CREATE TYPE user_action_type AS ENUM ('CREATED', 'UPDATED', 'INVOICED', 'DISCHARGED', 'CANCELLED', 'REACTIVATED');
CREATE TYPE authorization_type AS ENUM ('EMERGENCY_SERVICES', 'HOSPITALIZATION', 'AMBULATORY_SERVICES', 'SPECIALIZED_SERVICES', 'MEDICATIONS', 'SPACE_TRANSPORT_SERVICES');

-- =====================================================
-- 1. TABLA SERVICE_TYPES (Tipos de Servicio)
-- =====================================================
CREATE TABLE service_types (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 2. TABLA LOCATIONS (Ubicaciones)
-- =====================================================
CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_locations_name UNIQUE (name)
);

-- =====================================================
-- 3. TABLA CARE_TYPES (Tipos de Atención)
-- =====================================================
CREATE TABLE care_types (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    service_type_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_care_types_name UNIQUE (name),
    CONSTRAINT fk_care_types_service_type FOREIGN KEY (service_type_id) REFERENCES service_types(id)
);

-- =====================================================
-- 4. TABLA CONFIGURATION_SERVICES (Configuración de Servicios)
-- =====================================================
CREATE TABLE configuration_services (
    id BIGSERIAL PRIMARY KEY,
    service_type_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_config_services_service_type FOREIGN KEY (service_type_id) REFERENCES service_types(id),
    CONSTRAINT fk_config_services_location FOREIGN KEY (location_id) REFERENCES locations(id),
    CONSTRAINT uk_config_services_type_location UNIQUE (service_type_id, location_id)
);

-- =====================================================
-- 5. TABLA ATTENTIONS (Atenciones)
-- =====================================================
CREATE TABLE attentions (
    id BIGSERIAL PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    has_movements BOOLEAN NOT NULL DEFAULT TRUE,
    is_active_attention BOOLEAN NOT NULL DEFAULT TRUE,
    is_pre_admission BOOLEAN NOT NULL DEFAULT FALSE,
    invoiced BOOLEAN NOT NULL DEFAULT FALSE,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    invoice_number BIGINT NULL,
    configuration_service_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    admission_date_time TIMESTAMP NULL,
    discharge_date_time TIMESTAMP NULL,
    status attention_status NOT NULL,
    entry_method VARCHAR(50) NULL,
    triage_level triage_level NULL,
    cause cause_type NOT NULL,
    -- Campos del companion embebido
    full_name VARCHAR(255) NULL,
    phone_number VARCHAR(255) NULL,
    relationship VARCHAR(255) NULL,
    observations VARCHAR(1000) NULL,
    CONSTRAINT fk_attentions_config_service FOREIGN KEY (configuration_service_id) REFERENCES configuration_services(id)
);

-- Crear índices para la tabla attentions
CREATE INDEX idx_attention_patient_id ON attentions(patient_id);
CREATE INDEX idx_attention_doctor_id ON attentions(doctor_id);

-- =====================================================
-- 6. TABLA ATTENTION_HEALTH_PROVIDERS (Proveedores de Salud por Atención)
-- =====================================================
CREATE TABLE attention_health_providers (
    attention_id BIGINT NOT NULL,
    health_provider_nit VARCHAR(255) NOT NULL,
    PRIMARY KEY (attention_id, health_provider_nit),
    CONSTRAINT fk_attention_hp_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE
);

CREATE INDEX idx_attention_hp_attention ON attention_health_providers(attention_id);

-- =====================================================
-- 7. TABLA ATTENTION_DIAGNOSTIC_CODES (Códigos de Diagnóstico por Atención)
-- =====================================================
CREATE TABLE attention_diagnostic_codes (
    attention_id BIGINT NOT NULL,
    diagnostic_codes VARCHAR(255) NOT NULL,
    PRIMARY KEY (attention_id, diagnostic_codes),
    CONSTRAINT fk_attention_diagnostic_codes_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE
);

CREATE INDEX idx_attention_diagnostic_codes_attention ON attention_diagnostic_codes(attention_id);

-- =====================================================
-- 8. TABLA ATTENTION_USER_HISTORY (Historial de Usuario por Atención)
-- =====================================================
CREATE TABLE attention_user_history (
    id BIGSERIAL PRIMARY KEY,
    attention_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action_type user_action_type NOT NULL,
    action_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observations VARCHAR(500) NULL,
    CONSTRAINT fk_user_history_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_history_attention_id ON attention_user_history(attention_id);

-- =====================================================
-- 9. TABLA AUTHORIZATIONS (Autorizaciones)
-- =====================================================
CREATE TABLE authorizations (
    id BIGSERIAL PRIMARY KEY,
    attention_id BIGINT NOT NULL,
    authorization_number VARCHAR(255) NOT NULL,
    type_of_authorization authorization_type NOT NULL,
    authorization_by VARCHAR(255) NOT NULL,
    copayment_value DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_authorizations_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE
);

CREATE INDEX idx_authorizations_attention ON authorizations(attention_id);

-- =====================================================
-- 10. TABLA AUTHORIZATION_PORTFOLIO_ITEMS (Items del Portafolio Autorizados)
-- =====================================================
CREATE TABLE authorization_portfolio_items (
    authorization_id BIGINT NOT NULL,
    portfolio_item_id BIGINT NOT NULL,
    PRIMARY KEY (authorization_id, portfolio_item_id),
    CONSTRAINT fk_auth_portfolio_authorization FOREIGN KEY (authorization_id) REFERENCES authorizations(id) ON DELETE CASCADE
);

CREATE INDEX idx_auth_portfolio_authorization ON authorization_portfolio_items(authorization_id);

-- =====================================================
-- 11. TABLA ATTENTION_MOVEMENTS (Movimientos de Atención)
-- =====================================================
CREATE TABLE attention_movements (
    id BIGSERIAL PRIMARY KEY,
    attention_id BIGINT NULL,
    from_configuration_id BIGINT NULL,
    to_configuration_id BIGINT NULL,
    moved_at TIMESTAMP NOT NULL,
    reason VARCHAR(500) NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_movements_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE SET NULL,
    CONSTRAINT fk_movements_from_config FOREIGN KEY (from_configuration_id) REFERENCES configuration_services(id) ON DELETE SET NULL,
    CONSTRAINT fk_movements_to_config FOREIGN KEY (to_configuration_id) REFERENCES configuration_services(id) ON DELETE SET NULL
);

CREATE INDEX idx_movements_attention ON attention_movements(attention_id);

-- =====================================================
-- FUNCIONES PARA ACTUALIZAR TIMESTAMP (equivalente a ON UPDATE CURRENT_TIMESTAMP de MySQL)
-- =====================================================

-- Función para actualizar updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers para actualizar updated_at automáticamente
CREATE TRIGGER update_service_types_updated_at BEFORE UPDATE ON service_types FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_locations_updated_at BEFORE UPDATE ON locations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_care_types_updated_at BEFORE UPDATE ON care_types FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_configuration_services_updated_at BEFORE UPDATE ON configuration_services FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_attentions_updated_at BEFORE UPDATE ON attentions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_authorizations_updated_at BEFORE UPDATE ON authorizations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
