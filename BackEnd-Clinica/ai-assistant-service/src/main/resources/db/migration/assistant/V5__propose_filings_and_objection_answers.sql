ALTER TABLE assistant.proposed_actions
    ADD COLUMN payload JSONB NULL,
    ADD COLUMN target_uuid UUID NULL,
    ADD COLUMN target_version BIGINT NULL,
    DROP CONSTRAINT chk_proposed_actions_kind,
    ADD CONSTRAINT chk_proposed_actions_kind
        CHECK (kind IN ('SIGN', 'SEND_TO_DIAN', 'VALIDATE_RIPS', 'FILE', 'ANSWER_OBJECTION')),
    ADD CONSTRAINT chk_proposed_actions_payload
        CHECK ((kind IN ('FILE', 'ANSWER_OBJECTION')) = (payload IS NOT NULL)),
    ADD CONSTRAINT chk_proposed_actions_target
        CHECK ((kind = 'ANSWER_OBJECTION') = (target_uuid IS NOT NULL AND target_version IS NOT NULL));
