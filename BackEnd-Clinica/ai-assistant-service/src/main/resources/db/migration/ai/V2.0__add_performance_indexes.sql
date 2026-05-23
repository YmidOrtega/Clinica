-- Índice compuesto para conversation_history: mejora la query findByUserIdAndIsActiveTrue
CREATE INDEX idx_conversation_user_active ON conversation_history(user_id, is_active);
