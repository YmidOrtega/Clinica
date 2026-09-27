# Prueba de carga de billing-service

Simula un turno de facturación: el equipo consulta cuentas, facturas y bandejas sin parar, cuatro
facturadores cargan consultas a sus ventas, uno por segundo cada uno, y en paralelo se facturan consultas
de punta a punta (abrir venta, cargar, confirmar con el precio del contrato, borrador y emisión con firma
XAdES en OpenBao).

| Escenario | Qué hace | Ritmo con `MULTIPLIER=1` |
|---|---|---|
| `reads` | resumen de la cuenta, facturas de la cuenta, una factura, bandeja de radicación y bandeja de glosas | 10 por segundo |
| `charges` | cuatro facturadores, cada uno con su propia venta abierta, cargan una consulta por segundo | 4 por segundo |
| `invoicing` | una consulta facturada de punta a punta | 18 por minuto |

Al final revisa que las facturas de la cuenta no repitan consecutivo ni dejen huecos: la numeración
tiene que aguantar emisiones concurrentes.

## Preparar datos

La emisión cruza servicios (admisiones, contratación, pacientes) con intercambio de tokens, así que
necesita un usuario real de facturación, no uno firmado a mano. El E2E deja todo listo y, con
`E2E_EXPORT_FILE`, escribe los datos y el token de un facturador recién creado:

```sh
COMPOSE_PROJECT=<proyecto> E2E_EXPORT_FILE=/tmp/billing-load.env sh platform/e2e/billing-e2e.sh
```

## Ejecutar

Desde `BackEnd-Clinica`, inmediatamente después del E2E. El token dura cinco minutos y la emisión exige
que el segundo factor tenga menos de cinco minutos, y la prueba dura unos cuatro:

```sh
set -a; . /tmp/billing-load.env; set +a
docker run --rm --network host -v "$PWD/billing-service/load-test:/scripts:ro" \
  -e BASE_URL="$BILLING_URL" -e TOKEN -e ADMISSION_NUMBER -e INVOICE_UUID -e PORTFOLIO_ITEM_UUID \
  -e MULTIPLIER=1 grafana/k6 run --quiet /scripts/billing-load.js
```

`MULTIPLIER` escala solo las lecturas. La facturación no escala con él: una cuenta admite 99 ventas y
cada factura de la prueba abre una, así que con `DURATION=3m` y 18 facturas por minuto se usan unas 60.
Para medir más emisión, corre el E2E otra vez (episodio nuevo) y sube `ISSUE_RATE_PER_MINUTE` sin pasar
ese techo.

## Umbrales

| Operación | p95 |
|---|---|
| Lecturas | 300 ms |
| Cargos a la venta | 500 ms |
| Borrador y emisión con firma | 2 000 ms |

Con menos de 1 % de peticiones fallidas y más de 99 % de comprobaciones correctas, incluidas las de la
numeración.

## Medido

Stack de desarrollo en Docker con dos réplicas de billing-service, OpenBao en clúster de tres nodos y
los simuladores de la DIAN y del MUV:

| MULTIPLIER | Peticiones | Facturas emitidas | Errores | p95 lecturas | p95 cargos | p95 emisión |
|---|---|---|---|---|---|---|
| 1 | 2 997 | 55 | 0 % | 64 ms | 59 ms | 116 ms |
| 5 | 11 099 | 54 | 0 % | 48 ms | 55 ms | 84 ms |

Ningún consecutivo repetido ni hueco en las dos corridas. La emisión incluye la firma con la clave DIAN
en OpenBao transit; si se degrada, el primer sospechoso es la latencia hacia OpenBao y luego el bloqueo
de la fila del consecutivo de la resolución.
