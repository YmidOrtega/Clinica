-- Búsquedas por estado y fechas
CREATE INDEX idx_patients_status_created ON patients(status, created_at DESC);

-- Búsquedas geográficas
CREATE INDEX idx_patients_locality_status ON patients(locality_site_id, status);

-- Búsquedas por proveedor de salud
CREATE INDEX idx_patients_provider_status ON patients(health_provider_nit, status);

-- Búsquedas por tipo de afiliación
CREATE INDEX idx_patients_affiliation_status ON patients(type_of_affiliation, status);

-- =====================================================
-- ÍNDICES ADICIONALES PARA MEDICAL_HISTORIES
-- =====================================================

-- Búsquedas por tipo de sangre y seguro (nuevo índice compuesto)
CREATE INDEX idx_medical_history_blood_insurance ON medical_histories(blood_type, has_insurance);

-- =====================================================
-- ÍNDICES ADICIONALES PARA ALLERGIES
-- =====================================================

-- Alergias por severidad y estado verificado
CREATE INDEX idx_allergy_verified_active ON allergies(verified, active);

-- Alergias por paciente, severidad y estado (índice compuesto mejorado)
CREATE INDEX idx_allergy_patient_severity_active ON allergies(patient_id, severity, active);

-- =====================================================
-- ÍNDICES ADICIONALES PARA CHRONIC_DISEASES
-- =====================================================

-- Enfermedades por especialista requerido
CREATE INDEX idx_chronic_specialist_active ON chronic_diseases(requires_specialist, specialist_type, active);

-- Enfermedades por paciente, severidad y estado
CREATE INDEX idx_chronic_patient_severity_active ON chronic_diseases(patient_id, severity, active);

-- =====================================================
-- ÍNDICES ADICIONALES PARA CURRENT_MEDICATIONS
-- =====================================================

-- Medicamentos activos con fecha de inicio
CREATE INDEX idx_medication_patient_active_date ON current_medications(patient_id, active, discontinued, start_date DESC);

-- Medicamentos por resurtidos restantes
CREATE INDEX idx_medication_refills_active ON current_medications(refills_remaining, active, discontinued);

-- Medicamentos próximos a vencer
CREATE INDEX idx_medication_end_active ON current_medications(end_date, active, discontinued);

-- =====================================================
-- ÍNDICES ADICIONALES PARA FAMILY_HISTORIES
-- =====================================================

-- Riesgo genético por paciente
CREATE INDEX idx_family_patient_genetic_risk ON family_histories(patient_id, genetic_risk, screening_recommended);

-- Screening recomendado con verificación
CREATE INDEX idx_family_screening_verified ON family_histories(screening_recommended, active, verified);

-- =====================================================
-- ÍNDICES ADICIONALES PARA VACCINATION_RECORDS
-- =====================================================

-- Esquemas incompletos
CREATE INDEX idx_vaccination_patient_incomplete ON vaccination_records(patient_id, dose_number, total_doses_required);

-- Vacunas válidas para viaje
CREATE INDEX idx_vaccination_travel_verified ON vaccination_records(valid_for_travel, verified, patient_id);

-- =====================================================
-- ACTUALIZAR ESTADÍSTICAS DEL OPTIMIZADOR
-- =====================================================

ANALYZE TABLE medical_histories;
ANALYZE TABLE allergies;
ANALYZE TABLE chronic_diseases;
ANALYZE TABLE current_medications;
ANALYZE TABLE family_histories;
ANALYZE TABLE vaccination_records;
ANALYZE TABLE patients;
