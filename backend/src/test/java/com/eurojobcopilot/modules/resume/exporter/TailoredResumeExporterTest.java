package com.eurojobcopilot.modules.resume.exporter;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.modules.resume.config.ResumeStorageProperties;
import com.eurojobcopilot.modules.resume.dto.ResumeExportFormat;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeDataDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeHeaderDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeProjectDto;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.parser.ExtractedResumeText;
import com.eurojobcopilot.modules.resume.parser.ResumeTextExtractor;
import com.eurojobcopilot.modules.resume.template.ResumeExportTextFormatter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TailoredResumeExporterTest {

    private PdfResumeExporter pdfExporter;
    private DocxResumeExporter docxExporter;
    private ResumeTextExtractor textExtractor;

    @BeforeEach
    void setUp() {
        pdfExporter = new PdfResumeExporter();
        docxExporter = new DocxResumeExporter();
        ResumeStorageProperties props = new ResumeStorageProperties();
        props.setMaxFileSizeBytes(10L * 1024L * 1024L);
        textExtractor = new ResumeTextExtractor(props);
    }

    @Test
    void exportAllTemplates_pdfAndDocx_produceReadableTextAndSafeLinks() throws IOException {
        TailoredResumeDataDto data = buildSampleResumeData(2);

        for (ResumeTemplate template : ResumeTemplate.values()) {
            byte[] pdfBytes = pdfExporter.export(data, template, "Staff Java Architect · Adyen (v1)");
            assertThat(pdfBytes).isNotEmpty();
            assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

            ExtractedResumeText pdfText = textExtractor.extract(
                    new ByteArrayResource(pdfBytes),
                    ResumeTextExtractor.MIME_PDF,
                    "cv.pdf"
            );
            assertThat(pdfText.text())
                    .contains("Elena Müller", "München, Germany", "Java 21", "ClearPay Systems",
                            "Technical University of Munich", "English (C2)", "AWS Solutions Architect");

            // Ensure safe URLs become clickable PDF annotations while javascript: URI is excluded
            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                List<String> uris = new ArrayList<>();
                for (PDPage page : doc.getPages()) {
                    for (PDAnnotation ann : page.getAnnotations()) {
                        if (ann instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI action) {
                            uris.add(action.getURI());
                        }
                    }
                }
                assertThat(uris).contains("https://linkedin.com/in/elena-mueller", "https://github.com/elena/sepa-core");
                assertThat(uris).noneMatch(u -> u.startsWith("javascript:"));
            }

            byte[] docxBytes = docxExporter.export(data, template, "Staff Java Architect · Adyen (v1)");
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
            assertThat(entries.get("word/_rels/document.xml.rels"))
                    .contains("https://linkedin.com/in/elena-mueller", "https://github.com/elena/sepa-core")
                    .doesNotContain("javascript:");

            ExtractedResumeText docxText = textExtractor.extract(
                    new ByteArrayResource(docxBytes),
                    ResumeTextExtractor.MIME_DOCX,
                    "cv.docx"
            );
            assertThat(docxText.text())
                    .contains("Elena Müller", "ClearPay Systems", "SEPA Instant", "Technical University of Munich");
        }
    }

    @Test
    void exportPdfAndDocx_withUnicodeEuropeanDiacriticsAndLongExperience_paginatesCleanly() {
        TailoredResumeDataDto richData = buildSampleResumeData(10);
        richData.getHeader().setFullName("Łukasz Žižka-Müller");
        richData.getHeader().setLocation("Zürich, Switzerland → Brno");
        richData.setTailoredSummary("Staff Systems Engineer ≥ 99.99% SLA handling Český & Wrocław clearing nodes 🚀.");

        byte[] pdfBytes = pdfExporter.export(richData, ResumeTemplate.EUROPASS, "Euro Staff Role");
        ExtractedResumeText extractedPdf = textExtractor.extract(
                new ByteArrayResource(pdfBytes),
                ResumeTextExtractor.MIME_PDF,
                "multi-page.pdf"
        );
        assertThat(extractedPdf.pageCount()).isGreaterThanOrEqualTo(2);
        assertThat(extractedPdf.text())
                .contains("Lukasz Žižka-Müller", "Zürich", "Ceský", "Wroclaw", "-> Brno", ">= 99.99%", "Page 1 of", "Page 2 of");

        byte[] docxBytes = docxExporter.export(richData, ResumeTemplate.EUROPASS, "Euro Staff Role");
        ExtractedResumeText extractedDocx = textExtractor.extract(
                new ByteArrayResource(docxBytes),
                ResumeTextExtractor.MIME_DOCX,
                "multi-page.docx"
        );
        assertThat(extractedDocx.text()).contains("Łukasz Žižka-Müller", "Zürich", "Český", "Wrocław");
    }

    @Test
    void formatQueryParamAndDownloadFilename_enforceValidationAndSafeSlugs() {
        assertThat(ResumeExportFormat.fromQueryParam("PDF")).isEqualTo(ResumeExportFormat.PDF);
        assertThat(ResumeExportFormat.fromQueryParam(" docx ")).isEqualTo(ResumeExportFormat.DOCX);
        assertThatThrownBy(() -> ResumeExportFormat.fromQueryParam("txt"))
                .isInstanceOf(BusinessValidationException.class);
        assertThatThrownBy(() -> ResumeExportFormat.fromQueryParam(" "))
                .isInstanceOf(BusinessValidationException.class);

        TailoredResumeHeaderDto hdr = TailoredResumeHeaderDto.builder()
                .fullName("Elena Müller")
                .build();
        String filename = ResumeExportTextFormatter.buildDownloadFilename(
                "Staff Java Architect · Adyen N.V. (v2)",
                hdr,
                ResumeTemplate.MINIMAL_ATS,
                ResumeExportFormat.DOCX
        );
        assertThat(filename).endsWith("-minimal-ats-cv.docx");
        assertThat(filename).doesNotContain("..", "/", "\\", " ");
    }

    private TailoredResumeDataDto buildSampleResumeData(int experienceCount) {
        List<TailoredResumeExperienceDto> experiences = new ArrayList<>();
        for (int i = 0; i < experienceCount; i++) {
            experiences.add(TailoredResumeExperienceDto.builder()
                    .companyName(i == 0 ? "ClearPay Systems GmbH" : "EuroLedger Labs AG #" + i)
                    .designation("Senior Java Backend Engineer")
                    .location("München, Germany")
                    .startDate(LocalDate.of(2023 - Math.min(i, 10), 3, 1))
                    .endDate(i == 0 ? null : LocalDate.of(2024 - Math.min(i, 10), 2, 28))
                    .current(i == 0)
                    .tailoredBullets(List.of(
                            "Architected SEPA Instant clearing workflows processing 5,000 TPS in Java 21 & Spring Boot.",
                            "Scaled PostgreSQL ledger shards and Kafka consumers with 99.995% availability across EU regions.",
                            "Reduced P99 settlement latency from 140ms to 28ms using virtual threads and connection tuning."
                    ))
                    .build());
        }

        return TailoredResumeDataDto.builder()
                .header(TailoredResumeHeaderDto.builder()
                        .fullName("Elena Müller")
                        .headline("Staff Java Backend Architect")
                        .email("elena.mueller@eurojob.eu")
                        .location("München, Germany")
                        .country("Germany")
                        .yearsOfExperience(new BigDecimal("7.5"))
                        .workAuthorizationSummary("EU/EEA Citizen · Open to relocation")
                        .linkedinUrl("https://linkedin.com/in/elena-mueller")
                        .githubUrl("javascript:alert(1)")
                        .build())
                .tailoredSummary("Principal-track Java 21 and Spring Boot architect specializing in ISO 20022 SEPA payment rails.")
                .highlightedSkills(List.of("Java 21", "Spring Boot", "PostgreSQL", "Kafka", "Docker"))
                .additionalSkills(List.of("Kubernetes", "Terraform", "OpenAPI"))
                .optimizedExperiences(experiences)
                .projects(List.of(TailoredResumeProjectDto.builder()
                        .name("SEPA Instant Core")
                        .description("Sub-50ms ISO 20022 settlement engine.")
                        .technologies(List.of("Java 21", "PostgreSQL"))
                        .tailoredBullets(List.of("Sustained zero data loss under chaos failover testing."))
                        .url("https://github.com/elena/sepa-core")
                        .build()))
                .educations(List.of(TailoredResumeEducationDto.builder()
                        .institution("Technical University of Munich")
                        .degree("M.Sc.")
                        .fieldOfStudy("Computer Science")
                        .startDate(LocalDate.of(2015, 10, 1))
                        .endDate(LocalDate.of(2017, 9, 30))
                        .gradeOrGpa("1.3 (Sehr Gut)")
                        .country("Germany")
                        .build()))
                .languages(List.of("English (C2)", "German (C1)"))
                .certifications(List.of("AWS Solutions Architect"))
                .build();
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
