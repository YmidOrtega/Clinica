# Prueba de carga — auth-service

Mide si `auth-service` soporta el cambio de turno de una clínica con **1.500 personas del personal**,
todas con segundo factor, mientras las sesiones abiertas se renuevan y `clinical-history-service`
intercambia tokens para consultar `patient-service`.

## Modelo de carga

| Operación | Escenario | x1 | Qué recorre |
|---|---|---|---|
| Login completo | `shiftChange` | 80 por minuto | `/oauth2/authorize` sin sesión, `GET /api/v1/session`, contraseña (Argon2id), código TOTP verificado en OpenBao, `continueUrl` y canje del código con `private_key_jwt` firmado en transit |
| Refresh | `sustainedRefresh` | 300 sesiones, una renovación cada 30 s | rotación del refresh token con su familia y nuevo access token firmado en transit |
| Token exchange | `tokenExchange` | 50 por segundo | `client_credentials` de `clinical-history-service` (cacheado 25 min) como `actor_token` y el access token de una persona como `subject_token`, hacia `aud: patient-service` |

- El cambio de turno concentra en 5 minutos lo que en la realidad se reparte en 15 a 20: 80 logins por
  minuto es todo un turno de 400 personas entrando a la vez.
- Una sesión real renueva cada 4 a 5 minutos; renovar cada 30 s multiplica por 10 la presión de 300
  sesiones. Las sesiones empiezan escalonadas en los primeros 30 s.
- 50 intercambios por segundo equivalen a más de 3.000 lecturas de pacientes por minuto desde la
  historia clínica, muy por encima del pico medido en `clinical-history-service/load-test`.
- `setup()` abre antes, sin medir, una sesión por cada usuario virtual de refresh e intercambio, para
  que ningún refresh token se comparta entre usuarios virtuales.
- Cada login usa una persona distinta con su propio secreto TOTP, y k6 calcula el código.

## Resultados

Ejecución del 2026-09-15 en una máquina de 16 núcleos y 61 GB de RAM, con dos réplicas de
`auth-service` (pool de 15 conexiones cada una), `auth-db`, el clúster OpenBao de tres nodos, Kafka y
k6 en contenedores sin límites de recursos. Cada perfil: 5 minutos sostenidos.

| Métrica | x1 p95 | x1 p99 | x2 p95 | x2 p99 |
|---|---|---|---|---|
| Login completo (6 peticiones) | 303 ms | 528 ms | 617 ms | 1,81 s |
| `POST /api/v1/login` | 69 ms | 89 ms | 86 ms | 211 ms |
| `POST /api/v1/login/second-factor` | 54 ms | 113 ms | 85 ms | 256 ms |
| Canje del código | 54 ms | 76 ms | 101 ms | 302 ms |
| Refresh | 60 ms | 91 ms | 176 ms | 636 ms |
| Token exchange | 47 ms | 73 ms | 97 ms | 394 ms |

- x1: 42.151 peticiones (109 req/s), 21.451 comprobaciones, 0 fallos.
- x2: 84.281 peticiones (185 req/s), 42.891 comprobaciones, 0 fallos.
- Umbrales cumplidos en ambos perfiles: p95 < 800 ms la contraseña, < 500 ms el segundo factor y el
  canje, < 2,5 s el login completo, < 300 ms refresh e intercambio, y ningún fallo.

## Conclusiones

1. **El primer intento no pasó y encontró un defecto real.** Leer una autorización consultaba sus
   tokens desde dentro del mapeo de la fila, así que cada petición tomaba una segunda conexión con la
   primera abierta. Con 15 conexiones y cientos de hilos virtuales el pool quedó bloqueado: 36 % de
   refresh aceptados y login p95 de 10 s. Con las dos lecturas separadas, `JdbcAuthorizationStoreIT`
   corre con un pool de una conexión para que el defecto no vuelva. En la misma corrección las 300
   sesiones de refresh dejaron de arrancar en el mismo instante, que no representa un turno real.
2. **OpenBao no es el cuello de botella.** Cada login firma en transit y valida el TOTP, cada refresh e
   intercambio firma otra vez, y aun a x2 las operaciones de un solo paso quedan por debajo de 100 ms en
   p95.
3. **Lo que crece primero es la cola del refresh.** Rotar el refresh token bloquea la fila de la
   autorización y reescribe sus tokens; a x2 su p99 sube a 636 ms. Si hiciera falta más, el primer
   ajuste es subir `AUTH_DB_POOL_SIZE`, antes que añadir réplicas.
4. **Argon2id no domina el login**: la contraseña se verifica en menos de 90 ms en p95 a x2.

## Cómo ejecutarla

Requisitos: el stack de `docker-compose.debug.yml` con `auth-service`, `mailpit` y OpenBao, Node.js, `jq`,
`curl` y `openssl`. La prueba firma las aserciones de los clientes con el token root de OpenBao del stack
local; no se ejecuta contra un entorno real.

```bash
cd BackEnd-Clinica

# 1. Personal sintético: 1.500 usuarios activos con TOTP en auth-db y sus claves en OpenBao.
#    Borra y recrea los usuarios carga*@clinica.load; los secretos quedan en staff.csv (ignorado por git).
COMPOSE_PROJECT=<proyecto> sh auth-service/load-test/seed-staff.sh

# 2. Carga (MULTIPLIER=2 para x2, DURATION para acortarla)
ROOT=$(docker run --rm --user root -v <proyecto>_openbao_bootstrap:/b:ro --entrypoint jq clinica/openbao-tools:2.6.2 -r .root_token /b/init.json)
docker create --name auth-k6 --network <proyecto>_clinica-net -e OPENBAO_TOKEN="$ROOT" -e MULTIPLIER=1 \
  -e STAFF_FILE=/scripts/staff.csv -v "$PWD/auth-service/load-test:/scripts:ro" grafana/k6 run --quiet /scripts/auth-load.js
docker network connect <proyecto>_secrets-net auth-k6
docker start -a auth-k6; docker rm auth-k6
```

`seed-staff.sh` restablece la contraseña del `SUPER_ADMIN` a la de la prueba para copiar su hash
Argon2id con el enlace de reseteo que llega a Mailpit, igual que las pruebas E2E. Si el contenedor
de k6 no puede leer `staff.csv`, genérelo con `STAFF_FILE` en un directorio legible por otros usuarios y
móntelo en `/data`.
