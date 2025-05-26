-- =====================================================
-- 1. TABLA SITES (Sitios/Ubicaciones)
-- =====================================================
DROP TABLE IF EXISTS sites;

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

    -- Validaciones
    CONSTRAINT chk_sites_city_not_empty CHECK (TRIM(city) != ''),
    CONSTRAINT chk_sites_department_not_empty CHECK (TRIM(department) != ''),
    CONSTRAINT chk_sites_country_not_empty CHECK (TRIM(country) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. TABLA OCCUPATIONS (Ocupaciones)
-- =====================================================
DROP TABLE IF EXISTS occupations;

CREATE TABLE occupations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Índices
    UNIQUE KEY uk_occupations_name (name),
    INDEX idx_occupations_active (is_active),

    -- Validaciones
    CONSTRAINT chk_occupations_name_not_empty CHECK (TRIM(name) != ''),
    CONSTRAINT chk_occupations_name_length CHECK (CHAR_LENGTH(name) >= 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. TABLA HEALTH_POLICIES (Pólizas de Salud)
-- =====================================================
DROP TABLE IF EXISTS health_policies;

CREATE TABLE health_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    social_reason VARCHAR(200) NOT NULL,
    nit INT NOT NULL,
    contract VARCHAR(100),
    number_contract VARCHAR(100),
    type VARCHAR(50),
    address VARCHAR(255),
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Índices únicos
    UNIQUE KEY uk_health_policies_nit (nit),
    UNIQUE KEY uk_health_policies_contract (number_contract),

    -- Índices de búsqueda
    INDEX idx_health_policies_social_reason (social_reason),
    INDEX idx_health_policies_type (type),
    INDEX idx_health_policies_active (is_active),

    -- Validaciones
    CONSTRAINT chk_health_policies_social_reason_not_empty CHECK (TRIM(social_reason) != ''),
    CONSTRAINT chk_health_policies_nit_positive CHECK (nit > 0),
    CONSTRAINT chk_health_policies_nit_valid CHECK (nit >= 100000 AND nit <= 999999999),
    CONSTRAINT chk_health_policies_phone_format CHECK (phone IS NULL OR phone REGEXP '^[0-9+\\-\\s()]+$'),
    CONSTRAINT chk_health_policies_contract_length CHECK (number_contract IS NULL OR CHAR_LENGTH(number_contract) >= 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 4. TABLA patients (Usuarios)
-- =====================================================
DROP TABLE IF EXISTS patients;

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
    identification VARCHAR(20) NOT NULL,
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
        'OTRA'
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
        'masculine',
        'feminine',
        'other',
        'notDisclosed'
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
    ) DEFAULT 'ATHEIST',
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
    health_policy_id BIGINT NOT NULL,
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status ENUM(
        'ALIVE',
        'DECEASED',
        'SUSPENDED',
        'DELETED'
    ) DEFAULT 'ALIVE',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Claves únicas
    UNIQUE KEY uk_patients_uuid (uuid),
    UNIQUE KEY uk_patients_identification (identification),

    -- Claves foráneas
    CONSTRAINT fk_patients_birth_site FOREIGN KEY (birth_site_id) REFERENCES sites(id),
    CONSTRAINT fk_patients_issuance_site FOREIGN KEY (issuance_site_id) REFERENCES sites(id),
    CONSTRAINT fk_patients_occupation FOREIGN KEY (occupation_id) REFERENCES occupations(id),
    CONSTRAINT fk_patients_health_policy FOREIGN KEY (health_policy_id) REFERENCES health_policies(id),
    CONSTRAINT fk_patients_locality_site FOREIGN KEY (locality_site_id) REFERENCES sites(id),

    -- Índices de rendimiento
    INDEX idx_patients_identification_type (identification_type),
    INDEX idx_patients_name_lastname (name, last_name),
    INDEX idx_patients_date_birth (date_of_birth),
    INDEX idx_patients_gender (gender),
    INDEX idx_patients_status (status),
    INDEX idx_patients_email (email),
    INDEX idx_patients_created_at (created_at),
    INDEX idx_patients_affiliation (type_of_affiliation, affiliation_number),

    -- Validaciones de negocio
    CONSTRAINT chk_patients_uuid_format CHECK (uuid REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'),
    CONSTRAINT chk_patients_identification_not_empty CHECK (TRIM(identification) != ''),
    CONSTRAINT chk_patients_identification_length CHECK (CHAR_LENGTH(identification) >= 3),
    CONSTRAINT chk_patients_name_not_empty CHECK (TRIM(name) != ''),
    CONSTRAINT chk_patients_lastname_not_empty CHECK (TRIM(last_name) != ''),
    CONSTRAINT chk_patients_name_length CHECK (CHAR_LENGTH(name) >= 2),
    CONSTRAINT chk_patients_lastname_length CHECK (CHAR_LENGTH(last_name) >= 2),
    CONSTRAINT chk_patients_birth_date_reasonable CHECK (date_of_birth >= '1900-01-01'),
    CONSTRAINT chk_patients_email_format CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_patients_phone_format CHECK (phone IS NULL OR phone REGEXP '^[0-9+\\-\\s()]+$'),
    CONSTRAINT chk_patients_mobile_format CHECK (mobile IS NULL OR mobile REGEXP '^[0-9+\\-\\s()]+$'),
    CONSTRAINT chk_patients_identification_cc_length CHECK (
        identification_type != 'CEDULA_DE_CIUDADANIA' OR
        (CHAR_LENGTH(identification) >= 6 AND CHAR_LENGTH(identification) <= 12)
    ),
    CONSTRAINT chk_patients_identification_numeric CHECK (
        identification_type NOT IN ('CEDULA_DE_CIUDADANIA', 'TARJETA_DE_IDENTIDAD', 'CEDULA_DE_EXTRANJERIA') OR
        identification REGEXP '^[0-9]+$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TRIGGERS PARA AUDITORIA Y VALIDACIONES ADICIONALES
-- =====================================================

-- Trigger para validar edad mínima según tipo de identificación
DELIMITER //
CREATE TRIGGER tr_patients_validate_age_identification_type
BEFORE INSERT ON patients
FOR EACH ROW
BEGIN
    DECLARE user_age INT;
    SET user_age = TIMESTAMPDIFF(YEAR, NEW.date_of_birth, CURDATE());

    -- Validaciones por tipo de documento
    CASE NEW.identification_type
        WHEN 'TARJETA_DE_IDENTIDAD' THEN
            IF user_age >= 18 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tarjeta de Identidad solo válida para menores de 18 años';
            END IF;
        WHEN 'CEDULA_DE_CIUDADANIA' THEN
            IF user_age < 18 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cédula de Ciudadanía solo válida para mayores de 18 años';
            END IF;
        WHEN 'REGISTRO_CIVIL' THEN
            IF user_age >= 7 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro Civil solo válido para menores de 7 años';
            END IF;
    END CASE;

    -- Validación general para que la fecha de nacimiento no sea futura
    IF NEW.date_of_birth > CURDATE() THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La fecha de nacimiento no puede ser una fecha futura.';
    END IF;

    -- Validación general para la edad sea válida (no negativa)
    IF DATEDIFF(CURDATE(), NEW.date_of_birth) < 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La fecha de nacimiento no puede resultar en una edad negativa.';
    END IF;

END//
DELIMITER ;

-- Trigger para actualizar timestamp en updates
DELIMITER //
CREATE TRIGGER tr_patients_update_timestamp
BEFORE UPDATE ON patients
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END//
DELIMITER ;
