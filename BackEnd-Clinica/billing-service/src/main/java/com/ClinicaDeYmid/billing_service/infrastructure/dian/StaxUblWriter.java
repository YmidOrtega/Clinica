package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.dian.ElectronicCreditNote;
import com.ClinicaDeYmid.billing_service.application.dian.ElectronicInvoice;
import com.ClinicaDeYmid.billing_service.application.dian.UblWriter;
import com.ClinicaDeYmid.billing_service.domain.Buyer;
import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.CreditNoteLine;
import com.ClinicaDeYmid.billing_service.domain.Cufe;
import com.ClinicaDeYmid.billing_service.domain.HealthUser;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceLine;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.IssuerProfile;
import com.ClinicaDeYmid.billing_service.domain.ResolutionTerms;
import com.ClinicaDeYmid.billing_service.domain.TaxResponsibility;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Map;

@Component
class StaxUblWriter implements UblWriter {

    static final String INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    static final String CREDIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    static final String STS = "dian:gov:co:facturaelectronica:Structures-2-1";
    static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    static final String XADES = "http://uri.etsi.org/01903/v1.3.2#";
    static final String DIAN_AGENCY = "CO, DIAN (Dirección de Impuestos y Aduanas Nacionales)";
    static final String DIAN_NIT = "800197268";
    static final String NIT_SCHEME = "31";
    static final String CURRENCY = "COP";

    private static final Map<String, String> DOCUMENT_TYPES = Map.of(
            "NO_IDENTIFICADO", "AS",
            "REGISTRO_CIVIL", "11",
            "TARJETA_DE_IDENTIDAD", "12",
            "CEDULA_DE_CIUDADANIA", "13",
            "CEDULA_DE_EXTRANJERIA", "22",
            "NIT", "31",
            "PASAPORTE", "41",
            "DOCUMENTO_EXTRANJERO", "42",
            "PERMISO_ESPECIAL_DE_PERMANENCIA", "47",
            "PERMISO_POR_PROTECCION_TEMPORAL", "48");

    private final XMLOutputFactory factory = XMLOutputFactory.newFactory();

    @Override
    public String invoice(ElectronicInvoice electronic) {
        return written(xml -> write(xml, electronic));
    }

    @Override
    public String creditNote(ElectronicCreditNote electronic) {
        return written(xml -> write(xml, electronic));
    }

    private interface Content {
        void write(XMLStreamWriter xml) throws XMLStreamException;
    }

