package com.eurojobcopilot.modules.resume.tailored;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.engine.CandidateMatchContext;
import com.eurojobcopilot.modules.matching.engine.DeterministicJobMatchingEngine;
import com.eurojobcopilot.modules.matching.engine.DeterministicMatchResult;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.resume.dto.GenerateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ResumeExportFormat;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeDataDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeResponse;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeSummaryDto;
import com.eurojobcopilot.modules.resume.dto.UpdateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.TailoredResume;
import com.eurojobcopilot.modules.resume.exception.ResumeConflictException;
import com.eurojobcopilot.modules.resume.exception.ResumeNotFoundException;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.resume.repository.TailoredResumeRepository;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.HeaderEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.MasterResumeEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.WorkExperienceEvidence;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.UserEducation;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates short transactional reads and writes for tailored resume drafts, isolating
 * external AI invocation and binary document text extraction outside active database locks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TailoredResumePersistenceHelper {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };
    private static final int MAX_TITLE_LENGTH = 200;

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserWorkExperienceRepository userWorkExperienceRepository;
    private final UserEducationRepository userEducationRepository;
    private final UserJobPreferenceRepository userJobPreferenceRepository;
    private final ResumeRepository resumeRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final JobRepository jobRepository;
    private final JobAiAnalysisRepository jobAiAnalysisRepository;
    private final CompanyRepository companyRepository;
    private final JobMatchScoreRepository jobMatchScoreRepository;
    private final DeterministicJobMatchingEngine deterministicEngine;
    private final JobNormalizer jobNormalizer;
    private final CanonicalHashGenerator canonicalHashGenerator;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TailoredResumeContextSnapshot loadGenerationSnapshot(Long userId, GenerateTailoredResumeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        Job job = jobRepository.findByIdWithCompany(request.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + request.getJobId()));

        Resume masterResume = resolveMasterResume(userId, request.getMasterResumeUuid());
        ResumeTemplate template = request.getTemplateName() != null ? request.getTemplateName() : ResumeTemplate.MODERN;
        String focus = request.getTailoringFocus() != null && !request.getTailoringFocus().isBlank()
                ? request.getTailoringFocus().trim()
                : null;

        TailoredResumeResponse cachedDraft = null;
        if (Boolean.FALSE.equals(request.getForceRefresh())) {
            cachedDraft = tailoredResumeRepository
                    .findFirstByUserIdAndJobIdAndMasterResumeIdAndTemplateNameOrderByVersionNumberDescCreatedAtDesc(
                            userId, job.getId(), masterResume.getId(), template
                    )
                    .map(existing -> toResponse(existing, deserializeData(existing.getResumeDataJson())))
                    .orElse(null);
        }

        return assembleContextSnapshot(user, job, masterResume, template, focus, cachedDraft);
    }

    @Transactional(readOnly = true)
    public EditContextLoad loadEditContextSnapshot(Long userId, String uuid) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        TailoredResume entity = tailoredResumeRepository.findWithDetailsByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new ResumeNotFoundException("Tailored resume not found"));

        TailoredResumeDataDto existingData = deserializeData(entity.getResumeDataJson());
        TailoredResumeContextSnapshot snapshot = assembleContextSnapshot(
                user,
                entity.getJob(),
                entity.getMasterResume(),
                entity.getTemplateName(),
                entity.getTailoringFocus(),
                null
        );
        return new EditContextLoad(entity.getUuid(), existingData, snapshot);
    }

    @Transactional
    public TailoredResumeResponse saveGeneratedDraft(
            TailoredResumeContextSnapshot snapshot,
            ValidatedTailoredResume validated,
            AiStructuredResponse<RawAiTailoredResumePayload> aiResponse
    ) {
        User user = userRepository.findById(snapshot.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        Job job = jobRepository.findByIdWithCompany(snapshot.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + snapshot.jobId()));
        Resume masterResume = resumeRepository.findById(snapshot.masterResume().id())
                .orElseThrow(() -> new ResumeNotFoundException("Master resume not found"));

        Integer currentMax = tailoredResumeRepository.findMaxVersionNumberByUserIdAndJobId(user.getId(), job.getId());
        int nextVersion = (currentMax == null ? 0 : currentMax) + 1;

        ResumeTemplate template = snapshot.templateName() != null ? snapshot.templateName() : ResumeTemplate.MODERN;
        String title = buildDefaultTitle(job.getTitle(), job.getCompanyName(), nextVersion);
        Map<String, Object> serializedData = objectMapper.convertValue(validated.resumeData(), MAP_TYPE_REF);

        TailoredResume entity = TailoredResume.builder()
                .uuid(UUID.randomUUID().toString())
                .masterResume(masterResume)
                .job(job)
                .user(user)
                .templateName(template)
                .title(title)
                .versionNumber(nextVersion)
                .tailoringFocus(snapshot.tailoringFocus())
                .resumeDataJson(serializedData != null ? serializedData : new LinkedHashMap<>())
                .matchScore(snapshot.baselineMatchScore() != null ? snapshot.baselineMatchScore() : BigDecimal.ZERO)
                .atsKeywordCoverage(validated.atsKeywordCoverage() != null ? validated.atsKeywordCoverage() : BigDecimal.ZERO)
                .aiProvider(aiResponse != null ? aiResponse.provider() : null)
                .aiModelUsed(aiResponse != null ? aiResponse.model() : null)
                .generatedAt(Instant.now())
                .editedByUser(false)
                .build();

        TailoredResume saved = tailoredResumeRepository.saveAndFlush(entity);
        log.info("Generated tailored resume uuid={} version={} jobId={} userId={} atsCoverage={}%",
                saved.getUuid(), saved.getVersionNumber(), job.getId(), user.getId(), saved.getAtsKeywordCoverage());

        return toResponse(saved, validated.resumeData());
    }

    @Transactional
    public EditSaveResult saveEditedDraft(
            Long userId,
            String uuid,
            UpdateTailoredResumeRequest request,
            ValidatedTailoredResume validated
    ) {
        TailoredResume entity = tailoredResumeRepository.findWithDetailsByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new ResumeNotFoundException("Tailored resume not found"));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            String trimmedTitle = request.getTitle().trim();
            entity.setTitle(trimmedTitle.length() <= MAX_TITLE_LENGTH
                    ? trimmedTitle
                    : trimmedTitle.substring(0, MAX_TITLE_LENGTH).trim());
        }
        if (request.getTemplateName() != null) {
            entity.setTemplateName(request.getTemplateName());
        }

        // Invalidate previously cached binary exports because content or layout changed
        String previousPdfPath = entity.getPdfFilePath();
        String previousDocxPath = entity.getDocxFilePath();
        entity.setPdfFilePath(null);
        entity.setDocxFilePath(null);

        Map<String, Object> serializedData = objectMapper.convertValue(validated.resumeData(), MAP_TYPE_REF);
        entity.setResumeDataJson(serializedData != null ? serializedData : new LinkedHashMap<>());
        entity.setAtsKeywordCoverage(validated.atsKeywordCoverage() != null ? validated.atsKeywordCoverage() : BigDecimal.ZERO);
        entity.setEditedByUser(true);

        TailoredResume saved = tailoredResumeRepository.saveAndFlush(entity);
        log.info("Updated tailored resume uuid={} editedByUser=true atsCoverage={}%",
                saved.getUuid(), saved.getAtsKeywordCoverage());
        return new EditSaveResult(
                toResponse(saved, validated.resumeData()),
                new ExportFilePaths(previousPdfPath, previousDocxPath)
        );
    }

    @Transactional(readOnly = true)
    public ExportSnapshot loadExportSnapshot(Long userId, String uuid) {
        TailoredResume entity = tailoredResumeRepository.findWithDetailsByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new ResumeNotFoundException("Tailored resume not found"));
        TailoredResumeDataDto data = deserializeData(entity.getResumeDataJson());
        String userUuid = entity.getUser() != null ? entity.getUser().getUuid() : null;
        return new ExportSnapshot(
                entity.getUuid(),
                userUuid,
                entity.getTitle(),
                entity.getTemplateName(),
                entity.getPdfFilePath(),
                entity.getDocxFilePath(),
                data
        );
    }

    @Transactional
    public String persistExportedFilePath(
            Long userId,
            String uuid,
            ResumeExportFormat format,
            String newStorageKey
    ) {
        TailoredResume entity = tailoredResumeRepository.findByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new ResumeNotFoundException("Tailored resume not found"));

        String previousKey;
        if (format == ResumeExportFormat.PDF) {
            previousKey = entity.getPdfFilePath();
            entity.setPdfFilePath(newStorageKey);
        } else {
            previousKey = entity.getDocxFilePath();
            entity.setDocxFilePath(newStorageKey);
        }
        tailoredResumeRepository.saveAndFlush(entity);
        return previousKey;
    }

    @Transactional(readOnly = true)
    public List<TailoredResumeSummaryDto> listTailoredResumes(Long userId, Long jobId) {
        List<TailoredResume> rows = jobId != null
                ? tailoredResumeRepository.findAllWithDetailsByUserIdAndJobIdOrderByVersionDesc(userId, jobId)
                : tailoredResumeRepository.findAllWithDetailsByUserIdOrderByCreatedAtDesc(userId);
        return rows.stream().map(this::toSummaryDto).toList();
    }

    @Transactional(readOnly = true)
    public TailoredResumeResponse getTailoredResume(Long userId, String uuid) {
        TailoredResume entity = tailoredResumeRepository.findWithDetailsByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new ResumeNotFoundException("Tailored resume not found"));
        return toResponse(entity, deserializeData(entity.getResumeDataJson()));
    }

    @Transactional
    public ExportFilePaths deleteTailoredResume(Long userId, String uuid) {
        TailoredResume entity = tailoredResumeRepository.findByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new ResumeNotFoundException("Tailored resume not found"));
        ExportFilePaths exportedPaths = new ExportFilePaths(entity.getPdfFilePath(), entity.getDocxFilePath());
        tailoredResumeRepository.clearApplicationReferences(entity.getId());
        tailoredResumeRepository.clearCoverLetterReferences(entity.getId());
        tailoredResumeRepository.delete(entity);
        tailoredResumeRepository.flush();
        log.info("Deleted tailored resume uuid={} userId={}", uuid, userId);
        return exportedPaths;
    }

    private TailoredResumeContextSnapshot assembleContextSnapshot(
            User user,
            Job job,
            Resume masterResume,
            ResumeTemplate template,
            String tailoringFocus,
            TailoredResumeResponse cachedDraft
    ) {
        Long userId = user.getId();
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        List<UserSkill> skills = userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(userId);
        List<UserWorkExperience> workExperiences = userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(userId);
        List<UserEducation> educations = userEducationRepository.findAllByUserIdOrderByStartDateDesc(userId);
        UserJobPreference preference = userJobPreferenceRepository.findByUserId(userId).orElse(null);
        Company resolvedCompany = resolveCompany(job);
        JobAiAnalysis jobAiAnalysis = jobAiAnalysisRepository.findByJobId(job.getId()).orElse(null);
        JobMatchScore existingScore = jobMatchScoreRepository.findByUserIdAndJobId(userId, job.getId()).orElse(null);

        BigDecimal baselineMatchScore;
        if (existingScore != null && existingScore.getFinalScore() != null) {
            baselineMatchScore = existingScore.getFinalScore();
        } else {
            CandidateMatchContext matchContext = new CandidateMatchContext(profile, skills, workExperiences, preference);
            DeterministicMatchResult det = deterministicEngine.evaluate(matchContext, job, resolvedCompany, null);
            baselineMatchScore = det.finalScore();
        }

        return new TailoredResumeContextSnapshot(
                userId,
                job.getId(),
                template,
                tailoringFocus,
                toHeaderEvidence(user, profile),
                skills.stream().map(this::toSkillEvidence).toList(),
                workExperiences.stream().map(this::toWorkExperienceEvidence).toList(),
                educations.stream().map(this::toEducationEvidence).toList(),
                toMasterResumeEvidence(masterResume),
                toJobEvidence(job, resolvedCompany),
                toJobAiAnalysisEvidence(jobAiAnalysis),
                baselineMatchScore,
                cachedDraft
        );
    }

    private Resume resolveMasterResume(Long userId, String requestedUuid) {
        if (requestedUuid != null && !requestedUuid.isBlank()) {
            Resume resume = resumeRepository.findByUuidAndUserId(requestedUuid.trim(), userId)
                    .orElseThrow(() -> new ResumeNotFoundException("Master resume not found"));
            if (resume.getResumeType() != ResumeType.MASTER) {
                throw new ResumeConflictException("Selected resume is not a MASTER resume");
            }
            return resume;
        }

        return resumeRepository.findFirstByUserIdAndMasterApprovedTrueOrderByVersionNumberDesc(userId)
                .filter(r -> r.getResumeType() == ResumeType.MASTER)
                .or(() -> resumeRepository.findAllByUserIdOrderByVersionNumberDescCreatedAtDesc(userId)
                        .stream()
                        .filter(r -> r.getResumeType() == ResumeType.MASTER)
                        .findFirst())
                .orElseThrow(() -> new BusinessValidationException(
                        "No master resume found. Upload a master resume first before generating a tailored CV."
                ));
    }

    private Company resolveCompany(Job job) {
        if (job.getCompany() != null) {
            return job.getCompany();
        }
        String rawName = job.getCompanyName();
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String canonicalKey = canonicalHashGenerator.normalizeCompanyKey(rawName);
        if (!canonicalKey.isBlank()) {
            Optional<Company> byCanonical = companyRepository.findByNormalizedName(canonicalKey);
            if (byCanonical.isPresent()) {
                return byCanonical.get();
            }
        }
        String lowerTrimmed = rawName.trim().toLowerCase(Locale.ROOT);
        if (!lowerTrimmed.equals(canonicalKey)) {
            return companyRepository.findByNormalizedName(lowerTrimmed).orElse(null);
        }
        return null;
    }

    private HeaderEvidence toHeaderEvidence(User user, UserProfile profile) {
        String fullName = ((user.getFirstName() != null ? user.getFirstName().trim() : "")
                + " " + (user.getLastName() != null ? user.getLastName().trim() : "")).trim();
        return new HeaderEvidence(
                fullName.isBlank() ? null : fullName,
                user.getEmail(),
                profile != null ? profile.getCurrentDesignation() : null,
                profile != null ? profile.getYearsOfExperience() : null,
                profile != null ? profile.getCurrentCountry() : null,
                profile != null ? profile.getCurrentCity() : null,
                profile != null && profile.getWorkAuthorizationStatus() != null
                        ? profile.getWorkAuthorizationStatus().name()
                        : null,
                profile != null && profile.getRelocationPreference() != null
                        ? profile.getRelocationPreference().name()
                        : null,
                profile != null ? profile.getLinkedinUrl() : null,
                profile != null ? profile.getGithubUrl() : null,
                profile != null ? profile.getPortfolioUrl() : null,
                profile != null ? profile.getBio() : null
        );
    }

    private SkillEvidence toSkillEvidence(UserSkill skill) {
        return new SkillEvidence(
                skill.getSkillName(),
                skill.getYearsExperience(),
                skill.getProficiencyLevel() != null ? skill.getProficiencyLevel().name() : null,
                skill.isPrimary()
        );
    }

    private WorkExperienceEvidence toWorkExperienceEvidence(UserWorkExperience exp) {
        return new WorkExperienceEvidence(
                exp.getCompanyName(),
                exp.getDesignation(),
                exp.getLocation(),
                exp.getStartDate(),
                exp.getEndDate(),
                exp.isCurrent(),
                exp.getDescription(),
                exp.getSkillsUsed() == null ? List.of() : List.copyOf(exp.getSkillsUsed())
        );
    }

    private EducationEvidence toEducationEvidence(UserEducation edu) {
        return new EducationEvidence(
                edu.getInstitution(),
                edu.getDegree(),
                edu.getFieldOfStudy(),
                edu.getStartDate(),
                edu.getEndDate(),
                edu.getGradeOrGpa(),
                edu.getCountry()
        );
    }

    private MasterResumeEvidence toMasterResumeEvidence(Resume resume) {
        ParsedResumeJsonDto parsedDto = null;
        if (resume.getParsedContentJson() != null && !resume.getParsedContentJson().isEmpty()) {
            try {
                parsedDto = objectMapper.convertValue(resume.getParsedContentJson(), ParsedResumeJsonDto.class);
            } catch (IllegalArgumentException ex) {
                log.warn("Could not deserialize parsedContentJson for master resume uuid={}", resume.getUuid(), ex);
            }
        }
        return new MasterResumeEvidence(
                resume.getId(),
                resume.getUuid(),
                resume.getTitle(),
                resume.getFileName(),
                resume.getMimeType(),
                resume.getFilePath(),
                resume.getVersionNumber(),
                resume.isMasterApproved(),
                parsedDto,
                null
        );
    }

    private JobEvidence toJobEvidence(Job job, Company resolvedCompany) {
        String cleanDesc = jobNormalizer.sanitizeHtmlToText(job.getDescription());
        if (cleanDesc.isBlank() && job.getDescription() != null) {
            cleanDesc = job.getDescription().trim();
        }
        return new JobEvidence(
                job.getId(),
                job.getTitle(),
                job.getCompanyName(),
                job.getCountry(),
                job.getCity(),
                job.getLocation(),
                job.getWorkArrangement() != null ? job.getWorkArrangement().name() : null,
                job.getEmploymentType() != null ? job.getEmploymentType().name() : null,
                job.getExperienceLevel() != null ? job.getExperienceLevel().name() : null,
                job.getMinExperienceYears(),
                job.getSponsorStatus() != null ? job.getSponsorStatus().name() : null,
                resolvedCompany != null ? resolvedCompany.getIndustry() : null,
                job.getSkills() == null ? List.of() : List.copyOf(job.getSkills()),
                cleanDesc
        );
    }

    private JobAiAnalysisEvidence toJobAiAnalysisEvidence(JobAiAnalysis ai) {
        if (ai == null) {
            return null;
        }
        return new JobAiAnalysisEvidence(
                ai.getRequiredExperienceSummary(),
                ai.getRequiredSkills() == null ? List.of() : List.copyOf(ai.getRequiredSkills()),
                ai.getNiceToHaveSkills() == null ? List.of() : List.copyOf(ai.getNiceToHaveSkills()),
                ai.getResponsibilities() == null ? List.of() : List.copyOf(ai.getResponsibilities()),
                ai.getLanguageRequirements() == null ? List.of() : List.copyOf(ai.getLanguageRequirements()),
                ai.getSummary()
        );
    }

    private TailoredResumeSummaryDto toSummaryDto(TailoredResume entity) {
        Resume master = entity.getMasterResume();
        Job job = entity.getJob();
        return TailoredResumeSummaryDto.builder()
                .uuid(entity.getUuid())
                .masterResumeUuid(master != null ? master.getUuid() : null)
                .masterResumeTitle(master != null ? master.getTitle() : null)
                .jobId(job != null ? job.getId() : null)
                .jobTitle(job != null ? job.getTitle() : null)
                .companyName(job != null ? job.getCompanyName() : null)
                .jobCountry(job != null ? job.getCountry() : null)
                .title(entity.getTitle())
                .versionNumber(entity.getVersionNumber())
                .templateName(entity.getTemplateName())
                .matchScore(entity.getMatchScore() != null ? entity.getMatchScore().doubleValue() : 0.0)
                .atsKeywordCoverage(entity.getAtsKeywordCoverage() != null ? entity.getAtsKeywordCoverage().doubleValue() : 0.0)
                .aiProvider(entity.getAiProvider())
                .aiModelUsed(entity.getAiModelUsed())
                .editedByUser(entity.isEditedByUser())
                .hasPdfExport(hasText(entity.getPdfFilePath()))
                .hasDocxExport(hasText(entity.getDocxFilePath()))
                .generatedAt(entity.getGeneratedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private TailoredResumeResponse toResponse(TailoredResume entity, TailoredResumeDataDto data) {
        Resume master = entity.getMasterResume();
        Job job = entity.getJob();
        return TailoredResumeResponse.builder()
                .uuid(entity.getUuid())
                .masterResumeUuid(master != null ? master.getUuid() : null)
                .masterResumeTitle(master != null ? master.getTitle() : null)
                .jobId(job != null ? job.getId() : null)
                .jobTitle(job != null ? job.getTitle() : null)
                .companyName(job != null ? job.getCompanyName() : null)
                .jobCountry(job != null ? job.getCountry() : null)
                .title(entity.getTitle())
                .versionNumber(entity.getVersionNumber())
                .templateName(entity.getTemplateName())
                .tailoringFocus(entity.getTailoringFocus())
                .matchScore(entity.getMatchScore() != null ? entity.getMatchScore().doubleValue() : 0.0)
                .atsKeywordCoverage(entity.getAtsKeywordCoverage() != null ? entity.getAtsKeywordCoverage().doubleValue() : 0.0)
                .tailoredSummary(data != null ? data.getTailoredSummary() : null)
                .optimizedExperiences(data != null && data.getOptimizedExperiences() != null
                        ? data.getOptimizedExperiences()
                        : List.of())
                .highlightedSkills(data != null && data.getHighlightedSkills() != null
                        ? data.getHighlightedSkills()
                        : List.of())
                .missingKeywords(data != null && data.getMissingKeywords() != null
                        ? data.getMissingKeywords()
                        : List.of())
                .resumeData(data)
                .aiProvider(entity.getAiProvider())
                .aiModelUsed(entity.getAiModelUsed())
                .editedByUser(entity.isEditedByUser())
                .hasPdfExport(hasText(entity.getPdfFilePath()))
                .hasDocxExport(hasText(entity.getDocxFilePath()))
                .generatedAt(entity.getGeneratedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private TailoredResumeDataDto deserializeData(Map<String, Object> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return TailoredResumeDataDto.builder().build();
        }
        try {
            return objectMapper.convertValue(rawMap, TailoredResumeDataDto.class);
        } catch (IllegalArgumentException ex) {
            log.warn("Stored tailored resume_data_json could not be deserialized into TailoredResumeDataDto", ex);
            return TailoredResumeDataDto.builder().build();
        }
    }

    private String buildDefaultTitle(String jobTitle, String companyName, int version) {
        String versionSuffix = " (v" + version + ")";
        String safeTitle = hasText(jobTitle) ? jobTitle.trim() : "Tailored Role";
        String safeCompany = hasText(companyName) ? companyName.trim() : "Target Employer";
        String prefix = safeTitle + " · " + safeCompany;
        int maxPrefixLen = MAX_TITLE_LENGTH - versionSuffix.length();
        if (prefix.length() > maxPrefixLen) {
            prefix = prefix.substring(0, Math.max(10, maxPrefixLen)).trim();
        }
        return prefix + versionSuffix;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record EditContextLoad(
            String uuid,
            TailoredResumeDataDto existingData,
            TailoredResumeContextSnapshot snapshot
    ) {
    }

    public record ExportFilePaths(
            String pdfFilePath,
            String docxFilePath
    ) {
    }

    public record EditSaveResult(
            TailoredResumeResponse response,
            ExportFilePaths invalidatedExports
    ) {
    }

    public record ExportSnapshot(
            String uuid,
            String userUuid,
            String title,
            ResumeTemplate templateName,
            String pdfFilePath,
            String docxFilePath,
            TailoredResumeDataDto resumeData
    ) {
    }
}

