-- =====================================================
-- INSERTAR ROLES Y PERMISOS PREDETERMINADOS
-- =====================================================

-- =====================================================
-- 1. INSERTAR ROLES
-- =====================================================
INSERT INTO roles (id, name, created_at, updated_at) VALUES
                                                         (1, 'SUPER_ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                                                         (2, 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                                                         (3, 'DOCTOR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                                                         (4, 'NURSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                                                         (5, 'RECEPTIONIST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- =====================================================
-- 2. INSERTAR PERMISOS PARA SUPER_ADMIN
-- =====================================================
INSERT INTO role_permissions (role_id, permission) VALUES
                                                       (1, 'USER_CREATE'),
                                                       (1, 'USER_READ'),
                                                       (1, 'USER_UPDATE'),
                                                       (1, 'USER_DELETE'),
                                                       (1, 'ROLE_MANAGE'),
                                                       (1, 'SYSTEM_CONFIG'),
                                                       (1, 'AUDIT_READ'),
                                                       (1, 'SESSION_MANAGE');

-- =====================================================
-- 3. INSERTAR PERMISOS PARA ADMIN
-- =====================================================
INSERT INTO role_permissions (role_id, permission) VALUES
                                                       (2, 'USER_CREATE'),
                                                       (2, 'USER_READ'),
                                                       (2, 'USER_UPDATE'),
                                                       (2, 'SESSION_READ');

-- =====================================================
-- 4. INSERTAR PERMISOS PARA DOCTOR
-- =====================================================
INSERT INTO role_permissions (role_id, permission) VALUES
                                                       (3, 'USER_READ'),
                                                       (3, 'PATIENT_READ'),
                                                       (3, 'PATIENT_UPDATE'),
                                                       (3, 'PATIENT_CREATE'),
                                                       (3, 'MEDICAL_RECORDS_WRITE'),
                                                       (3, 'MEDICAL_RECORDS_READ');

-- =====================================================
-- 5. INSERTAR PERMISOS PARA NURSE
-- =====================================================
INSERT INTO role_permissions (role_id, permission) VALUES
                                                       (4, 'USER_READ'),
                                                       (4, 'PATIENT_READ'),
                                                       (4, 'PATIENT_UPDATE'),
                                                       (4, 'MEDICAL_RECORDS_READ');

-- =====================================================
-- 6. INSERTAR PERMISOS PARA RECEPTIONIST
-- =====================================================
INSERT INTO role_permissions (role_id, permission) VALUES
                                                       (5, 'USER_READ'),
                                                       (5, 'PATIENT_READ'),
                                                       (5, 'PATIENT_CREATE'),
                                                       (5, 'APPOINTMENT_CREATE'),
                                                       (5, 'APPOINTMENT_READ'),
                                                       (5, 'APPOINTMENT_UPDATE');