-- =====================================================
-- TABLA: conversation_history
-- =====================================================
CREATE TABLE conversation_history (
                                      id BIGSERIAL PRIMARY KEY,
                                      user_id BIGINT NOT NULL,
                                      username VARCHAR(255) NOT NULL,
                                      session_id VARCHAR(100) NOT NULL UNIQUE,
                                      is_active BOOLEAN NOT NULL DEFAULT TRUE,
                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      closed_at TIMESTAMP NULL,

                                      CONSTRAINT uk_conversation_session UNIQUE (session_id)
);

-- Índices para optimizar búsquedas
CREATE INDEX idx_conversation_user_id ON conversation_history(user_id);
CREATE INDEX idx_conversation_session_id ON conversation_history(session_id);
CREATE INDEX idx_conversation_is_active ON conversation_history(is_active);
CREATE INDEX idx_conversation_created_at ON conversation_history(created_at);

-- Comentarios para documentación
COMMENT ON TABLE conversation_history IS 'Historial de conversaciones entre usuarios y el asistente IA';
COMMENT ON COLUMN conversation_history.user_id IS 'ID del usuario autenticado';
COMMENT ON COLUMN conversation_history.username IS 'Nombre del usuario para personalización';
COMMENT ON COLUMN conversation_history.session_id IS 'Identificador único de la sesión de conversación';
COMMENT ON COLUMN conversation_history.is_active IS 'Indica si la conversación está activa';
COMMENT ON COLUMN conversation_history.closed_at IS 'Fecha de cierre de la conversación';

-- =====================================================
-- TABLA: conversation_messages
-- =====================================================
CREATE TABLE conversation_messages (
                                       id BIGSERIAL PRIMARY KEY,
                                       conversation_id BIGINT NOT NULL,
                                       role VARCHAR(20) NOT NULL,
                                       content TEXT NOT NULL,
                                       metadata TEXT NULL,
                                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                       CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id)
                                           REFERENCES conversation_history(id) ON DELETE CASCADE,
                                       CONSTRAINT chk_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

-- Índices para optimizar búsquedas
CREATE INDEX idx_message_conversation_id ON conversation_messages(conversation_id);
CREATE INDEX idx_message_role ON conversation_messages(role);
CREATE INDEX idx_message_created_at ON conversation_messages(created_at);

-- Comentarios para documentación
COMMENT ON TABLE conversation_messages IS 'Mensajes individuales de las conversaciones';
COMMENT ON COLUMN conversation_messages.conversation_id IS 'Referencia a la conversación padre';
COMMENT ON COLUMN conversation_messages.role IS 'Rol del emisor: USER, ASSISTANT o SYSTEM';
COMMENT ON COLUMN conversation_messages.content IS 'Contenido del mensaje';
COMMENT ON COLUMN conversation_messages.metadata IS 'Metadatos en formato JSON (intent, actions, etc.)';