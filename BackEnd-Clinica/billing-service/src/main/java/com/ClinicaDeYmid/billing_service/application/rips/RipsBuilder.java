package com.ClinicaDeYmid.billing_service.application.rips;

import com.ClinicaDeYmid.billing_service.application.clinical.CareRecord;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalFact;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDetails;
import com.ClinicaDeYmid.billing_service.domain.AccountStatus;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.Buyer;
import com.ClinicaDeYmid.billing_service.domain.DischargeType;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.HealthUser;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RipsBuilder {

    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String CONSULTATION_PREFIX = "890";
    private static final int MAX_RELATED = 3;

    private final RipsSources sources;
    private final Set<String> gaps = new LinkedHashSet<>();
    private final ZoneId zone;

    private RipsBuilder(RipsSources sources) {
        this.sources = sources;
        this.zone = sources.zone();
    }

    public static RipsDraft build(RipsSources sources) {
        return new RipsBuilder(sources).draft();
    }

    private RipsDraft draft() {
        Invoice invoice = sources.invoice();
        RipsDocument.User user = user(services());
        return new RipsDraft(new RipsDocument(sources.issuer().nit().number(), invoice.number(), null, null,
                List.of(user)), List.copyOf(gaps));
    }

    private RipsDocument.User user(RipsDocument.Services services) {
        Invoice invoice = sources.invoice();
        HealthUser user = invoice.user();
        PatientDetails patient = sources.patient();
        LocalDate birthDate = null;
        String sex = null;
        String userType = null;
        String country = null;
        String municipality = null;
        String zoneCode = null;
        String origin = null;
        switch (patient) {
            case PatientDetails.Registered registered -> {
                birthDate = registered.birthDate();
                sex = RipsCodes.sex(registered.sex());
                userType = invoice.buyer().kind() == Buyer.Kind.PATIENT ? RipsCodes.PRIVATE_USER
                        : RipsCodes.userType(registered.healthRegime(), registered.affiliateType());
                municipality = registered.municipalityCode();
                country = municipality == null ? null : RipsCodes.COLOMBIA;
                zoneCode = RipsCodes.zone(registered.zone());
                origin = registered.countryOfOriginCode();
            }
            case PatientDetails.Unidentified unidentified -> {
                birthDate = LocalDate.of(unidentified.estimatedBirthYear(), 1, 1);
                sex = RipsCodes.sex(unidentified.sex());
                userType = invoice.buyer().kind() == Buyer.Kind.PATIENT ? RipsCodes.PRIVATE_USER : null;
            }
        }
        require(sex, "el sexo del paciente");
        require(userType, "el tipo de usuario (régimen y tipo de afiliado del paciente)");
        require(municipality, "el código DIVIPOLA del municipio de residencia del paciente");
        require(zoneCode, "la zona de residencia del paciente");
        require(origin, "el código del país de origen del paciente");
        boolean minor = birthDate != null
                && Period.between(birthDate, LocalDate.ofInstant(sources.account().openedAt(), zone)).getYears() < 18;
        String documentType = RipsCodes.documentType(user.documentType(), minor);
        require(documentType, "el tipo de documento del paciente en la tabla de SISPRO");
        return new RipsDocument.User(documentType, user.documentNumber(), userType,
                birthDate == null ? null : birthDate.toString(), sex, country, municipality, zoneCode,
                RipsCodes.NO_DISABILITY, 1, origin, null, services);
    }

    private RipsDocument.Services services() {
        Invoice invoice = sources.invoice();
        Map<Integer, Invoice> sharedByLine = allocateSharedPayments(invoice);
        List<RipsDocument.Consultation> consultations = new ArrayList<>();
        List<RipsDocument.Procedure> procedures = new ArrayList<>();
        List<RipsDocument.OtherService> others = new ArrayList<>();
        for (InvoiceLine line : invoice.lines()) {
            Invoice shared = sharedByLine.get(line.position());
            Collection collection = collection(shared);
            if (line.kind() == InvoiceLine.Kind.PACKAGE || line.stay()) {
                others.add(other(line, collection, others.size() + 1));
            } else if (line.kind() == InvoiceLine.Kind.SERVICE && line.code().startsWith(CONSULTATION_PREFIX)) {
                consultations.add(consultation(line, collection, consultations.size() + 1));
            } else if (line.kind() == InvoiceLine.Kind.SERVICE) {
                procedures.add(procedure(line, collection, procedures.size() + 1));
            }
        }
        List<RipsDocument.Emergency> emergencies = new ArrayList<>();
        List<RipsDocument.Hospitalization> hospitalizations = new ArrayList<>();
        AdmissionKind kind = sources.account().kind();
        if (kind == AdmissionKind.EMERGENCY) {
            emergencies.add(emergency());
        } else if (kind == AdmissionKind.INPATIENT) {
            hospitalizations.add(hospitalization());
        }
        return new RipsDocument.Services(consultations, procedures, emergencies, hospitalizations, List.of(), List.of(),
                others);
    }

    private RipsDocument.Consultation consultation(InvoiceLine line, Collection collection, int consecutive) {
        Instant moment = serviceMoment(line);
        Optional<CareRecord.Encounter> encounter = encounterOf(line);
        ClinicalFact.CareSetting setting = setting(encounter);
        Reason reason = reason(encounter);
        Diagnoses diagnoses = diagnoses(encounter);
        require(reason.purpose(), "la finalidad de la atención (historia clínica) de la consulta " + line.code());
        require(reason.cause(), "la causa de la atención (historia clínica) de la consulta " + line.code());
        return new RipsDocument.Consultation(providerCode(), format(moment), line.authorizationNumber(), line.code(),
                setting == null ? null : setting.modality(), setting == null ? null : setting.serviceGroup(),
                setting == null ? null : Integer.valueOf(setting.serviceCode()), reason.purpose(), reason.cause(),
                diagnoses.principal(), null, null, diagnoses.related(0), null, null, diagnoses.related(1), null, null,
                diagnoses.related(2), null, null, diagnoses.principalType(), professionalType(), professionalNumber(),
                amount(line.lineTotal()), collection.concept(), collection.value(), collection.invoiceNumber(),
                consecutive, null);
    }

    private RipsDocument.Procedure procedure(InvoiceLine line, Collection collection, int consecutive) {
        Instant moment = serviceMoment(line);
        Optional<CareRecord.Encounter> encounter = encounterOf(line);
        ClinicalFact.CareSetting setting = setting(encounter);
        Reason reason = reason(encounter);
        Diagnoses diagnoses = diagnoses(encounter);
        require(reason.purpose(), "la finalidad de la atención (historia clínica) del procedimiento " + line.code());
        return new RipsDocument.Procedure(providerCode(), format(moment), null, line.authorizationNumber(), line.code(),
                entryRoute(), setting == null ? null : setting.modality(),
                setting == null ? null : setting.serviceGroup(),
                setting == null ? null : Integer.valueOf(setting.serviceCode()), reason.purpose(), professionalType(),
                professionalNumber(), diagnoses.principal(), null, null, diagnoses.related(0), null, null, null, null,
                null, amount(line.lineTotal()), collection.concept(), collection.value(), collection.invoiceNumber(),
                null, consecutive);
    }

    private RipsDocument.OtherService other(InvoiceLine line, Collection collection, int consecutive) {
        LocalDate day = line.serviceDate() == null ? sources.invoice().issuedOn() : line.serviceDate();
        return new RipsDocument.OtherService(providerCode(), line.authorizationNumber(), null,
                format(day.atStartOfDay(zone).toInstant()), line.stay() ? RipsCodes.STAYS : RipsCodes.COMPLEMENTARY_SERVICES,
                line.code(), line.description(), line.quantity(), professionalType(), professionalNumber(),
                amount(line.unitPrice()), BigDecimal.ZERO, amount(line.lineTotal()), collection.concept(),
                collection.value(), collection.invoiceNumber(), null, consecutive);
    }

    private RipsDocument.Emergency emergency() {
        Stay stay = stay();
        return new RipsDocument.Emergency(providerCode(), format(stay.startedAt()), stay.cause(), stay.admittedWith(),
                null, null, stay.dischargedWith(), null, null, stay.related(0), null, null, stay.related(1), null, null,
                stay.related(2), null, null, RipsCodes.destination(stay.discharge()), deathCause(stay), format(stay.endedAt()),
                1, null);
    }

    private RipsDocument.Hospitalization hospitalization() {
        Stay stay = stay();
        return new RipsDocument.Hospitalization(providerCode(), hospitalizationRoute(), format(stay.startedAt()),
                firstAuthorization(), stay.cause(), stay.admittedWith(), null, null, stay.dischargedWith(), null, null,
                stay.related(0), null, null, stay.related(1), null, null, stay.related(2), null, null, null,
                RipsCodes.destination(stay.discharge()), deathCause(stay), format(stay.endedAt()), null, 1);
    }

    private record Stay(Instant startedAt, Instant endedAt, DischargeType discharge, String cause, String admittedWith,
                        String dischargedWith, List<String> relatedAtDischarge) {

        String related(int index) {
            return index < relatedAtDischarge.size() ? relatedAtDischarge.get(index) : null;
        }
    }

    private Stay stay() {
        EpisodeAccount account = sources.account();
        CareRecord care = sources.care();
        Instant startedAt = sources.episode().phases().stream().map(EpisodeDetails.PhasePeriod::startedAt)
                .min(Instant::compareTo).orElse(account.openedAt());
        Instant endedAt = account.status() instanceof AccountStatus.Frozen frozen ? frozen.since() : null;
        DischargeType discharge = account.status() instanceof AccountStatus.Frozen frozen ? frozen.discharge() : null;
        require(endedAt, "la fecha de egreso del episodio");
        String cause = care.notes().stream().map(CareRecord.Note::cause).filter(value -> value != null).findFirst()
                .orElse(null);
        require(cause, "la causa de la atención del episodio (historia clínica)");
        Optional<CareRecord.Note> first = care.firstDiagnosed();
        Optional<CareRecord.Note> last = care.notes().stream()
                .filter(note -> "DISCHARGE".equals(note.type()) && note.principal().isPresent())
                .reduce((one, other) -> other).or(care::lastDiagnosed);
        String admittedWith = first.flatMap(CareRecord.Note::principal).map(ClinicalFact.CodedDiagnosis::code).orElse(null);
        String dischargedWith = last.flatMap(CareRecord.Note::principal).map(ClinicalFact.CodedDiagnosis::code)
                .orElse(null);
        require(admittedWith, "el diagnóstico principal de ingreso del episodio (historia clínica)");
        require(dischargedWith, "el diagnóstico principal de egreso del episodio (historia clínica)");
        List<String> related = last.map(note -> note.related().stream().map(ClinicalFact.CodedDiagnosis::code)
                .limit(MAX_RELATED).toList()).orElse(List.of());
        return new Stay(startedAt, endedAt, discharge, cause, admittedWith, dischargedWith, related);
    }

    private String deathCause(Stay stay) {
        if (stay.discharge() != DischargeType.DEATH) {
            return null;
        }
        require(null, "la causa de muerte (el egreso fue por fallecimiento)");
        return null;
    }

    private record Reason(String purpose, String cause) {
    }

    private Reason reason(Optional<CareRecord.Encounter> encounter) {
        List<CareRecord.Note> notes = encounter.map(found -> sources.care().notesOf(found.id()))
                .orElse(sources.care().notes());
        String purpose = null;
        String cause = null;
        for (CareRecord.Note note : notes) {
            purpose = note.purpose() == null ? purpose : note.purpose();
            cause = note.cause() == null ? cause : note.cause();
        }
        return new Reason(purpose, cause);
    }

    private record Diagnoses(String principal, String principalType, List<String> relatedCodes) {

        String related(int index) {
            return index < relatedCodes.size() ? relatedCodes.get(index) : null;
        }
    }

    private Diagnoses diagnoses(Optional<CareRecord.Encounter> encounter) {
        List<CareRecord.Note> notes = encounter.map(found -> sources.care().notesOf(found.id()))
                .orElse(sources.care().notes());
        Optional<CareRecord.Note> diagnosed = notes.stream().filter(note -> note.principal().isPresent())
                .reduce((first, second) -> second).or(() -> sources.care().lastDiagnosed());
        Optional<ClinicalFact.CodedDiagnosis> principal = diagnosed.flatMap(CareRecord.Note::principal);
        require(principal.orElse(null), "el diagnóstico principal de la atención (historia clínica)");
        return new Diagnoses(principal.map(ClinicalFact.CodedDiagnosis::code).orElse(null),
                principal.map(found -> RipsCodes.diagnosisType(found.type())).orElse(null),
                diagnosed.map(note -> note.related().stream().map(ClinicalFact.CodedDiagnosis::code)
                        .limit(MAX_RELATED).toList()).orElse(List.of()));
    }

    private ClinicalFact.CareSetting setting(Optional<CareRecord.Encounter> encounter) {
        ClinicalFact.CareSetting setting = encounter.map(CareRecord.Encounter::careSetting).orElse(null);
        require(setting, "el servicio REPS, la modalidad y el grupo de la atención (historia clínica)");
        return setting;
    }

    private Optional<CareRecord.Encounter> encounterOf(InvoiceLine line) {
        return sources.care().encounterAt(serviceMoment(line).plusSeconds(86_399));
    }

    private Instant serviceMoment(InvoiceLine line) {
        LocalDate day = line.serviceDate() == null ? sources.invoice().issuedOn() : line.serviceDate();
        return sources.care().encounters().stream()
                .map(CareRecord.Encounter::openedAt)
                .filter(opened -> LocalDate.ofInstant(opened, zone).equals(day))
                .findFirst()
                .orElse(day.atStartOfDay(zone).toInstant());
    }

    private String entryRoute() {
        return switch (sources.account().kind()) {
            case OUTPATIENT -> RipsCodes.FROM_OUTPATIENT;
            case EMERGENCY -> RipsCodes.FROM_EMERGENCY;
            case INPATIENT -> RipsCodes.FROM_HOSPITALIZATION;
        };
    }

    private String hospitalizationRoute() {
        boolean fromEmergency = sources.episode().phases().stream()
                .anyMatch(phase -> "EMERGENCY".equals(phase.kind()));
        return fromEmergency ? RipsCodes.FROM_EMERGENCY : RipsCodes.SPONTANEOUS_DEMAND;
    }

    private String firstAuthorization() {
        return sources.invoice().lines().stream().map(InvoiceLine::authorizationNumber).filter(value -> value != null)
                .findFirst().orElse(null);
    }

    private record Collection(String concept, BigDecimal value, String invoiceNumber) {
    }

    private Collection collection(Invoice shared) {
        if (shared == null) {
            return new Collection(RipsCodes.NOT_APPLICABLE_COLLECTION, BigDecimal.ZERO, null);
        }
        String concept = RipsCodes.collection(shared.sharedPaymentKind());
        if (RipsCodes.NOT_APPLICABLE_COLLECTION.equals(concept)) {
            gaps.add("La cuota de recuperación " + shared.number() + " no tiene concepto de recaudo en la tabla de "
                    + "SISPRO; revise cómo reportarla");
        }
        return new Collection(concept, amount(shared.grossTotal()), shared.number());
    }

    private Map<Integer, Invoice> allocateSharedPayments(Invoice invoice) {
        Map<Integer, Invoice> byLine = new HashMap<>();
        List<InvoiceLine> reportable = invoice.lines().stream().filter(line -> line.kind() != InvoiceLine.Kind.SHARED_PAYMENT)
                .toList();
        for (Invoice shared : invoice.sharedPayments()) {
            Optional<InvoiceLine> target = reportable.stream()
                    .filter(line -> !byLine.containsKey(line.position()))
                    .filter(line -> shared.authorizationNumber() != null
                            && shared.authorizationNumber().equals(line.authorizationNumber()))
                    .findFirst()
                    .or(() -> reportable.stream().filter(line -> !byLine.containsKey(line.position())).findFirst());
            if (target.isPresent()) {
                byLine.put(target.get().position(), shared);
            } else {
                gaps.add("No hay un servicio donde reportar el pago compartido " + shared.number());
            }
        }
        return byLine;
    }

    private String providerCode() {
        String code = sources.issuer().profile().healthProviderCode();
        require(code, "el código de prestador del emisor");
        return code;
    }

    private String professionalType() {
        RipsSources.Professional professional = sources.professional();
        String type = professional == null ? null : RipsCodes.documentType(professional.documentType(), false);
        require(type, "el documento del profesional tratante (admisiones y profesionales)");
        return type;
    }

    private String professionalNumber() {
        return sources.professional() == null ? null : sources.professional().documentNumber();
    }

    private String format(Instant moment) {
        return moment == null ? null : MOMENT.format(moment.atZone(zone));
    }

    private static BigDecimal amount(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private void require(Object value, String what) {
        if (value == null) {
            gaps.add("Falta " + what);
        }
    }
}
