# El ciclo de facturación: de la venta a la glosa

Una cuenta de billing es la de un episodio de admisiones y se busca por su número de atención
(`ADM-AAAA-NNNNNN`). Todo lo que pasa después cuelga de ese número:

```
venta ──► confirmación ──► borrador ──► emisión ──► firma ──► DIAN ──► RIPS + CUV ──► radicado ──► glosas
 sell        sell          invoice      invoice    (auto)    (auto)       invoice        file        glosses
```

La fila de abajo es el permiso que exige cada paso. Los datos del episodio, del paciente, del contrato y
de los profesionales llegan por eventos de admisiones y de historia clínica, y cuando falta algo se
consultan en vivo a su dueño con intercambio de tokens: el usuario que factura es el que consulta.

## La venta fija el precio

Se abre una venta sobre el número de atención (`POST /sales`) y se le cargan servicios del portafolio
(`/lines`) o procedimientos quirúrgicos con su vía y su equipo (`/procedures`, `/surgical-team`). La
estancia no se carga a mano: los tramos de cama de admisiones se convierten en periodos de 24 h y cada
tipo de estancia se cobra con el servicio que diga `PUT /stay-charges/{stayType}`.

`GET /sales/{uuid}/price-preview` tasa con el contrato vigente sin comprometer nada. Confirmar
(`/confirmation`) congela los precios que dio `contracting-service`; desde ahí la venta no cambia y solo
se anula con motivo. Lo que el contrato no tasa se pone a mano con `billing:price-manually`, y queda
registrado quién y por qué. Las ventas quirúrgicas confirmadas generan los honorarios de los profesionales
(`/practitioner-fees`).

## La factura: borrador, emisión y firma

El borrador (`POST /invoices`) agrupa lo confirmado de una unidad facturable y calcula lo que paga el
paciente. Emitirlo (`/issuance`) toma el siguiente consecutivo de la resolución activa bajo un bloqueo de
fila: dos emisiones simultáneas nunca repiten número ni dejan huecos, y la prueba de carga lo comprueba.

Sin contrato con el pagador, el borrador se factura al paciente como particular, salvo que facturación
indique en `uncontracted` uno de los casos del DT2 en que el pagador responde sin contrato (urgencias;
ADRES, SOAT o planes voluntarios; tutela; portabilidad; excepcional; recuperación de órganos), la cobertura
y una justificación. Esa factura va al pagador por evento, sin CUCON y con `FACTURA_SIN_CONTRATO`, y pide
segundo factor reciente. `GET /accounts/{n}/summary` trae `uncontractedProposal` cuando el episodio tiene
pagador y no contrato: urgencias si el episodio lo es y la cobertura UPC del régimen del paciente. La
póliza (`policyNumber`) es obligatoria con coberturas SOAT o de planes voluntarios y no se admite en las demás.

Lo que paga el paciente se le factura al recaudarlo (`POST /shared-payments`): copago, cuota moderadora o
pago compartido de plan voluntario, los tres conceptos de recaudo que admite el DT2 de la Resolución 948
de 2026. La factura al pagador lo descuenta como `PrepaidPayment`. La cuota de recuperación no se recauda:
la extensión de salud vigente no tiene campo para ella ni RIPS un concepto, así que no habría cómo
descontarla al pagador; a quien no tiene afiliación se le factura como particular.

La firma XAdES-EPES es un paso aparte: emitir nunca espera a OpenBao. La clave privada del certificado
DIAN está importada en OpenBao transit (`transit/billing-dian`) y no se puede exportar; billing arma el XML
UBL 2.1 con sus extensiones (DIAN, sector salud con modalidad de pago, cobertura y copagos, y firma) y
transit firma el resumen. Si OpenBao no responde, la factura queda emitida sin firma y una tarea la
reintenta (`BILLING_DIAN_SIGNATURE_RETRY_DELAY`); `POST /signature` lo fuerza y es idempotente.

## La DIAN

