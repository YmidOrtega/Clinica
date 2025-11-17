-- =====================================================
-- 1. TABLA MEDICAL_HISTORIES (Historia Clínica Completa)
-- =====================================================
CREATE TABLE medical_histories (
                                   id BIGINT NOT NULL AUTO_INCREMENT,
                                   patient_id BIGINT NOT NULL UNIQUE,
                                   blood_type ENUM(
        'A_POSITIVE', 'A_NEGATIVE',
        'B_POSITIVE', 'B_NEGATIVE',
        'AB_POSITIVE', 'AB_NEGATIVE',
        'O_POSITIVE', 'O_NEGATIVE',
        'UNKNOWN'
    ) DEFAULT 'UNKNOWN',
                                   rh_factor VARCHAR(10),
                                   blood_pressure VARCHAR(20),
                                   weight DOUBLE,
                                   height DOUBLE,
                                   bmi DOUBLE,
                                   smoking_status VARCHAR(50),
                                   alcohol_consumption VARCHAR(50),
                                   exercise_frequency VARCHAR(50),
                                   diet_type VARCHAR(50),
                                   notes TEXT,
                                   last_checkup_date DATE,
                                   next_checkup_date DATE,
                                   has_insurance BOOLEAN DEFAULT FALSE,
                                   insurance_provider VARCHAR(200),
                                   insurance_number VARCHAR(100),
                                   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                   created_by BIGINT,
                                   updated_by BIGINT,

                                   PRIMARY KEY (id),

    -- Clave foránea
                                   CONSTRAINT fk_medical_history_patient FOREIGN KEY (patient_id)
                                       REFERENCES patients(id) ON DELETE CASCADE,

    -- Índices
                                   INDEX idx_medical_history_patient (patient_id),
                                   INDEX idx_medical_history_updated (updated_at),
                                   INDEX idx_medical_history_blood_type (blood_type),
                                   INDEX idx_medical_history_next_checkup (next_checkup_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. TABLA ALLERGIES (Alergias)
-- =====================================================
CREATE TABLE allergies (
                           id BIGINT NOT NULL AUTO_INCREMENT,
                           patient_id BIGINT NOT NULL,
                           allergen VARCHAR(200) NOT NULL,
                           severity ENUM(
        'MILD',
        'MODERATE',
        'SEVERE',
        'LIFE_THREATENING'
    ) NOT NULL DEFAULT 'MILD',
                           reaction_type ENUM(
        'RESPIRATORY',
        'SKIN',
        'GASTROINTESTINAL',
        'CARDIOVASCULAR',
        'NEUROLOGICAL',
        'OCULAR',
        'ANAPHYLAXIS',
        'OTHER'
    ),
                           symptoms TEXT,
                           diagnosed_date DATE,
                           diagnosed_by VARCHAR(200),
                           treatment TEXT,
                           notes TEXT,
                           active BOOLEAN NOT NULL DEFAULT TRUE,
                           verified BOOLEAN NOT NULL DEFAULT FALSE,
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           created_by BIGINT,
                           updated_by BIGINT,

                           PRIMARY KEY (id),

    -- Clave foránea
                           CONSTRAINT fk_allergy_patient FOREIGN KEY (patient_id)
                               REFERENCES patients(id) ON DELETE CASCADE,

    -- Índices
                           INDEX idx_allergy_patient (patient_id),
                           INDEX idx_allergy_active (active),
                           INDEX idx_allergy_severity (severity),
                           INDEX idx_allergy_allergen (allergen),
                           INDEX idx_allergy_patient_active (patient_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. TABLA CHRONIC_DISEASES (Enfermedades Crónicas)
-- =====================================================
CREATE TABLE chronic_diseases (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  patient_id BIGINT NOT NULL,
                                  disease_name VARCHAR(200) NOT NULL,
                                  icd10_code VARCHAR(10),
                                  diagnosed_date DATE,
                                  diagnosed_by VARCHAR(200),
                                  severity ENUM(
        'CONTROLLED',
        'PARTIALLY_CONTROLLED',
        'UNCONTROLLED',
        'CRITICAL',
        'IN_REMISSION'
    ) NOT NULL DEFAULT 'CONTROLLED',
                                  treatment_plan TEXT,
                                  complications TEXT,
                                  last_flare_date DATE,
                                  monitoring_frequency VARCHAR(100),
                                  notes TEXT,
                                  active BOOLEAN NOT NULL DEFAULT TRUE,
                                  requires_specialist BOOLEAN NOT NULL DEFAULT FALSE,
                                  specialist_type VARCHAR(100),
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  created_by BIGINT,
                                  updated_by BIGINT,

                                  PRIMARY KEY (id),

    -- Clave foránea
                                  CONSTRAINT fk_chronic_disease_patient FOREIGN KEY (patient_id)
                                      REFERENCES patients(id) ON DELETE CASCADE,

    -- Índices
                                  INDEX idx_chronic_disease_patient (patient_id),
                                  INDEX idx_chronic_disease_active (active),
                                  INDEX idx_chronic_disease_severity (severity),
                                  INDEX idx_chronic_disease_icd10 (icd10_code),
                                  INDEX idx_chronic_disease_name (disease_name),
                                  INDEX idx_chronic_disease_patient_active (patient_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 4. TABLA CURRENT_MEDICATIONS (Medicamentos Actuales)
-- =====================================================
CREATE TABLE current_medications (
                                     id BIGINT NOT NULL AUTO_INCREMENT,
                                     patient_id BIGINT NOT NULL,
                                     medication_name VARCHAR(200) NOT NULL,
                                     generic_name VARCHAR(200),
                                     dosage VARCHAR(100) NOT NULL,
                                     frequency VARCHAR(100) NOT NULL,
                                     route ENUM(
        'ORAL',
        'INTRAVENOUS',
        'INTRAMUSCULAR',
        'SUBCUTANEOUS',
        'TOPICAL',
        'INHALATION',
        'RECTAL',
        'OPHTHALMIC',
        'OTIC',
        'NASAL',
        'TRANSDERMAL',
        'SUBLINGUAL',
        'OTHER'
    ),
                                     instructions TEXT,
                                     start_date DATE NOT NULL,
                                     end_date DATE,
                                     prescribed_by VARCHAR(200) NOT NULL,
                                     prescribed_by_id BIGINT,
                                     prescription_number VARCHAR(100),
                                     pharmacy VARCHAR(200),
                                     refills_remaining INT,
                                     reason TEXT,
                                     side_effects TEXT,
                                     interactions TEXT,
                                     notes TEXT,
                                     active BOOLEAN NOT NULL DEFAULT TRUE,
                                     discontinued BOOLEAN NOT NULL DEFAULT FALSE,
                                     discontinued_date DATE,
                                     discontinued_reason VARCHAR(500),
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     created_by BIGINT,
                                     updated_by BIGINT,

                                     PRIMARY KEY (id),

    -- Clave foránea
                                     CONSTRAINT fk_medication_patient FOREIGN KEY (patient_id)
                                         REFERENCES patients(id) ON DELETE CASCADE,

    -- Índices
                                     INDEX idx_medication_patient (patient_id),
                                     INDEX idx_medication_active (active),
                                     INDEX idx_medication_end_date (end_date),
                                     INDEX idx_medication_name (medication_name),
                                     INDEX idx_medication_patient_active (patient_id, active),
                                     INDEX idx_medication_prescribed_by (prescribed_by_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 5. TABLA FAMILY_HISTORIES (Antecedentes Familiares)
-- =====================================================
CREATE TABLE family_histories (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  patient_id BIGINT NOT NULL,
                                  relationship ENUM(
        'FATHER',
        'MOTHER',
        'BROTHER',
        'SISTER',
        'PATERNAL_GRANDFATHER',
        'PATERNAL_GRANDMOTHER',
        'MATERNAL_GRANDFATHER',
        'MATERNAL_GRANDMOTHER',
        'SON',
        'DAUGHTER',
        'UNCLE',
        'AUNT',
        'COUSIN',
        'OTHER'
    ) NOT NULL,
                                  relative_name VARCHAR(200),
                                  condition_name VARCHAR(200) NOT NULL,
                                  icd10_code VARCHAR(10),
                                  age_of_onset INT,
                                  current_status VARCHAR(50),
                                  age_at_death INT,
                                  cause_of_death VARCHAR(200),
                                  severity VARCHAR(50),
                                  treatment_received TEXT,
                                  genetic_risk BOOLEAN NOT NULL DEFAULT FALSE,
                                  screening_recommended BOOLEAN NOT NULL DEFAULT FALSE,
                                  screening_details TEXT,
                                  notes TEXT,
                                  active BOOLEAN NOT NULL DEFAULT TRUE,
                                  verified BOOLEAN NOT NULL DEFAULT FALSE,
                                  verified_by VARCHAR(200),
                                  verified_date TIMESTAMP,
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  created_by BIGINT,
                                  updated_by BIGINT,

                                  PRIMARY KEY (id),

    -- Clave foránea
                                  CONSTRAINT fk_family_history_patient FOREIGN KEY (patient_id)
                                      REFERENCES patients(id) ON DELETE CASCADE,

    -- Índices
                                  INDEX idx_family_history_patient (patient_id),
                                  INDEX idx_family_history_relationship (relationship),
                                  INDEX idx_family_history_active (active),
                                  INDEX idx_family_history_condition (condition_name),
                                  INDEX idx_family_history_genetic (genetic_risk),
                                  INDEX idx_family_history_patient_active (patient_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 6. TABLA VACCINATION_RECORDS (Historial de Vacunas)
-- =====================================================
CREATE TABLE vaccination_records (
                                     id BIGINT NOT NULL AUTO_INCREMENT,
                                     patient_id BIGINT NOT NULL,
                                     vaccine_name VARCHAR(200) NOT NULL,
                                     vaccine_type VARCHAR(100),
                                     manufacturer VARCHAR(200),
                                     dose_number INT NOT NULL,
                                     total_doses_required INT,
                                     lot_number VARCHAR(100),
                                     administered_date DATE NOT NULL,
                                     next_dose_date DATE,
                                     administered_by VARCHAR(200) NOT NULL,
                                     administered_by_id BIGINT,
                                     location VARCHAR(200) NOT NULL,
                                     site_of_administration VARCHAR(100),
                                     route VARCHAR(50),
                                     expiration_date DATE,
                                     adverse_reactions TEXT,
                                     had_reaction BOOLEAN NOT NULL DEFAULT FALSE,
                                     reaction_severity VARCHAR(50),
                                     contraindications TEXT,
                                     notes TEXT,
                                     verified BOOLEAN NOT NULL DEFAULT FALSE,
                                     verified_by VARCHAR(200),
                                     verified_date TIMESTAMP,
                                     certificate_number VARCHAR(100),
                                     valid_for_travel BOOLEAN NOT NULL DEFAULT FALSE,
                                     booster BOOLEAN NOT NULL DEFAULT FALSE,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     created_by BIGINT,
                                     updated_by BIGINT,

                                     PRIMARY KEY (id),

    -- Clave foránea
                                     CONSTRAINT fk_vaccination_patient FOREIGN KEY (patient_id)
                                         REFERENCES patients(id) ON DELETE CASCADE,

    -- Índices
                                     INDEX idx_vaccination_patient (patient_id),
                                     INDEX idx_vaccination_date (administered_date),
                                     INDEX idx_vaccination_next_dose (next_dose_date),
                                     INDEX idx_vaccination_vaccine (vaccine_name),
                                     INDEX idx_vaccination_patient_vaccine (patient_id, vaccine_name),
                                     INDEX idx_vaccination_certificate (certificate_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;