-- Agregar columnas timestamp faltantes solo en la tabla portfolios
-- (health_providers y contracts ya las tienen desde V1.0)

ALTER TABLE portfolios
    ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha de creación del registro',
    ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Fecha de última actualización del registro';

CREATE INDEX idx_portfolios_created_at ON portfolios(created_at);
CREATE INDEX idx_portfolios_updated_at ON portfolios(updated_at);
