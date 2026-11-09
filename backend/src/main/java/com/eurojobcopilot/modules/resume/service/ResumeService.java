package com.eurojobcopilot.modules.resume.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.resume.dto.ApproveMasterResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ResumeDownloadResource;
import com.eurojobcopilot.modules.resume.dto.ResumeParseResponse;
import com.eurojobcopilot.modules.resume.dto.ResumeResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Business operations for authenticated user master resume storage, AI parsing, human review, and profile sync lifecycle.
 */
public interface ResumeService {

    ResumeResponse uploadMasterResume(AuthenticatedUserPrincipal currentUser, MultipartFile file, String title);

    List<ResumeResponse> listResumes(AuthenticatedUserPrincipal currentUser);

    ResumeResponse getResume(AuthenticatedUserPrincipal currentUser, String uuid);

    ResumeDownloadResource downloadResume(AuthenticatedUserPrincipal currentUser, String uuid);

    void deleteResume(AuthenticatedUserPrincipal currentUser, String uuid);

    ResumeParseResponse parseMasterResume(AuthenticatedUserPrincipal currentUser, String uuid, boolean forceRefresh);

    ResumeParseResponse getParsedResume(AuthenticatedUserPrincipal currentUser, String uuid);

    ResumeResponse approveMasterResume(AuthenticatedUserPrincipal currentUser, ApproveMasterResumeRequest request);
}
