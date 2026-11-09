package com.eurojobcopilot.modules.recruiter.ai;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.CandidateEvidence;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.RecruiterEvidence;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecruiterOutreachValidatorTest {

    private final RecruiterOutreachValidator validator = new RecruiterOutreachValidator();

    @Test
    void shouldTruncateOversizedLinkedInConnectionNoteAndClearSubject() {
        RecruiterOutreachContextSnapshot snapshot = buildSnapshot(
                CommunicationChannel.LINKEDIN,
                RecruiterMessageType.LINKEDIN_CONNECTION,
                true
        );

        RawAiOutreachPayload raw = new RawAiOutreachPayload();
        raw.setSubjectLine("Should be stripped");
        raw.setMessageBody("Hi Sophie, ".concat("I would welcome connecting regarding Staff Java engineering roles in Amsterdam where my distributed systems experience matches closely. ".repeat(4)));
        raw.setKeyTalkingPoints(List.of("6.5 yrs Java 21 payments impact", "Relocation ready to Amsterdam"));

        var draft = validator.validateAndRefine(raw, snapshot);

        assertThat(draft.subjectLine()).isNull();
        assertThat(draft.messageBody().length()).isLessThanOrEqualTo(RecruiterOutreachValidator.LINKEDIN_CONNECTION_MAX_CHARS);
        assertThat(draft.warnings()).anyMatch(w -> w.contains("300-character invite limit"));
        assertThat(draft.suggestedFollowUpDays()).isEqualTo(7);
    }

    @Test
    void shouldWarnOnPlaceholdersAndFalseCitizenshipClaimsForEmailFollowUp() {
        RecruiterOutreachContextSnapshot snapshot = buildSnapshot(
                CommunicationChannel.EMAIL,
                RecruiterMessageType.FOLLOW_UP,
                true
        );

        RawAiOutreachPayload raw = new RawAiOutreachPayload();
        raw.setSubjectLine("   ");
        raw.setMessageBody("Hi [Recruiter Name], I am an EU citizen available immediately for the Staff Java role.");

        var draft = validator.validateAndRefine(raw, snapshot);

        assertThat(draft.subjectLine()).isEqualTo("Application Follow-up: Engineering Role at Adyen N.V.");
        assertThat(draft.warnings()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(draft.warnings()).anyMatch(w -> w.contains("placeholders"));
        assertThat(draft.warnings()).anyMatch(w -> w.contains("visa sponsorship"));
    }

    @Test
    void shouldRejectBlankMessageBody() {
        RecruiterOutreachContextSnapshot snapshot = buildSnapshot(
                CommunicationChannel.EMAIL,
                RecruiterMessageType.POST_INTERVIEW_THANKS,
                false
        );

        RawAiOutreachPayload raw = new RawAiOutreachPayload();
        raw.setMessageBody("   ");

        assertThatThrownBy(() -> validator.validateAndRefine(raw, snapshot))
                .isInstanceOf(AiResponseValidationException.class);
    }

    private RecruiterOutreachContextSnapshot buildSnapshot(
            CommunicationChannel channel,
            RecruiterMessageType type,
            boolean requiresSponsorship
    ) {
        return new RecruiterOutreachContextSnapshot(
                channel,
                type,
                null,
                new CandidateEvidence(
                        "Elena Weber",
                        "Senior Java Engineer",
                        new BigDecimal("6.5"),
                        "Berlin",
                        "Germany",
                        requiresSponsorship ? "NEEDS_SPONSORSHIP" : "EU_CITIZEN",
                        "WILLING_TO_RELOCATE",
                        30,
                        "Backend specialist",
                        List.of("Java 21", "Spring Boot"),
                        List.of(),
                        null,
                        requiresSponsorship,
                        true
                ),
                new RecruiterEvidence(
                        1L,
                        "Sophie van Dijk",
                        "Technical Recruiter",
                        "Adyen N.V.",
                        null,
                        true,
                        List.of("Java"),
                        "FinTech"
                ),
                null
        );
    }
}

