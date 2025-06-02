-- =====================================================
-- 1. TABLA HEALTH_POLICIES (Pólizas de Salud)
-- =====================================================
DROP TABLE IF EXISTS health_policies;

CREATE TABLE health_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    social_reason VARCHAR(200) NOT NULL,
    nit INT NOT NULL,
    contract VARCHAR(100),
    number_contract VARCHAR(100),
    type VARCHAR(50),
    address VARCHAR(255),
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Índices únicos
    UNIQUE KEY uk_health_policies_nit (nit),
    UNIQUE KEY uk_health_policies_contract (number_contract),

    -- Índices de búsqueda
    INDEX idx_health_policies_social_reason (social_reason),
    INDEX idx_health_policies_type (type),
    INDEX idx_health_policies_active (is_active),

    -- Validaciones
    CONSTRAINT chk_health_policies_social_reason_not_empty CHECK (TRIM(social_reason) != ''),
    CONSTRAINT chk_health_policies_nit_positive CHECK (nit > 0),
    CONSTRAINT chk_health_policies_nit_valid CHECK (nit >= 100000 AND nit <= 999999999),
    CONSTRAINT chk_health_policies_phone_format CHECK (phone IS NULL OR phone REGEXP '^[0-9+\\-\\s()]+$'),
    CONSTRAINT chk_health_policies_contract_length CHECK (number_contract IS NULL OR CHAR_LENGTH(number_contract) >= 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;