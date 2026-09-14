# Copias de la historia clínica para el paciente

El área de archivo clínico (rol `MEDICAL_RECORDS`) emite la copia que el paciente tiene derecho a
recibir. La copia es un PDF que incluye:

- datos del paciente y de los registros provisionales (NN) vinculados a él;
- estado de la verificación de integridad de cada cadena al momento de generarla;
- alergias, antecedentes, medicamentos y signos vitales con su origen;
- cada atención con todas sus notas, incluidas las restringidas y las anuladas (con motivo), marcas de
  registro extemporáneo, diagnósticos CIE-10 con la versión del catálogo, actualizaciones y anexos con
  su SHA-256;
- el hash del contenido y del eslabón sellado de cada registro;
- una página final que explica cómo verificar la copia.

## Emitir

```http
POST /api/v1/clinical/patients/{uuid}/record-copies
{"reason": "Derecho de petición radicado por la paciente", "from": "2026-01-01T00:00:00Z", "to": null}
```

- `reason` es obligatorio (mínimo 10 caracteres) y se guarda cifrado.
- La respuesta es el PDF; la cabecera `X-Record-Copy-Id` trae el identificador impreso en cada página.
- Se registra en `clinical_ledger.record_copies` (solo inserción) y en `clinical.access-audit.v1` como
  `EXPORT_RECORD` con `basis: RECORDS_OFFICE`, `exportReason` y `restrictedContent`.
- Los anexos no se incrustan: se entregan descargándolos y se comprueban con el SHA-256 de la copia.

## Verificar

La institución guarda el SHA-256 del PDF emitido y un sello ECDSA sobre ese hash, hecho con la misma
clave institucional de la cadena (`claves-y-cifrado.md`).

- `GET /api/v1/clinical/record-copies/{id}`: registro de la copia, hash, clave y sello.
- `POST /api/v1/clinical/record-copies/{id}/verification` con el archivo: responde `authentic: true`
  solo si el PDF es idéntico al emitido y el sello es válido. Cualquier byte cambiado da
  `documentMatches: false`.

La copia en FHIR queda para un incremento futuro.
