-- =====================================================
-- API GATEWAY - Request Logging Tables
-- Version: 1.0
-- Description: Creation of request_logs table and analytics views
-- =====================================================

-- Crear índices adicionales para mejorar el rendimiento de queries analíticas
CREATE INDEX IF NOT EXISTS idx_request_logs_service_timestamp 
    ON request_logs(service_name, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_request_logs_user_timestamp 
    ON request_logs(user_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_request_logs_status_timestamp 
    ON request_logs(status_code, timestamp DESC);

-- Vista para analytics de peticiones por servicio
CREATE OR REPLACE VIEW v_service_analytics AS
SELECT 
    service_name,
    DATE(timestamp) as date,
    COUNT(*) as total_requests,
    COUNT(CASE WHEN status_code < 400 THEN 1 END) as successful_requests,
    COUNT(CASE WHEN status_code >= 400 THEN 1 END) as error_requests,
    AVG(duration_ms) as avg_duration_ms,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY duration_ms) as p50_duration_ms,
    PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY duration_ms) as p90_duration_ms,
    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY duration_ms) as p99_duration_ms
FROM request_logs
GROUP BY service_name, DATE(timestamp);

-- Vista para analytics de usuarios más activos
CREATE OR REPLACE VIEW v_top_users AS
SELECT 
    user_id,
    user_email,
    COUNT(*) as total_requests,
    MAX(timestamp) as last_request
FROM request_logs
WHERE user_id IS NOT NULL
GROUP BY user_id, user_email
ORDER BY total_requests DESC;

-- Vista para endpoints más utilizados
CREATE OR REPLACE VIEW v_top_endpoints AS
SELECT 
    endpoint,
    http_method,
    COUNT(*) as request_count,
    AVG(duration_ms) as avg_duration_ms
FROM request_logs
GROUP BY endpoint, http_method
ORDER BY request_count DESC;

-- Función para limpiar logs antiguos (mantener solo últimos 90 días)
CREATE OR REPLACE FUNCTION cleanup_old_logs()
RETURNS void AS $$
BEGIN
    DELETE FROM request_logs 
    WHERE timestamp < NOW() - INTERVAL '90 days';
END;
$$ LANGUAGE plpgsql;

-- Comentarios para documentación
COMMENT ON TABLE request_logs IS 'Tabla de logs de todas las peticiones al API Gateway para analytics y auditoría';
COMMENT ON COLUMN request_logs.user_id IS 'ID del usuario que realizó la petición (null si no autenticado)';
COMMENT ON COLUMN request_logs.endpoint IS 'Endpoint completo de la petición';
COMMENT ON COLUMN request_logs.duration_ms IS 'Duración de la petición en milisegundos';
COMMENT ON COLUMN request_logs.ip_address IS 'Dirección IP del cliente (considerando X-Forwarded-For)';
