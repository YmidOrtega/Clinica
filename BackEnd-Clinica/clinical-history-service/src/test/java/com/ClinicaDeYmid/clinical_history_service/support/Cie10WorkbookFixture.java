package com.ClinicaDeYmid.clinical_history_service.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Cie10WorkbookFixture {

    public static final String[] HEADER = {"Capitulo", "Nombre Capitulo", "Código de la CIE-10 tres caracteres",
            "Descripcion  de codigos a tres caracteres", "Código de la CIE-10 cuatro caracteres", "Descripcion  de códigos a cuatro caracteres"};

    private Cie10WorkbookFixture() {
    }

    public static byte[] standard() {
        return workbook("08-02-2021", List.of(
                row("1", "Ciertas enfermedades infecciosas y parasitarias (A00-B99)", "A00", "Colera", "A000",
                        "Colera debido a Vibrio cholerae 01, biotipo cholerae"),
                row("4", "Enfermedades endocrinas, nutricionales y metabolicas (E00-E90)", "E11", "Diabetes Mellitus No Insulinodependiente",
                        "E119", "Diabetes mellitus no insulinodependiente sin mencion de complicacion"),
                row("9", "Enfermedades del sistema circulatorio (I00-I99)", "I10", "Hipertension Esencial (Primaria)", "I10X",
                        "Hipertension esencial (primaria)"),
                row("9", "Enfermedades del sistema circulatorio (I00-I99)", "I69", "Secuelas De Enfermedades Cerebrovascular", "I700",
                        "Aterosclerosis de la aorta"),
                row("10", "Enfermedades del sistema respiratorio (J00-J99)", "J45", "Asma", "J459", "Asma, no especificado")));
    }

    public static String[] row(String... cells) {
        return cells;
    }

    public static byte[] workbook(String updatedOn, List<String[]> dataRows) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{});
        rows.add(new String[]{"TITULO: Catálogo de patologías CIE-10"});
        rows.add(new String[]{"FECHA DE ACTUALIZACION: " + updatedOn});
        rows.add(new String[]{});
        rows.add(HEADER);
        rows.addAll(dataRows);
        Map<String, Integer> shared = new LinkedHashMap<>();
        StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            sheet.append("<row r=\"").append(r + 1).append("\">");
            String[] cells = rows.get(r);
            for (int c = 0; c < cells.length; c++) {
                String reference = (char) ('A' + c) + String.valueOf(r + 1);
                if (c == 0 && r > 4 && cells[c].matches("\\d+")) {
                    sheet.append("<c r=\"").append(reference).append("\"><v>").append(cells[c]).append("</v></c>");
                } else {
                    int index = shared.computeIfAbsent(cells[c], key -> shared.size());
                    sheet.append("<c r=\"").append(reference).append("\" t=\"s\"><v>").append(index).append("</v></c>");
                }
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");
        StringBuilder strings = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        shared.keySet().forEach(value -> strings.append("<si><t xml:space=\"preserve\">").append(escape(value)).append("</t></si>"));
        strings.append("</sst>");
        return zip(Map.of(
                "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                        + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
                        + "<sheet name=\"Descripcion\" sheetId=\"2\" r:id=\"rId2\"/><sheet name=\"Final\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
                "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                        + "<Relationship Id=\"rId2\" Type=\"worksheet\" Target=\"worksheets/sheet2.xml\"/></Relationships>",
                "xl/worksheets/sheet1.xml", sheet.toString(),
                "xl/worksheets/sheet2.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData/></worksheet>",
                "xl/sharedStrings.xml", strings.toString()));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static byte[] zip(Map<String, String> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return bytes.toByteArray();
    }
}
