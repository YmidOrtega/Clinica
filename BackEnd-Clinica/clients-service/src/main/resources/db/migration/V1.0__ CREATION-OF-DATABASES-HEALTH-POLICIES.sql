-- =====================================================
-- 1. TABLA HEALTH_PROVIDERS (Proveedores de Salud)
-- =====================================================
DROP TABLE IF EXISTS contracts;
DROP TABLE IF EXISTS contract_covered_services;
DROP TABLE IF EXISTS health_providers;

CREATE TABLE health_providers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    social_reason VARCHAR(200) NOT NULL,
    nit VARCHAR(20) NOT NULL,
    type_provider VARCHAR(50),
    address VARCHAR(500),
    phone VARCHAR(20),
    active BOOLEAN DEFAULT TRUE NOT NULL,
    year_of_validity INT,
    year_completion INT,
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
-- 2. TABLA CONTRACTS (Contratos)
-- =====================================================
CREATE TABLE contracts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    health_provider_id BIGINT NOT NULL,
    contract_number VARCHAR(100) NOT NULL,
    agreed_tariff DECIMAL(15,2),
    start_date DATE,
    end_date DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
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

    -- Claves foráneas
    CONSTRAINT fk_contracts_health_provider
        FOREIGN KEY (health_provider_id) REFERENCES health_providers(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- Validaciones
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
-- 3. TABLA CONTRACT_COVERED_SERVICES (Servicios Cubiertos por Contrato)
-- =====================================================
CREATE TABLE contract_covered_services (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    service_name VARCHAR(200) NOT NULL,

    PRIMARY KEY (id),

    -- Índices
    INDEX idx_contract_services_contract_id (contract_id),
    INDEX idx_contract_services_service_name (service_name),

    -- Clave foránea
    CONSTRAINT fk_contract_services_contract
        FOREIGN KEY (contract_id) REFERENCES contracts(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- Validaciones
    CONSTRAINT chk_contract_services_service_name_not_empty
        CHECK (TRIM(service_name) != ''),
    CONSTRAINT chk_contract_services_service_name_length
        CHECK (CHAR_LENGTH(TRIM(service_name)) >= 2),

    -- Evitar duplicados de servicio por contrato
    UNIQUE KEY uk_contract_services_contract_service (contract_id, service_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Comentarios para documentación
ALTER TABLE contract_covered_services
COMMENT = 'Tabla que almacena los servicios cubiertos por cada contrato';

-- =====================================================
-- 4. DATOS DE EJEMPLO (OPCIONAL)
-- =====================================================
-- Insertar un proveedor de ejemplo
INSERT INTO health_providers (social_reason, nit, type_provider, address, phone, year_of_validity, year_completion)
VALUES ('EPS SURA S.A.', '860002503', 'EPS', 'Calle 72 #10-07 Bogotá', '601-5115000', 2024, 2025);

-- Insertar un contrato de ejemplo
INSERT INTO contracts (health_provider_id, contract_number, agreed_tariff, start_date, end_date, status)
VALUES (1, 'CONT-2024-001', 150000.00, '2024-01-01', '2024-12-31', 'ACTIVE');

-- Insertar servicios cubiertos de ejemplo
INSERT INTO contract_covered_services (contract_id, service_name) VALUES
(1, 'Consulta médica general'),
(1, 'Exámenes de laboratorio'),
(1, 'Radiografías'),
(1, 'Procedimientos menores');