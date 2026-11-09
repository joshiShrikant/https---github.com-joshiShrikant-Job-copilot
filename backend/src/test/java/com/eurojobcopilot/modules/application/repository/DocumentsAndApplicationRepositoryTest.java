package com.eurojobcopilot.modules.application.repository;

import com.eurojobcopilot.config.JpaAuditingConfig;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.ApplicationStatusHistory;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetter;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import com.eurojobcopilot.modules.coverletter.repository.CoverLetterRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.Recruiter;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterInteraction;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterInteractionRepository;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterRepository;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.StorageProvider;
import com.eurojobcopilot.modules.resume.entity.TailoredResume;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.resume.repository.TailoredResumeRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.support.TestEntityFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class DocumentsAndApplicationRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private TailoredResumeRepository tailoredResumeRepository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private JobApplicationRepository applicationRepository;

    @Autowired
    private ApplicationStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private RecruiterRepository recruiterRepository;

    @Autowired
    private RecruiterInteractionRepository recruiterInteractionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistResumesCoverLettersApplicationsHistoryAndRecruiterCrm() {
        User user = userRepository.saveAndFlush(TestEntityFactory.createUser("applicant@eurojob.eu"));
        Company company = companyRepository.saveAndFlush(
                TestEntityFactory.createCompany("Mollie B.V.", "Netherlands", true)
        );
        Job job = jobRepository.saveAndFlush(
                TestEntityFactory.createJob(company, "Senior Backend Engineer", "Netherlands", "mollie-1")
        );

        Resume masterResume = resumeRepository.saveAndFlush(Resume.builder()
                .user(user)
                .resumeType(ResumeType.MASTER)
                .title("Master Java Architect CV")
                .fileName("master-cv.pdf")
                .filePath("/uploads/resumes/master-cv.pdf")
                .fileSizeBytes(245760L)
                .mimeType("application/pdf")
                .storageProvider(StorageProvider.LOCAL)
                .versionNumber(1)
                .masterApproved(true)
                .parsedContentJson(Map.of("summary", "8+ years Java & Angular", "skills", List.of("Java 21", "Angular")))
                .build());

        TailoredResume tailoredResume = tailoredResumeRepository.saveAndFlush(TailoredResume.builder()
                .masterResume(masterResume)
                .job(job)
                .user(user)
                .templateName(ResumeTemplate.MODERN)
                .resumeDataJson(Map.of("headline", "Senior Java Payments Engineer"))
                .matchScore(new BigDecimal("91.50"))
                .atsKeywordCoverage(new BigDecimal("87.00"))
                .build());

        CoverLetter coverLetter = coverLetterRepository.saveAndFlush(CoverLetter.builder()
                .user(user)
                .job(job)
                .tailoredResume(tailoredResume)
                .title("Mollie Backend Cover Letter")
                .content("Dear Hiring Team at Mollie, ... ".repeat(200))
                .tone(CoverLetterTone.PROFESSIONAL)
                .versionNumber(1)
                .build());

        JobApplication application = applicationRepository.saveAndFlush(JobApplication.builder()
                .user(user)
                .job(job)
                .status(ApplicationStatus.APPLIED)
                .appliedDate(Instant.now())
                .tailoredResume(tailoredResume)
                .coverLetter(coverLetter)
                .nextFollowUpDate(LocalDate.now().plusDays(7))
                .notes("Applied directly on company careers page.")
                .build());

        statusHistoryRepository.saveAll(List.of(
                ApplicationStatusHistory.builder()
                        .application(application)
                        .previousStatus(null)
                        .newStatus(ApplicationStatus.INTERESTED)
                        .transitionNotes("Saved to pipeline")
                        .transitionedAt(Instant.now().minusSeconds(3600))
                        .build(),
                ApplicationStatusHistory.builder()
                        .application(application)
                        .previousStatus(ApplicationStatus.INTERESTED)
                        .newStatus(ApplicationStatus.APPLIED)
                        .transitionNotes("Submitted tailored resume & cover letter")
                        .transitionedAt(Instant.now())
                        .build()
        ));

        Recruiter recruiter = recruiterRepository.saveAndFlush(Recruiter.builder()
                .user(user)
                .company(company)
                .fullName("Sophie van Dijk")
                .companyName("Mollie B.V.")
                .email("sophie@mollie.example")
                .linkedinUrl("https://linkedin.com/in/sophievandijk")
                .build());

        recruiterInteractionRepository.saveAndFlush(RecruiterInteraction.builder()
                .recruiter(recruiter)
                .application(application)
                .channel(CommunicationChannel.LINKEDIN)
                .messageType(RecruiterMessageType.INITIAL_OUTREACH)
                .messageBody("Hi Sophie, I just applied for the Senior Backend Engineer role...")
                .sentAt(Instant.now())
                .followUpReminderDate(LocalDate.now().plusDays(5))
                .build());

        entityManager.flush();
        entityManager.clear();

        Resume foundMaster = resumeRepository.findFirstByUserIdAndMasterApprovedTrueOrderByVersionNumberDesc(user.getId())
                .orElseThrow();
        assertThat(foundMaster.getUuid()).hasSize(36);
        assertThat(foundMaster.getParsedContentJson()).containsKey("summary");

        TailoredResume foundTailored = tailoredResumeRepository.findByUuidAndUserId(tailoredResume.getUuid(), user.getId())
                .orElseThrow();
        assertThat(foundTailored.getResumeDataJson()).containsEntry("headline", "Senior Java Payments Engineer");

        assertThat(coverLetterRepository.findByUuid(coverLetter.getUuid())).isPresent();

        JobApplication foundApp = applicationRepository.findByUserIdAndJobId(user.getId(), job.getId()).orElseThrow();
        assertThat(foundApp.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(foundApp.getVersion()).isZero();
        assertThat(applicationRepository.countByUserIdAndStatus(user.getId(), ApplicationStatus.APPLIED)).isEqualTo(1L);

        List<ApplicationStatusHistory> history = statusHistoryRepository.findAllByApplicationIdOrderByTransitionedAtDesc(foundApp.getId());
        assertThat(history).hasSize(2);
        assertThat(history.getFirst().getNewStatus()).isEqualTo(ApplicationStatus.APPLIED);

        assertThat(recruiterRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 10)).getContent())
                .hasSize(1);
        assertThat(recruiterInteractionRepository.findAllByApplicationIdOrderByCreatedAtDesc(foundApp.getId()))
                .singleElement()
                .satisfies(i -> assertThat(i.getMessageType()).isEqualTo(RecruiterMessageType.INITIAL_OUTREACH));
    }
}

