-- Índices compuestos para refresh_tokens: mejora queries de limpieza y sesiones activas
CREATE INDEX idx_refresh_token_user_revoked ON refresh_tokens(user_id, revoked);
CREATE INDEX idx_refresh_token_expires_revoked ON refresh_tokens(expires_at, revoked);

-- Índice compuesto para audit_logs: mejora consultas analíticas por acción
CREATE INDEX idx_audit_log_action_created ON audit_logs(action, created_at DESC);

-- Índice compuesto para password_history: mejora validación de historial de contraseñas
CREATE INDEX idx_password_history_user_created ON password_history(user_id, changed_at DESC);
