CREATE TABLE patient_share_adjustments (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    uuid       CHAR(36)      NOT NULL,
    account_id BIGINT        NOT NULL,
    sale_uuid  CHAR(36)      NULL,
    amount     DECIMAL(14,2) NOT NULL,
    reason     VARCHAR(500)  NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    created_by VARCHAR(36)   NULL,

    CONSTRAINT pk_patient_share_adjustments PRIMARY KEY (id),
    CONSTRAINT uk_patient_share_adjustments_uuid UNIQUE (uuid),
    CONSTRAINT fk_patient_share_adjustments_account FOREIGN KEY (account_id) REFERENCES episode_accounts (id),

    CONSTRAINT chk_patient_share_adjustments_amount CHECK (amount >= 0),
    CONSTRAINT chk_patient_share_adjustments_reason CHECK (CHAR_LENGTH(TRIM(reason)) >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_patient_share_adjustments_account ON patient_share_adjustments (account_id, created_at);
