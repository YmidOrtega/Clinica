# Prueba de carga — patient-service

Mide si `patient-service` soporta el pico de una clínica que atiende **5.000 pacientes al día** sin caché,
con un registro de **500.000 pacientes** en MySQL.

## Modelo de carga

Cada atención consulta el registro del paciente varias veces (admisión, triage, consulta, órdenes,
farmacia, facturación). Se estiman ~15 lecturas por paciente, concentradas en 12 horas y con un pico
de 10 veces el promedio:

| Operación                         | Pico esperado (x1) | Estrés (x5) |
|-----------------------------------|--------------------|-------------|
| `GET /patients/{uuid}`            | 20 req/s           | 100 req/s   |
| `POST /patients/search` documento | 6 req/s            | 30 req/s    |
| `POST /patients/search` nombre    | 2 req/s            | 10 req/s    |
| `POST /patients`                  | 1 req/s            | 5 req/s     |
| `GET` + `PUT /patients/{uuid}/contact` con `If-Match` | 1 req/s | 5 req/s |

Los pacientes sembrados no tienen aseguradora para medir el camino de base de datos sin depender de
`clients-service`.

## Resultados

Ejecución del 2026-09-13 en una máquina de 16 núcleos y 61 GB de RAM, con `patient-service`,
`patient-db` y k6 en contenedores sin límites de recursos. Cada perfil: 30 s de rampa, 2 min
sostenidos.

| Operación       | x1 p50 | x1 p95 | x1 p99 | x5 p50 | x5 p95 | x5 p99 |
|-----------------|--------|--------|--------|--------|--------|--------|
| readPatient     | 6.6 ms | 10.4 ms | 13.8 ms | 3.4 ms | 5.0 ms | 6.3 ms |
| searchDocument  | 6.4 ms | 9.7 ms | 11.8 ms | 3.7 ms | 5.3 ms | 6.2 ms |
| searchName      | 10.5 ms | 37.8 ms | 42.2 ms | 14.9 ms | 37.2 ms | 43.5 ms |
| registerPatient | 20.3 ms | 30.3 ms | 50.1 ms | 17.8 ms | 22.6 ms | 25.4 ms |
| updateContact   | 10.3 ms | 23.6 ms | 33.2 ms | 8.8 ms | 18.9 ms | 21.3 ms |

- x1: 4.638 peticiones (28,7 req/s), 0 % de errores.
- x5: 21.999 peticiones (137,2 req/s), 0 % de errores.
- Umbrales cumplidos: p95 < 200 ms en lecturas y < 500 ms en búsquedas por nombre y escrituras.

### Con eventos (outbox + Debezium)

Misma prueba con Kafka, Kafka Connect, Kafka UI y dos réplicas de `patient-service` corriendo en la
misma máquina (k6 apunta a una réplica):

| Operación       | x1 p50 | x1 p95 | x1 p99 | x5 p50 | x5 p95 | x5 p99 |
|-----------------|--------|--------|--------|--------|--------|--------|
| readPatient     | 6.8 ms | 11.5 ms | 15.7 ms | 3.7 ms | 5.6 ms | 6.7 ms |
| searchDocument  | 6.8 ms | 10.8 ms | 14.8 ms | 3.9 ms | 5.7 ms | 6.9 ms |
| searchName      | 16.7 ms | 48.3 ms | 56.6 ms | 11.6 ms | 40.5 ms | 48.9 ms |
| registerPatient | 26.8 ms | 37.2 ms | 43.9 ms | 20.2 ms | 24.9 ms | 30.1 ms |
| updateContact   | 12.5 ms | 28.7 ms | 37.5 ms | 7.8 ms | 20.8 ms | 24.0 ms |

- 0 % de errores en ambos perfiles.
- Escribir el evento en el outbox dentro de la transacción suma ~7 ms al p95 del registro en el pico.
- Conciliación al terminar: 844 filas en el outbox y 844 eventos en `patient.events.v1` (840 registros
  de la prueba y 4 manuales). Ningún evento perdido ni duplicado.

## Conclusiones

1. **No hace falta caché de pacientes.** Una lectura por UUID responde en p95 de 5–10 ms incluso a
   5 veces el pico; una caché ahorraría menos de 1 ms a cambio de datos desactualizados, una copia más
   de datos sensibles y una dependencia que puede caer.
2. **La búsqueda por nombre se rediseñó por esta prueba.** La primera versión usaba un índice
   `FULLTEXT` con parser `ngram`: con nombres comunes (`ana restrepo`) el p95 fue de **1.895 ms**.
   Se reemplazó por búsqueda por prefijo de apellidos (y opcionalmente nombres) sobre el índice
   B-tree `idx_patients_names`: p95 de **37 ms**, sin índice adicional que mantener en cada escritura.
3. **Los conflictos de edición concurrente se detectan** (`412` con `If-Match`) sin afectar la latencia.
4. **Publicar eventos con outbox cuesta poco**: unos milisegundos por escritura, sin que
   `patient-service` dependa de Kafka.

## Cómo ejecutarla

Requisitos: Docker y Node.js (solo para firmar tokens locales).

Exporta antes las variables `PATIENT_DB_*` (incluidas `PATIENT_DB_DEBEZIUM_*`, que usa el script de
inicialización de la base) descritas en `docs/variablesDeEntorno.md`.

```bash
cd BackEnd-Clinica

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/patient-private.pem
export JWT_PUBLIC_KEY=$(openssl pkey -in /tmp/patient-private.pem -pubout | grep -v '^-----' | tr -d '\n')

docker compose -p patient-load -f docker-compose.yml -f docker-compose.debug.yml \
  up -d --build patient-db eureka-service patient-service

docker exec -i patient-db mysql -uroot -p"$PATIENT_DB_ROOT_PASSWORD" "$PATIENT_DB_NAME" \
  < patient-service/load-test/seed-patients.sql

TOKEN=$(node patient-service/load-test/generate-token.mjs /tmp/patient-private.pem ADMIN 7200)

docker run --rm --network host -v "$PWD/patient-service/load-test:/scripts:ro" \
  -e TOKEN="$TOKEN" -e MULTIPLIER=1 -e DURATION=2m \
  grafana/k6 run --quiet /scripts/patient-load.js
```

Variables del script: `BASE_URL` (por defecto `http://127.0.0.1:8081`), `MULTIPLIER`, `DURATION`,
`SEEDED_PATIENTS` y `SUMMARY_FILE` para exportar el resumen completo en JSON.

El siembre escribe directamente como `root` en la base de datos de pruebas; en un entorno real
nadie más que `patient_migrator` y `patient_app` tiene acceso.
