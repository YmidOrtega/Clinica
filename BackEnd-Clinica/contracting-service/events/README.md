# Eventos de contracting-service

Los eventos se escriben en `contracting_outbox.outbox_events` **dentro de la misma transacción** que el
cambio, y Debezium los publica en Kafka. El servicio no habla con Kafka.

| Topic | Política | Clave | Qué lleva |
|---|---|---|---|
| `contracting.contracts.v1` | compactado | uuid del contrato | el estado completo del contrato tras cada cambio: pagador, vigencia, modalidad, estado, términos tarifarios, excepciones vigentes, paquetes vigentes, servicios que exigen autorización previa y el acuerdo de capitación o presupuesto |
| `contracting.tariffs.v1` | sin compactar, retención indefinida | uuid del manual | cada publicación o retiro de una versión de manual, con su unidad, el valor de la unidad, la cantidad de tarifas y la huella del contenido |

El contrato va **compactado con estado completo** para que un consumidor que se conecte hoy reconstruya la
foto actual leyendo el topic desde el principio, sin llamar a la API. Las tarifas van como hechos porque
interesa la historia de qué manual estuvo vigente y cuándo; los ítems del manual no viajan en el evento
(son miles), se consultan por la API cuando hacen falta.

Los esquemas de `*.schema.json` son el contrato con los consumidores: `additionalProperties: false` y los
campos opcionales viajan ausentes, no en `null` (Debezium omite los nulos al expandir el JSON del payload).

Consumidor previsto: `billing-service`, para su copia local de contratos y tarifas, y el futuro
`settlement-service` para la liquidación de capitación y PGP.
