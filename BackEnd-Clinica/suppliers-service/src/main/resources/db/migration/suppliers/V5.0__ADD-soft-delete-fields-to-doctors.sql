-- src/main/resources/db/migration/suppliers/V5.0__ADD-soft-delete-fields-to-doctors.sql

-- =====================================================
-- AGREGAR CAMPOS DE SOFT DELETE A TABLA DOCTORS
-- =====================================================

ALTER TABLE doctors
    ADD COLUMN deleted_at TIMESTAMP NULL AFTER updated_by,
    ADD COLUMN deleted_by BIGINT NULL AFTER deleted_at,
    ADD COLUMN deletion_reason VARCHAR(500) NULL AFTER deleted_by;

-- Índices para consultas de soft delete
CREATE INDEX idx_doctors_deleted_at ON doctors(deleted_at);
CREATE INDEX idx_doctors_deleted_by ON doctors(deleted_by);

-- Comentarios para documentación
ALTER TABLE doctors
    MODIFY COLUMN deleted_at TIMESTAMP NULL COMMENT 'Fecha y hora en que se eliminó el registro (soft delete)',
    MODIFY COLUMN deleted_by BIGINT NULL COMMENT 'ID del usuario que eliminó el registro',
    MODIFY COLUMN deletion_reason VARCHAR(500) NULL COMMENT 'Razón por la cual se eliminó el registro';
