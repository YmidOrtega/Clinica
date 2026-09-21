CREATE TABLE admissions.episode_receipts (
    id              UUID         NOT NULL,
    admission_id    BIGINT       NOT NULL,
    admission_number VARCHAR(20) NOT NULL,
    issued_by       UUID         NOT NULL,
    issued_by_role  VARCHAR(30)  NOT NULL,
    issued_at       TIMESTAMP(6) NOT NULL,
    document_sha256 CHAR(64)     NOT NULL,
    key_id          VARCHAR(64)  NOT NULL,
    seal            VARCHAR(512) NOT NULL,

    CONSTRAINT pk_episode_receipts PRIMARY KEY (id),
    CONSTRAINT fk_episode_receipts_admission FOREIGN KEY (admission_id) REFERENCES admissions.admissions (id),
    CONSTRAINT chk_episode_receipts_sha256 CHECK (document_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_episode_receipts_admission ON admissions.episode_receipts (admission_id);
CREATE UNIQUE INDEX uq_episode_receipts_number_fingerprint
    ON admissions.episode_receipts (admission_number, document_sha256);
