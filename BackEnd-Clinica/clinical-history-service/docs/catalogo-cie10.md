# Catálogo CIE-10

Los diagnósticos de las notas y las enfermedades crónicas se codifican con la **Tabla de referencia
CIE-10 de SISPRO** (Ministerio de Salud y Protección Social). El servicio no la versiona en Flyway: se
importa el archivo oficial y se activa explícitamente.

## Importar y activar

1. Descargar de SISPRO la tabla vigente (por ejemplo `Tabla-CIE-10-2018_08022021 sin restricciones.xlsx`).
2. Con un usuario `SUPER_ADMIN`:

   ```bash
   curl -H "Authorization: Bearer $TOKEN" -F "file=@Tabla-CIE-10-2018_08022021 sin restricciones.xlsx" \
        https://<gateway>/api/v1/clinical/admin/terminology/cie10/releases
   ```

   - Se lee la hoja `Final`; la versión es la `FECHA DE ACTUALIZACION` del archivo (`2021-02-08`).
   - Es idempotente por SHA-256: volver a subir el mismo archivo responde `200` con `created: false`.
   - Otra tabla con la misma fecha y distinto contenido responde `409 TERMINOLOGY_VERSION_CONFLICT`.
   - `warnings` lista filas corregidas al importar. El archivo de 2021 trae los códigos `I700` a `I709`
     bajo la categoría `I69`; se toman bajo `I70` sin título de categoría.
3. Revisar la respuesta y activar:
   `POST /api/v1/clinical/admin/terminology/cie10/releases/{id}/activation`.

La tabla oficial (12.568 códigos) se importa en unos 3 segundos. Las activaciones quedan registradas en
`terminology_activations`; la vigente es la última.

## Qué guarda cada nota

Al firmar, cada diagnóstico queda con código, descripción y versión del catálogo dentro del contenido
firmado y sellado. Cambiar de catálogo después no altera notas ya firmadas. Sin catálogo activo, las
búsquedas y las notas con diagnósticos responden `503 TERMINOLOGY_NOT_ACTIVE`.

La tabla de 2021 no trae restricciones de sexo ni de edad, por eso el servicio no las valida.
