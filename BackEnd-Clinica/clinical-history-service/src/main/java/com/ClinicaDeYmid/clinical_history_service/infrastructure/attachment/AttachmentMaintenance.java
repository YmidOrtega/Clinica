package com.ClinicaDeYmid.clinical_history_service.infrastructure.attachment;

import com.ClinicaDeYmid.clinical_history_service.application.attachment.AttachmentRetention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class AttachmentMaintenance {

    static final String RETENTION_JOB = "attachment-retention-extension";
    private static final Logger log = LoggerFactory.getLogger(AttachmentMaintenance.class);

    private final S3AttachmentVault vault;
    private final AttachmentStorageProperties properties;
    private final AttachmentRetention retention;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final Clock clock;

    AttachmentMaintenance(S3AttachmentVault vault, AttachmentStorageProperties properties, AttachmentRetention retention,
                          NamedParameterJdbcTemplate jdbc, TransactionOperations transactions, Clock clock) {
        this.vault = vault;
        this.properties = properties;
        this.retention = retention;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void createBucketsWhenAsked() {
        if (properties.createBuckets()) {
            try {
                vault.createBucketsIfMissing();
            } catch (RuntimeException unavailable) {
                log.warn("Could not create attachment buckets at startup; attachments stay unavailable until storage responds");
            }
        }
    }

    @Scheduled(cron = "${clinica.clinical.attachments.retention-cron:0 30 3 * * *}", zone = "America/Bogota")
    public int extendRetentionForRecentCare() {
        Integer extended = transactions.execute(status -> {
            Instant startedAt = Instant.now(clock);
            Instant since = lastCompleted();
            record Target(UUID attachmentId, Instant lastCare) {
            }
            List<Target> targets = jdbc.query("""
                    SELECT a.id AS attachment_id, MAX(e.opened_at) AS last_care
                    FROM clinical_ledger.note_attachments a
                    JOIN patient_references attached ON attached.uuid = a.patient_uuid
                    JOIN patient_references person ON person.uuid = COALESCE(attached.identified_patient_uuid, attached.uuid)
                    JOIN patient_references subject
                         ON subject.uuid = person.uuid OR subject.identified_patient_uuid = person.uuid
                    JOIN clinical_ledger.encounters e ON e.patient_uuid = subject.uuid
                    GROUP BY a.id
                    HAVING MAX(e.opened_at) > :since""", new MapSqlParameterSource("since", Timestamp.from(since)),
                    (row, index) -> new Target(UUID.fromString(row.getString("attachment_id")), row.getTimestamp("last_care").toInstant()));
            for (Target target : targets) {
                vault.extendRetention(target.attachmentId(), retention.retainUntil(target.lastCare()));
            }
            jdbc.update("""
                    INSERT INTO scheduled_job_runs (name, last_completed_at) VALUES (:name, :at)
                    ON DUPLICATE KEY UPDATE last_completed_at = :at""",
                    new MapSqlParameterSource().addValue("name", RETENTION_JOB).addValue("at", Timestamp.from(startedAt)));
            return targets.size();
        });
        log.info("Checked retention of {} attachments of patients with new care", extended);
        return extended == null ? 0 : extended;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "${clinica.clinical.attachments.purge-interval:PT1H}")
    public int purgeUnconfirmedUploads() {
        List<UUID> staged = vault.stagedOlderThan(Instant.now(clock).minus(properties.stagingOrphanAfter()));
        int purged = 0;
        for (UUID attachmentId : staged) {
            boolean referenced = !jdbc.queryForList("SELECT id FROM clinical_workspace.draft_attachments WHERE id = :id",
                    new MapSqlParameterSource("id", attachmentId.toString()), String.class).isEmpty();
            if (!referenced) {
                vault.discardStaged(attachmentId);
                purged++;
            }
        }
        if (purged > 0) {
            log.info("Purged {} unconfirmed attachment uploads", purged);
        }
        return purged;
    }

    private Instant lastCompleted() {
        List<Timestamp> runs = jdbc.queryForList("SELECT last_completed_at FROM scheduled_job_runs WHERE name = :name FOR UPDATE",
                new MapSqlParameterSource("name", RETENTION_JOB), Timestamp.class);
        return runs.isEmpty() ? Instant.EPOCH : runs.getFirst().toInstant();
    }
}
