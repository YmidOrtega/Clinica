# Security Roles - AI Assistant Service

## Roles Disponibles

### SUPER_ADMIN
- Acceso total a todas las operaciones del asistente IA
- Puede ver historial completo de conversaciones
- Puede cerrar conversaciones

### ADMIN
- Acceso completo al chat con IA
- Puede ver su propio historial
- Puede cerrar sus propias conversaciones

### RECEPTIONIST
- Acceso al chat para creación de atenciones
- Puede ver su propio historial
- Puede cerrar sus propias conversaciones

### DOCTOR
- SIN ACCESO al asistente IA (por ahora)
- Los doctores no necesitan crear atenciones directamente

## Matriz de Permisos

| Endpoint | SUPER_ADMIN | ADMIN | RECEPTIONIST | DOCTOR |
|----------|-------------|-------|--------------|--------|
| POST /api/v1/ai-assistant/chat | ✅ | ✅ | ✅ | ❌ |
| GET /api/v1/ai-assistant/history | ✅ | ✅ | ✅ | ❌ |
| DELETE /api/v1/ai-assistant/conversation/{id} | ✅ | ✅ | ✅ | ❌ |
| GET /api/v1/ai-assistant/health | ✅ | ✅ | ✅ | ✅ |

## Reglas de Negocio

1. **Chat**: RECEPTIONIST, ADMIN y SUPER_ADMIN pueden interactuar con el asistente IA
2. **Historial**: Los usuarios solo ven su propio historial (el servicio filtra por userId automáticamente)
3. **Cierre de conversación**: Los usuarios solo pueden cerrar sus propias conversaciones
4. **Health Check**: Endpoint público para monitoreo