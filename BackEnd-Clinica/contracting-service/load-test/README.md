# Prueba de carga de contracting-service

Mide lo que consulta el resto del sistema: resolver precios, leer un contrato, buscar un pagador y
preguntar por la cobertura capitada de una persona.

## Preparar datos

Con el stack arriba (`docker-compose.debug.yml` publica el puerto 8087), el guion de E2E deja un pagador,
un manual publicado y un contrato activo listos:

```sh
sh ../../platform/e2e/contracting-e2e.sh
```

Toma de su salida el uuid del contrato y el del pagador, o consúltalos:

```sh
TOKEN=$(sh ../../platform/e2e/staff-token.sh CONTRACTING)
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"socialReason":"E"}' http://127.0.0.1:8087/api/v1/payers/search | jq '.content[0]'
```

## Correr

```sh
k6 run -e BASE_URL=http://127.0.0.1:8087 \
       -e TOKEN="$(sh ../../platform/e2e/staff-token.sh CONTRACTING)" \
       -e CONTRACT_UUID=... -e PAYER_UUID=... \
       -e MULTIPLIER=1 contracting-load.js
```

Sin k6 instalado, desde `BackEnd-Clinica`:

```sh
TOKEN=$(COMPOSE_PROJECT=<proyecto> E2E_TOKEN_REFRESH=1 E2E_TOKEN_TTL=3600 sh platform/e2e/staff-token.sh CONTRACTING)

docker run --rm --network host -v "$PWD/contracting-service/load-test:/scripts:ro" \
  -e BASE_URL=http://127.0.0.1:8087 -e TOKEN="$TOKEN" \
  -e CONTRACT_UUID=... -e PAYER_UUID=... -e MULTIPLIER=1 \
  grafana/k6 run --quiet /scripts/contracting-load.js
```

`MULTIPLIER` escala las cuatro tasas a la vez: 1 es el pico esperado de la clínica (25 consultas de precio
por segundo, que es lo que factura un día completo concentrado en la hora pico), 5 y 10 sirven para ver
dónde empieza a doler.

El token dura cinco minutos, así que para corridas largas usa `E2E_TOKEN_TTL=3600` al generarlo.

## Umbrales

| Escenario | p95 |
|---|---|
| Resolver precios | 400 ms |
| Leer un contrato | 200 ms |
| Buscar un pagador | 300 ms |
| Consultar cobertura | 300 ms |

Con menos de 1 % de errores en total. Resolver precios es el más caro porque toca paquetes, excepciones y
manual en la misma consulta; si se degrada, el sospechoso es el índice
`idx_contract_tariff_exceptions_lookup` o el número de paquetes vigentes del contrato.

## Medido

Con una réplica del servicio, la base en Docker y los datos que deja el E2E (un manual publicado, un
contrato por evento con una excepción y sin paquetes):

| MULTIPLIER | Consultas de precio/s | Peticiones | Errores | p95 por escenario |
|---|---|---|---|---|
| 1  | 25  | 8 098  | 0 % | 13–18 ms |
| 5  | 125 | 40 498 | 0 % | 9–12 ms |
| 10 | 250 | 80 996 | 0 % | 8–10 ms |

Los umbrales sobran por dos órdenes de magnitud, pero el contrato de la medición es el caso barato:
una sola excepción y ningún paquete vigente. Un contrato con decenas de paquetes es el escenario que
hay que volver a medir antes de confiar en estos números.
