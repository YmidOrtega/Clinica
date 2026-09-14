package com.ClinicaDeYmid.clinical_history_service.infrastructure.terminology;

import com.ClinicaDeYmid.clinical_history_service.domain.terminology.Concept;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Cie10Workbook {

    static final String SHEET = "Final";

    private static final Pattern CODE = Pattern.compile("^[A-Z][0-9]{2}[0-9X]$");
    private static final Pattern CATEGORY = Pattern.compile("^[A-Z][0-9]{2}$");
    private static final Pattern UPDATED = Pattern.compile("FECHA DE ACTUALIZACI[OÓ]N:\\s*(\\d{2}-\\d{2}-\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

    record Parsed(String version, List<Concept> concepts, List<String> warnings) {
    }

    private Cie10Workbook() {
    }

    static Parsed parse(byte[] workbook) {
        List<Map<String, String>> rows = XlsxSheetReader.read(workbook, SHEET);
        String version = null;
        int header = -1;
        for (int index = 0; index < rows.size() && header < 0; index++) {
            Map<String, String> row = rows.get(index);
            Matcher updated = UPDATED.matcher(row.getOrDefault("A", ""));
            if (updated.find()) {
                version = versionOf(updated.group(1));
            }
            if ("Capitulo".equalsIgnoreCase(row.get("A")) && row.getOrDefault("E", "").toLowerCase().startsWith("código de la cie-10 cuatro")) {
                header = index;
            }
        }
        if (header < 0) {
            throw new InvalidWorkbookException("No se encontró el encabezado de la tabla CIE-10 de SISPRO en la hoja 'Final'");
        }
        if (version == null) {
            throw new InvalidWorkbookException("No se encontró la fecha de actualización del catálogo");
        }
        List<Concept> concepts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (int index = header + 1; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            if (row.isEmpty()) {
                continue;
            }
            Concept concept = conceptOf(row, index, warnings);
            if (!codes.add(concept.code())) {
                throw new InvalidWorkbookException("El código " + concept.code() + " aparece repetido");
            }
            concepts.add(concept);
        }
        if (concepts.isEmpty()) {
            throw new InvalidWorkbookException("La tabla CIE-10 no tiene códigos");
        }
        return new Parsed(version, List.copyOf(concepts), List.copyOf(warnings));
    }

    private static Concept conceptOf(Map<String, String> row, int index, List<String> warnings) {
        String code = row.getOrDefault("E", "").toUpperCase();
        String category = row.getOrDefault("C", "").toUpperCase();
        String display = row.get("F");
        String categoryDisplay = row.get("D");
        String chapterDisplay = row.get("B");
        if (!CODE.matcher(code).matches() || !CATEGORY.matcher(category).matches() || display == null || categoryDisplay == null
                || chapterDisplay == null) {
            throw new InvalidWorkbookException("La fila " + (index + 1) + " de la hoja 'Final' no tiene el formato de la tabla CIE-10");
        }
        if (!code.startsWith(category)) {
            warnings.add("El código " + code + " aparece bajo la categoría " + category + "; se usa " + code.substring(0, 3)
                    + " y queda sin título de categoría");
            category = code.substring(0, 3);
            categoryDisplay = null;
        }
        int chapter;
        try {
            chapter = Integer.parseInt(row.getOrDefault("A", ""));
        } catch (NumberFormatException ex) {
            throw new InvalidWorkbookException("La fila " + (index + 1) + " tiene un capítulo inválido");
        }
        if (chapter < 1 || chapter > 22 || display.length() > 300 || (categoryDisplay != null && categoryDisplay.length() > 300)
                || chapterDisplay.length() > 200) {
            throw new InvalidWorkbookException("La fila " + (index + 1) + " tiene valores fuera de rango");
        }
        return new Concept(code, display, category, categoryDisplay, chapter, chapterDisplay);
    }

    private static String versionOf(String dayMonthYear) {
        try {
            return LocalDate.parse(dayMonthYear, DAY_MONTH_YEAR).toString();
        } catch (DateTimeParseException ex) {
            throw new InvalidWorkbookException("La fecha de actualización del catálogo no es válida");
        }
    }
}
