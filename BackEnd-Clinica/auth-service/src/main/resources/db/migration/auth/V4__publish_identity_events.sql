CREATE DATABASE IF NOT EXISTS auth_outbox
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE auth_outbox.outbox_events (
    id            CHAR(36)    NOT NULL,
    aggregatetype VARCHAR(50) NOT NULL,
    aggregateid   CHAR(36)    NOT NULL,
    type          VARCHAR(80) NOT NULL,
    payload       JSON        NOT NULL,
    created_at    DATETIME(6) NOT NULL,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT chk_outbox_events_aggregatetype CHECK (aggregatetype IN ('auth.users', 'auth.security-audit')),
    CONSTRAINT chk_outbox_events_type CHECK (type IN (
        'UserInvited', 'UserActivated', 'UserRenamed', 'UserRoleChanged', 'UserSuspended', 'UserDeactivated', 'UserReactivated',
        'UserPasswordChangeRequired', 'UserPasswordChanged', 'UserPasswordReset', 'UserSecondFactorEnrolled', 'UserSecondFactorReset',
        'UserSessionsRevoked', 'SignInCompleted', 'SignInFailed', 'PasswordResetRequested', 'RecoveryCodesRegenerated', 'SignInUnlocked',
        'InvitationResent', 'RefreshTokenReuseDetected'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_outbox_events_created_at ON auth_outbox.outbox_events (created_at);
