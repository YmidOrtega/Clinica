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

Requisitos: Docker, Node.js (para firmar tokens), `jq` y `curl`.

```bash
cd BackEnd-Clinica

# 1. Clave RSA para firmar los tokens de prueba
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/clinica-private.pem
export JWT_PUBLIC_KEY=$(openssl pkey -in /tmp/clinica-private.pem -pubout | grep -v '^-----' | tr -d '\n')

# 2. Stack: OpenBao genera las credenciales y las claves de transit; solo hacen falta los nombres de las bases
export PATIENT_DB_NAME=patient_db CLINICAL_DB_NAME=clinical_db
docker compose -p clinical-e2e -f docker-compose.yml -f docker-compose.debug.yml \
  up -d --build patient-service clinical-history-service kafka-connect-init

# 3. Pruebas
COMPOSE_PROJECT=clinical-e2e JWT_PRIVATE_KEY=/tmp/clinica-private.pem \
  sh platform/e2e/clinical-e2e.sh
COMPOSE_PROJECT=clinical-e2e JWT_PRIVATE_KEY=/tmp/clinica-private.pem \
  sh platform/e2e/openbao-e2e.sh
```

## Prueba de OpenBao

`openbao-e2e.sh` corre sobre el mismo stack y detiene nodos a propósito:

| Paso                         | Qué demuestra                                                            |
| ---------------------------- | ------------------------------------------------------------------------ |
| Clúster                      | Tres nodos desellados, raft con tres votantes y un nodo activo           |
| Políticas                    | Cada AppRole (patient, clinical, auth) lee solo sus rutas; el agente no escribe; solo `clinical-history-service` firma con `transit/clinical-seal` y no puede rotar sus claves |
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