Una tarea envía a la DIAN las facturas firmadas cada `BILLING_DIAN_DELIVERY_DELAY` (30 s por defecto) y
consulta el estado de las que esperan validación. `/dian-delivery` la envía ya o reenvía una rechazada, y
`/dian-verdicts` guarda cada respuesta tal cual. Aceptada, queda disponible el `AttachedDocument` firmado,
que es lo que se entrega al adquiriente y al Ministerio.

Mientras el emisor esté en habilitación se factura contra el ambiente de pruebas; pasar a producción
(`POST /issuer/production`) es configuración fiscal y exige `billing:manage-config` con segundo factor.

Anular no existe: lo emitido se corrige con **notas crédito** (`/credit-notes`, `billing:void`), que
siguen el mismo camino de firma y DIAN.

## RIPS y CUV del Ministerio

`GET /invoices/{uuid}/rips` muestra el RIPS JSON que se armaría. `POST /rips-validation` lo envía junto
con el `AttachedDocument` al mecanismo único de validación (MUV, API FEV-RIPS) con las credenciales
SISPRO que están en OpenBao. El envío es manual y con reintentos: si el Ministerio no contesta, el envío
queda `PENDING` y se reintenta cada `BILLING_MINISTRY_RETRY_DELAY`. Validado, la factura guarda el
**CUV**; rechazado, quedan los hallazgos para corregir y volver a enviar. Cada envío conserva el RIPS
enviado y la respuesta (`/rips-validations/{submission}/{rips|response}`).

El cliente sigue el *Documento para consumo de API FEV-RIPS* del Ministerio: `POST /api/Auth/LoginSISPRO`
con `persona.identificacion`, `clave` y `nit` responde `token` (JWT), `login`, `registrado` y `errors`.
El token se reusa hasta cinco minutos antes del `exp` que trae (110 minutos si no lo trae) y se renueva
si el MUV contesta 401 o `TOT002`. `login: false` es `MINISTRY_CREDENTIALS_REJECTED`: no se reintenta
hasta corregir las credenciales. El campo opcional `tipoUsuario` del login es para profesionales
independientes y no aplica a la clínica.

## Radicar ante el pagador

El pagador tiene que recibir la factura dentro de los 22 días hábiles siguientes a su expedición
(Res. 2284 de 2023). Radicar (`POST /filing`, `billing:file`) exige el CUV, y el radicado se corrige con
motivo (`PUT /filing`). `/filing-package` descarga el ZIP con la factura, el `AttachedDocument`, el RIPS y
la respuesta del Ministerio.

`GET /filings/pending` es la bandeja de cartera con su semáforo: `ON_TIME`, `DUE_SOON` cuando quedan
`BILLING_FILING_WARNING_BUSINESS_DAYS` días hábiles (5 por defecto) y `OVERDUE`. Los días hábiles
excluyen fines de semana y festivos colombianos, incluidos los que dependen de la Pascua.

## Devoluciones y glosas

Lo que comunica el pagador se registra con sus causales del manual único (Anexo 3: `DE`, `FA`, `TA`,
`SO`, `AU`, `CO`, `CL`, `SA`; catálogo en `GET /objection-codes`):

| | Plazo del pagador para formularla | Plazo de la clínica para responder | Plazo del pagador para decidir |
|---|---|---|---|
| Devolución | 5 días hábiles | 5 días hábiles | 5 días hábiles |
| Glosa | 20 días hábiles | 15 días hábiles | 10 días hábiles |

Cada causal de glosa señala la línea glosada, salvo las de seguimiento de acuerdos (`SA`: indicadores,
nota técnica), que afectan la factura completa y solo se limitan contra su saldo. Si se acepta valor de
una `SA`, la nota crédito lo reparte entre las líneas en proporción a lo que a cada una le queda por
acreditar.

