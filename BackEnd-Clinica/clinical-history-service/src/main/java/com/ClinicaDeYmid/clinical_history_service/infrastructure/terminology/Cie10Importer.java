package com.ClinicaDeYmid.clinical_history_service.infrastructure.terminology;

import com.ClinicaDeYmid.clinical_history_service.domain.terminology.TerminologyRelease;
import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class Cie10Importer {

    private static final Logger log = LoggerFactory.getLogger(Cie10Importer.class);

    public record ImportResult(TerminologyRelease release, boolean created, List<String> warnings) {
    }

    static final class ReleaseNotFound extends DomainException {
        ReleaseNotFound() {
            super(ErrorCategory.NOT_FOUND, "TERMINOLOGY_RELEASE_NOT_FOUND", "No se encontró la versión del catálogo CIE-10");
        }
    }

    static final class VersionConflict extends DomainException {
        VersionConflict(String version) {
            super(ErrorCategory.CONFLICT, "TERMINOLOGY_VERSION_CONFLICT",
                    "Ya existe un catálogo CIE-10 con fecha " + version + " y contenido distinto; revisa el archivo");
        }
    }

    private final JdbcConceptCatalog catalog;
    private final TransactionOperations transactions;
    private final Clock clock;

    Cie10Importer(JdbcConceptCatalog catalog, TransactionOperations transactions, Clock clock) {
        this.catalog = catalog;
        this.transactions = transactions;
        this.clock = clock;
    }

    public ImportResult importWorkbook(String sourceFile, byte[] workbook, UUID importedBy) {
        String checksum = sha256(workbook);
        var existing = catalog.releaseWithChecksum(checksum);
        if (existing.isPresent()) {
            return new ImportResult(existing.get(), false, List.of());
        }
        Cie10Workbook.Parsed parsed = Cie10Workbook.parse(workbook);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now(clock);
        String fileName = sourceFile == null || sourceFile.isBlank() ? "cie10.xlsx" : sourceFile.strip();
        transactions.executeWithoutResult(status -> {
            if (catalog.versionExists(parsed.version())) {
                throw new VersionConflict(parsed.version());
            }
            catalog.insertRelease(id, parsed.version(), checksum, fileName.length() > 255 ? fileName.substring(0, 255) : fileName,
                    parsed.concepts(), importedBy, now);
        });
        log.info("Imported CIE-10 release {} with {} codes and {} warnings", parsed.version(), parsed.concepts().size(),
                parsed.warnings().size());
        parsed.warnings().forEach(warning -> log.warn("CIE-10 {}: {}", parsed.version(), warning));
        return new ImportResult(release(id), true, parsed.warnings());
    }

    public TerminologyRelease activate(UUID releaseId, UUID activatedBy) {
        if (!Boolean.TRUE.equals(transactions.execute(status -> catalog.activate(releaseId, activatedBy, Instant.now(clock))))) {
            throw new ReleaseNotFound();
        }
        log.info("CIE-10 release {} activated by {}", releaseId, activatedBy);
        return release(releaseId);
    }

    public List<TerminologyRelease> releases() {
        return catalog.releases();
    }

    private TerminologyRelease release(UUID id) {
        return catalog.releases().stream().filter(release -> release.id().equals(id)).findFirst().orElseThrow(ReleaseNotFound::new);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
