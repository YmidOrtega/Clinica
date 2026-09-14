package com.ClinicaDeYmid.clinical_history_service.infrastructure.terminology;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.CodedConcept;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.Concept;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.ConceptCatalog;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.TerminologyRelease;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Repository
class JdbcConceptCatalog implements ConceptCatalog {

    static final String CIE10 = "CIE10";

    private static final Pattern CODE_PREFIX = Pattern.compile("^[A-Za-z][0-9]{0,2}[0-9Xx]?$");
    private static final String ACTIVE_RELEASE = """
            SELECT release_id FROM terminology_activations WHERE code_system = :system ORDER BY id DESC LIMIT 1""";
    private static final RowMapper<Concept> CONCEPT = (row, index) -> new Concept(row.getString("code"), row.getString("display"),
            row.getString("category_code"), row.getString("category_display"), row.getInt("chapter"), row.getString("chapter_display"));

    private final NamedParameterJdbcTemplate jdbc;

    JdbcConceptCatalog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CodedConcept> resolve(String code) {
        return jdbc.query("""
                SELECT c.code, c.display, r.version
                FROM terminology_concepts c
                JOIN terminology_releases r ON r.id = c.release_id
                WHERE c.release_id = (%s) AND c.code = :code""".formatted(ACTIVE_RELEASE),
                new MapSqlParameterSource().addValue("system", CIE10).addValue("code", normalize(code)),
                (row, index) -> new CodedConcept(row.getString("code"), row.getString("display"), row.getString("version")))
                .stream().findFirst()
                .or(() -> {
                    requireActiveRelease();
                    return Optional.empty();
                });
    }

    @Override
    public List<Concept> search(String query, int limit) {
        requireActiveRelease();
        String term = query == null ? "" : query.strip();
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("system", CIE10).addValue("limit", limit);
        if (CODE_PREFIX.matcher(term.replace(".", "")).matches()) {
            return jdbc.query("SELECT * FROM terminology_concepts WHERE release_id = (" + ACTIVE_RELEASE
                    + ") AND code LIKE :prefix ORDER BY code LIMIT :limit", parameters.addValue("prefix", normalize(term) + "%"), CONCEPT);
        }
        return jdbc.query("SELECT * FROM terminology_concepts WHERE release_id = (" + ACTIVE_RELEASE
                + ") AND display LIKE :term ESCAPE '!' ORDER BY CHAR_LENGTH(display), code LIMIT :limit",
                parameters.addValue("term", "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"), CONCEPT);
    }

    Optional<TerminologyRelease> releaseWithChecksum(String checksum) {
        return releases("WHERE r.code_system = :system AND r.checksum = :checksum", Map.of("system", CIE10, "checksum", checksum))
                .stream().findFirst();
    }

    boolean versionExists(String version) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM terminology_releases WHERE code_system = :system AND version = :version",
                Map.of("system", CIE10, "version", version), Integer.class);
        return count != null && count > 0;
    }

    List<TerminologyRelease> releases() {
        return releases("WHERE r.code_system = :system", Map.of("system", CIE10));
    }

    void insertRelease(UUID id, String version, String checksum, String sourceFile, List<Concept> concepts, UUID importedBy, Instant now) {
        jdbc.update("""
                INSERT INTO terminology_releases (id, code_system, version, checksum, source_file, concept_count, imported_at, imported_by)
                VALUES (:id, :system, :version, :checksum, :sourceFile, :count, :importedAt, :importedBy)""",
                new MapSqlParameterSource()
                        .addValue("id", id.toString())
                        .addValue("system", CIE10)
                        .addValue("version", version)
                        .addValue("checksum", checksum)
                        .addValue("sourceFile", sourceFile)
                        .addValue("count", concepts.size())
                        .addValue("importedAt", Timestamp.from(now))
                        .addValue("importedBy", importedBy.toString()));
        for (int from = 0; from < concepts.size(); from += 1000) {
            jdbc.batchUpdate("""
                    INSERT INTO terminology_concepts (release_id, code, display, category_code, category_display, chapter, chapter_display)
                    VALUES (:releaseId, :code, :display, :categoryCode, :categoryDisplay, :chapter, :chapterDisplay)""",
                    concepts.subList(from, Math.min(from + 1000, concepts.size())).stream()
                            .map(concept -> new MapSqlParameterSource()
                                    .addValue("releaseId", id.toString())
                                    .addValue("code", concept.code())
                                    .addValue("display", concept.display())
                                    .addValue("categoryCode", concept.categoryCode())
                                    .addValue("categoryDisplay", concept.categoryDisplay())
                                    .addValue("chapter", concept.chapter())
                                    .addValue("chapterDisplay", concept.chapterDisplay()))
                            .toArray(MapSqlParameterSource[]::new));
        }
    }

    boolean activate(UUID releaseId, UUID activatedBy, Instant now) {
        return jdbc.update("""
                INSERT INTO terminology_activations (code_system, release_id, activated_at, activated_by)
                SELECT code_system, id, :now, :activatedBy FROM terminology_releases WHERE id = :releaseId AND code_system = :system""",
                new MapSqlParameterSource()
                        .addValue("releaseId", releaseId.toString())
                        .addValue("system", CIE10)
                        .addValue("now", Timestamp.from(now))
                        .addValue("activatedBy", activatedBy.toString())) == 1;
    }

    private void requireActiveRelease() {
        if (jdbc.queryForList(ACTIVE_RELEASE, Map.of("system", CIE10), String.class).isEmpty()) {
            throw new ClinicalException.TerminologyUnavailable();
        }
    }

    private List<TerminologyRelease> releases(String where, Map<String, ?> parameters) {
        return jdbc.query("""
                SELECT r.id, r.version, r.checksum, r.source_file, r.concept_count, r.imported_at, r.imported_by,
                       r.id = (%s) AS active
                FROM terminology_releases r %s ORDER BY r.imported_at DESC""".formatted(ACTIVE_RELEASE, where), parameters,
                (row, index) -> new TerminologyRelease(UUID.fromString(row.getString("id")), row.getString("version"),
                        row.getString("checksum"), row.getString("source_file"), row.getInt("concept_count"),
                        row.getTimestamp("imported_at").toInstant(), UUID.fromString(row.getString("imported_by")), row.getBoolean("active")));
    }

    private static String normalize(String code) {
        return code == null ? "" : code.strip().replace(".", "").toUpperCase();
    }
}
