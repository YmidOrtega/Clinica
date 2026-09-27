# Simuladores de servicios externos

`docker-compose.debug.yml` levanta dos WireMock para que el stack de desarrollo y los E2E recorran la
facturación completa sin credenciales reales:

| Contenedor | Simula | Responde |
|---|---|---|
| `dian-simulator` | Servicio web de facturación electrónica de la DIAN (SOAP 1.2, ambiente de habilitación) | recibe el set de pruebas, acepta cualquier zip con un `ApplicationResponse` y dice no conocer documentos consultados por CUFE |
| `muv-simulator` | API FEV-RIPS del mecanismo único de validación del Ministerio de Salud | autentica cualquier usuario SISPRO y valida cualquier RIPS con un CUV aleatorio de 96 caracteres |

Solo exigen lo mínimo del contrato real: el sobre de la DIAN debe venir firmado con WS-Security y el
MUV exige el token del login. No validan el contenido: lo que sí valida el contenido son las pruebas de
integración de billing-service y, antes de producción, la habilitación ante la DIAN y el MUV reales.

Los mapeos viven en `dian/mappings` y `muv/mappings`; `.gitignore` ignora `*.json`, así que un mapeo
nuevo se agrega con `git add -f`.
