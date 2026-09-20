# Prueba de carga de practitioners-service

Mide lo que consulta el resto del sistema: leer la ficha de un profesional, buscar por especialidad, leer
el catálogo y resolver qué profesional corresponde a una cuenta de auth (lo que necesita la historia
clínica para nombrar a quien firma).

Los honorarios **no** se miden aquí: son de talento humano, no de un camino caliente.

## Preparar datos

Con el stack arriba (`docker-compose.debug.yml` publica el puerto 8085), el guion de E2E deja una
especialidad, un profesional con subespecialidad y una cuenta vinculada:

```sh
COMPOSE_PROJECT=<proyecto> sh ../../platform/e2e/practitioners-e2e.sh
```

De su salida, o consultando, se toman los tres identificadores:

```sh
TOKEN=$(COMPOSE_PROJECT=<proyecto> E2E_TOKEN_TTL=3600 sh ../../platform/e2e/staff-token.sh HUMAN_RESOURCES 00000000-0000-4000-8000-000000000009)
curl -s -X POST http://127.0.0.1:8085/api/v1/practitioners/search -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"lastNames":"Quintero"}' \
  | jq -c '[.[] | select(.account.linked)] | .[0] | {uuid, specialty: .specialties[0].specialtyCode, account: .account.userUuid}'
```

## Correr

```sh
k6 run -e BASE_URL=http://127.0.0.1:8085 -e TOKEN="$TOKEN" \
       -e PRACTITIONER_UUID=... -e SPECIALTY_CODE=... -e ACCOUNT_UUID=... \
       -e MULTIPLIER=1 practitioners-load.js
```

Sin k6 instalado, desde `BackEnd-Clinica`:

```sh
docker run --rm --network host -v "$PWD/practitioners-service/load-test:/scripts:ro" \
  -e BASE_URL=http://127.0.0.1:8085 -e TOKEN="$TOKEN" \
  -e PRACTITIONER_UUID=... -e SPECIALTY_CODE=... -e ACCOUNT_UUID=... -e MULTIPLIER=1 \
  grafana/k6 run --quiet /scripts/practitioners-load.js
```

`MULTIPLIER` escala las cuatro tasas a la vez: 1 es el pico esperado (20 lecturas de ficha por segundo);
5 y 10 sirven para ver dónde empieza a doler.

## Umbrales

| Escenario | p95 |
|---|---|
| Leer la ficha | 200 ms |
| Buscar por especialidad | 400 ms |
| Leer el catálogo | 300 ms |
| Resolver la cuenta | 200 ms |

Con menos de 1 % de errores en total. Buscar por especialidad es el más caro porque cruza las tres tablas
del directorio; si se degrada, el sospechoso es `idx_practitioner_specialties_specialty`.

## Medido

Con una réplica del servicio, la base en Docker y los datos que deja el E2E (un puñado de profesionales y
una especialidad con una subespecialidad):

| MULTIPLIER | Lecturas de ficha/s | Peticiones | Errores | p95 por escenario |
|---|---|---|---|---|
| 1 | 20  | 8 910  | 0 % | 17–18 ms |
| 5 | 100 | 44 547 | 0 % | 13–14 ms |

El directorio de una clínica son cientos de filas, no millones: estas cifras dicen que las consultas están
bien indexadas, no que el servicio aguante un catálogo enorme. Con miles de profesionales habría que volver
a medir la búsqueda por especialidad.
