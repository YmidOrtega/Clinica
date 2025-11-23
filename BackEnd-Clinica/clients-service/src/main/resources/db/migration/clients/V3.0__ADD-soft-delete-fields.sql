-- =====================================================
-- 1. HEALTH_PROVIDERS - Campos de soft delete
-- =====================================================
ALTER TABLE health_providers
    ADD COLUMN deleted_at TIMESTAMP NULL COMMENT 'Fecha y hora de eliminación lógica',
    ADD COLUMN deleted_by BIGINT NULL COMMENT 'ID del usuario que eliminó el registro',
    ADD COLUMN deletion_reason VARCHAR(500) NULL COMMENT 'Razón de la eliminación';

-- Índices para soft delete
CREATE INDEX idx_health_providers_deleted_at ON health_providers(deleted_at);
CREATE INDEX idx_health_providers_deleted_by ON health_providers(deleted_by);

-- Índice compuesto para consultas de registros activos (no eliminados)
CREATE INDEX idx_health_providers_active_not_deleted
    ON health_providers(active, deleted_at);

-- =====================================================
-- 2. CONTRACTS - Campos de soft delete
-- =====================================================
ALTER TABLE contracts
    ADD COLUMN deleted_at TIMESTAMP NULL COMMENT 'Fecha y hora de eliminación lógica',
    ADD COLUMN deleted_by BIGINT NULL COMMENT 'ID del usuario que eliminó el registro',
    ADD COLUMN deletion_reason VARCHAR(500) NULL COMMENT 'Razón de la eliminación';

-- Índices para soft delete
CREATE INDEX idx_contracts_deleted_at ON contracts(deleted_at);
CREATE INDEX idx_contracts_deleted_by ON contracts(deleted_by);

-- Índice compuesto para consultas de registros activos (no eliminados)
CREATE INDEX idx_contracts_active_not_deleted
    ON contracts(active, deleted_at);

-- =====================================================
-- 3. PORTFOLIOS - Campos de soft delete (si aplica)
-- =====================================================
ALTER TABLE portfolios
    ADD COLUMN deleted_at TIMESTAMP NULL COMMENT 'Fecha y hora de eliminación lógica',
    ADD COLUMN deleted_by BIGINT NULL COMMENT 'ID del usuario que eliminó el registro',
    ADD COLUMN deletion_reason VARCHAR(500) NULL COMMENT 'Razón de la eliminación';

-- Índices para soft delete
CREATE INDEX idx_portfolios_deleted_at ON portfolios(deleted_at);
CREATE INDEX idx_portfolios_deleted_by ON portfolios(deleted_by);
