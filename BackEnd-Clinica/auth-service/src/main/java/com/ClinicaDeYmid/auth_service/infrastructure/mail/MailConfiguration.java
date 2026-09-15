package com.ClinicaDeYmid.auth_service.infrastructure.mail;

import com.ClinicaDeYmid.auth_service.application.mail.AccountLinks;
import com.ClinicaDeYmid.auth_service.application.mail.AccountMailing;
import com.ClinicaDeYmid.auth_service.application.mail.Mailer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
public class MailConfiguration {

    @Bean
    Mailer mailer(JavaMailSender sender, @Value("${clinica.auth.mail.from}") String from) {
        return new SmtpMailer(sender, from);
    }

    @Bean
    AccountLinks accountLinks(@Value("${clinica.auth.frontend.activation-url}") String activationUrl,
                              @Value("${clinica.auth.frontend.password-reset-url}") String passwordResetUrl) {
        return new AccountLinks(activationUrl, passwordResetUrl);
    }

    @Bean
    MailDispatchSchedule mailDispatchSchedule(AccountMailing mailing) {
        return new MailDispatchSchedule(mailing);
    }

    static class MailDispatchSchedule {

        private final AccountMailing mailing;

        MailDispatchSchedule(AccountMailing mailing) {
            this.mailing = mailing;
        }

        @Scheduled(initialDelayString = "PT10S", fixedDelayString = "${clinica.auth.mail.dispatch-interval:PT5S}")
        void dispatch() {
            mailing.dispatchDue();
        }
    }
}
