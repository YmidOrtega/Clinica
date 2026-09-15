# OpenBao — secretos de la plataforma

Clúster OpenBao 2.6.2 de tres nodos que guarda las credenciales de `patient-service`,
`clinical-history-service` y su infraestructura. Ningún secreto de estos componentes va en `.env`, en
el código ni en la configuración de los contenedores.

## Piezas

| Contenedor          | Qué hace                                                                                   |
| ------------------- | ------------------------------------------------------------------------------------------ |
| `openbao-bootstrap` | Crea la clave de sello estático y una CA con el certificado TLS de los nodos. Idempotente; renueva el certificado 30 días antes de vencer |
| `openbao-1..3`      | Nodos raft con TLS 1.3 en `secrets-net` (red interna). El alias `openbao` apunta a los tres |
| `openbao-init`      | Inicializa el clúster, aplica las políticas de `policies/`, crea un AppRole por consumidor y siembra los secretos que falten. Se puede repetir sin cambiar nada |
| `openbao-agent`     | Se autentica con el AppRole `infra-agent` y renderiza archivos para MySQL, Kafka Connect y el almacenamiento S3 |

| Volumen                     | Contenido                                                           |
| --------------------------- | ------------------------------------------------------------------- |
| `openbao_seal`              | Clave del sello estático (solo la leen los nodos)                   |
| `openbao_tls`               | `ca.crt`, `server.crt`, `server.key`                                |
| `openbao_bootstrap`         | `init.json` (token root y claves de recuperación) y la clave de la CA |
| `openbao_{1,2,3}_data`      | Almacenamiento raft de cada nodo                                    |
| `openbao_{1,2,3}_audit`     | Registro de auditoría de cada nodo                                  |
| `openbao_approle_*`         | `role-id` y `secret-id` de cada consumidor                          |
| `*_secrets`                 | Archivos renderizados por el agente, uno por contenedor consumidor  |

## Rutas y políticas

| Ruta KV v2 (`secret/`)          | `patient-service` | `clinical-history-service` | `infra-agent` |
| ------------------------------- | :---------------: | :------------------------: | :-----------: |
| `patient/db/root`               |                   |                            | lectura       |
| `patient/db/migrator`, `app`    | lectura           |                            | lectura       |
| `patient/db/debezium`           |                   |                            | lectura       |
| `clinical/db/root`, `debezium`  |                   |                            | lectura       |
| `clinical/db/migrator`, `app`   |                   | lectura                    | lectura       |
| `clinical/storage/root`         |                   |                            | lectura       |
| `clinical/storage/attachments`  |                   | lectura                    | lectura       |
| `clinical/retired-master-keys`  |                   | lectura                    |               |
| `clinical/retired-seal-keys`    |                   | lectura                    |               |
| `auth/db/root`                  |                   |                            | lectura       |
| `auth/db/migrator`, `app`       |                   |                            | lectura       |
| `auth/db/debezium`              |                   |                            | lectura       |
| `auth/bootstrap`                |                   |                            |               |

`auth-service` tiene su propio AppRole: lee `auth/db/migrator`, `auth/db/app` y `auth/bootstrap`, firma con
`transit/auth-jwt`, lee la clave pública de `transit/api-gateway-client` y administra el segundo factor del
personal en el motor `totp/`: crea, reemplaza y borra claves `totp/keys/staff-*` y valida códigos en
`totp/code/staff-*`. No puede leer ni listar las claves, así que los secretos TOTP solo salen de OpenBao
una vez, en el QR del enrolamiento. El motor rechaza un código ya usado dentro de su ventana.

| Clave transit (`transit/`) | Tipo           | Uso                                               |
| -------------------------- | -------------- | ------------------------------------------------- |
| `clinical-kek`             | `aes256-gcm96` | `clinical-history-service` cifra, descifra y lee versiones |
| `clinical-seal`            | `ecdsa-p256`   | `clinical-history-service` firma y lee versiones y claves públicas |
| `auth-jwt`                 | `ecdsa-p256`   | `auth-service` firma los tokens ES256; rota cada 30 días |
| `api-gateway-client`       | `ecdsa-p256`   | el gateway firma sus aserciones `private_key_jwt`; `auth-service` solo lee la pública |

Las claves de transit se crean no exportables y ningún consumidor puede rotarlas ni borrarlas. Su uso
y rotación están en `clinical-history-service/docs/claves-y-cifrado.md`.

Los servicios Spring usan Spring Cloud Vault con el perfil `openbao`: leen `role-id` y `secret-id` de
`/run/secrets/openbao/approle`, confían en `/run/secrets/openbao-tls/ca.crt` e importan cada ruta con un
prefijo (`vault://secret/patient/db/app?prefix=patient.db.app.`). Leen los secretos **al arrancar**; si
el clúster cae después, siguen funcionando. `clinical-history-service` además llama a transit en cada
firma y al abrir la clave de un paciente que no tiene en caché.

