# Prueba de carga — clinical-history-service

Mide si la historia clínica soporta el pico de una clínica que atiende **5.000 pacientes al día**
cuando cada escritura se firma, se encadena y se cifra, con **50.000 pacientes** en la copia local.

## Modelo de carga

Una atención escribe pocas notas y se lee muchas veces: al recibir el turno, antes de cada
procedimiento, al revisar antecedentes y al facturar. El perfil del pico (x1) reparte así el trabajo:

| Operación                                   | Escenario         | Pico esperado (x1) |
|---------------------------------------------|-------------------|--------------------|
| `POST /encounters`                          | `openEncounter`   | 72 req/min         |
| `POST /encounters/{id}/drafts` + `POST /drafts/{id}/signature` | `writeNote` | 300 req/min |
| `GET /encounters/{id}`                      | `readEncounter`   | 600 req/min        |
| `GET /notes/{id}`                           | `readNote`        | 300 req/min        |
| `GET /patients/{uuid}/lists`                | `readLists`       | 180 req/min        |
| `GET /patients/{uuid}/vital-signs`          | `readVitalSigns`  | 180 req/min        |
| `GET /patients/{uuid}/integrity`            | `verifyIntegrity` | 12 req/min         |

Cada nota firmada es el caso peor de escritura: cifra el contenido, calcula el hash canónico, lo sella
con ECDSA, lo encadena tomando el bloqueo del paciente, aplica los cambios a las listas vivas y los
signos vitales, y escribe el evento en el outbox, todo en la misma transacción.

## Resultados

Ejecución del 2026-09-14 en una máquina de 16 núcleos y 61 GB de RAM, con dos réplicas de
`clinical-history-service`, `clinical-db`, el almacenamiento de anexos, Kafka, Kafka Connect y k6 en
contenedores sin límites de recursos (k6 apunta a una réplica). Cada perfil: 30 s de rampa y 2 min
sostenidos, sobre el catálogo CIE-10 oficial activo.

| Operación       | x1 p95 | x5 p95 | x10 p95 | x10 p99 |
|-----------------|--------|--------|---------|---------|
| openEncounter   | 27.0 ms | 24.2 ms | 29.0 ms | 35.6 ms |
| startDraft      | 25.3 ms | 23.5 ms | 24.9 ms | 32.2 ms |
| signNote        | 32.6 ms | 35.1 ms | 40.1 ms | 47.5 ms |
| readEncounter   | 25.6 ms | 24.4 ms | 29.9 ms | 37.2 ms |
| readNote        | 25.6 ms | 23.9 ms | 26.1 ms | 31.0 ms |
| readLists       | 23.1 ms | 22.2 ms | 24.5 ms | 28.2 ms |
| readVitalSigns  | 25.5 ms | 25.2 ms | 30.5 ms | 37.5 ms |
| verifyIntegrity | 41.7 ms | 71.4 ms | 114.2 ms | 127.4 ms |

- x1: 6.031 peticiones (33,2 req/s), 0 % de errores.
- x5: 24.176 peticiones (131,1 req/s), 0 % de errores.
- x10: 46.852 peticiones (255,2 req/s), 0 % de errores.
- Umbrales cumplidos en los tres perfiles: p95 < 300 ms en lecturas, < 500 ms en apertura y borrador,
  < 800 ms al firmar y < 2 s al verificar la integridad.

## Conclusiones

1. **Firmar cuesta poco.** Cifrado, hash canónico, sello ECDSA y encadenamiento suman menos de 10 ms
   sobre una escritura simple, y a 10 veces el pico el p95 sigue por debajo de 41 ms.
2. **Encadenar por paciente no crea contención.** La serialización toma el bloqueo del paciente, no
   de la tabla, así que atenciones de pacientes distintos no compiten.
3. **La verificación de integridad es lo único que crece con la historia.** Recalcula toda la cadena
   del paciente: con 16.593 registros acumulados el p95 pasó de 42 ms a 114 ms entre x1 y x10. Es una
   operación de auditoría, no de consulta clínica; si llegara a pesar, se verifica por tramos desde el
   último punto anclado en `clinical.encounters.v1`.
4. **Nada se cae al subir la carga**: 0 % de errores en los tres perfiles y sin degradación de las
   lecturas mientras se firma.

## Cómo ejecutarla

Requisitos: Docker y Node.js (solo para firmar tokens locales). El stack se levanta igual que en
[`platform/e2e/README.md`](../../platform/e2e/README.md).

```bash
cd BackEnd-Clinica

docker exec -i clinical-db sh -c 'mysql -uroot -p"$(cat "$MYSQL_ROOT_PASSWORD_FILE")" "$MYSQL_DATABASE"' \
  < clinical-history-service/load-test/seed-patient-references.sql

TOKEN=$(node platform/e2e/generate-token.mjs /tmp/clinica-private.pem DOCTOR "$(cat /proc/sys/kernel/random/uuid)" 7200)

docker run --rm --network host -v "$PWD/clinical-history-service/load-test:/scripts:ro" \
  -e TOKEN="$TOKEN" -e BASE_URL=http://127.0.0.1:8091 -e MULTIPLIER=1 -e DURATION=2m \
  grafana/k6 run --quiet /scripts/clinical-load.js
```

Variables del script: `BASE_URL`, `MULTIPLIER`, `DURATION`, `SEEDED_PATIENTS`, `ENCOUNTER_POOL` y
`SUMMARY_FILE` para exportar el resumen completo en JSON. El token debe ser reciente: firmar exige un
`iat` de menos de 15 minutos, así que una corrida larga necesita renovarlo.

La siembra escribe directamente como `root` en la base de pruebas; en un entorno real nadie fuera de
`clinical_migrator` y `clinical_app` tiene acceso, y la copia local solo se llena con eventos de
`patient-service`.
