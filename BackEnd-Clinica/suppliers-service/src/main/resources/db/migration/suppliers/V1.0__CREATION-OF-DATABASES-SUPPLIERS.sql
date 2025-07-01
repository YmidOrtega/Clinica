-- Eliminar tablas en orden correcto (por dependencias)
DROP TABLE IF EXISTS doctor_service_types;
DROP TABLE IF EXISTS doctor_subspecialties;
DROP TABLE IF EXISTS doctors;
DROP TABLE IF EXISTS sub_specialties;
DROP TABLE IF EXISTS specialties;

-- =====================================================
-- 1. TABLA SPECIALTIES (Especialidades)
-- =====================================================
CREATE TABLE specialties (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code_speciality INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Restricciones de unicidad
    UNIQUE KEY uk_specialties_code (code_speciality),
    UNIQUE KEY uk_specialties_name (name),

    -- Índices para rendimiento
    INDEX idx_specialties_active (active),
    INDEX idx_specialties_name (name),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_specialties_name_not_empty CHECK (TRIM(name) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. TABLA SUB_SPECIALTIES (Subespecialidades)
-- =====================================================
CREATE TABLE sub_specialties (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code_sub_specialty INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    speciality_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Restricciones de unicidad
    UNIQUE KEY uk_sub_specialties_code (code_sub_specialty),
    UNIQUE KEY uk_sub_specialties_name (name),

    -- Clave foránea
    CONSTRAINT fk_sub_specialties_speciality FOREIGN KEY (speciality_id) REFERENCES specialties(id) ON DELETE CASCADE,

    -- Índices para rendimiento
    INDEX idx_sub_specialties_speciality (speciality_id),
    INDEX idx_sub_specialties_name (name),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_sub_specialties_name_not_empty CHECK (TRIM(name) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. TABLA DOCTORS (Doctores)
-- =====================================================
CREATE TABLE doctors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_code INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    identification_number VARCHAR(20) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    email VARCHAR(150) NOT NULL,
    license_number VARCHAR(50) NOT NULL,
    address VARCHAR(200),
    hourly_rate DECIMAL(10,2),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Restricciones de unicidad
    UNIQUE KEY uk_doctors_provider_code (provider_code),
    UNIQUE KEY uk_doctors_identification_number (identification_number),
    UNIQUE KEY uk_doctors_email (email),
    UNIQUE KEY uk_doctors_license_number (license_number),

    -- Índices para rendimiento (coinciden con los @Index de JPA)
    INDEX idx_doctor_license (license_number),
    INDEX idx_doctor_email (email),
    INDEX idx_doctor_active (active),
    INDEX idx_doctors_name_lastname (name, last_name),
    INDEX idx_doctors_created_at (created_at),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_doctors_name_not_empty CHECK (TRIM(name) != ''),
    CONSTRAINT chk_doctors_last_name_not_empty CHECK (TRIM(last_name) != ''),
    CONSTRAINT chk_doctors_identification_not_empty CHECK (TRIM(identification_number) != ''),
    CONSTRAINT chk_doctors_phone_not_empty CHECK (TRIM(phone_number) != ''),
    CONSTRAINT chk_doctors_email_not_empty CHECK (TRIM(email) != ''),
    CONSTRAINT chk_doctors_license_not_empty CHECK (TRIM(license_number) != ''),
    CONSTRAINT chk_doctors_hourly_rate_positive CHECK (hourly_rate IS NULL OR hourly_rate >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 4. TABLA DOCTOR_SUBSPECIALTIES (Relación Many-to-Many)
-- =====================================================
CREATE TABLE doctor_subspecialties (
    doctor_id BIGINT NOT NULL,
    subspecialty_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (doctor_id, subspecialty_id),

    -- Claves foráneas
    CONSTRAINT fk_doctor_subspecialties_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
    CONSTRAINT fk_doctor_subspecialties_subspecialty FOREIGN KEY (subspecialty_id) REFERENCES sub_specialties(id) ON DELETE CASCADE,

    -- Índices para rendimiento
    INDEX idx_doctor_subspecialties_doctor (doctor_id),
    INDEX idx_doctor_subspecialties_subspecialty (subspecialty_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
