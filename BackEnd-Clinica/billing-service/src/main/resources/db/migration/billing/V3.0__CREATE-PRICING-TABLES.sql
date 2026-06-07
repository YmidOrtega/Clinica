-- =====================================================
-- 1. TABLA PRICE_MANUAL (Manuales de cobro)
-- =====================================================

CREATE TABLE price_manual (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    name          VARCHAR(200)   NOT NULL,
    code          VARCHAR(50)    NOT NULL,
    type          ENUM(
                      'ISS',
                      'SOAT',
                      'PARTICULAR',
                      'CONTRATO',
                      'OTRO'
                  )              NOT NULL,
    year          SMALLINT       DEFAULT NULL,
    description   VARCHAR(500)   DEFAULT NULL,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by    VARCHAR(100)   DEFAULT NULL,

    PRIMARY KEY (id),

    UNIQUE KEY uk_price_manual_code (code),

    INDEX idx_price_manual_type (type),
    INDEX idx_price_manual_active (active),
    INDEX idx_price_manual_year (year),

    CONSTRAINT chk_price_manual_name_not_empty
        CHECK (TRIM(name) != ''),
    CONSTRAINT chk_price_manual_code_not_empty
        CHECK (TRIM(code) != ''),
    CONSTRAINT chk_price_manual_year_range
        CHECK (year IS NULL OR (year >= 1990 AND year <= 2100)),
    CONSTRAINT chk_price_manual_type_values
        CHECK (type IN ('ISS', 'SOAT', 'PARTICULAR', 'CONTRATO', 'OTRO'))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE price_manual
COMMENT = 'Manuales de cobro de referencia (ISS, SOAT, Particular, etc.)';

-- =====================================================
-- 2. TABLA PRICE_MANUAL_ITEM (Ítems del manual)
-- =====================================================

CREATE TABLE price_manual_item (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    price_manual_id BIGINT          NOT NULL,
    portfolio_id    BIGINT          DEFAULT NULL,
    code_cups       VARCHAR(50)     DEFAULT NULL,
    code_clinic     VARCHAR(50)     DEFAULT NULL,
    description     VARCHAR(300)    NOT NULL,
    base_price      DECIMAL(15,2)   NOT NULL,
    unit            VARCHAR(50)     DEFAULT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),

    -- Un portafolio no puede aparecer dos veces en el mismo manual
    UNIQUE KEY uk_price_manual_item_manual_portfolio (price_manual_id, portfolio_id),

    INDEX idx_price_manual_item_manual_id (price_manual_id),
    INDEX idx_price_manual_item_portfolio_id (portfolio_id),
    INDEX idx_price_manual_item_code_cups (code_cups),
    INDEX idx_price_manual_item_code_clinic (code_clinic),
    INDEX idx_price_manual_item_active (active),

    CONSTRAINT fk_price_manual_item_manual
        FOREIGN KEY (price_manual_id) REFERENCES price_manual(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT chk_price_manual_item_description_not_empty
        CHECK (TRIM(description) != ''),
    CONSTRAINT chk_price_manual_item_base_price_positive
        CHECK (base_price >= 0),
    CONSTRAINT chk_price_manual_item_code_reference
        CHECK (portfolio_id IS NOT NULL OR code_cups IS NOT NULL OR code_clinic IS NOT NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE price_manual_item
COMMENT = 'Precios por servicio/examen dentro de un manual de cobro';

-- =====================================================
-- 3. TABLA CLIENT_PRICE_OVERRIDE (Overrides por contrato)
-- =====================================================

CREATE TABLE client_price_override (
    id                   BIGINT          NOT NULL AUTO_INCREMENT,
    contract_id          BIGINT          NOT NULL,
    health_provider_nit  VARCHAR(20)     NOT NULL,
    portfolio_id         BIGINT          NOT NULL,
    code_cups            VARCHAR(50)     DEFAULT NULL,
    negotiated_price     DECIMAL(15,2)   NOT NULL,
    discount_percentage  DECIMAL(5,2)    DEFAULT NULL,
    valid_from           DATE            NOT NULL,
    valid_to             DATE            NOT NULL,
    active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by           VARCHAR(100)    DEFAULT NULL,

    PRIMARY KEY (id),

    INDEX idx_client_price_override_contract_id (contract_id),
    INDEX idx_client_price_override_portfolio_id (portfolio_id),
    INDEX idx_client_price_override_health_provider_nit (health_provider_nit),
    INDEX idx_client_price_override_active (active),
    INDEX idx_client_price_override_valid_to (valid_to),
    -- Búsqueda combinada principal: dado un contrato y servicio, encontrar el precio activo
    INDEX idx_client_price_override_lookup (contract_id, portfolio_id, active),

    CONSTRAINT chk_client_price_override_nit_format
        CHECK (health_provider_nit REGEXP '^[0-9]{6,12}(-[0-9])?$'),
    CONSTRAINT chk_client_price_override_price_positive
        CHECK (negotiated_price >= 0),
    CONSTRAINT chk_client_price_override_discount_range
        CHECK (discount_percentage IS NULL OR (discount_percentage >= 0 AND discount_percentage <= 100)),
    CONSTRAINT chk_client_price_override_validity_logical
        CHECK (valid_to > valid_from)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE client_price_override
COMMENT = 'Precios negociados específicos por contrato y servicio del portafolio';
