package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGeneratorService {

    private static final Font TITLE_FONT = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
    private static final Font SUBTITLE_FONT = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
    private static final Font NORMAL_FONT = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Genera un PDF a partir de un AttentionResponseDto
     * @param attention El DTO de atención
     * @return Array de bytes con el contenido del PDF
     */
    public byte[] generateAttentionPdf(AttentionResponseDto attention) {
        try {
            Document document = new Document(PageSize.A4);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, outputStream);

            document.open();

            // Título del documento
            Paragraph title = new Paragraph("Informe de Atención Médica", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(Chunk.NEWLINE);

            // Información básica de la atención
            document.add(new Paragraph("ID de Atención: " + attention.id(), BOLD_FONT));
            document.add(new Paragraph("Estado: " + (attention.status() != null ? attention.status().toString() : "N/A"), NORMAL_FONT));
            document.add(new Paragraph("Fecha de creación: " +
                    (attention.createdAt() != null ? attention.createdAt().format(DATE_FORMATTER) : "N/A"), NORMAL_FONT));
            document.add(Chunk.NEWLINE);

            // Información del paciente
            if (attention.patientDetails() != null) {
                document.add(new Paragraph("Información del Paciente", SUBTITLE_FONT));
                document.add(new Paragraph("Nombre: " + attention.patientDetails().fullName(), NORMAL_FONT));
                document.add(new Paragraph("Documento: " + attention.patientDetails().identificationNumber(), NORMAL_FONT));
                document.add(new Paragraph("Tipo de documento: " + attention.patientDetails().dateOfBirth(), NORMAL_FONT));
                document.add(Chunk.NEWLINE);
            }

            // Información del médico
            if (attention.doctorDetails() != null) {
                document.add(new Paragraph("Médico Tratante", SUBTITLE_FONT));
                document.add(new Paragraph("Nombre: " + attention.doctorDetails().fullName(), NORMAL_FONT));
                document.add(new Paragraph("Especialidad: " +
                        (attention.doctorDetails().specialties() != null && !attention.doctorDetails().specialties().isEmpty() ?
                                attention.doctorDetails().specialties().get(0).name() : "N/A"), NORMAL_FONT));
                document.add(Chunk.NEWLINE);
            }

            // Códigos diagnósticos
            if (attention.diagnosticCodes() != null && !attention.diagnosticCodes().isEmpty()) {
                document.add(new Paragraph("Códigos diagnósticos: " + String.join(", ", attention.diagnosticCodes()), NORMAL_FONT));
            }

            // Observaciones
            if (attention.observations() != null && !attention.observations().isEmpty()) {
                document.add(Chunk.NEWLINE);
                document.add(new Paragraph("Observaciones", SUBTITLE_FONT));
                document.add(new Paragraph(attention.observations(), NORMAL_FONT));
            }

            // Información del acompañante
            if (attention.companion() != null) {
                document.add(Chunk.NEWLINE);
                document.add(new Paragraph("Información del Acompañante", SUBTITLE_FONT));
                document.add(new Paragraph("Nombre: " + attention.companion().fullName(), NORMAL_FONT));
                document.add(new Paragraph("Parestesco: " + attention.companion().relationship(), NORMAL_FONT));
                document.add(new Paragraph("Numero Celular: " + attention.companion().phoneNumber(), NORMAL_FONT));
            }

            // Proveedores de salud
            if (attention.healthProviderDetails() != null && !attention.healthProviderDetails().isEmpty()) {
                document.add(Chunk.NEWLINE);
                document.add(new Paragraph("Proveedores de Salud", SUBTITLE_FONT));

                PdfPTable table = new PdfPTable(3);
                table.setWidthPercentage(100);

                // Encabezados
                PdfPCell cell1 = new PdfPCell(new Phrase("NIT", BOLD_FONT));
                PdfPCell cell2 = new PdfPCell(new Phrase("Nombre", BOLD_FONT));
                PdfPCell cell3 = new PdfPCell(new Phrase("Tipo", BOLD_FONT));

                table.addCell(cell1);
                table.addCell(cell2);
                table.addCell(cell3);

                // Datos
                for (var provider : attention.healthProviderDetails()) {
                    table.addCell(new Phrase(provider.nit(), NORMAL_FONT));
                    table.addCell(new Phrase(provider.socialReason(), NORMAL_FONT));
                    table.addCell(new Phrase(provider.typeProvider(), NORMAL_FONT));
                }

                document.add(table);
            }

            document.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Error al generar PDF de atención: {}", e.getMessage());
            throw new RuntimeException("Error al generar PDF de atención", e);
        }
    }
}