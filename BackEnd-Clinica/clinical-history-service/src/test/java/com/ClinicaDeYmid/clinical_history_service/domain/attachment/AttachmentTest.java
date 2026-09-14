package com.ClinicaDeYmid.clinical_history_service.domain.attachment;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.support.SampleFiles;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentTest {

    @Test
    void recognisesPdfJpegAndPngByTheirContentNotTheirName() {
        assertThat(Attachment.inspect("resultado.txt", SampleFiles.pdf("glucemia")).mediaType()).isEqualTo(AttachmentMediaType.PDF);
        assertThat(Attachment.inspect("foto", SampleFiles.png()).mediaType()).isEqualTo(AttachmentMediaType.PNG);
        assertThat(Attachment.inspect("rx.jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16}).mediaType())
                .isEqualTo(AttachmentMediaType.JPEG);
        assertThatThrownBy(() -> Attachment.inspect("script.pdf", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ClinicalException.InvalidAttachment.class)
                .hasMessageContaining("PDF, JPEG o PNG");
        assertThatThrownBy(() -> Attachment.inspect("vacio.pdf", new byte[0])).hasMessageContaining("vacío");
        assertThatThrownBy(() -> Attachment.inspect("grande.pdf", new byte[(int) Attachment.MAX_BYTES + 1])).hasMessageContaining("20 MB");
    }

    @Test
    void keepsASafeFileNameWithTheRealExtension() {
        assertThat(Attachment.inspect("../../etc/passwd", SampleFiles.pdf("x")).fileName()).isEqualTo("passwd.pdf");
        assertThat(Attachment.inspect("C:\\\\Informes\\\\Eco\"abd<>.exe", SampleFiles.png()).fileName()).isEqualTo("Eco_abd_.png");
        assertThat(Attachment.inspect("   ", SampleFiles.png()).fileName()).isEqualTo("anexo.png");
    }

    @Test
    void verifiesContentAgainstItsDigest() {
        byte[] content = SampleFiles.pdf("hemograma");
        Attachment attachment = Attachment.inspect("hemograma.pdf", content);
        byte[] altered = content.clone();
        altered[altered.length - 3] ^= 1;

        assertThat(attachment.sha256()).isEqualTo(Attachment.sha256Of(content));
        assertThat(attachment.matches(content)).isTrue();
        assertThat(attachment.matches(altered)).isFalse();
    }
}
