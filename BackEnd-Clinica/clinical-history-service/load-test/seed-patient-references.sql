SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO patient_references (uuid, kind, source_version, document_type, document_number, first_names, last_names, birth_date, sex, status,
                                health_regime, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50000)
SELECT CONCAT('10000000-0000-4000-8000-', LPAD(n, 12, '0')), 'REGISTERED', 0, 'CEDULA_DE_CIUDADANIA', LPAD(n, 10, '7'),
       ELT(1 + MOD(n, 6), 'Ana', 'Luis', 'Valentina', 'Carlos', 'María José', 'Andrés'),
       ELT(1 + MOD(n, 5), 'Restrepo Gómez', 'Pérez Muñoz', 'Ortiz', 'Castro Rojas', 'Martínez'),
       DATE_SUB('2000-01-01', INTERVAL MOD(n, 25000) DAY), IF(MOD(n, 2) = 0, 'FEMALE', 'MALE'), 'ACTIVE', 'CONTRIBUTORY', NOW(6)
FROM seq
ON DUPLICATE KEY UPDATE updated_at = updated_at;
