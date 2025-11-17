-- =====================================================
-- ÍNDICES COMPUESTOS PARA PATIENTS
-- =====================================================

-- Búsquedas por estado y fechas
CREATE INDEX idx_patients_status_created ON patients(status, created_at DESC);
CREATE INDEX idx_patients_active_status ON patients(active, status);

-- Búsquedas geográficas
CREATE INDEX idx_patients_locality_status ON patients(locality_site_id, status);

-- Búsquedas por proveedor de salud
CREATE INDEX idx_patients_provider_status ON patients(health_provider_nit, status);

-- Búsquedas por tipo de afiliación
CREATE INDEX idx_patients_affiliation_active ON patients(type_of_affiliation, active);

-- =====================================================
-- ÍNDICES PARA MEDICAL_HISTORIES
-- =====================================================

-- Búsquedas por tipo de sangre
CREATE INDEX idx_medical_history_blood_insurance ON medical_histories(blood_type, has_insurance);

-- Próximos chequeos
CREATE INDEX idx_medical_history_checkup_dates ON medical_histories(next_checkup_date, last_checkup_date);

-- =====================================================
-- ÍNDICES PARA ALLERGIES
-- =====================================================

-- Alergias críticas activas
CREATE INDEX idx_allergy_critical ON allergies(patient_id, severity, active)
    WHERE severity IN ('SEVERE', 'LIFE_THREATENING') AND active = TRUE;

-- Alergias no verificadas
CREATE INDEX idx_allergy_unverified ON allergies(verified, active)
    WHERE verified = FALSE AND active = TRUE;

-- =====================================================
-- ÍNDICES PARA CHRONIC_DISEASES
-- =====================================================

-- Enfermedades críticas
CREATE INDEX idx_chronic_critical ON chronic_diseases(patient_id, severity, active)
    WHERE severity = 'CRITICAL' AND active = TRUE;

-- Enfermedades no controladas
CREATE INDEX idx_chronic_uncontrolled ON chronic_diseases(patient_id, severity, active)
    WHERE severity = 'UNCONTROLLED' AND active = TRUE;

-- Requiere especialista
CREATE INDEX idx_chronic_specialist ON chronic_diseases(requires_specialist, specialist_type, active)
    WHERE requires_specialist = TRUE AND active = TRUE;

-- =====================================================
-- ÍNDICES PARA CURRENT_MEDICATIONS
-- =====================================================

-- Medicamentos activos por paciente
CREATE INDEX idx_medication_active_patient ON current_medications(patient_id, active, start_date DESC)
    WHERE active = TRUE AND discontinued = FALSE;

-- Medicamentos que necesitan resurtido
CREATE INDEX idx_medication_refills ON current_medications(patient_id, refills_remaining, active)
    WHERE active = TRUE AND (refills_remaining IS NULL OR refills_remaining <= 1);

-- Medicamentos próximos a vencer
CREATE INDEX idx_medication_expiring ON current_medications(patient_id, end_date, active)
    WHERE active = TRUE AND end_date IS NOT NULL;

-- =====================================================
-- ÍNDICES PARA FAMILY_HISTORIES
-- =====================================================

-- Riesgo genético
CREATE INDEX idx_family_genetic_risk ON family_histories(patient_id, genetic_risk, screening_recommended)
    WHERE genetic_risk = TRUE;

-- Screening recomendado
CREATE INDEX idx_family_screening ON family_histories(patient_id, screening_recommended, active)
    WHERE screening_recommended = TRUE AND active = TRUE;

-- =====================================================
-- ÍNDICES PARA VACCINATION_RECORDS
-- =====================================================

-- Próximas dosis
CREATE INDEX idx_vaccination_upcoming ON vaccination_records(patient_id, next_dose_date)
    WHERE next_dose_date IS NOT NULL;

-- Esquemas incompletos
CREATE INDEX idx_vaccination_incomplete ON vaccination_records(patient_id, dose_number, total_doses_required)
    WHERE dose_number < total_doses_required;

-- Vacunas válidas para viaje
CREATE INDEX idx_vaccination_travel ON vaccination_records(patient_id, valid_for_travel, verified)
    WHERE valid_for_travel = TRUE AND verified = TRUE;

-- =====================================================
-- ESTADÍSTICAS PARA EL OPTIMIZADOR
-- =====================================================

-- Actualizar estadísticas de las tablas nuevas
ANALYZE TABLE medical_histories;
ANALYZE TABLE allergies;
ANALYZE TABLE chronic_diseases;
ANALYZE TABLE current_medications;
ANALYZE TABLE family_histories;
ANALYZE TABLE vaccination_records;
ANALYZE TABLE patients;