package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.TriageLevel;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.json.NoteContentJsonModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteContentReaderTest {

    private final ObjectMapper mapper = JsonMapper.builder().addModule(new NoteContentJsonModule()).build();
    private final NoteContentReader reader = new NoteContentReader(mapper);

    @Test
    void readsEachNoteTypeByItsDiscriminator() throws Exception {
        UUID amended = UUID.randomUUID();

        assertThat(read("{\"type\": \"TRIAGE\", \"level\": \"II\", \"reason\": \"Dolor torácico\"}"))
                .isEqualTo(new NoteContent.Triage(TriageLevel.II, "Dolor torácico", null));
        assertThat(read("{\"type\": \"ADDENDUM\", \"amendsNoteId\": \"" + amended + "\", \"text\": \"Aclaración\"}"))
                .isEqualTo(new NoteContent.Addendum(amended, "Aclaración"));
    }

    @Test
    void writesTheDiscriminatorOnce() throws Exception {
        String json = mapper.writerFor(NoteContent.class).writeValueAsString(new NoteContent.Nursing("Tranquilo", "Curación"));

        assertThat(mapper.readTree(json).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("type", "observations", "careProvided");
        assertThat(mapper.readTree(json).get("type").asText()).isEqualTo("NURSING");
    }

    @Test
    void rejectsUnknownTypesAndFieldsWithTheirName() {
        assertThatThrownBy(() -> read("{\"type\": \"SURGERY\"}"))
                .isInstanceOf(ClinicalException.InvalidData.class)
                .hasMessageContaining("content.type");
        assertThatThrownBy(() -> read("{\"level\": \"II\"}"))
                .isInstanceOf(ClinicalException.InvalidData.class)
                .hasMessageContaining("content.type");
        assertThatThrownBy(() -> read("{\"type\": \"NURSING\", \"observations\": \"Tranquilo\", \"physicalExm\": \"Normal\"}"))
                .isInstanceOf(ClinicalException.InvalidData.class)
                .hasMessageContaining("content.physicalExm");
    }

    @Test
    void reportsInvalidValuesAndDomainLimits() {
        assertThatThrownBy(() -> read("{\"type\": \"TRIAGE\", \"level\": \"VI\"}"))
                .isInstanceOf(ClinicalException.InvalidData.class)
                .hasMessageContaining("content.level");
        assertThatThrownBy(() -> read("{\"type\": \"TRIAGE\", \"reason\": \"" + "x".repeat(501) + "\"}"))
                .isInstanceOf(ClinicalException.InvalidData.class)
                .hasMessageContaining("'reason'");
        assertThatThrownBy(() -> reader.read(null))
                .isInstanceOf(ClinicalException.InvalidData.class)
                .hasMessageContaining("content");
    }

    private NoteContent read(String json) throws Exception {
        return reader.read(mapper.readTree(json));
    }
}
