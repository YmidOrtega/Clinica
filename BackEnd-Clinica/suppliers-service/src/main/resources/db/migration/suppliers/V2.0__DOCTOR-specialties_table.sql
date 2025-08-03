-- ===============================
-- 1. ELIMINAR TABLA DOCTOR_SUBSPECIALTIES
-- ===============================
DROP TABLE IF EXISTS doctor_subspecialties;

-- ===============================
-- 2. CREAR TABLA DOCTOR_SPECIALTIES
-- ===============================
CREATE TABLE doctor_specialties (
    doctor_id BIGINT NOT NULL,
    specialty_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (doctor_id, specialty_id),

    -- Claves foráneas
    CONSTRAINT fk_doctor_specialties_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
    CONSTRAINT fk_doctor_specialties_specialty FOREIGN KEY (specialty_id) REFERENCES specialties(id) ON DELETE CASCADE,

    -- Índices para rendimiento
    INDEX idx_doctor_specialties_doctor (doctor_id),
    INDEX idx_doctor_specialties_specialty (specialty_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;