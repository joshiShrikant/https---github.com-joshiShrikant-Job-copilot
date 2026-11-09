package com.eurojobcopilot.modules.coverletter.exporter;

import com.eurojobcopilot.modules.coverletter.dto.CoverLetterExportFormat;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import com.eurojobcopilot.modules.coverletter.exception.CoverLetterExportException;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterPersistenceHelper.ExportSnapshot;
import com.eurojobcopilot.modules.resume.exporter.PdfResumeExporter;
import com.eurojobcopilot.modules.resume.template.ResumeExportTextFormatter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Renders grounded European cover letter drafts into formal A4 PDF (via Apache PDFBox),
 * OpenXML DOCX (via streaming ZIP/XML), and plain-text UTF-8 documents.
 */
@Component
public class CoverLetterDocumentExporter {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN_X = 54f;
    private static final float MARGIN_TOP = 50f;
    private static final float MARGIN_BOTTOM = 52f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (2 * MARGIN_X);

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final int A4_WIDTH_TWIPS = 11906;
    private static final int A4_HEIGHT_TWIPS = 16838;
    private static final int DOCX_MARGIN_TWIPS = 1080; // 0.75 in

    private static final String PRIMARY_HEX = "1E3A8A";
    private static final String ACCENT_HEX = "2563EB";
    private static final String DARK_INK_HEX = "0F172A";
    private static final String MUTED_INK_HEX = "475569";
    private static final String RULE_HEX = "CBD5E1";

    private static final DateTimeFormatter LETTER_DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern SLUG_NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    public byte[] export(ExportSnapshot snapshot, CoverLetterExportFormat format) {
        if (snapshot == null) {
            throw new CoverLetterExportException("Cover letter snapshot must not be null");
        }
        if (format == null) {
            throw new CoverLetterExportException("Cover letter export format must not be null");
        }
        return switch (format) {
            case PDF -> exportPdf(snapshot);
            case DOCX -> exportDocx(snapshot);
            case TXT -> exportTxt(snapshot);
        };
    }

    public String buildDownloadFilename(ExportSnapshot snapshot, CoverLetterExportFormat format) {
        String nameSlug = toSlug(snapshot != null ? snapshot.candidateFullName() : null);
        String companySlug = toSlug(snapshot != null ? snapshot.companyName() : null);

        StringBuilder stem = new StringBuilder();
        if (!nameSlug.isEmpty()) {
            stem.append(truncateSlug(nameSlug, 40));
        }
        if (!companySlug.isEmpty()) {
            if (!stem.isEmpty()) {
                stem.append('-');
            }
            stem.append(truncateSlug(companySlug, 45));
        }
        if (stem.isEmpty()) {
            String titleSlug = toSlug(snapshot != null ? snapshot.title() : null);
            stem.append(!titleSlug.isEmpty() ? truncateSlug(titleSlug, 60) : "cover-letter");
        }
        if (!stem.toString().endsWith("cover-letter")) {
            stem.append("-cover-letter");
        }

        String finalStem = stem.toString();
        if (finalStem.length() > 110) {
            finalStem = finalStem.substring(0, 110);
            while (finalStem.endsWith("-")) {
                finalStem = finalStem.substring(0, finalStem.length() - 1);
            }
        }
        CoverLetterExportFormat safeFormat = format != null ? format : CoverLetterExportFormat.PDF;
        return finalStem + "." + safeFormat.extension();
    }

