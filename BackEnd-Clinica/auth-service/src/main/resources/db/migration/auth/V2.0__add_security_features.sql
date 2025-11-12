-- Tabla refresh_tokens
CREATE TABLE refresh_tokens (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                token VARCHAR(512) NOT NULL UNIQUE,
                                user_id BIGINT NOT NULL,
                                expires_at TIMESTAMP NOT NULL,
                                created_at TIMESTAMP NOT NULL,
                                revoked_at TIMESTAMP NULL,
                                revoked BOOLEAN NOT NULL DEFAULT FALSE,
                                ip_address VARCHAR(45),
                                user_agent VARCHAR(255),
                                replaced_by_token VARCHAR(512),

                                INDEX idx_refresh_token (token),
                                INDEX idx_user_id (user_id),
                                INDEX idx_expires_at (expires_at),

                                CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
                                    REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla login_attempts
CREATE TABLE login_attempts (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                email VARCHAR(100) NOT NULL,
                                ip_address VARCHAR(45) NOT NULL,
                                user_agent VARCHAR(255),
                                successful BOOLEAN NOT NULL DEFAULT FALSE,
                                failure_reason VARCHAR(255),
                                attempted_at TIMESTAMP NOT NULL,

                                INDEX idx_email (email),
                                INDEX idx_ip_address (ip_address),
                                INDEX idx_attempted_at (attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla password_reset_tokens
CREATE TABLE password_reset_tokens (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       token VARCHAR(36) NOT NULL UNIQUE,
                                       user_id BIGINT NOT NULL,
                                       expires_at TIMESTAMP NOT NULL,
                                       created_at TIMESTAMP NOT NULL,
                                       used_at TIMESTAMP NULL,
                                       used BOOLEAN NOT NULL DEFAULT FALSE,

                                       INDEX idx_token (token),
                                       INDEX idx_user_id (user_id),

                                       CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id)
                                           REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla audit_logs
CREATE TABLE audit_logs (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            user_id BIGINT,
                            user_email VARCHAR(100),
                            action VARCHAR(50) NOT NULL,
                            details VARCHAR(500),
                            ip_address VARCHAR(45),
                            user_agent VARCHAR(255),
                            created_at TIMESTAMP NOT NULL,

                            INDEX idx_user_id (user_id),
                            INDEX idx_action (action),
                            INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla password_history
CREATE TABLE password_history (
                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  user_id BIGINT NOT NULL,
                                  password_hash VARCHAR(255) NOT NULL,
                                  created_at TIMESTAMP NOT NULL,

                                  INDEX idx_user_id (user_id),
                                  INDEX idx_created_at (created_at),

                                  CONSTRAINT fk_password_history_user FOREIGN KEY (user_id)
                                      REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Agregar campos de seguridad a la tabla users
ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN account_locked_until TIMESTAMP NULL,
    ADD COLUMN last_password_change TIMESTAMP NULL,
    ADD COLUMN password_never_expires BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_password_change BOOLEAN NOT NULL DEFAULT FALSE;

-- Crear índices adicionales en users
CREATE INDEX idx_account_locked_until ON users(account_locked_until);
CREATE INDEX idx_failed_login_attempts ON users(failed_login_attempts);