-- Índices compuestos para attentions: mejora queries filtradas por paciente/doctor + estado
CREATE INDEX idx_attention_patient_status ON attentions(patient_id, status);
CREATE INDEX idx_attention_doctor_status ON attentions(doctor_id, status);
