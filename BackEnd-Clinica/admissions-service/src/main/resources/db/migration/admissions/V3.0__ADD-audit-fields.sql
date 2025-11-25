-- =====================================================
-- AGREGAR CAMPOS DE AUDITORÍA A ATTENTIONS
-- =====================================================
ALTER TABLE attentions
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL;

-- Índices para consultas de auditoría
CREATE INDEX idx_attentions_created_by ON attentions(created_by);
CREATE INDEX idx_attentions_updated_by ON attentions(updated_by);

COMMENT ON COLUMN attentions.created_by IS 'ID del usuario que creó el registro';
COMMENT ON COLUMN attentions.updated_by IS 'ID del usuario que actualizó el registro por última vez';

-- =====================================================
-- AGREGAR CAMPOS DE AUDITORÍA A AUTHORIZATIONS
-- =====================================================
ALTER TABLE authorizations
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL;

CREATE INDEX idx_authorizations_created_by ON authorizations(created_by);
CREATE INDEX idx_authorizations_updated_by ON authorizations(updated_by);

COMMENT ON COLUMN authorizations.created_by IS 'ID del usuario que creó el registro';
COMMENT ON COLUMN authorizations.updated_by IS 'ID del usuario que actualizó el registro por última vez';

-- =====================================================
-- AGREGAR CAMPOS DE AUDITORÍA A CONFIGURATION_SERVICES
-- =====================================================
ALTER TABLE configuration_services
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL;

CREATE INDEX idx_configuration_services_created_by ON configuration_services(created_by);
CREATE INDEX idx_configuration_services_updated_by ON configuration_services(updated_by);

COMMENT ON COLUMN configuration_services.created_by IS 'ID del usuario que creó el registro';
COMMENT ON COLUMN configuration_services.updated_by IS 'ID del usuario que actualizó el registro por última vez';

-- =====================================================
-- AGREGAR CAMPOS DE AUDITORÍA A SERVICE_TYPES
-- =====================================================
ALTER TABLE service_types
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL;

CREATE INDEX idx_service_types_created_by ON service_types(created_by);
CREATE INDEX idx_service_types_updated_by ON service_types(updated_by);

COMMENT ON COLUMN service_types.created_by IS 'ID del usuario que creó el registro';
COMMENT ON COLUMN service_types.updated_by IS 'ID del usuario que actualizó el registro por última vez';

-- =====================================================
-- AGREGAR CAMPOS DE AUDITORÍA A CARE_TYPES
-- =====================================================
ALTER TABLE care_types
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL;

CREATE INDEX idx_care_types_created_by ON care_types(created_by);
CREATE INDEX idx_care_types_updated_by ON care_types(updated_by);

COMMENT ON COLUMN care_types.created_by IS 'ID del usuario que creó el registro';
COMMENT ON COLUMN care_types.updated_by IS 'ID del usuario que actualizó el registro por última vez';

-- =====================================================
-- AGREGAR CAMPOS DE AUDITORÍA A LOCATIONS
-- =====================================================
ALTER TABLE locations
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL;

CREATE INDEX idx_locations_created_by ON locations(created_by);
CREATE INDEX idx_locations_updated_by ON locations(updated_by);

COMMENT ON COLUMN locations.created_by IS 'ID del usuario que creó el registro';
COMMENT ON COLUMN locations.updated_by IS 'ID del usuario que actualizó el registro por última vez';