# Prueba de extremo a extremo — pacientes + historia clínica

`clinical-e2e.sh` levanta el camino completo contra el stack real: `patient-service` registra un
paciente, el evento viaja por Debezium y Kafka hasta la copia local de `clinical-history-service`, y
allí se abre una atención, se firma un triage con anexo, se firma la epicrisis con diagnóstico
CIE-10, se cierra la atención, se emite la copia en PDF y se comprueba la auditoría. Al final apaga
`patient-service` para verificar el aislamiento de fallos.

## Qué verifica

| Paso                            | Qué demuestra                                                        |
| ------------------------------- | -------------------------------------------------------------------- |
| Registro y propagación          | `patient.events.v1` llega a `patient_references` sin llamadas síncronas |
| Triage con anexo                | Cifrado del archivo, archivo WORM al firmar y descarga verificada por SHA-256 |
| Epicrisis                       | Exige diagnóstico principal; sin catálogo CIE-10 activo responde `422` |
| Listas y signos vitales         | Los `updates` del borrador se aplican al firmar                       |
| Integridad                      | La cadena del paciente verifica de punta a punta                      |
| Acceso                          | Sin relación de cuidado responde `403` y el rechazo llega a `clinical.access-audit.v1` |
| Copia en PDF                    | `MEDICAL_RECORDS` obtiene el PDF sellado con su motivo                |
| Eventos                         | Los hechos sellados aparecen en `clinical.encounters.v1`              |
| Aislamiento                     | Con `patient-service` caído, un paciente conocido sigue atendiéndose y uno desconocido responde `503` |

## Cómo ejecutarla

Requisitos: Docker, Node.js, `jq`, `curl` y `openssl`.

```bash
cd BackEnd-Clinica

# 1. Stack: OpenBao genera las credenciales y las claves de transit; solo hacen falta los nombres de las bases
export PATIENT_DB_NAME=patient_db CLINICAL_DB_NAME=clinical_db
docker compose -p clinical-e2e -f docker-compose.yml -f docker-compose.debug.yml \
  up -d --build auth-service patient-service clinical-history-service kafka-connect-init

# 2. Pruebas
COMPOSE_PROJECT=clinical-e2e sh platform/e2e/clinical-e2e.sh
COMPOSE_PROJECT=clinical-e2e sh platform/e2e/openbao-e2e.sh
```

Estas dos pruebas no pasan por el login: `staff-token.sh ROL [UUID]` firma con el token root de
OpenBao, en `transit/auth-jwt`, un access token con los mismos claims que emite `auth-service`
(`aud: clinica-api`, `auth_time` actual y `amr` con `mfa`). Los servicios lo validan con el JWKS real.
Guarda cada token 4 minutos en `$E2E_TOKEN_CACHE`; `E2E_TOKEN_REFRESH=1` fuerza uno nuevo y
`E2E_TOKEN_TTL` cambia su vigencia (300 s). Un token así no existe en `auth-service`, así que no sirve
para el intercambio: el flujo real con tokens de auth lo recorre `auth-e2e.sh`.

## Prueba de OpenBao

`openbao-e2e.sh` corre sobre el mismo stack y detiene nodos a propósito:

| Paso                         | Qué demuestra                                                            |
| ---------------------------- | ------------------------------------------------------------------------ |
| Clúster                      | Tres nodos desellados, raft con tres votantes y un nodo activo           |
| Políticas                    | Cada AppRole (patient, clinical, auth) lee solo sus rutas; el agente no escribe; solo `clinical-history-service` firma con `transit/clinical-seal` y no puede rotar sus claves; `auth-service` administra solo claves TOTP `staff-*` y no puede listarlas |
| Configuración de contenedores | Ninguna contraseña ni clave aparece en `docker inspect`                 |
| Conmutación                  | Cae el nodo activo, otro asume, los logins siguen y una réplica de `patient-service` arranca; el nodo vuelve desellado solo |
| Aislamiento                  | Con los tres nodos caídos `patient-service` sigue registrando pacientes, `clinical-history-service` verifica una cadena ya sellada y responde `503 CLINICAL_KEYS_UNAVAILABLE` al sellar, y el clúster se recupera sin intervención |
| Auditoría                    | Los logins AppRole quedan en el registro de auditoría                    |

El script descubre los puertos publicados con `docker compose port`; si el stack corre en otra parte,
define `PATIENT_URL` y `CLINICAL_URL`. Para que la epicrisis se firme con diagnóstico hay que importar
y activar antes una versión del catálogo CIE-10 (`clinical-history-service/docs/catalogo-cie10.md`);
sin catálogo el script toma la rama alterna y comprueba que la epicrisis se rechaza.

Las claves de cifrado y sello de la historia clínica viven en transit dentro del clúster OpenBao del
stack: `docker compose down -v` las destruye junto con los datos que protegen
(`clinical-history-service/docs/claves-y-cifrado.md`).

## Prueba de auth-service

`auth-e2e.sh` necesita además `auth-service` y `mailpit` levantados con `docker-compose.debug.yml`:

```bash
docker compose -p clinical-e2e -f docker-compose.yml -f docker-compose.debug.yml up -d --build auth-service
COMPOSE_PROJECT=clinical-e2e sh platform/e2e/auth-e2e.sh
```

La primera corrida enrola el TOTP del `SUPER_ADMIN` y guarda la URL `otpauth` (con permisos `600`) en
`${E2E_STATE_DIR:-~/.local/state/clinica-e2e}/<proyecto>-super-admin-totp.json` para calcular los códigos
de las siguientes con `totp-code.mjs`. Si ese archivo se pierde, recrear el volumen de `auth-db`.

| Paso | Qué demuestra |
|---|---|
| Primer `SUPER_ADMIN` | Llega el correo de activación a Mailpit y el enlace activa la cuenta; si ya estaba activa, el reseteo por correo funciona |
| Authorization code + PKCE | Sin sesión redirige al login del frontend; la contraseña sola no autentica la sesión |
| Segundo factor | El primer login enrola TOTP en OpenBao (QR y 10 códigos de recuperación); los siguientes validan el código; un código ya usado se rechaza en otra sesión; `continueUrl` termina en el `redirect_uri` con código y `state` |
| Cliente confidencial | El canje se autentica con una aserción `private_key_jwt` firmada en transit |
| Tokens | La firma ES256 verifica con el JWKS, que solo expone claves públicas; `amr` y `acr` reflejan el segundo factor; el refresh token solo existe como SHA-256 |
| Step-up | Una autorización con `max_age` vencido redirige a `?step=step-up`; tras el código TOTP el token trae un `auth_time` nuevo |
| API de usuarios | Con el access token real: `/api/v1/me`, invitación con correo en Mailpit, `428` sin `If-Match`, desactivación con la versión consultada e historial con el autor |
| Eventos | `auth.users.v1` trae el estado completo de la enfermera desactivada y se compacta; `auth.security-audit.v1` registra logins, fallos y reutilización de refresh tokens y no expira |
| Tokens de auth en patient y clinical | Si `patient-service` y `clinical-history-service` están publicados: una médica invitada activa su cuenta y enrola TOTP; `patient-service` acepta el token del `SUPER_ADMIN`; con Kafka Connect en pausa, clinical intercambia el token de la médica para consultar `patient-service` y abre la atención; al suspenderla, `patient-service` rechaza su token vigente en segundos |
| Refresh | Rota en cada uso y reutilizar uno rotado revoca toda la familia |
| Frenado | Tras 5 fallos desde la misma dirección responde `429` y, pasada la espera, vuelve a entrar |

