-- =====================================================
-- 1. refresh_tokens - Eliminar índice UNIQUE temporal
-- =====================================================
ALTER TABLE refresh_tokens
    DROP INDEX token;

-- Cambiar tamaño de columnas
ALTER TABLE refresh_tokens
    MODIFY COLUMN token VARCHAR(768) NOT NULL,
    MODIFY COLUMN replaced_by_token VARCHAR(768) NULL;

-- Recrear índice UNIQUE
CREATE UNIQUE INDEX idx_token_unique ON refresh_tokens(token);

-- =====================================================
-- 2. password_reset_tokens
-- =====================================================

ALTER TABLE password_reset_tokens
    MODIFY COLUMN token VARCHAR(500) NOT NULL;