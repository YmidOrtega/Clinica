# Claves, cifrado y sello de la historia clínica

`clinical-history-service` usa tres juegos de claves. Las dos de aplicación viven en el motor
**transit** de OpenBao y **nunca salen de él**: el servicio le pide cifrar, descifrar y firmar. Ninguna
clave está en la base de datos, en archivos del servicio ni en variables de entorno.

| Clave | Para qué | Dónde | Configuración |
|---|---|---|---|
| Clave maestra de cifrado (KEK, `aes256-gcm96`) | Envolver la clave de datos de cada paciente | Transit `clinical-kek` | `CLINICAL_ENCRYPTION_TRANSIT_KEY` |
| Clave del sello institucional (`ecdsa-p256`) | Sellar cada eslabón de la cadena del paciente y las copias en PDF | Transit `clinical-seal` | `CLINICAL_SEAL_TRANSIT_KEY` |
| Keyring de MySQL | Cifrado InnoDB de tablas, redo, undo y binlog | Volumen `/var/lib/mysql-keyring` | Imagen `docker/mysql` |

`openbao-init` crea las dos claves de transit como no exportables. La política de
`clinical-history-service` solo permite cifrar y descifrar con `clinical-kek`, firmar con
`clinical-seal` y leer sus metadatos (versiones y claves públicas); rotarlas o borrarlas queda para un
operador. El servicio no arranca si alguna falta o no es del tipo esperado.

## Capas de cifrado

```
texto de la nota ──AES-256-GCM──► content_ciphertext              (clinical_ledger.notes)
                    ▲ clave de datos del paciente (DEK, en memoria)
                    │
      DEK ──transit encrypt (clinical-kek, AAD)──► vault:vN:…      (clinical_keys.data_key_wrappings)

entrada de la cadena ──SHA-256──► entry_hash ──transit sign (clinical-seal)──► seal (DER)

todo el tablespace ──InnoDB + keyring──► archivos .ibd, redo, undo y binlog cifrados
```

- Se cifra lo narrativo: contenido de notas y borradores, motivos de anulación, de copia y de acceso de
  emergencia, y los anexos. Tipo, autor, fechas y hashes quedan en claro para consultar y verificar sin
  descifrar.
- Cada paciente tiene una DEK. El contenido se cifra en el servicio con AES-256-GCM y un AAD que lo ata a
  su propósito y a su registro, así que copiar el cifrado de una nota en otra la vuelve ilegible y la
  verificación de integridad lo reporta como `UNREADABLE_ENTRY`.
- La DEK se envuelve en transit con el AAD `data-key/v1|<dek>|<paciente>|<clave>`: una envoltura copiada a
  otro paciente no abre. Las DEK abiertas se guardan en memoria 10 minutos desde el último uso.
- Cada envoltura y cada sello guardan la versión exacta de la clave (`clinical-kek-v1`,
  `clinical-seal-v2`), que también entra en el hash de la cadena.
- Los sellos se **verifican localmente** con las claves públicas que publica transit: verificar la
  integridad no necesita a OpenBao mientras las versiones ya estén en caché.
- El cifrado InnoDB protege copias de archivos y respaldos físicos; no protege de alguien con acceso a
  MySQL, para eso está el cifrado de aplicación.

## Qué pasa si OpenBao no responde

| Operación | Sin OpenBao |
|---|---|
| Leer notas de un paciente con la DEK en caché | Funciona |
| Leer notas de otro paciente, crear la DEK de un paciente nuevo | `503 CLINICAL_KEYS_UNAVAILABLE` |
| Firmar, anular o cerrar (sellan un eslabón), emitir una copia en PDF | `503 CLINICAL_KEYS_UNAVAILABLE` |
| Verificar la integridad o una copia | Funciona con las versiones ya conocidas |

El clúster de tres nodos (`platform/openbao/README.md`) tolera la caída de uno sin que nada de esto ocurra.

## Rotar la clave maestra

1. Rotar en OpenBao: `bao write -f transit/keys/clinical-kek/rotate`. Las envolturas nuevas usan la
   versión nueva en cuanto el servicio refresca los metadatos (cada 5 minutos o al consultar el estado).
