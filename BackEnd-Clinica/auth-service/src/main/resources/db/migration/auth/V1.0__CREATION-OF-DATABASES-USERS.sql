
-- Eliminar tablas en orden correcto (por dependencias)
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS roles;

-- =====================================================
-- 1. TABLA ROLES (Roles del Sistema)
-- =====================================================
CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Restricciones de unicidad
    UNIQUE KEY uk_roles_name (name),

    -- Índices para optimizar consultas
    INDEX idx_roles_name (name),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_roles_name_not_empty CHECK (TRIM(name) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. TABLA ROLE_PERMISSIONS (Permisos por Rol)
-- =====================================================
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission VARCHAR(100) NOT NULL,

    PRIMARY KEY (role_id, permission),

    -- Clave foránea
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,

    -- Índices
    INDEX idx_role_permissions_role_id (role_id),
    INDEX idx_role_permissions_permission (permission),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_role_permissions_permission_not_empty CHECK (TRIM(permission) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. TABLA USERS (Usuarios del Sistema)
-- =====================================================
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    birth_date DATE,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    status ENUM(
        'PENDING',
        'ACTIVE',
        'INACTIVE',
        'SUSPENDED',
        'DELETED'
    ) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Restricciones de unicidad
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),

    -- Clave foránea
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id),

    -- Índices para optimizar consultas
    INDEX idx_users_email (email),
    INDEX idx_users_username (username),
    INDEX idx_users_status (status),
    INDEX idx_users_active (active),
    INDEX idx_users_role_id (role_id),
    INDEX idx_users_created_at (created_at),

    -- Validaciones básicas de integridad
    CONSTRAINT chk_users_username_not_empty CHECK (TRIM(username) != ''),
    CONSTRAINT chk_users_email_not_empty CHECK (TRIM(email) != ''),
    CONSTRAINT chk_users_password_not_empty CHECK (TRIM(password) != '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

