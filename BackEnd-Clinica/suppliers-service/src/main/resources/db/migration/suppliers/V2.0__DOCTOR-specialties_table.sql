DROP TABLE IF EXISTS doctor_subspecialties;
DROP TABLE IF EXISTS doctor_specialties;

-- ===============================
-- 1. TABLA DOCTOR_SPECIALTIES (ManyToMany Doctor–Specialty)
-- ===============================
CREATE TABLE doctor_specialties (
    doctor_id BIGINT NOT NULL,
    specialty_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (doctor_id, specialty_id),

    -- Claves foráneas
    CONSTRAINT fk_doctor_specialties_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
    CONSTRAINT fk_doctor_specialties_specialty FOREIGN KEY (specialty_id) REFERENCES specialties(id) ON DELETE CASCADE,

    -- Índices
    INDEX idx_doctor_specialties_doctor (doctor_id),
    INDEX idx_doctor_specialties_specialty (specialty_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===============================
-- 2. TABLA DOCTOR_SUBSPECIALTIES
-- ===============================
CREATE TABLE doctor_subspecialties (
    doctor_id BIGINT NOT NULL,
    subspecialty_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (doctor_id, subspecialty_id),

    -- Claves foráneas
    CONSTRAINT fk_doctor_subspecialties_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
    CONSTRAINT fk_doctor_subspecialties_subspecialty FOREIGN KEY (subspecialty_id) REFERENCES sub_specialties(id) ON DELETE CASCADE,

    -- Índices
    INDEX idx_doctor_subspecialties_doctor (doctor_id),
    INDEX idx_doctor_subspecialties_subspecialty (subspecialty_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;