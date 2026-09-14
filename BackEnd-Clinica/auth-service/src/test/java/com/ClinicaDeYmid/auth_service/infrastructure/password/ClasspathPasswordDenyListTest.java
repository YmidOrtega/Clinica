package com.ClinicaDeYmid.auth_service.infrastructure.password;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathPasswordDenyListTest {

    @Test
    void loadsTheBundledListOfCommonPasswords() {
        ClasspathPasswordDenyList denyList = new ClasspathPasswordDenyList(List.of(new ClassPathResource("passwords/ncsc-100k-common.txt")));

        assertThat(denyList.size()).isGreaterThan(40_000);
        assertThat(denyList.contains("password")).isTrue();
        assertThat(denyList.contains("iloveyou")).isTrue();
        assertThat(denyList.contains("un caballo verde toma café")).isFalse();
    }

    @Test
    void combinesListsIgnoringCaseAndBlankLines() {
        ClasspathPasswordDenyList denyList = new ClasspathPasswordDenyList(List.of(
                new ByteArrayResource("Clinica2026\n\n  Bogota123  \n".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayResource("medellin2026\n".getBytes(StandardCharsets.UTF_8))));

        assertThat(denyList.contains("clinica2026")).isTrue();
        assertThat(denyList.contains("bogota123")).isTrue();
        assertThat(denyList.contains("medellin2026")).isTrue();
        assertThat(denyList.size()).isEqualTo(3);
    }

    @Test
    void refusesToStartWithAnEmptyList() {
        assertThatThrownBy(() -> new ClasspathPasswordDenyList(List.of(new ByteArrayResource(new byte[0]))))
                .hasMessageContaining("empty");
    }
}
