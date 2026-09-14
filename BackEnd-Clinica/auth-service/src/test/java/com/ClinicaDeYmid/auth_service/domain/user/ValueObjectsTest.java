package com.ClinicaDeYmid.auth_service.domain.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    @Test
    void emailsAreNormalizedToLowercase() {
        EmailAddress email = new EmailAddress("  Ana.Rojas@Clinica.TEST ");

        assertThat(email.value()).isEqualTo("ana.rojas@clinica.test");
        assertThat(email.localPart()).isEqualTo("ana.rojas");
        assertThat(email).isEqualTo(new EmailAddress("ana.rojas@clinica.test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sin-arroba", "dos@@clinica.test", "espacio en@clinica.test", "ana@localhost", "ana@-clinica.test"})
    void rejectsMalformedEmails(String value) {
        assertThatThrownBy(() -> new EmailAddress(value)).isInstanceOf(UserException.InvalidData.class);
    }

    @Test
    void namesCollapseWhitespaceAndExposeTheirWords() {
        FullName name = new FullName("  María   José O'Neil-Díaz ");

        assertThat(name.value()).isEqualTo("María José O'Neil-Díaz");
        assertThat(name.words()).containsExactly("maría", "josé", "o", "neil", "díaz");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Al", "Ana123", "<script>"})
    void rejectsInvalidNames(String value) {
        assertThatThrownBy(() -> new FullName(value)).isInstanceOf(UserException.InvalidData.class);
    }
}
