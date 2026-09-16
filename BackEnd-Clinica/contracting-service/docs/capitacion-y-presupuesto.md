# Capitación y presupuesto global

## Qué vive aquí y qué no

Este servicio guarda **lo que se pactó**: el valor per cápita o el techo presupuestal, su periodicidad, la
nota técnica y la población asignada a cada contrato.

La **liquidación** —comparar la ejecución del periodo contra el presupuesto, calcular el pago, conciliar y
glosar— no se hace aquí. Es de un servicio futuro, `settlement-service`, que consumirá los eventos de este
servicio y los de las atenciones. Mientras no exista, el ciclo de capitación y PGP queda abierto: se puede
registrar el acuerdo y la población, pero nadie liquida el periodo.

La factura por evento y sus glosas siguen siendo de `billing-service`.

## Acuerdos

| Modalidad del contrato | Endpoint | Qué guarda |
|---|---|---|
| `CAPITATION` | `POST /api/v1/contracts/{uuid}/capitation-agreement` | valor per cápita, periodicidad, nota técnica |
| `GLOBAL_BUDGET` | `POST /api/v1/contracts/{uuid}/budget-agreement` | techo presupuestal, periodicidad, nota técnica |

Los acuerdos son append-only: registrar uno nuevo revoca el anterior desde la misma fecha, y la lista
completa queda en `GET /api/v1/contracts/{uuid}/funding-agreements`. Registrar o revocar exige segundo
factor verificado en los últimos cinco minutos, igual que cualquier otra decisión de precio.

## Población capitada

`POST /api/v1/contracts/{uuid}/capitated-members/imports?period=YYYY-MM` carga el archivo del periodo. Es
idempotente por documento dentro del periodo: crea los que faltan, corrige el nombre de los que cambiaron
y deja intactos los iguales.

Cada afiliado se contrasta contra `patient-service` por tipo y número de documento:

| Estado | Significado |
|---|---|
| `MATCHED` | hay un paciente registrado con ese documento; se guarda su UUID |
| `UNMATCHED` | no existe todavía como paciente de la clínica |
| `UNVERIFIED` | `patient-service` no respondió; el afiliado queda cargado y pendiente de contrastar |

**Si el registro de pacientes está caído, la carga no falla**: los afiliados entran como `UNVERIFIED` y
`POST /capitated-members/verification?period=YYYY-MM` reintenta después. Las respuestas conocidas se
cachean 24 horas y el circuito se abre tras una racha de fallos, para no castigar cada fila del archivo.

## Cobertura

`GET /api/v1/capitated-members/coverage?documentType=CC&documentNumber=...&on=2026-03-15` responde con los
contratos que tienen a esa persona asignada en el periodo de esa fecha. Devuelve una lista porque una
persona puede estar capitada en más de un contrato, y recepción necesita verlos todos.