La respuesta usa los códigos `RE`. Aceptar un valor emite en la misma transacción la nota crédito por ese
valor, por eso exige además `billing:void`. La decisión del pagador (`/decision`) deja en firme lo
aceptado y lo levantado. `GET /objections/pending` es la bandeja con semáforo; si vence el plazo sin
respuesta, la glosa se entiende aceptada tácitamente.

## Quién hace qué

| Rol | Puede |
|---|---|
| `BILLING` | todo el ciclo: vender, facturar, notas crédito, recaudar copagos, radicar y responder glosas |
| `ACCOUNTS_RECEIVABLE` (cartera) | leer, radicar y responder glosas; aceptar valor le exige `billing:void`, que no tiene |
| `ADMIN` | configuración fiscal: emisor, resoluciones de numeración y paso a producción |

Emisor, numeración, radicado y glosas piden segundo factor reciente.

## Eventos

Cada cambio de una factura emitida sale en `billing.invoices.v1` (compactado, con el estado completo), y
las alertas diarias de radicación y de glosas en `billing.filing-deadlines.v1` y
`billing.claim-objections.v1`. El detalle está en [`../events/README.md`](../events/README.md).

## Probarlo sin la DIAN ni el Ministerio

`docker-compose.debug.yml` levanta `dian-simulator` y `muv-simulator` (WireMock, ver
`platform/simulators/README.md`) y apunta billing a ellos. Con el stack arriba:

```sh
COMPOSE_PROJECT=<proyecto> sh platform/e2e/billing-e2e.sh
```

recorre el ciclo completo con personal real y segundo factor: venta, emisión, firma en OpenBao, DIAN,
CUV, radicado, glosa respondida con nota crédito y decisión del pagador. La prueba de carga está en
[`../load-test/README.md`](../load-test/README.md).

## Variables

| Variable | Por defecto | Para qué |
|---|---|---|
| `BILLING_DIAN_TEST_URL`, `BILLING_DIAN_PRODUCTION_URL` | servicios web de la DIAN | endpoint SOAP de habilitación y de producción |
| `BILLING_DIAN_SOFTWARE_ID`, `BILLING_DIAN_SOFTWARE_PIN`, `BILLING_DIAN_TEST_SET_ID` | vacías | software propio registrado ante la DIAN; en el stack vienen de OpenBao (`secret/billing/dian/software`) |
| `BILLING_DIAN_SIGNING_KEY` | `billing-dian` | clave de transit con la que se firma |
| `BILLING_DIAN_DELIVERY_DELAY` | `PT30S` | cada cuánto se envían las firmadas y se consultan las pendientes |
| `BILLING_DIAN_SIGNATURE_RETRY_DELAY` | `PT1M` | reintento de firmas pendientes |
| `BILLING_MINISTRY_VALIDATOR_URL` | `https://fevrips-api:9443` | API FEV-RIPS del MUV |
| `BILLING_MINISTRY_DOCUMENT_TYPE`, `BILLING_MINISTRY_DOCUMENT_NUMBER`, `BILLING_MINISTRY_PASSWORD` | vacías | usuario SISPRO; en el stack vienen de `secret/billing/ministry/credentials` |
| `BILLING_MINISTRY_RETRY_DELAY` | `PT5M` | reintento de validaciones pendientes |
| `BILLING_FILING_WARNING_BUSINESS_DAYS`, `BILLING_OBJECTIONS_WARNING_BUSINESS_DAYS` | 5 y 3 | cuándo el semáforo pasa a `DUE_SOON` |
| `BILLING_FILING_ALERTS_CRON`, `BILLING_OBJECTIONS_ALERTS_CRON` | 6:00 y 6:05 | tareas diarias de alertas |
| `BILLING_OUTBOX_RETENTION` | `7d` | cuánto se guardan los eventos ya publicados |
| `BILLING_PRIVATE_CONTRACT_UUID` | vacía | contrato con que se tasa lo particular |
| `BILLING_PUBLIC_CHECKS_PER_WINDOW`, `BILLING_PUBLIC_CHECK_WINDOW` | 600 por `1m` | límite de la verificación pública de PDFs |
