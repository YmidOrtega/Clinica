ALTER TABLE attentions
    ALTER COLUMN patient_id TYPE VARCHAR(20) USING patient_id::VARCHAR;
