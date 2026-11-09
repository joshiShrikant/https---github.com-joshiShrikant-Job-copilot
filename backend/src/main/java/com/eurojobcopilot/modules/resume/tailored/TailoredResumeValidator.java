package com.eurojobcopilot.modules.resume.tailored;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.matching.engine.DeterministicJobMatchingEngine;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProfileDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProjectDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeSkillDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeDataDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeHeaderDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeProjectDto;
import com.eurojobcopilot.modules.resume.dto.UpdateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.EducationEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.HeaderEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.JobAiAnalysisEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.JobEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.MasterResumeEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.SkillEvidence;
import com.eurojobcopilot.modules.resume.tailored.TailoredResumeContextSnapshot.WorkExperienceEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enforces strict anti-hallucination grounding against verified candidate evidence and
 * computes deterministic ATS keyword coverage across JD requirements.
 */
@Component
@RequiredArgsConstructor
public class TailoredResumeValidator {

    private static final int MAX_HIGHLIGHTED_SKILLS = 40;
    private static final int MAX_ADDITIONAL_SKILLS = 40;
    private static final int MAX_MATCHED_KEYWORDS = 40;
    private static final int MAX_MISSING_KEYWORDS = 40;
    private static final int MAX_EXPERIENCES = 25;
    private static final int MAX_PROJECTS = 15;
    private static final int MAX_EDUCATIONS = 15;
    private static final int MAX_LANGUAGES = 20;
    private static final int MAX_CERTIFICATIONS = 20;
    private static final int MAX_WARNINGS = 30;
    private static final int MAX_BULLETS_PER_EXP = 15;
    private static final int MAX_ORIG_BULLETS_PER_EXP = 20;
    private static final int MAX_BULLETS_PER_PROJECT = 10;

