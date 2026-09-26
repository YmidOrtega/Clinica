package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.support.JwtTestTokens;
import com.ClinicaDeYmid.billing_service.support.LocalSeal;
import com.ClinicaDeYmid.commons.documents.Documents;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RepresentationApiIT extends InvoicingIntegrationTest {

    private static final String REPRESENTATIONS = "/api/v1/billing/graphic-representations";

    @Autowired
    private DianDelivery delivery;

    @Test
    void rendersASealedPdfOfTheInvoiceWithAReadableQr() throws Exception {
        anActiveResolution("SETG");
        String invoice = acceptedInvoice(delivery);
        String issued = as("BILLING", get(INVOICES + "/" + invoice)).andReturn().getResponse().getContentAsString();

        MockHttpServletResponse response = as("BILLING", post(INVOICES + "/" + invoice + "/graphic-representation"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + JsonPath.read(issued, "$.number") + ".pdf\""))
                .andReturn().getResponse();
        byte[] pdf = response.getContentAsByteArray();

        assertThat(response.getHeader("X-Document-Sha256")).isEqualTo(Documents.sha256(pdf));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Factura electrónica de venta " + JsonPath.read(issued, "$.number"),
                    "Documento de pruebas de habilitación", "Clínica de Ymid S.A.S.", "NIT 800197268-4",
                    "Nueva EPS S.A.", "Ana María Restrepo Gómez", "890201", "Total a pagar", "$ 10.000,00",
                    "Validado por la DIAN", "Autorización de numeración de facturación DIAN N.º 18760000001",
                    LocalSeal.KEY_ID);
            assertThat(text.replaceAll("\\s", "")).contains((String) JsonPath.read(issued, "$.cufe"));
            assertThat(document.getDocumentInformation().getProducer()).isEqualTo("billing-service");
            assertThat(qrOf(document.getPage(0))).isEqualTo(JsonPath.read(issued, "$.qrContent"));
        }

        String id = response.getHeader("X-Representation-Id");
        as("BILLING", get(REPRESENTATIONS + "/" + id))
                .andExpect(jsonPath("$.sha256").value(Documents.sha256(pdf)))
                .andExpect(jsonPath("$.keyId").value(LocalSeal.KEY_ID))
                .andExpect(jsonPath("$.documentNumber").value((String) JsonPath.read(issued, "$.number")));
        verify(id, pdf).andExpect(jsonPath("$.authentic").value(true));
        byte[] tampered = Arrays.copyOf(pdf, pdf.length);
        tampered[tampered.length / 2] ^= 1;
        verify(id, tampered)
                .andExpect(jsonPath("$.documentMatches").value(false))
                .andExpect(jsonPath("$.sealValid").value(true))
                .andExpect(jsonPath("$.authentic").value(false));

        String number = JsonPath.read(issued, "$.number");
        mockMvc.perform(post(REPRESENTATIONS + "/verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"" + number + "\",\"sha256\":\"" + Documents.sha256(pdf) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authentic").value(true));
        mockMvc.perform(post(REPRESENTATIONS + "/verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"" + number + "\",\"sha256\":\"" + Documents.sha256(tampered) + "\"}"))
                .andExpect(jsonPath("$.authentic").value(false));
        mockMvc.perform(get("/api/v1/billing/seal-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + LocalSeal.KEY_ID + "']").exists());
    }

    @Test
    void eachRequestIsANewSealedRecordAndAVoidedInvoiceSaysSo() throws Exception {
        anActiveResolution("SETH");
        String invoice = acceptedInvoice(delivery);
        String first = as("BILLING", post(INVOICES + "/" + invoice + "/graphic-representation"))
                .andReturn().getResponse().getHeader("X-Representation-Id");

        String note = JsonPath.read(change("BILLING", post(INVOICES + "/" + invoice + "/credit-notes"), 1,
                "{\"concept\":\"VOID\",\"reason\":\"Error en el pagador\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        MockHttpServletResponse invoicePdf = as("BILLING", post(INVOICES + "/" + invoice + "/graphic-representation"))
                .andExpect(status().isOk()).andReturn().getResponse();
        MockHttpServletResponse notePdf = as("BILLING",
                post("/api/v1/billing/credit-notes/" + note + "/graphic-representation"))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(invoicePdf.getHeader("X-Representation-Id")).isNotEqualTo(first);
        try (PDDocument document = Loader.loadPDF(invoicePdf.getContentAsByteArray())) {
            assertThat(new PDFTextStripper().getText(document)).contains("ANULADA", "Error en el pagador");
        }
        try (PDDocument document = Loader.loadPDF(notePdf.getContentAsByteArray())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Nota crédito electrónica", "Factura que se acredita", "CUDE",
                    "2 · Anulación de factura electrónica", "Pendiente de validación por la DIAN");
            assertThat(qrOf(document.getPage(0))).startsWith("NumNC: ");
        }
    }

    @Test
    void aDraftHasNoGraphicRepresentationAndTheRecordNeedsCredentials() throws Exception {
        Episode episode = outpatient("COVERED");
        String draft = JsonPath.read(as("BILLING", post(INVOICES), drafting(episode, confirmedSale(episode)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");

        as("BILLING", post(INVOICES + "/" + draft + "/graphic-representation"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_ISSUED"));
        mockMvc.perform(post(INVOICES + "/" + draft + "/graphic-representation")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(REPRESENTATIONS + "/" + java.util.UUID.randomUUID())).andExpect(status().isUnauthorized());
        as("BILLING", get(REPRESENTATIONS + "/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions verify(String id, byte[] pdf) throws Exception {
        return mockMvc.perform(multipart(REPRESENTATIONS + "/" + id + "/verification")
                .file(new MockMultipartFile("document", "factura.pdf", MediaType.APPLICATION_PDF_VALUE, pdf))
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer("BILLING")));
    }

    private static String qrOf(PDPage page) throws Exception {
        COSName name = page.getResources().getXObjectNames().iterator().next();
        BufferedImage modules = ((PDImageXObject) page.getResources().getXObject(name)).getImage();
        int scale = 8;
        BufferedImage large = new BufferedImage(modules.getWidth() * scale, modules.getHeight() * scale,
                BufferedImage.TYPE_INT_RGB);
        large.getGraphics().drawImage(modules.getScaledInstance(large.getWidth(), large.getHeight(),
                Image.SCALE_FAST), 0, 0, null);
        int[] pixels = large.getRGB(0, 0, large.getWidth(), large.getHeight(), null, 0, large.getWidth());
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                        new RGBLuminanceSource(large.getWidth(), large.getHeight(), pixels))),
                Map.of(DecodeHintType.CHARACTER_SET, "UTF-8")).getText();
    }
}
