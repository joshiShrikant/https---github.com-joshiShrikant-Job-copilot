package com.eurojobcopilot.modules.resume.parser;

import com.eurojobcopilot.modules.resume.config.ResumeStorageProperties;
import com.eurojobcopilot.modules.resume.exception.InvalidResumeFileException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts normalized readable text from stored PDF (Apache PDFBox) and OOXML DOCX resume files,
 * enforcing file-size caps, encryption checks, ZIP-bomb bounds, XXE protections, and minimum text thresholds.
 */
@Slf4j
@Component
public class ResumeTextExtractor {

    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final int MIN_READABLE_NON_WHITESPACE_CHARS = 30;
    private static final int MAX_PDF_PAGES = 50;
    private static final int MAX_ZIP_ENTRIES = 2048;
    private static final long MAX_DOCX_XML_TOTAL_BYTES = 15L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 8192;

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("[ \\t\\x0B\\f]+");
    private static final Pattern TRAILING_LINE_SPACES = Pattern.compile("[ \\t]+\\n");
    private static final Pattern LEADING_LINE_SPACES = Pattern.compile("\\n[ \\t]+");
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{3,}");

    private final long maxFileSizeBytes;

    public ResumeTextExtractor(ResumeStorageProperties storageProperties) {
        long configuredLimit = storageProperties != null ? storageProperties.getMaxFileSizeBytes() : 10L * 1024L * 1024L;
        this.maxFileSizeBytes = Math.max(1024L, configuredLimit);
    }

    public ExtractedResumeText extract(Resource resource, String mimeType, String fileName) {
        if (resource == null) {
            throw new InvalidResumeFileException("Stored resume resource is missing");
        }

        byte[] fileBytes = readBoundedBytes(resource);
        boolean isPdf = isPdfFile(mimeType, fileName);
        boolean isDocx = isDocxFile(mimeType, fileName);

        if (isPdf) {
            return extractFromPdf(fileBytes);
        }
        if (isDocx) {
            return extractFromDocx(fileBytes);
        }
        throw new InvalidResumeFileException("Unsupported resume format for text extraction: only PDF and DOCX are supported");
    }

    private ExtractedResumeText extractFromPdf(byte[] bytes) {
        List<String> warnings = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()
                    && document.getCurrentAccessPermission() != null
                    && !document.getCurrentAccessPermission().canExtractContent()) {
                throw new InvalidResumeFileException("PDF resume restricts text extraction; please upload an unlocked PDF or DOCX file");
            }

            int totalPages = document.getNumberOfPages();
            if (totalPages <= 0) {
                throw new InvalidResumeFileException("Uploaded PDF resume contains zero readable pages");
            }

            int pagesToExtract = Math.min(totalPages, MAX_PDF_PAGES);
            if (totalPages > MAX_PDF_PAGES) {
                warnings.add("PDF exceeded " + MAX_PDF_PAGES + " pages; only the first " + MAX_PDF_PAGES + " pages were extracted.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(pagesToExtract);

            String rawText = stripper.getText(document);
            String normalized = normalizeExtractedText(rawText);
            if (countNonWhitespaceChars(normalized) < MIN_READABLE_NON_WHITESPACE_CHARS) {
                throw new InvalidResumeFileException(
                        "No readable text could be extracted from the uploaded PDF resume (it may be scanned, image-only, or empty). "
                                + "Please upload a selectable-text PDF or DOCX resume."
                );
            }

            return new ExtractedResumeText(normalized, normalized.length(), totalPages, "PDF", List.copyOf(warnings));
        } catch (InvalidPasswordException ex) {
            throw new InvalidResumeFileException(
                    "Encrypted or password-protected PDF resumes cannot be parsed automatically. Please upload an unencrypted PDF or DOCX file."
            );
        } catch (InvalidResumeFileException ex) {
            throw ex;
        } catch (IOException ex) {
            log.debug("Failed to read PDF resume content", ex);
            throw new InvalidResumeFileException("Uploaded PDF resume is corrupted or unreadable");
        }
    }

