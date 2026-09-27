# Eventos de billing-service

Los eventos se escriben en `billing_outbox.outbox_events` **dentro de la misma transacción** que los
origina, y Debezium los publica en Kafka. El servicio no habla con Kafka para publicar.

| Topic | Política | Clave | Qué lleva |
|---|---|---|---|
| `billing.claim-objections.v1` | sin compactar, retención de 30 días | uuid de la devolución o glosa | la alerta de una devolución o glosa del pagador sin respuesta cuando entra en `DUE_SOON` (quedan los días hábiles de `clinica.billing.objections.warning-business-days`, 3 por defecto) o en `OVERDUE` (pasaron los 5 días hábiles de una devolución o los 15 de una glosa y se entiende aceptada tácitamente) |
| `billing.filing-deadlines.v1` | sin compactar, retención de 30 días | uuid de la factura | la alerta de una factura de servicios al pagador sin radicar cuando entra en `DUE_SOON` (quedan los días hábiles configurados en `clinica.billing.filing.warning-business-days`, 5 por defecto) o en `OVERDUE` (pasaron los 22 días hábiles desde la expedición) |

Dos tareas diarias revisan sus bandejas y publican **una sola vez** por elemento y estado:
`clinica.billing.filing.alerts.cron` (6:00 hora de Bogotá) para la radicación y
`clinica.billing.objections.alerts.cron` (6:05) para las respuestas a devoluciones y glosas.
`filing_alerts` y `objection_alerts` recuerdan lo ya alertado, así que correrlas varias veces o en varias
réplicas no duplica eventos. Una factura radicada o una glosa respondida deja de alertar.

Los días hábiles excluyen sábados, domingos y festivos nacionales (Ley 51 de 1983, incluidos los que
dependen de la Pascua).

El esquema de `*.schema.json` es el contrato con los consumidores: `additionalProperties: false` y los
campos opcionales viajan ausentes, no en `null`.

Consumidor previsto: el servicio de notificaciones, para avisar al equipo de facturación y de cartera.
