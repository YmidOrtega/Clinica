# Eventos de practitioners-service

Un solo topic: `practitioners.v1`, **compactado** por el uuid del profesional. Cada mensaje trae el
**estado completo** después del cambio, para que quien lo consuma no tenga que reconstruir nada ni llamar
al servicio: con el último mensaje de cada clave tiene el directorio entero.

| Qué | Cómo |
|---|---|
| Clave | uuid del profesional |
| Valor | estado completo, según [practitioners.v1.schema.json](practitioners.v1.schema.json) |
| Cabecera `eventType` | el tipo del cambio (`PractitionerRegistered`, `PractitionerSuspended`, …) |
| Limpieza | compactado, con `min.compaction.lag.ms` de una hora |

## Lo que no viaja

Los **honorarios no salen en el evento**. Son de talento humano y solo se leen con el permiso
`practitioners:manage-fees`; ponerlos en un topic compactado los repartiría a todo el que lo consuma.

## Cómo llega

El servicio escribe en `practitioners_outbox.outbox_events` **dentro de la misma transacción** del cambio
(`Propagation.MANDATORY`: si no hay transacción, falla en vez de publicar un evento huérfano). Debezium lee
esa tabla con el conector [practitioners-outbox.json](../debezium/practitioners-outbox.json) y enruta por
`aggregatetype`. Las filas publicadas se purgan a los 7 días; el topic es la fuente, no la tabla.

## Quién lo consume

- `clinical-history-service`: el registro profesional y la especialidad de quien firma, para la copia de la
  historia clínica.
- Cuando entren al refactor, `admissions` y `billing` reemplazarán con esto su consulta del médico.
