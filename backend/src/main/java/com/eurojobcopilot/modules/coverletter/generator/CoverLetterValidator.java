package com.eurojobcopilot.modules.coverletter.generator;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.coverletter.dto.CoverLetterMetadataDto;
import com.eurojobcopilot.modules.coverletter.dto.UpdateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetter;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.CandidateHeaderEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.MasterResumeEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.TailoredResumeEvidence;
import com.eurojobcopilot.modules.coverletter.generator.CoverLetterContextSnapshot.WorkExperienceEvidence;
import com.eurojobcopilot.modules.matching.engine.DeterministicJobMatchingEngine;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProfileDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProjectDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeSkillDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeDataDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeProjectDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enforces strict anti-hallucination verification on AI-generated cover letters, sanitizes
 * prose formatting, validates highlighted skills and EU work authorization consistency, and
 * recomputes metadata when candidates edit drafts manually.
 */
@Component
@RequiredArgsConstructor
public class CoverLetterValidator {

    private static final int MAX_CONTENT_LENGTH = 15000;
    private static final int MAX_HIGHLIGHTED_SKILLS = 15;
    private static final int MAX_ALIGNMENT_POINTS = 8;
    private static final int MAX_WARNINGS = 20;

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]{1,120}>");
    private static final Pattern INLINE_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EXCESS_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern FALSE_CITIZENSHIP_PHRASE = Pattern.compile(
            "\\b(?:eu citizen|european citizen|eea citizen|no visa sponsorship required|without needing sponsorship|no sponsorship needed|unrestricted eu work authorization)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final DeterministicJobMatchingEngine matchingEngine;

    public ValidatedCoverLetter validateAiDraft(
            RawAiCoverLetterPayload raw,
            CoverLetterContextSnapshot snapshot
    ) {
        if (raw == null) {
            throw new AiResponseValidationException("AI returned a null cover letter payload.");
        }

        CandidateGroundTruth groundTruth = buildGroundTruth(snapshot);
        LinkedHashSet<String> warnings = new LinkedHashSet<>();

        String defaultSalutation = buildDefaultSalutation(snapshot.job());
        String salutation = cleanSingleLine(stripHtml(raw.getSalutation()), 160);
        if (salutation == null) {
            salutation = defaultSalutation;
        }

        String defaultSignOff = buildDefaultSignOff(groundTruth.fullName());
        String signOff = cleanMultilineProse(stripHtml(raw.getSignOff()), 200);
        if (signOff == null) {
            signOff = defaultSignOff;
        }

        String assembledBody = assembleFormattedLetter(raw, salutation, signOff);
        if (assembledBody == null || countWords(assembledBody) < 10) {
            throw new AiResponseValidationException(
                    "AI cover letter returned an empty or unusable draft with insufficient prose."
            );
        }

        // Verify highlighted skills strictly against verified candidate evidence
        List<String> verifiedHighlightedSkills = verifyHighlightedSkills(
                raw.getHighlightedSkills(),
                groundTruth,
                snapshot,
                warnings
        );

        // Scan prose for unverified JD technical keywords that could signal hallucinated stack claims
        flagUnverifiedJdSkillsInProse(assembledBody, snapshot, groundTruth, warnings, "Grounding notice");

        // Verify sponsorship/citizenship truthfulness when candidate requires sponsorship
        verifyWorkAuthorizationConsistency(assembledBody, snapshot, warnings, "Grounding notice");

        if (raw.getWarnings() != null) {
            for (String w : raw.getWarnings()) {
                addWarning(warnings, stripHtml(w));
            }
        }

        String companyConnection = cleanSingleLine(stripHtml(raw.getCompanyConnectionSummary()), 400);
        if (companyConnection == null) {
            companyConnection = buildFallbackCompanyConnection(snapshot.job());
        }

        List<String> alignmentPoints = sanitizeDistinctList(raw.getKeyAlignmentPoints(), MAX_ALIGNMENT_POINTS, 260);
        int wordCount = countWords(assembledBody);

        CoverLetterMetadataDto metadata = CoverLetterMetadataDto.builder()
                .salutation(salutation)
                .signOff(signOff)
                .candidateFullName(groundTruth.fullName())
                .candidateEmail(groundTruth.email())
                .candidateLocation(groundTruth.location())
                .candidateHeadline(groundTruth.headline())
                .workAuthorizationSummary(buildWorkAuthorizationSummary(snapshot.header()))
                .relocationReadinessNote(buildRelocationReadinessNote(snapshot))
                .companyConnectionSummary(companyConnection)
                .keyAlignmentPoints(alignmentPoints)
                .highlightedSkills(verifiedHighlightedSkills)
                .groundingWarnings(limitWarnings(warnings))
                .wordCount(wordCount)
                .build();

        return new ValidatedCoverLetter(assembledBody, wordCount, metadata);
    }

    public ValidatedCoverLetter revalidateEditedDraft(
            CoverLetter existingEntity,
            CoverLetterMetadataDto existingMetadata,
            UpdateCoverLetterRequest request,
            CoverLetterContextSnapshot snapshot
    ) {
        String resolvedContent;
        if (request.getContent() != null) {
            String cleaned = cleanMultilineProse(stripHtml(request.getContent()), MAX_CONTENT_LENGTH);
            if (cleaned == null || cleaned.isBlank()) {
                throw new BusinessValidationException("Cover letter content must not be blank");
            }
            resolvedContent = cleaned;
        } else {
            String existingCleaned = cleanMultilineProse(stripHtml(existingEntity.getContent()), MAX_CONTENT_LENGTH);
            if (existingCleaned == null || existingCleaned.isBlank()) {
                throw new BusinessValidationException("Cover letter content must not be blank");
            }
            resolvedContent = existingCleaned;
        }

        CandidateGroundTruth groundTruth = buildGroundTruth(snapshot);
        LinkedHashSet<String> warnings = new LinkedHashSet<>();

        flagUnverifiedJdSkillsInProse(resolvedContent, snapshot, groundTruth, warnings, "Manual edit note");
        verifyWorkAuthorizationConsistency(resolvedContent, snapshot, warnings, "Manual edit note");

        int wordCount = countWords(resolvedContent);
        CoverLetterMetadataDto base = existingMetadata != null
                ? existingMetadata
                : CoverLetterMetadataDto.builder().build();

        CoverLetterMetadataDto rebuilt = CoverLetterMetadataDto.builder()
                .salutation(firstNonBlank(base.getSalutation(), buildDefaultSalutation(snapshot.job())))
                .signOff(firstNonBlank(base.getSignOff(), buildDefaultSignOff(groundTruth.fullName())))
                .candidateFullName(firstNonBlank(groundTruth.fullName(), base.getCandidateFullName()))
                .candidateEmail(firstNonBlank(groundTruth.email(), base.getCandidateEmail()))
                .candidateLocation(firstNonBlank(groundTruth.location(), base.getCandidateLocation()))
                .candidateHeadline(firstNonBlank(groundTruth.headline(), base.getCandidateHeadline()))
                .workAuthorizationSummary(firstNonBlank(
                        buildWorkAuthorizationSummary(snapshot.header()),
                        base.getWorkAuthorizationSummary()))
                .relocationReadinessNote(firstNonBlank(
                        buildRelocationReadinessNote(snapshot),
                        base.getRelocationReadinessNote()))
                .companyConnectionSummary(firstNonBlank(
                        base.getCompanyConnectionSummary(),
                        buildFallbackCompanyConnection(snapshot.job())))
                .keyAlignmentPoints(base.getKeyAlignmentPoints() != null ? List.copyOf(base.getKeyAlignmentPoints()) : List.of())
                .highlightedSkills(base.getHighlightedSkills() != null ? List.copyOf(base.getHighlightedSkills()) : List.of())
                .groundingWarnings(limitWarnings(warnings))
                .wordCount(wordCount)
                .build();

        return new ValidatedCoverLetter(resolvedContent, wordCount, rebuilt);
    }

    private String assembleFormattedLetter(RawAiCoverLetterPayload raw, String salutation, String signOff) {
        List<String> bodySections = new ArrayList<>();

        String opening = cleanMultilineProse(stripHtml(raw.getOpeningParagraph()), 2500);
        if (opening != null) {
            bodySections.add(opening);
        }

        if (raw.getBodyParagraphs() != null) {
            for (String para : raw.getBodyParagraphs()) {
                String cleaned = cleanMultilineProse(stripHtml(para), 3500);
                if (cleaned != null) {
                    bodySections.add(cleaned);
                }
            }
        }

        String reloc = cleanMultilineProse(stripHtml(raw.getRelocationAndAuthorizationParagraph()), 1500);
        if (reloc != null) {
            bodySections.add(reloc);
        }

        String closing = cleanMultilineProse(stripHtml(raw.getClosingParagraph()), 1500);
        if (closing != null) {
            bodySections.add(closing);
        }

        if (!bodySections.isEmpty()) {
            String joinedBody = String.join("\n\n", bodySections);
            if (joinedBody.length() < 20) {
                return null;
            }
            StringBuilder out = new StringBuilder();
            out.append(salutation).append("\n\n");
            out.append(joinedBody);
            out.append("\n\n").append(signOff);
            return truncate(out.toString(), MAX_CONTENT_LENGTH);
        }

        // Fallback if AI returned raw full 'content'
        String fallback = cleanMultilineProse(stripHtml(raw.getContent()), MAX_CONTENT_LENGTH);
        if (fallback == null) {
            return null;
        }
        String lower = fallback.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        if (!lower.startsWith("dear ") && !lower.startsWith("hello ") && !lower.startsWith("to ")) {
            out.append(salutation).append("\n\n");
        }
        out.append(fallback);
        if (!lower.contains("sincerely") && !lower.contains("kind regards")
                && !lower.contains("best regards") && !lower.contains("regards,")) {
            out.append("\n\n").append(signOff);
        }
        return truncate(out.toString(), MAX_CONTENT_LENGTH);
    }

    private List<String> verifyHighlightedSkills(
            List<String> rawHighlighted,
            CandidateGroundTruth groundTruth,
            CoverLetterContextSnapshot snapshot,
            LinkedHashSet<String> warnings
    ) {
        Map<String, String> verified = new LinkedHashMap<>();
        if (rawHighlighted != null) {
            for (String item : rawHighlighted) {
                String cleaned = cleanSingleLine(stripHtml(item), 100);
                if (cleaned == null) {
                    continue;
                }
                if (!isSkillVerified(cleaned, groundTruth)) {
                    addWarning(warnings, "Removed unverified skill '" + cleaned
                            + "' from highlighted cover letter skills because it is absent from verified candidate evidence.");
                    continue;
                }
                String key = canonicalKey(cleaned);
                if (!key.isBlank() && verified.size() < MAX_HIGHLIGHTED_SKILLS) {
                    verified.putIfAbsent(key, cleaned);
                }
            }
        }

        if (verified.isEmpty() && !groundTruth.verifiedSkills().isEmpty()) {
            Map<String, String> jdKeywords = collectTargetJdKeywords(snapshot);
            for (Map.Entry<String, String> candEntry : groundTruth.verifiedSkills().entrySet()) {
                if (jdKeywords.containsKey(candEntry.getKey())
                        || matchingEngine.skillSetSatisfies(jdKeywords.keySet(), candEntry.getKey())) {
                    verified.putIfAbsent(candEntry.getKey(), candEntry.getValue());
                    if (verified.size() >= 6) {
                        break;
                    }
                }
            }
            if (verified.isEmpty()) {
                for (Map.Entry<String, String> candEntry : groundTruth.verifiedSkills().entrySet()) {
                    verified.putIfAbsent(candEntry.getKey(), candEntry.getValue());
                    if (verified.size() >= 6) {
                        break;
                    }
                }
            }
        }

        return List.copyOf(verified.values());
    }

    private void flagUnverifiedJdSkillsInProse(
            String letterContent,
            CoverLetterContextSnapshot snapshot,
            CandidateGroundTruth groundTruth,
            LinkedHashSet<String> warnings,
            String noticePrefix
    ) {
        if (!hasText(letterContent)) {
            return;
        }
        Map<String, String> jdKeywords = collectTargetJdKeywords(snapshot);
        if (jdKeywords.isEmpty()) {
            return;
        }

        String lowerLetter = letterContent.toLowerCase(Locale.ROOT);
        Set<String> letterExtracted = matchingEngine.extractSkillsFromText(lowerLetter).keySet();

        for (Map.Entry<String, String> jdEntry : jdKeywords.entrySet()) {
            String jdKey = jdEntry.getKey();
            String jdDisplay = jdEntry.getValue();
            boolean presentInProse = letterExtracted.contains(jdKey)
                    || containsBoundaryKeyword(lowerLetter, jdDisplay)
                    || (!jdKey.equals(jdDisplay.toLowerCase(Locale.ROOT)) && containsBoundaryKeyword(lowerLetter, jdKey));

            if (presentInProse && !isCanonicalOrTextSkillVerified(jdKey, jdDisplay, groundTruth)) {
                addWarning(warnings, noticePrefix + ": draft text referenced unverified JD skill '" + jdDisplay
                        + "' not found in verified candidate profile or CV; verify before sending.");
            }
        }
    }

    private void verifyWorkAuthorizationConsistency(
            String letterContent,
            CoverLetterContextSnapshot snapshot,
            LinkedHashSet<String> warnings,
            String noticePrefix
    ) {
        CandidateHeaderEvidence h = snapshot.header();
        if (h == null || !"NEEDS_SPONSORSHIP".equalsIgnoreCase(h.workAuthorizationStatus())) {
            return;
        }
        if (FALSE_CITIZENSHIP_PHRASE.matcher(letterContent).find()) {
            addWarning(warnings, noticePrefix
                    + ": your profile states visa sponsorship is required; review work authorization claims in the letter before sending.");
        }
    }

    private CandidateGroundTruth buildGroundTruth(CoverLetterContextSnapshot snapshot) {
        Map<String, String> verifiedSkills = new LinkedHashMap<>();
        StringBuilder corpus = new StringBuilder();

        CandidateHeaderEvidence header = snapshot.header();
        String fullName = header != null ? cleanSingleLine(header.fullName(), 150) : null;
        String email = header != null ? cleanSingleLine(header.email(), 150) : null;
        String location = header != null ? joinLocation(header.currentCity(), header.currentCountry()) : null;
        String headline = header != null ? cleanSingleLine(header.currentDesignation(), 150) : null;

        if (header != null) {
            appendText(corpus, header.currentDesignation());
            appendText(corpus, header.bio());
            registerSkillsExtractedFromText(verifiedSkills, header.currentDesignation());
            registerSkillsExtractedFromText(verifiedSkills, header.bio());
        }

        for (SkillEvidence s : snapshot.skills()) {
            if (s != null) {
                registerVerifiedSkill(verifiedSkills, s.skillName());
                appendText(corpus, s.skillName());
            }
        }

        for (WorkExperienceEvidence exp : snapshot.workExperiences()) {
            if (exp == null) {
                continue;
            }
            appendText(corpus, exp.companyName());
            appendText(corpus, exp.designation());
            appendText(corpus, exp.description());
            registerSkillsExtractedFromText(verifiedSkills, exp.description());
            for (String used : exp.skillsUsed()) {
                registerVerifiedSkill(verifiedSkills, used);
                appendText(corpus, used);
            }
        }

        for (EducationEvidence edu : snapshot.educations()) {
            if (edu == null) {
                continue;
            }
            appendText(corpus, edu.degree());
            appendText(corpus, edu.fieldOfStudy());
            appendText(corpus, edu.institution());
        }

        MasterResumeEvidence mr = snapshot.masterResume();
        if (mr != null) {
            appendText(corpus, mr.rawExtractedText());
            registerSkillsExtractedFromText(verifiedSkills, mr.rawExtractedText());

            ParsedResumeJsonDto parsed = mr.parsedResume();
            if (parsed != null) {
                appendText(corpus, parsed.getSummary());
                registerSkillsExtractedFromText(verifiedSkills, parsed.getSummary());
                if (parsed.getProfile() != null) {
                    ParsedResumeProfileDto pp = parsed.getProfile();
                    if (fullName == null) {
                        fullName = cleanSingleLine(pp.getFullName(), 150);
                    }
                    if (headline == null) {
                        headline = cleanSingleLine(pp.getCurrentDesignation(), 150);
                    }
                    if (location == null) {
                        location = joinLocation(pp.getCurrentCity(), pp.getCurrentCountry());
                    }
                    appendText(corpus, pp.getCurrentDesignation());
                    appendText(corpus, pp.getBio());
                    registerSkillsExtractedFromText(verifiedSkills, pp.getBio());
                }
                if (parsed.getSkills() != null) {
                    for (ParsedResumeSkillDto ps : parsed.getSkills()) {
                        if (ps != null) {
                            registerVerifiedSkill(verifiedSkills, ps.getSkillName());
                            appendText(corpus, ps.getSkillName());
                        }
                    }
                }
                if (parsed.getExperiences() != null) {
                    for (ParsedResumeExperienceDto pe : parsed.getExperiences()) {
                        if (pe != null) {
                            appendText(corpus, pe.getCompanyName());
                            appendText(corpus, pe.getDesignation());
                            appendText(corpus, pe.getDescription());
                            registerSkillsExtractedFromText(verifiedSkills, pe.getDescription());
                            if (pe.getSkillsUsed() != null) {
                                pe.getSkillsUsed().forEach(su -> {
                                    registerVerifiedSkill(verifiedSkills, su);
                                    appendText(corpus, su);
                                });
                            }
                        }
                    }
                }
                if (parsed.getEducations() != null) {
                    for (ParsedResumeEducationDto ped : parsed.getEducations()) {
                        if (ped != null) {
                            appendText(corpus, ped.getDegree());
                            appendText(corpus, ped.getFieldOfStudy());
                            appendText(corpus, ped.getInstitution());
                        }
                    }
                }
                if (parsed.getProjects() != null) {
                    for (ParsedResumeProjectDto proj : parsed.getProjects()) {
                        if (proj != null) {
                            appendText(corpus, proj.getName());
                            appendText(corpus, proj.getDescription());
                            registerSkillsExtractedFromText(verifiedSkills, proj.getDescription());
                            if (proj.getTechnologies() != null) {
                                proj.getTechnologies().forEach(t -> {
                                    registerVerifiedSkill(verifiedSkills, t);
                                    appendText(corpus, t);
                                });
                            }
                        }
                    }
                }
                if (parsed.getCertifications() != null) {
                    parsed.getCertifications().forEach(cert -> {
                        appendText(corpus, cert);
                        registerSkillsExtractedFromText(verifiedSkills, cert);
                    });
                }
            }
        }

        TailoredResumeEvidence tr = snapshot.tailoredResume();
        if (tr != null && tr.resumeData() != null) {
            TailoredResumeDataDto td = tr.resumeData();
            if (td.getHeader() != null) {
                if (fullName == null) {
                    fullName = cleanSingleLine(td.getHeader().getFullName(), 150);
                }
                if (headline == null) {
                    headline = cleanSingleLine(td.getHeader().getHeadline(), 150);
                }
                if (location == null) {
                    location = cleanSingleLine(td.getHeader().getLocation(), 150);
                }
            }
            appendText(corpus, td.getTailoredSummary());
            registerSkillsExtractedFromText(verifiedSkills, td.getTailoredSummary());
            if (td.getHighlightedSkills() != null) {
                td.getHighlightedSkills().forEach(s -> {
                    registerVerifiedSkill(verifiedSkills, s);
                    appendText(corpus, s);
                });
            }
            if (td.getAdditionalSkills() != null) {
                td.getAdditionalSkills().forEach(s -> {
                    registerVerifiedSkill(verifiedSkills, s);
                    appendText(corpus, s);
                });
            }
            if (td.getOptimizedExperiences() != null) {
                for (TailoredResumeExperienceDto exp : td.getOptimizedExperiences()) {
                    if (exp != null) {
                        appendText(corpus, exp.getCompanyName());
                        appendText(corpus, exp.getDesignation());
                        if (exp.getTailoredBullets() != null) {
                            exp.getTailoredBullets().forEach(b -> {
                                appendText(corpus, b);
                                registerSkillsExtractedFromText(verifiedSkills, b);
                            });
                        }
                    }
                }
            }
            if (td.getProjects() != null) {
                for (TailoredResumeProjectDto p : td.getProjects()) {
                    if (p != null) {
                        appendText(corpus, p.getName());
                        appendText(corpus, p.getDescription());
                        if (p.getTechnologies() != null) {
                            p.getTechnologies().forEach(t -> {
                                registerVerifiedSkill(verifiedSkills, t);
                                appendText(corpus, t);
                            });
                        }
                    }
                }
            }
        }

        return new CandidateGroundTruth(
                verifiedSkills,
                corpus.toString().toLowerCase(Locale.ROOT),
                fullName,
                email,
                location,
                headline
        );
    }

    private Map<String, String> collectTargetJdKeywords(CoverLetterContextSnapshot snapshot) {
        Map<String, String> jdKeywords = new LinkedHashMap<>();
        JobEvidence job = snapshot.job();
        JobAiAnalysisEvidence ai = snapshot.jobAiAnalysis();

        if (job != null) {
            for (String skill : job.skills()) {
                addJdKeyword(jdKeywords, skill);
            }
        }
        if (ai != null) {
            for (String skill : ai.requiredSkills()) {
                addJdKeyword(jdKeywords, skill);
            }
            for (String skill : ai.niceToHaveSkills()) {
                addJdKeyword(jdKeywords, skill);
            }
        }
        if (job != null) {
            String jobText = (job.title() != null ? job.title() : "") + "\n"
                    + (job.sanitizedDescription() != null ? job.sanitizedDescription() : "");
            Map<String, String> extracted = matchingEngine.extractSkillsFromText(jobText);
            for (Map.Entry<String, String> entry : extracted.entrySet()) {
                jdKeywords.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return jdKeywords;
    }

    private void addJdKeyword(Map<String, String> target, String rawSkill) {
        String cleaned = cleanSingleLine(rawSkill, 100);
        if (cleaned == null) {
            return;
        }
        String key = canonicalKey(cleaned);
        if (!key.isBlank()) {
            target.putIfAbsent(key, cleaned);
        }
    }

    private String buildDefaultSalutation(JobEvidence job) {
        String company = job != null && hasText(job.companyName()) ? job.companyName().trim() : "the Hiring Team";
        if ("the Hiring Team".equals(company)) {
            return "Dear Hiring Team,";
        }
        return "Dear Hiring Team at " + company + ",";
    }

    private String buildDefaultSignOff(String fullName) {
        if (hasText(fullName)) {
            return "Sincerely,\n" + fullName.trim();
        }
        return "Sincerely,";
    }

    private String buildFallbackCompanyConnection(JobEvidence job) {
        if (job == null) {
            return null;
        }
        String company = hasText(job.companyName()) ? job.companyName().trim() : "Target Employer";
        if (hasText(job.companyIndustry()) && hasText(job.country())) {
            return "Tailored for " + company + "'s " + job.companyIndustry().trim() + " operations in " + job.country().trim() + ".";
        }
        if (hasText(job.title())) {
            return "Aligned directly with " + company + "'s " + job.title().trim() + " role.";
        }
        return "Aligned with " + company + ".";
    }

    private String buildWorkAuthorizationSummary(CandidateHeaderEvidence header) {
        if (header == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (hasText(header.workAuthorizationStatus())) {
            switch (header.workAuthorizationStatus().trim()) {
                case "EU_CITIZEN" -> parts.add("EU/EEA Citizen (Unrestricted EU Work Authorization)");
                case "BLUE_CARD_HOLDER" -> parts.add("EU Blue Card Holder");
                case "PERMANENT_RESIDENT" -> parts.add("EU Permanent Resident");
                case "NEEDS_SPONSORSHIP" -> parts.add("Requires Visa Sponsorship");
                default -> parts.add(header.workAuthorizationStatus().trim().replace('_', ' '));
            }
        }
        if (hasText(header.relocationPreference())) {
            switch (header.relocationPreference().trim()) {
                case "ALREADY_IN_EUROPE" -> parts.add("Based in Europe");
                case "WILLING_TO_RELOCATE" -> parts.add("Open to relocation");
                case "RELOCATING_SOON" -> parts.add("Relocating to Europe soon");
                case "REMOTE_ONLY" -> parts.add("Remote preference");
                case "NOT_INTERESTED" -> parts.add("Non-relocation preference");
                default -> {
                }
            }
        }
        if (header.noticePeriodDays() != null && header.noticePeriodDays() >= 0) {
            parts.add(header.noticePeriodDays() + "-day notice");
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private String buildRelocationReadinessNote(CoverLetterContextSnapshot snapshot) {
        CandidateHeaderEvidence h = snapshot.header();
        JobEvidence job = snapshot.job();
        if (h == null || job == null) {
            return null;
        }
        List<String> notes = new ArrayList<>();
        String targetCountry = hasText(job.country()) ? job.country().trim() : null;

        if (targetCountry != null && hasText(h.currentCountry())
                && targetCountry.equalsIgnoreCase(h.currentCountry().trim())) {
            notes.add("Already located in " + targetCountry);
        } else if (targetCountry != null && hasText(h.relocationPreference())) {
            switch (h.relocationPreference().trim()) {
                case "ALREADY_IN_EUROPE" -> notes.add("Based in Europe and ready for " + targetCountry);
                case "WILLING_TO_RELOCATE" -> notes.add("Open to relocating to " + targetCountry);
                case "RELOCATING_SOON" -> notes.add("Actively relocating toward " + targetCountry);
                case "REMOTE_ONLY" -> notes.add("Remote-first preference for " + targetCountry + " role");
                default -> {
                }
            }
        }

        if (job.verifiedSponsor() && targetCountry != null) {
            notes.add(job.companyName() + " is a verified sponsor in " + targetCountry);
        } else if ("SPONSORSHIP_MENTIONED".equals(job.sponsorStatus())) {
            notes.add("Job posting explicitly mentions visa sponsorship");
        } else if ("RELOCATION_OFFERED".equals(job.sponsorStatus())) {
            notes.add("Job posting notes relocation support");
        }

        if (h.noticePeriodDays() != null && h.noticePeriodDays() >= 0) {
            notes.add("Notice period: " + h.noticePeriodDays() + " days");
        }
        return notes.isEmpty() ? null : String.join(" · ", notes);
    }

    private boolean isSkillVerified(String rawSkill, CandidateGroundTruth groundTruth) {
        String key = canonicalKey(rawSkill);
        if (key.isBlank()) {
            return false;
        }
        return isCanonicalOrTextSkillVerified(key, rawSkill, groundTruth);
    }

    private boolean isCanonicalOrTextSkillVerified(
            String canonicalKey,
            String displayText,
            CandidateGroundTruth groundTruth
    ) {
        if (matchingEngine.skillSetSatisfies(groundTruth.verifiedSkills().keySet(), canonicalKey)) {
            return true;
        }
        return containsBoundaryKeyword(groundTruth.candidateCorpusLower(), displayText)
                || (!canonicalKey.equalsIgnoreCase(displayText)
                && containsBoundaryKeyword(groundTruth.candidateCorpusLower(), canonicalKey));
    }

    private boolean containsBoundaryKeyword(String normalizedLowerCorpus, String keyword) {
        if (normalizedLowerCorpus == null || normalizedLowerCorpus.isBlank()
                || keyword == null || keyword.isBlank()) {
            return false;
        }
        String lowerKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        if (lowerKeyword.length() <= 1 && !"#".equals(lowerKeyword) && !"+".equals(lowerKeyword)) {
            return false;
        }
        Pattern pattern = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(lowerKeyword) + "(?![a-z0-9])");
        return pattern.matcher(normalizedLowerCorpus).find();
    }

    private void registerVerifiedSkill(Map<String, String> target, String rawSkill) {
        String cleaned = cleanSingleLine(rawSkill, 100);
        if (cleaned == null) {
            return;
        }
        String key = canonicalKey(cleaned);
        if (!key.isBlank()) {
            target.putIfAbsent(key, cleaned);
        }
    }

    private void registerSkillsExtractedFromText(Map<String, String> target, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Map<String, String> extracted = matchingEngine.extractSkillsFromText(text);
        for (Map.Entry<String, String> entry : extracted.entrySet()) {
            target.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private List<String> sanitizeDistinctList(List<String> rawItems, int maxKeep, int maxLen) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String item : rawItems) {
            String cleaned = cleanSingleLine(stripHtml(item), maxLen);
            if (cleaned != null) {
                unique.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
                if (unique.size() >= maxKeep) {
                    break;
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private String canonicalKey(String raw) {
        String key = matchingEngine.canonicalizeSkill(raw);
        if (!key.isBlank()) {
            return key;
        }
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
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

    private void appendText(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(' ').append(value.trim());
        }
    }

    private void addWarning(LinkedHashSet<String> warnings, String rawWarning) {
        String cleaned = cleanSingleLine(rawWarning, 350);
        if (cleaned != null && warnings.size() < MAX_WARNINGS) {
            warnings.add(cleaned);
        }
    }

    private List<String> limitWarnings(LinkedHashSet<String> warnings) {
        return warnings.stream().limit(MAX_WARNINGS).toList();
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private String stripHtml(String raw) {
        if (raw == null) {
            return null;
        }
        String clean = CONTROL_CHARS.matcher(raw.replace('\u00A0', ' ')).replaceAll("");
        return HTML_TAG_PATTERN.matcher(clean).replaceAll("");
    }

    private String cleanSingleLine(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String collapsed = INLINE_WHITESPACE.matcher(raw).replaceAll(" ").trim();
        return collapsed.isEmpty() ? null : truncate(collapsed, maxLength);
    }

    private String cleanMultilineProse(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim();
        normalized = EXCESS_NEWLINES.matcher(normalized).replaceAll("\n\n");
        return normalized.isEmpty() ? null : truncate(normalized, maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CandidateGroundTruth(
            Map<String, String> verifiedSkills,
            String candidateCorpusLower,
            String fullName,
            String email,
            String location,
            String headline
    ) {
    }
}
