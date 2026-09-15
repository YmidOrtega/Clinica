package com.ClinicaDeYmid.auth_service.infrastructure.secondfactor;

import com.ClinicaDeYmid.auth_service.domain.secondfactor.RecoveryCodes;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.TotpAuthenticator;
import com.ClinicaDeYmid.commons.openbao.totp.TotpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class SecondFactorConfiguration {

    @Bean
    TotpAuthenticator totpAuthenticator(TotpClient totp, @Value("${clinica.auth.second-factor.issuer:Clinica}") String issuer) {
        return new OpenBaoTotpAuthenticator(totp, issuer);
    }

    @Bean
    RecoveryCodes recoveryCodes(JdbcTemplate jdbc) {
        return new JdbcRecoveryCodes(jdbc);
    }
}
