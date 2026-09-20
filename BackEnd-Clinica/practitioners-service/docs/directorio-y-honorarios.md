# Directorio profesional y honorarios

## Por qué este servicio existe aparte de auth

`auth-service` sabe quién entra al sistema; este servicio sabe **quién es el profesional**: su registro
ante el ReTHUS, sus especialidades y lo que se le paga. Son dos preguntas distintas y tienen dueños
distintos: hay profesionales sin cuenta (un especialista externo que solo firma interconsultas) y cuentas
que no son de profesionales (recepción, facturación).

El vínculo entre ambos mundos es **opcional y explícito**: talento humano registra al profesional y, si
tiene cuenta, la vincula indicando su UUID. Ese vínculo es lo que permite que la copia de la historia
clínica diga "Ana María Restrepo Gómez (DOCTOR) · RM 12345 · Cardiología" en vez de un UUID.

## Cómo se verifica la cuenta

Sin llamar a `auth-service`. El servicio ya recibe `auth.users.v1` —el topic compactado que
`clinica-commons-security` lee para revocar tokens— y contra esa copia local comprueba que la cuenta
existe. De ahí salen tres reglas:

| Situación | Respuesta |
|---|---|
| La cuenta existe en la copia | Se vincula |
| La copia no conoce esa cuenta | `422 AUTH_USER_NOT_FOUND` |
| La copia todavía no está al día | `503 ACCOUNT_DIRECTORY_UNAVAILABLE`, nunca se vincula a ciegas |

El estado de la cuenta (`ACTIVE`, `INACTIVE`, `UNKNOWN`) se **proyecta al leer**, no se guarda: cuando auth
suspende a alguien, el directorio lo refleja sin reescribir ninguna fila. Y una cuenta suspendida **no**
impide que el profesional atienda: eso lo decide su propio estado (`ACTIVE`, `SUSPENDED`, `RETIRED`).

Al soltar la cuenta, la historia clínica **conserva** lo que ya sabía del profesional y solo marca que ya
no hay cuenta vinculada: una nota firmada hace dos años debe seguir mostrando el registro de quien la
firmó.

## Honorarios

Un acuerdo por vez, y nunca se reescribe:

- **Por hora** o **por turno**: un valor.
- **Por procedimiento**: una línea por código de servicio, con su valor.

Pactar un acuerdo nuevo **revoca el vigente desde la fecha nueva**, que debe ser posterior a la del
anterior. Así, años después, se puede responder qué honorario regía el día que se prestó un servicio.

Pactarlos exige **segundo factor reciente**, y tanto escribirlos como leerlos exige
`practitioners:manage-fees`: contratación y recepción no ven lo que gana un profesional. Por lo mismo,
**los honorarios no viajan en `practitioners.v1`**.

## Lo que este servicio no hace

- **Agenda**: horarios, ausencias y disponibilidad son de un `scheduling-service` futuro. Al refactorizar
  se borraron del legacy porque nadie los consumía.
- **Liquidar**: pagar esos honorarios es de cuentas por pagar, no de aquí.
- **Proveedores institucionales**: laboratorios externos, ambulancias e insumos quedan para otro servicio;
  por eso el nombre dejó de ser `suppliers`.
