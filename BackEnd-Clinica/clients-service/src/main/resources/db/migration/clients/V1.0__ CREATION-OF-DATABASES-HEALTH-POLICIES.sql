-- =====================================================
-- 1. TABLA HEALTH_PROVIDERS (Proveedores de Salud)
-- =====================================================

CREATE TABLE health_providers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    social_reason VARCHAR(200) NOT NULL,
    nit VARCHAR(20) NOT NULL,
    type_provider ENUM(
        'EPS',
        'IPS',
        'ARL',
        'POLIZA_DE_SALUD',
        'POLIZA_ESTUDIANTE',
        'MEDICINA_PREPAGADA',
        'PLAN_COMPLEMENTARIO',
        'OTRO'
    ) DEFAULT NULL,
    address VARCHAR(500) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    year_of_validity INT NOT NULL,
    year_completion INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,

    PRIMARY KEY (id),

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

-- =====================================================
-- 2. TABLA PORTFOLIOS (Catálogo de Servicios/Exámenes)
-- =====================================================
CREATE TABLE portfolios (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    code_cups VARCHAR(50) NOT NULL,
    code_clinic VARCHAR(50) NOT NULL,
    price DECIMAL(15,2) NOT NULL,

    PRIMARY KEY (id),

    -- Índices de búsqueda
    INDEX idx_portfolios_name (name),
    INDEX idx_portfolios_code_cups (code_cups),
    INDEX idx_portfolios_code_clinic (code_clinic),
    INDEX idx_portfolios_price (price),

    -- Índices únicos para códigos
    UNIQUE KEY uk_portfolios_code_cups (code_cups),
    UNIQUE KEY uk_portfolios_code_clinic (code_clinic),

    -- Validaciones
    CONSTRAINT chk_portfolios_name_not_empty
        CHECK (TRIM(name) != ''),
    CONSTRAINT chk_portfolios_name_length
        CHECK (CHAR_LENGTH(TRIM(name)) >= 2),
    CONSTRAINT chk_portfolios_code_cups_format
        CHECK (code_cups IS NULL OR TRIM(code_cups) != ''),
    CONSTRAINT chk_portfolios_code_clinic_format
        CHECK (code_clinic IS NULL OR TRIM(code_clinic) != ''),
    CONSTRAINT chk_portfolios_price_positive
        CHECK (price IS NULL OR price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Comentarios para documentación
ALTER TABLE portfolios
COMMENT = 'Tabla que almacena el catálogo maestro de servicios y exámenes disponibles';

-- =====================================================
-- 3. TABLA CONTRACTS (Contratos)
-- =====================================================
CREATE TABLE contracts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    health_provider_id BIGINT NOT NULL,
    contract_name VARCHAR(255) NOT NULL,
    contract_number VARCHAR(100) UNIQUE NOT NULL,
    agreed_tariff DECIMAL(15,2) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL ,
    status ENUM(
        'ACTIVE',
        'INACTIVE',
        'PENDING',
        'EXPIRED',
        'CANCELLED',
        'SUSPENDED'
    ) DEFAULT 'ACTIVE' NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,

    PRIMARY KEY (id),

    -- Índices únicos
    UNIQUE KEY uk_contracts_contract_number (contract_number),

    -- Índices de búsqueda
    INDEX idx_contracts_health_provider_id (health_provider_id),
    INDEX idx_contracts_status (status),
    INDEX idx_contracts_start_date (start_date),
    INDEX idx_contracts_end_date (end_date),
    INDEX idx_contracts_created_at (created_at),
    INDEX idx_contracts_active (active),
    INDEX idx_contracts_contract_name (contract_name),

    -- Claves foráneas
    CONSTRAINT fk_contracts_health_provider
        FOREIGN KEY (health_provider_id) REFERENCES health_providers(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- Validaciones
    CONSTRAINT chk_contracts_contract_name_not_empty
        CHECK (TRIM(contract_name) != ''),
    CONSTRAINT chk_contracts_contract_number_not_empty
        CHECK (TRIM(contract_number) != ''),
    CONSTRAINT chk_contracts_contract_number_length
        CHECK (CHAR_LENGTH(TRIM(contract_number)) >= 3),
    CONSTRAINT chk_contracts_agreed_tariff_positive
        CHECK (agreed_tariff IS NULL OR agreed_tariff >= 0),
    CONSTRAINT chk_contracts_status_values
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'EXPIRED', 'CANCELLED', 'SUSPENDED')),
    CONSTRAINT chk_contracts_dates_logical
        CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Comentarios para documentación
ALTER TABLE contracts
COMMENT = 'Tabla que almacena información de los contratos con proveedores de salud';

-- =====================================================
-- 4. TABLA CONTRACT_PORTFOLIOS (Servicios Contratados)
-- =====================================================
CREATE TABLE contract_portfolios (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    portfolio_id BIGINT NOT NULL,

    PRIMARY KEY (id),

    -- Índices
    INDEX idx_contract_portfolios_contract_id (contract_id),
    INDEX idx_contract_portfolios_portfolio_id (portfolio_id),

    -- Claves foráneas
    CONSTRAINT fk_contract_portfolios_contract
        FOREIGN KEY (contract_id) REFERENCES contracts(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_contract_portfolios_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- Evitar duplicados de servicio por contrato
    UNIQUE KEY uk_contract_portfolios_contract_portfolio (contract_id, portfolio_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Comentarios para documentación
ALTER TABLE contract_portfolios
COMMENT = 'Tabla intermedia que relaciona contratos con los servicios del portafolio que pueden contratar';