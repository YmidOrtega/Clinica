package com.ClinicaDeYmid.auth_service.infrastructure.secondfactor;

import com.ClinicaDeYmid.auth_service.domain.secondfactor.TotpAuthenticator;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.commons.openbao.totp.TotpClient;

class OpenBaoTotpAuthenticator implements TotpAuthenticator {

    private final TotpClient totp;
    private final String issuer;

    OpenBaoTotpAuthenticator(TotpClient totp, String issuer) {
        this.totp = totp;
        this.issuer = issuer;
    }

    @Override
    public TotpEnrollment enroll(User user) {
        TotpClient.Enrollment enrollment = totp.enroll(keyOf(user), issuer, user.email().value());
        return new TotpEnrollment(enrollment.otpauthUrl(), enrollment.qrPngBase64());
    }

    @Override
    public boolean verify(User user, String code) {
        return totp.validate(keyOf(user), code == null ? null : code.strip());
    }

    @Override
    public void forget(User user) {
        totp.delete(keyOf(user));
    }

    static String keyOf(User user) {
        return "staff-" + user.uuid();
    }
}
