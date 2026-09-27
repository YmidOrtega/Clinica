# Roles y permisos

## Cómo se autoriza

El access token lleva un solo claim de rol, `role`. Los permisos no viajan en el token: cada servicio
los deriva del rol con el catálogo de `clinica-commons-security`, que convierte el rol en authorities
de Spring Security.

Un token de persona produce:

- `ROLE_<ROL>`, para las reglas que dependen del rol.
- un authority por permiso, con la forma `servicio:acción` (por ejemplo `contracting:manage-tariffs`).

Un token de servicio (`client_credentials`) nunca produce authorities de rol: solo sus `SCOPE_*`.

Los servicios nuevos autorizan por permiso:

```java
@PreAuthorize("hasAuthority('contracting:manage-contracts')")
```

Los servicios ya escritos (patient, clinical-history) siguen autorizando por rol y no cambian.

## Catálogo actual

| Rol | Para quién | Permisos |
|---|---|---|
| `SUPER_ADMIN` | administración del sistema | todos |
| `ADMIN` | administración operativa | todos |
| `CONTRACTING` | contratación | `contracting:read`, `manage-payers`, `manage-contracts`, `manage-tariffs`, `manage-capitation`, `quote-prices` |
| `BILLING` | facturación | `billing:read`, `sell`, `price-manually`, `invoice`, `void`, `collect`, `file`, `glosses`; `contracting:read`, `contracting:quote-prices`, `admissions:read`, `practitioners:read`, `practitioners:read-fees`; `assistant:use` |
| `ACCOUNTS_RECEIVABLE` | cartera | `billing:read`, `billing:file`, `billing:glosses`; `contracting:read`, `admissions:read`, `practitioners:read`; `assistant:use` |
| `HUMAN_RESOURCES` | talento humano | `practitioners:read`, `practitioners:manage`, `practitioners:manage-fees` |
| `RECEPTIONIST` | recepción | `admissions:read`, `admissions:admit`; `contracting:read`, `practitioners:read` |
| `DOCTOR` | médicos | `admissions:read`, `admissions:discharge` |
| `NURSE` | enfermería | `admissions:read`, `admissions:move-bed` |
| `MEDICAL_RECORDS` | archivo clínico | `admissions:read` |

`SUPER_ADMIN` recibe todo permiso nuevo automáticamente: es el usuario con el que corren los E2E y las
pruebas de carga.

## Agregar un rol o un permiso

Dos archivos, ninguno en los servicios que ya autorizan por permiso:

1. `auth-service` → `domain/user/Role.java`: el valor nuevo. El `switch` de `manageableBy` no compila
   hasta declarar quién lo administra, y `RoleTest` cubre el rol nuevo sin escribir un caso.
2. `clinica-commons-security` → `StaffPermission` (si el permiso es nuevo) y `StaffRole`: el conjunto
   de permisos del rol. El constructor del enum obliga a declararlo y `StaffAuthoritiesTest` verifica
   que llegue al token.

Después hay que publicar la versión de la librería y subirla en los servicios que la usen, y aceptar el
valor nuevo en el `CHECK` de la tabla `users` con una migración, porque la base repite la regla del
dominio.

`RECEPTIONIST` lee el directorio profesional para nombrar al médico tratante al admitir: admissions lo busca
en practitioners-service con el token del usuario cuando aún no le llegó por eventos. No ve honorarios.

`assistant:use` abre el asistente de revisión de facturas (ai-assistant-service). Lo que el asistente
consulta o ejecuta en billing sigue exigiendo los permisos de billing del usuario, porque lo hace con su
token intercambiado.

`HUMAN_RESOURCES` administra el directorio profesional y los honorarios, y nada más: no toca
contratación ni pacientes. Como cualquier rol operativo, no administra usuarios.

La configuración fiscal de billing (`billing:manage-config`: emisor, resoluciones de numeración, paso a
producción ante la DIAN, tarifas de estancia) es solo de la administración. `ACCOUNTS_RECEIVABLE` valida
el RIPS, radica ante el pagador y responde devoluciones y glosas, pero no vende, no factura ni emite
notas crédito: responder aceptando un valor emite una nota crédito y exige además `billing:void`, que
solo tiene `BILLING`.

## Lo que falta parametrizar

El catálogo cubre lo que contracting-service necesita hoy. Cada servicio que entre al refactor traerá
sus permisos, y hay casos ya identificados que no son un rol sino un permiso acotado:

- **admissions**: quién abre una atención, quién la corrige y quién la anula.
- **settlement** (servicio futuro): liquidar y conciliar capitación y PGP.
- **practitioners**: quién puede leer el directorio desde los servicios asistenciales. Hoy solo lo leen
  `HUMAN_RESOURCES` y la administración; cuando admissions y billing entren al refactor traerán su
  `practitioners:read`.

Estos permisos se agregan en el turno de cada servicio. No se definen antes: el catálogo solo crece con
permisos que alguien usa.
