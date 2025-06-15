-- Eliminar tablas en orden correcto para evitar errores de claves foráneas
DROP TABLE IF EXISTS attention_user_history;
DROP TABLE IF EXISTS authorization_portfolio_items;
DROP TABLE IF EXISTS attention_secondary_diagnoses;
DROP TABLE IF EXISTS authorizations;
DROP TABLE IF EXISTS attention_movements;
DROP TABLE IF EXISTS attentions;
DROP TABLE IF EXISTS configuration_services;
DROP TABLE IF EXISTS service_types;
DROP TABLE IF EXISTS care_types;
DROP TABLE IF EXISTS locations;

-- =====================================================
-- 1. TABLA CARE_TYPES (Tipos de Atención)
-- =====================================================
CREATE TABLE care_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_care_types_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. TABLA LOCATIONS (Ubicaciones)
-- =====================================================
CREATE TABLE locations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_locations_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. TABLA SERVICE_TYPES (Tipos de Servicio)
-- =====================================================

CREATE TABLE service_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) UNIQUE NOT NULL,
    care_type_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_service_types_care_type FOREIGN KEY (care_type_id) REFERENCES care_types(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 4. TABLA CONFIGURATION_SERVICES (Configuración de Servicios)
-- =====================================================
CREATE TABLE configuration_services (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_type_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_config_services_service_type FOREIGN KEY (service_type_id) REFERENCES service_types(id),
    CONSTRAINT fk_config_services_location FOREIGN KEY (location_id) REFERENCES locations(id),
    UNIQUE KEY uk_config_services_type_location (service_type_id, location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 5. TABLA ATTENTIONS (Atenciones)
-- =====================================================
CREATE TABLE attentions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    has_movements BOOLEAN NOT NULL DEFAULT TRUE,
    is_active_attention BOOLEAN NOT NULL DEFAULT TRUE,
    is_pre_admission BOOLEAN NOT NULL DEFAULT FALSE,
    invoiced BOOLEAN NOT NULL DEFAULT FALSE,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    health_provider_id BIGINT NOT NULL,
    invoice_number BIGINT NULL,
    configuration_service_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    admission_date_time TIMESTAMP NULL,
    discharge_date_time TIMESTAMP NULL,
    status ENUM('CREATED', 'IN_PROGRESS', 'DISCHARGED', 'CANCELLED') NOT NULL,
    entry_method VARCHAR(50) NULL,
    referring_entity VARCHAR(100) NULL,
    is_referral BOOLEAN NULL,
    main_diagnosis_code VARCHAR(20) NULL,
    triage_level ENUM('RED', 'ORANGE', 'YELLOW', 'GREEN', 'BLUE') NULL,
    cause ENUM('ILLNESS', 'ACCIDENT', 'WORK_ACCIDENT', 'TRAFFIC_ACCIDENT', 'VIOLENCE', 'MATERNITY', 'PREVENTION', 'CONTROL', 'EMERGENCY', 'ROUTINE_CHECKUP', 'VACCINATION', 'OTHER') NOT NULL,
    full_name VARCHAR(255) NULL,
    phone_number VARCHAR(255) NULL,
    relationship VARCHAR(255) NULL,
    observations VARCHAR(1000) NULL,
    billing_observations VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attentions_config_service FOREIGN KEY (configuration_service_id) REFERENCES configuration_services(id),
    INDEX idx_attention_patient_id (patient_id),
    INDEX idx_attention_doctor_id (doctor_id),
    INDEX idx_attention_health_provider_id (health_provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 6. TABLA ATTENTION_USER_HISTORY (Historial de Usuario por Atención)
-- =====================================================
CREATE TABLE attention_user_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attention_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action_type ENUM('CREATED', 'UPDATED', 'INVOICED', 'DISCHARGED', 'CANCELLED', 'REACTIVATED') NOT NULL,
    action_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observations VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_history_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE,
    INDEX idx_user_history_attention_id (attention_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 7. TABLA ATTENTION_SECONDARY_DIAGNOSES (Diagnósticos Secundarios)
-- =====================================================
CREATE TABLE attention_secondary_diagnoses (
    attention_id BIGINT NOT NULL,
    diagnosis_code VARCHAR(255) NOT NULL,
    CONSTRAINT fk_secondary_diagnoses_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE,
    INDEX idx_secondary_diagnoses_attention (attention_id),
    PRIMARY KEY (attention_id, diagnosis_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 8. TABLA AUTHORIZATIONS (Autorizaciones)
-- =====================================================
CREATE TABLE authorizations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attention_id BIGINT NOT NULL,
    authorization_number VARCHAR(255) NOT NULL,
    type_of_authorization ENUM('EMERGENCY_SERVICES', 'HOSPITALIZATION', 'AMBULATORY_SERVICES', 'SPECIALIZED_SERVICES', 'MEDICATIONS', 'SPACE_TRANSPORT_SERVICES') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_authorizations_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE,
    INDEX idx_authorizations_attention (attention_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 9. TABLA AUTHORIZATION_PORTFOLIO_ITEMS (Items del Portafolio Autorizados)
-- =====================================================
CREATE TABLE authorization_portfolio_items (
    authorization_id BIGINT NOT NULL,
    portfolio_item_id BIGINT NOT NULL,
    CONSTRAINT fk_auth_portfolio_authorization FOREIGN KEY (authorization_id) REFERENCES authorizations(id) ON DELETE CASCADE,
    INDEX idx_auth_portfolio_authorization (authorization_id),
    PRIMARY KEY (authorization_id, portfolio_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 10. TABLA ATTENTION_MOVEMENTS (Movimientos de Atención)
-- =====================================================
CREATE TABLE attention_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attention_id BIGINT NULL,
    from_configuration_id BIGINT NULL,
    to_configuration_id BIGINT NULL,
    moved_at TIMESTAMP NOT NULL,
    reason VARCHAR(500) NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_movements_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE SET NULL,
    CONSTRAINT fk_movements_from_config FOREIGN KEY (from_configuration_id) REFERENCES configuration_services(id) ON DELETE SET NULL,
    CONSTRAINT fk_movements_to_config FOREIGN KEY (to_configuration_id) REFERENCES configuration_services(id) ON DELETE SET NULL,
    INDEX idx_movements_attention (attention_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;