Justo después de que el clúster entero se reinicia hay unos segundos en que un nodo en espera todavía
no conoce al activo y responde `500 active cluster node not found`. Un servicio que arranque en esa
ventana falla y Docker lo reinicia (`restart: unless-stopped`).

## Arranque

`docker compose up` levanta la cadena completa: `openbao-bootstrap` → nodos → `openbao-init` →
`openbao-agent` → bases, Kafka Connect, almacenamiento y servicios. No hay pasos manuales: los nodos se
desellan solos con el sello estático, también tras un reinicio.

Token root y claves de recuperación (solo desarrollo):

```bash
docker run --rm --user root -v clinica_openbao_bootstrap:/bootstrap --entrypoint jq \
  clinica/openbao-tools:2.6.2 . /bootstrap/init.json
```

Consola `bao` contra el clúster (el prefijo `clinica_` es el nombre del proyecto de Compose):

```bash
docker run --rm -it --user root --network clinica_secrets-net \
  -v clinica_openbao_tls:/openbao/tls:ro -v clinica_openbao_bootstrap:/bootstrap:ro \
  -e BAO_ADDR=https://openbao:8200 -e BAO_CACERT=/openbao/tls/ca.crt \
  --entrypoint sh clinica/openbao-tools:2.6.2 -c 'export BAO_TOKEN=$(jq -r .root_token /bootstrap/init.json); sh'
```

## Agregar un servicio

1. Política en `policies/<servicio>.hcl` con solo las rutas que necesita.
2. Añadir el servicio a `APPROLES` y sus secretos a `seed` en `scripts/configure.sh`.
3. Un volumen `openbao_approle_<servicio>` montado en `openbao-init` y, de solo lectura, en el
   servicio en `/run/secrets/openbao/approle`, más `openbao_tls` en `/run/secrets/openbao-tls`.
4. La red `secrets-net` y el perfil `openbao` en el servicio.

## Rotar una contraseña de base de datos sin cortar el servicio

MySQL 8 admite dos contraseñas por usuario mientras se reinician las réplicas:

```bash
bao kv patch -mount=secret patient/db/app password="$NUEVA"

docker exec -i patient-db sh -c 'mysql -uroot -p"$(cat "$MYSQL_ROOT_PASSWORD_FILE")"' <<SQL
ALTER USER 'patient_app'@'%' IDENTIFIED BY '$NUEVA' RETAIN CURRENT PASSWORD;
SQL

for replica in clinica-patient-service-1 clinica-patient-service-2; do
  docker restart "$replica"
  until [ "$(docker inspect -f '{{.State.Health.Status}}' "$replica")" = healthy ]; do sleep 5; done
done

docker exec -i patient-db sh -c 'mysql -uroot -p"$(cat "$MYSQL_ROOT_PASSWORD_FILE")"' <<SQL
ALTER USER 'patient_app'@'%' DISCARD OLD PASSWORD;
SQL
```

El agente vuelve a renderizar los archivos de infraestructura en su siguiente ciclo (cada 5 minutos).
La contraseña root de MySQL solo se usa al crear la base: cambiarla en OpenBao exige también un
`ALTER USER 'root'`.

Para rotar un `secret-id`, borrar el archivo del volumen `openbao_approle_<servicio>` y volver a correr
`openbao-init`; el anterior se revoca con
`bao write auth/approle/role/<servicio>/secret-id/destroy secret_id=...`.

## Recuperación

| Situación                     | Qué pasa y qué hacer                                                   |
| ----------------------------- | ---------------------------------------------------------------------- |
| Cae un nodo                   | Los otros dos mantienen quórum y eligen un activo. Al volver, el nodo se desella y se reincorpora solo |
| Caen dos nodos                | Sin quórum no hay lecturas nuevas; los servicios en marcha siguen. Al volver uno, el clúster se recupera |
| Se pierde el volumen de un nodo | Borrarlo del clúster (`bao operator raft remove-peer <nodo>`) y arrancarlo vacío: se une con `retry_join` |
| Se pierde `openbao_seal`      | Los datos quedan ilegibles. En desarrollo: `docker compose down -v` y empezar de nuevo. En producción la clave vive en un KMS/HSM y no se pierde con un volumen |

## Producción

Esta configuración es de desarrollo en un solo host. En producción:

- Auto-unseal con KMS o HSM en lugar del sello estático.
- Certificados emitidos por la CA de la organización, no por `openbao-bootstrap`.
- Nodos en máquinas o zonas distintas.
- Token root revocado tras la instalación (`bao token revoke`), y regenerado con las claves de
  recuperación solo cuando haga falta.
- `secret-id` entregado con la identidad de la plataforma (Kubernetes, nube) o con *response
  wrapping*, con TTL y usos limitados.
- Registro de auditoría enviado a un almacenamiento central.
- Un agente por consumidor si no se acepta que `infra-agent` lea los secretos de toda la
  infraestructura.

Lo verifica `platform/e2e/openbao-e2e.sh`: políticas, ausencia de secretos en `docker inspect`,
conmutación del nodo activo, caída total del clúster y auditoría.
