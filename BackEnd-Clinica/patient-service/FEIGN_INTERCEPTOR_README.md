# Implementación de RequestInterceptor para Feign Client

## Descripción

Se ha implementado un `RequestInterceptor` personalizado para el servicio de pacientes que automáticamente propaga el token JWT de autenticación a todas las peticiones inter-servicios realizadas mediante Feign Client.

## Archivos Creados

### 1. FeignClientInterceptor.java
**Ubicación:** `src/main/java/com/ClinicaDeYmid/patient_service/infra/security/FeignClientInterceptor.java`

**Responsabilidades:**
- Intercepta todas las peticiones Feign antes de ser enviadas
- Extrae el token JWT del `HttpServletRequest` actual (fuente primaria)
- Fallback: intenta extraer del `SecurityContext` si el request no está disponible
- Añade el token al header `Authorization` con el formato `Bearer {token}`
- Logging detallado para debugging y troubleshooting

**Características de Seguridad:**
- ✅ No almacena tokens en memoria más allá del scope de la petición
- ✅ Manejo seguro de excepciones sin exponer información sensible
- ✅ Validación de tokens antes de propagarlos
- ✅ Logging apropiado (debug para datos sensibles, warn para problemas)

### 2. FeignConfig.java
**Ubicación:** `src/main/java/com/ClinicaDeYmid/patient_service/module/config/FeignConfig.java`

**Responsabilidades:**
- Registra el interceptor como bean de Spring
- Configura el nivel de logging de Feign (BASIC por defecto)
- Centraliza la configuración de Feign Client

## Flujo de Funcionamiento

```
1. Usuario hace petición HTTP → API Gateway
2. API Gateway valida JWT y enruta a patient-service
3. JwtAuthenticationFilter valida token y establece SecurityContext
4. Controlador procesa la petición
5. Servicio necesita llamar a otro microservicio (ej: clients-service)
6. FeignClientInterceptor se ejecuta automáticamente:
   a. Extrae token del HttpServletRequest actual
   b. Si no está disponible, intenta obtenerlo del SecurityContext
   c. Añade header: Authorization: Bearer {token}
7. Feign envía la petición con el token JWT incluido
8. El servicio destino recibe la petición autenticada
```

## Uso

El interceptor se aplica automáticamente a todos los clientes Feign del servicio. No requiere configuración adicional en los clientes individuales.

### Ejemplo - Cliente Feign Existente:

```java
@FeignClient(name = "clients-service", path = "/api/v1/billing-service")
public interface HealthProviderClient {
    
    @GetMapping("/health-providers/{nit}")
    HealthProviderNitDto getHealthProviderByNit(@PathVariable("nit") String nit);
    
    // El token JWT se propagará automáticamente ✅
}
```

## Configuración de Logging

Para habilitar logs detallados de Feign en `application.yml`:

```yaml
logging:
  level:
    com.ClinicaDeYmid.patient_service.module.feignclient: DEBUG
    com.ClinicaDeYmid.patient_service.infra.security.FeignClientInterceptor: DEBUG
```

## Buenas Prácticas Implementadas

### Seguridad
1. **No almacenamiento de tokens**: Los tokens no se almacenan en variables de instancia
2. **Validación de entrada**: Se verifica que el token existe antes de propagarlo
3. **Manejo de errores**: Excepciones manejadas sin exponer detalles sensibles
4. **Principio de mínimo privilegio**: Solo propaga información necesaria

### Performance
1. **Extracción eficiente**: Prioriza la fuente más confiable (HttpServletRequest)
2. **Fallback strategy**: Implementa fallback sin overhead significativo
3. **Logging condicional**: Debug logs solo cuando es necesario

### Mantenibilidad
1. **Código documentado**: Javadoc completo y comentarios donde es necesario
2. **Separación de responsabilidades**: Interceptor y configuración separados
3. **Uso de constantes**: Headers y prefijos como constantes
4. **Logging estructurado**: Mensajes de log consistentes y útiles

## Mejoras Futuras Opcionales

### 1. Almacenar Token en CustomUserDetails (Opcional)

Si se necesita acceder al token desde contextos sin HttpServletRequest:

```java
@Builder
public class CustomUserDetails implements UserDetails {
    private String userId;
    private String uuid;
    private String email;
    private String role;
    private List<String> permissions;
    private String jwtToken; // ⚠️ Agregar con precaución
    
    // ... resto del código
}
```

**Pros:**
- Acceso al token desde cualquier punto con SecurityContext
- Útil para tareas asíncronas o scheduled

**Contras:**
- Mayor uso de memoria
- Token almacenado durante toda la sesión HTTP
- Posible riesgo si no se maneja correctamente la limpieza

### 2. Circuit Breaker para Feign (Resilience4j)

Agregar tolerancia a fallos:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

### 3. Request/Response Logging Interceptor

Para debugging más detallado:

```java
@Bean
public Logger.Level feignLoggerLevel() {
    return Logger.Level.FULL; // Solo en desarrollo
}
```

## Testing

### Test Unitario del Interceptor

```java
@ExtendWith(MockitoExtension.class)
class FeignClientInterceptorTest {
    
    @InjectMocks
    private FeignClientInterceptor interceptor;
    
    @Mock
    private RequestTemplate requestTemplate;
    
    @Mock
    private HttpServletRequest httpServletRequest;
    
    @Test
    void apply_ShouldAddAuthorizationHeader_WhenTokenExists() {
        // Arrange
        String token = "eyJhbGciOiJSUzI1NiJ9...";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(request)
        );
        
        // Act
        interceptor.apply(requestTemplate);
        
        // Assert
        verify(requestTemplate).header("Authorization", "Bearer " + token);
    }
    
    @Test
    void apply_ShouldLogWarning_WhenNoTokenAvailable() {
        // Arrange
        RequestContextHolder.resetRequestAttributes();
        
        // Act
        interceptor.apply(requestTemplate);
        
        // Assert
        verify(requestTemplate, never()).header(anyString(), anyString());
    }
}
```

## Troubleshooting

### Problema: El token no se propaga

**Solución 1:** Verificar que el request tiene el header Authorization
```bash
# Habilitar logging debug
logging.level.com.ClinicaDeYmid.patient_service.infra.security.FeignClientInterceptor=DEBUG
```

**Solución 2:** Verificar que RequestContextHolder tiene acceso al request
- Asegurarse de que la petición se hace en el mismo thread
- Para async: configurar TaskDecorator para copiar RequestAttributes

### Problema: "No se encontró token JWT para propagar"

**Causas posibles:**
1. La petición no tiene header Authorization
2. El formato del header es incorrecto
3. La petición Feign se hace desde un thread asíncrono sin contexto

**Solución:** Verificar el origen de la petición y considerar implementar propagación de contexto para async.

## Notas de Seguridad

⚠️ **Importante:**
- Los tokens nunca deben loggearse en nivel INFO o superior en producción
- Los logs DEBUG deben estar deshabilitados en producción
- Considerar usar Spring Cloud Sleuth para trazabilidad sin exponer tokens
- Implementar rotación de tokens y validación de expiración

## Referencias

- [Spring Cloud OpenFeign Documentation](https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/)
- [Feign RequestInterceptor](https://github.com/OpenFeign/feign#requestinterceptors)
- [Spring Security Context](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)
