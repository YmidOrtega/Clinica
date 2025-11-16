-- =====================================================
-- 1. AJUSTAR login_attempts
-- =====================================================
-- Cambiar 'successful' a 'success' para coincidir con la entidad
ALTER TABLE login_attempts
    CHANGE COLUMN successful success BOOLEAN NOT NULL DEFAULT FALSE;

-- =====================================================
-- 2. AJUSTAR password_reset_tokens
-- =====================================================
-- Agregar campo ip_address que falta
ALTER TABLE password_reset_tokens
    ADD COLUMN ip_address VARCHAR(45) NULL AFTER used_at;

-- Crear índice compuesto para búsquedas de validación
CREATE INDEX idx_token_valid ON password_reset_tokens(token, used, expires_at);

-- =====================================================
-- 3. AJUSTAR audit_logs
-- =====================================================
-- Agregar campos entity_type y entity_id
ALTER TABLE audit_logs
    ADD COLUMN entity_type VARCHAR(50) NULL AFTER action,
    ADD COLUMN entity_id VARCHAR(50) NULL AFTER entity_type;

-- Cambiar details de VARCHAR(500) a TEXT para soportar más información
ALTER TABLE audit_logs
    MODIFY COLUMN details TEXT NULL;

-- Crear índices para búsquedas por entidad
CREATE INDEX idx_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_user_action ON audit_logs(user_id, action, created_at);

-- =====================================================
-- 4. AJUSTAR password_history
-- =====================================================
-- Renombrar created_at a changed_at para mayor claridad
ALTER TABLE password_history
    CHANGE COLUMN created_at changed_at TIMESTAMP NOT NULL;

-- Crear índice compuesto para consultas de historial reciente
CREATE INDEX idx_user_recent ON password_history(user_id, changed_at DESC);

-- =====================================================
-- 5. ÍNDICES ADICIONALES PARA OPTIMIZACIÓN
-- =====================================================

-- refresh_tokens - búsquedas de tokens activos
CREATE INDEX idx_user_active ON refresh_tokens(user_id, revoked, expires_at);
CREATE INDEX idx_revoked_expires ON refresh_tokens(revoked, expires_at);

-- login_attempts - ventana de tiempo para intentos fallidos
CREATE INDEX idx_email_attempted ON login_attempts(email, attempted_at);
CREATE INDEX idx_ip_attempted ON login_attempts(ip_address, attempted_at);

-- users - búsquedas de seguridad
CREATE INDEX idx_user_security ON users(email, status, active, account_locked_until);