    private byte[] exportPdf(ExportSnapshot snapshot) {
        boolean serif = snapshot.tone() == CoverLetterTone.ACADEMIC;
        PDFont regular = new PDType1Font(serif
                ? Standard14Fonts.FontName.TIMES_ROMAN
                : Standard14Fonts.FontName.HELVETICA);
        PDFont bold = new PDType1Font(serif
                ? Standard14Fonts.FontName.TIMES_BOLD
                : Standard14Fonts.FontName.HELVETICA_BOLD);
        PDFont italic = new PDType1Font(serif
                ? Standard14Fonts.FontName.TIMES_ITALIC
                : Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument document = new PDDocument()) {
            populatePdfMetadata(document, snapshot);
            LetterPdfCursor cursor = new LetterPdfCursor(document, regular, bold, italic);

            // Accent strip along top margin
            cursor.fillRect(0f, PAGE_HEIGHT - 5f, PAGE_WIDTH, 5f, ACCENT_HEX);

            // Sender Header
            String senderName = cleanOrFallback(snapshot.candidateFullName(), "Candidate");
            cursor.drawWrappedLines(List.of(senderName), bold, 17f, 21f, DARK_INK_HEX);

            String headline = ResumeExportTextFormatter.cleanInlineText(snapshot.candidateHeadline());
            if (!headline.isEmpty()) {
                cursor.drawWrappedLines(wrapText(regular, 10.5f, headline, CONTENT_WIDTH),
                        regular, 10.5f, 14f, PRIMARY_HEX);
            }

            List<String> contactParts = buildSenderContactSegments(snapshot);
            if (!contactParts.isEmpty()) {
                cursor.drawWrappedLines(wrapText(regular, 9.2f, String.join("  ·  ", contactParts), CONTENT_WIDTH),
                        regular, 9.2f, 13f, MUTED_INK_HEX);
            }

            cursor.advance(4f);
            cursor.drawLine(MARGIN_X, cursor.y, PAGE_WIDTH - MARGIN_X, cursor.y, 0.9f, RULE_HEX);
            cursor.advance(16f);

            // Letter Date
            String formattedDate = formatLetterDate(snapshot.dateInstant());
            cursor.drawWrappedLines(List.of(formattedDate), regular, 9.8f, 13.5f, MUTED_INK_HEX);
            cursor.advance(10f);

            // Employer Recipient Block
            String company = ResumeExportTextFormatter.cleanInlineText(snapshot.companyName());
            String employerLocation = joinNonBlank(
                    ResumeExportTextFormatter.cleanInlineText(snapshot.jobLocation()),
                    ResumeExportTextFormatter.cleanInlineText(snapshot.jobCountry())
            );
            String jobTitle = ResumeExportTextFormatter.cleanInlineText(snapshot.jobTitle());

            cursor.drawWrappedLines(List.of("Hiring Team"), bold, 10f, 13.5f, DARK_INK_HEX);
            if (!company.isEmpty()) {
                cursor.drawWrappedLines(wrapText(regular, 10f, company, CONTENT_WIDTH),
                        regular, 10f, 13.5f, DARK_INK_HEX);
            }
            if (!employerLocation.isEmpty()) {
                cursor.drawWrappedLines(wrapText(regular, 9.5f, employerLocation, CONTENT_WIDTH),
                        regular, 9.5f, 13f, MUTED_INK_HEX);
            }
            if (!jobTitle.isEmpty()) {
                cursor.advance(6f);
                cursor.drawWrappedLines(wrapText(bold, 10.2f, "Re: Application for " + jobTitle, CONTENT_WIDTH),
                        bold, 10.2f, 14f, PRIMARY_HEX);
            }
            cursor.advance(12f);

            // Letter Paragraphs
            List<String> paragraphs = splitParagraphs(snapshot.content());
            for (int i = 0; i < paragraphs.size(); i++) {
                String paragraph = paragraphs.get(i);
                String[] manualLines = paragraph.split("\\n");
                for (String singleLine : manualLines) {
                    String trimmed = singleLine.trim();
                    if (!trimmed.isEmpty()) {
                        List<String> wrapped = wrapText(regular, 10.2f, trimmed, CONTENT_WIDTH);
                        cursor.drawWrappedLines(wrapped, regular, 10.2f, 14.5f, DARK_INK_HEX);
                    }
                }
                if (i < paragraphs.size() - 1) {
                    cursor.advance(8f);
                }
            }

            cursor.closeCurrentStream();
            drawPdfFooters(document, regular, snapshot);

            ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new CoverLetterExportException("Failed to generate cover letter PDF document", ex);
        }
    }

