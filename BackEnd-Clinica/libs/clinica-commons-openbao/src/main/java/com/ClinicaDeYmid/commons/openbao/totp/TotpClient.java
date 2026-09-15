package com.ClinicaDeYmid.commons.openbao.totp;

import com.ClinicaDeYmid.commons.openbao.OpenBaoHttp;
import org.springframework.http.HttpMethod;
import org.springframework.vault.core.VaultOperations;

import java.util.Map;

public class TotpClient {

    private final VaultOperations vault;
    private final String mount;

    public TotpClient(VaultOperations vault, String mount) {
        this.vault = vault;
        this.mount = mount;
    }

    public record Enrollment(String otpauthUrl, String qrPngBase64) {
    }

    public Enrollment enroll(String keyName, String issuer, String accountName) {
        Map<String, Object> data = OpenBaoHttp.exchange(vault, HttpMethod.POST, mount + "/keys/" + keyName, Map.of(
                "generate", true,
                "exported", true,
                "issuer", issuer,
                "account_name", accountName,
                "period", 30,
                "digits", 6,
                "algorithm", "SHA1",
                "skew", 1,
                "qr_size", 256), TotpRejectedException::new);
        return new Enrollment((String) data.get("url"), (String) data.get("barcode"));
    }

    public boolean validate(String keyName, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        try {
            Map<String, Object> data = OpenBaoHttp.exchange(vault, HttpMethod.POST, mount + "/code/" + keyName, Map.of("code", code),
                    TotpRejectedException::new);
            return Boolean.TRUE.equals(data.get("valid"));
        } catch (TotpRejectedException alreadyUsedOrUnknown) {
            return false;
        }
    }

    public void delete(String keyName) {
        OpenBaoHttp.exchange(vault, HttpMethod.DELETE, mount + "/keys/" + keyName, null, TotpRejectedException::new);
    }
}