    private String written(Content content) {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter xml = factory.createXMLStreamWriter(out);
            content.write(xml);
            xml.flush();
            xml.close();
        } catch (XMLStreamException impossible) {
            throw new IllegalStateException("Cannot write the UBL invoice", impossible);
        }
        return out.toString();
    }

    private void write(XMLStreamWriter xml, ElectronicInvoice electronic) throws XMLStreamException {
        Invoice invoice = electronic.invoice();
        Issuer issuer = electronic.issuer();
        root(xml, INVOICE, "Invoice");

        xml.writeStartElement(EXT, "UBLExtensions");
        dianExtension(xml, electronic);
        signatureSlot(xml);
        healthExtension(xml, invoice, issuer, invoice.patientShare());
        xml.writeEndElement();

        basic(xml, "UBLVersionID", "UBL 2.1");
        basic(xml, "CustomizationID", invoice.patientShare().signum() > 0 ? "SS-Recaudo" : "SS-SinAporte");
        basic(xml, "ProfileID", "DIAN 2.1: Factura Electrónica de Venta");
        basic(xml, "ProfileExecutionID", issuer.environment().dianCode());
        basic(xml, "ID", invoice.number());
        basic(xml, "UUID", invoice.cufe(), "schemeID", issuer.environment().dianCode(), "schemeName", "CUFE-SHA384");
        basic(xml, "IssueDate", invoice.issuedOn().toString());
        basic(xml, "IssueTime", Cufe.time(invoice.issuedTime()));
        basic(xml, "InvoiceTypeCode", "01");
        basic(xml, "DocumentCurrencyCode", CURRENCY);
        basic(xml, "LineCountNumeric", String.valueOf(electronic.reportedLines().size()));

        supplier(xml, issuer);
        customer(xml, invoice.buyer());
        paymentMeans(xml, invoice);
        if (invoice.patientShare().signum() > 0) {
            xml.writeStartElement(CAC, "PrepaidPayment");
            basic(xml, "ID", "COPAGO");
            amount(xml, "PaidAmount", invoice.patientShare());
            basic(xml, "ReceivedDate", invoice.issuedOn().toString());
            xml.writeEndElement();
        }
        xml.writeStartElement(CAC, "LegalMonetaryTotal");
        amount(xml, "LineExtensionAmount", invoice.grossTotal());
        amount(xml, "TaxExclusiveAmount", BigDecimal.ZERO);
        amount(xml, "TaxInclusiveAmount", invoice.grossTotal());
        amount(xml, "PrepaidAmount", invoice.patientShare());
        amount(xml, "PayableAmount", invoice.payableTotal());
        xml.writeEndElement();

        int id = 1;
        for (InvoiceLine line : electronic.reportedLines()) {
            invoiceLine(xml, id++, line);
        }
        xml.writeEndElement();
        xml.writeEndDocument();
    }

    private void write(XMLStreamWriter xml, ElectronicCreditNote electronic) throws XMLStreamException {
        CreditNote note = electronic.note();
        Invoice invoice = note.invoice();
        Issuer issuer = electronic.issuer();
        root(xml, CREDIT_NOTE, "CreditNote");

        xml.writeStartElement(EXT, "UBLExtensions");
        xml.writeStartElement(EXT, "UBLExtension");
        xml.writeStartElement(EXT, "ExtensionContent");
        xml.writeStartElement(STS, "DianExtensions");
        softwareExtensions(xml, issuer, electronic.softwareId(), electronic.softwareSecurityCode(),
                ElectronicCreditNote.qrContent(note, issuer));
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
        signatureSlot(xml);
        healthExtension(xml, invoice, issuer, note.creditedShare());
        xml.writeEndElement();

        basic(xml, "UBLVersionID", "UBL 2.1");
        basic(xml, "CustomizationID", "20");
        basic(xml, "ProfileID", "DIAN 2.1: Nota Crédito de Factura Electrónica de Venta");
        basic(xml, "ProfileExecutionID", issuer.environment().dianCode());
        basic(xml, "ID", note.number());
        basic(xml, "UUID", note.cude(), "schemeID", issuer.environment().dianCode(), "schemeName", "CUDE-SHA384");
        basic(xml, "IssueDate", note.issuedOn().toString());
        basic(xml, "IssueTime", Cufe.time(note.issuedTime()));
        basic(xml, "CreditNoteTypeCode", "91");
        basic(xml, "Note", note.reason());
        basic(xml, "DocumentCurrencyCode", CURRENCY);
        basic(xml, "LineCountNumeric", String.valueOf(note.lines().size()));
        xml.writeStartElement(CAC, "DiscrepancyResponse");
        basic(xml, "ReferenceID", invoice.number());
        basic(xml, "ResponseCode", note.concept().dianCode());
        basic(xml, "Description", note.reason());
        xml.writeEndElement();
        xml.writeStartElement(CAC, "BillingReference");
        xml.writeStartElement(CAC, "InvoiceDocumentReference");
        basic(xml, "ID", invoice.number());
        basic(xml, "UUID", invoice.cufe(), "schemeName", "CUFE-SHA384");
        basic(xml, "IssueDate", invoice.issuedOn().toString());
        xml.writeEndElement();
        xml.writeEndElement();

        supplier(xml, issuer);
        customer(xml, invoice.buyer());
        if (note.creditedShare().signum() > 0) {
            xml.writeStartElement(CAC, "PrepaidPayment");
            basic(xml, "ID", "COPAGO");
            amount(xml, "PaidAmount", note.creditedShare());
            basic(xml, "ReceivedDate", invoice.issuedOn().toString());
            xml.writeEndElement();
        }
        xml.writeStartElement(CAC, "LegalMonetaryTotal");
        amount(xml, "LineExtensionAmount", note.creditedGross());
        amount(xml, "TaxExclusiveAmount", BigDecimal.ZERO);
        amount(xml, "TaxInclusiveAmount", note.creditedGross());
        amount(xml, "PrepaidAmount", note.creditedShare());
        amount(xml, "PayableAmount", note.creditedPayable());
        xml.writeEndElement();

        for (CreditNoteLine line : note.lines()) {
            xml.writeStartElement(CAC, "CreditNoteLine");
            basic(xml, "ID", String.valueOf(line.position()));
            basic(xml, "CreditedQuantity", String.valueOf(line.quantity()), "unitCode", "94");
            amount(xml, "LineExtensionAmount", line.lineTotal());
            xml.writeStartElement(CAC, "Item");
            basic(xml, "Description", line.description());
            xml.writeStartElement(CAC, "StandardItemIdentification");
            basic(xml, "ID", line.code(), "schemeID", "999", "schemeName", "CUPS");
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeStartElement(CAC, "Price");
            amount(xml, "PriceAmount", line.unitPrice());
            basic(xml, "BaseQuantity", "1", "unitCode", "94");
            xml.writeEndElement();
            xml.writeEndElement();
        }
        xml.writeEndElement();
        xml.writeEndDocument();
    }

    private void root(XMLStreamWriter xml, String namespace, String element) throws XMLStreamException {
        xml.writeStartDocument("UTF-8", "1.0");
        xml.setDefaultNamespace(namespace);
        xml.setPrefix("cac", CAC);
        xml.setPrefix("cbc", CBC);
        xml.setPrefix("ext", EXT);
        xml.setPrefix("sts", STS);
        xml.setPrefix("ds", DS);
        xml.setPrefix("xades", XADES);
        xml.writeStartElement(namespace, element);
        xml.writeDefaultNamespace(namespace);
        xml.writeNamespace("cac", CAC);
        xml.writeNamespace("cbc", CBC);
        xml.writeNamespace("ext", EXT);
        xml.writeNamespace("sts", STS);
        xml.writeNamespace("ds", DS);
        xml.writeNamespace("xades", XADES);
    }

    private void signatureSlot(XMLStreamWriter xml) throws XMLStreamException {
        xml.writeStartElement(EXT, "UBLExtension");
        xml.writeEmptyElement(EXT, "ExtensionContent");
        xml.writeEndElement();
    }

    private void dianExtension(XMLStreamWriter xml, ElectronicInvoice electronic) throws XMLStreamException {
        Invoice invoice = electronic.invoice();
        Issuer issuer = electronic.issuer();
        ResolutionTerms terms = electronic.resolution().terms();
        xml.writeStartElement(EXT, "UBLExtension");
        xml.writeStartElement(EXT, "ExtensionContent");
        xml.writeStartElement(STS, "DianExtensions");
        xml.writeStartElement(STS, "InvoiceControl");
        text(xml, STS, "InvoiceAuthorization", terms.resolutionNumber());
        xml.writeStartElement(STS, "AuthorizationPeriod");
        basic(xml, "StartDate", terms.validFrom().toString());
        basic(xml, "EndDate", terms.validUntil().toString());
        xml.writeEndElement();
        xml.writeStartElement(STS, "AuthorizedInvoices");
        if (!terms.prefix().isEmpty()) {
            text(xml, STS, "Prefix", terms.prefix());
        }
        text(xml, STS, "From", String.valueOf(terms.rangeFrom()));
        text(xml, STS, "To", String.valueOf(terms.rangeTo()));
        xml.writeEndElement();
        xml.writeEndElement();
        softwareExtensions(xml, issuer, electronic.softwareId(), electronic.softwareSecurityCode(),
                invoice.qrContent());
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void softwareExtensions(XMLStreamWriter xml, Issuer issuer, String softwareId, String securityCode,
                                    String qrContent) throws XMLStreamException {
        xml.writeStartElement(STS, "InvoiceSource");
        basic(xml, "IdentificationCode", "CO", "listAgencyID", "6", "listAgencyName",
                "United Nations Economic Commission for Europe", "listSchemeURI",
                "urn:oasis:names:specification:ubl:codelist:gc:CountryIdentificationCode-2.1");
        xml.writeEndElement();
        xml.writeStartElement(STS, "SoftwareProvider");
        text(xml, STS, "ProviderID", issuer.nit().number(), "schemeAgencyID", "195", "schemeAgencyName", DIAN_AGENCY,
                "schemeID", String.valueOf(issuer.nit().verificationDigit()), "schemeName", NIT_SCHEME);
        text(xml, STS, "SoftwareID", softwareId, "schemeAgencyID", "195", "schemeAgencyName", DIAN_AGENCY);
        xml.writeEndElement();
        text(xml, STS, "SoftwareSecurityCode", securityCode, "schemeAgencyID", "195", "schemeAgencyName", DIAN_AGENCY);
        xml.writeStartElement(STS, "AuthorizationProvider");
        text(xml, STS, "AuthorizationProviderID", DIAN_NIT, "schemeAgencyID", "195", "schemeAgencyName", DIAN_AGENCY,
                "schemeID", "4", "schemeName", NIT_SCHEME);
        xml.writeEndElement();
        text(xml, STS, "QRCode", qrContent);
    }

    private void healthExtension(XMLStreamWriter xml, Invoice invoice, Issuer issuer, BigDecimal share)
            throws XMLStreamException {
        HealthUser user = invoice.user();
        xml.writeStartElement(EXT, "UBLExtension");
        xml.writeStartElement(EXT, "ExtensionContent");
        xml.writeStartElement("CustomTagGeneral");
        xml.writeStartElement("Interoperabilidad");
        xml.writeStartElement("Group");
        xml.writeAttribute("schemeName", "Sector Salud");
        xml.writeStartElement("Collection");
        xml.writeAttribute("schemeName", "Usuario");
        information(xml, "CODIGO_PRESTADOR", issuer.profile().healthProviderCode());
        information(xml, "TIPO_DOCUMENTO_IDENTIFICACION", documentType(user.documentType()));
        information(xml, "NUMERO_DOCUMENTO_IDENTIFICACION", user.documentNumber());
        information(xml, "NOMBRE_USUARIO", user.name());
        if (invoice.contractNumber() != null) {
            information(xml, "NUMERO_CONTRATO", invoice.contractNumber());
        }
        information(xml, "COPAGO", Cufe.amount(share));
        information(xml, "PAGOS_COMPARTIDOS", Cufe.amount(share));
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void supplier(XMLStreamWriter xml, Issuer issuer) throws XMLStreamException {
        IssuerProfile profile = issuer.profile();
        String nit = issuer.nit().number();
        String digit = String.valueOf(issuer.nit().verificationDigit());
        xml.writeStartElement(CAC, "AccountingSupplierParty");
        basic(xml, "AdditionalAccountID", profile.personType().dianCode());
        xml.writeStartElement(CAC, "Party");
        xml.writeStartElement(CAC, "PartyName");
        basic(xml, "Name", profile.tradeName() == null ? profile.legalName() : profile.tradeName());
        xml.writeEndElement();
        xml.writeStartElement(CAC, "PhysicalLocation");
        address(xml, "Address", profile);
        xml.writeEndElement();
        xml.writeStartElement(CAC, "PartyTaxScheme");
        basic(xml, "RegistrationName", profile.legalName());
        basic(xml, "CompanyID", nit, "schemeAgencyID", "195", "schemeAgencyName", DIAN_AGENCY, "schemeID", digit,
                "schemeName", NIT_SCHEME);
        basic(xml, "TaxLevelCode", TaxResponsibility.joined(profile.taxResponsibilities()), "listName", "48");
        address(xml, "RegistrationAddress", profile);
        taxScheme(xml, profile.taxScheme().dianCode(), profile.taxScheme().dianName());
        xml.writeEndElement();
        xml.writeStartElement(CAC, "PartyLegalEntity");
        basic(xml, "RegistrationName", profile.legalName());
        basic(xml, "CompanyID", nit, "schemeAgencyID", "195", "schemeAgencyName", DIAN_AGENCY, "schemeID", digit,
                "schemeName", NIT_SCHEME);
        xml.writeEndElement();
        xml.writeStartElement(CAC, "Contact");
        basic(xml, "Telephone", profile.phone());
        basic(xml, "ElectronicMail", profile.email());
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void customer(XMLStreamWriter xml, Buyer buyer) throws XMLStreamException {
        boolean payer = buyer.kind() == Buyer.Kind.PAYER;
        String number = Cufe.withoutVerificationDigit(buyer.documentNumber());
        String scheme = payer ? NIT_SCHEME : documentType(buyer.documentType());
        xml.writeStartElement(CAC, "AccountingCustomerParty");
        basic(xml, "AdditionalAccountID", payer ? "1" : "2");
        xml.writeStartElement(CAC, "Party");
        xml.writeStartElement(CAC, "PartyIdentification");
        if (payer && buyer.documentNumber().contains("-")) {
            basic(xml, "ID", number, "schemeID", buyer.documentNumber().substring(buyer.documentNumber().indexOf('-') + 1),
                    "schemeName", scheme);
        } else {
            basic(xml, "ID", number, "schemeName", scheme);
        }
        xml.writeEndElement();
        xml.writeStartElement(CAC, "PartyName");
        basic(xml, "Name", buyer.name());
        xml.writeEndElement();
        xml.writeStartElement(CAC, "PartyTaxScheme");
        basic(xml, "RegistrationName", buyer.name());
        basic(xml, "CompanyID", number, "schemeAgencyID", "195", "schemeAgencyName", DIAN_AGENCY, "schemeName", scheme);
        basic(xml, "TaxLevelCode", "R-99-PN", "listName", "48");
        taxScheme(xml, "ZZ", "No aplica");
        xml.writeEndElement();
        xml.writeStartElement(CAC, "PartyLegalEntity");
        basic(xml, "RegistrationName", buyer.name());
        basic(xml, "CompanyID", number, "schemeAgencyID", "195", "schemeAgencyName", DIAN_AGENCY, "schemeName", scheme);
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void paymentMeans(XMLStreamWriter xml, Invoice invoice) throws XMLStreamException {
        boolean credit = invoice.buyer().kind() == Buyer.Kind.PAYER;
        xml.writeStartElement(CAC, "PaymentMeans");
        basic(xml, "ID", credit ? "2" : "1");
        basic(xml, "PaymentMeansCode", credit ? "ZZZ" : "10");
        if (credit) {
            basic(xml, "PaymentDueDate", invoice.issuedOn().plusDays(30).toString());
        }
        xml.writeEndElement();
    }

    private void invoiceLine(XMLStreamWriter xml, int id, InvoiceLine line) throws XMLStreamException {
        xml.writeStartElement(CAC, "InvoiceLine");
        basic(xml, "ID", String.valueOf(id));
        basic(xml, "InvoicedQuantity", String.valueOf(line.quantity()), "unitCode", "94");
        amount(xml, "LineExtensionAmount", line.lineTotal());
        xml.writeStartElement(CAC, "Item");
        basic(xml, "Description", line.description());
        xml.writeStartElement(CAC, "StandardItemIdentification");
        basic(xml, "ID", line.code(), "schemeID", "999", "schemeName",
                line.kind() == InvoiceLine.Kind.PACKAGE ? "Paquete" : "CUPS");
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeStartElement(CAC, "Price");
        amount(xml, "PriceAmount", line.unitPrice());
        basic(xml, "BaseQuantity", "1", "unitCode", "94");
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void address(XMLStreamWriter xml, String element, IssuerProfile profile) throws XMLStreamException {
        xml.writeStartElement(CAC, element);
        basic(xml, "ID", profile.municipalityCode());
        basic(xml, "CityName", profile.cityName());
        if (profile.postalCode() != null) {
            basic(xml, "PostalZone", profile.postalCode());
        }
        basic(xml, "CountrySubentity", profile.departmentName());
        basic(xml, "CountrySubentityCode", profile.departmentCode());
        xml.writeStartElement(CAC, "AddressLine");
        basic(xml, "Line", profile.addressLine());
        xml.writeEndElement();
        xml.writeStartElement(CAC, "Country");
        basic(xml, "IdentificationCode", "CO");
        basic(xml, "Name", "Colombia", "languageID", "es");
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void taxScheme(XMLStreamWriter xml, String code, String name) throws XMLStreamException {
        xml.writeStartElement(CAC, "TaxScheme");
        basic(xml, "ID", code);
        basic(xml, "Name", name);
        xml.writeEndElement();
    }

    private void information(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement("AdditionalInformation");
        xml.writeStartElement("Name");
        xml.writeCharacters(name);
        xml.writeEndElement();
        xml.writeStartElement("Value");
        xml.writeCharacters(value);
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void amount(XMLStreamWriter xml, String element, BigDecimal value) throws XMLStreamException {
        basic(xml, element, Cufe.amount(value), "currencyID", CURRENCY);
    }

    private void basic(XMLStreamWriter xml, String element, String value, String... attributes) throws XMLStreamException {
        text(xml, CBC, element, value, attributes);
    }

    private void text(XMLStreamWriter xml, String namespace, String element, String value, String... attributes)
            throws XMLStreamException {
        xml.writeStartElement(namespace, element);
        for (int index = 0; index < attributes.length; index += 2) {
            xml.writeAttribute(attributes[index], attributes[index + 1]);
        }
        xml.writeCharacters(value);
        xml.writeEndElement();
    }

    static String documentType(String type) {
        String code = DOCUMENT_TYPES.get(type);
        if (code == null) {
            throw new IllegalStateException("No DIAN document type for " + type);
        }
        return code;
    }
}
