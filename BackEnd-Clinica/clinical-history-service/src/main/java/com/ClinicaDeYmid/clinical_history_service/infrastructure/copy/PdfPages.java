package com.ClinicaDeYmid.clinical_history_service.infrastructure.copy;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PdfPages implements AutoCloseable {

    private static final float MARGIN = 50;
    private static final float BOTTOM = 60;

    enum Style {
        TITLE(Standard14Fonts.FontName.HELVETICA_BOLD, 16, 22),
        SECTION(Standard14Fonts.FontName.HELVETICA_BOLD, 12, 18),
        SUBSECTION(Standard14Fonts.FontName.HELVETICA_BOLD, 10, 15),
        LABEL(Standard14Fonts.FontName.HELVETICA_BOLD, 9, 12),
        TEXT(Standard14Fonts.FontName.HELVETICA, 9, 12),
        MUTED(Standard14Fonts.FontName.HELVETICA_OBLIQUE, 8, 11),
        MONO(Standard14Fonts.FontName.COURIER, 7.5f, 10);

        final Standard14Fonts.FontName font;
        final float size;
        final float leading;

        Style(Standard14Fonts.FontName font, float size, float leading) {
            this.font = font;
            this.size = size;
            this.leading = leading;
        }
    }

    private final PDDocument document = new PDDocument();
    private final Map<Style, PDType1Font> fonts = new HashMap<>();
    private final Map<Integer, Boolean> encodable = new HashMap<>();
    private PDPageContentStream stream;
    private float y;

    PdfPages() {
        for (Style style : Style.values()) {
            fonts.put(style, new PDType1Font(style.font));
        }
        newPage();
    }

    void line(Style style, String text) {
        for (String wrapped : wrap(style, sanitize(style, text == null ? "" : text))) {
            ensure(style.leading);
            write(style, MARGIN, wrapped);
            y -= style.leading;
        }
    }

    void field(String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        line(Style.LABEL, label);
        line(Style.TEXT, value);
    }

    void gap(float points) {
        y -= points;
        ensure(0);
    }

    void pageBreak() {
        newPage();
    }

    byte[] finish(String footerPrefix, String title) {
        try {
            stream.close();
            int total = document.getNumberOfPages();
            for (int index = 0; index < total; index++) {
                PDPage page = document.getPage(index);
                try (PDPageContentStream footer = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.beginText();
                    footer.setFont(fonts.get(Style.MUTED), Style.MUTED.size);
                    footer.newLineAtOffset(MARGIN, 30);
                    footer.showText(sanitize(Style.MUTED, footerPrefix + " · Página " + (index + 1) + " de " + total));
                    footer.endText();
                }
            }
            document.getDocumentInformation().setTitle(sanitize(Style.TEXT, title));
            document.getDocumentInformation().setProducer("clinical-history-service");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not render the record copy", ex);
        }
    }

    @Override
    public void close() {
        try {
            document.close();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void newPage() {
        try {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void ensure(float needed) {
        if (y - needed < BOTTOM) {
            newPage();
        }
    }

    private void write(Style style, float x, String text) {
        try {
            stream.beginText();
            stream.setFont(fonts.get(style), style.size);
            stream.newLineAtOffset(x, y - style.size);
            stream.showText(text);
            stream.endText();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private List<String> wrap(Style style, String text) {
        float width = PDRectangle.A4.getWidth() - 2 * MARGIN;
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ", -1)) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (widthOf(style, candidate) <= width) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                String rest = word;
                while (widthOf(style, rest) > width) {
                    int cut = rest.length() - 1;
                    while (cut > 1 && widthOf(style, rest.substring(0, cut)) > width) {
                        cut--;
                    }
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                current.append(rest);
            }
            lines.add(current.toString());
        }
        return lines;
    }

    private float widthOf(Style style, String text) {
        try {
            return fonts.get(style).getStringWidth(text) / 1000 * style.size;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String sanitize(Style style, String text) {
        StringBuilder safe = new StringBuilder(text.length());
        text.replace('\t', ' ').replace("\r", "").codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || encodable.computeIfAbsent(codePoint, point -> canEncode(style, point))) {
                safe.appendCodePoint(codePoint);
            } else {
                safe.append('?');
            }
        });
        return safe.toString();
    }

    private boolean canEncode(Style style, int codePoint) {
        try {
            fonts.get(style).encode(new String(Character.toChars(codePoint)));
            return true;
        } catch (IllegalArgumentException | IOException unsupported) {
            return false;
        }
    }
}
