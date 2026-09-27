# El asistente de revisión de facturas

`ai-assistant-service` ayuda a facturación y a cartera a controlar las facturas ya emitidas: detecta con
reglas las que tienen problemas, explica cada caso con un modelo de lenguaje **local** y deja propuestas de
acción que solo ocurren si el usuario las confirma. No admite pacientes ni factura: lee lo que publica
billing y actúa sobre billing con los permisos del propio usuario.

```
billing.invoices.v1 ──► proyección (assistant.invoices) ──► reglas ──► bandeja de hallazgos
billing.filing-deadlines.v1 ─┘                                               │
billing.claim-objections.v1 ─┘                                               ▼
                               usuario ──► conversación ──► modelo local (LM Studio) ──► herramientas
                                                                  │                        │ lectura: proyección + API de billing
                                                                  ▼                        │ con el token del usuario
                                                         acción propuesta ──(confirmación del usuario)──► billing
```

## Por qué un modelo local

Las preguntas y las respuestas de las herramientas llevan datos de facturación y referencias a pacientes.
El modelo corre en la máquina de la clínica (LM Studio, API compatible con OpenAI), así que nada sale a un
proveedor externo. Cambiar de runtime (Ollama, vLLM) es solo configuración: `AI_ASSISTANT_LLM_BASE_URL` y
`AI_ASSISTANT_LLM_MODEL`.

## Arquitectura por capas

A diferencia de los servicios hexagonales del proyecto, este es por capas, igual que practitioners-service:

| Capa | Paquete | Qué hace |
|---|---|---|
| Entrada HTTP | `web` | controladores, DTO de la frontera, `Access`, usuario actual |
| Entrada Kafka | `messaging` | lee los tres topics de billing y los traduce a comandos del servicio |
| Servicio | `service` | reglas, conversación, herramientas del modelo, propuestas y su ejecución |
| Persistencia | `repository` | entidades JPA y repositorios; la entidad no sale del servicio |
| Clientes | `client` | Feign hacia billing con circuit breaker |
| Común | `shared` | excepciones, catálogos (`FindingRule`, `ActionKind`) y validación |

`ArchitectureTest` vigila que las dependencias solo bajen: `web` y `messaging` hablan con `service`, que
habla con `repository` y `client`; ni `web` ni `messaging` tocan JPA, y las entidades no tienen setters
públicos.

## La copia local de las facturas

`billing.invoices.v1` es compactado y trae el estado completo de cada factura, así que el asistente la
reconstruye sin llamar a billing. Guarda en `assistant.invoices` las columnas que filtra (número, pagador,
DIAN, CUV, radicado, saldo, contrato, copago faltante) y el evento entero en `state` (JSONB) para las notas
crédito y las glosas. Gana siempre el evento más reciente (`occurredAt`): uno viejo que llegue tarde no
retrocede la copia. Un evento ilegible va a `billing.invoices.v1.assistant.dlt` sin frenar a los demás.

## Reglas y bandeja de hallazgos

Los hallazgos no dependen del modelo: los calculan reglas deterministas y auditables en cada cambio de la
factura, más un barrido cada `AI_ASSISTANT_REVIEW_SWEEP_DELAY` (30 min) para las que dependen del tiempo.

| Regla | Gravedad | Cuándo | Se cierra cuando |
|---|---|---|---|
| `DIAN_REJECTED` | alta | la DIAN rechazó la factura | la DIAN la acepta o se anula |
| `DIAN_UNCONFIRMED` | media | lleva más de 2 h sin firma o sin respuesta de la DIAN | la DIAN responde |
| `CREDIT_NOTE_REJECTED` | alta | la DIAN rechazó una nota crédito (un hallazgo por nota) | la nota se acepta |
| `RIPS_PENDING` | media | aceptada por la DIAN hace más de 24 h y sin CUV del Ministerio | llega el CUV |
| `COPAY_SHORTFALL` | media | al paciente le faltó facturar copago o cuota | se completa |
| `BILLED_WITHOUT_CONTRACT` | baja | se facturó al pagador sin contrato (caso del DT2) | se anula |
| `FILING_DUE_SOON` / `FILING_OVERDUE` | media / alta | alerta de billing sobre el plazo de radicación | se radica |
| `OBJECTION_DUE_SOON` / `OBJECTION_OVERDUE` | media / alta | alerta de billing sobre una glosa sin responder | se responde |

Los plazos en días hábiles los calcula billing y los publica en `billing.filing-deadlines.v1` y
`billing.claim-objections.v1`; el asistente no repite el calendario. Una alerta vencida reemplaza a la de
"por vencer" de la misma factura o glosa.

`GET /api/v1/assistant/findings` es la bandeja (alta gravedad primero, luego la fecha límite más cercana),
`/findings/summary` cuenta por regla y `/invoices/{número}/findings` da el historial de una factura.

## Conversaciones y herramientas

`POST /api/v1/assistant/conversations` abre una conversación y `POST /conversations/{uuid}/messages` le
pregunta al modelo. Cada usuario ve solo las suyas (la de otro responde 404). El modelo recibe el prompt de
sistema (`prompts/system.st`), los últimos 12 mensajes y estas herramientas:

| Herramienta | Fuente |
|---|---|
| `openFindings`, `invoiceStatus`, `searchInvoices` | copia local y bandeja |
| `invoiceDetail`, `dianVerdicts`, `ripsValidations`, `filing`, `objections`, `creditNotes` | API de billing con el token del usuario |
| `proposeAction`, `proposeFiling`, `proposeObjectionAnswer` | crean una propuesta; no tocan billing |