    private static final Pattern INLINE_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EXCESS_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern LEADING_BULLET_MARKER = Pattern.compile("^(?:[•\\-*▪▫◦‣–—]+|\\d+[.)])\\s*");
    private static final Pattern LEGAL_SUFFIX_PATTERN = Pattern.compile(
            "\\b(?:gmbh|ag|se|kg|ohg|ug|b\\.v\\.|bv|n\\.v\\.|nv|inc\\.|inc|llc|ltd\\.|ltd|limited|plc|corp\\.|corp|s\\.a\\.|sa|sas|sarl|s\\.r\\.l\\.|srl|ab|oy|aps|as)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final DeterministicJobMatchingEngine matchingEngine;

    public ValidatedTailoredResume validateAiDraft(
            RawAiTailoredResumePayload raw,
            TailoredResumeContextSnapshot snapshot
    ) {
        if (raw == null) {
            throw new AiResponseValidationException("AI returned a null tailored resume payload.");
        }

        CandidateGroundTruth groundTruth = buildGroundTruth(snapshot);
        LinkedHashSet<String> warnings = new LinkedHashSet<>();

        String headline = cleanSingleLine(raw.getHeadline(), 150);
        if (headline == null) {
            headline = cleanSingleLine(groundTruth.defaultDesignation(), 150);
        }
        String tailoredSummary = cleanProse(raw.getTailoredSummary(), 2500);
        if (tailoredSummary == null) {
            tailoredSummary = cleanProse(groundTruth.fallbackSummary(), 2500);
            if (tailoredSummary != null) {
                addWarning(warnings, "Used verified candidate summary because AI did not supply a tailored summary.");
            }
        }
        if (tailoredSummary == null && headline == null && groundTruth.experiences().isEmpty()
                && groundTruth.verifiedSkills().isEmpty()) {
            throw new AiResponseValidationException("AI tailored resume returned an empty draft with no usable content.");
        }

        String tailoringRationale = cleanProse(raw.getTailoringRationale(), 2000);

        List<TailoredResumeExperienceDto> optimizedExperiences = validateAiExperiences(
                raw.getOptimizedExperiences(),
                groundTruth,
                warnings
        );

        List<TailoredResumeProjectDto> tailoredProjects = validateAiProjects(
                raw.getProjects(),
                groundTruth,
                warnings
        );

        SkillPartitionResult skillPartition = partitionAndVerifySkills(
                raw.getHighlightedSkills(),
                raw.getAdditionalSkills(),
                groundTruth,
                warnings
        );

        TailoredResumeHeaderDto header = buildLockedHeader(snapshot, groundTruth, headline);

        TailoredResumeDataDto provisional = TailoredResumeDataDto.builder()
                .header(header)
                .tailoredSummary(tailoredSummary)
                .highlightedSkills(skillPartition.highlighted())
                .additionalSkills(skillPartition.additional())
                .matchedKeywords(List.of())
                .missingKeywords(List.of())
                .tailoringRationale(tailoringRationale)
                .optimizedExperiences(optimizedExperiences)
                .projects(tailoredProjects)
                .educations(groundTruth.educations())
                .languages(groundTruth.languages())
                .certifications(groundTruth.certifications())
                .groundingWarnings(List.of())
                .build();

        AtsCoverageResult atsCoverage = computeAtsCoverage(
                provisional,
                snapshot,
                groundTruth,
                raw.getMissingKeywords(),
                true,
                warnings
        );

        if (raw.getWarnings() != null) {
            for (String w : raw.getWarnings()) {
                addWarning(warnings, w);
            }
        }

        provisional.setMatchedKeywords(atsCoverage.matchedKeywords());
        provisional.setMissingKeywords(atsCoverage.missingKeywords());
        provisional.setGroundingWarnings(limitWarnings(warnings));

        return new ValidatedTailoredResume(provisional, atsCoverage.coveragePercentage());
    }

    public ValidatedTailoredResume revalidateEditedDraft(
            TailoredResumeDataDto existingData,
            UpdateTailoredResumeRequest request,
            TailoredResumeContextSnapshot snapshot
    ) {
        CandidateGroundTruth groundTruth = buildGroundTruth(snapshot);
        LinkedHashSet<String> warnings = new LinkedHashSet<>();

        TailoredResumeHeaderDto header = existingData != null && existingData.getHeader() != null
                ? copyHeader(existingData.getHeader())
                : buildLockedHeader(snapshot, groundTruth, groundTruth.defaultDesignation());

        String summary = request.getTailoredSummary() != null
                ? cleanProse(request.getTailoredSummary(), 2500)
                : (existingData != null ? cleanProse(existingData.getTailoredSummary(), 2500) : null);

        List<String> highlightedSkills = request.getHighlightedSkills() != null
                ? sanitizeDistinctList(request.getHighlightedSkills(), MAX_HIGHLIGHTED_SKILLS, 100)
                : (existingData != null && existingData.getHighlightedSkills() != null
                ? sanitizeDistinctList(existingData.getHighlightedSkills(), MAX_HIGHLIGHTED_SKILLS, 100)
                : List.of());

        List<String> additionalSkills = request.getAdditionalSkills() != null
                ? sanitizeDistinctList(request.getAdditionalSkills(), MAX_ADDITIONAL_SKILLS, 100)
                : (existingData != null && existingData.getAdditionalSkills() != null
                ? sanitizeDistinctList(existingData.getAdditionalSkills(), MAX_ADDITIONAL_SKILLS, 100)
                : List.of());

        // Deduplicate between highlightedSkills and additionalSkills (highlighted wins)
        Set<String> highlightedKeys = new LinkedHashSet<>();
        for (String skill : highlightedSkills) {
            String key = canonicalKey(skill);
            if (!key.isBlank()) {
                highlightedKeys.add(key);
            }
        }
        List<String> cleanedAdditional = new ArrayList<>();
        for (String skill : additionalSkills) {
            String key = canonicalKey(skill);
            if (!key.isBlank() && highlightedKeys.add(key)) {
                cleanedAdditional.add(skill);
            }
        }

        List<TailoredResumeExperienceDto> experiences = request.getOptimizedExperiences() != null
                ? sanitizeEditedExperiences(request.getOptimizedExperiences(), existingData, groundTruth)
                : (existingData != null && existingData.getOptimizedExperiences() != null
                ? List.copyOf(existingData.getOptimizedExperiences())
                : List.of());

        List<TailoredResumeProjectDto> projects = request.getProjects() != null
                ? sanitizeEditedProjects(request.getProjects())
                : (existingData != null && existingData.getProjects() != null
                ? List.copyOf(existingData.getProjects())
                : List.of());

        List<TailoredResumeEducationDto> educations = request.getEducations() != null
                ? sanitizeEditedEducations(request.getEducations())
                : (existingData != null && existingData.getEducations() != null
                ? List.copyOf(existingData.getEducations())
                : groundTruth.educations());

        List<String> languages = request.getLanguages() != null
                ? sanitizeDistinctList(request.getLanguages(), MAX_LANGUAGES, 100)
                : (existingData != null && existingData.getLanguages() != null
                ? sanitizeDistinctList(existingData.getLanguages(), MAX_LANGUAGES, 100)
                : groundTruth.languages());

        List<String> certifications = request.getCertifications() != null
                ? sanitizeDistinctList(request.getCertifications(), MAX_CERTIFICATIONS, 150)
                : (existingData != null && existingData.getCertifications() != null
                ? sanitizeDistinctList(existingData.getCertifications(), MAX_CERTIFICATIONS, 150)
                : groundTruth.certifications());

        String rationale = existingData != null ? cleanProse(existingData.getTailoringRationale(), 2000) : null;

        // Gentle grounding warning if human draft adds skills absent from master profile
        for (String skill : highlightedSkills) {
            if (!isSkillVerified(skill, groundTruth)) {
                addWarning(warnings, "Manual edit note: highlighted skill '" + skill
                        + "' is not currently verified on your Master Profile/CV.");
            }
        }
        for (String skill : cleanedAdditional) {
            if (!isSkillVerified(skill, groundTruth)) {
                addWarning(warnings, "Manual edit note: additional skill '" + skill
                        + "' is not currently verified on your Master Profile/CV.");
            }
        }

        TailoredResumeDataDto rebuilt = TailoredResumeDataDto.builder()
                .header(header)
                .tailoredSummary(summary)
                .highlightedSkills(List.copyOf(highlightedSkills))
                .additionalSkills(List.copyOf(cleanedAdditional))
                .matchedKeywords(List.of())
                .missingKeywords(List.of())
                .tailoringRationale(rationale)
                .optimizedExperiences(experiences)
                .projects(projects)
                .educations(educations)
                .languages(languages)
                .certifications(certifications)
                .groundingWarnings(List.of())
                .build();

        AtsCoverageResult atsCoverage = computeAtsCoverage(
                rebuilt,
                snapshot,
                groundTruth,
                null,
                false,
                warnings
        );

        rebuilt.setMatchedKeywords(atsCoverage.matchedKeywords());
        rebuilt.setMissingKeywords(atsCoverage.missingKeywords());
        rebuilt.setGroundingWarnings(limitWarnings(warnings));

        return new ValidatedTailoredResume(rebuilt, atsCoverage.coveragePercentage());
    }

    private CandidateGroundTruth buildGroundTruth(TailoredResumeContextSnapshot snapshot) {
        Map<String, String> verifiedSkills = new LinkedHashMap<>();
        StringBuilder corpus = new StringBuilder();

        HeaderEvidence header = snapshot.header();
        if (header != null) {
            appendText(corpus, header.currentDesignation());
            appendText(corpus, header.bio());
        }

        for (SkillEvidence s : snapshot.skills()) {
            if (s != null) {
                registerVerifiedSkill(verifiedSkills, s.skillName());
                appendText(corpus, s.skillName());
            }
        }

        List<VerifiedExperience> mergedExperiences = new ArrayList<>();
        Set<String> seenExperienceKeys = new LinkedHashSet<>();

        for (WorkExperienceEvidence exp : snapshot.workExperiences()) {
            if (exp == null) {
                continue;
            }
            for (String used : exp.skillsUsed()) {
                registerVerifiedSkill(verifiedSkills, used);
                appendText(corpus, used);
            }
            appendText(corpus, exp.designation());
            appendText(corpus, exp.description());
            registerSkillsExtractedFromText(verifiedSkills, exp.description());

            List<String> origBullets = extractOriginalBullets(exp.description());
            VerifiedExperience entry = new VerifiedExperience(
                    cleanSingleLine(exp.companyName(), 200),
                    cleanSingleLine(exp.designation(), 150),
                    cleanSingleLine(exp.location(), 150),
                    exp.startDate(),
                    exp.endDate(),
                    exp.current(),
                    origBullets
            );
            if (entry.companyName() != null && entry.designation() != null) {
                mergedExperiences.add(entry);
                seenExperienceKeys.add(experienceIdentityKey(entry.companyName(), entry.designation(), entry.startDate()));
            }
        }

        List<TailoredResumeEducationDto> mergedEducations = new ArrayList<>();
        Set<String> seenEduKeys = new LinkedHashSet<>();
        for (EducationEvidence edu : snapshot.educations()) {
            if (edu == null) {
                continue;
            }
            TailoredResumeEducationDto dto = TailoredResumeEducationDto.builder()
                    .institution(cleanSingleLine(edu.institution(), 200))
                    .degree(cleanSingleLine(edu.degree(), 150))
                    .fieldOfStudy(cleanSingleLine(edu.fieldOfStudy(), 150))
                    .startDate(edu.startDate())
                    .endDate(edu.endDate())
                    .gradeOrGpa(cleanSingleLine(edu.gradeOrGpa(), 50))
                    .country(cleanSingleLine(edu.country(), 100))
                    .build();
            if (dto.getInstitution() != null && dto.getDegree() != null && dto.getFieldOfStudy() != null) {
                mergedEducations.add(dto);
                seenEduKeys.add(educationKey(dto.getInstitution(), dto.getDegree(), dto.getFieldOfStudy()));
            }
        }

        Map<String, VerifiedProject> verifiedProjects = new LinkedHashMap<>();
        LinkedHashSet<String> languages = new LinkedHashSet<>();
        LinkedHashSet<String> certifications = new LinkedHashSet<>();

        String parsedSummary = null;
        String parsedDesignation = null;
        MasterResumeEvidence mr = snapshot.masterResume();
        if (mr != null) {
            appendText(corpus, mr.rawExtractedText());
            registerSkillsExtractedFromText(verifiedSkills, mr.rawExtractedText());

            ParsedResumeJsonDto parsed = mr.parsedResume();
            if (parsed != null) {
                parsedSummary = cleanProse(parsed.getSummary(), 2500);
                appendText(corpus, parsed.getSummary());
                registerSkillsExtractedFromText(verifiedSkills, parsed.getSummary());

                if (parsed.getProfile() != null) {
                    ParsedResumeProfileDto pp = parsed.getProfile();
                    parsedDesignation = cleanSingleLine(pp.getCurrentDesignation(), 150);
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
                        if (pe == null) {
                            continue;
                        }
                        if (pe.getSkillsUsed() != null) {
                            for (String su : pe.getSkillsUsed()) {
                                registerVerifiedSkill(verifiedSkills, su);
                                appendText(corpus, su);
                            }
                        }
                        appendText(corpus, pe.getDesignation());
                        appendText(corpus, pe.getDescription());
                        registerSkillsExtractedFromText(verifiedSkills, pe.getDescription());

                        String comp = cleanSingleLine(pe.getCompanyName(), 200);
                        String desig = cleanSingleLine(pe.getDesignation(), 150);
                        if (comp != null && desig != null) {
                            String idKey = experienceIdentityKey(comp, desig, pe.getStartDate());
                            if (!seenExperienceKeys.contains(idKey) && mergedExperiences.size() < MAX_EXPERIENCES) {
                                boolean alreadySimilarCompany = mergedExperiences.stream().anyMatch(existing ->
                                        normalizeCompanyKey(existing.companyName()).equals(normalizeCompanyKey(comp))
                                                && normalizeTextKey(existing.designation()).equals(normalizeTextKey(desig))
                                );
                                if (!alreadySimilarCompany) {
                                    mergedExperiences.add(new VerifiedExperience(
                                            comp,
                                            desig,
                                            cleanSingleLine(pe.getLocation(), 150),
                                            pe.getStartDate(),
                                            pe.getEndDate(),
                                            pe.isCurrent(),
                                            extractOriginalBullets(pe.getDescription())
                                    ));
                                    seenExperienceKeys.add(idKey);
                                }
                            }
                        }
                    }
                }

                if (parsed.getEducations() != null) {
                    for (ParsedResumeEducationDto ped : parsed.getEducations()) {
                        if (ped == null) {
                            continue;
                        }
                        TailoredResumeEducationDto dto = TailoredResumeEducationDto.builder()
                                .institution(cleanSingleLine(ped.getInstitution(), 200))
                                .degree(cleanSingleLine(ped.getDegree(), 150))
                                .fieldOfStudy(cleanSingleLine(ped.getFieldOfStudy(), 150))
                                .startDate(ped.getStartDate())
                                .endDate(ped.getEndDate())
                                .gradeOrGpa(cleanSingleLine(ped.getGradeOrGpa(), 50))
                                .country(cleanSingleLine(ped.getCountry(), 100))
                                .build();
                        if (dto.getInstitution() != null && dto.getDegree() != null && dto.getFieldOfStudy() != null) {
                            String key = educationKey(dto.getInstitution(), dto.getDegree(), dto.getFieldOfStudy());
                            if (seenEduKeys.add(key) && mergedEducations.size() < MAX_EDUCATIONS) {
                                mergedEducations.add(dto);
                            }
                        }
                    }
                }

                if (parsed.getProjects() != null) {
                    for (ParsedResumeProjectDto proj : parsed.getProjects()) {
                        if (proj == null) {
                            continue;
                        }
                        String name = cleanSingleLine(proj.getName(), 150);
                        if (name == null) {
                            continue;
                        }
                        List<String> techs = sanitizeDistinctList(proj.getTechnologies(), 25, 100);
                        for (String tech : techs) {
                            registerVerifiedSkill(verifiedSkills, tech);
                            appendText(corpus, tech);
                        }
                        appendText(corpus, proj.getDescription());
                        registerSkillsExtractedFromText(verifiedSkills, proj.getDescription());

                        String key = normalizeTextKey(name);
                        verifiedProjects.putIfAbsent(key, new VerifiedProject(
                                name,
                                cleanProse(proj.getDescription(), 1500),
                                extractOriginalBullets(proj.getDescription()),
                                techs,
                                cleanSingleLine(proj.getUrl(), 255)
                        ));
                    }
                }

                if (parsed.getLanguages() != null) {
                    for (String lang : sanitizeDistinctList(parsed.getLanguages(), MAX_LANGUAGES, 100)) {
                        languages.add(lang);
                        appendText(corpus, lang);
                    }
                }

                if (parsed.getCertifications() != null) {
                    for (String cert : sanitizeDistinctList(parsed.getCertifications(), MAX_CERTIFICATIONS, 150)) {
                        certifications.add(cert);
                        appendText(corpus, cert);
                        registerSkillsExtractedFromText(verifiedSkills, cert);
                    }
                }
            }
        }

        if (header != null) {
            registerSkillsExtractedFromText(verifiedSkills, header.bio());
            registerSkillsExtractedFromText(verifiedSkills, header.currentDesignation());
        }

        mergedExperiences.sort(Comparator.comparing(
                VerifiedExperience::startDate,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        String defaultDesignation = header != null && hasText(header.currentDesignation())
                ? header.currentDesignation()
                : parsedDesignation;
        String fallbackSummary = header != null && hasText(header.bio())
                ? header.bio()
                : parsedSummary;

        return new CandidateGroundTruth(
                verifiedSkills,
                List.copyOf(mergedExperiences),
                Map.copyOf(verifiedProjects),
                List.copyOf(mergedEducations),
                List.copyOf(languages),
                List.copyOf(certifications),
                corpus.toString().toLowerCase(Locale.ROOT),
                defaultDesignation,
                fallbackSummary
        );
    }

    private List<TailoredResumeExperienceDto> validateAiExperiences(
            List<RawAiTailoredResumePayload.RawOptimizedExperience> rawExperiences,
            CandidateGroundTruth groundTruth,
            LinkedHashSet<String> warnings
    ) {
        List<VerifiedExperience> verifiedPool = groundTruth.experiences();
        if (verifiedPool.isEmpty()) {
            if (rawExperiences != null && !rawExperiences.isEmpty()) {
                addWarning(warnings, "Stripped AI-generated work experiences because no verified work history exists on candidate profile or master CV.");
            }
            return List.of();
        }

        if (rawExperiences == null || rawExperiences.isEmpty()) {
            addWarning(warnings, "AI omitted work experiences; populated verified candidate roles and original bullets.");
            return verifiedPool.stream()
                    .limit(MAX_EXPERIENCES)
                    .map(v -> toDefaultExperienceDto(v, List.of()))
                    .toList();
        }

        List<TailoredResumeExperienceDto> validated = new ArrayList<>();
        Set<Integer> matchedIndices = new LinkedHashSet<>();

        for (RawAiTailoredResumePayload.RawOptimizedExperience item : rawExperiences) {
            if (item == null) {
                continue;
            }
            String rawCompany = cleanSingleLine(item.getCompanyName(), 200);
            String rawDesignation = cleanSingleLine(item.getDesignation(), 150);
            if (rawCompany == null && rawDesignation == null) {
                continue;
            }

            int matchIndex = findBestVerifiedExperienceIndex(rawCompany, rawDesignation, verifiedPool, matchedIndices);
            if (matchIndex < 0) {
                String label = rawCompany != null ? rawCompany : rawDesignation;
                addWarning(warnings, "Removed unverified work experience entry '" + label
                        + "' because it is absent from verified candidate history.");
                continue;
            }

            matchedIndices.add(matchIndex);
            VerifiedExperience verified = verifiedPool.get(matchIndex);

            List<String> tailoredBullets = sanitizeBulletList(item.getTailoredBullets(), MAX_BULLETS_PER_EXP, 600);
            if (tailoredBullets.isEmpty()) {
                tailoredBullets = verified.originalBullets().stream().limit(5).toList();
                if (!tailoredBullets.isEmpty()) {
                    addWarning(warnings, "Restored original verified bullets for " + verified.designation()
                            + " at " + verified.companyName() + ".");
                }
            }

            List<String> expMatchedKeywords = filterVerifiedSkills(item.getMatchedKeywords(), groundTruth, 25);
            Integer relevance = clampRelevance(item.getRelevanceScore());

            validated.add(TailoredResumeExperienceDto.builder()
                    .companyName(verified.companyName())
                    .designation(verified.designation())
                    .location(verified.location())
                    .startDate(verified.startDate())
                    .endDate(verified.endDate())
                    .current(verified.current())
                    .originalBullets(verified.originalBullets())
                    .tailoredBullets(tailoredBullets)
                    .matchedKeywords(expMatchedKeywords)
                    .relevanceScore(relevance)
                    .build());

            if (validated.size() >= MAX_EXPERIENCES) {
                break;
            }
        }

        if (validated.isEmpty()) {
            throw new AiResponseValidationException(
                    "AI tailored resume failed grounding verification: none of the returned work experiences matched verified candidate employers."
            );
        }

        return List.copyOf(validated);
    }

    private List<TailoredResumeProjectDto> validateAiProjects(
            List<RawAiTailoredResumePayload.RawTailoredProject> rawProjects,
            CandidateGroundTruth groundTruth,
            LinkedHashSet<String> warnings
    ) {
        if (groundTruth.projects().isEmpty()) {
            if (rawProjects != null && !rawProjects.isEmpty()) {
                addWarning(warnings, "Stripped AI-generated projects because no verified projects exist on candidate master CV.");
            }
            return List.of();
        }

        if (rawProjects == null || rawProjects.isEmpty()) {
            return groundTruth.projects().values().stream()
                    .limit(MAX_PROJECTS)
                    .map(vp -> TailoredResumeProjectDto.builder()
                            .name(vp.name())
                            .description(vp.description())
                            .tailoredBullets(vp.originalBullets().stream().limit(3).toList())
                            .technologies(vp.technologies())
                            .url(vp.url())
                            .build())
                    .toList();
        }

        List<TailoredResumeProjectDto> result = new ArrayList<>();
        Set<String> matchedKeys = new LinkedHashSet<>();

        for (RawAiTailoredResumePayload.RawTailoredProject rp : rawProjects) {
            if (rp == null) {
                continue;
            }
            String rawName = cleanSingleLine(rp.getName(), 150);
            if (rawName == null) {
                continue;
            }
            VerifiedProject vp = findVerifiedProject(rawName, groundTruth.projects(), matchedKeys);
            if (vp == null) {
                addWarning(warnings, "Removed unverified project '" + rawName
                        + "' because it is absent from verified master CV projects.");
                continue;
            }
            matchedKeys.add(normalizeTextKey(vp.name()));

            String desc = cleanProse(rp.getDescription(), 1500);
            if (desc == null) {
                desc = vp.description();
            }
            List<String> bullets = sanitizeBulletList(rp.getTailoredBullets(), MAX_BULLETS_PER_PROJECT, 500);
            if (bullets.isEmpty() && !vp.originalBullets().isEmpty()) {
                bullets = vp.originalBullets().stream().limit(3).toList();
            }
            List<String> techs = filterVerifiedSkills(rp.getTechnologies(), groundTruth, 25);
            if (techs.isEmpty()) {
                techs = vp.technologies();
            }

            result.add(TailoredResumeProjectDto.builder()
                    .name(vp.name())
                    .description(desc)
                    .tailoredBullets(bullets)
                    .technologies(techs)
                    .url(vp.url())
                    .build());

            if (result.size() >= MAX_PROJECTS) {
                break;
            }
        }

        return List.copyOf(result);
    }

    private SkillPartitionResult partitionAndVerifySkills(
            List<String> rawHighlighted,
            List<String> rawAdditional,
            CandidateGroundTruth groundTruth,
            LinkedHashSet<String> warnings
    ) {
        Map<String, String> highlightedMap = new LinkedHashMap<>();
        Map<String, String> additionalMap = new LinkedHashMap<>();

        if (rawHighlighted != null) {
            for (String item : rawHighlighted) {
                String cleaned = cleanSingleLine(item, 100);
                if (cleaned == null) {
                    continue;
                }
                if (!isSkillVerified(cleaned, groundTruth)) {
                    addWarning(warnings, "Removed unverified skill '" + cleaned
                            + "' from highlighted skills because it is absent from candidate evidence.");
                    continue;
                }
                String key = canonicalKey(cleaned);
                if (!key.isBlank() && highlightedMap.size() < MAX_HIGHLIGHTED_SKILLS) {
                    highlightedMap.putIfAbsent(key, cleaned);
                }
            }
        }

        if (rawAdditional != null) {
            for (String item : rawAdditional) {
                String cleaned = cleanSingleLine(item, 100);
                if (cleaned == null) {
                    continue;
                }
                if (!isSkillVerified(cleaned, groundTruth)) {
                    addWarning(warnings, "Removed unverified skill '" + cleaned
                            + "' from additional skills because it is absent from candidate evidence.");
                    continue;
                }
                String key = canonicalKey(cleaned);
                if (!key.isBlank() && !highlightedMap.containsKey(key) && additionalMap.size() < MAX_ADDITIONAL_SKILLS) {
                    additionalMap.putIfAbsent(key, cleaned);
                }
            }
        }

        // Preserve any remaining verified skills not already surfaced so the resume never silently drops candidate skills
        for (Map.Entry<String, String> entry : groundTruth.verifiedSkills().entrySet()) {
            if (!highlightedMap.containsKey(entry.getKey()) && !additionalMap.containsKey(entry.getKey())) {
                if (highlightedMap.isEmpty() && highlightedMap.size() < 12) {
                    highlightedMap.put(entry.getKey(), entry.getValue());
                } else if (additionalMap.size() < MAX_ADDITIONAL_SKILLS) {
                    additionalMap.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return new SkillPartitionResult(
                List.copyOf(highlightedMap.values()),
                List.copyOf(additionalMap.values())
        );
    }

    private TailoredResumeHeaderDto buildLockedHeader(
            TailoredResumeContextSnapshot snapshot,
            CandidateGroundTruth groundTruth,
            String headline
    ) {
        HeaderEvidence h = snapshot.header();
        ParsedResumeProfileDto parsedProfile = (snapshot.masterResume() != null
                && snapshot.masterResume().parsedResume() != null)
                ? snapshot.masterResume().parsedResume().getProfile()
                : null;

        String fullName = firstNonBlank(
                h != null ? h.fullName() : null,
                parsedProfile != null ? parsedProfile.getFullName() : null
        );
        String email = h != null ? cleanSingleLine(h.email(), 150) : null;
        String city = firstNonBlank(
                h != null ? h.currentCity() : null,
                parsedProfile != null ? parsedProfile.getCurrentCity() : null
        );
        String country = firstNonBlank(
                h != null ? h.currentCountry() : null,
                parsedProfile != null ? parsedProfile.getCurrentCountry() : null
        );
        String location = joinLocation(city, country);
        BigDecimal years = (h != null && h.yearsOfExperience() != null)
                ? h.yearsOfExperience()
                : (parsedProfile != null ? parsedProfile.getYearsOfExperience() : null);

        String authSummary = buildWorkAuthorizationSummary(
                h != null ? h.workAuthorizationStatus() : null,
                h != null ? h.relocationPreference() : null
        );

        String resolvedHeadline = firstNonBlank(headline, groundTruth.defaultDesignation());

        return TailoredResumeHeaderDto.builder()
                .fullName(fullName)
                .headline(cleanSingleLine(resolvedHeadline, 150))
                .email(email)
                .location(cleanSingleLine(location, 150))
                .country(cleanSingleLine(country, 100))
                .yearsOfExperience(years)
                .workAuthorizationSummary(cleanSingleLine(authSummary, 120))
                .linkedinUrl(firstNonBlank(h != null ? h.linkedinUrl() : null, parsedProfile != null ? parsedProfile.getLinkedinUrl() : null))
                .githubUrl(firstNonBlank(h != null ? h.githubUrl() : null, parsedProfile != null ? parsedProfile.getGithubUrl() : null))
                .portfolioUrl(firstNonBlank(h != null ? h.portfolioUrl() : null, parsedProfile != null ? parsedProfile.getPortfolioUrl() : null))
                .build();
    }

    private AtsCoverageResult computeAtsCoverage(
            TailoredResumeDataDto draft,
            TailoredResumeContextSnapshot snapshot,
            CandidateGroundTruth groundTruth,
            List<String> aiReportedMissingKeywords,
            boolean enforceCandidateVerification,
            LinkedHashSet<String> warnings
    ) {
        Map<String, String> jdKeywords = collectTargetJdKeywords(snapshot);
        if (jdKeywords.isEmpty()) {
            addWarning(warnings, "Target job has no extracted technical keywords; ATS keyword coverage defaults to 0%.");
            List<String> extraMissing = sanitizeDistinctList(aiReportedMissingKeywords, MAX_MISSING_KEYWORDS, 100);
            return new AtsCoverageResult(List.of(), extraMissing, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        String draftCorpus = buildDraftCorpus(draft);
        Set<String> draftCanonicalSkills = extractDraftCanonicalSkills(draft, draftCorpus);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> missingKeys = new LinkedHashSet<>();

        for (Map.Entry<String, String> jdEntry : jdKeywords.entrySet()) {
            String jdKey = jdEntry.getKey();
            String jdDisplay = jdEntry.getValue();

            boolean presentInDraft = matchingEngine.skillSetSatisfies(draftCanonicalSkills, jdKey)
                    || containsBoundaryKeyword(draftCorpus, jdDisplay)
                    || (!jdKey.equals(jdDisplay.toLowerCase(Locale.ROOT)) && containsBoundaryKeyword(draftCorpus, jdKey));

            if (enforceCandidateVerification) {
                boolean verifiedInCandidate = isCanonicalOrTextSkillVerified(jdKey, jdDisplay, groundTruth);
                if (presentInDraft && verifiedInCandidate) {
                    if (matched.size() < MAX_MATCHED_KEYWORDS) {
                        matched.add(jdDisplay);
                    }
                } else {
                    if (!verifiedInCandidate && presentInDraft) {
                        addWarning(warnings, "Grounding notice: draft text referenced unverified JD keyword '"
                                + jdDisplay + "'; excluded from ATS credit and placed in missingKeywords.");
                    }
                    if (missing.size() < MAX_MISSING_KEYWORDS && missingKeys.add(jdKey)) {
                        missing.add(jdDisplay);
                    }
                }
            } else {
                if (presentInDraft) {
                    if (matched.size() < MAX_MATCHED_KEYWORDS) {
                        matched.add(jdDisplay);
                    }
                } else {
                    if (missing.size() < MAX_MISSING_KEYWORDS && missingKeys.add(jdKey)) {
                        missing.add(jdDisplay);
                    }
                }
            }
        }

        if (enforceCandidateVerification && aiReportedMissingKeywords != null) {
            for (String item : aiReportedMissingKeywords) {
                String cleaned = cleanSingleLine(item, 100);
                if (cleaned == null) {
                    continue;
                }
                String key = canonicalKey(cleaned);
                if (!key.isBlank()
                        && !isCanonicalOrTextSkillVerified(key, cleaned, groundTruth)
                        && missingKeys.add(key)
                        && missing.size() < MAX_MISSING_KEYWORDS) {
                    missing.add(cleaned);
                }
            }
        }

        double pct = (matched.size() * 100.0) / jdKeywords.size();
        BigDecimal coverage = BigDecimal.valueOf(Math.max(0.0, Math.min(100.0, pct)))
                .setScale(2, RoundingMode.HALF_UP);

        return new AtsCoverageResult(List.copyOf(matched), List.copyOf(missing), coverage);
    }

    private Map<String, String> collectTargetJdKeywords(TailoredResumeContextSnapshot snapshot) {
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

    private void addJdKeyword(Map<String, String> jdKeywords, String rawSkill) {
        String cleaned = cleanSingleLine(rawSkill, 100);
        if (cleaned == null) {
            return;
        }
        String key = canonicalKey(cleaned);
        if (!key.isBlank()) {
            jdKeywords.putIfAbsent(key, cleaned);
        }
    }

    private String buildDraftCorpus(TailoredResumeDataDto draft) {
        StringBuilder sb = new StringBuilder();
        if (draft.getHeader() != null) {
            appendText(sb, draft.getHeader().getHeadline());
        }
        appendText(sb, draft.getTailoredSummary());
        if (draft.getHighlightedSkills() != null) {
            draft.getHighlightedSkills().forEach(s -> appendText(sb, s));
        }
        if (draft.getAdditionalSkills() != null) {
            draft.getAdditionalSkills().forEach(s -> appendText(sb, s));
        }
        if (draft.getOptimizedExperiences() != null) {
            for (TailoredResumeExperienceDto exp : draft.getOptimizedExperiences()) {
                if (exp != null) {
                    appendText(sb, exp.getDesignation());
                    if (exp.getTailoredBullets() != null) {
                        exp.getTailoredBullets().forEach(b -> appendText(sb, b));
                    }
                    if (exp.getMatchedKeywords() != null) {
                        exp.getMatchedKeywords().forEach(k -> appendText(sb, k));
                    }
                }
            }
        }
        if (draft.getProjects() != null) {
            for (TailoredResumeProjectDto proj : draft.getProjects()) {
                if (proj != null) {
                    appendText(sb, proj.getDescription());
                    if (proj.getTailoredBullets() != null) {
                        proj.getTailoredBullets().forEach(b -> appendText(sb, b));
                    }
                    if (proj.getTechnologies() != null) {
                        proj.getTechnologies().forEach(t -> appendText(sb, t));
                    }
                }
            }
        }
        if (draft.getCertifications() != null) {
            draft.getCertifications().forEach(c -> appendText(sb, c));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private Set<String> extractDraftCanonicalSkills(TailoredResumeDataDto draft, String draftCorpus) {
        Set<String> canonical = new LinkedHashSet<>(matchingEngine.extractSkillsFromText(draftCorpus).keySet());
        if (draft.getHighlightedSkills() != null) {
            for (String s : draft.getHighlightedSkills()) {
                String k = canonicalKey(s);
                if (!k.isBlank()) {
                    canonical.add(k);
                }
            }
        }
        if (draft.getAdditionalSkills() != null) {
            for (String s : draft.getAdditionalSkills()) {
                String k = canonicalKey(s);
                if (!k.isBlank()) {
                    canonical.add(k);
                }
            }
        }
        if (draft.getProjects() != null) {
            for (TailoredResumeProjectDto p : draft.getProjects()) {
                if (p != null && p.getTechnologies() != null) {
                    for (String t : p.getTechnologies()) {
                        String k = canonicalKey(t);
                        if (!k.isBlank()) {
                            canonical.add(k);
                        }
                    }
                }
            }
        }
        return canonical;
    }

    private List<TailoredResumeExperienceDto> sanitizeEditedExperiences(
            List<TailoredResumeExperienceDto> edited,
            TailoredResumeDataDto existingData,
            CandidateGroundTruth groundTruth
    ) {
        List<TailoredResumeExperienceDto> result = new ArrayList<>();
        for (TailoredResumeExperienceDto item : edited) {
            if (item == null) {
                continue;
            }
            String company = cleanSingleLine(item.getCompanyName(), 200);
            String designation = cleanSingleLine(item.getDesignation(), 150);
            if (company == null || designation == null) {
                continue;
            }

            List<String> origBullets = item.getOriginalBullets() != null && !item.getOriginalBullets().isEmpty()
                    ? sanitizeBulletList(item.getOriginalBullets(), MAX_ORIG_BULLETS_PER_EXP, 600)
                    : findFallbackOriginalBullets(company, designation, existingData, groundTruth);

            List<String> tailoredBullets = sanitizeBulletList(item.getTailoredBullets(), MAX_BULLETS_PER_EXP, 600);
            List<String> matchedKeywords = sanitizeDistinctList(item.getMatchedKeywords(), 25, 100);

            result.add(TailoredResumeExperienceDto.builder()
                    .companyName(company)
                    .designation(designation)
                    .location(cleanSingleLine(item.getLocation(), 150))
                    .startDate(item.getStartDate())
                    .endDate(item.isCurrent() ? null : item.getEndDate())
                    .current(item.isCurrent())
                    .originalBullets(origBullets)
                    .tailoredBullets(tailoredBullets)
                    .matchedKeywords(matchedKeywords)
                    .relevanceScore(clampRelevance(item.getRelevanceScore()))
                    .build());

            if (result.size() >= MAX_EXPERIENCES) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private List<TailoredResumeProjectDto> sanitizeEditedProjects(List<TailoredResumeProjectDto> edited) {
        List<TailoredResumeProjectDto> result = new ArrayList<>();
        for (TailoredResumeProjectDto item : edited) {
            if (item == null) {
                continue;
            }
            String name = cleanSingleLine(item.getName(), 150);
            if (name == null) {
                continue;
            }
            result.add(TailoredResumeProjectDto.builder()
                    .name(name)
                    .description(cleanProse(item.getDescription(), 1500))
                    .tailoredBullets(sanitizeBulletList(item.getTailoredBullets(), MAX_BULLETS_PER_PROJECT, 500))
                    .technologies(sanitizeDistinctList(item.getTechnologies(), 25, 100))
                    .url(cleanSingleLine(item.getUrl(), 255))
                    .build());
            if (result.size() >= MAX_PROJECTS) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private List<TailoredResumeEducationDto> sanitizeEditedEducations(List<TailoredResumeEducationDto> edited) {
        List<TailoredResumeEducationDto> result = new ArrayList<>();
        for (TailoredResumeEducationDto item : edited) {
            if (item == null) {
                continue;
            }
            String inst = cleanSingleLine(item.getInstitution(), 200);
            String deg = cleanSingleLine(item.getDegree(), 150);
            String field = cleanSingleLine(item.getFieldOfStudy(), 150);
            if (inst == null || deg == null || field == null) {
                continue;
            }
            result.add(TailoredResumeEducationDto.builder()
                    .institution(inst)
                    .degree(deg)
                    .fieldOfStudy(field)
                    .startDate(item.getStartDate())
                    .endDate(item.getEndDate())
                    .gradeOrGpa(cleanSingleLine(item.getGradeOrGpa(), 50))
                    .country(cleanSingleLine(item.getCountry(), 100))
                    .build());
            if (result.size() >= MAX_EDUCATIONS) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private List<String> findFallbackOriginalBullets(
            String company,
            String designation,
            TailoredResumeDataDto existingData,
            CandidateGroundTruth groundTruth
    ) {
        String compKey = normalizeCompanyKey(company);
        if (existingData != null && existingData.getOptimizedExperiences() != null) {
            for (TailoredResumeExperienceDto ex : existingData.getOptimizedExperiences()) {
                if (ex != null && normalizeCompanyKey(ex.getCompanyName()).equals(compKey)
                        && ex.getOriginalBullets() != null && !ex.getOriginalBullets().isEmpty()) {
                    return List.copyOf(ex.getOriginalBullets());
                }
            }
        }
        for (VerifiedExperience ve : groundTruth.experiences()) {
            if (normalizeCompanyKey(ve.companyName()).equals(compKey)
                    && (normalizeTextKey(ve.designation()).equals(normalizeTextKey(designation)) || ve.originalBullets() != null)) {
                return ve.originalBullets();
            }
        }
        return List.of();
    }

    private TailoredResumeHeaderDto copyHeader(TailoredResumeHeaderDto source) {
        return TailoredResumeHeaderDto.builder()
                .fullName(source.getFullName())
                .headline(source.getHeadline())
                .email(source.getEmail())
                .location(source.getLocation())
                .country(source.getCountry())
                .yearsOfExperience(source.getYearsOfExperience())
                .workAuthorizationSummary(source.getWorkAuthorizationSummary())
                .linkedinUrl(source.getLinkedinUrl())
                .githubUrl(source.getGithubUrl())
                .portfolioUrl(source.getPortfolioUrl())
                .build();
    }

    private int findBestVerifiedExperienceIndex(
            String rawCompany,
            String rawDesignation,
            List<VerifiedExperience> verifiedPool,
            Set<Integer> alreadyMatched
    ) {
        String targetComp = normalizeCompanyKey(rawCompany);
        String targetDesig = normalizeTextKey(rawDesignation);
        if (targetComp.isEmpty()) {
            return -1;
        }

        int bestIdx = -1;
        int bestScore = -1;
        for (int i = 0; i < verifiedPool.size(); i++) {
            VerifiedExperience v = verifiedPool.get(i);
            String candComp = normalizeCompanyKey(v.companyName());
            if (candComp.isEmpty()) {
                continue;
            }
            boolean compMatch = candComp.equals(targetComp)
                    || (candComp.length() >= 4 && targetComp.length() >= 4
                    && (candComp.contains(targetComp) || targetComp.contains(candComp)));
            if (!compMatch) {
                continue;
            }

            int score = candComp.equals(targetComp) ? 100 : 60;
            String candDesig = normalizeTextKey(v.designation());
            if (!targetDesig.isEmpty() && candDesig.equals(targetDesig)) {
                score += 40;
            } else if (!targetDesig.isEmpty() && (candDesig.contains(targetDesig) || targetDesig.contains(candDesig))) {
                score += 20;
            }
            if (!alreadyMatched.contains(i)) {
                score += 10;
            }
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private VerifiedProject findVerifiedProject(
            String rawName,
            Map<String, VerifiedProject> verifiedProjects,
            Set<String> matchedKeys
    ) {
        String targetKey = normalizeTextKey(rawName);
        if (targetKey.isEmpty()) {
            return null;
        }
        if (verifiedProjects.containsKey(targetKey)) {
            return verifiedProjects.get(targetKey);
        }
        for (Map.Entry<String, VerifiedProject> entry : verifiedProjects.entrySet()) {
            String key = entry.getKey();
            if (!matchedKeys.contains(key) && key.length() >= 4 && targetKey.length() >= 4
                    && (key.contains(targetKey) || targetKey.contains(key))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private TailoredResumeExperienceDto toDefaultExperienceDto(
            VerifiedExperience v,
            List<String> matchedKeywords
    ) {
        return TailoredResumeExperienceDto.builder()
                .companyName(v.companyName())
                .designation(v.designation())
                .location(v.location())
                .startDate(v.startDate())
                .endDate(v.endDate())
                .current(v.current())
                .originalBullets(v.originalBullets())
                .tailoredBullets(v.originalBullets().stream().limit(5).toList())
                .matchedKeywords(matchedKeywords)
                .relevanceScore(null)
                .build();
    }

    private List<String> filterVerifiedSkills(
            List<String> rawSkills,
            CandidateGroundTruth groundTruth,
            int maxItems
    ) {
        if (rawSkills == null || rawSkills.isEmpty()) {
            return List.of();
        }
        Map<String, String> verified = new LinkedHashMap<>();
        for (String item : rawSkills) {
            String cleaned = cleanSingleLine(item, 100);
            if (cleaned != null && isSkillVerified(cleaned, groundTruth)) {
                String key = canonicalKey(cleaned);
                if (!key.isBlank() && verified.size() < maxItems) {
                    verified.putIfAbsent(key, cleaned);
                }
            }
        }
        return List.copyOf(verified.values());
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

    private List<String> extractOriginalBullets(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        String normalized = description.replace("\r\n", "\n").trim();
        String[] rawLines = normalized.split("\\n+");
        List<String> bullets = new ArrayList<>();
        for (String line : rawLines) {
            String stripped = LEADING_BULLET_MARKER.matcher(line.trim()).replaceFirst("");
            String cleaned = cleanSingleLine(stripped, 600);
            if (cleaned != null) {
                bullets.add(cleaned);
                if (bullets.size() >= MAX_ORIG_BULLETS_PER_EXP) {
                    break;
                }
            }
        }
        return List.copyOf(bullets);
    }

    private List<String> sanitizeBulletList(List<String> rawBullets, int maxKeep, int maxLen) {
        if (rawBullets == null || rawBullets.isEmpty()) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String b : rawBullets) {
            if (b == null || b.isBlank()) {
                continue;
            }
            String stripped = LEADING_BULLET_MARKER.matcher(b.trim()).replaceFirst("");
            String cleaned = cleanSingleLine(stripped, maxLen);
            if (cleaned != null) {
                unique.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
                if (unique.size() >= maxKeep) {
                    break;
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private List<String> sanitizeDistinctList(List<String> rawItems, int maxKeep, int maxLen) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String item : rawItems) {
            String cleaned = cleanSingleLine(item, maxLen);
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

    private String normalizeCompanyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String withoutLegal = LEGAL_SUFFIX_PATTERN.matcher(raw).replaceAll(" ");
        return normalizeTextKey(withoutLegal);
    }

    private String normalizeTextKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String experienceIdentityKey(String company, String designation, LocalDate startDate) {
        return normalizeCompanyKey(company) + "|" + normalizeTextKey(designation)
                + "|" + (startDate != null ? startDate.toString() : "none");
    }

    private String educationKey(String institution, String degree, String field) {
        return normalizeTextKey(institution) + "|" + normalizeTextKey(degree) + "|" + normalizeTextKey(field);
    }

    private Integer clampRelevance(Integer rawScore) {
        if (rawScore == null) {
            return null;
        }
        return Math.max(0, Math.min(100, rawScore));
    }

    private String buildWorkAuthorizationSummary(String workAuthStatus, String relocationPref) {
        List<String> parts = new ArrayList<>();
        if (hasText(workAuthStatus)) {
            switch (workAuthStatus.trim()) {
                case "EU_CITIZEN" -> parts.add("EU/EEA Citizen (Unrestricted EU Work Authorization)");
                case "BLUE_CARD_HOLDER" -> parts.add("EU Blue Card Holder");
                case "PERMANENT_RESIDENT" -> parts.add("EU Permanent Resident");
                case "NEEDS_SPONSORSHIP" -> parts.add("Requires Visa Sponsorship");
                default -> parts.add(workAuthStatus.trim().replace('_', ' '));
            }
        }
        if (hasText(relocationPref)) {
            switch (relocationPref.trim()) {
                case "ALREADY_IN_EUROPE" -> parts.add("Based in Europe");
                case "WILLING_TO_RELOCATE" -> parts.add("Open to relocation");
                case "RELOCATING_SOON" -> parts.add("Relocating to Europe soon");
                case "REMOTE_ONLY" -> parts.add("Remote preference");
                case "NOT_INTERESTED" -> parts.add("Non-relocation preference");
                default -> {
                }
            }
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
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

    private String cleanSingleLine(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String collapsed = INLINE_WHITESPACE.matcher(raw).replaceAll(" ").trim();
        return collapsed.isEmpty() ? null : truncate(collapsed, maxLength);
    }

    private String cleanProse(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace("\r\n", "\n").trim();
        normalized = EXCESS_NEWLINES.matcher(normalized).replaceAll("\n\n");
        return normalized.isEmpty() ? null : truncate(normalized, maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CandidateGroundTruth(
            Map<String, String> verifiedSkills,
            List<VerifiedExperience> experiences,
            Map<String, VerifiedProject> projects,
            List<TailoredResumeEducationDto> educations,
            List<String> languages,
            List<String> certifications,
            String candidateCorpusLower,
            String defaultDesignation,
            String fallbackSummary
    ) {
    }

    private record VerifiedExperience(
            String companyName,
            String designation,
            String location,
            LocalDate startDate,
            LocalDate endDate,
            boolean current,
            List<String> originalBullets
    ) {
    }

    private record VerifiedProject(
            String name,
            String description,
            List<String> originalBullets,
            List<String> technologies,
            String url
    ) {
    }

    private record SkillPartitionResult(
            List<String> highlighted,
            List<String> additional
    ) {
    }

    private record AtsCoverageResult(
            List<String> matchedKeywords,
            List<String> missingKeywords,
            BigDecimal coveragePercentage
    ) {
    }
}

