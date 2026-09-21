package com.ClinicaDeYmid.admissions_service.infrastructure.messaging;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionEvent;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.Cause;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import com.ClinicaDeYmid.admissions_service.domain.Discharge;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.support.ProducerContract;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdmissionEventContractTest {

    private static final Instant NOW = Instant.parse("2026-09-21T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void theRegistrationCarriesTheSummaryOfTheEpisode() {
        ConfigurationService service = emergency();
        Admission admission = Admission.register("ADM-2026-000001", UUID.randomUUID(), service, Cause.ILLNESS,
                null, null, CLOCK);

        String json = json(only(admission.pullEvents()), admission);

        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(json)).isEmpty();
        assertThat(json).contains("\"type\":\"AdmissionRegistered\"", "\"schemaVersion\":1",
                "\"kind\":\"EMERGENCY\"", "\"number\":\"ADM-2026-000001\"",
                "\"configurationServiceUuid\":\"" + service.uuid() + "\"");
    }

    @Test
    void movingBetweenServicesAndBedsCarriesWhereItCameFrom() {
        Admission admission = emergencyEpisode();
        UUID ward = UUID.randomUUID();
        ConfigurationService inpatient = ConfigurationService.configure(
                ServiceType.define("Hospitalización", AdmissionKind.INPATIENT), Location.define("Piso 3"));
        UUID emergencyService = admission.lastPhase().configurationService().uuid();

        admission.activate(CLOCK);
        admission.moveTo(inpatient, "Requiere hospitalización", CLOCK);
        String moved = json(only(admission.pullEvents()), admission);

        admission.assignBed(ward);
        String assigned = json(only(admission.pullEvents()), admission);

        admission.releaseBed();
        String released = json(only(admission.pullEvents()), admission);

        assertThat(List.of(moved, assigned, released)).allSatisfy(event ->
                assertThat(ProducerContract.ADMISSION_EVENTS.breaches(event)).isEmpty());
        assertThat(moved).contains("\"previousServiceUuid\":\"" + emergencyService + "\"",
                "\"reason\":\"Requiere hospitalización\"", "\"kind\":\"INPATIENT\"");
        assertThat(assigned).contains("\"type\":\"AdmissionBedAssigned\"", "\"bedUuid\":\"" + ward + "\"");
        assertThat(released).contains("\"type\":\"AdmissionBedReleased\"", "\"bedUuid\":\"" + ward + "\"");
    }

    @Test
    void cancellingTravelsWithTheClosedEpisodeAndItsReason() {
        Admission admission = emergencyEpisode();

        admission.cancel("Se registró dos veces", CLOCK);
        String json = json(only(admission.pullEvents()), admission);

        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(json)).isEmpty();
        assertThat(json).contains("\"type\":\"AdmissionCancelled\"", "\"status\":\"CANCELLED\"",
                "\"reason\":\"Se registró dos veces\"");
    }

    @Test
    void aPendingCoverageTravelsWithItsStatusAndDetail() {
        Admission admission = emergencyEpisode();
        admission.assess(Coverage.unknown("contracting-service no respondió", UUID.randomUUID(), NOW));
        admission.pullEvents();

        String json = json(new AdmissionEvent.CoveragePending(Coverage.Code.UNKNOWN,
                "contracting-service no respondió"), admission);

        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(json)).isEmpty();
        assertThat(json).contains("\"coverage\":\"UNKNOWN\"", "\"coverageDetail\":\"contracting-service no respondió\"");
    }

    @Test
    void aDischargeTravelsWithItsTypeAndAClosedEpisode() {
        Admission admission = emergencyEpisode();
        admission.activate(CLOCK);
        admission.discharge(new Discharge.Death(NOW, NOW.minusSeconds(600), "CD-2026-0001"));

        String json = json(only(admission.pullEvents()), admission);

        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(json)).isEmpty();
        assertThat(json).contains("\"discharge\":\"DEATH\"", "\"status\":\"DISCHARGED\"")
                .doesNotContain("CD-2026-0001");
    }

    @Test
    void theSchemaRejectsEventsThatBreakTheContract() {
        String json = json(new AdmissionEvent.Registered(), emergencyEpisode());

        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(json.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
                .isNotEmpty();
        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(
                json.replace("AdmissionRegistered", "AdmissionDischarged"))).isNotEmpty();
        assertThat(ProducerContract.ADMISSION_EVENTS.breaches(
                json.replace("\"data\":{", "\"data\":{\"diagnosis\":\"J18.9\","))).isNotEmpty();
    }

    private static AdmissionEvent only(List<AdmissionEvent> events) {
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private static String json(AdmissionEvent event, Admission admission) {
        return AdmissionEventJson.write(AdmissionEventMessage.of(event, admission, UUID.randomUUID(), NOW,
                "4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    private static Admission emergencyEpisode() {
        Admission admission = Admission.register("ADM-2026-000001", UUID.randomUUID(), emergency(), Cause.ILLNESS,
                null, null, CLOCK);
        admission.pullEvents();
        return admission;
    }

    private static ConfigurationService emergency() {
        return ConfigurationService.configure(
                ServiceType.define("Urgencias", AdmissionKind.EMERGENCY), Location.define("Piso 1"));
    }
}