Las consultas a billing usan el token del usuario intercambiado por uno con audiencia `billing-service`
(cliente OAuth `ai-assistant-service`, clave de aserción en OpenBao transit): si el usuario no tiene
permiso en billing, la herramienta lo dice y el modelo también. Cada resultado se recorta a 6.000
caracteres.

El prompt ordena tratar lo que devuelven las herramientas como datos y nunca como instrucciones: los textos
de la DIAN o el detalle de una glosa los escriben terceros. Aunque el modelo se desviara, no puede escribir
en billing: solo puede proponer.

Si el modelo no responde en `AI_ASSISTANT_LLM_TIMEOUT` (25 s, por debajo de los 30 s del gateway) o falla,
la API responde `503 ASSISTANT_MODEL_UNAVAILABLE` y la pregunta no queda guardada.

## Acciones propuestas

| Acción | En billing | Exige |
|---|---|---|
| `SIGN` | `POST /invoices/{uuid}/signature` | `billing:invoice` |
| `SEND_TO_DIAN` | `POST /invoices/{uuid}/dian-delivery` | `billing:invoice` |
| `VALIDATE_RIPS` | `POST /invoices/{uuid}/rips-validation` | `billing:file` |
| `FILE` | `POST /invoices/{uuid}/filing` con número y fecha del radicado | `billing:file` |
| `ANSWER_OBJECTION` | `POST /objections/{uuid}/response` con los códigos RE | `billing:glosses`, segundo factor reciente y `billing:void` si acepta valor |

La propuesta queda en `assistant.proposed_actions` con sus datos, dura 30 minutos y la respuesta de la
pregunta la devuelve en `proposedActions`. El usuario la revisa y la confirma con
`POST /api/v1/assistant/actions/{uuid}/confirmation` e `If-Match`, o la descarta con `/discard`. Al
confirmar pasa a `EXECUTING` antes de llamar a billing, así que dos confirmaciones simultáneas no la
ejecutan dos veces; termina en `DONE` o `FAILED` con el motivo que dio billing.

Para responder una glosa el asistente guarda el ETag de la glosa al proponer y lo envía como `If-Match`: si
la glosa cambió, billing responde 412 y la acción falla sin responder sobre datos viejos. El segundo factor
lo exige el propio asistente al confirmar; el token intercambiado conserva `auth_time` y `acr`, así que
billing lo acepta igual.

## Datos y retención

PostgreSQL 16 en su red interna (`assistant-data`), con credenciales en OpenBao:

- esquema `assistant` para las tablas y `assistant_migrations` para el historial de Flyway, que la
  aplicación no ve;
- `ai_assistant_migrator` es dueño del esquema; `ai_assistant_app` solo lee, inserta y actualiza, sin
  `DELETE` ni DDL.

Las conversaciones se borran a los 30 días sin actividad (`AI_ASSISTANT_CONVERSATION_RETENTION`, cada día a
las 3:30) mediante `assistant.purge_conversations`, una función `SECURITY DEFINER` del migrator que es lo
único que la aplicación puede ejecutar para borrar. Las acciones propuestas no se borran: quedan como
auditoría de quién confirmó qué, sobre qué factura, con qué datos y con qué resultado.

## Configuración

| Variable | Por defecto | Qué es |
|---|---|---|
| `AI_ASSISTANT_LLM_BASE_URL` | `http://localhost:1234` (`http://host.docker.internal:1234` en compose) | API compatible con OpenAI del modelo local |
| `AI_ASSISTANT_LLM_MODEL` | `qwen3-8b` | modelo cargado en LM Studio; debe soportar tool calling |
| `AI_ASSISTANT_LLM_TIMEOUT` | `25s` | espera máxima por respuesta del modelo |
| `AI_ASSISTANT_REVIEW_DIAN_GRACE` / `_RIPS_GRACE` | `PT2H` / `PT24H` | cuándo se marca una factura sin respuesta DIAN o sin CUV |
| `AI_ASSISTANT_REVIEW_SWEEP_DELAY` | `PT30M` | barrido de las reglas que dependen del tiempo |
| `AI_ASSISTANT_ACTION_LIFETIME` | `PT30M` | vigencia de una propuesta |
| `AI_ASSISTANT_CONVERSATION_RETENTION` | `P30D` | cuánto se guarda una conversación sin actividad |

Con `docker-compose.debug.yml` el servicio habla con `llm-simulator` (WireMock), que simula el modelo con
llamadas a herramientas; para usar LM Studio real se define `AI_ASSISTANT_LLM_BASE_URL`.

## Pruebas

- Unitarias: reglas de revisión, propuestas (vencimiento, una sola ejecución) y arquitectura.
- Integración (Testcontainers PostgreSQL y Kafka, WireMock para billing y el modelo): permisos de la base,
  consumo de los topics con contratos validados contra los esquemas de billing, bandeja, herramientas,
  conversación con llamadas a herramientas y caídas del modelo, propuestas y confirmación de cada acción.
- `platform/e2e/assistant-e2e.sh`: sobre el stack completo, toma la factura de `billing-e2e.sh`, comprueba
  que llegó a la copia local y que el modelo la consulta, y confirma una acción que llega a billing con el
  token intercambiado del usuario.
