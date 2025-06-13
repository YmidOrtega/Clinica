
-- Eliminar tablas en orden correcto (respetando dependencias)
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
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Restricciones de unicidad
    UNIQUE KEY uk_care_types_name (name),

    -- Índices
    INDEX idx_care_types_active (active),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_care_types_name_not_empty CHECK (TRIM(name) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. TABLA LOCATIONS (Ubicaciones/Locaciones)
-- =====================================================
CREATE TABLE locations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Restricciones de unicidad
    UNIQUE KEY uk_locations_name (name),

    -- Índices
    INDEX idx_locations_active (active),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_locations_name_not_empty CHECK (TRIM(name) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. TABLA SERVICE_TYPES (Tipos de Servicio)
-- =====================================================
CREATE TABLE service_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    care_type_id BIGINT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Claves foráneas
    CONSTRAINT fk_service_types_care_type FOREIGN KEY (care_type_id) REFERENCES care_types(id),

    -- Índices
    INDEX idx_service_types_care_type (care_type_id),
    INDEX idx_service_types_active (active),
    INDEX idx_service_types_name (name),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_service_types_name_not_empty CHECK (TRIM(name) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 4. TABLA CONFIGURATION_SERVICES (Configuración de Servicios)
-- =====================================================
CREATE TABLE configuration_services (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_type_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Claves foráneas
    CONSTRAINT fk_config_services_service_type FOREIGN KEY (service_type_id) REFERENCES service_types(id),
    CONSTRAINT fk_config_services_location FOREIGN KEY (location_id) REFERENCES locations(id),

    -- Restricciones de unicidad (evitar duplicados de configuración)
    UNIQUE KEY uk_config_services_type_location (service_type_id, location_id),

    -- Índices
    INDEX idx_config_services_service_type (service_type_id),
    INDEX idx_config_services_location (location_id),
    INDEX idx_config_services_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 5. TABLA ATTENTIONS (Atenciones)
-- =====================================================
CREATE TABLE attentions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    active BOOLEAN DEFAULT TRUE,
    has_movements BOOLEAN DEFAULT TRUE,
    is_active_attention BOOLEAN DEFAULT TRUE,
    is_pre_admission BOOLEAN DEFAULT FALSE,
    invoiced BOOLEAN DEFAULT FALSE,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    health_provider_id BIGINT NOT NULL,
    invoice_number BIGINT NULL,
    user_id BIGINT NULL,
    created_by_user_id BIGINT NULL,
    updated_by_user_id BIGINT NULL,
    configuration_service_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    admission_date_time TIMESTAMP NULL,
    discharge_date_time TIMESTAMP NULL,
    status ENUM(
        'PENDING',
        'IN_PROGRESS',
        'COMPLETED',
        'CANCELLED',
        'SUSPENDED'
    ) NOT NULL DEFAULT 'PENDING',
    entry_method VARCHAR(50) NULL,
    referring_entity VARCHAR(100) NULL,
    is_referral BOOLEAN NULL,
    main_diagnosis_code VARCHAR(20) NULL,
    triage_level ENUM(
        'LEVEL_1',
        'LEVEL_2',
        'LEVEL_3',
        'LEVEL_4',
        'LEVEL_5'
    ) NULL,
    cause ENUM(
        'ILLNESS',
        'ACCIDENT',
        'VIOLENCE',
        'EMERGENCY',
        'PREVENTION',
        'OTHER'
    ) NOT NULL,
    -- Campos del companion embebido
    companion_full_name VARCHAR(150) NULL,
    companion_phone_number VARCHAR(20) NULL,
    companion_relationship VARCHAR(100) NULL,
    observations TEXT NULL,
    billing_observations TEXT NULL,

    PRIMARY KEY (id),

    -- Claves foráneas
    CONSTRAINT fk_attentions_config_service FOREIGN KEY (configuration_service_id) REFERENCES configuration_services(id),

    -- Índices de rendimiento
    INDEX idx_attentions_patient_id (patient_id),
    INDEX idx_attentions_doctor_id (doctor_id),
    INDEX idx_attentions_health_provider_id (health_provider_id),
    INDEX idx_attentions_status (status),
    INDEX idx_attentions_active (active),
    INDEX idx_attentions_admission_date (admission_date_time),
    INDEX idx_attentions_discharge_date (discharge_date_time),
    INDEX idx_attentions_created_at (created_at),
    INDEX idx_attentions_invoice_number (invoice_number),
    INDEX idx_attentions_triage_level (triage_level),
    INDEX idx_attentions_cause (cause),
    INDEX idx_attentions_is_active_attention (is_active_attention),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_attentions_discharge_after_admission
        CHECK (discharge_date_time IS NULL OR admission_date_time IS NULL OR discharge_date_time >= admission_date_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 6. TABLA ATTENTION_SECONDARY_DIAGNOSES (Diagnósticos Secundarios)
-- =====================================================
CREATE TABLE attention_secondary_diagnoses (
    attention_id BIGINT NOT NULL,
    diagnosis_code VARCHAR(20) NOT NULL,

    -- Claves foráneas
    CONSTRAINT fk_secondary_diagnoses_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE,

    -- Índices
    INDEX idx_secondary_diagnoses_attention (attention_id),
    INDEX idx_secondary_diagnoses_code (diagnosis_code),

    -- Restricción de unicidad compuesta
    UNIQUE KEY uk_secondary_diagnoses (attention_id, diagnosis_code),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_secondary_diagnoses_code_not_empty CHECK (TRIM(diagnosis_code) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 7. TABLA AUTHORIZATIONS (Autorizaciones)
-- =====================================================
CREATE TABLE authorizations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attention_id BIGINT NOT NULL,
    authorization_number VARCHAR(100) NOT NULL,
    type_of_authorization ENUM(
        'MEDICAL',
        'SURGICAL',
        'DIAGNOSTIC',
        'THERAPEUTIC',
        'HOSPITALIZATION',
        'EMERGENCY',
        'OTHER'
    ) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Claves foráneas
    CONSTRAINT fk_authorizations_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE,

    -- Restricciones de unicidad
    UNIQUE KEY uk_authorizations_number (authorization_number),

    -- Índices
    INDEX idx_authorizations_attention (attention_id),
    INDEX idx_authorizations_type (type_of_authorization),
    INDEX idx_authorizations_created_at (created_at),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_authorizations_number_not_empty CHECK (TRIM(authorization_number) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 8. TABLA AUTHORIZATION_PORTFOLIO_ITEMS (Items del Portafolio Autorizados)
-- =====================================================
CREATE TABLE authorization_portfolio_items (
    authorization_id BIGINT NOT NULL,
    portfolio_item_id BIGINT NOT NULL,

    -- Claves foráneas
    CONSTRAINT fk_auth_portfolio_authorization FOREIGN KEY (authorization_id) REFERENCES authorizations(id) ON DELETE CASCADE,

    -- Índices
    INDEX idx_auth_portfolio_authorization (authorization_id),
    INDEX idx_auth_portfolio_item (portfolio_item_id),

    -- Restricción de unicidad compuesta
    UNIQUE KEY uk_auth_portfolio (authorization_id, portfolio_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 9. TABLA ATTENTION_MOVEMENTS (Movimientos de Atención)
-- =====================================================
CREATE TABLE attention_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attention_id BIGINT NOT NULL,
    from_configuration_id BIGINT NULL,
    to_configuration_id BIGINT NOT NULL,
    moved_at TIMESTAMP NOT NULL,
    reason VARCHAR(500) NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Claves foráneas
    CONSTRAINT fk_movements_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE,
    CONSTRAINT fk_movements_from_config FOREIGN KEY (from_configuration_id) REFERENCES configuration_services(id),
    CONSTRAINT fk_movements_to_config FOREIGN KEY (to_configuration_id) REFERENCES configuration_services(id),

    -- Índices
    INDEX idx_movements_attention (attention_id),
    INDEX idx_movements_from_config (from_configuration_id),
    INDEX idx_movements_to_config (to_configuration_id),
    INDEX idx_movements_moved_at (moved_at),
    INDEX idx_movements_user (user_id),
    INDEX idx_movements_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TRIGGERS DE AUDITORÍA Y MANTENIMIENTO
-- =====================================================

DELIMITER //

-- Trigger para actualizar has_movements en attentions
CREATE TRIGGER tr_update_has_movements_after_insert
AFTER INSERT ON attention_movements
FOR EACH ROW
BEGIN
    UPDATE attentions
    SET has_movements = TRUE
    WHERE id = NEW.attention_id;
END//

CREATE TRIGGER tr_update_has_movements_after_delete
AFTER DELETE ON attention_movements
FOR EACH ROW
BEGIN
    DECLARE movement_count INT DEFAULT 0;

    SELECT COUNT(*) INTO movement_count
    FROM attention_movements
    WHERE attention_id = OLD.attention_id;

    UPDATE attentions
    SET has_movements = (movement_count > 0)
    WHERE id = OLD.attention_id;
END//

DELIMITER ;
