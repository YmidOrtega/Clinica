-- Agregar campos de soft delete mejorado
ALTER TABLE patients
    ADD COLUMN deleted_at TIMESTAMP NULL AFTER updated_at,
    ADD COLUMN deleted_by BIGINT NULL AFTER deleted_at,
    ADD COLUMN deletion_reason VARCHAR(500) NULL AFTER deleted_by,
    ADD COLUMN can_be_restored BOOLEAN NOT NULL DEFAULT TRUE AFTER deletion_reason;

-- Crear índices para optimizar consultas de soft delete
CREATE INDEX idx_patients_deleted_at ON patients(deleted_at);
CREATE INDEX idx_patients_deleted_by ON patients(deleted_by);
CREATE INDEX idx_patients_status_deleted ON patients(status, deleted_at);

-- Comentarios para documentación
ALTER TABLE patients
    MODIFY COLUMN deleted_at TIMESTAMP NULL
    COMMENT 'Fecha en que el paciente fue marcado como eliminado',

    MODIFY COLUMN deleted_by BIGINT NULL
    COMMENT 'ID del usuario que eliminó el paciente',

    MODIFY COLUMN deletion_reason VARCHAR(500) NULL
    COMMENT 'Razón por la cual se eliminó el paciente',

    MODIFY COLUMN can_be_restored BOOLEAN NOT NULL DEFAULT TRUE
    COMMENT 'Indica si el paciente puede ser restaurado después de eliminarse';