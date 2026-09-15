package com.ClinicaDeYmid.commons.openbao.totp;

import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import com.ClinicaDeYmid.commons.openbao.testing.TotpCodes;
import com.ClinicaDeYmid.commons.openbao.transit.OpenBaoUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

class TotpClientIT {

    private final TotpClient totp = new TotpClient(OpenBaoTestContainer.template(), "totp");

    @Test
    void enrollsAKeyWhoseCodesValidateOnlyOnce() {
        String key = "staff-" + UUID.randomUUID();

        TotpClient.Enrollment enrollment = totp.enroll(key, "Clinica", "ana@clinica.test");
        String code = TotpCodes.at(enrollment.otpauthUrl(), Instant.now());

        assertThat(enrollment.otpauthUrl()).startsWith("otpauth://totp/Clinica:ana@clinica.test?").contains("secret=");
        assertThat(Base64.getDecoder().decode(enrollment.qrPngBase64())).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(totp.validate(key, code)).isTrue();
        assertThat(totp.validate(key, code)).isFalse();
        assertThat(totp.validate(key, "12345")).isFalse();
    }

    @Test
    void reEnrollingReplacesTheSecret() {
        String key = "staff-" + UUID.randomUUID();
        TotpClient.Enrollment first = totp.enroll(key, "Clinica", "ana@clinica.test");
        TotpClient.Enrollment second = totp.enroll(key, "Clinica", "ana@clinica.test");
        Instant now = Instant.now();
        String oldCode = TotpCodes.at(first.otpauthUrl(), now);
        assumeThat(oldCode).isNotEqualTo(TotpCodes.at(second.otpauthUrl(), now));

        assertThat(totp.validate(key, oldCode)).isFalse();
        assertThat(totp.validate(key, TotpCodes.at(second.otpauthUrl(), now))).isTrue();
    }

    @Test
    void aDeletedKeyValidatesNothing() {
        String key = "staff-" + UUID.randomUUID();
        TotpClient.Enrollment enrollment = totp.enroll(key, "Clinica", "ana@clinica.test");

        totp.delete(key);

        assertThat(totp.validate(key, TotpCodes.at(enrollment.otpauthUrl(), Instant.now()))).isFalse();
    }

    @Test
    void anUnreachableServerIsReportedAsUnavailable() {
        TotpClient unreachable = new TotpClient(new VaultTemplate(VaultEndpoint.from(URI.create("http://127.0.0.1:1")),
                new TokenAuthentication("root")), "totp");

        assertThatThrownBy(() -> unreachable.validate("staff-x", "123456")).isInstanceOf(OpenBaoUnavailableException.class);
    }
}
