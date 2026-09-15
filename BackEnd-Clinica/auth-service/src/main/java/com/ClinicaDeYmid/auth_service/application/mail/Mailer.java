package com.ClinicaDeYmid.auth_service.application.mail;

public interface Mailer {

    void send(OutgoingMail mail);

    record OutgoingMail(String to, String subject, String body) {
    }
}
