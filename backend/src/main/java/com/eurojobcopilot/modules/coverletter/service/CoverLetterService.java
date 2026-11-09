package com.eurojobcopilot.modules.coverletter.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterDownloadResource;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterExportFormat;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterResponse;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterSummaryDto;
import com.eurojobcopilot.modules.coverletter.dto.GenerateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.dto.UpdateCoverLetterRequest;

import java.util.List;

public interface CoverLetterService {

    CoverLetterResponse generateCoverLetter(
            AuthenticatedUserPrincipal currentUser,
            GenerateCoverLetterRequest request
    );

    List<CoverLetterSummaryDto> listCoverLetters(
            AuthenticatedUserPrincipal currentUser,
            Long jobId
    );

    CoverLetterResponse getCoverLetter(
            AuthenticatedUserPrincipal currentUser,
            String uuid
    );

    CoverLetterResponse updateCoverLetter(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            UpdateCoverLetterRequest request
    );

    CoverLetterDownloadResource exportCoverLetter(
            AuthenticatedUserPrincipal currentUser,
            String uuid,
            CoverLetterExportFormat format
    );

    void deleteCoverLetter(
            AuthenticatedUserPrincipal currentUser,
            String uuid
    );
}

