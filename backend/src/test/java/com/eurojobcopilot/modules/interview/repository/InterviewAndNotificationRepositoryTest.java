package com.eurojobcopilot.modules.interview.repository;

import com.eurojobcopilot.config.JpaAuditingConfig;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.interview.entity.InterviewPrepSession;
import com.eurojobcopilot.modules.interview.entity.InterviewQuestion;
import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.notification.entity.Notification;
import com.eurojobcopilot.modules.notification.entity.NotificationReferenceType;
import com.eurojobcopilot.modules.notification.entity.NotificationType;
import com.eurojobcopilot.modules.notification.repository.NotificationRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class InterviewAndNotificationRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private InterviewPrepSessionRepository sessionRepository;

    @Autowired
    private InterviewQuestionRepository questionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistInterviewSessionsQuestionsAndNotifications() {
        User user = userRepository.saveAndFlush(TestEntityFactory.createUser("interviewee@eurojob.eu"));
        Company company = companyRepository.saveAndFlush(
                TestEntityFactory.createCompany("Klarna Bank AB", "Sweden", true)
        );
        Job job = jobRepository.saveAndFlush(
                TestEntityFactory.createJob(company, "Senior Fullstack Engineer", "Sweden", "klarna-1")
        );

        InterviewPrepSession session = sessionRepository.saveAndFlush(InterviewPrepSession.builder()
                .user(user)
                .job(job)
                .sessionTitle("Klarna System Design & Java Round")
                .overallFeedback("Solid concurrency and Signal state explanations.")
                .score(new BigDecimal("88.00"))
                .build());

        questionRepository.saveAll(List.of(
                InterviewQuestion.builder()
                        .session(session)
                        .category(QuestionCategory.JAVA)
                        .difficulty(QuestionDifficulty.SENIOR)
                        .questionText("How do Java 21 virtual threads change connection pool sizing in Spring Boot?")
                        .idealAnswer("Virtual threads unmount during blocking I/O, making DB pools the primary concurrency bottleneck.")
                        .userAnswer("They decouple OS platform threads from JDBC calls...")
                        .aiFeedback("Clear explanation; mention pinning with synchronized blocks.")
                        .score(new BigDecimal("89.00"))
                        .build(),
                InterviewQuestion.builder()
                        .session(session)
                        .category(QuestionCategory.ANGULAR)
                        .difficulty(QuestionDifficulty.SENIOR)
                        .questionText("When should you bridge RxJS observables with Angular Signals using toSignal?")
                        .idealAnswer("Use RxJS for event streams/HTTP and Signals for synchronous template rendering.")
                        .build()
        ));

        notificationRepository.saveAll(List.of(
                Notification.builder()
                        .user(user)
                        .type(NotificationType.HIGH_MATCH_JOB)
                        .title("92% Match: Klarna Bank AB")
                        .message("New visa-sponsored Java + Angular role in Stockholm matches your profile.")
                        .referenceType(NotificationReferenceType.JOB)
                        .referenceId(job.getId())
                        .read(false)
                        .build(),
                Notification.builder()
                        .user(user)
                        .type(NotificationType.SYSTEM_ALERT)
                        .title("Welcome to EuroJob Copilot")
                        .message("Your profile is active.")
                        .read(true)
                        .readAt(Instant.now())
                        .build()
        ));

        entityManager.flush();
        entityManager.clear();

        assertThat(sessionRepository.findAllByUserIdAndJobIdOrderByCreatedAtDesc(user.getId(), job.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.getScore()).isEqualByComparingTo("88.00"));

        List<InterviewQuestion> questions = questionRepository.findAllBySessionIdOrderByIdAsc(session.getId());
        assertThat(questions).hasSize(2);
        assertThat(questions.get(0).getCategory()).isEqualTo(QuestionCategory.JAVA);
        assertThat(questions.get(1).getCategory()).isEqualTo(QuestionCategory.ANGULAR);

        assertThat(notificationRepository.countByUserIdAndReadFalse(user.getId())).isEqualTo(1L);
        assertThat(notificationRepository.findAllByUserIdAndReadFalseOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(0, 10)
        ).getContent()).singleElement().satisfies(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.HIGH_MATCH_JOB);
            assertThat(n.getReferenceId()).isEqualTo(job.getId());
        });
    }
}

