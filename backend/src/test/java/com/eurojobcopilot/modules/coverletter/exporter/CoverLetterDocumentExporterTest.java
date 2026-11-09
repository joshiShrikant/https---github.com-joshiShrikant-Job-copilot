package com.eurojobcopilot.modules.coverletter.exporter;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterExportFormat;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterPersistenceHelper.ExportSnapshot;
import com.eurojobcopilot.modules.resume.config.ResumeStorageProperties;
import com.eurojobcopilot.modules.resume.parser.ExtractedResumeText;
import com.eurojobcopilot.modules.resume.parser.ResumeTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverLetterDocumentExporterTest {

    private CoverLetterDocumentExporter exporter;
    private ResumeTextExtractor textExtractor;

    @BeforeEach
    void setUp() {
        exporter = new CoverLetterDocumentExporter();
        ResumeStorageProperties props = new ResumeStorageProperties();
        props.setMaxFileSizeBytes(10L * 1024L * 1024L);
        textExtractor = new ResumeTextExtractor(props);
    }

    @Test
    void exportPdfDocxAndTxt_allFormatsRenderEuropeanUnicodeTextCleanly() throws IOException {
        ExportSnapshot snapshot = buildSnapshot(CoverLetterTone.ACADEMIC, 1);

        // PDF (serif font for ACADEMIC)
        byte[] pdfBytes = exporter.export(snapshot, CoverLetterExportFormat.PDF);
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        ExtractedResumeText pdfExtracted = textExtractor.extract(
                new ByteArrayResource(pdfBytes),
                ResumeTextExtractor.MIME_PDF,
                "cover-letter.pdf"
        );
        assertThat(pdfExtracted.text())
                .contains("Elena Müller", "Adyen N.V.", "Re: Application for Staff Java Settlement Engineer",
                        "Lukasz", "Ceský", "Page 1 of");

        // DOCX
        byte[] docxBytes = exporter.export(snapshot, CoverLetterExportFormat.DOCX);
        assertThat(docxBytes).isNotEmpty();
        assertThat(docxBytes[0]).isEqualTo((byte) 0x50);
        assertThat(docxBytes[1]).isEqualTo((byte) 0x4B);

        Map<String, String> entries = readZipEntriesAsStrings(docxBytes);
        assertThat(entries.keySet()).contains(
                "[Content_Types].xml",
                "_rels/.rels",
                "word/_rels/document.xml.rels",
                "word/styles.xml",
                "word/footer1.xml",
                "word/document.xml"
        );
        assertThat(entries.get("word/styles.xml")).contains("Georgia");

        ExtractedResumeText docxExtracted = textExtractor.extract(
                new ByteArrayResource(docxBytes),
                ResumeTextExtractor.MIME_DOCX,
                "cover-letter.docx"
        );
        assertThat(docxExtracted.text())
                .contains("Elena Müller", "Adyen N.V.", "Łukasz", "Český", "ClearPay Systems");

        // TXT
        byte[] txtBytes = exporter.export(snapshot, CoverLetterExportFormat.TXT);
        String txt = new String(txtBytes, StandardCharsets.UTF_8);
        assertThat(txt)
                .contains("Elena Müller", "Re: Application for Staff Java Settlement Engineer", "Dear Hiring Team at Adyen N.V.,");
    }

    @Test
    void exportPdf_paginatesLongLettersCleanlyAcrossMultiplePages() {
        ExportSnapshot longSnapshot = buildSnapshot(CoverLetterTone.PROFESSIONAL, 18);

        byte[] pdfBytes = exporter.export(longSnapshot, CoverLetterExportFormat.PDF);
        ExtractedResumeText pdfExtracted = textExtractor.extract(
                new ByteArrayResource(pdfBytes),
                ResumeTextExtractor.MIME_PDF,
                "long-letter.pdf"
        );
        assertThat(pdfExtracted.pageCount()).isGreaterThanOrEqualTo(2);
        assertThat(pdfExtracted.text()).contains("Page 1 of", "Page 2 of");
    }

    @Test
    void formatQueryParamAndDownloadFilename_enforceValidationAndSafeSlugs() {
        assertThat(CoverLetterExportFormat.fromQueryParam("PDF")).isEqualTo(CoverLetterExportFormat.PDF);
        assertThat(CoverLetterExportFormat.fromQueryParam(" docx ")).isEqualTo(CoverLetterExportFormat.DOCX);
        assertThat(CoverLetterExportFormat.fromQueryParam("txt")).isEqualTo(CoverLetterExportFormat.TXT);
        assertThatThrownBy(() -> CoverLetterExportFormat.fromQueryParam("html"))
                .isInstanceOf(BusinessValidationException.class);
        assertThatThrownBy(() -> CoverLetterExportFormat.fromQueryParam(" "))
                .isInstanceOf(BusinessValidationException.class);

        ExportSnapshot snapshot = buildSnapshot(CoverLetterTone.CONFIDENT, 1);
        String filename = exporter.buildDownloadFilename(snapshot, CoverLetterExportFormat.PDF);
        assertThat(filename).isEqualTo("elena-muller-adyen-n-v-cover-letter.pdf");
        assertThat(filename).doesNotContain("..", "/", "\\", " ");
    }

    private ExportSnapshot buildSnapshot(CoverLetterTone tone, int repeatParagraphs) {
        StringBuilder content = new StringBuilder("Dear Hiring Team at Adyen N.V.,\n\n");
        for (int i = 0; i < repeatParagraphs; i++) {
            content.append("At ClearPay Systems (#").append(i + 1)
                    .append("), I collaborated with Łukasz across Český and München engineering hubs to ship Java 21 SEPA instant clearing services handling over 5,000 TPS.\n\n");
        }
        content.append("Sincerely,\nElena Müller");

        return new ExportSnapshot(
                "11111111-1111-1111-1111-111111111111",
                "Cover Letter · Staff Java Settlement Engineer · Adyen N.V. (v1)",
                content.toString(),
                tone,
                "Staff Java Settlement Engineer",
                "Adyen N.V.",
                "Netherlands",
                "Amsterdam, Netherlands",
                "Elena Müller",
                "elena.mueller@eurojob.eu",
                "München, Germany",
                "Principal Java Backend Architect",
                "EU/EEA Citizen · Open to relocation",
                "Dear Hiring Team at Adyen N.V.,",
                "Sincerely,\nElena Müller",
                Instant.parse("2026-04-15T10:15:30Z")
        );
    }

    private Map<String, String> readZipEntriesAsStrings(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return entries;
    }
}

