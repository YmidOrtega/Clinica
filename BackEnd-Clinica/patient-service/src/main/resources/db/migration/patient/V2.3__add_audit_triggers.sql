DELIMITER //

-- =====================================================
-- TRIGGER: Validar eliminación de paciente
-- =====================================================
CREATE TRIGGER tr_patients_before_soft_delete
    BEFORE UPDATE ON patients
    FOR EACH ROW
BEGIN
    -- Si se está cambiando el status a DELETED
    IF NEW.status = 'DELETED' AND OLD.status != 'DELETED' THEN
        -- Validar que se proporcione una razón
        IF NEW.deletion_reason IS NULL OR TRIM(NEW.deletion_reason) = '' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Debe proporcionar una razón para eliminar el paciente';
        END IF;

        -- Validar que se especifique quién eliminó
        IF NEW.deleted_by IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Debe especificar el usuario que elimina el paciente';
        END IF;

        -- Establecer fecha de eliminación automáticamente
        IF NEW.deleted_at IS NULL THEN
            SET NEW.deleted_at = NOW();
        END IF;
    END IF;
END//

-- =====================================================
-- TRIGGER: Validar restauración de paciente
-- =====================================================
CREATE TRIGGER tr_patients_before_restore
    BEFORE UPDATE ON patients
    FOR EACH ROW
BEGIN
    -- Si se está restaurando un paciente (de DELETED a otro status)
    IF OLD.status = 'DELETED' AND NEW.status != 'DELETED' THEN
        -- Validar que el paciente pueda ser restaurado
        IF OLD.can_be_restored = FALSE THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Este paciente no puede ser restaurado';
        END IF;

        -- Limpiar campos de eliminación
        SET NEW.deleted_at = NULL;
        SET NEW.deleted_by = NULL;
        SET NEW.deletion_reason = NULL;
    END IF;
END//

-- =====================================================
-- TRIGGER: Crear historia médica automáticamente
-- =====================================================
CREATE TRIGGER tr_patients_create_medical_history
    AFTER INSERT ON patients
    FOR EACH ROW
BEGIN
    -- Crear historia médica básica para cada nuevo paciente
    INSERT INTO medical_histories (
        patient_id,
        created_at,
        updated_at,
        created_by
    ) VALUES (
                 NEW.id,
                 NOW(),
                 NOW(),
                 NEW.id
             );
END//

-- =====================================================
-- TRIGGER: Validar alergias críticas
-- =====================================================
CREATE TRIGGER tr_allergies_validate_critical
    BEFORE INSERT ON allergies
    FOR EACH ROW
BEGIN
    -- Si es una alergia severa o amenaza vital, debe estar verificada
    IF NEW.severity IN ('SEVERE', 'LIFE_THREATENING') AND NEW.verified = FALSE THEN
        -- Establecer como no verificada pero registrar advertencia
        SET NEW.notes = CONCAT(
                IFNULL(NEW.notes, ''),
                '\n[ADVERTENCIA] Alergia crítica sin verificar - Requiere confirmación médica urgente'
                        );
    END IF;
END//

-- =====================================================
-- TRIGGER: Validar medicamentos activos
-- =====================================================
CREATE TRIGGER tr_medications_validate_active
    BEFORE INSERT ON current_medications
    FOR EACH ROW
BEGIN
    -- Si la fecha de fin ya pasó, marcar como inactivo
    IF NEW.end_date IS NOT NULL AND NEW.end_date < CURDATE() THEN
        SET NEW.active = FALSE;
    END IF;

    -- Si está descontinuado, no puede estar activo
    IF NEW.discontinued = TRUE THEN
        SET NEW.active = FALSE;
        IF NEW.discontinued_date IS NULL THEN
            SET NEW.discontinued_date = CURDATE();
        END IF;
    END IF;
END//

-- =====================================================
-- TRIGGER: Validar fechas de vacunación
-- =====================================================
CREATE TRIGGER tr_vaccination_validate_dates
    BEFORE INSERT ON vaccination_records
    FOR EACH ROW
BEGIN
    -- La fecha de administración no puede ser futura
    IF NEW.administered_date > CURDATE() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La fecha de administración no puede ser futura';
    END IF;

    -- La próxima dosis debe ser después de la fecha de administración
    IF NEW.next_dose_date IS NOT NULL AND NEW.next_dose_date <= NEW.administered_date THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La fecha de la próxima dosis debe ser posterior a la fecha de administración';
    END IF;

    -- La fecha de vencimiento debe ser posterior a la fecha de administración
    IF NEW.expiration_date IS NOT NULL AND NEW.expiration_date <= NEW.administered_date THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La fecha de vencimiento debe ser posterior a la fecha de administración';
    END IF;
END//

DELIMITER ;