-- =====================================================
-- 1. MODIFICACION TABLA ATTENTIONS (Atenciones)
-- =====================================================
ALTER TABLE attentions
DROP COLUMN health_provider_nit;
-- =====================================================
-- 2. TABLA ATTENTION_HEALTH_PROVIDERS (IDs de los proveedores)
-- =====================================================
CREATE TABLE attention_health_providers (
    attention_id BIGINT NOT NULL,
    health_provider_nit BIGINT NOT NULL,
    PRIMARY KEY (attention_id, health_provider_nit),
    CONSTRAINT fk_attention_hp_attention FOREIGN KEY (attention_id) REFERENCES attentions(id) ON DELETE CASCADE,
    INDEX idx_attention_hp_attention (attention_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;