    private byte[] exportDocx(ExportSnapshot snapshot) {
        String fontFamily = snapshot.tone() == CoverLetterTone.ACADEMIC ? "Georgia" : "Calibri";
        String documentXml = buildDocxDocumentXml(snapshot);
        String stylesXml = buildDocxStylesXml(fontFamily);
        String footerXml = buildDocxFooterXml(snapshot);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(16384);
            try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml());
                writeZipEntry(zos, "_rels/.rels", buildRootRelsXml());
                writeZipEntry(zos, "word/_rels/document.xml.rels", buildDocumentRelsXml());
                writeZipEntry(zos, "word/styles.xml", stylesXml);
                writeZipEntry(zos, "word/footer1.xml", footerXml);
                writeZipEntry(zos, "word/document.xml", documentXml);
            }
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new CoverLetterExportException("Failed to generate cover letter DOCX document", ex);
        }
    }

    private byte[] exportTxt(ExportSnapshot snapshot) {
        StringBuilder out = new StringBuilder(2048);
        String senderName = cleanOrFallback(snapshot.candidateFullName(), "Candidate");
        out.append(senderName).append('\n');

        String headline = ResumeExportTextFormatter.cleanInlineText(snapshot.candidateHeadline());
        if (!headline.isEmpty()) {
            out.append(headline).append('\n');
        }

        List<String> contactParts = buildSenderContactSegments(snapshot);
        if (!contactParts.isEmpty()) {
            out.append(String.join(" · ", contactParts)).append('\n');
        }
        out.append('\n');

        out.append(formatLetterDate(snapshot.dateInstant())).append("\n\n");

        String company = ResumeExportTextFormatter.cleanInlineText(snapshot.companyName());
        String employerLocation = joinNonBlank(
                ResumeExportTextFormatter.cleanInlineText(snapshot.jobLocation()),
                ResumeExportTextFormatter.cleanInlineText(snapshot.jobCountry())
        );
        String jobTitle = ResumeExportTextFormatter.cleanInlineText(snapshot.jobTitle());

        out.append("Hiring Team").append('\n');
        if (!company.isEmpty()) {
            out.append(company).append('\n');
        }
        if (!employerLocation.isEmpty()) {
            out.append(employerLocation).append('\n');
        }
        if (!jobTitle.isEmpty()) {
            out.append("\nRe: Application for ").append(jobTitle).append('\n');
        }
        out.append('\n');

        String cleanBody = ResumeExportTextFormatter.cleanMultilineText(snapshot.content());
        out.append(cleanBody).append('\n');

        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void populatePdfMetadata(PDDocument document, ExportSnapshot snapshot) {
        PDDocumentInformation info = document.getDocumentInformation();
        String fullName = ResumeExportTextFormatter.cleanInlineText(snapshot.candidateFullName());
        String jobTitle = ResumeExportTextFormatter.cleanInlineText(snapshot.jobTitle());
        String company = ResumeExportTextFormatter.cleanInlineText(snapshot.companyName());
        String docTitle = !fullName.isEmpty() && !company.isEmpty()
                ? fullName + " — Cover Letter (" + company + ")"
                : cleanOrFallback(snapshot.title(), "Cover Letter");
        info.setTitle(docTitle);
        if (!fullName.isEmpty()) {
            info.setAuthor(fullName);
        }
        if (!jobTitle.isEmpty()) {
            info.setSubject("Application for " + jobTitle);
        }
        info.setCreator("EuroJob Copilot Cover Letter Studio");
    }

    private void drawPdfFooters(PDDocument document, PDFont regular, ExportSnapshot snapshot) throws IOException {
        int totalPages = document.getNumberOfPages();
        String name = ResumeExportTextFormatter.cleanInlineText(snapshot.candidateFullName());
        String baseLabel = !name.isEmpty() ? name + " · Cover Letter" : "EuroJob Copilot · Cover Letter";

        for (int idx = 0; idx < totalPages; idx++) {
            PDPage page = document.getPage(idx);
            String footerText = PdfResumeExporter.sanitizeForFont(
                    regular,
                    baseLabel + " · Page " + (idx + 1) + " of " + totalPages
            );
            try (PDPageContentStream footerStream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                float[] ruleRgb = rgbComponents(RULE_HEX);
                footerStream.setStrokingColor(ruleRgb[0], ruleRgb[1], ruleRgb[2]);
                footerStream.setLineWidth(0.5f);
                footerStream.moveTo(MARGIN_X, 34f);
                footerStream.lineTo(PAGE_WIDTH - MARGIN_X, 34f);
                footerStream.stroke();

                float[] mutedRgb = rgbComponents(MUTED_INK_HEX);
                footerStream.setNonStrokingColor(mutedRgb[0], mutedRgb[1], mutedRgb[2]);
                footerStream.beginText();
                footerStream.setFont(regular, 8f);
                float width = regular.getStringWidth(footerText) / 1000f * 8f;
                footerStream.newLineAtOffset(PAGE_WIDTH - MARGIN_X - width, 22f);
                footerStream.showText(footerText);
                footerStream.endText();
            }
        }
    }

    private String buildDocxDocumentXml(ExportSnapshot snapshot) {
        StringBuilder body = new StringBuilder(4096);
        body.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        body.append("<w:document xmlns:w=\"").append(W_NS).append("\" xmlns:r=\"").append(R_NS).append("\">\n");
        body.append("  <w:body>\n");

        // Top accent rule & sender name
        String senderName = cleanOrFallback(snapshot.candidateFullName(), "Candidate");
        body.append("    <w:p><w:pPr>")
                .append("<w:pBdr><w:top w:val=\"single\" w:sz=\"24\" w:space=\"6\" w:color=\"").append(ACCENT_HEX).append("\"/></w:pBdr>")
                .append("<w:spacing w:before=\"0\" w:after=\"40\"/>")
                .append("</w:pPr>")
                .append(runXml(senderName, true, false, 34, DARK_INK_HEX))
                .append("</w:p>\n");

        String headline = ResumeExportTextFormatter.cleanInlineText(snapshot.candidateHeadline());
        if (!headline.isEmpty()) {
            body.append("    <w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"30\"/></w:pPr>")
                    .append(runXml(headline, false, false, 21, PRIMARY_HEX))
                    .append("</w:p>\n");
        }

        List<String> contacts = buildSenderContactSegments(snapshot);
        body.append("    <w:p><w:pPr>")
                .append("<w:pBdr><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"8\" w:color=\"").append(RULE_HEX).append("\"/></w:pBdr>")
                .append("<w:spacing w:before=\"0\" w:after=\"180\"/>")
                .append("</w:pPr>");
        if (!contacts.isEmpty()) {
            body.append(runXml(String.join("  ·  ", contacts), false, false, 18, MUTED_INK_HEX));
        }
        body.append("</w:p>\n");

        // Date
        body.append("    <w:p><w:pPr><w:spacing w:before=\"40\" w:after=\"140\"/></w:pPr>")
                .append(runXml(formatLetterDate(snapshot.dateInstant()), false, false, 20, MUTED_INK_HEX))
                .append("</w:p>\n");

        // Recipient block
        String company = ResumeExportTextFormatter.cleanInlineText(snapshot.companyName());
        String employerLocation = joinNonBlank(
                ResumeExportTextFormatter.cleanInlineText(snapshot.jobLocation()),
                ResumeExportTextFormatter.cleanInlineText(snapshot.jobCountry())
        );
        String jobTitle = ResumeExportTextFormatter.cleanInlineText(snapshot.jobTitle());

        body.append("    <w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"25\"/></w:pPr>")
                .append(runXml("Hiring Team", true, false, 20, DARK_INK_HEX))
                .append("</w:p>\n");
        if (!company.isEmpty()) {
            body.append("    <w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"25\"/></w:pPr>")
                    .append(runXml(company, false, false, 20, DARK_INK_HEX))
                    .append("</w:p>\n");
        }
        if (!employerLocation.isEmpty()) {
            body.append("    <w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"40\"/></w:pPr>")
                    .append(runXml(employerLocation, false, false, 19, MUTED_INK_HEX))
                    .append("</w:p>\n");
        }
        if (!jobTitle.isEmpty()) {
            body.append("    <w:p><w:pPr><w:spacing w:before=\"60\" w:after=\"180\"/></w:pPr>")
                    .append(runXml("Re: Application for " + jobTitle, true, false, 21, PRIMARY_HEX))
                    .append("</w:p>\n");
        }

        // Body paragraphs
        List<String> paragraphs = splitParagraphs(snapshot.content());
        for (String paragraph : paragraphs) {
            String[] manualLines = paragraph.split("\\n");
            if (manualLines.length == 1) {
                body.append("    <w:p><w:pPr><w:spacing w:after=\"140\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr>")
                        .append(runXml(manualLines[0].trim(), false, false, 21, DARK_INK_HEX))
                        .append("</w:p>\n");
            } else {
                body.append("    <w:p><w:pPr><w:spacing w:after=\"140\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr>");
                for (int idx = 0; idx < manualLines.length; idx++) {
                    String trimmed = manualLines[idx].trim();
                    if (idx > 0) {
                        body.append("<w:r><w:br/></w:r>");
                    }
                    if (!trimmed.isEmpty()) {
                        body.append(runXml(trimmed, false, false, 21, DARK_INK_HEX));
                    }
                }
                body.append("</w:p>\n");
            }
        }

        body.append("    <w:sectPr>\n");
        body.append("      <w:footerReference w:type=\"default\" r:id=\"rId2\"/>\n");
        body.append("      <w:pgSz w:w=\"").append(A4_WIDTH_TWIPS).append("\" w:h=\"").append(A4_HEIGHT_TWIPS).append("\"/>\n");
        body.append("      <w:pgMar w:top=\"").append(DOCX_MARGIN_TWIPS)
                .append("\" w:right=\"").append(DOCX_MARGIN_TWIPS)
                .append("\" w:bottom=\"").append(DOCX_MARGIN_TWIPS)
                .append("\" w:left=\"").append(DOCX_MARGIN_TWIPS)
                .append("\" w:header=\"420\" w:footer=\"420\" w:gutter=\"0\"/>\n");
        body.append("    </w:sectPr>\n");
        body.append("  </w:body>\n");
        body.append("</w:document>\n");
        return body.toString();
    }

    private String buildContentTypesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                  <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
                </Types>
                """;
    }

    private String buildRootRelsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """;
    }

    private String buildDocumentRelsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
                </Relationships>
                """;
    }

    private String buildDocxStylesXml(String fontFamily) {
        String font = escapeXml(fontFamily);
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="%s">
                  <w:docDefaults>
                    <w:rPrDefault>
                      <w:rPr>
                        <w:rFonts w:ascii="%s" w:hAnsi="%s" w:cs="%s"/>
                        <w:color w:val="%s"/>
                        <w:sz w:val="21"/>
                        <w:szCs w:val="21"/>
                      </w:rPr>
                    </w:rPrDefault>
                    <w:pPrDefault>
                      <w:pPr>
                        <w:spacing w:after="100" w:line="276" w:lineRule="auto"/>
                      </w:pPr>
                    </w:pPrDefault>
                  </w:docDefaults>
                  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
                    <w:name w:val="Normal"/>
                  </w:style>
                </w:styles>
                """.formatted(W_NS, font, font, font, DARK_INK_HEX);
    }

    private String buildDocxFooterXml(ExportSnapshot snapshot) {
        String name = ResumeExportTextFormatter.cleanInlineText(snapshot.candidateFullName());
        String label = !name.isEmpty() ? name + " · Cover Letter" : "Cover Letter";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<w:ftr xmlns:w=\"" + W_NS + "\">\n"
                + "  <w:p><w:pPr><w:jc w:val=\"right\"/></w:pPr>\n"
                + "    <w:r><w:rPr><w:color w:val=\"" + MUTED_INK_HEX + "\"/><w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\">" + escapeXml(label + " · EuroJob Copilot") + "</w:t></w:r>\n"
                + "  </w:p>\n</w:ftr>\n";
    }

    private String runXml(String text, boolean bold, boolean italic, int halfPoints, String colorHex) {
        StringBuilder rPr = new StringBuilder("<w:rPr>");
        if (bold) {
            rPr.append("<w:b/>");
        }
        if (italic) {
            rPr.append("<w:i/>");
        }
        rPr.append("<w:color w:val=\"").append(colorHex).append("\"/>");
        rPr.append("<w:sz w:val=\"").append(halfPoints).append("\"/>");
        rPr.append("<w:szCs w:val=\"").append(halfPoints).append("\"/>");
        rPr.append("</w:rPr>");
        return "<w:r>" + rPr + "<w:t xml:space=\"preserve\">" + escapeXml(text) + "</w:t></w:r>";
    }

    private List<String> buildSenderContactSegments(ExportSnapshot snapshot) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, ResumeExportTextFormatter.cleanInlineText(snapshot.candidateLocation()));
        addIfPresent(parts, ResumeExportTextFormatter.cleanInlineText(snapshot.candidateEmail()));
        addIfPresent(parts, ResumeExportTextFormatter.cleanInlineText(snapshot.workAuthorizationSummary()));
        return parts;
    }

    private String formatLetterDate(Instant instant) {
        Instant effective = instant != null ? instant : Instant.now();
        return LETTER_DATE_FMT.format(effective);
    }

    private List<String> splitParagraphs(String rawContent) {
        String normalized = ResumeExportTextFormatter.cleanMultilineText(rawContent);
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] blocks = PARAGRAPH_SPLIT.split(normalized);
        List<String> out = new ArrayList<>(blocks.length);
        for (String block : blocks) {
            String trimmed = block != null ? block.trim() : "";
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        if (out.isEmpty()) {
            out.add(normalized);
        }
        return out;
    }

    private List<String> wrapText(PDFont font, float fontSize, String rawText, float maxWidth) throws IOException {
        String safe = PdfResumeExporter.sanitizeForFont(font, rawText);
        if (safe.isEmpty()) {
            return List.of();
        }
        String[] words = safe.split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (currentLine.isEmpty()) {
                if (measureWidth(font, fontSize, word) <= maxWidth) {
                    currentLine.append(word);
                } else {
                    appendLongTokenWrapped(font, fontSize, word, maxWidth, lines, currentLine);
                }
            } else {
                String candidate = currentLine + " " + word;
                if (measureWidth(font, fontSize, candidate) <= maxWidth) {
                    currentLine.append(' ').append(word);
                } else {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                    if (measureWidth(font, fontSize, word) <= maxWidth) {
                        currentLine.append(word);
                    } else {
                        appendLongTokenWrapped(font, fontSize, word, maxWidth, lines, currentLine);
                    }
                }
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private void appendLongTokenWrapped(
            PDFont font,
            float fontSize,
            String token,
            float maxWidth,
            List<String> lines,
            StringBuilder currentLine
    ) throws IOException {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            String next = currentLine.toString() + c;
            if (!currentLine.isEmpty() && measureWidth(font, fontSize, next) > maxWidth) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
            }
            currentLine.append(c);
        }
    }

    private float measureWidth(PDFont font, float fontSize, String text) throws IOException {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    private String cleanOrFallback(String value, String fallback) {
        String cleaned = ResumeExportTextFormatter.cleanInlineText(value);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private String joinNonBlank(String first, String second) {
        if (!first.isEmpty() && !second.isEmpty() && !first.toLowerCase(Locale.ROOT).contains(second.toLowerCase(Locale.ROOT))) {
            return first + ", " + second;
        }
        return !first.isEmpty() ? first : second;
    }

    private static void addIfPresent(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value);
        }
    }

    private static void writeZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    static String escapeXml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (!(cp == 0x9 || cp == 0xA || cp == 0xD
                    || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD)
                    || (cp >= 0x10000 && cp <= 0x10FFFF))) {
                continue;
            }
            switch (cp) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    private static float[] rgbComponents(String hex) {
        if (hex == null || hex.length() != 6) {
            return new float[]{0.06f, 0.09f, 0.16f};
        }
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new float[]{r / 255f, g / 255f, b / 255f};
    }

    private static String toSlug(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String lowered = decomposed.toLowerCase(Locale.ROOT)
                .replace("ß", "ss")
                .replace("ø", "o")
                .replace("ł", "l");
        String slug = SLUG_NON_ALNUM.matcher(lowered).replaceAll("-");
        while (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        while (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug;
    }

    private static String truncateSlug(String slug, int maxLen) {
        if (slug.length() <= maxLen) {
            return slug;
        }
        String sub = slug.substring(0, maxLen);
        while (sub.endsWith("-")) {
            sub = sub.substring(0, sub.length() - 1);
        }
        return sub;
    }

    private static final class LetterPdfCursor {
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private final PDFont italic;
        private PDPage currentPage;
        private PDPageContentStream stream;
        private float y;

        private LetterPdfCursor(PDDocument document, PDFont regular, PDFont bold, PDFont italic) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            this.italic = italic;
            openNewPage();
        }

        private void openNewPage() throws IOException {
            closeCurrentStream();
            currentPage = new PDPage(PDRectangle.A4);
            document.addPage(currentPage);
            stream = new PDPageContentStream(document, currentPage);
            y = PAGE_HEIGHT - MARGIN_TOP;
        }

        private void ensureSpace(float neededHeight) throws IOException {
            if (y - neededHeight < MARGIN_BOTTOM) {
                openNewPage();
            }
        }

        private void advance(float pts) {
            y -= pts;
        }

        private void drawWrappedLines(
                List<String> lines,
                PDFont font,
                float fontSize,
                float leading,
                String hexColor
        ) throws IOException {
            for (String line : lines) {
                ensureSpace(leading);
                String safe = PdfResumeExporter.sanitizeForFont(font, line);
                if (!safe.isEmpty()) {
                    float[] rgb = rgbComponents(hexColor);
                    stream.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                    stream.beginText();
                    stream.setFont(font, fontSize);
                    stream.newLineAtOffset(MARGIN_X, y);
                    stream.showText(safe);
                    stream.endText();
                }
                y -= leading;
            }
        }

        private void drawLine(float x1, float y1, float x2, float y2, float lineWidth, String hexColor) throws IOException {
            float[] rgb = rgbComponents(hexColor);
            stream.setStrokingColor(rgb[0], rgb[1], rgb[2]);
            stream.setLineWidth(lineWidth);
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        }

        private void fillRect(float x, float bottomY, float width, float height, String hexColor) throws IOException {
            float[] rgb = rgbComponents(hexColor);
            stream.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
            stream.addRect(x, bottomY, width, height);
            stream.fill();
        }

        private void closeCurrentStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}