2. Con un usuario `SUPER_ADMIN`: `POST /api/v1/clinical/admin/encryption/rewrap`.
   Agrega a cada DEK una envoltura con la versión activa; no vuelve a cifrar el contenido.
3. `GET /api/v1/clinical/admin/encryption` debe mostrar `pendingRewrap: 0` y `retiredKeysCanBeRemoved: true`.
4. Opcional: retirar las versiones anteriores con
   `bao write transit/keys/clinical-kek/config min_decryption_version=<N>`. Sin respaldo de OpenBao, una
   versión retirada y recortada ya no descifra.

## Rotar la clave del sello

`bao write -f transit/keys/clinical-seal/rotate`. Los sellos nuevos usan la versión nueva y los
anteriores se siguen verificando: transit conserva las claves públicas de todas las versiones y
`GET /api/v1/clinical/seal-keys` las publica. **Nunca** subir `min_decryption_version` de `clinical-seal`:
la cadena debe verificarse durante los 15 años de retención.

## Migrar desde claves en archivos

Los entornos creados antes de transit tienen DEK envueltas con claves maestras en archivos
(`master-*.key`) y eslabones sellados con claves PEM (`seal-*`). Se migran sin reescribir la historia:

1. Cargar el material retirado en OpenBao con el token root:

   ```bash
   bao kv put -mount=secret clinical/retired-master-keys master-2026="$(cat master-2026.key)"
   bao kv put -mount=secret clinical/retired-seal-keys seal-2026=@seal-2026.public.pem
   ```

2. Reiniciar las réplicas: la clave maestra activa pasa a ser `clinical-kek-vN` y las retiradas solo abren.
3. `POST /api/v1/clinical/admin/encryption/rewrap` hasta `pendingRewrap: 0`.
4. Borrar las claves maestras retiradas de OpenBao (`bao kv metadata delete -mount=secret
   clinical/retired-master-keys`), reiniciar y archivar los `.key` fuera de línea. **La privada del sello
   anterior se destruye; su pública se queda para siempre en `clinical/retired-seal-keys`**, porque los
   eslabones que firmó se siguen verificando con ella.

## Respaldo

- Respaldar el almacenamiento raft de OpenBao (`bao operator raft snapshot save`) y, en producción, la
  clave del KMS/HSM que lo desella. Sin ambos, el contenido clínico es ilegible.
- Respaldar el volumen del keyring de MySQL por separado del volumen de datos.
- La retención legal es de 15 años: las versiones de `clinical-kek` que aún envuelven DEK y todas las
  versiones de `clinical-seal` deben seguir disponibles durante ese tiempo.

## Recuperación

| Situación | Síntoma | Qué hacer |
|---|---|---|
| OpenBao sin quórum | `503 CLINICAL_KEYS_UNAVAILABLE` al firmar o al leer pacientes fuera de caché | Recuperar nodos (`platform/openbao/README.md`); el servicio no necesita reiniciarse |
| Se recortó una versión de `clinical-kek` que aún envolvía DEK | `CLINICAL_CONTENT_UNREADABLE` y `UNREADABLE_ENTRY` | Restaurar el snapshot de OpenBao y ejecutar el rewrap antes de recortar |
| Falta la pública de un sello anterior a transit | `UNKNOWN_KEY` en la verificación | Volver a cargarla en `secret/clinical/retired-seal-keys` |
| Se perdió el keyring de MySQL | MySQL no abre los tablespaces | Restaurar el volumen del keyring del mismo respaldo que los datos |
| Contenido alterado fuera de la aplicación | `UNREADABLE_ENTRY` o `PAYLOAD_MISMATCH` | Tratar como incidente de seguridad; los datos originales salen del respaldo |

Las pruebas `TransitClientIT`, `TransitSealSignerIT`, `ContentEncryptionIT` (migración desde claves en
archivos y rotación de transit), `SealKeyRingTest`, `EcdsaClinicalSignatureTest` e `InnoDbEncryptionIT`
ejercitan el cifrado, la rotación y la migración contra un OpenBao real.
