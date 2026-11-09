package com.eurojobcopilot.modules.resume.service;

import com.eurojobcopilot.modules.resume.config.ResumeStorageProperties;
import com.eurojobcopilot.modules.resume.dto.StoredResumeFile;
import com.eurojobcopilot.modules.resume.exception.InvalidResumeFileException;
import com.eurojobcopilot.modules.resume.exception.ResumeNotFoundException;
import com.eurojobcopilot.modules.resume.exception.ResumePayloadTooLargeException;
import com.eurojobcopilot.modules.resume.exception.ResumeStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalFileStorageServiceTest {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;
    private String userUuid;

    @BeforeEach
    void setUp() {
        ResumeStorageProperties properties = new ResumeStorageProperties();
        properties.setResumesPath(tempDir.toString());
        properties.setMaxFileSizeBytes(1024L);
        storageService = new LocalFileStorageService(properties);
        userUuid = UUID.randomUUID().toString();
    }

    @Test
    void storeResumeFile_validPdf_storesWithUniqueKeyOutsideFilenameControl() throws Exception {
        byte[] pdfBytes = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "C:\\fakepath\\Alice\r\nCV (2026).pdf",
                "application/pdf; charset=binary",
                pdfBytes
        );

        StoredResumeFile stored = storageService.storeResumeFile(file, userUuid);

        assertThat(stored.storageKey()).startsWith(userUuid + "/").endsWith(".pdf");
        assertThat(stored.storageKey()).doesNotContain("Alice");
        assertThat(stored.sanitizedFileName()).isEqualTo("Alice__CV (2026).pdf");
        assertThat(stored.mimeType()).isEqualTo(MIME_PDF);
        assertThat(stored.fileSizeBytes()).isEqualTo(pdfBytes.length);

        Resource loaded = storageService.loadResumeAsResource(stored.storageKey());
        assertThat(loaded.getInputStream().readAllBytes()).isEqualTo(pdfBytes);
    }

    @Test
    void storeResumeFile_validDocx_validatesOoxmlEntriesAndLoadsAndDeletesCleanly() throws Exception {
        byte[] docxBytes = createZipBytes(
                "[Content_Types].xml", "<Types/>",
                "word/document.xml", "<w:document><w:body><w:p/></w:body></w:document>"
        );
        MockMultipartFile file = new MockMultipartFile("file", "cv-final.docx", MIME_DOCX, docxBytes);

        StoredResumeFile stored = storageService.storeResumeFile(file, userUuid);

        assertThat(stored.storageKey()).startsWith(userUuid + "/").endsWith(".docx");
        assertThat(stored.mimeType()).isEqualTo(MIME_DOCX);
        assertThat(stored.fileSizeBytes()).isEqualTo(docxBytes.length);

        Resource resource = storageService.loadResumeAsResource(stored.storageKey());
        assertThat(resource.exists()).isTrue();
        assertThat(resource.getInputStream().readAllBytes()).isEqualTo(docxBytes);

        storageService.deleteResumeFile(stored.storageKey());
        assertThatThrownBy(() -> storageService.loadResumeAsResource(stored.storageKey()))
                .isInstanceOf(ResumeNotFoundException.class);
        // Deleting again should remain idempotent
        storageService.deleteResumeFile(stored.storageKey());
    }

    @Test
    void storeResumeFile_spoofedSignaturesAndUnsupportedTypes_throwInvalidAndLeaveNoFilesBehind() throws Exception {
        MockMultipartFile emptyPdf = new MockMultipartFile("file", "empty.pdf", MIME_PDF, new byte[0]);
        MockMultipartFile textAsPdf = new MockMultipartFile("file", "fake.pdf", MIME_PDF, "Hello World".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile exeAsPdf = new MockMultipartFile("file", "payload.pdf", MIME_PDF, new byte[]{'M', 'Z', 0x00, 0x01});
        MockMultipartFile wrongMimePdf = new MockMultipartFile("file", "real.pdf", "text/plain", "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile plainZipAsDocx = new MockMultipartFile("file", "archive.docx", MIME_DOCX, createZipBytes("notes.txt", "hello"));
        MockMultipartFile xlsxAsDocx = new MockMultipartFile("file", "sheet.docx", MIME_DOCX,
                createZipBytes("[Content_Types].xml", "<Types/>", "xl/workbook.xml", "<workbook/>"));
        MockMultipartFile zipSlipDocx = new MockMultipartFile("file", "slip.docx", MIME_DOCX,
                createZipBytes("[Content_Types].xml", "<Types/>", "word/document.xml", "<doc/>", "../../evil.xml", "<x/>"));
        MockMultipartFile exeExtension = new MockMultipartFile("file", "malware.pdf.exe", MIME_PDF, "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile traversalFilename = new MockMultipartFile("file", "../../etc/passwd.pdf", MIME_PDF, "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> storageService.storeResumeFile(emptyPdf, userUuid))
                .isInstanceOf(InvalidResumeFileException.class);
        assertThatThrownBy(() -> storageService.storeResumeFile(textAsPdf, userUuid))
                .isInstanceOf(InvalidResumeFileException.class)
                .hasMessageContaining("signature");
        assertThatThrownBy(() -> storageService.storeResumeFile(exeAsPdf, userUuid))
                .isInstanceOf(InvalidResumeFileException.class);
        assertThatThrownBy(() -> storageService.storeResumeFile(wrongMimePdf, userUuid))
                .isInstanceOf(InvalidResumeFileException.class)
                .hasMessageContaining("MIME");
        assertThatThrownBy(() -> storageService.storeResumeFile(plainZipAsDocx, userUuid))
                .isInstanceOf(InvalidResumeFileException.class)
                .hasMessageContaining("DOCX");
        assertThatThrownBy(() -> storageService.storeResumeFile(xlsxAsDocx, userUuid))
                .isInstanceOf(InvalidResumeFileException.class)
                .hasMessageContaining("DOCX");
        assertThatThrownBy(() -> storageService.storeResumeFile(zipSlipDocx, userUuid))
                .isInstanceOf(InvalidResumeFileException.class)
                .hasMessageContaining("unsafe relative paths");
        assertThatThrownBy(() -> storageService.storeResumeFile(exeExtension, userUuid))
                .isInstanceOf(InvalidResumeFileException.class)
                .hasMessageContaining("Unsupported file extension");
        assertThatThrownBy(() -> storageService.storeResumeFile(traversalFilename, userUuid))
                .isInstanceOf(InvalidResumeFileException.class)
                .hasMessageContaining("Path traversal");

        try (Stream<Path> walk = Files.walk(tempDir)) {
            assertThat(walk.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    void storeResumeFile_oversizedDeclaredOrStreamed_throwsPayloadTooLargeAndCleansTempFile() throws Exception {
        byte[] oversized = new byte[1500];
        System.arraycopy("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), 0, oversized, 0, 9);
        MockMultipartFile declaredTooBig = new MockMultipartFile("file", "big.pdf", MIME_PDF, oversized);

        assertThatThrownBy(() -> storageService.storeResumeFile(declaredTooBig, userUuid))
                .isInstanceOf(ResumePayloadTooLargeException.class);

        // Even if reported size lies as 100 bytes, stream ceiling blocks > 1024 bytes and deletes temp file
        MultipartFile lyingFile = mock(MultipartFile.class);
        when(lyingFile.isEmpty()).thenReturn(false);
        when(lyingFile.getSize()).thenReturn(100L);
        when(lyingFile.getOriginalFilename()).thenReturn("streamed.pdf");
        when(lyingFile.getContentType()).thenReturn(MIME_PDF);
        when(lyingFile.getInputStream()).thenReturn(new ByteArrayInputStream(oversized));

        assertThatThrownBy(() -> storageService.storeResumeFile(lyingFile, userUuid))
                .isInstanceOf(ResumePayloadTooLargeException.class);

        try (Stream<Path> walk = Files.walk(tempDir)) {
            assertThat(walk.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    void pathTraversalKeysOnLoadAndDelete_areRejectedStrictly() {
        assertThatThrownBy(() -> storageService.loadResumeAsResource("../secret.pdf"))
                .isInstanceOf(ResumeStorageException.class);
        assertThatThrownBy(() -> storageService.loadResumeAsResource("/etc/passwd"))
                .isInstanceOf(ResumeStorageException.class);
        assertThatThrownBy(() -> storageService.deleteResumeFile("../../outside.pdf"))
                .isInstanceOf(ResumeStorageException.class);
        assertThatThrownBy(() -> storageService.loadResumeAsResource("  "))
                .isInstanceOf(ResumeStorageException.class);
    }

    private byte[] createZipBytes(String... entriesAndContents) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (int i = 0; i < entriesAndContents.length; i += 2) {
                zos.putNextEntry(new ZipEntry(entriesAndContents[i]));
                zos.write(entriesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}

