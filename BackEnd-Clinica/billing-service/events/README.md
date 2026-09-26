# Eventos de billing-service

Los eventos se escriben en `billing_outbox.outbox_events` **dentro de la misma transacción** que los
origina, y Debezium los publica en Kafka. El servicio no habla con Kafka para publicar.

| Topic | Política | Clave | Qué lleva |
|---|---|---|---|
| `billing.filing-deadlines.v1` | sin compactar, retención de 30 días | uuid de la factura | la alerta de una factura de servicios al pagador sin radicar cuando entra en `DUE_SOON` (quedan los días hábiles configurados en `clinica.billing.filing.warning-business-days`, 5 por defecto) o en `OVERDUE` (pasaron los 22 días hábiles desde la expedición) |

Una tarea diaria (`clinica.billing.filing.alerts.cron`, 6:00 hora de Bogotá) revisa la bandeja de
radicación y publica **una sola vez** por factura y estado; `filing_alerts` recuerda lo ya alertado, así
que correrla varias veces o en varias réplicas no duplica eventos. Una factura radicada deja de alertar.

Los días hábiles excluyen sábados, domingos y festivos nacionales (Ley 51 de 1983, incluidos los que
dependen de la Pascua).

El esquema de `*.schema.json` es el contrato con los consumidores: `additionalProperties: false` y los
campos opcionales viajan ausentes, no en `null`.

Consumidor previsto: el servicio de notificaciones, para avisar al equipo de facturación.
