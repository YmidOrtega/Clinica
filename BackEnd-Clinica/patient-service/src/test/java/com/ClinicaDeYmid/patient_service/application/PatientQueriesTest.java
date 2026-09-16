package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.Affiliation;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientException;
import com.ClinicaDeYmid.patient_service.domain.PatientFixtures;
import com.ClinicaDeYmid.patient_service.domain.PatientRegistration;
import com.ClinicaDeYmid.patient_service.domain.Patients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientQueriesTest {

    private final InMemoryPatients patients = new InMemoryPatients();
    private final PatientCommandsTest.StubPayers payers = new PatientCommandsTest.StubPayers();
    private final PatientQueries queries = new PatientQueries(patients, payers, uuid -> List.of());

    @Test
    void includesThePayerLookupInTheDetails() {
        Patient patient = patients.save(Patient.register(PatientFixtures.adultRegistration(), PatientFixtures.today()));
        payers.answer = new PayerLookup.Unavailable();

        PatientQueries.PatientDetails details = queries.get(patient.uuid());

        assertThat(details.patient()).isEqualTo(patient);
        assertThat(details.payer()).isEqualTo(new PayerLookup.Unavailable());
    }

    @Test
    void skipsTheLookupForUninsuredPatients() {
        Patient patient = patients.save(Patient.register(new PatientRegistration(PatientFixtures.cedula(), PatientFixtures.adult(),
                PatientFixtures.contact(), null, Affiliation.uninsured(), PatientFixtures.residence()), PatientFixtures.today()));

        assertThat(queries.get(patient.uuid()).payer()).isEqualTo(new PayerLookup.NotAffiliated());
        assertThat(payers.requestedPayers).isEmpty();
    }

    @Test
    void reportsMissingPatients() {
        assertThatThrownBy(() -> queries.get(UUID.randomUUID())).isInstanceOf(PatientException.NotFound.class);
        assertThatThrownBy(() -> queries.history(UUID.randomUUID())).isInstanceOf(PatientException.NotFound.class);
    }

    @Test
    void normalizesNamePrefixes() {
        queries.searchByName("  restrepo   gómez ", " ", PageRequest.of(0, 10));

        assertThat(patients.lastSearch()).isEqualTo(new Patients.NameQuery("restrepo gómez", null));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "r", "rest%", "o'_", "gómez; drop", "\"ana\""})
    void rejectsLastNamePrefixesThatAreTooShortOrCouldActAsWildcards(String lastNames) {
        assertThatThrownBy(() -> queries.searchByName(lastNames, null, PageRequest.of(0, 10)))
                .isInstanceOf(PatientException.InvalidData.class);
    }

    @Test
    void validatesTheOptionalFirstNamesPrefix() {
        assertThatThrownBy(() -> queries.searchByName("restrepo", "a%", PageRequest.of(0, 10)))
                .isInstanceOf(PatientException.InvalidData.class);
    }
}
