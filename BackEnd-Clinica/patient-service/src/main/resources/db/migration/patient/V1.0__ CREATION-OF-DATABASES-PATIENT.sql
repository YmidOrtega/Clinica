-- Eliminar tablas en orden correcto
DROP TABLE IF EXISTS patients;
DROP TABLE IF EXISTS occupations;
DROP TABLE IF EXISTS sites;

-- =====================================================
-- 1. TABLA SITES (Sitios/Ubicaciones)
-- =====================================================
CREATE TABLE sites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    city VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL,
    locality VARCHAR(100),
    neighborhood VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Índices para optimizar búsquedas geográficas
    INDEX idx_sites_city (city),
    INDEX idx_sites_department (department),
    INDEX idx_sites_country (country),
    INDEX idx_sites_location (city, department, country),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_sites_city_not_empty CHECK (TRIM(city) != ''),
    CONSTRAINT chk_sites_department_not_empty CHECK (TRIM(department) != ''),
    CONSTRAINT chk_sites_country_not_empty CHECK (TRIM(country) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. TABLA OCCUPATIONS (Ocupaciones)
-- =====================================================
CREATE TABLE occupations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Restricciones de unicidad
    UNIQUE KEY uk_occupations_name (name),

    -- Índices
    INDEX idx_occupations_active (is_active),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_occupations_name_not_empty CHECK (TRIM(name) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. TABLA PATIENTS (Pacientes)
-- =====================================================
CREATE TABLE patients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid CHAR(36) NOT NULL,
    identification_type ENUM(
        'CEDULA_DE_CIUDADANIA',
        'CEDULA_DE_EXTRANJERIA',
        'TARJETA_DE_IDENTIDAD',
        'PASAPORTE',
        'REGISTRO_CIVIL',
        'PERMISO_ESPECIAL_DE_PERMANENCIA',
        'DOCUMENTO_NACIONAL_DE_IDENTIFICACION'
    ) NOT NULL,
    identification_number VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    birth_site_id BIGINT NOT NULL,
    issuance_site_id BIGINT NOT NULL,
    disability ENUM(
        'NONE',
        'VISUAL',
        'AUDITIVA',
        'FISICA',
        'COGNITIVA',
        'PSICOSOCIAL',
        'MULTIPLE',
        'OTHER'
    ) DEFAULT 'NONE',
    language ENUM(
        'SPANISH',
        'ENGLISH',
        'FRENCH',
        'PORTUGUESE',
        'ITALIAN',
        'GERMAN',
        'MANDARIN',
        'JAPANESE',
        'ARABIC',
        'RUSSIAN'
    ) DEFAULT 'SPANISH',
    gender ENUM(
        'MASCULINE',
        'FEMININE',
        'OTHER',
        'NOT_DISCLOSED'
    ) NOT NULL,
    occupation_id BIGINT NOT NULL,
    marital_status ENUM(
        'SINGLE',
        'MARRIED',
        'DIVORCED',
        'WIDOWED',
        'SEPARATED',
        'COHABITING',
        'NOT_DISCLOSED'
    ) DEFAULT 'SINGLE',
    religion ENUM(
        'CATHOLIC',
        'PROTESTANT',
        'JEWISH',
        'MUSLIM',
        'BUDDHIST',
        'HINDU',
        'ATHEIST',
        'AGNOSTIC',
        'OTHER'
    ) DEFAULT 'CATHOLIC',
    type_of_affiliation ENUM(
        'BENEFICIARY',
        'CONTRIBUTOR',
        'POLIZA',
        'PREPAID_MEDICINE',
        'ARL',
        'SUBSIDIZED',
        'VOLUNTARY',
        'SPECIAL',
        'NONE'
    ) NOT NULL,
    affiliation_number VARCHAR(50),
    health_provider_nit VARCHAR(100) NOT NULL,
    health_policy_number VARCHAR(50),
    mothers_name VARCHAR(100),
    fathers_name VARCHAR(100),
    zone ENUM(
        'URBAN',
        'RURAL'
    ) DEFAULT 'URBAN',
    locality_site_id BIGINT NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(20),
    mobile VARCHAR(20),
    email VARCHAR(150) NOT NULL,
    status ENUM(
        'ALIVE',
        'DECEASED',
        'SUSPENDED',
        'DELETED'
    ) DEFAULT 'ALIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Restricciones de unicidad
    UNIQUE KEY uk_patients_uuid (uuid),
    UNIQUE KEY uk_patients_identification_number (identification_number),

    -- Claves foráneas
    CONSTRAINT fk_patients_birth_site FOREIGN KEY (birth_site_id) REFERENCES sites(id),
    CONSTRAINT fk_patients_issuance_site FOREIGN KEY (issuance_site_id) REFERENCES sites(id),
    CONSTRAINT fk_patients_occupation FOREIGN KEY (occupation_id) REFERENCES occupations(id),
    CONSTRAINT fk_patients_locality_site FOREIGN KEY (locality_site_id) REFERENCES sites(id),

    -- Índices de rendimiento
    INDEX idx_patients_identification_type (identification_type),
    INDEX idx_patients_identification_number (identification_number),
    INDEX idx_patients_name_lastname (name, last_name),
    INDEX idx_patients_date_birth (date_of_birth),
    INDEX idx_patients_gender (gender),
    INDEX idx_patients_status (status),
    INDEX idx_patients_email (email),
    INDEX idx_patients_created_at (created_at),
    INDEX idx_patients_affiliation (type_of_affiliation, affiliation_number),
    INDEX idx_patients_health_provider (health_provider_nit),

    -- Validaciones básicas de integridad solamente
    CONSTRAINT chk_patients_identification_number_not_empty CHECK (TRIM(identification_number) != ''),
    CONSTRAINT chk_patients_name_not_empty CHECK (TRIM(name) != ''),
    CONSTRAINT chk_patients_lastname_not_empty CHECK (TRIM(last_name) != ''),
    CONSTRAINT chk_patients_email_not_empty CHECK (TRIM(email) != ''),
    CONSTRAINT chk_patients_health_provider_not_empty CHECK (TRIM(health_provider_nit) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TRIGGERS PARA AUDITORIA Y VALIDACIONES ADICIONALES
-- =====================================================

DELIMITER //
CREATE TRIGGER tr_patients_validate_age_identification_type
BEFORE INSERT ON patients
FOR EACH ROW
BEGIN
    DECLARE user_age INT;

    -- Validación: fecha de nacimiento no puede ser futura
    IF NEW.date_of_birth > CURDATE() THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La fecha de nacimiento no puede ser una fecha futura.';
    END IF;

    SET user_age = TIMESTAMPDIFF(YEAR, NEW.date_of_birth, CURDATE());

    -- Validaciones por tipo de documento (solo para ciertos tipos)
    CASE NEW.identification_type
        WHEN 'REGISTRO_CIVIL' THEN
            IF user_age >= 7 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro Civil solo válido para menores de 7 años';
            END IF;
        WHEN 'TARJETA_DE_IDENTIDAD' THEN
            IF user_age < 7 OR user_age >= 18 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tarjeta de Identidad válida para edades entre 7 y 17 años';
            END IF;
        WHEN 'CEDULA_DE_CIUDADANIA' THEN
            IF user_age < 18 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cédula de Ciudadanía solo válida para mayores o iguales a 18 años';
            END IF;
        ELSE
            BEGIN END;
    END CASE;
END//

CREATE TRIGGER tr_patients_validate_age_identification_type_update
BEFORE UPDATE ON patients
FOR EACH ROW
BEGIN
    DECLARE user_age INT;

    -- Validación: fecha de nacimiento no puede ser futura
    IF NEW.date_of_birth > CURDATE() THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La fecha de nacimiento no puede ser una fecha futura.';
    END IF;

    SET user_age = TIMESTAMPDIFF(YEAR, NEW.date_of_birth, CURDATE());

    -- Validaciones por tipo de documento (solo para ciertos tipos)
    CASE NEW.identification_type
        WHEN 'REGISTRO_CIVIL' THEN
            IF user_age >= 7 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro Civil solo válido para menores de 7 años';
            END IF;
        WHEN 'TARJETA_DE_IDENTIDAD' THEN
            IF user_age < 7 OR user_age >= 18 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tarjeta de Identidad válida para edades entre 7 y 17 años';
            END IF;
        WHEN 'CEDULA_DE_CIUDADANIA' THEN
            IF user_age < 18 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cédula de Ciudadanía solo válida para mayores o iguales a 18 años';
            END IF;
        ELSE
            BEGIN END;
    END CASE;
END//
DELIMITER ;