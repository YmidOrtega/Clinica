-- =====================================================
-- 1. TABLA HEALTH_PROVIDERS (Proveedores de Salud)
-- =====================================================
DROP TABLE IF EXISTS health_providers;

CREATE TABLE health_providers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    social_reason VARCHAR(200) NOT NULL,
    nit VARCHAR(20) NOT NULL,
    contract VARCHAR(100),
    number_contract VARCHAR(100),
    type_provider VARCHAR(50),
    address VARCHAR(500),
    phone VARCHAR(20),
    active BOOLEAN DEFAULT TRUE NOT NULL,
    year_of_validity INT,
    year_completion INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,

    PRIMARY KEY (id),

    -- Índices únicos
    -- UNIQUE KEY uk_health_providers_nit (nit),
    UNIQUE KEY uk_health_providers_number_contract (number_contract),

    -- Índices de búsqueda
    INDEX idx_health_providers_social_reason (social_reason),
    INDEX idx_health_providers_type_provider (type_provider),
    INDEX idx_health_providers_active (active),
    INDEX idx_health_providers_year_of_validity (year_of_validity),
    INDEX idx_health_providers_created_at (created_at),

    -- Validaciones
    CONSTRAINT chk_health_providers_social_reason_not_empty
        CHECK (TRIM(social_reason) != ''),
    CONSTRAINT chk_health_providers_nit_not_empty
        CHECK (TRIM(nit) != ''),
    CONSTRAINT chk_health_providers_nit_format
        CHECK (nit REGEXP '^[0-9]{6,12}$'),
    CONSTRAINT chk_health_providers_phone_format
        CHECK (phone IS NULL OR phone REGEXP '^[0-9+\\-\\s()]{7,20}$'),
    CONSTRAINT chk_health_providers_number_contract_length
        CHECK (number_contract IS NULL OR CHAR_LENGTH(TRIM(number_contract)) >= 3),
    CONSTRAINT chk_health_providers_type_provider_values
        CHECK (type_provider IS NULL OR type_provider IN ('EPS', 'IPS', 'ARL', 'POLIZA_DE_SALUD', 'POLIZA_ESTUDIANTE', 'MEDICINA_PREPAGADA', 'PLAN_COMPLEMENTARIO', 'OTRO')),
    CONSTRAINT chk_health_providers_year_of_validity_range
        CHECK (year_of_validity IS NULL OR (year_of_validity >= 2000 AND year_of_validity <= 2050)),
    CONSTRAINT chk_health_providers_year_completion_range
        CHECK (year_completion IS NULL OR (year_completion >= 2000 AND year_completion <= 2050)),
    CONSTRAINT chk_health_providers_years_logical
        CHECK (year_completion IS NULL OR year_of_validity IS NULL OR year_completion >= year_of_validity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Comentarios para documentación
ALTER TABLE health_providers
COMMENT = 'Tabla que almacena información de los proveedores de salud';