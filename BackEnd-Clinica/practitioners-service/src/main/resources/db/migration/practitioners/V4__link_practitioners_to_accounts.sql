ALTER TABLE practitioners
    ADD COLUMN auth_user_uuid CHAR(36) NULL AFTER specialties_agreed_at,
    ADD CONSTRAINT uk_practitioners_auth_user UNIQUE (auth_user_uuid),
    ADD CONSTRAINT chk_practitioners_auth_user_uuid
        CHECK (auth_user_uuid IS NULL
            OR REGEXP_LIKE(auth_user_uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'));

ALTER TABLE practitioners_history.practitioners_aud
    ADD COLUMN auth_user_uuid CHAR(36) NULL AFTER specialties_agreed_at;
