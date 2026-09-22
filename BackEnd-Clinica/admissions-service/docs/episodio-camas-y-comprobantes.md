# El episodio, las camas y el comprobante

## Qué es un episodio y por qué no es una "atención"

Antes de este refactor, "atención" nombraba dos cosas distintas: lo que abre la historia clínica cuando
alguien atiende al paciente, y lo que abre admisiones cuando el paciente entra a la clínica. Son dos
conceptos con dueños distintos y ahora se llaman distinto: **atención** es de `clinical-history-service`,
**episodio** es de aquí. La llave común es el `admissionUuid` que la atención guarda.

Un episodio es administrativo: quién entra, por qué servicio pasa, qué cama ocupa, quién responde por él,
qué autorizó el pagador y cómo sale. No hay diagnósticos ni notas: eso es historia clínica.

## Una admisión, varias fases

Urgencias que termina en hospitalización **no** son dos admisiones. Es un episodio que cambia de fase:

```
ADM-2026-000123
  ├─ fase 1  EMERGENCY     Urgencias · Sede Norte      10:04 → 14:30
  └─ fase 2  INPATIENT     Hospitalización · Piso 3    14:30 → (en curso)
```

Un número, una factura, un historial. Cada fase sabe si exige cama (`bedRequired`) y si la cobertura
puede bloquear la admisión, porque eso depende del **tipo de servicio**, no del flujo. Por eso activar
una hospitalización sin cama responde `ADMISSION_BED_REQUIRED`, y urgencias no.

## La cobertura nunca frena una urgencia

La verificación contra `contracting-service` tiene tres resultados y dos comportamientos:

| Resultado | Urgencias | Hospitalización y ambulatorio |
|---|---|---|
| Con contrato vigente | admite | admite |
| Sin cobertura | **admite** y lo marca | bloquea (`ADMISSION_WITHOUT_COVERAGE`) |
| Contracting no responde | **admite** y lo marca | admite y lo marca |

La primera fila de la primera columna no es una concesión técnica: la atención inicial de urgencias es
obligatoria por ley (Ley 100, art. 168). Todo lo marcado alimenta `GET /episodes/pending-coverage`, que
es lo que revisa facturación. Saltarse la cobertura a propósito existe (`overrideCoverage`), pero exige
permiso **y** reautenticación con segundo factor.

## Las camas las cuida la base de datos

Dos personas pueden pedir la misma cama en el mismo instante. La exclusividad no la decide la aplicación:
la impone PostgreSQL con `EXCLUDE USING gist (bed_id WITH =, tsrange(started_at, ended_at) WITH &&)` sobre
las estancias. La aplicación traduce el rechazo a `BED_ALREADY_TAKEN`; aunque alguien escriba por debajo
de la aplicación, la cama no se duplica.

Liberar una cama la manda a **limpieza**, no a disponible. Es lo que pasa en el piso, y evita que el
siguiente paciente entre a una cama sin tender.

## Cómo termina un episodio

El egreso dice **cómo** termina, con lo que cada forma exige:

| Egreso | Qué pide | Consecuencia |
|---|---|---|
| Alta médica | notas opcionales | libera la cama |
| Alta voluntaria | quién firma la responsabilidad y su documento | libera la cama |
| Remisión | código REPS de la institución receptora, su nombre y el motivo | libera la cama |
| Fuga | cuándo se notó la ausencia | libera la cama · **step-up** |
| Fallecimiento | certificado y momento del fallecimiento | libera la cama · **step-up** · avisa a patient-service |

El aviso del fallecimiento no bloquea el egreso: si `patient-service` no responde, el episodio queda en
`GET /episodes/pending-death-notice` y se reintenta con `POST /episodes/{uuid}/death-notice`. Cerrar el
episodio nunca depende de que otro servicio esté en pie.

Un NN que fallece **sigue pudiendo identificarse después**: eso obligó a cambiar `patient-service`, donde
la muerte de un no identificado era un estado terminal que impedía identificarlo. Ahora la muerte viaja
con él cuando se identifica, y si la identificación fue un error, revertirla devuelve a la persona real
a la vida (con motivo y auditoría).

## El triage no se decide aquí

El nivel de triage lo firma una enfermera en la historia clínica. Admisiones guarda una **copia de solo
lectura** que llega por `clinical.encounters.v1` y solo la usa para ordenar su cola:

```
SIN TRIAGE (por hora de llegada)   ← primero: nadie los ha valorado todavía
Nivel I    (por hora de llegada)
...
Nivel V    (por hora de llegada)
```

Se descartó preguntarle el nivel a clinical cada vez que se pinta el tablero: ese servicio audita cada
lectura de historia clínica, y un tablero de urgencias refrescándose inundaría la auditoría.

## El comprobante y su verificación

`POST /episodes/{uuid}/receipt` entrega un PDF y guarda **solo su huella SHA-256 y el sello**, firmado con
la clave institucional `admissions-seal` que nunca sale de OpenBao. El PDF no se almacena: reemitirlo
produce otro documento con otra huella.

Se verifica de dos maneras:

- **Con el documento**, autenticado: se sube el PDF y responde si coincide con el emitido y si el sello es
  válido.
- **Públicamente**, sin credenciales: `{number, sha256}` → `{authentic}`. No revela nada del episodio ni
  del paciente, y tiene cuota por IP en el gateway y en el servicio.

El material que se firma incluye el **propósito** del documento (`clinica.admissions.receipt/v1`), así que
un sello de una copia de historia clínica no sirve para un comprobante de admisión, ni al revés.

## Lo que sale al bus

Cada hecho del episodio se publica en `admissions.events.v1` por outbox y Debezium (réplica lógica de
PostgreSQL), con la ficha resumida del episodio dentro:

`AdmissionRegistered` · `AdmissionPhaseChanged` · `AdmissionBedAssigned` · `AdmissionBedReleased` ·
`AdmissionCoveragePending` · `AdmissionDischarged` · `AdmissionCancelled`

El número del certificado de defunción, los motivos clínicos del triage y el detalle de las
autorizaciones **no** viajan. Billing escuchará `AdmissionDischarged` en su turno: admisiones no conoce
facturas y el episodio se congela al egresar, no al facturarse.
