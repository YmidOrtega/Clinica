package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.dian.DianSoftware;
import com.ClinicaDeYmid.billing_service.application.dian.ElectronicAttachment;
import com.ClinicaDeYmid.billing_service.application.dian.ElectronicInvoice;
import com.ClinicaDeYmid.billing_service.domain.AccountSummary;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.domain.Buyer;
import com.ClinicaDeYmid.billing_service.domain.ChargedService;
import com.ClinicaDeYmid.billing_service.domain.Copayment;
import com.ClinicaDeYmid.billing_service.domain.Cufe;
import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.domain.DischargeType;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
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
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import com.ClinicaDeYmid.billing_service.domain.PersonType;
import com.ClinicaDeYmid.billing_service.domain.PriceOrigin;
import com.ClinicaDeYmid.billing_service.domain.PricingTerms;
import com.ClinicaDeYmid.billing_service.domain.ResolutionTerms;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.SaleLine;
import com.ClinicaDeYmid.billing_service.domain.SaleType;
import com.ClinicaDeYmid.billing_service.domain.SharedPaymentKind;
import com.ClinicaDeYmid.billing_service.domain.TaxResponsibility;
import com.ClinicaDeYmid.billing_service.domain.TaxScheme;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaxUblWriterTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-27T15:15:30Z"), ZoneId.of("America/Bogota"));
    private static final String CUCON = "5f0e2b7c9a1d4e3f8b6a0c2d4e6f8a1b3c5d7e9f0a2b4c6d8e0f1a3b5c7d9e1f";
    private static final String KEY = "fc8eac422eba16e22ffd8c6f94b3f40a6e38162c";

    @Test
    void writesAWellFormedUblInvoiceWhoseCufeCanBeRecomputedFromItsOwnFields() throws Exception {
        Fixture fixture = issued();

        String xml = new StaxUblWriter().invoice(ElectronicInvoice.of(fixture.invoice(), fixture.issuer(),
                fixture.resolution(), new DianSoftware("56f2ae4e-9812-4fad-9255-643406bbb1a1", "12345", null)));
        XPath path = xpath();
        Document document = parse(xml);

        assertThat(path.evaluate("/inv:Invoice/cbc:ID", document)).isEqualTo("SETP990000000");
        assertThat(path.evaluate("/inv:Invoice/cbc:UUID/@schemeName", document)).isEqualTo("CUFE-SHA384");
        assertThat(path.evaluate("/inv:Invoice/cbc:IssueTime", document)).isEqualTo("10:15:30-05:00");
        assertThat(path.evaluate("/inv:Invoice/cbc:CustomizationID", document)).isEqualTo("SS-CUFE");
        assertThat(path.evaluate("/inv:Invoice/cac:InvoicePeriod/cbc:StartDate", document)).isEqualTo("2026-09-26");
        assertThat(path.evaluate("/inv:Invoice/cac:InvoicePeriod/cbc:EndDate", document)).isEqualTo("2026-09-26");
        assertThat(path.evaluate("/inv:Invoice/cbc:LineCountNumeric", document)).isEqualTo("1");
        assertThat(path.evaluate("count(/inv:Invoice/cac:InvoiceLine)", document)).isEqualTo("1");
        assertThat(path.evaluate("//sts:InvoiceAuthorization", document)).isEqualTo("18760000001");
        assertThat(path.evaluate("//sts:SoftwareSecurityCode", document)).isEqualTo(
                "04b857d859779f7f2f0ca928b921a6b580d48f6045b9174d4f3f7f3da19454fca7a0b177f25219b9d716324628330adc");
        assertThat(path.evaluate("count(//ext:UBLExtension)", document)).isEqualTo("3");
        assertThat(path.evaluate("//ext:UBLExtension[2]//inv:CustomTagGeneral/inv:Value[2]", document))
                .isEqualTo("Resolución 0948:2026");
        assertThat(path.evaluate("//ext:UBLExtension[3]/ext:ExtensionContent", document)).isEmpty();
        assertThat(path.evaluate("//inv:Collection/inv:AdditionalInformation/inv:Name", document))
                .isEqualTo("CODIGO_PRESTADOR");
        assertThat(path.evaluate("//inv:AdditionalInformation[inv:Name='CODIGO_PRESTADOR']/inv:Value", document))
                .isEqualTo("0500101234");
        assertThat(path.evaluate("//inv:AdditionalInformation[inv:Name='MODALIDAD_PAGO']/inv:Value/@schemeID", document))
                .isEqualTo("04");
        assertThat(path.evaluate("//inv:AdditionalInformation[inv:Name='MODALIDAD_PAGO']/inv:Value/@schemeName",
                document)).isEqualTo("salud_modalidad_pago.gc");
        assertThat(path.evaluate("//inv:AdditionalInformation[inv:Name='COBERTURA_PLAN_BENEFICIOS']/inv:Value/@schemeID",
                document)).isEqualTo("16");
        assertThat(path.evaluate("//inv:AdditionalInformation[inv:Name='NUMERO_CONTRATO']/inv:Value", document))
                .isEqualTo(CUCON);
        assertThat(path.evaluate("count(//inv:AdditionalInformation[inv:Name='FACTURA_SIN_CONTRATO'])", document))
                .isEqualTo("0");
        assertThat(path.evaluate("count(//inv:AdditionalInformation[inv:Name='COPAGO'])", document)).isEqualTo("0");
        assertThat(path.evaluate("count(//inv:AdditionalInformation[inv:Name='NUMERO_DOCUMENTO_IDENTIFICACION'])",
                document)).isEqualTo("0");
        assertThat(path.evaluate("count(//cac:PrepaidPayment)", document)).isEqualTo("1");
        assertThat(path.evaluate("//cac:PrepaidPayment/cbc:ID", document)).isEqualTo("1");
        assertThat(path.evaluate("//cac:PrepaidPayment/cbc:ID/@schemeID", document)).isEqualTo("01");
        assertThat(path.evaluate("//cac:PrepaidPayment/cbc:PaidAmount", document)).isEqualTo("35000.00");
        assertThat(path.evaluate("//cac:LegalMonetaryTotal/cbc:PrepaidAmount", document)).isEqualTo("35000.00");
        assertThat(path.evaluate("//cac:LegalMonetaryTotal/cbc:PayableAmount", document)).isEqualTo("10000.00");
        assertThat(path.evaluate("//cac:AccountingCustomerParty//cac:PartyIdentification/cbc:ID/@schemeID", document))
                .isEqualTo("2");

        String recomputed = Cufe.of(new Cufe.Input(path.evaluate("/inv:Invoice/cbc:ID", document),
                LocalDate.parse(path.evaluate("/inv:Invoice/cbc:IssueDate", document)),
                LocalTime.parse(path.evaluate("/inv:Invoice/cbc:IssueTime", document).substring(0, 8)),
                new BigDecimal(path.evaluate("//cac:LegalMonetaryTotal/cbc:LineExtensionAmount", document)),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(path.evaluate("//cac:LegalMonetaryTotal/cbc:PayableAmount", document)),
                path.evaluate("//cac:AccountingSupplierParty//cac:PartyTaxScheme/cbc:CompanyID", document),
                path.evaluate("//cac:AccountingCustomerParty//cac:PartyTaxScheme/cbc:CompanyID", document), KEY,
                DianEnvironment.TEST));
        assertThat(path.evaluate("/inv:Invoice/cbc:UUID", document)).isEqualTo(recomputed).isEqualTo(fixture.invoice().cufe());
        assertThat(fixture.invoice().qrContent()).contains("CUFE: " + recomputed)
                .contains("https://catalogo-vpfe-hab.dian.gov.co/document/searchqr?documentkey=" + recomputed);
    }

    @Test
    void aSharedPaymentInvoiceIsACollectionWithoutTheHealthExtension() throws Exception {
        Fixture fixture = issued();
        Invoice copayment = fixture.invoice().sharedPayments().getFirst();
        String cufe = Cufe.of(copayment.cufeInput(fixture.issuer(), fixture.resolution()));
        copayment.identify(cufe, ElectronicInvoice.qrContent(copayment, fixture.issuer(), cufe));

        Document document = parse(new StaxUblWriter().invoice(ElectronicInvoice.of(copayment, fixture.issuer(),
                fixture.resolution(), new DianSoftware("56f2ae4e-9812-4fad-9255-643406bbb1a1", "12345", null))));
        XPath path = xpath();

        assertThat(path.evaluate("/inv:Invoice/cbc:CustomizationID", document)).isEqualTo("SS-Recaudo");
        assertThat(path.evaluate("count(//ext:UBLExtension)", document)).isEqualTo("2");
        assertThat(path.evaluate("count(//inv:CustomTagGeneral)", document)).isEqualTo("0");
        assertThat(path.evaluate("count(//cac:PrepaidPayment)", document)).isEqualTo("0");
        assertThat(path.evaluate("/inv:Invoice/cac:InvoicePeriod/cbc:StartDate", document)).isEqualTo("2026-09-27");
    }

    @Test
    void anInvoiceWithoutSharedPaymentsCreditsNothingToThePayer() throws Exception {
        Fixture fixture = issued(false);

        Document document = parse(new StaxUblWriter().invoice(ElectronicInvoice.of(fixture.invoice(), fixture.issuer(),
                fixture.resolution(), new DianSoftware("56f2ae4e-9812-4fad-9255-643406bbb1a1", "12345", null))));
        XPath path = xpath();

        assertThat(path.evaluate("/inv:Invoice/cbc:CustomizationID", document)).isEqualTo("SS-SinAporte");
        assertThat(path.evaluate("count(//cac:PrepaidPayment)", document)).isEqualTo("0");
    }

    @Test
    void wrapsTheSignedDocumentAndTheDianResponseInAnAttachedDocument() throws Exception {
        Fixture fixture = issued();
        String signed = sampleUbl();
        String response = "<ApplicationResponse xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\"/>";
        ElectronicAttachment attachment = new ElectronicAttachment(UUID.randomUUID(), ElectronicDocument.Type.INVOICE,
                fixture.invoice().number(), fixture.invoice().cufe(), fixture.invoice().issuedOn(), fixture.issuer(),
                fixture.invoice().buyer(), signed, response, Instant.parse("2026-09-27T15:20:00Z"),
                Instant.parse("2026-09-27T15:21:00Z"));

        Document document = parse(new StaxUblWriter().attachedDocument(attachment));
        XPath path = xpath();

        assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo(StaxUblWriter.ATTACHED_DOCUMENT);
        assertThat(path.evaluate("count(//ext:UBLExtension)", document)).isEqualTo("1");
        assertThat(path.evaluate("/*/cbc:ParentDocumentID", document)).isEqualTo("SETP990000000");
        assertThat(path.evaluate("/*/cac:Attachment/cac:ExternalReference/cbc:Description", document)).isEqualTo(signed);
        assertThat(path.evaluate("//cac:DocumentReference/cac:Attachment//cbc:Description", document)).isEqualTo(response);
        assertThat(path.evaluate("//cac:DocumentReference/cbc:UUID", document)).isEqualTo(fixture.invoice().cufe());
        assertThat(path.evaluate("//cac:ResultOfVerification/cbc:ValidationTime", document)).isEqualTo("10:20:00-05:00");
        assertThat(path.evaluate("//cac:ReceiverParty//cbc:CompanyID", document)).isEqualTo("900156264");
    }

    static String sampleUbl() {
        Fixture fixture = issued();
        return new StaxUblWriter().invoice(ElectronicInvoice.of(fixture.invoice(), fixture.issuer(),
                fixture.resolution(), new DianSoftware("56f2ae4e-9812-4fad-9255-643406bbb1a1", "12345", null)));
    }

    private static Fixture issued() {
        return issued(true);
    }

    private static Fixture issued(boolean collected) {
        Issuer issuer = Issuer.configure(new Nit("800197268", 4), new IssuerProfile(PersonType.LEGAL_ENTITY,
                "Clínica de Ymid S.A.S.", "Clínica de Ymid", TaxScheme.NOT_APPLICABLE,
                Set.of(TaxResponsibility.LARGE_TAXPAYER), "Calle 10 # 43-20", "05001", "Medellín", "Antioquia", null,
                "facturacion@clinica.co", "6044441234", "050010123401"));
        NumberingResolution resolution = NumberingResolution.register(new ResolutionTerms("18760000001",
                LocalDate.parse("2019-01-19"), "SETP", 990000000, 995000000, LocalDate.parse("2019-01-19"),
                LocalDate.parse("2030-01-19"), KEY), DianEnvironment.TEST);
        EpisodeAccount account = EpisodeAccount.open(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000123", 1,
                UUID.randomUUID(), AdmissionKind.INPATIENT, AdmissionSnapshot.Status.DISCHARGED, UUID.randomUUID(),
                Instant.parse("2026-09-25T13:00:00Z"), DischargeType.MEDICAL, null));
        UUID consultation = UUID.randomUUID();
        Sale sale = Sale.open(account, 1, new SaleType.NonSurgical());
        SaleLine charged = sale.charge(new ChargedService(consultation, "890201", null, "Consulta", null), 1,
                LocalDate.parse("2026-09-26"), new LineOrigin.Manual(), NOW);
        SaleLine covered = sale.charge(new ChargedService(UUID.randomUUID(), "903841", null, "Hemograma", null), 1,
                LocalDate.parse("2026-09-26"), new LineOrigin.Manual(), NOW);
        sale.confirm(new PricingTerms(UUID.randomUUID(), "CT-1", UUID.randomUUID(), Map.of(
                charged.uuid(), new LinePrice(PriceOrigin.TARIFF_MANUAL, new BigDecimal("45000"), new BigDecimal("45000"), null, null),
                covered.uuid(), new LinePrice(PriceOrigin.CAPITATION, BigDecimal.ZERO, BigDecimal.ZERO, null, null)),
                List.of()), NOW);
        AccountSummary.Unit unit = AccountSummary.of(account, List.of(sale), true, List.of(new Copayment(UUID.randomUUID(),
                "AUT-1", new BigDecimal("35000"), null, null, Set.of(consultation), false)), List.of()).units().getFirst();
        HealthUser ana = new HealthUser(UUID.randomUUID(), "CEDULA_DE_CIUDADANIA", "1098765432", "Ana María Restrepo",
                "CONTRIBUTORY");
        Invoice copayment = Invoice.sharedPayment(account, new Buyer(Buyer.Kind.PATIENT, ana.patientUuid(),
                ana.documentType(), ana.documentNumber(), ana.name()), ana, SharedPaymentKind.COPAYMENT,
                new BigDecimal("35000"), "AUT-1", "REC-1", "CT-1");
        copayment.issue(new IssuedNumber(UUID.randomUUID(), "SETP", 989999999), NOW);
        Invoice invoice = Invoice.draft(unit, account,
                new Buyer(Buyer.Kind.PAYER, UUID.randomUUID(), "NIT", "900156264-2", "Nueva EPS S.A."), ana,
                HealthTerms.contracted(PaymentModality.EVENT, CoveragePlan.UPC_CONTRIBUTORY, CUCON),
                collected ? List.of(copayment) : List.of());
        invoice.issue(new IssuedNumber(UUID.randomUUID(), "SETP", 990000000), NOW);
        String cufe = Cufe.of(invoice.cufeInput(issuer, resolution));
        invoice.identify(cufe, ElectronicInvoice.qrContent(invoice, issuer, cufe));
        return new Fixture(invoice, issuer, resolution);
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static XPath xpath() {
        XPath path = XPathFactory.newInstance().newXPath();
        Map<String, String> namespaces = Map.of("inv", StaxUblWriter.INVOICE, "cac", StaxUblWriter.CAC,
                "cbc", StaxUblWriter.CBC, "sts", StaxUblWriter.STS, "ext", StaxUblWriter.EXT);
        path.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return namespaces.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
        return path;
    }

    private record Fixture(Invoice invoice, Issuer issuer, NumberingResolution resolution) {
    }
}