    private ExtractedResumeText extractFromDocx(byte[] bytes) {
        Map<String, byte[]> headerParts = new TreeMap<>(Comparator.naturalOrder());
        byte[] mainDocumentXml = null;
        Map<String, byte[]> footerParts = new TreeMap<>(Comparator.naturalOrder());

        long cumulativeXmlBytes = 0L;
        int entryCount = 0;

        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new InvalidResumeFileException("DOCX archive exceeds safe entry limit");
                }
                if (entry.isDirectory() || entry.getName() == null) {
                    zipIn.closeEntry();
                    continue;
                }

                String normalizedEntryName = entry.getName().replace('\\', '/');
                if (normalizedEntryName.contains("..") || normalizedEntryName.startsWith("/")) {
                    throw new InvalidResumeFileException("DOCX archive contains unsafe entry path");
                }

                if (isExtractableWordXml(normalizedEntryName)) {
                    byte[] xmlBytes = readBoundedZipEntry(zipIn, cumulativeXmlBytes, normalizedEntryName);
                    cumulativeXmlBytes += xmlBytes.length;

                    if ("word/document.xml".equals(normalizedEntryName)) {
                        mainDocumentXml = xmlBytes;
                    } else if (normalizedEntryName.startsWith("word/header")) {
                        headerParts.put(normalizedEntryName, xmlBytes);
                    } else if (normalizedEntryName.startsWith("word/footer")) {
                        footerParts.put(normalizedEntryName, xmlBytes);
                    }
                }
                zipIn.closeEntry();
            }
        } catch (InvalidResumeFileException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            log.debug("Failed to read DOCX zip structure", ex);
            throw new InvalidResumeFileException("Uploaded DOCX resume is corrupted or unreadable");
        }

        if (mainDocumentXml == null) {
            throw new InvalidResumeFileException("Uploaded DOCX resume is missing word/document.xml content");
        }

        XMLInputFactory xmlInputFactory = createSecureXmlInputFactory();
        StringBuilder combined = new StringBuilder();

        for (byte[] headerXml : headerParts.values()) {
            appendDocxPartText(xmlInputFactory, headerXml, combined);
        }
        appendDocxPartText(xmlInputFactory, mainDocumentXml, combined);
        for (byte[] footerXml : footerParts.values()) {
            appendDocxPartText(xmlInputFactory, footerXml, combined);
        }

        String normalized = normalizeExtractedText(combined.toString());
        if (countNonWhitespaceChars(normalized) < MIN_READABLE_NON_WHITESPACE_CHARS) {
            throw new InvalidResumeFileException(
                    "No readable text could be extracted from the uploaded DOCX resume. Please verify the file contains text content."
            );
        }

        return new ExtractedResumeText(normalized, normalized.length(), 1, "DOCX", List.of());
    }

    private void appendDocxPartText(XMLInputFactory factory, byte[] xmlBytes, StringBuilder out) {
        if (xmlBytes == null || xmlBytes.length == 0) {
            return;
        }
        StringBuilder partText = new StringBuilder();
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name());
            try {
                boolean insideTextRun = false;
                boolean insideDeletedText = false;
                boolean paragraphHasContent = false;
                boolean rowHasContent = false;

                while (reader.hasNext()) {
                    int eventType = reader.next();
                    switch (eventType) {
                        case XMLStreamConstants.START_ELEMENT -> {
                            String localName = reader.getLocalName();
                            if ("delText".equals(localName)) {
                                insideDeletedText = true;
                            } else if ("t".equals(localName) && !insideDeletedText) {
                                insideTextRun = true;
                            } else if ("tab".equals(localName)) {
                                partText.append(' ');
                            } else if ("br".equals(localName) || "cr".equals(localName)) {
                                partText.append('\n');
                            }
                        }
                        case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
                            if (insideTextRun && !insideDeletedText) {
                                String text = reader.getText();
                                if (text != null && !text.isEmpty()) {
                                    partText.append(text);
                                    paragraphHasContent = true;
                                    rowHasContent = true;
                                }
                            }
                        }
                        case XMLStreamConstants.END_ELEMENT -> {
                            String localName = reader.getLocalName();
                            if ("t".equals(localName)) {
                                insideTextRun = false;
                            } else if ("delText".equals(localName)) {
                                insideDeletedText = false;
                            } else if ("tc".equals(localName)) {
                                if (paragraphHasContent && !endsWithSeparator(partText)) {
                                    partText.append(" | ");
                                }
                            } else if ("p".equals(localName)) {
                                if (paragraphHasContent) {
                                    partText.append('\n');
                                    paragraphHasContent = false;
                                }
                            } else if ("tr".equals(localName)) {
                                if (rowHasContent) {
                                    trimTrailingCellDelimiter(partText);
                                    partText.append('\n');
                                    rowHasContent = false;
                                }
                            }
                        }
                        default -> {
                            // Ignore comments, PIs, etc.
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException ex) {
            log.debug("Malformed XML inside DOCX part", ex);
            throw new InvalidResumeFileException("Uploaded DOCX resume contains malformed or unreadable XML content");
        }

        String cleanedPart = partText.toString().trim();
        if (!cleanedPart.isEmpty()) {
            if (!out.isEmpty()) {
                out.append("\n\n");
            }
            out.append(cleanedPart);
        }
    }

    private XMLInputFactory createSecureXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        try {
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Not every StAX provider implements JAXP external access properties; SUPPORT_DTD=false already blocks DTDs.
        }
        return factory;
    }

    private byte[] readBoundedBytes(Resource resource) {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0L;
        List<byte[]> chunks = new ArrayList<>();
        try (InputStream in = resource.getInputStream()) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxFileSizeBytes) {
                    throw new InvalidResumeFileException("Stored resume file exceeds safe parsing size limit");
                }
                byte[] copy = new byte[read];
                System.arraycopy(buffer, 0, copy, 0, read);
                chunks.add(copy);
            }
        } catch (InvalidResumeFileException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new InvalidResumeFileException("Failed to read stored resume file bytes");
        }

        byte[] result = new byte[(int) total];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        return result;
    }

    private byte[] readBoundedZipEntry(ZipInputStream zipIn, long cumulativeSoFar, String entryName) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long entryTotal = 0L;
        List<byte[]> chunks = new ArrayList<>();
        int read;
        while ((read = zipIn.read(buffer)) != -1) {
            entryTotal += read;
            if (cumulativeSoFar + entryTotal > MAX_DOCX_XML_TOTAL_BYTES) {
                throw new InvalidResumeFileException(
                        "DOCX XML payload exceeded maximum allowed decompressed size (" + entryName + ")"
                );
            }
            byte[] copy = new byte[read];
            System.arraycopy(buffer, 0, copy, 0, read);
            chunks.add(copy);
        }

        byte[] result = new byte[(int) entryTotal];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        return result;
    }

    private boolean isExtractableWordXml(String normalizedEntryName) {
        if ("word/document.xml".equals(normalizedEntryName)) {
            return true;
        }
        return (normalizedEntryName.startsWith("word/header") || normalizedEntryName.startsWith("word/footer"))
                && normalizedEntryName.endsWith(".xml");
    }

    private boolean isPdfFile(String mimeType, String fileName) {
        if (mimeType != null && MIME_PDF.equalsIgnoreCase(mimeType.split(";", 2)[0].trim())) {
            return true;
        }
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private boolean isDocxFile(String mimeType, String fileName) {
        if (mimeType != null && MIME_DOCX.equalsIgnoreCase(mimeType.split(";", 2)[0].trim())) {
            return true;
        }
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".docx");
    }

    private String normalizeExtractedText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.replace('\u0000', ' ')
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        text = MULTIPLE_SPACES.matcher(text).replaceAll(" ");
        text = TRAILING_LINE_SPACES.matcher(text).replaceAll("\n");
        text = LEADING_LINE_SPACES.matcher(text).replaceAll("\n");
        text = EXCESS_BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.trim();
    }

    private long countNonWhitespaceChars(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        return text.chars().filter(ch -> !Character.isWhitespace(ch)).count();
    }

    private boolean endsWithSeparator(StringBuilder sb) {
        int len = sb.length();
        return len >= 3 && sb.substring(len - 3).equals(" | ");
    }

    private void trimTrailingCellDelimiter(StringBuilder sb) {
        if (endsWithSeparator(sb)) {
            sb.setLength(sb.length() - 3);
        }
    }
}

