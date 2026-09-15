package com.ClinicaDeYmid.auth_service.domain.secondfactor;

public sealed interface SecondFactorProof {

    record Totp(String code) implements SecondFactorProof {
    }

    record RecoveryCode(String code) implements SecondFactorProof {
    }

    default AuthenticationMethod method() {
        return switch (this) {
            case Totp totp -> AuthenticationMethod.TOTP;
            case RecoveryCode recoveryCode -> AuthenticationMethod.RECOVERY_CODE;
        };
    }
}
