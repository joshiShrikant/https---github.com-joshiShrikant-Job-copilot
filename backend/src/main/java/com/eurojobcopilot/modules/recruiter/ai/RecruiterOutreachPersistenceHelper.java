package com.eurojobcopilot.modules.recruiter.ai;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.CandidateEvidence;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.ExperienceItem;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.JobAndApplicationEvidence;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.RecruiterEvidence;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachValidator.ValidatedOutreachDraft;
import com.eurojobcopilot.modules.recruiter.dto.GenerateOutreachRequest;
import com.eurojobcopilot.modules.recruiter.dto.RecruiterInteractionDto;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.Recruiter;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterInteraction;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageStatus;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterInteractionRepository;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterRepository;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecruiterOutreachPersistenceHelper {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserWorkExperienceRepository userWorkExperienceRepository;
    private final UserJobPreferenceRepository userJobPreferenceRepository;
    private final ResumeRepository resumeRepository;
    private final RecruiterRepository recruiterRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final JobAiAnalysisRepository jobAiAnalysisRepository;
    private final RecruiterInteractionRepository recruiterInteractionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public RecruiterOutreachContextSnapshot loadContextSnapshot(
            Long userId,
            Long recruiterId,
            GenerateOutreachRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        Recruiter recruiter = recruiterRepository.findWithCompanyByIdAndUserId(recruiterId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter contact not found"));

        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        List<UserSkill> skills = userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(userId);
        List<UserWorkExperience> experiences = userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(userId);
        UserJobPreference pref = userJobPreferenceRepository.findByUserId(userId).orElse(null);
        Resume masterResume = resumeRepository
                .findFirstByUserIdAndMasterApprovedTrueOrderByVersionNumberDesc(userId)
                .orElse(null);

        ParsedResumeJsonDto parsedMaster = null;
        if (masterResume != null && masterResume.getParsedContentJson() != null) {
            try {
                parsedMaster = objectMapper.convertValue(masterResume.getParsedContentJson(), ParsedResumeJsonDto.class);
            } catch (IllegalArgumentException ignored) {
                parsedMaster = null;
            }
        }

        boolean hasEvidence = profile != null
                || !skills.isEmpty()
                || !experiences.isEmpty()
                || (parsedMaster != null
                && ((parsedMaster.getSummary() != null && !parsedMaster.getSummary().isBlank())
                || (parsedMaster.getSkills() != null && !parsedMaster.getSkills().isEmpty())
                || (parsedMaster.getExperiences() != null && !parsedMaster.getExperiences().isEmpty())));

        if (!hasEvidence) {
            throw new BusinessValidationException(
                    "Add candidate profile details, skills, work history, or an approved Master CV before generating recruiter outreach."
            );
        }

        String fullName = joinName(user.getFirstName(), user.getLastName(), user.getEmail());

        List<String> topSkills = skills.stream()
                .map(UserSkill::getSkillName)
                .filter(s -> s != null && !s.isBlank())
                .limit(15)
                .toList();
        if (topSkills.isEmpty() && parsedMaster != null) {
            topSkills = RecruiterOutreachContextSnapshot.extractParsedSkills(parsedMaster);
        }

        List<ExperienceItem> expItems = experiences.stream()
                .limit(4)
                .map(e -> new ExperienceItem(e.getDesignation(), e.getCompanyName(), e.getDescription()))
                .toList();

        CandidateEvidence candidate = new CandidateEvidence(
                fullName,
                profile != null ? profile.getCurrentDesignation() : null,
                profile != null ? profile.getYearsOfExperience() : null,
                profile != null ? profile.getCurrentCity() : null,
                profile != null ? profile.getCurrentCountry() : null,
                profile != null && profile.getWorkAuthorizationStatus() != null
                        ? profile.getWorkAuthorizationStatus().name()
                        : null,
                profile != null && profile.getRelocationPreference() != null
                        ? profile.getRelocationPreference().name()
                        : null,
                profile != null ? profile.getNoticePeriodDays() : null,
                profile != null ? profile.getBio() : null,
                topSkills,
                expItems,
                parsedMaster != null ? parsedMaster.getSummary() : null,
                pref != null && pref.isRequiresSponsorship(),
                pref != null && pref.isRequiresRelocation()
        );

        Company company = recruiter.getCompany();
        RecruiterEvidence recruiterEvidence = new RecruiterEvidence(
                recruiter.getId(),
                recruiter.getFullName(),
                recruiter.getRoleTitle(),
                recruiter.getCompanyName() != null && !recruiter.getCompanyName().isBlank()
                        ? recruiter.getCompanyName()
                        : (company != null ? company.getName() : null),
                recruiter.getNotes(),
                company != null && company.isVerifiedSponsor(),
                company != null && company.getTechStack() != null ? List.copyOf(company.getTechStack()) : List.of(),
                company != null ? company.getIndustry() : null
        );

        JobAndApplicationEvidence jobContext = resolveJobAndApplicationContext(userId, request);

        CommunicationChannel channel = request.getChannel() != null
                ? request.getChannel()
                : CommunicationChannel.LINKEDIN;
        RecruiterMessageType messageType = request.getMessageType() != null
                ? request.getMessageType()
                : RecruiterMessageType.INITIAL_OUTREACH;

        return new RecruiterOutreachContextSnapshot(
                channel,
                messageType,
                request.getPersonalContext(),
                candidate,
                recruiterEvidence,
                jobContext
        );
    }

    @Transactional
    public RecruiterInteractionDto saveAiOutreachDraft(
            Long userId,
            Long recruiterId,
            RecruiterOutreachContextSnapshot snapshot,
            ValidatedOutreachDraft draft,
            AiStructuredResponse<RawAiOutreachPayload> aiResponse,
            LocalDate followUpReminderDate
    ) {
        Recruiter recruiter = recruiterRepository.findWithCompanyByIdAndUserId(recruiterId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter contact not found"));

        JobApplication linkedApp = null;
        if (snapshot.jobContext() != null && snapshot.jobContext().applicationUuid() != null) {
            linkedApp = jobApplicationRepository
                    .findWithDetailsByUuidAndUserId(snapshot.jobContext().applicationUuid(), userId)
                    .orElse(null);
        }

        RecruiterInteraction interaction = RecruiterInteraction.builder()
                .recruiter(recruiter)
                .application(linkedApp)
                .channel(snapshot.channel())
                .messageType(snapshot.messageType())
                .status(RecruiterMessageStatus.DRAFT)
                .subjectLine(draft.subjectLine())
                .messageBody(draft.messageBody())
                .sentAt(null)
                .approvedAt(null)
                .followUpReminderDate(followUpReminderDate)
                .reminderCompleted(false)
                .aiGenerated(true)
                .aiProvider(aiResponse.provider())
                .aiModelUsed(aiResponse.model())
                .editedByUser(false)
                .build();

        interaction = recruiterInteractionRepository.saveAndFlush(interaction);
        return toDto(interaction);
    }

    public RecruiterInteractionDto toDto(RecruiterInteraction i) {
        LocalDate today = LocalDate.now();
        boolean overdue = i.getFollowUpReminderDate() != null
                && !i.isReminderCompleted()
                && i.getFollowUpReminderDate().isBefore(today);

        JobApplication app = i.getApplication();
        Job job = app != null ? app.getJob() : null;
        Recruiter r = i.getRecruiter();

        return RecruiterInteractionDto.builder()
                .id(i.getId())
                .recruiterId(r.getId())
                .recruiterName(r.getFullName())
                .companyName(r.getCompanyName())
                .applicationUuid(app != null ? app.getUuid() : null)
                .jobId(job != null ? job.getId() : null)
                .jobTitle(job != null ? job.getTitle() : null)
                .channel(i.getChannel())
                .messageType(i.getMessageType())
                .status(i.getStatus())
                .subjectLine(i.getSubjectLine())
                .messageBody(i.getMessageBody())
                .sentAt(i.getSentAt())
                .approvedAt(i.getApprovedAt())
                .followUpReminderDate(i.getFollowUpReminderDate())
                .reminderCompleted(i.isReminderCompleted())
                .overdueReminder(overdue)
                .aiGenerated(i.isAiGenerated())
                .aiProvider(i.getAiProvider())
                .aiModelUsed(i.getAiModelUsed())
                .editedByUser(i.isEditedByUser())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }

    private JobAndApplicationEvidence resolveJobAndApplicationContext(Long userId, GenerateOutreachRequest request) {
        JobApplication app = null;
        Job job = null;

        if (request.getApplicationUuid() != null && !request.getApplicationUuid().isBlank()) {
            app = jobApplicationRepository.findWithDetailsByUuidAndUserId(request.getApplicationUuid().trim(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Linked application not found"));
            job = app.getJob();
        } else if (request.getJobId() != null) {
            job = jobRepository.findByIdWithCompany(request.getJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target job not found with id: " + request.getJobId()));
            app = jobApplicationRepository.findWithDetailsByUserIdAndJobId(userId, job.getId()).orElse(null);
        }

        if (job == null) {
            return null;
        }

        JobAiAnalysis aiAnalysis = jobAiAnalysisRepository.findByJobId(job.getId()).orElse(null);
        return new JobAndApplicationEvidence(
                job.getId(),
                job.getTitle(),
                job.getCompanyName(),
                job.getLocation(),
                job.getCountry(),
                app != null ? app.getUuid() : null,
                app != null ? app.getStatus() : null,
                app != null ? app.getAppliedDate() : null,
                app != null ? app.getNextInterviewDate() : null,
                job.getSkills() != null ? List.copyOf(job.getSkills()) : List.of(),
                job.getSponsorStatus() != null ? job.getSponsorStatus().name() : null,
                aiAnalysis != null ? aiAnalysis.getSummary() : job.getDescriptionSnippet()
        );
    }

    private String joinName(String first, String last, String fallbackEmail) {
        String combined = ((first != null ? first.trim() : "") + " " + (last != null ? last.trim() : "")).trim();
        return combined.isEmpty() ? fallbackEmail : combined;
    }
}

