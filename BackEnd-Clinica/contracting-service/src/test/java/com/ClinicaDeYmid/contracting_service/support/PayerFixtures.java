package com.ClinicaDeYmid.contracting_service.support;

import com.ClinicaDeYmid.contracting_service.domain.ContactInfo;
import com.ClinicaDeYmid.contracting_service.domain.Nit;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import com.ClinicaDeYmid.contracting_service.domain.PayerRegistration;
import com.ClinicaDeYmid.contracting_service.domain.PayerType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class PayerFixtures {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-15T14:00:00Z"), ZoneOffset.UTC);

    private PayerFixtures() {
    }

    public static PayerRegistration registration() {
        return registration("901234567");
    }

    public static PayerRegistration registration(String nit) {
        return new PayerRegistration("Salud Total EPS S.A.", new Nit(nit), PayerType.EPS, "EPS002",
                new ContactInfo("Calle 100 # 7-33", "+576017429000", "radicacion@saludtotal.test"));
    }

    public static Payer active() {
        return Payer.register(registration(), CLOCK);
    }
}
