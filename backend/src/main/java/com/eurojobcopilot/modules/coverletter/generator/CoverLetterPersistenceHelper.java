package com.eurojobcopilot.modules.coverletter.generator;

import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterMetadataDto;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterResponse;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterSummaryDto;
import com.eurojobcopilot.modules.coverletter.dto.GenerateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.dto.UpdateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetter;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import com.eurojobcopilot.modules.coverletter.exception.CoverLetterNotFoundException;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.CandidateHeaderEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.MasterResumeEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.PreferenceEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.TailoredResumeEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.WorkExperienceEvidence;
import com.eurojobcopilot.modules.coverletter.repository.CoverLetterRepository;
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
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeDataDto;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.TailoredResume;
import com.eurojobcopilot.modules.resume.exception.ResumeNotFoundException;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.resume.repository.TailoredResumeRepository;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates short transactional reads and writes for AI cover letter drafts while keeping
 * external LLM execution and document extraction cleanly outside database locks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoverLetterPersistenceHelper {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_PERSONAL_NOTES_LENGTH = 1000;

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
    private final CoverLetterRepository coverLetterRepository;
    private final DeterministicJobMatchingEngine deterministicEngine;
    private final JobNormalizer jobNormalizer;
    private final CanonicalHashGenerator canonicalHashGenerator;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CoverLetterContextSnapshot loadGenerationSnapshot(Long userId, GenerateCoverLetterRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        Job job = jobRepository.findByIdWithCompany(request.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + request.getJobId()));

        TailoredResume tailoredResume = resolveTailoredResume(userId, job.getId(), request.getTailoredResumeUuid());
        Resume masterResume = resolveMasterResume(userId, tailoredResume);
        CoverLetterTone tone = request.getTone() != null ? request.getTone() : CoverLetterTone.PROFESSIONAL;
        String personalNotes = normalizePersonalNotes(request.getPersonalNotes());

        CoverLetterResponse cachedDraft = null;
        if (Boolean.FALSE.equals(request.getForceRefresh())) {
            Long expectedTailoredId = tailoredResume != null ? tailoredResume.getId() : null;
            cachedDraft = coverLetterRepository.findAllWithDetailsByUserIdAndJobIdOrderByVersionDesc(userId, job.getId())
                    .stream()
                    .filter(existing -> existing.getTone() == tone)
                    .filter(existing -> Objects.equals(
                            existing.getTailoredResume() != null ? existing.getTailoredResume().getId() : null,
                            expectedTailoredId))
                    .filter(existing -> Objects.equals(
                            normalizePersonalNotes(existing.getPersonalNotes()),
                            personalNotes))
                    .findFirst()
                    .map(existing -> toResponse(existing, deserializeMetadata(existing.getMetadataJson())))
                    .orElse(null);
        }

        return assembleSnapshot(user, job, tailoredResume, masterResume, tone, personalNotes, cachedDraft);
    }

    @Transactional(readOnly = true)
    public EditContextLoad loadEditContextSnapshot(Long userId, String uuid) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        CoverLetter entity = coverLetterRepository.findWithDetailsByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new CoverLetterNotFoundException("Cover letter not found"));

        TailoredResume tailoredResume = entity.getTailoredResume();
        Resume masterResume = resolveMasterResume(userId, tailoredResume);
        CoverLetterMetadataDto existingMetadata = deserializeMetadata(entity.getMetadataJson());
        CoverLetterContextSnapshot snapshot = assembleSnapshot(
                user,
                entity.getJob(),
                tailoredResume,
                masterResume,
                entity.getTone(),
                entity.getPersonalNotes(),
                null
        );
        return new EditContextLoad(entity, existingMetadata, snapshot);
    }

    @Transactional
    public CoverLetterResponse saveGeneratedDraft(
            CoverLetterContextSnapshot snapshot,
            ValidatedCoverLetter validated,
            AiStructuredResponse<RawAiCoverLetterPayload> aiResponse
    ) {
        User user = userRepository.findById(snapshot.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        Job job = jobRepository.findByIdWithCompany(snapshot.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + snapshot.jobId()));

        TailoredResume tailoredResume = null;
        if (snapshot.tailoredResume() != null && snapshot.tailoredResume().id() != null) {
            tailoredResume = tailoredResumeRepository.findById(snapshot.tailoredResume().id()).orElse(null);
        }

        Integer maxVer = coverLetterRepository.findMaxVersionNumberByUserIdAndJobId(user.getId(), job.getId());
        int nextVersion = (maxVer == null ? 0 : maxVer) + 1;

        CoverLetterTone tone = snapshot.tone() != null ? snapshot.tone() : CoverLetterTone.PROFESSIONAL;
        String title = buildDefaultTitle(job.getTitle(), job.getCompanyName(), nextVersion);
        Map<String, Object> serializedMetadata = objectMapper.convertValue(validated.metadata(), MAP_TYPE_REF);

        CoverLetter entity = CoverLetter.builder()
                .uuid(UUID.randomUUID().toString())
                .user(user)
                .job(job)
                .tailoredResume(tailoredResume)
                .title(title)
                .content(validated.content())
                .tone(tone)
                .versionNumber(nextVersion)
                .personalNotes(snapshot.personalNotes())
                .metadataJson(serializedMetadata != null ? serializedMetadata : new LinkedHashMap<>())
                .wordCount(validated.wordCount() != null ? validated.wordCount() : 0)
                .aiProvider(aiResponse != null ? aiResponse.provider() : null)
                .aiModelUsed(aiResponse != null ? aiResponse.model() : null)
                .generatedAt(Instant.now())
                .editedByUser(false)
                .build();

        CoverLetter saved = coverLetterRepository.saveAndFlush(entity);
        log.info("Generated cover letter uuid={} version={} tone={} jobId={} userId={} words={}",
                saved.getUuid(), saved.getVersionNumber(), saved.getTone(), job.getId(), user.getId(), saved.getWordCount());

        return toResponse(saved, validated.metadata());
    }

    @Transactional
    public CoverLetterResponse saveEditedDraft(
            Long userId,
            String uuid,
            UpdateCoverLetterRequest request,
            ValidatedCoverLetter revalidated
    ) {
        CoverLetter entity = coverLetterRepository.findWithDetailsByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new CoverLetterNotFoundException("Cover letter not found"));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            String trimmed = request.getTitle().trim();
            entity.setTitle(trimmed.length() <= MAX_TITLE_LENGTH
                    ? trimmed
                    : trimmed.substring(0, MAX_TITLE_LENGTH).trim());
        }
        if (request.getTone() != null) {
            entity.setTone(request.getTone());
        }
        if (request.getPersonalNotes() != null) {
            entity.setPersonalNotes(normalizePersonalNotes(request.getPersonalNotes()));
        }

        entity.setContent(revalidated.content());
        entity.setWordCount(revalidated.wordCount() != null ? revalidated.wordCount() : 0);
        Map<String, Object> serializedMetadata = objectMapper.convertValue(revalidated.metadata(), MAP_TYPE_REF);
        entity.setMetadataJson(serializedMetadata != null ? serializedMetadata : new LinkedHashMap<>());
        entity.setEditedByUser(true);

        CoverLetter saved = coverLetterRepository.saveAndFlush(entity);
        log.info("Updated cover letter uuid={} editedByUser=true words={}", saved.getUuid(), saved.getWordCount());
        return toResponse(saved, revalidated.metadata());
    }

    @Transactional(readOnly = true)
    public List<CoverLetterSummaryDto> listCoverLetters(Long userId, Long jobId) {
        List<CoverLetter> rows = jobId != null
                ? coverLetterRepository.findAllWithDetailsByUserIdAndJobIdOrderByVersionDesc(userId, jobId)
                : coverLetterRepository.findAllWithDetailsByUserIdOrderByCreatedAtDesc(userId);
        return rows.stream().map(this::toSummaryDto).toList();
    }

    @Transactional(readOnly = true)
    public CoverLetterResponse getCoverLetter(Long userId, String uuid) {
        CoverLetter entity = coverLetterRepository.findWithDetailsByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new CoverLetterNotFoundException("Cover letter not found"));
        return toResponse(entity, deserializeMetadata(entity.getMetadataJson()));
    }

    @Transactional(readOnly = true)
    public ExportSnapshot loadExportSnapshot(Long userId, String uuid) {
        CoverLetter entity = coverLetterRepository.findWithDetailsByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new CoverLetterNotFoundException("Cover letter not found"));
        Job job = entity.getJob();
        User user = entity.getUser();
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);

        CoverLetterMetadataDto metadata = deserializeMetadata(entity.getMetadataJson());
        String fullName = firstNonBlank(
                metadata.getCandidateFullName(),
                ((user.getFirstName() != null ? user.getFirstName().trim() : "")
                        + " " + (user.getLastName() != null ? user.getLastName().trim() : "")).trim()
        );
        String email = firstNonBlank(metadata.getCandidateEmail(), user.getEmail());
        String location = firstNonBlank(
                metadata.getCandidateLocation(),
                profile != null ? joinLocation(profile.getCurrentCity(), profile.getCurrentCountry()) : null
        );
        String headline = firstNonBlank(
                metadata.getCandidateHeadline(),
                profile != null ? profile.getCurrentDesignation() : null
        );

        return new ExportSnapshot(
                entity.getUuid(),
                entity.getTitle(),
                entity.getContent(),
                entity.getTone(),
                job != null ? job.getTitle() : null,
                job != null ? job.getCompanyName() : null,
                job != null ? job.getCountry() : null,
                job != null ? job.getLocation() : null,
                fullName,
                email,
                location,
                headline,
                metadata.getWorkAuthorizationSummary(),
                metadata.getSalutation(),
                metadata.getSignOff(),
                entity.getGeneratedAt() != null ? entity.getGeneratedAt() : entity.getCreatedAt()
        );
    }

    @Transactional
    public void deleteCoverLetter(Long userId, String uuid) {
        CoverLetter entity = coverLetterRepository.findByUuidAndUserId(uuid.trim(), userId)
                .orElseThrow(() -> new CoverLetterNotFoundException("Cover letter not found"));
        coverLetterRepository.clearApplicationReferences(entity.getId());
        coverLetterRepository.delete(entity);
        coverLetterRepository.flush();
        log.info("Deleted cover letter uuid={} userId={}", uuid, userId);
    }

    private CoverLetterContextSnapshot assembleSnapshot(
            User user,
            Job job,
            TailoredResume tailoredResume,
            Resume masterResume,
            CoverLetterTone tone,
            String personalNotes,
            CoverLetterResponse cachedDraft
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

        return new CoverLetterContextSnapshot(
                userId,
                job.getId(),
                tone,
                personalNotes,
                toHeaderEvidence(user, profile),
                toPreferenceEvidence(preference),
                skills.stream().map(this::toSkillEvidence).toList(),
                workExperiences.stream().map(this::toWorkExperienceEvidence).toList(),
                educations.stream().map(this::toEducationEvidence).toList(),
                masterResume != null ? toMasterResumeEvidence(masterResume) : null,
                tailoredResume != null ? toTailoredResumeEvidence(tailoredResume) : null,
                toJobEvidence(job, resolvedCompany),
                toJobAiAnalysisEvidence(jobAiAnalysis),
                baselineMatchScore,
                cachedDraft
        );
    }

    private TailoredResume resolveTailoredResume(Long userId, Long jobId, String requestedUuid) {
        if (requestedUuid != null && !requestedUuid.isBlank()) {
            return tailoredResumeRepository.findWithDetailsByUuidAndUserId(requestedUuid.trim(), userId)
                    .orElseThrow(() -> new ResumeNotFoundException("Tailored resume not found"));
        }
        return tailoredResumeRepository.findAllWithDetailsByUserIdAndJobIdOrderByVersionDesc(userId, jobId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Resume resolveMasterResume(Long userId, TailoredResume tailoredResume) {
        if (tailoredResume != null && tailoredResume.getMasterResume() != null) {
            return tailoredResume.getMasterResume();
        }
        return resumeRepository.findFirstByUserIdAndMasterApprovedTrueOrderByVersionNumberDesc(userId)
                .filter(r -> r.getResumeType() == ResumeType.MASTER)
                .or(() -> resumeRepository.findAllByUserIdOrderByVersionNumberDescCreatedAtDesc(userId)
                        .stream()
                        .filter(r -> r.getResumeType() == ResumeType.MASTER)
                        .findFirst())
                .orElse(null);
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

    private CandidateHeaderEvidence toHeaderEvidence(User user, UserProfile profile) {
        String fullName = ((user.getFirstName() != null ? user.getFirstName().trim() : "")
                + " " + (user.getLastName() != null ? user.getLastName().trim() : "")).trim();
        return new CandidateHeaderEvidence(
                fullName.isBlank() ? null : fullName,
                user.getEmail(),
                profile != null ? profile.getCurrentDesignation() : null,
                profile != null ? profile.getYearsOfExperience() : null,
                profile != null ? profile.getCurrentCountry() : null,
                profile != null ? profile.getCurrentCity() : null,
                profile != null ? profile.getNoticePeriodDays() : null,
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

    private PreferenceEvidence toPreferenceEvidence(UserJobPreference preference) {
        if (preference == null) {
            return null;
        }
        return new PreferenceEvidence(
                preference.getPreferredCountries(),
                preference.getPreferredCities(),
                preference.isRequiresSponsorship(),
                preference.isRequiresRelocation(),
                preference.getWorkArrangement() != null ? preference.getWorkArrangement().name() : null
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

    private TailoredResumeEvidence toTailoredResumeEvidence(TailoredResume tr) {
        TailoredResumeDataDto dataDto = null;
        if (tr.getResumeDataJson() != null && !tr.getResumeDataJson().isEmpty()) {
            try {
                dataDto = objectMapper.convertValue(tr.getResumeDataJson(), TailoredResumeDataDto.class);
            } catch (IllegalArgumentException ex) {
                log.warn("Could not deserialize resumeDataJson for tailored resume uuid={}", tr.getUuid(), ex);
            }
        }
        return new TailoredResumeEvidence(
                tr.getId(),
                tr.getUuid(),
                tr.getTitle(),
                tr.getTemplateName(),
                tr.getTailoringFocus(),
                tr.getAtsKeywordCoverage(),
                dataDto
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
                resolvedCompany != null ? resolvedCompany.getDescription() : null,
                resolvedCompany != null ? resolvedCompany.getCompanySize() : null,
                resolvedCompany != null && resolvedCompany.getTechStack() != null
                        ? List.copyOf(resolvedCompany.getTechStack())
                        : List.of(),
                resolvedCompany != null && resolvedCompany.isVerifiedSponsor(),
                resolvedCompany != null && resolvedCompany.getSponsorConfidenceLevel() != null
                        ? resolvedCompany.getSponsorConfidenceLevel().name()
                        : null,
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
                ai.getSponsorshipOffered(),
                ai.getVisaSponsorshipAnalysis(),
                ai.getRelocationAssistanceOffered(),
                ai.getRelocationAnalysis(),
                ai.getSummary()
        );
    }

    private CoverLetterSummaryDto toSummaryDto(CoverLetter entity) {
        Job job = entity.getJob();
        TailoredResume tr = entity.getTailoredResume();
        return CoverLetterSummaryDto.builder()
                .uuid(entity.getUuid())
                .jobId(job != null ? job.getId() : null)
                .jobTitle(job != null ? job.getTitle() : null)
                .companyName(job != null ? job.getCompanyName() : null)
                .jobCountry(job != null ? job.getCountry() : null)
                .tailoredResumeUuid(tr != null ? tr.getUuid() : null)
                .tailoredResumeTitle(tr != null ? tr.getTitle() : null)
                .title(entity.getTitle())
                .tone(entity.getTone())
                .versionNumber(entity.getVersionNumber())
                .wordCount(entity.getWordCount() != null ? entity.getWordCount() : 0)
                .aiProvider(entity.getAiProvider())
                .aiModelUsed(entity.getAiModelUsed())
                .editedByUser(entity.isEditedByUser())
                .generatedAt(entity.getGeneratedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private CoverLetterResponse toResponse(CoverLetter entity, CoverLetterMetadataDto metadata) {
        Job job = entity.getJob();
        TailoredResume tr = entity.getTailoredResume();
        return CoverLetterResponse.builder()
                .uuid(entity.getUuid())
                .jobId(job != null ? job.getId() : null)
                .jobTitle(job != null ? job.getTitle() : null)
                .companyName(job != null ? job.getCompanyName() : null)
                .jobCountry(job != null ? job.getCountry() : null)
                .tailoredResumeUuid(tr != null ? tr.getUuid() : null)
                .tailoredResumeTitle(tr != null ? tr.getTitle() : null)
                .title(entity.getTitle())
                .content(entity.getContent())
                .tone(entity.getTone())
                .versionNumber(entity.getVersionNumber())
                .personalNotes(entity.getPersonalNotes())
                .wordCount(entity.getWordCount() != null ? entity.getWordCount() : 0)
                .metadata(metadata)
                .aiProvider(entity.getAiProvider())
                .aiModelUsed(entity.getAiModelUsed())
                .editedByUser(entity.isEditedByUser())
                .generatedAt(entity.getGeneratedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private CoverLetterMetadataDto deserializeMetadata(Map<String, Object> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return CoverLetterMetadataDto.builder().build();
        }
        try {
            return objectMapper.convertValue(rawMap, CoverLetterMetadataDto.class);
        } catch (IllegalArgumentException ex) {
            log.warn("Stored cover_letter metadata_json could not be deserialized", ex);
            return CoverLetterMetadataDto.builder().build();
        }
    }

    private String normalizePersonalNotes(String rawNotes) {
        if (rawNotes == null || rawNotes.isBlank()) {
            return null;
        }
        String trimmed = rawNotes.trim();
        return trimmed.length() <= MAX_PERSONAL_NOTES_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_PERSONAL_NOTES_LENGTH).trim();
    }

    private String buildDefaultTitle(String jobTitle, String companyName, int version) {
        String versionSuffix = " (v" + version + ")";
        String safeTitle = hasText(jobTitle) ? jobTitle.trim() : "Target Role";
        String safeCompany = hasText(companyName) ? companyName.trim() : "Target Employer";
        String prefix = "Cover Letter · " + safeTitle + " · " + safeCompany;
        int maxPrefixLen = MAX_TITLE_LENGTH - versionSuffix.length();
        if (prefix.length() > maxPrefixLen) {
            prefix = prefix.substring(0, Math.max(10, maxPrefixLen)).trim();
        }
        return prefix + versionSuffix;
    }

    private String joinLocation(String city, String country) {
        if (hasText(city) && hasText(country)) {
            return city.trim() + ", " + country.trim();
        }
        if (hasText(city)) {
            return city.trim();
        }
        return hasText(country) ? country.trim() : null;
    }

    private String firstNonBlank(String primary, String secondary) {
        if (hasText(primary)) {
            return primary.trim();
        }
        return hasText(secondary) ? secondary.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record EditContextLoad(
            CoverLetter existingEntity,
            CoverLetterMetadataDto existingMetadata,
            CoverLetterContextSnapshot snapshot
    ) {
    }

    public record ExportSnapshot(
            String uuid,
            String title,
            String content,
            CoverLetterTone tone,
            String jobTitle,
            String companyName,
            String jobCountry,
            String jobLocation,
            String candidateFullName,
            String candidateEmail,
            String candidateLocation,
            String candidateHeadline,
            String workAuthorizationSummary,
            String salutation,
            String signOff,
            Instant dateInstant
    ) {
    }
}

