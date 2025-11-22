-- =====================================================
-- 1. HEALTH_PROVIDERS - Campos de auditoría
-- =====================================================
ALTER TABLE health_providers
    ADD COLUMN created_by BIGINT NULL COMMENT 'ID del usuario que creó el registro',
    ADD COLUMN updated_by BIGINT NULL COMMENT 'ID del usuario que actualizó el registro';

-- Índices para búsquedas por usuario
CREATE INDEX idx_health_providers_created_by ON health_providers(created_by);
CREATE INDEX idx_health_providers_updated_by ON health_providers(updated_by);

-- =====================================================
-- 2. CONTRACTS - Campos de auditoría
-- =====================================================
ALTER TABLE contracts
    ADD COLUMN created_by BIGINT NULL COMMENT 'ID del usuario que creó el registro',
    ADD COLUMN updated_by BIGINT NULL COMMENT 'ID del usuario que actualizó el registro';

-- Índices para búsquedas por usuario
CREATE INDEX idx_contracts_created_by ON contracts(created_by);
CREATE INDEX idx_contracts_updated_by ON contracts(updated_by);

-- =====================================================
-- 3. PORTFOLIOS - Campos de auditoría (si aplica)
-- =====================================================
ALTER TABLE portfolios
    ADD COLUMN created_by BIGINT NULL COMMENT 'ID del usuario que creó el registro',
    ADD COLUMN updated_by BIGINT NULL COMMENT 'ID del usuario que actualizó el registro';

-- Índices para búsquedas por usuario
CREATE INDEX idx_portfolios_created_by ON portfolios(created_by);
CREATE INDEX idx_portfolios_updated_by ON portfolios(updated_by);