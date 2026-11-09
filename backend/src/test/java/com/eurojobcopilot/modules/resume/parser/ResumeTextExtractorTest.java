package com.eurojobcopilot.modules.resume.parser;

import com.eurojobcopilot.modules.resume.config.ResumeStorageProperties;
import com.eurojobcopilot.modules.resume.exception.InvalidResumeFileException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeTextExtractorTest {

    private ResumeTextExtractor extractor;

    @BeforeEach
    void setUp() {
        ResumeStorageProperties properties = new ResumeStorageProperties();
        properties.setMaxFileSizeBytes(10L * 1024L * 1024L);
        extractor = new ResumeTextExtractor(properties);
    }

    @Test
    void extractPdf_multiLineWithGermanUmlauts_extractsReadableText() throws IOException {
        byte[] pdfBytes = createPdfWithLines(
                "Lukas Müller - Senior Java Backend Architect",
                "München, Deutschland | https://github.com/lukas-mueller",
                "Skills: Java 21, Spring Boot, PostgreSQL, Kubernetes, Kafka",
                "Adyen N.V. (2022-03 - Present): Distributed SEPA payment rails"
        );

        ExtractedResumeText result = extractor.extract(
                new ByteArrayResource(pdfBytes),
                ResumeTextExtractor.MIME_PDF,
                "lukas-cv.pdf"
        );

        assertThat(result.format()).isEqualTo("PDF");
        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.charCount()).isGreaterThan(100);
        assertThat(result.text()).contains("Lukas Müller", "München", "Adyen N.V.", "Java 21");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void extractDocx_withHeadersTablesAndDeletedText_extractsCleanly() throws IOException {
        byte[] docxBytes = createZipBytes(
                "[Content_Types].xml",
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
                "_rels/.rels",
                "<Relationships/>",
                "word/header1.xml",
                "<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "<w:p><w:r><w:t>Elena Weber | Berlin, Germany | linkedin.com/in/elena-weber</w:t></w:r></w:p>"
                        + "</w:hdr>",
                "word/document.xml",
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "<w:body>"
                        + "<w:p><w:r><w:t>Staff Full-Stack Engineer specializing in Java 21 and Angular 19.</w:t></w:r></w:p>"
                        + "<w:p><w:r><w:delText>OLD REMOVED EMPLOYER SECRET</w:delText></w:r></w:p>"
                        + "<w:tbl><w:tr>"
                        + "<w:tc><w:p><w:r><w:t>Booking.com</w:t></w:r></w:p></w:tc>"
                        + "<w:tc><w:p><w:r><w:t>Amsterdam</w:t></w:r></w:p></w:tc>"
                        + "</w:tr></w:tbl>"
                        + "</w:body></w:document>",
                "word/footer1.xml",
                "<w:ftr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "<w:p><w:r><w:t>TU Munich M.Sc. Computer Science</w:t></w:r></w:p>"
                        + "</w:ftr>"
        );

        ExtractedResumeText result = extractor.extract(
                new ByteArrayResource(docxBytes),
                ResumeTextExtractor.MIME_DOCX,
                "elena-cv.docx"
        );

        assertThat(result.format()).isEqualTo("DOCX");
        assertThat(result.text())
                .contains("Elena Weber", "Berlin, Germany", "Angular 19", "Booking.com", "Amsterdam", "TU Munich")
                .doesNotContain("OLD REMOVED EMPLOYER SECRET");
    }

    @Test
    void extractPdf_encryptedPasswordProtected_throwsInvalidResumeFileException() throws IOException {
        byte[] encryptedBytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Confidential Master CV with sufficient characters inside.");
                cs.endText();
            }
            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pass", "user-pass", ap);
            doc.protect(policy);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            encryptedBytes = baos.toByteArray();
        }

        assertThatThrownBy(() -> extractor.extract(
                new ByteArrayResource(encryptedBytes),
                ResumeTextExtractor.MIME_PDF,
                "locked.pdf"
        )).isInstanceOf(InvalidResumeFileException.class)
                .hasMessageContaining("Encrypted or password-protected");
    }

    @Test
    void extractPdf_scannedOrEmptyText_throwsInvalidResumeFileException() throws IOException {
        byte[] blankPdf = createPdfWithLines("   ", "CV");

        assertThatThrownBy(() -> extractor.extract(
                new ByteArrayResource(blankPdf),
                ResumeTextExtractor.MIME_PDF,
                "scanned.pdf"
        )).isInstanceOf(InvalidResumeFileException.class)
                .hasMessageContaining("No readable text");
    }

    @Test
    void extractDocx_xxeDtdAndZipSlipRejected() throws IOException {
        byte[] xxeDocx = createZipBytes(
                "word/document.xml",
                "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                        + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "<w:body><w:p><w:r><w:t>&xxe; Master Resume Text Long Enough</w:t></w:r></w:p></w:body></w:document>"
        );

        assertThatThrownBy(() -> extractor.extract(
                new ByteArrayResource(xxeDocx),
                ResumeTextExtractor.MIME_DOCX,
                "xxe.docx"
        )).isInstanceOf(InvalidResumeFileException.class);

        byte[] zipSlipDocx = createZipBytes(
                "../../etc/evil.xml", "<evil/>",
                "word/document.xml", "<w:document><w:body><w:p><w:r><w:t>Sufficient text content here for testing.</w:t></w:r></w:p></w:body></w:document>"
        );

        assertThatThrownBy(() -> extractor.extract(
                new ByteArrayResource(zipSlipDocx),
                ResumeTextExtractor.MIME_DOCX,
                "slip.docx"
        )).isInstanceOf(InvalidResumeFileException.class)
                .hasMessageContaining("unsafe entry path");
    }

    private byte[] createPdfWithLines(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.setLeading(15f);
                cs.newLineAtOffset(40, 740);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createZipBytes(String... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (int i = 0; i < entries.length; i += 2) {
                zos.putNextEntry(new ZipEntry(entries[i]));
                zos.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}

