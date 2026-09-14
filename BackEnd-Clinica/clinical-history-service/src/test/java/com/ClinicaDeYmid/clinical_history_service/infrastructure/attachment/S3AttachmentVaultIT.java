package com.ClinicaDeYmid.clinical_history_service.infrastructure.attachment;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.Attachment;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.EncryptedContentUnreadableException;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.EncryptionConfiguration;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.transit.TransitConfiguration;
import com.ClinicaDeYmid.clinical_history_service.support.MinioTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.OpenBaoTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.SampleFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({S3AttachmentVault.class, AttachmentStorageConfiguration.class, EncryptionConfiguration.class, TransitConfiguration.class, OpenBaoTestContainer.class, ClockConfiguration.class,
        MySqlTestContainer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class S3AttachmentVaultIT {

    private static final String STAGING = "vault-it-staging";
    private static final String ARCHIVE = "vault-it-archive";

    @Autowired
    private S3AttachmentVault vault;

    @Autowired
    private S3Client s3;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        MinioTestContainer.register(registry, STAGING, ARCHIVE);
    }

    @BeforeEach
    void buckets() {
        vault.createBucketsIfMissing();
    }

    @Test
    void storesOnlyEncryptedBytesAndArchivesThemUnderComplianceRetention() {
        byte[] content = SampleFiles.pdf("Ecografía abdominal: colelitiasis");
        Attachment attachment = Attachment.inspect("eco.pdf", content);
        Instant retainUntil = Instant.now().plus(3650, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        vault.stage(UUID.randomUUID(), attachment, content);
        byte[] staged = s3.getObjectAsBytes(request -> request.bucket(STAGING).key("attachments/" + attachment.id())).asByteArray();
        vault.archive(attachment, retainUntil);
        vault.discardStaged(attachment.id());

        assertThat(new String(staged, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("colelitiasis").doesNotContain("%PDF");
        assertThat(vault.openArchived(attachment)).isEqualTo(content);
        assertThat(vault.retainedUntil(attachment.id())).isEqualTo(retainUntil);
        assertThatThrownBy(() -> s3.headObject(request -> request.bucket(STAGING).key("attachments/" + attachment.id())))
                .isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void archivedVersionsCannotBeDeletedOrShortenedEvenWithFullStorageCredentials() {
        byte[] content = SampleFiles.png();
        Attachment attachment = Attachment.inspect("rx.png", content);
        Instant retainUntil = Instant.now().plus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        vault.stage(UUID.randomUUID(), attachment, content);
        vault.archive(attachment, retainUntil);
        String key = "attachments/" + attachment.id();
        List<ObjectVersion> versions = s3.listObjectVersions(request -> request.bucket(ARCHIVE).prefix(key)).versions();

        assertThatThrownBy(() -> s3.deleteObject(request -> request.bucket(ARCHIVE).key(key).versionId(versions.getFirst().versionId())))
                .isInstanceOf(S3Exception.class);
        assertThatThrownBy(() -> s3.putObjectRetention(request -> request.bucket(ARCHIVE).key(key)
                .retention(retention -> retention.mode("COMPLIANCE").retainUntilDate(retainUntil.minus(30, ChronoUnit.DAYS)))))
                .isInstanceOf(S3Exception.class);

        vault.extendRetention(attachment.id(), retainUntil.minus(10, ChronoUnit.DAYS));
        assertThat(vault.retainedUntil(attachment.id())).isEqualTo(retainUntil);
        vault.extendRetention(attachment.id(), retainUntil.plus(10, ChronoUnit.DAYS));
        assertThat(vault.retainedUntil(attachment.id())).isEqualTo(retainUntil.plus(10, ChronoUnit.DAYS));
    }

    @Test
    void detectsAReplacedObjectEvenThoughTheLockedOriginalSurvives() {
        byte[] content = SampleFiles.pdf("Biopsia benigna");
        Attachment attachment = Attachment.inspect("biopsia.pdf", content);
        vault.stage(UUID.randomUUID(), attachment, content);
        vault.archive(attachment, Instant.now().plus(30, ChronoUnit.DAYS));
        String key = "attachments/" + attachment.id();

        s3.putObject(request -> request.bucket(ARCHIVE).key(key), RequestBody.fromBytes(SampleFiles.pdf("Biopsia maligna")));

        assertThatThrownBy(() -> vault.openArchived(attachment)).isInstanceOf(EncryptedContentUnreadableException.class);
        assertThat(s3.listObjectVersions(request -> request.bucket(ARCHIVE).prefix(key)).versions()).hasSize(2);
    }

    @Test
    void reportsStagedUploadsReadyForPurgeAndRefusesArchivesWithoutObjectLock() {
        byte[] content = SampleFiles.png();
        Attachment attachment = Attachment.inspect("borrador.png", content);
        vault.stage(UUID.randomUUID(), attachment, content);

        assertThat(vault.stagedOlderThan(Instant.now().plusSeconds(5))).contains(attachment.id());
        assertThat(vault.stagedOlderThan(Instant.now().minusSeconds(3600))).doesNotContain(attachment.id());

        S3AttachmentVault unlocked = new S3AttachmentVault(s3, new AttachmentStorageProperties(null, "us-east-1", null, null, STAGING, STAGING,
                java.time.Period.ofYears(15), false, java.time.Duration.ofHours(1), java.time.Duration.ofSeconds(30)), null);
        assertThatThrownBy(() -> unlocked.archive(attachment, Instant.now().plusSeconds(60)))
                .isInstanceOf(RuntimeException.class)
                .satisfies(failure -> assertThat(failure).isNotInstanceOf(ClinicalException.AttachmentStorageUnavailable.class)
                        .hasMessageContaining("Object Lock"));
    }
}
