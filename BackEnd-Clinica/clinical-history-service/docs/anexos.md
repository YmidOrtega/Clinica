# Anexos de la historia clínica

Las notas pueden llevar hasta 10 anexos PDF, JPEG o PNG de máximo 20 MB. Se guardan en un
almacenamiento compatible con **Amazon S3 con Object Lock en modo COMPLIANCE** (WORM): una vez
firmados, nadie puede borrarlos ni acortar su retención, ni siquiera con las credenciales del
almacenamiento.

## Por qué S3 y no un producto concreto

MinIO dejó de publicar imágenes comunitarias (la última es `RELEASE.2025-09-07T16-13-09Z`). El
servicio usa la API S3 estándar (AWS SDK v2), así que en producción sirve cualquier almacenamiento con
Object Lock: Amazon S3, Ceph RGW o MinIO AIStor. Las pruebas usan esa imagen de MinIO fijada.

## Flujo

```
POST /drafts/{id}/attachments ──► valida por contenido (magic bytes), SHA-256, cifra con la clave del paciente
                                   └─► bucket de espera  attachments/{id}   (sin bloqueo, expira a los 7 días)
POST /drafts/{id}/signature   ──► CopyObject al bucket de archivo con COMPLIANCE hasta la última atención + 15 años
                                   ├─► el SHA-256, nombre, tipo y tamaño entran al contenido firmado y sellado
                                   └─► tras el commit se borra la copia en espera
GET /notes/{id}/attachments/{attachmentId}
                              ──► acceso por relación de cuidado (y reglas de notas restringidas)
                                   ├─► evento READ_ATTACHMENT en clinical.access-audit.v1
                                   └─► descifra, verifica el SHA-256 firmado y responde como descarga (nosniff, no-store)
```

- El almacenamiento nunca ve el contenido en claro: los objetos van cifrados con AES-256-GCM con la
  clave de datos del paciente (ver `claves-y-cifrado.md`). El id de la clave va en los metadatos del
  objeto; el nombre del archivo va cifrado en la base de datos.
- No hay URLs firmadas: toda descarga pasa por el servicio para controlar el acceso y auditarla.
- Si alguien reemplaza el objeto, S3 conserva la versión bloqueada y el servicio responde
  `CLINICAL_CONTENT_UNREADABLE` porque el SHA-256 ya no coincide.

## Retención

- Al firmar: `recordedAt + 15 años + 1 día`.
- Tarea diaria (`clinica.clinical.attachments.retention-cron`, 03:30 hora de Bogotá): para los pacientes
  con atenciones nuevas desde la última corrida, extiende la retención de todos sus anexos (incluidos
  los registrados mientras estuvieron sin identificar) a `última atención + 15 años + 1 día`. COMPLIANCE
  solo permite extender, nunca acortar.
- Eliminar anexos vencidos es un proceso manual con acta, fuera del servicio.

## Buckets y permisos

| Bucket | Configuración | Permisos del servicio |
|---|---|---|
| `clinical-attachments-staging` | Sin Object Lock, regla de expiración de 7 días | `PutObject`, `GetObject`, `DeleteObject`, `ListBucket` |
| `clinical-attachments` | Creado con Object Lock (activa el versionado) | `PutObject` (vía copia), `GetObject`, `GetObjectVersion`, `HeadObject`, `PutObjectRetention`, `GetBucketObjectLockConfiguration` |

- El servicio **no** necesita `DeleteObject` sobre el archivo; no se lo den.
- Antes del primer archivado verifica que el bucket tenga Object Lock; si no, rechaza el anexo en vez de
  guardarlo sin protección.
- `CLINICAL_ATTACHMENTS_CREATE_BUCKETS=true` solo para desarrollo y pruebas; en producción los buckets
  se crean con infraestructura.

Variables: `CLINICAL_ATTACHMENTS_ENDPOINT` (vacío para Amazon S3), `CLINICAL_ATTACHMENTS_REGION`,
`CLINICAL_ATTACHMENTS_ACCESS_KEY`, `CLINICAL_ATTACHMENTS_SECRET_KEY`,
`CLINICAL_ATTACHMENTS_STAGING_BUCKET`, `CLINICAL_ATTACHMENTS_ARCHIVE_BUCKET`.

## Fallos

- Si el almacenamiento no responde, subir, descargar o firmar una nota **con** anexos responde
  `503 ATTACHMENT_STORAGE_UNAVAILABLE`; el resto de la historia clínica sigue funcionando.
- Si la copia al archivo se hizo pero la firma falla después, queda un objeto cifrado y bloqueado sin
  nota; al reintentar la firma se vuelve a copiar con el mismo id. Es inofensivo: está cifrado y no se
  puede leer sin su nota.
- Una tarea horaria borra de la zona de espera las subidas sin borrador que tengan más de 1 hora.

## Pendiente

Antivirus (ClamAV) antes de aceptar el archivo, en un incremento futuro.
