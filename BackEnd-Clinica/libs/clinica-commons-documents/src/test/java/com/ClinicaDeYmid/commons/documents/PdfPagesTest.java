package com.ClinicaDeYmid.commons.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class PdfPagesTest {

    @Test
    void drawsPicturesAndNamesItsProducer() throws Exception {
        BufferedImage square = new BufferedImage(21, 21, BufferedImage.TYPE_BYTE_BINARY);
        byte[] pdf;
        try (PdfPages pages = new PdfPages()) {
            pages.line(PdfPages.Style.TITLE, "Factura electrónica");
            pages.picture(square, 90, 90);
            pages.line(PdfPages.Style.TEXT, "Después del código");
            pdf = pages.finish("Clínica", "Factura FE1", "billing-service");
        }

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getDocumentInformation().getProducer()).isEqualTo("billing-service");
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Factura FE1");
            COSName name = document.getPage(0).getResources().getXObjectNames().iterator().next();
            PDImageXObject image = (PDImageXObject) document.getPage(0).getResources().getXObject(name);
            assertThat(image.getWidth()).isEqualTo(21);
            assertThat(new PDFTextStripper().getText(document)).contains("Factura electrónica", "Después del código");
        }
    }
}
