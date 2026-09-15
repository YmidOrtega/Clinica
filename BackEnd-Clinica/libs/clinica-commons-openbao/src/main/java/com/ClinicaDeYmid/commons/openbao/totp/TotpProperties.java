package com.ClinicaDeYmid.commons.openbao.totp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("clinica.openbao.totp")
public record TotpProperties(@DefaultValue("totp") String mount) {
}
