-- Tabla de roles
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,

    -- Índices
    INDEX idx_role_name (name),

    -- Constraints adicionales
    CONSTRAINT chk_role_name_not_empty CHECK (TRIM(name) != ''),
    CONSTRAINT chk_role_name_length CHECK (CHAR_LENGTH(name) >= 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla de usuarios
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    birth_date DATE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    created_at DATE NOT NULL DEFAULT (CURRENT_DATE),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    status ENUM('ACTIVE', 'INACTIVE', 'PENDING', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',

    -- Claves foráneas
    CONSTRAINT fk_user_role
        FOREIGN KEY (role_id) REFERENCES roles(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Índices
    INDEX idx_user_email (email),
    INDEX idx_user_username (username),
    INDEX idx_user_status (status),
    INDEX idx_user_role (role_id),
    INDEX idx_user_created_at (created_at),

    -- Constraints adicionales
    CONSTRAINT chk_username_not_empty CHECK (TRIM(username) != ''),
    CONSTRAINT chk_username_length CHECK (CHAR_LENGTH(username) >= 3),
    CONSTRAINT chk_email_format CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_password_not_empty CHECK (TRIM(password) != ''),
    CONSTRAINT chk_password_length CHECK (CHAR_LENGTH(password) >= 8),
    CONSTRAINT chk_phone_format CHECK (phone_number IS NULL OR phone_number REGEXP '^\\+?[1-9][0-9]{1,14}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

