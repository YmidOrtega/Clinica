-- Configuración inicial
SET FOREIGN_KEY_CHECKS = 0;

-- ========================================
-- TABLA: doctors
-- ========================================
CREATE TABLE IF NOT EXISTS doctors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone_number VARCHAR(20) NOT NULL,
    specialty ENUM(
        'GENERAL_MEDICINE',
        'CARDIOLOGY',
        'DERMATOLOGY',
        'PEDIATRICS',
        'GYNECOLOGY',
        'ORTHOPEDICS',
        'NEUROLOGY',
        'PSYCHIATRY',
        'SURGERY',
        'INTERNAL_MEDICINE',
        'EMERGENCY',
        'ANESTHESIOLOGY',
        'RADIOLOGY',
        'PATHOLOGY',
        'OPHTHALMOLOGY',
        'OTOLARYNGOLOGY',
        'UROLOGY',
        'ENDOCRINOLOGY',
        'GASTROENTEROLOGY',
        'PULMONOLOGY'
    ) NOT NULL,
    address VARCHAR(200),
    license_number VARCHAR(20) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_doctor_first_name_length CHECK (CHAR_LENGTH(first_name) >= 2),
    CONSTRAINT chk_doctor_last_name_length CHECK (CHAR_LENGTH(last_name) >= 2),
    CONSTRAINT chk_doctor_email_format CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_doctor_phone_format CHECK (phone_number REGEXP '^\\+?[1-9][0-9]{1,14}$'),
    CONSTRAINT chk_doctor_license_length CHECK (CHAR_LENGTH(license_number) >= 5),

    -- Indexes
    INDEX idx_doctor_license (license_number),
    INDEX idx_doctor_email (email),
    INDEX idx_doctor_specialty (specialty),
    INDEX idx_doctor_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- TABLA: type_of_attention
-- ========================================
CREATE TABLE IF NOT EXISTS type_of_attention (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(10) NOT NULL UNIQUE,
    description VARCHAR(500),
    attention_category ENUM(
        'OUTPATIENT',
        'EMERGENCY',
        'HOSPITALIZATION',
        'SURGERY',
        'CONSULTATION',
        'PROCEDURE'
    ),
    active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Constraints
    CONSTRAINT chk_attention_type_name_length CHECK (CHAR_LENGTH(name) >= 2),
    CONSTRAINT chk_attention_type_code_length CHECK (CHAR_LENGTH(code) >= 1),

    -- Indexes
    INDEX idx_attention_type_name (name),
    INDEX idx_attention_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- TABLA: attentions
-- ========================================
CREATE TABLE IF NOT EXISTS attentions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    attention_movements BOOLEAN NOT NULL DEFAULT TRUE,
    active_attention BOOLEAN NOT NULL DEFAULT TRUE,
    pre_admission BOOLEAN NOT NULL DEFAULT FALSE,
    invoiced BOOLEAN NOT NULL DEFAULT FALSE,

    -- Foreign Keys
    user_id BIGINT NOT NULL,
    type_of_attention_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    health_policy_id BIGINT,
    doctor_id BIGINT NOT NULL,
    site_id BIGINT,

    -- Companion Information
    companion_name VARCHAR(100),
    companion_phone VARCHAR(20),
    relationship VARCHAR(50),

    -- General Information
    observations VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Location and Zone
    zone ENUM('URBAN', 'RURAL') COMMENT 'Assuming basic zone types',

    -- Attention Classification
    entry_route ENUM(
        'EMERGENCY',
        'CONSULTATION',
        'REFERRAL',
        'HOSPITALIZATION',
        'SURGERY'
    ),
    entry_service ENUM(
        'EMERGENCY',
        'INTERNAL_MEDICINE',
        'SURGERY',
        'PEDIATRICS',
        'GYNECOLOGY',
        'ORTHOPEDICS',
        'CARDIOLOGY',
        'ICU',
        'GENERAL_WARD'
    ),
    location_service ENUM(
        'EMERGENCY',
        'INTERNAL_MEDICINE',
        'SURGERY',
        'PEDIATRICS',
        'GYNECOLOGY',
        'ORTHOPEDICS',
        'CARDIOLOGY',
        'ICU',
        'GENERAL_WARD'
    ),
    cause ENUM(
        'ILLNESS',
        'ACCIDENT',
        'WORK_ACCIDENT',
        'TRAFFIC_ACCIDENT',
        'VIOLENCE',
        'MATERNITY',
        'PREVENTION',
        'CONTROL',
        'EMERGENCY',
        'ROUTINE_CHECKUP',
        'VACCINATION',
        'OTHER'
    ) NOT NULL,

    -- Constraints
    CONSTRAINT chk_companion_phone_format CHECK (companion_phone IS NULL OR companion_phone REGEXP '^\\+?[1-9][0-9]{1,14}$'),
    CONSTRAINT chk_companion_name_length CHECK (companion_name IS NULL OR CHAR_LENGTH(companion_name) <= 100),
    CONSTRAINT chk_relationship_length CHECK (relationship IS NULL OR CHAR_LENGTH(relationship) <= 50),
    CONSTRAINT chk_observations_length CHECK (observations IS NULL OR CHAR_LENGTH(observations) <= 1000),

    -- Foreign Key Constraints
    CONSTRAINT fk_attention_type FOREIGN KEY (type_of_attention_id) REFERENCES type_of_attention(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_attention_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Note: The following FKs assume these tables exist. Uncomment when ready:
    CONSTRAINT fk_attention_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_attention_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_attention_health_policy FOREIGN KEY (health_policy_id) REFERENCES health_policies(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_attention_site FOREIGN KEY (site_id) REFERENCES sites(id) ON DELETE SET NULL ON UPDATE CASCADE,

    -- Indexes
    INDEX idx_attention_patient (patient_id),
    INDEX idx_attention_doctor (doctor_id),
    INDEX idx_attention_created (created_at),
    INDEX idx_attention_active (active),
    INDEX idx_attention_status (active_attention),
    INDEX idx_attention_user (user_id),
    INDEX idx_attention_type (type_of_attention_id),
    INDEX idx_attention_invoiced (invoiced),
    INDEX idx_attention_cause (cause)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Verificar si la tabla patients existe antes de modificarla
SET @table_exists = (SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
    AND table_name = 'patients');

-- Solo ejecutar si la tabla patients existe
SET @sql = CASE
    WHEN @table_exists > 0 THEN
        'ALTER TABLE patients
         ADD CONSTRAINT fk_attention_patient
         FOREIGN KEY (id) REFERENCES attentions(patient_id)
         ON DELETE RESTRICT ON UPDATE CASCADE'
    ELSE
        'SELECT "Tabla patients no encontrada. La relación se establecerá cuando se cree la tabla." as message'
END;

SET FOREIGN_KEY_CHECKS = 1;

