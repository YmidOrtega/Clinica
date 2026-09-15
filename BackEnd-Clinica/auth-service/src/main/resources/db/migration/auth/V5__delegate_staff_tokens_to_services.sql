ALTER TABLE auth_sessions.authorizations
    ADD COLUMN actors JSON NULL,
    ADD CONSTRAINT chk_authorizations_actors CHECK (actors IS NULL OR JSON_TYPE(actors) = 'ARRAY');
