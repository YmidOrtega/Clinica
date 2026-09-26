package com.ClinicaDeYmid.billing_service.application.rips;

import com.ClinicaDeYmid.billing_service.application.clinical.CareRecord;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalFact;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDetails;
import com.ClinicaDeYmid.billing_service.domain.AccountSummary;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.domain.Buyer;
import com.ClinicaDeYmid.billing_service.domain.ChargedService;
import com.ClinicaDeYmid.billing_service.domain.Copayment;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.HealthUser;
import com.ClinicaDeYmid.billing_service.domain.CoveragePlan;
import com.ClinicaDeYmid.billing_service.domain.HealthTerms;
import com.ClinicaDeYmid.billing_service.domain.PaymentModality;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.IssuedNumber;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.IssuerProfile;
import com.ClinicaDeYmid.billing_service.domain.LineOrigin;
import com.ClinicaDeYmid.billing_service.domain.LinePrice;
import com.ClinicaDeYmid.billing_service.domain.Nit;
import com.ClinicaDeYmid.billing_service.domain.PersonType;
import com.ClinicaDeYmid.billing_service.domain.PriceOrigin;
import com.ClinicaDeYmid.billing_service.domain.PricingTerms;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.SaleLine;
import com.ClinicaDeYmid.billing_service.domain.SaleType;
import com.ClinicaDeYmid.billing_service.domain.SharedPaymentKind;
import com.ClinicaDeYmid.billing_service.domain.TaxResponsibility;
import com.ClinicaDeYmid.billing_service.domain.TaxScheme;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RipsBuilderTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), BOGOTA);
    private static final UUID CONSULTATION = UUID.randomUUID();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void reportsTheConsultationWithItsClinicalDataAndTheCopaymentInvoice() {
        Fixture fixture = fixture();

        RipsDraft draft = RipsBuilder.build(new RipsSources(fixture.invoice(), issuer(), fixture.account(), episode(),
                registered("68001", "170", "BENEFICIARY"), new RipsSources.Professional("CEDULA_DE_CIUDADANIA", "80100200"),
                care(), BOGOTA));
        JsonNode rips = JSON.valueToTree(draft.document());

        assertThat(draft.gaps()).isEmpty();
        assertThat(rips.path("numDocumentoIdObligado").asText()).isEqualTo("800197268");
        assertThat(rips.path("numFactura").asText()).isEqualTo("SETP990000001");
        assertThat(rips.path("tipoNota").isNull()).isTrue();
        JsonNode user = rips.path("usuarios").get(0);
        assertThat(user.path("tipoDocumentoIdentificacion").asText()).isEqualTo("CC");
        assertThat(user.path("tipoUsuario").asText()).isEqualTo("02");
        assertThat(user.path("codSexo").asText()).isEqualTo("M");
        assertThat(user.path("codPaisResidencia").asText()).isEqualTo("170");
        assertThat(user.path("codMunicipioResidencia").asText()).isEqualTo("68001");
        assertThat(user.path("codZonaTerritorialResidencia").asText()).isEqualTo("02");
        assertThat(user.path("codPaisOrigen").asText()).isEqualTo("170");
        assertThat(user.path("fechaNacimiento").asText()).isEqualTo("1990-04-12");
        JsonNode consultation = user.path("serviciosTecnologias").path("consultas").get(0);
        assertThat(consultation.path("codPrestador").asText()).isEqualTo("680010123401");
        assertThat(consultation.path("fechaInicioAtencion").asText()).isEqualTo("2026-09-26 10:05");
        assertThat(consultation.path("codConsulta").asText()).isEqualTo("890201");
        assertThat(consultation.path("modalidadGrupoServicioTecSal").asText()).isEqualTo("01");
        assertThat(consultation.path("grupoServicios").asText()).isEqualTo("01");
        assertThat(consultation.path("codServicio").asInt()).isEqualTo(328);
        assertThat(consultation.path("finalidadTecnologiaSalud").asText()).isEqualTo("15");
        assertThat(consultation.path("causaMotivoAtencion").asText()).isEqualTo("38");
        assertThat(consultation.path("codDiagnosticoPrincipal").asText()).isEqualTo("I10X");
        assertThat(consultation.path("codDiagnosticoRelacionado1").asText()).isEqualTo("E119");
        assertThat(consultation.path("codDiagnosticoRelacionado2").isNull()).isTrue();
        assertThat(consultation.path("tipoDiagnosticoPrincipal").asText()).isEqualTo("02");
        assertThat(consultation.path("tipoDocumentoIdentificacion").asText()).isEqualTo("CC");
        assertThat(consultation.path("numDocumentoIdentificacion").asText()).isEqualTo("80100200");
        assertThat(consultation.path("vrServicio").decimalValue()).isEqualByComparingTo("45000");
        assertThat(consultation.path("conceptoRecaudo").asText()).isEqualTo("02");
        assertThat(consultation.path("valorPagoModerador").decimalValue()).isEqualByComparingTo("35000");
        assertThat(consultation.path("numFEVPagoModerador").asText()).isEqualTo("SETP990000000");
        assertThat(consultation.path("consecutivo").asInt()).isEqualTo(1);
        JsonNode procedure = user.path("serviciosTecnologias").path("procedimientos").get(0);
        assertThat(procedure.path("codProcedimiento").asText()).isEqualTo("903841");
        assertThat(procedure.path("viaIngresoServicioSalud").asText()).isEqualTo("02");
        assertThat(procedure.path("vrServicio").decimalValue()).isEqualByComparingTo("0");
        assertThat(procedure.path("conceptoRecaudo").asText()).isEqualTo("05");
        assertThat(procedure.path("numFEVPagoModerador").isNull()).isTrue();
        assertThat(user.path("serviciosTecnologias").path("urgencias")).isEmpty();
        assertThat(user.path("serviciosTecnologias").path("hospitalizacion")).isEmpty();
    }

    @Test
    void listsWhatIsMissingInsteadOfGuessing() {
        Fixture fixture = fixture();

        RipsDraft draft = RipsBuilder.build(new RipsSources(fixture.invoice(), issuer(), fixture.account(), episode(),
                registered(null, null, null), null, CareRecord.empty(), BOGOTA));

        assertThat(draft.complete()).isFalse();
        assertThat(draft.gaps()).contains(
                "Falta el tipo de usuario (régimen y tipo de afiliado del paciente)",
                "Falta el código DIVIPOLA del municipio de residencia del paciente",
                "Falta el código del país de origen del paciente",
                "Falta el servicio REPS, la modalidad y el grupo de la atención (historia clínica)",
                "Falta el diagnóstico principal de la atención (historia clínica)",
                "Falta el documento del profesional tratante (admisiones y profesionales)");
        assertThat(draft.gaps()).doesNotHaveDuplicates();
    }

    private record Fixture(Invoice invoice, EpisodeAccount account) {
    }

    private static Fixture fixture() {
        EpisodeAccount account = EpisodeAccount.open(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000123", 1,
                UUID.randomUUID(), AdmissionKind.OUTPATIENT, AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(),
                Instant.parse("2026-09-26T15:00:00Z"), null, null));
        Sale sale = Sale.open(account, 1, new SaleType.NonSurgical());
        SaleLine consultation = sale.charge(new ChargedService(CONSULTATION, "890201", null, "Consulta", null), 1,
                LocalDate.parse("2026-09-26"), new LineOrigin.Manual(), NOW);
        SaleLine test = sale.charge(new ChargedService(UUID.randomUUID(), "903841", null, "Hemograma", null), 1,
                LocalDate.parse("2026-09-26"), new LineOrigin.Manual(), NOW);
        sale.confirm(new PricingTerms(UUID.randomUUID(), "CT-1", UUID.randomUUID(), Map.of(
                consultation.uuid(), new LinePrice(PriceOrigin.TARIFF_MANUAL, new BigDecimal("45000"), new BigDecimal("45000"), null, null),
                test.uuid(), new LinePrice(PriceOrigin.CAPITATION, BigDecimal.ZERO, BigDecimal.ZERO, null, null)),
                List.of()), NOW);
        AccountSummary.Unit unit = AccountSummary.of(account, List.of(sale), true, List.of(new Copayment(UUID.randomUUID(),
                "AUT-1", new BigDecimal("35000"), null, null, Set.of(CONSULTATION), false)), List.of()).units().getFirst();
        HealthUser ana = new HealthUser(UUID.randomUUID(), "CEDULA_DE_CIUDADANIA", "1098765432", "Ana María Restrepo",
                "CONTRIBUTORY");
        Invoice fee = Invoice.sharedPayment(account, new Buyer(Buyer.Kind.PATIENT, ana.patientUuid(), ana.documentType(),
                ana.documentNumber(), ana.name()), ana, SharedPaymentKind.MODERATING_FEE, new BigDecimal("35000"), "AUT-1",
                "REC-1", "CT-1");
        fee.issue(new IssuedNumber(UUID.randomUUID(), "SETP", 990000000), NOW);
        Invoice invoice = Invoice.draft(unit, account,
                new Buyer(Buyer.Kind.PAYER, UUID.randomUUID(), "NIT", "900156264-2", "Nueva EPS S.A."), ana, HealthTerms.contracted(PaymentModality.EVENT, CoveragePlan.UPC_CONTRIBUTORY, "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1"),
                List.of(fee));
        invoice.issue(new IssuedNumber(UUID.randomUUID(), "SETP", 990000001), NOW);
        return new Fixture(invoice, account);
    }

    private static Issuer issuer() {
        return Issuer.configure(new Nit("800197268", 4), new IssuerProfile(PersonType.LEGAL_ENTITY,
                "Clínica de Ymid S.A.S.", "Clínica de Ymid", TaxScheme.NOT_APPLICABLE,
                Set.of(TaxResponsibility.LARGE_TAXPAYER), "Calle 10 # 43-20", "68001", "Bucaramanga", "Santander", null,
                "facturacion@clinica.co", "6076441234", "680010123401"));
    }

    private static EpisodeDetails episode() {
        return new EpisodeDetails(UUID.randomUUID(), "ADM-2026-000123", "GENERAL_ILLNESS", "OUTPATIENT", "ACTIVE", null,
                new EpisodeDetails.Attending(UUID.randomUUID(), "Dra. Paula Gómez", "RM-1"), null, List.of(),
                List.of(new EpisodeDetails.PhasePeriod("OUTPATIENT", Instant.parse("2026-09-26T15:00:00Z"), null)));
    }

    private static PatientDetails registered(String municipality, String country, String affiliateType) {
        return new PatientDetails.Registered(UUID.randomUUID(), "CEDULA_DE_CIUDADANIA", "1098765432", "Ana María",
                "Restrepo", LocalDate.parse("1990-04-12"), "FEMALE", "CONTRIBUTORY", affiliateType, country, municipality,
                "URBAN");
    }

    private static CareRecord care() {
        UUID encounter = UUID.randomUUID();
        return new CareRecord(
                List.of(new CareRecord.Encounter(encounter, "OUTPATIENT", Instant.parse("2026-09-26T15:05:00Z"), null,
                        new ClinicalFact.CareSetting("328", "01", "01"))),
                List.of(new CareRecord.Note(UUID.randomUUID(), encounter, "CONSULTATION",
                        Instant.parse("2026-09-26T15:20:00Z"), "15", "38",
                        List.of(new ClinicalFact.CodedDiagnosis("I10X", "PRINCIPAL", "CONFIRMED_NEW"),
                                new ClinicalFact.CodedDiagnosis("E119", "RELATED", "IMPRESSION")))));
    }
}
