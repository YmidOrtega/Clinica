# Eventos de la historia clínica

`clinical-history-service` publica dos topics con el mismo outbox y conector
(`debezium/clinical-outbox.json`): los hechos clínicos en `clinical.encounters.v1` y los accesos en
`clinical.access-audit.v1`. Ninguno lleva el texto de las notas.

## Hechos clínicos — `clinical.encounters.v1`

Cada hecho que entra a la cadena sellada del paciente se publica en la misma transacción: apertura de
atención, nota firmada, nota anulada y cierre. Lo consumirán admisiones, facturación y los servicios
futuros de órdenes y prescripción.

| Propiedad | Valor |
|---|---|
| Clave | `patientUuid` |
| Limpieza | `delete`, `retention.ms = -1` (no se compacta) |
| Cabecera | `eventType` = `EncounterOpened`, `ClinicalNoteSigned`, `ClinicalNoteVoided`, `EncounterClosed` |
| Contrato | [`clinical.encounters.v1.schema.json`](clinical.encounters.v1.schema.json) |

```json
{
  "eventId": "5f0c7a1e-2b3d-4c5e-8f9a-0b1c2d3e4f5a",
  "type": "ClinicalNoteSigned",
  "schemaVersion": 1,
  "occurredAt": "2026-09-14T15:10:00.123456Z",
  "patientUuid": "3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d",
  "encounterId": "8a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
  "data": {
    "noteId": "5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e",
    "noteType": "DISCHARGE",
    "restricted": false,
    "author": {"uuid": "00000000-0000-4000-8000-000000000003", "role": "DOCTOR"},
    "careOccurredAt": "2026-09-14T15:00:00Z",
    "extemporaneous": false,
    "diagnoses": [{"code": "I10X", "display": "Hipertension esencial (primaria)", "catalogVersion": "2021-02-08",
                   "role": "PRINCIPAL", "type": "CONFIRMED_NEW"}],
    "listChanges": 1, "vitalSigns": 0, "attachments": 0
  },
  "chain": {"sequence": 7, "entryHash": "9d4…", "previousHash": "1a2…", "keyId": "seal-2026"}
}
```

- `diagnoses` lleva los códigos CIE-10 también de notas restringidas (facturación y RIPS los necesitan);
  `restricted: true` avisa a los consumidores que deben aplicar sus propias restricciones de acceso.
- `chain` publica la cabeza de la cadena después de cada hecho. Guardarla fuera del servicio permite
  detectar que alguien borró los últimos registros de un paciente, algo que la cadena por sí sola no
  puede detectar.
- `ClinicalNoteVoided` no incluye el motivo ni `encounterId`.

## Accesos — `clinical.access-audit.v1`

`clinical-history-service` publica un evento por cada decisión de acceso a los datos clínicos de un
paciente: lecturas, escrituras, aperturas y cierres, altas en el equipo de cuidado, accesos de
emergencia y también los intentos rechazados. Lo consumirá el futuro `audit-service`, que construirá
el registro de accesos que el paciente puede consultar.

## Cómo se publican

```
RecordAccess ── misma transacción que la lectura o escritura ──► clinical_outbox.outbox_events
                                                                          │ binlog
                                                                          ▼
                                               Debezium (Outbox Event Router, debezium/clinical-outbox.json)
                                                                          │
                                                                          ▼
                                                           clinical.access-audit.v1
```

- Si no se puede escribir el evento, la operación falla: no hay accesos concedidos sin rastro.
- Los rechazos se registran en una transacción propia después de que la operación fallida se deshace.
- La tabla del outbox se purga a los 7 días (`clinica.clinical.outbox.retention`); el topic es la
  fuente de verdad.

## Topic

| Propiedad | Valor |
|---|---|
| Nombre | `clinical.access-audit.v1` |
| Clave | `patientUuid` (los eventos de un paciente conservan su orden) |
| Particiones | 3 |
| Limpieza | `delete` con `retention.ms = -1` (retención indefinida, no se compacta) |
| Cabecera | `eventType` = `ClinicalRecordAccessed` o `ClinicalRecordAccessDenied` |
| Contrato | [`clinical.access-audit.v1.schema.json`](clinical.access-audit.v1.schema.json) |

## Evento

```json
{
  "eventId": "0b8f3c2e-5d7a-4f1b-9c3e-2a4d6f8b1c5e",
  "type": "ClinicalRecordAccessed",
  "schemaVersion": 1,
  "occurredAt": "2026-09-14T15:04:05.123456Z",
  "traceId": "6aa784268a19c13f520604db08f7b692",
  "patientUuid": "3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d",
  "actor": {"uuid": "00000000-0000-4000-8000-000000000003", "role": "DOCTOR"},
  "action": "READ_NOTE",
  "resource": {"id": "5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e"},
  "outcome": "GRANTED",
  "basis": "EMERGENCY_ACCESS",
  "restrictedContent": true,
  "emergencyReason": "Paciente inconsciente en reanimación, se requieren antecedentes"
}
```

- `basis` explica por qué se concedió: `NEW_ENCOUNTER`, `CARE_RELATIONSHIP`, `AUTHOR` o
  `EMERGENCY_ACCESS`; es `null` en los rechazos.
- `restrictedContent` marca la lectura de notas restringidas (salud mental, salud sexual, VIH,
  violencia).
- `emergencyReason` solo viaja cuando el acceso se apoyó en un acceso de emergencia.
- `EXPORT_RECORD` registra las copias en PDF emitidas por el área de archivo clínico (`MEDICAL_RECORDS`),
  con `basis: RECORDS_OFFICE` y `exportReason`.
- Ningún evento lleva texto clínico.
- `traceId`, `basis` y `emergencyReason` pueden llegar ausentes en lugar de `null`: el Outbox Event
  Router de Debezium omite los campos nulos al expandir el JSON. Los consumidores deben tratar ausente y
  `null` igual.

## Reglas de acceso que generan estos eventos

- Relación de cuidado: pertenecer al equipo de una atención del paciente mientras está abierta y hasta
  30 días después del cierre (`clinica.clinical.access.relationship-after-closure`). Quien abre la
  atención entra al equipo; los demás los agrega alguien del equipo.
- Escribir, cerrar o anular exige pertenecer al equipo de esa atención.
- Las notas restringidas solo las leen su autor y el equipo de su atención.
- Acceso de emergencia: motivo obligatorio, dura 4 horas
  (`clinica.clinical.access.emergency-access-duration`) y cubre también las notas restringidas.
