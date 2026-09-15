package com.ClinicaDeYmid.auth_service.domain.secondfactor;

import com.ClinicaDeYmid.auth_service.domain.user.User;

public interface TotpAuthenticator {

    TotpEnrollment enroll(User user);

    boolean verify(User user, String code);

    void forget(User user);

    record TotpEnrollment(String otpauthUrl, String qrPngBase64) {
    }
}
