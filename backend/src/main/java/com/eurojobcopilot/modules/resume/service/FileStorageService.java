package com.eurojobcopilot.modules.resume.service;

import com.eurojobcopilot.modules.resume.dto.ResumeExportFormat;
import com.eurojobcopilot.modules.resume.dto.StoredResumeFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction for validating, storing, retrieving, and deleting uploaded and generated resume binaries.
 */
public interface FileStorageService {

    /**
     * Validates and persists an uploaded resume file under the user's isolated storage prefix.
     */
    StoredResumeFile storeResumeFile(MultipartFile file, String userUuid);

    /**
     * Validates and persists an exported tailored PDF or DOCX binary under the user's isolated storage prefix.
     */
    StoredResumeFile storeGeneratedResumeFile(
            byte[] content,
            String userUuid,
            ResumeExportFormat format,
            String suggestedFilename
    );

    /**
     * Checks whether a previously stored resume key points to an existing readable file.
     */
    boolean resumeFileExists(String storageKey);

    /**
     * Loads a previously stored resume file as a readable Spring Resource.
     */
    Resource loadResumeAsResource(String storageKey);

    /**
     * Deletes a previously stored resume file if it exists.
     */
    void deleteResumeFile(String storageKey);
}

