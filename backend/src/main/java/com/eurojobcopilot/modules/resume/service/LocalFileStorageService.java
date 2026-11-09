package com.eurojobcopilot.modules.resume.service;

import com.eurojobcopilot.modules.resume.config.ResumeStorageProperties;
import com.eurojobcopilot.modules.resume.dto.ResumeExportFormat;
import com.eurojobcopilot.modules.resume.dto.StoredResumeFile;
import com.eurojobcopilot.modules.resume.exception.InvalidResumeFileException;
import com.eurojobcopilot.modules.resume.exception.ResumeNotFoundException;
import com.eurojobcopilot.modules.resume.exception.ResumePayloadTooLargeException;
import com.eurojobcopilot.modules.resume.exception.ResumeStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04};

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_ZIP_ENTRIES = 2048;
    private static final int MAX_DISPLAY_FILENAME_LENGTH = 200;

    private static final Pattern SAFE_USER_SEGMENT_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._ ()-]");

    private final Path rootLocation;
    private final long maxFileSizeBytes;

    public LocalFileStorageService(ResumeStorageProperties storageProperties) {
        if (storageProperties == null || storageProperties.getResumesPath() == null
                || storageProperties.getResumesPath().isBlank()
                || storageProperties.getResumesPath().contains("\0")) {
            throw new ResumeStorageException("Resume storage root path is not configured properly");
        }
        this.rootLocation = Path.of(storageProperties.getResumesPath()).toAbsolutePath().normalize();
        this.maxFileSizeBytes = storageProperties.getMaxFileSizeBytes();
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException ex) {
            throw new ResumeStorageException("Failed to initialize resume storage directory", ex);
        }
    }

    @Override
    public StoredResumeFile storeResumeFile(MultipartFile file, String userUuid) {
        String safeUserSegment = validateUserSegment(userUuid);

        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new InvalidResumeFileException("Resume file is required and must not be empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResumePayloadTooLargeException(
                    "Uploaded resume exceeds maximum allowed size of " + maxFileSizeBytes + " bytes"
            );
        }

        ValidatedFileMetadata validatedMetadata = validateFilenameAndMime(
                file.getOriginalFilename(),
                file.getContentType()
        );

        Path userDirectory = resolveSafeChild(rootLocation, safeUserSegment);
        Path tempFile = null;
        try {
            Files.createDirectories(userDirectory);
            tempFile = Files.createTempFile(userDirectory, "resume-upload-", ".tmp");

            long actualBytes = copyWithSizeCeiling(file, tempFile);
            if (actualBytes <= 0) {
                throw new InvalidResumeFileException("Resume file must not be empty");
            }

            verifyFileSignature(tempFile, validatedMetadata.fileFormat());

            String storedFileName = UUID.randomUUID() + "." + validatedMetadata.fileFormat().extension;
            Path targetFile = resolveSafeChild(userDirectory, storedFileName);
            moveAtomicallyWithFallback(tempFile, targetFile);
            tempFile = null; // Successfully moved to final destination

            String storageKey = safeUserSegment + "/" + storedFileName;
            return new StoredResumeFile(
                    storageKey,
                    validatedMetadata.sanitizedFileName(),
                    validatedMetadata.fileFormat().mimeType,
                    actualBytes
            );
        } catch (InvalidResumeFileException | ResumePayloadTooLargeException | ResumeStorageException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResumeStorageException("Failed to store uploaded resume file", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupEx) {
                    log.warn("Failed to clean up temporary resume upload file {}", tempFile, cleanupEx);
                }
            }
        }
    }

    @Override
    public StoredResumeFile storeGeneratedResumeFile(
            byte[] content,
            String userUuid,
            ResumeExportFormat format,
            String suggestedFilename
    ) {
        String safeUserSegment = validateUserSegment(userUuid);
        if (format == null) {
            throw new ResumeStorageException("Export format is required");
        }
        if (content == null || content.length == 0) {
            throw new ResumeStorageException("Generated resume binary must not be empty");
        }
        if (content.length > maxFileSizeBytes) {
            throw new ResumePayloadTooLargeException(
                    "Generated resume exceeds maximum allowed size of " + maxFileSizeBytes + " bytes"
            );
        }

        AllowedFormat allowedFormat = format == ResumeExportFormat.PDF ? AllowedFormat.PDF : AllowedFormat.DOCX;
        String sanitizedFilename = sanitizeGeneratedFilename(suggestedFilename, allowedFormat);

        Path userDirectory = resolveSafeChild(rootLocation, safeUserSegment);
        Path tempFile = null;
        try {
            Files.createDirectories(userDirectory);
            tempFile = Files.createTempFile(userDirectory, "resume-export-", ".tmp");
            Files.write(tempFile, content);

            verifyFileSignature(tempFile, allowedFormat);

            String storedFileName = UUID.randomUUID() + "." + allowedFormat.extension;
            Path targetFile = resolveSafeChild(userDirectory, storedFileName);
            moveAtomicallyWithFallback(tempFile, targetFile);
            tempFile = null;

            String storageKey = safeUserSegment + "/" + storedFileName;
            return new StoredResumeFile(
                    storageKey,
                    sanitizedFilename,
                    allowedFormat.mimeType,
                    content.length
            );
        } catch (InvalidResumeFileException | ResumePayloadTooLargeException | ResumeStorageException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResumeStorageException("Failed to store exported resume file", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupEx) {
                    log.warn("Failed to clean up temporary resume export file {}", tempFile, cleanupEx);
                }
            }
        }
    }

    @Override
    public boolean resumeFileExists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        try {
            Path resolved = resolveStorageKey(storageKey);
            return Files.exists(resolved) && Files.isRegularFile(resolved) && Files.isReadable(resolved);
        } catch (ResumeStorageException ex) {
            return false;
        }
    }

    @Override
    public Resource loadResumeAsResource(String storageKey) {
        Path resolved = resolveStorageKey(storageKey);
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
            throw new ResumeNotFoundException("Stored resume file not found");
        }
        return new PathResource(resolved);
    }

    @Override
    public void deleteResumeFile(String storageKey) {
        Path resolved = resolveStorageKey(storageKey);
        try {
            Files.deleteIfExists(resolved);
        } catch (IOException ex) {
            throw new ResumeStorageException("Failed to delete stored resume file", ex);
        }
    }

    private long copyWithSizeCeiling(MultipartFile file, Path destination) throws IOException {
        long totalBytes = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = file.getInputStream();
             OutputStream out = Files.newOutputStream(destination)) {
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > maxFileSizeBytes) {
                    throw new ResumePayloadTooLargeException(
                            "Uploaded resume exceeds maximum allowed size of " + maxFileSizeBytes + " bytes"
                    );
                }
                out.write(buffer, 0, bytesRead);
            }
        }
        return totalBytes;
    }

    private void verifyFileSignature(Path filePath, AllowedFormat format) throws IOException {
        byte[] header = new byte[8];
        int headerRead;
        try (InputStream in = Files.newInputStream(filePath)) {
            headerRead = in.readNBytes(header, 0, header.length);
        }

        if (format == AllowedFormat.PDF) {
            if (headerRead < PDF_MAGIC.length || !Arrays.equals(header, 0, PDF_MAGIC.length, PDF_MAGIC, 0, PDF_MAGIC.length)) {
                throw new InvalidResumeFileException("File signature does not match a valid PDF document");
            }
            return;
        }

        if (format == AllowedFormat.DOCX) {
            if (headerRead < ZIP_MAGIC.length || !Arrays.equals(header, 0, ZIP_MAGIC.length, ZIP_MAGIC, 0, ZIP_MAGIC.length)) {
                throw new InvalidResumeFileException("File signature does not match a valid DOCX document");
            }
            verifyDocxArchiveStructure(filePath);
        }
    }

    private void verifyDocxArchiveStructure(Path filePath) {
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;
        int entryCount = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(filePath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new InvalidResumeFileException("DOCX archive exceeds safe entry limit");
                }
                String entryName = entry.getName();
                if (entryName != null) {
                    String normalizedEntryName = entryName.replace('\\', '/');
                    if (normalizedEntryName.contains("..")) {
                        throw new InvalidResumeFileException("DOCX archive contains unsafe relative paths");
                    }
                    if ("[Content_Types].xml".equals(normalizedEntryName)) {
                        hasContentTypes = true;
                    } else if ("word/document.xml".equals(normalizedEntryName)
                            || (normalizedEntryName.startsWith("word/") && normalizedEntryName.endsWith(".xml"))) {
                        hasWordDocument = true;
                    }
                }
                zipInputStream.closeEntry();
            }
        } catch (InvalidResumeFileException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw new InvalidResumeFileException("Uploaded DOCX file is corrupted or unreadable");
        }

        if (!hasContentTypes || !hasWordDocument) {
            throw new InvalidResumeFileException("Uploaded file is not a valid OOXML DOCX document");
        }
    }

    private ValidatedFileMetadata validateFilenameAndMime(String rawFilename, String rawContentType) {
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new InvalidResumeFileException("Resume filename is required");
        }
        if (rawFilename.indexOf('\0') >= 0) {
            throw new InvalidResumeFileException("Invalid characters in resume filename");
        }

        String normalizedSeparators = rawFilename.replace('\\', '/');
        for (String segment : normalizedSeparators.split("/", -1)) {
            if ("..".equals(segment.trim())) {
                throw new InvalidResumeFileException("Path traversal sequence is not allowed in resume filename");
            }
        }

        int lastSlash = normalizedSeparators.lastIndexOf('/');
        String basename = (lastSlash >= 0 ? normalizedSeparators.substring(lastSlash + 1) : normalizedSeparators).trim();

        int lastDot = basename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == basename.length() - 1) {
            throw new InvalidResumeFileException("Only .pdf and .docx resume files are supported");
        }

        String extension = basename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        AllowedFormat format = AllowedFormat.fromExtension(extension);
        if (format == null) {
            throw new InvalidResumeFileException("Unsupported file extension '." + extension + "'. Only PDF and DOCX are allowed");
        }

        String normalizedMime = normalizeMimeType(rawContentType);
        if (normalizedMime == null || !format.mimeType.equals(normalizedMime)) {
            throw new InvalidResumeFileException(
                    "Invalid MIME content type for ." + extension + " resume. Expected " + format.mimeType
            );
        }

        String sanitizedFilename = sanitizeStem(basename.substring(0, lastDot), format) + "." + format.extension;
        return new ValidatedFileMetadata(format, sanitizedFilename);
    }

    private String sanitizeGeneratedFilename(String suggestedFilename, AllowedFormat format) {
        String candidate = suggestedFilename == null ? "" : suggestedFilename.replace('\\', '/');
        int lastSlash = candidate.lastIndexOf('/');
        String basename = lastSlash >= 0 ? candidate.substring(lastSlash + 1) : candidate;
        String suffix = "." + format.extension;
        if (basename.toLowerCase(Locale.ROOT).endsWith(suffix)) {
            basename = basename.substring(0, basename.length() - suffix.length());
        }
        return sanitizeStem(basename, format) + suffix;
    }

    private String sanitizeStem(String rawStem, AllowedFormat format) {
        String cleanedStem = UNSAFE_FILENAME_CHARS.matcher(rawStem == null ? "" : rawStem).replaceAll("_").trim();
        while (cleanedStem.startsWith(".")) {
            cleanedStem = cleanedStem.substring(1).trim();
        }
        if (cleanedStem.isEmpty()) {
            cleanedStem = "resume";
        }
        int maxStemLength = MAX_DISPLAY_FILENAME_LENGTH - format.extension.length() - 1;
        if (cleanedStem.length() > maxStemLength) {
            cleanedStem = cleanedStem.substring(0, maxStemLength).trim();
        }
        return cleanedStem;
    }

    private String normalizeMimeType(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            return null;
        }
        String baseMime = rawContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return baseMime.isEmpty() ? null : baseMime;
    }

    private String validateUserSegment(String userUuid) {
        if (userUuid == null || !SAFE_USER_SEGMENT_PATTERN.matcher(userUuid).matches()) {
            throw new ResumeStorageException("Invalid user storage namespace");
        }
        return userUuid;
    }

    private Path resolveSafeChild(Path parent, String childSegment) {
        Path resolved = parent.resolve(childSegment).normalize();
        if (!resolved.startsWith(parent) || resolved.equals(parent)) {
            throw new ResumeStorageException("Resolved path escapes storage directory");
        }
        return resolved;
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.indexOf('\0') >= 0) {
            throw new ResumeStorageException("Invalid resume storage key");
        }
        try {
            Path keyPath = Path.of(storageKey);
            if (keyPath.isAbsolute()) {
                throw new ResumeStorageException("Absolute resume storage paths are forbidden");
            }
            Path resolved = rootLocation.resolve(keyPath).normalize();
            if (!resolved.startsWith(rootLocation) || resolved.equals(rootLocation)) {
                throw new ResumeStorageException("Access outside configured resume storage root is forbidden");
            }
            return resolved;
        } catch (InvalidPathException ex) {
            throw new ResumeStorageException("Malformed resume storage key", ex);
        }
    }

    private void moveAtomicallyWithFallback(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicEx) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private enum AllowedFormat {
        PDF("pdf", MIME_PDF),
        DOCX("docx", MIME_DOCX);

        private final String extension;
        private final String mimeType;

        AllowedFormat(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        private static AllowedFormat fromExtension(String extension) {
            for (AllowedFormat candidate : values()) {
                if (candidate.extension.equals(extension)) {
                    return candidate;
                }
            }
            return null;
        }
    }

    private record ValidatedFileMetadata(AllowedFormat fileFormat, String sanitizedFileName) {
    }
}
