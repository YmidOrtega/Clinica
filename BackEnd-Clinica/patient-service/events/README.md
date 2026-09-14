# Eventos de pacientes — `patient.events.v1`

`patient-service` publica un evento cada vez que cambian los datos del paciente que otros servicios
necesitan. Los consumidores (admissions, clinical-history, billing) mantienen su propia copia de esos
datos y siguen funcionando aunque `patient-service` esté caído.

## Cómo se publican

```
PatientCommands ── misma transacción ──► patients  +  patient_outbox.outbox_events
                                                              │ binlog
                                                              ▼
                                         Debezium (Kafka Connect, Outbox Event Router)
                                                              │
                                                              ▼
                                                   topic patient.events.v1
```

1. El comando guarda el paciente y escribe el evento en `patient_outbox.outbox_events` en la **misma
   transacción**: o se guardan ambos o ninguno.
2. Debezium lee el binlog de MySQL y publica cada fila en Kafka. `patient-service` no usa Kafka y no
   depende de que Kafka o Connect estén arriba.
3. Las filas del outbox se purgan a los 7 días (`clinica.patient.outbox.retention`). Si Connect se
   registra tarde, su snapshot inicial publica las filas aún guardadas.

## Topic

| Propiedad        | Valor                                                                 |
| ---------------- | --------------------------------------------------------------------- |
| Nombre           | `patient.events.v1`                                                   |
| Clave            | UUID del paciente o del paciente sin identificar (orden garantizado por clave) |
| Particiones      | 3                                                                     |
| Limpieza         | `compact`: Kafka conserva al menos el último evento de cada paciente  |
| Cabecera         | `eventType` con el tipo del evento                                    |
| Valor            | JSON validado por [`patient.events.v1.schema.json`](patient.events.v1.schema.json) |

## Tipos

| Tipo                           | Cuándo                                        | Datos adicionales   |
| ------------------------------ | --------------------------------------------- | ------------------- |
| `PatientRegistered`            | Registro                                      | —                   |
| `PatientDocumentChanged`       | Cambio de documento                           | `previousDocument`  |
| `PatientDemographicsCorrected` | Corrección de nombre, nacimiento, sexo…       | —                   |
| `PatientAffiliationUpdated`    | Cambio de afiliación                          | —                   |
| `PatientDeactivated`           | Desactivación                                 | —                   |
| `PatientReactivated`           | Reactivación                                  | —                   |
| `PatientDied`                  | Registro de fallecimiento                     | `dateOfDeath` en el paciente |
| `UnidentifiedPatientRegistered` | Registro de un paciente sin identificar       | `data.unidentifiedPatient` |
| `UnidentifiedPatientIdentified` | Vinculación con el paciente real             | `identifiedPatientUuid` |
| `UnidentifiedPatientIdentificationReverted` | Reversión de una identificación equivocada | `previousPatientUuid` |
| `UnidentifiedPatientDied`      | Fallecimiento sin identificar                 | `dateOfDeath`        |

Los cambios de contacto y residencia **no** generan eventos: esos datos no salen del servicio. La
descripción física de un paciente sin identificar y los motivos de identificación tampoco.

## Ejemplo

```json
{
  "eventId": "8a0b6d3e-2f1c-4e5d-9a7b-6c5d4e3f2a1b",
  "type": "PatientDocumentChanged",
  "schemaVersion": 1,
  "occurredAt": "2026-09-13T18:46:02.114Z",
  "patientUuid": "034820b2-cffa-4360-b384-09658f31bf9c",
  "patientVersion": 4,
  "traceId": "6aa6ef7b71504a127e8dabde5040192a",
  "data": {
    "patient": {
      "uuid": "034820b2-cffa-4360-b384-09658f31bf9c",
      "document": { "type": "CEDULA_DE_CIUDADANIA", "number": "1098765432" },
      "firstNames": "Ana María",
      "lastNames": "Restrepo Gómez",
      "birthDate": "2008-04-12",
      "sex": "FEMALE",
      "status": "ACTIVE",
      "affiliation": { "regime": "CONTRIBUTORY", "healthProviderNit": "900123456-7" }
    },
    "previousDocument": { "type": "TARJETA_DE_IDENTIDAD", "number": "1005123456" }
  }
}
```

## Reglas para consumidores

1. **Guarda `data.patient` completo y reemplaza lo anterior.** Cada evento trae el estado actual; no
   hace falta reconstruir cambios.
2. **Descarta eventos viejos o repetidos.** La entrega es *al menos una vez*: tras un reinicio de
   Connect puede llegar un evento dos veces. Aplica el evento solo si `patientVersion` es mayor que la
   versión guardada.
3. **Para cargar el registro completo**, lee el topic desde el inicio (`auto.offset.reset=earliest`)
   con un grupo nuevo: la compactación deja el último estado de cada paciente.
4. **Si llega `PatientDocumentChanged`** y indexas por documento, usa `previousDocument` para
   actualizar tu índice.
5. **Pacientes sin identificar.** Sus eventos usan su UUID provisional como clave y en `patientUuid`.
   Al recibir `UnidentifiedPatientIdentified`, asocia todo lo que registraste con el UUID provisional
   al paciente de `identifiedPatientUuid`. Si llega `UnidentifiedPatientIdentificationReverted`,
   deshaz esa asociación con `previousPatientUuid`.
6. **No uses los eventos para decidir permisos** ni muestres su contenido a usuarios sin autorización:
   contienen datos personales.

## Evolución del contrato

- Agregar campos opcionales o tipos nuevos no rompe consumidores que ignoran lo desconocido.
- Un cambio incompatible (quitar o renombrar campos) exige `schemaVersion: 2` en un topic nuevo
  `patient.events.v2`, publicando ambos durante la migración.
- `PatientEventContractTest` y `PatientEventsIT` validan cada evento contra el esquema.
