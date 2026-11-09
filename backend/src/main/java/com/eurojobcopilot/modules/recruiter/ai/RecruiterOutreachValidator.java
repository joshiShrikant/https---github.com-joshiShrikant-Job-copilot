package com.eurojobcopilot.modules.recruiter.ai;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.CandidateEvidence;
import com.eurojobcopilot.modules.recruiter.ai.RecruiterOutreachContextSnapshot.JobAndApplicationEvidence;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class RecruiterOutreachValidator {

    public static final int LINKEDIN_CONNECTION_MAX_CHARS = 300;

    private static final Pattern UNREPLACED_PLACEHOLDER_PATTERN =
            Pattern.compile("\\[(Your Name|Recruiter Name|Company|Role|Job Title|Date|Insert[^\\]]*)\\]",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern FALSE_CITIZENSHIP_PATTERN = Pattern.compile(
            "\\b(eu citizen|eea citizen|permanent resident|no visa needed|do not require sponsorship|without needing sponsorship)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public ValidatedOutreachDraft validateAndRefine(
            RawAiOutreachPayload raw,
            RecruiterOutreachContextSnapshot snapshot
    ) {
        if (raw == null || raw.getMessageBody() == null || raw.getMessageBody().isBlank()) {
            throw new AiResponseValidationException("AI outreach generator returned an empty message body.");
        }

        List<String> warnings = new ArrayList<>();
        if (raw.getWarnings() != null) {
            for (String w : raw.getWarnings()) {
                if (hasText(w)) {
                    warnings.add(w.trim());
                }
            }
        }

        String body = raw.getMessageBody().trim();
        String subject = raw.getSubjectLine() != null && !raw.getSubjectLine().isBlank()
                ? raw.getSubjectLine().trim()
                : null;

        RecruiterMessageType messageType = snapshot.messageType() != null
                ? snapshot.messageType()
                : RecruiterMessageType.INITIAL_OUTREACH;
        CommunicationChannel channel = snapshot.channel() != null
                ? snapshot.channel()
                : CommunicationChannel.LINKEDIN;

        if (messageType == RecruiterMessageType.LINKEDIN_CONNECTION) {
            subject = null;
            if (body.length() > LINKEDIN_CONNECTION_MAX_CHARS) {
                body = trimToWordBoundary(body, LINKEDIN_CONNECTION_MAX_CHARS - 3) + "...";
                warnings.add("LinkedIn connection note was trimmed to fit LinkedIn's 300-character invite limit.");
            }
        } else if (channel == CommunicationChannel.EMAIL && subject == null) {
            subject = buildFallbackEmailSubject(messageType, snapshot.jobContext(), snapshot.recruiter().companyName());
        }

        if (UNREPLACED_PLACEHOLDER_PATTERN.matcher(body).find()
                || (subject != null && UNREPLACED_PLACEHOLDER_PATTERN.matcher(subject).find())) {
            warnings.add("Review draft carefully: one or more template placeholders remain for personalization.");
        }

        CandidateEvidence candidate = snapshot.candidate();
        if (candidate != null && requiresSponsorship(candidate) && FALSE_CITIZENSHIP_PATTERN.matcher(body).find()) {
            warnings.add("Verify work-authorization wording before sending: your profile indicates visa sponsorship is required.");
        }

        List<String> talkingPoints = raw.getKeyTalkingPoints() == null
                ? List.of()
                : raw.getKeyTalkingPoints().stream()
                .filter(RecruiterOutreachValidator::hasText)
                .map(String::trim)
                .distinct()
                .limit(4)
                .toList();

        int followUpDays = resolveSuggestedDays(raw.getSuggestedFollowUpDays(), messageType);

        return new ValidatedOutreachDraft(
                subject,
                body,
                talkingPoints,
                followUpDays,
                warnings.stream().distinct().toList()
        );
    }

    private String buildFallbackEmailSubject(
            RecruiterMessageType type,
            JobAndApplicationEvidence job,
            String companyName
    ) {
        String role = (job != null && hasText(job.jobTitle())) ? job.jobTitle().trim() : "Engineering Role";
        String employer = (job != null && hasText(job.companyName()))
                ? job.companyName().trim()
                : (hasText(companyName) ? companyName.trim() : "your team");

        return switch (type) {
            case FOLLOW_UP -> "Application Follow-up: " + role + " at " + employer;
            case POST_INTERVIEW_THANKS -> "Thank you — " + role + " interview with " + employer;
            case OFFER_NEGOTIATION -> "Follow-up on " + role + " offer — " + employer;
            default -> "Introduction: " + role + " opportunity at " + employer;
        };
    }

    private int resolveSuggestedDays(Integer rawDays, RecruiterMessageType type) {
        if (rawDays != null && rawDays >= 1 && rawDays <= 30) {
            return rawDays;
        }
        return switch (type) {
            case POST_INTERVIEW_THANKS -> 3;
            case FOLLOW_UP -> 5;
            case OFFER_NEGOTIATION -> 2;
            default -> 7;
        };
    }

    private boolean requiresSponsorship(CandidateEvidence candidate) {
        if (candidate.requiresSponsorship()) {
            return true;
        }
        String auth = candidate.workAuthorizationStatus();
        if (!hasText(auth)) {
            return false;
        }
        String upper = auth.trim().toUpperCase(Locale.ROOT);
        return upper.contains("SPONSOR") || upper.contains("VISA_NEEDED");
    }

    private String trimToWordBoundary(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        int cut = text.lastIndexOf(' ', maxLen);
        if (cut < maxLen / 2) {
            cut = maxLen;
        }
        return text.substring(0, cut).trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ValidatedOutreachDraft(
            String subjectLine,
            String messageBody,
            List<String> keyTalkingPoints,
            int suggestedFollowUpDays,
            List<String> warnings
    ) {
    }
}

