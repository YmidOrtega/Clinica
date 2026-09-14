package com.ClinicaDeYmid.commons.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityTagsTest {

    @Test
    void formatsVersionsAsStrongTags() {
        assertThat(EntityTags.of(7)).isEqualTo("\"7\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"7\"", "W/\"7\"", "7", " \"7\" "})
    void readsTheVersionFromIfMatch(String header) {
        assertThat(EntityTags.requiredVersion(header)).isEqualTo(7);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"*", "  "})
    void requiresAConcreteVersion(String header) {
        assertThatThrownBy(() -> EntityTags.requiredVersion(header)).isInstanceOf(EntityTags.VersionRequired.class);
    }

    @Test
    void rejectsMalformedTags() {
        assertThatThrownBy(() -> EntityTags.requiredVersion("\"abc\"")).isInstanceOf(EntityTags.MalformedVersion.class);
    }
}
