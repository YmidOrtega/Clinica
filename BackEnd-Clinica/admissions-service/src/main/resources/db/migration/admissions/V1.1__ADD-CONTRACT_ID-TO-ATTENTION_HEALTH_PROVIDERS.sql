ALTER TABLE attention_health_providers
ADD COLUMN contract_id BIGINT;
UPDATE attention_health_providers
SET contract_id = 0;