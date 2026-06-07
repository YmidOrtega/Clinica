-- =====================================================
-- 1. TABLA BILLING_CONFIGURATION (Configuración del emisor)
-- =====================================================

CREATE TABLE billing_configuration (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    clinic_nit    VARCHAR(20)     NOT NULL,
    social_reason VARCHAR(300)    NOT NULL,
    tax_regime    ENUM(
                      'RESPONSABLE_DE_IVA',
                      'NO_RESPONSABLE_DE_IVA'
                  )               NOT NULL,
    dian_environment ENUM(
                      'HABILITACION',
                      'PRODUCCION'
                  )               NOT NULL DEFAULT 'HABILITACION',
    software_id   VARCHAR(100)    DEFAULT NULL,
    software_pin  VARCHAR(100)    DEFAULT NULL,
    city          VARCHAR(100)    DEFAULT NULL,
    address       VARCHAR(300)    DEFAULT NULL,
    phone         VARCHAR(20)     DEFAULT NULL,
    email         VARCHAR(150)    DEFAULT NULL,
    active        BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by    VARCHAR(100)    DEFAULT NULL,

    PRIMARY KEY (id),

    -- Solo puede existir una configuración activa a la vez
    UNIQUE KEY uk_billing_configuration_active (active),

    -- Índices
    INDEX idx_billing_configuration_active (active),
    INDEX idx_billing_configuration_dian_environment (dian_environment),

    -- Validaciones
    CONSTRAINT chk_billing_configuration_nit_format
        CHECK (clinic_nit REGEXP '^[0-9]{6,12}(-[0-9])?$'),
    CONSTRAINT chk_billing_configuration_social_reason_not_empty
        CHECK (TRIM(social_reason) != ''),
    CONSTRAINT chk_billing_configuration_email_format
        CHECK (email IS NULL OR email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_billing_configuration_phone_format
        CHECK (phone IS NULL OR phone REGEXP '^[0-9+\\-\\s()]{7,20}$'),
    CONSTRAINT chk_billing_configuration_tax_regime_values
        CHECK (tax_regime IN ('RESPONSABLE_DE_IVA', 'NO_RESPONSABLE_DE_IVA')),
    CONSTRAINT chk_billing_configuration_dian_environment_values
        CHECK (dian_environment IN ('HABILITACION', 'PRODUCCION'))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE billing_configuration
COMMENT = 'Configuración global del emisor de facturas electrónicas (singleton activo)';

-- =====================================================
-- 2. TABLA TAX_CONFIGURATION (Impuestos configurables)
-- =====================================================

CREATE TABLE tax_configuration (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(100)   NOT NULL,
    code                  VARCHAR(20)    NOT NULL,
    type                  ENUM(
                              'IVA',
                              'INC',
                              'RETEFUENTE',
                              'RETEIVA'
                          )              NOT NULL,
    percentage            DECIMAL(5,2)   NOT NULL,
    applies_to_services   BOOLEAN        NOT NULL DEFAULT TRUE,
    applies_to_medications BOOLEAN       NOT NULL DEFAULT FALSE,
    active                BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Código único de impuesto (estándar DIAN)
    UNIQUE KEY uk_tax_configuration_code (code),

    -- Índices
    INDEX idx_tax_configuration_type (type),
    INDEX idx_tax_configuration_active (active),

    -- Validaciones
    CONSTRAINT chk_tax_configuration_name_not_empty
        CHECK (TRIM(name) != ''),
    CONSTRAINT chk_tax_configuration_code_not_empty
        CHECK (TRIM(code) != ''),
    CONSTRAINT chk_tax_configuration_percentage_range
        CHECK (percentage >= 0.00 AND percentage <= 100.00),
    CONSTRAINT chk_tax_configuration_type_values
        CHECK (type IN ('IVA', 'INC', 'RETEFUENTE', 'RETEIVA'))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE tax_configuration
COMMENT = 'Tipos de impuestos aplicables a los servicios facturados (IVA, INC, retenciones)';

-- =====================================================
-- 3. TABLA DIAN_RESOLUTION (Resoluciones de numeración)
-- =====================================================

CREATE TABLE dian_resolution (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    resolution_number   VARCHAR(50)    NOT NULL,
    issue_date          DATE           NOT NULL,
    prefix              VARCHAR(10)    DEFAULT NULL,
    document_type       ENUM(
                            'FACTURA_VENTA',
                            'NOTA_CREDITO',
                            'NOTA_DEBITO'
                        )              NOT NULL,
    from_number         BIGINT         NOT NULL,
    to_number           BIGINT         NOT NULL,
    valid_from          DATE           NOT NULL,
    valid_to            DATE           NOT NULL,
    current_consecutive BIGINT         NOT NULL DEFAULT 1,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Número de resolución único
    UNIQUE KEY uk_dian_resolution_number (resolution_number),

    -- Solo puede haber una resolución activa por tipo de documento
    UNIQUE KEY uk_dian_resolution_active_type (document_type, active),

    -- Índices
    INDEX idx_dian_resolution_document_type (document_type),
    INDEX idx_dian_resolution_active (active),
    INDEX idx_dian_resolution_valid_to (valid_to),

    -- Validaciones
    CONSTRAINT chk_dian_resolution_resolution_number_not_empty
        CHECK (TRIM(resolution_number) != ''),
    CONSTRAINT chk_dian_resolution_range_logical
        CHECK (to_number > from_number),
    CONSTRAINT chk_dian_resolution_consecutive_in_range
        CHECK (current_consecutive >= from_number AND current_consecutive <= to_number),
    CONSTRAINT chk_dian_resolution_validity_logical
        CHECK (valid_to > valid_from),
    CONSTRAINT chk_dian_resolution_document_type_values
        CHECK (document_type IN ('FACTURA_VENTA', 'NOTA_CREDITO', 'NOTA_DEBITO'))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE dian_resolution
COMMENT = 'Resoluciones de numeración habilitadas por la DIAN para facturación electrónica';
