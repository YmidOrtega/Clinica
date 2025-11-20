-- src/main/resources/db/migration/suppliers/V3.0__ADD-audit-fields-to-doctors.sql

-- =====================================================
-- AGREGAR CAMPOS DE AUDITORÍA A TABLA DOCTORS
-- =====================================================

ALTER TABLE doctors
    ADD COLUMN created_by BIGINT NULL AFTER active,
    ADD COLUMN updated_by BIGINT NULL AFTER created_by;

-- Índice para consultas de auditoría
CREATE INDEX idx_doctors_created_by ON doctors(created_by);
CREATE INDEX idx_doctors_updated_by ON doctors(updated_by);

-- Comentarios para documentación
ALTER TABLE doctors
    MODIFY COLUMN created_by BIGINT NULL COMMENT 'ID del usuario que creó el registro',
    MODIFY COLUMN updated_by BIGINT NULL COMMENT 'ID del usuario que actualizó el registro por última vez';