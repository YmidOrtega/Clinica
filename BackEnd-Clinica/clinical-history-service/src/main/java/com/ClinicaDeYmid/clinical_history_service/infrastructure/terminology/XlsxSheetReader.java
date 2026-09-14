package com.ClinicaDeYmid.clinical_history_service.infrastructure.terminology;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class XlsxSheetReader {

    static final long MAX_UNCOMPRESSED_BYTES = 200L * 1024 * 1024;
    private static final int MAX_ENTRIES = 200;
    private static final String RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private XlsxSheetReader() {
    }

    static List<Map<String, String>> read(byte[] workbook, String sheetName) {
        Map<String, byte[]> entries = unzip(workbook);
        String sheetId = sheetRelationship(entries.get("xl/workbook.xml"), sheetName);
        String target = relationshipTarget(entries.get("xl/_rels/workbook.xml.rels"), sheetId);
        byte[] sheet = entries.get(target.startsWith("/") ? target.substring(1) : "xl/" + target);
        if (sheet == null) {
            throw new InvalidWorkbookException("La hoja '" + sheetName + "' no está en el archivo");
        }
        return rows(sheet, sharedStrings(entries.get("xl/sharedStrings.xml")));
    }

    private static Map<String, byte[]> unzip(byte[] workbook) {
        Map<String, byte[]> entries = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(workbook))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entries.size() >= MAX_ENTRIES) {
                    throw new InvalidWorkbookException("El archivo tiene demasiadas partes para ser una tabla CIE-10");
                }
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED_BYTES) {
                        throw new InvalidWorkbookException("El archivo descomprimido supera el tamaño permitido");
                    }
                    content.write(buffer, 0, read);
                }
                entries.put(entry.getName(), content.toByteArray());
            }
        } catch (IOException ex) {
            throw new InvalidWorkbookException("El archivo no es un libro de Excel (.xlsx) válido");
        }
        if (!entries.containsKey("xl/workbook.xml") || !entries.containsKey("xl/_rels/workbook.xml.rels")) {
            throw new InvalidWorkbookException("El archivo no es un libro de Excel (.xlsx) válido");
        }
        return entries;
    }

    private static String sheetRelationship(byte[] workbook, String sheetName) {
        try {
            XMLStreamReader xml = parser(workbook);
            while (xml.hasNext()) {
                if (xml.next() == XMLStreamConstants.START_ELEMENT && xml.getLocalName().equals("sheet")
                        && sheetName.equals(xml.getAttributeValue(null, "name"))) {
                    return xml.getAttributeValue(RELATIONSHIPS_NS, "id");
                }
            }
        } catch (XMLStreamException ex) {
            throw new InvalidWorkbookException("El libro de Excel está dañado");
        }
        throw new InvalidWorkbookException("El archivo no tiene la hoja '" + sheetName + "'");
    }

    private static String relationshipTarget(byte[] relationships, String id) {
        try {
            XMLStreamReader xml = parser(relationships);
            while (xml.hasNext()) {
                if (xml.next() == XMLStreamConstants.START_ELEMENT && xml.getLocalName().equals("Relationship")
                        && id.equals(xml.getAttributeValue(null, "Id"))) {
                    return xml.getAttributeValue(null, "Target");
                }
            }
        } catch (XMLStreamException ex) {
            throw new InvalidWorkbookException("El libro de Excel está dañado");
        }
        throw new InvalidWorkbookException("El libro de Excel está dañado");
    }

    private static List<String> sharedStrings(byte[] content) {
        List<String> strings = new ArrayList<>();
        if (content == null) {
            return strings;
        }
        try {
            XMLStreamReader xml = parser(content);
            StringBuilder current = null;
            boolean inText = false;
            while (xml.hasNext()) {
                switch (xml.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        if (xml.getLocalName().equals("si")) {
                            current = new StringBuilder();
                        } else if (xml.getLocalName().equals("t")) {
                            inText = true;
                        } else if (xml.getLocalName().equals("rPh")) {
                            skip(xml);
                        }
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (inText && current != null) {
                            current.append(xml.getText());
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        if (xml.getLocalName().equals("t")) {
                            inText = false;
                        } else if (xml.getLocalName().equals("si") && current != null) {
                            strings.add(current.toString());
                            current = null;
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (XMLStreamException ex) {
            throw new InvalidWorkbookException("El libro de Excel está dañado");
        }
        return strings;
    }

    private static List<Map<String, String>> rows(byte[] sheet, List<String> strings) {
        List<Map<String, String>> rows = new ArrayList<>();
        try {
            XMLStreamReader xml = parser(sheet);
            Map<String, String> row = null;
            String column = null;
            String type = null;
            StringBuilder value = null;
            while (xml.hasNext()) {
                switch (xml.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        switch (xml.getLocalName()) {
                            case "row" -> row = new TreeMap<>();
                            case "c" -> {
                                column = columnOf(xml.getAttributeValue(null, "r"));
                                type = xml.getAttributeValue(null, "t");
                                value = new StringBuilder();
                            }
                            case "v", "t" -> {
                                if (value != null) {
                                    value.append(xml.getElementText());
                                }
                            }
                            default -> {
                            }
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        if (xml.getLocalName().equals("c") && row != null && column != null && value != null) {
                            String text = "s".equals(type) && !value.isEmpty() ? sharedString(strings, value.toString()) : value.toString();
                            if (!text.isBlank()) {
                                row.put(column, text.strip());
                            }
                            value = null;
                        } else if (xml.getLocalName().equals("row") && row != null) {
                            rows.add(row);
                            row = null;
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (XMLStreamException ex) {
            throw new InvalidWorkbookException("La hoja del libro de Excel está dañada");
        }
        return rows;
    }

    private static String sharedString(List<String> strings, String index) {
        try {
            return strings.get(Integer.parseInt(index));
        } catch (RuntimeException ex) {
            throw new InvalidWorkbookException("El libro de Excel está dañado");
        }
    }

    private static String columnOf(String reference) {
        if (reference == null) {
            return null;
        }
        int end = 0;
        while (end < reference.length() && Character.isLetter(reference.charAt(end))) {
            end++;
        }
        return reference.substring(0, end);
    }

    private static void skip(XMLStreamReader xml) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private static XMLStreamReader parser(byte[] content) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        InputStream input = new ByteArrayInputStream(content);
        return factory.createXMLStreamReader(input);
    }
}
