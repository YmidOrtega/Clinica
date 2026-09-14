# Claves, cifrado y sello de la historia clínica

`clinical-history-service` usa tres juegos de claves. Ninguna vive en la base de datos y cada una se
monta como secreto de solo lectura en el contenedor.

| Clave | Para qué | Dónde | Variable |
|---|---|---|---|
| Clave maestra de cifrado (AES-256) | Envolver las claves de datos de cada paciente | `<id>.key` en un directorio | `CLINICAL_ENCRYPTION_KEYS_LOCATION`, `CLINICAL_ENCRYPTION_ACTIVE_KEY_ID` |
| Clave del sello institucional (ECDSA P-256) | Sellar cada eslabón de la cadena del paciente | `<id>.private.pem` y `<id>.public.pem` | `CLINICAL_SEAL_KEYS_LOCATION`, `CLINICAL_SEAL_ACTIVE_KEY_ID` |
| Keyring de MySQL | Cifrado InnoDB de tablas, redo, undo y binlog | Volumen `/var/lib/mysql-keyring` | Imagen `docker/mysql` |

El servicio no arranca si falta la clave activa de cifrado o de sello, si la clave maestra no mide 32
bytes o si el par del sello no coincide.

## Capas de cifrado

```
texto de la nota ──AES-256-GCM──► content_ciphertext        (clinical_ledger.notes)
                    ▲ clave de datos del paciente (DEK)
                    │
      DEK ──AES-256-GCM──► wrapped_key                        (clinical_keys.data_key_wrappings)
                    ▲ clave maestra activa (KEK, fuera de la BD)

todo el tablespace ──InnoDB + keyring──► archivos .ibd, redo, undo y binlog cifrados
```

- Se cifra lo narrativo: contenido de notas y borradores y motivo de anulación. Tipo, autor, fechas y
  hashes quedan en claro para poder consultar y verificar sin descifrar.
- Cada paciente tiene una clave de datos. El texto cifrado se autentica con el id del registro y el
  campo, así que copiar el cifrado de una nota en otra la vuelve ilegible y la verificación de
  integridad lo reporta como `UNREADABLE_ENTRY`.
- El hash de la cadena se calcula sobre el texto en claro, de modo que rotar claves no rompe los sellos.
- El cifrado InnoDB protege copias de archivos y respaldos físicos; no protege de alguien con acceso a
  MySQL, para eso está el cifrado de aplicación.

## Generar claves

```bash
head -c 32 /dev/urandom | base64 > master-2026.key
chmod 0400 master-2026.key

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out seal-2026.private.pem
openssl pkey -in seal-2026.private.pem -pubout -out seal-2026.public.pem
chmod 0400 seal-2026.private.pem
```

## Respaldo

- Guardar cada clave maestra y la privada del sello en al menos dos lugares fuera del servidor (bóveda
  de secretos y medio offline bajo custodia), nunca junto a los respaldos de la base de datos.
- Respaldar el volumen del keyring de MySQL por separado del volumen de datos.
- **Perder una clave maestra sin respaldo hace ilegible para siempre el contenido de los pacientes
  cuyas claves de datos solo estaban envueltas con ella.** La retención legal es de 15 años.

## Rotar la clave maestra

1. Generar `master-2027.key` y copiarla al directorio junto a la anterior.
2. Cambiar `CLINICAL_ENCRYPTION_ACTIVE_KEY_ID=master-2027` y reiniciar las réplicas. Las claves de datos
   nuevas se envuelven con la nueva; las anteriores siguen abriendo con `master-2026`.
3. Con un usuario `SUPER_ADMIN`: `POST /api/v1/clinical/admin/encryption/rewrap`. Agrega una envoltura
   con la clave nueva a cada clave de datos; no vuelve a cifrar el contenido.
4. `GET /api/v1/clinical/admin/encryption` debe mostrar `pendingRewrap: 0` y
   `retiredKeysCanBeRemoved: true`.
5. Retirar `master-2026.key` del directorio de las réplicas y conservarla archivada.

## Rotar la clave del sello

1. Generar el par `seal-2027`, copiarlo junto al anterior y activar `CLINICAL_SEAL_ACTIVE_KEY_ID=seal-2027`.
2. Borrar `seal-2026.private.pem` pero **conservar `seal-2026.public.pem` para siempre**: los eslabones
   sellados con ella se siguen verificando con la pública. `GET /api/v1/clinical/seal-keys` las publica.

## Recuperación

| Situación | Síntoma | Qué hacer |
|---|---|---|
| Falta la clave maestra activa | El servicio no arranca | Restaurar el archivo desde el respaldo |
| Falta una clave maestra retirada antes de tiempo | Lecturas con `CLINICAL_CONTENT_UNREADABLE` y `UNREADABLE_ENTRY` en la verificación | Restaurar el archivo, reiniciar y ejecutar el rewrap |
| Falta la pública de un sello anterior | `UNKNOWN_KEY` en la verificación | Restaurar el `.public.pem` archivado |
| Se perdió el keyring de MySQL | MySQL no abre los tablespaces | Restaurar el volumen del keyring del mismo respaldo que los datos |
| Contenido alterado fuera de la aplicación | `UNREADABLE_ENTRY` o `PAYLOAD_MISMATCH` | Tratar como incidente de seguridad; los datos originales salen del respaldo |

Las pruebas `ContentEncryptionIT`, `SealKeyRingTest`, `EcdsaClinicalSignatureTest` e
`InnoDbEncryptionIT` ejercitan la rotación, la recuperación desde respaldo y el cifrado en disco.
