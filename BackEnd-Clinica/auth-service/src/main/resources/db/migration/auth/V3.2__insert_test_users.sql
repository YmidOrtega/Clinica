-- =====================================================
-- INSERTAR USUARIOS DE PRUEBA
-- =====================================================
-- IMPORTANTE: Todas las contraseñas son "Admin1234."
-- Hash BCrypt: $2a$10$UnG6If3t3sfXmTJw.aI2uu4Bh/que56BzTyCtzyjPKeDumOnX75i2

-- =====================================================
-- 1. Super Admin
-- =====================================================
INSERT INTO users (
    uuid,
    role_id,
    username,
    birth_date,
    email,
    password,
    phone_number,
    active,
    status,
    failed_login_attempts,
    last_password_change,
    password_never_expires,
    require_password_change
) VALUES (
             UUID(),
             1,
             'superadmin',
             '1985-01-15',
             'superadmin@clinica.com',
             '$2a$10$UnG6If3t3sfXmTJw.aI2uu4Bh/que56BzTyCtzyjPKeDumOnX75i2',
             '+573001234567',
             TRUE,
             'ACTIVE',
             0,
             CURRENT_TIMESTAMP,
             TRUE,
             FALSE
         );

-- =====================================================
-- 2. Admin
-- =====================================================
INSERT INTO users (
    uuid,
    role_id,
    username,
    birth_date,
    email,
    password,
    phone_number,
    active,
    status,
    failed_login_attempts,
    last_password_change,
    password_never_expires,
    require_password_change
) VALUES (
             UUID(),
             2,
             'admin',
             '1988-03-20',
             'admin@clinica.com',
             '$2a$10$UnG6If3t3sfXmTJw.aI2uu4Bh/que56BzTyCtzyjPKeDumOnX75i2',
             '+573001234568',
             TRUE,
             'ACTIVE',
             0,
             CURRENT_TIMESTAMP,
             FALSE,
             FALSE
         );

-- =====================================================
-- 3. Doctor
-- =====================================================
INSERT INTO users (
    uuid,
    role_id,
    username,
    birth_date,
    email,
    password,
    phone_number,
    active,
    status,
    failed_login_attempts,
    last_password_change,
    password_never_expires,
    require_password_change
) VALUES (
             UUID(),
             3,
             'doctor.garcia',
             '1990-06-12',
             'doctor@clinica.com',
             '$2a$10$UnG6If3t3sfXmTJw.aI2uu4Bh/que56BzTyCtzyjPKeDumOnX75i2',
             '+573001234569',
             TRUE,
             'ACTIVE',
             0,
             CURRENT_TIMESTAMP,
             FALSE,
             FALSE
         );

-- =====================================================
-- 4. Enfermera
-- =====================================================
INSERT INTO users (
    uuid,
    role_id,
    username,
    birth_date,
    email,
    password,
    phone_number,
    active,
    status,
    failed_login_attempts,
    last_password_change,
    password_never_expires,
    require_password_change
) VALUES (
             UUID(),
             4,
             'nurse.martinez',
             '1992-09-25',
             'nurse@clinica.com',
             '$2a$10$UnG6If3t3sfXmTJw.aI2uu4Bh/que56BzTyCtzyjPKeDumOnX75i2',
             '+573001234570',
             TRUE,
             'ACTIVE',
             0,
             CURRENT_TIMESTAMP,
             FALSE,
             FALSE
         );

-- =====================================================
-- 5. Recepcionista
-- =====================================================
INSERT INTO users (
    uuid,
    role_id,
    username,
    birth_date,
    email,
    password,
    phone_number,
    active,
    status,
    failed_login_attempts,
    last_password_change,
    password_never_expires,
    require_password_change
) VALUES (
             UUID(),
             5,
             'receptionist.lopez',
             '1995-11-08',
             'receptionist@clinica.com',
             '$2a$10$UnG6If3t3sfXmTJw.aI2uu4Bh/que56BzTyCtzyjPKeDumOnX75i2',
             '+573001234571',
             TRUE,
             'ACTIVE',
             0,
             CURRENT_TIMESTAMP,
             FALSE,
             FALSE
         );
