-- =====================================================
-- 1. TABLA DOCTOR_SCHEDULES (Horarios de Atención)
-- =====================================================
CREATE TABLE IF NOT EXISTS doctor_schedules (
                                                id BIGINT NOT NULL AUTO_INCREMENT,
                                                doctor_id BIGINT NOT NULL,
                                                day_of_week VARCHAR(20) NOT NULL,
                                                start_time TIME NOT NULL,
                                                end_time TIME NOT NULL,
                                                active BOOLEAN NOT NULL DEFAULT TRUE,
                                                notes VARCHAR(500),
                                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                                PRIMARY KEY (id),

    -- Restricción de unicidad: un doctor no puede tener horarios duplicados
                                                CONSTRAINT uk_doctor_day_time UNIQUE (doctor_id, day_of_week, start_time, end_time),

    -- Clave foránea
                                                CONSTRAINT fk_schedule_doctor FOREIGN KEY (doctor_id)
                                                    REFERENCES doctors(id) ON DELETE CASCADE,

    -- Índices para optimización de consultas
                                                INDEX idx_schedule_doctor (doctor_id),
                                                INDEX idx_schedule_day (day_of_week),
                                                INDEX idx_schedule_active (active),
                                                INDEX idx_schedule_doctor_day (doctor_id, day_of_week),

    -- Validación: hora de fin debe ser posterior a hora de inicio
                                                CONSTRAINT chk_schedule_time_range CHECK (end_time > start_time),

    -- Validación: día de la semana válido
                                                CONSTRAINT chk_schedule_day_valid CHECK (
                                                    day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
                                                    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. TABLA DOCTOR_UNAVAILABILITY (Ausencias y Permisos)
-- =====================================================
CREATE TABLE IF NOT EXISTS doctor_unavailability (
                                                     id BIGINT NOT NULL AUTO_INCREMENT,
                                                     doctor_id BIGINT NOT NULL,
                                                     type VARCHAR(50) NOT NULL,
                                                     start_date DATE NOT NULL,
                                                     end_date DATE NOT NULL,
                                                     reason VARCHAR(500),
                                                     approved BOOLEAN NOT NULL DEFAULT FALSE,
                                                     approved_by VARCHAR(100),
                                                     approved_at TIMESTAMP NULL,
                                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                                     PRIMARY KEY (id),

    -- Clave foránea
                                                     CONSTRAINT fk_unavailability_doctor FOREIGN KEY (doctor_id)
                                                         REFERENCES doctors(id) ON DELETE CASCADE,

    -- Índices para optimización de consultas
                                                     INDEX idx_unavailability_doctor (doctor_id),
                                                     INDEX idx_unavailability_dates (start_date, end_date),
                                                     INDEX idx_unavailability_type (type),
                                                     INDEX idx_unavailability_approved (approved),
                                                     INDEX idx_unavailability_doctor_dates (doctor_id, start_date, end_date),

    -- Validación: fecha de fin debe ser posterior o igual a fecha de inicio
                                                     CONSTRAINT chk_unavailability_date_range CHECK (end_date >= start_date),

    -- Validación: tipo de ausencia válido
                                                     CONSTRAINT chk_unavailability_type_valid CHECK (
                                                         type IN ('VACATION', 'SICK_LEAVE', 'MATERNITY_LEAVE', 'PATERNITY_LEAVE',
                                                                  'TRAINING', 'CONFERENCE', 'PERSONAL_LEAVE', 'EMERGENCY',
                                                                  'SABBATICAL', 'OTHER')
                                                         ),

    -- Validación: si está aprobado, debe tener quién lo aprobó y cuándo
                                                     CONSTRAINT chk_unavailability_approval_complete CHECK (
                                                         (approved = FALSE) OR
                                                         (approved = TRUE AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
                                                         )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. COMENTARIOS EN LAS TABLAS
-- =====================================================
ALTER TABLE doctor_schedules
    COMMENT = 'Horarios de atención configurados para cada doctor por día de la semana';

ALTER TABLE doctor_unavailability
    COMMENT = 'Periodos de no disponibilidad de doctores (vacaciones, permisos, etc.)';
