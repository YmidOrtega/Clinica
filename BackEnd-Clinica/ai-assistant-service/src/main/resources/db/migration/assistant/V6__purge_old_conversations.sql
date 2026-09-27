ALTER TABLE assistant.proposed_actions
    ALTER COLUMN conversation_id DROP NOT NULL,
    DROP CONSTRAINT proposed_actions_conversation_id_fkey,
    ADD CONSTRAINT fk_proposed_actions_conversation FOREIGN KEY (conversation_id)
        REFERENCES assistant.conversations (id) ON DELETE SET NULL;

CREATE FUNCTION assistant.purge_conversations(cutoff TIMESTAMPTZ) RETURNS INTEGER
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = assistant, pg_temp
AS $$
DECLARE
    purged INTEGER;
BEGIN
    DELETE FROM assistant.conversation_messages
     WHERE conversation_id IN (SELECT id FROM assistant.conversations WHERE updated_at < cutoff);
    DELETE FROM assistant.conversations WHERE updated_at < cutoff;
    GET DIAGNOSTICS purged = ROW_COUNT;
    RETURN purged;
END;
$$;

REVOKE ALL ON FUNCTION assistant.purge_conversations(TIMESTAMPTZ) FROM PUBLIC;
