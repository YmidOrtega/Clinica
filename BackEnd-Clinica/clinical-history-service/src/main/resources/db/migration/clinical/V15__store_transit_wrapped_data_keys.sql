ALTER TABLE clinical_keys.data_key_wrappings
    DROP CHECK chk_data_key_wrappings_length,
    MODIFY wrapped_key VARBINARY(128) NOT NULL,
    ADD CONSTRAINT chk_data_key_wrappings_length CHECK (
        LENGTH(wrapped_key) = 61
        OR (LEFT(wrapped_key, 7) = 'vault:v' AND LENGTH(wrapped_key) BETWEEN 89 AND 128));
