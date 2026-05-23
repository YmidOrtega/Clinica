-- Índice compuesto para doctor_schedules: mejora queries de disponibilidad activa por día
CREATE INDEX idx_schedule_doctor_day_active ON doctor_schedules(doctor_id, day_of_week, active);
