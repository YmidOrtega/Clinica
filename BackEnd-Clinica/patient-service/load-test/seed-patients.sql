SET SESSION cte_max_recursion_depth = 1000000;

INSERT INTO patients (uuid, version, document_type, document_number, first_names, last_names, birth_date, sex,
                      country_of_origin, disability, mobile, health_regime, residence_department,
                      residence_municipality, residence_zone, residence_address, status, created_at, updated_at)
WITH RECURSIVE sequence (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 500000
)
SELECT UUID(),
       0,
       'CEDULA_DE_CIUDADANIA',
       CAST(1000000000 + n AS CHAR),
       ELT(1 + n % 12, 'Ana', 'Luis', 'María', 'Carlos', 'Lucía', 'Jorge', 'Camila', 'Andrés', 'Valentina', 'Julián', 'Sofía', 'Mateo'),
       CONCAT(ELT(1 + (n DIV 12) % 10, 'Gómez', 'Restrepo', 'Pérez', 'Rodríguez', 'Martínez', 'García', 'López', 'Hernández', 'Ortiz', 'Castro'),
              ' ',
              ELT(1 + (n DIV 120) % 10, 'Rojas', 'Díaz', 'Moreno', 'Vargas', 'Suárez', 'Torres', 'Ramírez', 'Cruz', 'Muñoz', 'Silva')),
       DATE_SUB('2000-01-01', INTERVAL n % 20000 DAY),
       IF(n % 2 = 0, 'FEMALE', 'MALE'),
       'CO',
       'NONE',
       CONCAT('300', LPAD(n % 10000000, 7, '0')),
       'UNINSURED',
       'Santander',
       'Bucaramanga',
       IF(n % 5 = 0, 'RURAL', 'URBAN'),
       CONCAT('Calle ', 1 + n % 200, ' # ', 1 + n % 90, '-', 1 + n % 50),
       'ACTIVE',
       NOW(6),
       NOW(6)
FROM sequence;
