ALTER TABLE auth_outbox.outbox_events
    DROP CHECK chk_outbox_events_type,
    ADD CONSTRAINT chk_outbox_events_type CHECK (type IN (
        'UserInvited', 'UserActivated', 'UserRenamed', 'UserRoleChanged', 'UserSuspended', 'UserDeactivated', 'UserReactivated',
        'UserPasswordChangeRequired', 'UserPasswordChanged', 'UserPasswordReset', 'UserSecondFactorEnrolled', 'UserSecondFactorReset',
        'UserSessionsRevoked', 'SignInCompleted', 'SignInFailed', 'PasswordResetRequested', 'RecoveryCodesRegenerated', 'SignInUnlocked',
        'InvitationResent', 'RefreshTokenReuseDetected', 'SessionClosed'));
