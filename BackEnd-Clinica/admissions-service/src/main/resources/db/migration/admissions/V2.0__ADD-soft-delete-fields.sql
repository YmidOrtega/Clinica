-- =====================================================
-- AGREGAR CAMPOS DE SOFT DELETE A ATTENTIONS
-- =====================================================
ALTER TABLE attentions
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD COLUMN deleted_by BIGINT NULL,
    ADD COLUMN deletion_reason VARCHAR(500) NULL;

-- Índices para consultas de soft delete
CREATE INDEX idx_attentions_deleted_at ON attentions(deleted_at);
CREATE INDEX idx_attentions_deleted_by ON attentions(deleted_by);

-- Índice compuesto para consultas de registros activos (no eliminados)
CREATE INDEX idx_attentions_active_not_deleted ON attentions(active, deleted_at);

COMMENT ON COLUMN attentions.deleted_at IS 'Fecha y hora de eliminación lógica';
COMMENT ON COLUMN attentions.deleted_by IS 'ID del usuario que eliminó el registro';
COMMENT ON COLUMN attentions.deletion_reason IS 'Razón de la eliminación';

-- =====================================================
-- AGREGAR CAMPOS DE SOFT DELETE A AUTHORIZATIONS
-- =====================================================
ALTER TABLE authorizations
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD COLUMN deleted_by BIGINT NULL,
    ADD COLUMN deletion_reason VARCHAR(500) NULL;

CREATE INDEX idx_authorizations_deleted_at ON authorizations(deleted_at);
CREATE INDEX idx_authorizations_deleted_by ON authorizations(deleted_by);

COMMENT ON COLUMN authorizations.deleted_at IS 'Fecha y hora de eliminación lógica';
COMMENT ON COLUMN authorizations.deleted_by IS 'ID del usuario que eliminó el registro';
COMMENT ON COLUMN authorizations.deletion_reason IS 'Razón de la eliminación';

-- =====================================================
-- AGREGAR CAMPOS DE SOFT DELETE A CONFIGURATION_SERVICES
-- =====================================================
ALTER TABLE configuration_services
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD COLUMN deleted_by BIGINT NULL,
    ADD COLUMN deletion_reason VARCHAR(500) NULL;

CREATE INDEX idx_configuration_services_deleted_at ON configuration_services(deleted_at);
CREATE INDEX idx_configuration_services_deleted_by ON configuration_services(deleted_by);

COMMENT ON COLUMN configuration_services.deleted_at IS 'Fecha y hora de eliminación lógica';
COMMENT ON COLUMN configuration_services.deleted_by IS 'ID del usuario que eliminó el registro';
COMMENT ON COLUMN configuration_services.deletion_reason IS 'Razón de la eliminación';