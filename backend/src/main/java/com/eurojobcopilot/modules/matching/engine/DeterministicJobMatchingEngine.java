package com.eurojobcopilot.modules.matching.engine;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.matching.config.MatchingProperties;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure deterministic rule-based job matching engine computing configurable weighted compatibility
 * scores (0..100) across technical skills, experience, job title, location, sponsorship, and salary.
 *
 * <p>Missing candidate or job data is handled fairly and is never assumed to be a positive match.
 */
@Component
@RequiredArgsConstructor
public class DeterministicJobMatchingEngine {

    private static final double MIN_SCORE = 0.0;
    private static final double MAX_SCORE = 100.0;

    private static final Map<String, String> SKILL_ALIASES = buildSkillAliases();
    private static final Map<Pattern, String> DESCRIPTION_SKILL_PATTERNS = buildDescriptionSkillPatterns();
    private static final Map<String, Set<String>> PARENT_SKILL_SATISFIERS = Map.of(
            "sql", Set.of("sql", "postgresql", "mysql", "mariadb", "oracle", "mssql", "sqlite"),
            "spring", Set.of("spring", "spring boot", "spring framework"),
            "ci/cd", Set.of("ci/cd", "github actions", "gitlab ci", "jenkins", "circleci"),
            "cloud", Set.of("cloud", "aws", "azure", "gcp")
    );

    private static final Set<String> SENIORITY_TOKENS = Set.of(
            "entry", "graduate", "intern", "junior", "mid", "senior", "lead", "principal", "staff"
    );
    private static final Set<String> TITLE_STOP_WORDS = Set.of(
            "and", "or", "with", "the", "in", "at", "for", "of", "to", "a", "an", "on"
    );
    private static final Set<String> GENERIC_ROLE_TOKENS = Set.of(
            "engineer", "programmer", "specialist", "consultant", "associate"
    );
    private static final Map<String, Double> EUR_FX_RATES = Map.of(
            "EUR", 1.0,
            "GBP", 1.17,
            "CHF", 1.05,
            "USD", 0.92,
            "SEK", 0.088,
            "DKK", 0.134,
            "NOK", 0.087,
            "PLN", 0.23,
            "CZK", 0.04
    );
    private static final Map<String, String> CITY_ALIASES = buildCityAliases();

    private final CanonicalHashGenerator canonicalHashGenerator;
    private final JobNormalizer jobNormalizer;
    private final MatchingProperties matchingProperties;

    public DeterministicMatchResult evaluate(CandidateMatchContext context, Job job, Company resolvedCompany) {
        return evaluate(context, job, resolvedCompany, null);
    }

    public DeterministicMatchResult evaluate(
            CandidateMatchContext context,
            Job job,
            Company resolvedCompany,
            BigDecimal existingAiScore
    ) {
        Objects.requireNonNull(job, "Job must not be null");
        CandidateMatchContext safeContext = context != null ? context : CandidateMatchContext.empty();
        MatchingProperties.Weights weights = validateWeights();

        List<String> positiveReasons = new ArrayList<>();
        List<String> gapReasons = new ArrayList<>();

        SkillEvaluation skillEval = evaluateSkills(safeContext, job, positiveReasons, gapReasons);
        ExperienceEvaluation expEval = evaluateExperience(safeContext, job, positiveReasons, gapReasons);
        double titleScore = evaluateTitle(safeContext, job, positiveReasons, gapReasons);
        double locationScore = evaluateLocation(safeContext, job, positiveReasons, gapReasons);
        double sponsorshipScore = evaluateSponsorship(safeContext, job, resolvedCompany, positiveReasons, gapReasons);
        double salaryScore = evaluateSalary(safeContext, job, positiveReasons, gapReasons);

        double totalWeight = weights.totalWeight();
        double weightedComposite = (
                skillEval.score() * weights.getTechnicalSkills()
                        + expEval.score() * weights.getExperience()
                        + titleScore * weights.getJobTitle()
                        + locationScore * weights.getLocation()
                        + sponsorshipScore * weights.getSponsorship()
                        + salaryScore * weights.getSalary()
        ) / totalWeight;

        BigDecimal deterministicScore = toBoundedDecimal(weightedComposite);
        BigDecimal finalScore = computeFinalScore(deterministicScore, existingAiScore);
        int matchPercentage = clampPercentage(finalScore);

        List<String> combinedReasons = new ArrayList<>(positiveReasons.size() + gapReasons.size());
        combinedReasons.addAll(positiveReasons);
        combinedReasons.addAll(gapReasons);

        return new DeterministicMatchResult(
                deterministicScore,
                finalScore,
                matchPercentage,
                toBoundedDecimal(skillEval.score()),
                toBoundedDecimal(expEval.score()),
                toBoundedDecimal(titleScore),
                toBoundedDecimal(locationScore),
                toBoundedDecimal(sponsorshipScore),
                toBoundedDecimal(salaryScore),
                skillEval.matchingSkills(),
                skillEval.missingSkills(),
                expEval.compatibility(),
                List.copyOf(combinedReasons)
        );
    }

    private MatchingProperties.Weights validateWeights() {
        MatchingProperties.Weights weights = matchingProperties != null ? matchingProperties.getWeights() : null;
        if (weights == null) {
            throw new IllegalStateException("Matching weights configuration must not be null");
        }
        if (weights.getTechnicalSkills() < 0 || weights.getExperience() < 0 || weights.getJobTitle() < 0
                || weights.getLocation() < 0 || weights.getSponsorship() < 0 || weights.getSalary() < 0) {
            throw new IllegalStateException("Matching weights must be non-negative");
        }
        if (weights.totalWeight() <= 0.0) {
            throw new IllegalStateException("Sum of matching weights must be strictly positive");
        }
        return weights;
    }

    private SkillEvaluation evaluateSkills(
            CandidateMatchContext context,
            Job job,
            List<String> positiveReasons,
            List<String> gapReasons
    ) {
        Map<String, String> jobRequiredSkills = extractJobSkills(job);
        if (jobRequiredSkills.isEmpty()) {
            gapReasons.add("Job posting specifies no verifiable technical skills; skill match cannot be assumed (0%).");
            return new SkillEvaluation(0.0, List.of(), List.of());
        }

        Set<String> candidateCanonicalSkills = extractCandidateCanonicalSkills(context);
        if (candidateCanonicalSkills.isEmpty()) {
            List<String> missingAll = List.copyOf(jobRequiredSkills.values());
            gapReasons.add("No technical skills listed in your profile; add skills to match "
                    + summarizeSkillNames(missingAll) + ".");
            return new SkillEvaluation(0.0, List.of(), missingAll);
        }

        List<String> matching = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (Map.Entry<String, String> entry : jobRequiredSkills.entrySet()) {
            String requiredKey = entry.getKey();
            String displayName = entry.getValue();
            if (candidateHasSkill(candidateCanonicalSkills, requiredKey)) {
                matching.add(displayName);
            } else {
                missing.add(displayName);
            }
        }

        double score = (matching.size() * 100.0) / jobRequiredSkills.size();
        if (matching.size() == jobRequiredSkills.size()) {
            positiveReasons.add("Matches all " + matching.size() + " required technical skills ("
                    + summarizeSkillNames(matching) + ").");
        } else if (!matching.isEmpty()) {
            positiveReasons.add("Matches " + matching.size() + " of " + jobRequiredSkills.size()
                    + " required skills (" + summarizeSkillNames(matching) + "); missing "
                    + summarizeSkillNames(missing) + ".");
        } else {
            gapReasons.add("None of the job's required skills (" + summarizeSkillNames(missing)
                    + ") match your profile.");
        }

        return new SkillEvaluation(clampScore(score), List.copyOf(matching), List.copyOf(missing));
    }

    private ExperienceEvaluation evaluateExperience(
            CandidateMatchContext context,
            Job job,
            List<String> positiveReasons,
            List<String> gapReasons
    ) {
        ExperienceRequirement requirement = resolveJobExperienceRequirement(job);
        if (requirement == null) {
            String verdict = "Missing Data · Job experience requirement not specified";
            gapReasons.add("Job posting omits both experience years and seniority level; experience cannot be assumed (0%).");
            return new ExperienceEvaluation(0.0, verdict);
        }

        Double candidateYears = resolveCandidateExperienceYears(context);
        if (candidateYears == null) {
            String verdict = "Missing Data · Candidate years of experience not set";
            gapReasons.add("Candidate experience is missing in your profile; experience compatibility scored 0%.");
            return new ExperienceEvaluation(0.0, verdict);
        }

        double reqMin = requirement.minYears();
        double maxComfort = requirement.maxComfortYears();
        String levelLabel = requirement.levelLabel();
        String levelSuffix = levelLabel.isEmpty() ? "" : " (" + levelLabel + ")";

        if (candidateYears >= reqMin) {
            if (candidateYears <= maxComfort) {
                String verdict = "Strong Fit · " + formatYears(candidateYears) + " yrs meets "
                        + formatYears(reqMin) + "+ yrs" + levelSuffix;
                positiveReasons.add("Experience strongly aligns: " + formatYears(candidateYears)
                        + " years meets the " + formatYears(reqMin) + "+ year expectation" + levelSuffix + ".");
                return new ExperienceEvaluation(100.0, verdict);
            }
            double excess = candidateYears - maxComfort;
            double score = Math.max(65.0, 95.0 - excess * 5.0);
            String verdict = "Overqualified · " + formatYears(candidateYears) + " yrs vs "
                    + formatYears(reqMin) + "–" + formatYears(maxComfort) + " yr range" + levelSuffix;
            positiveReasons.add("Candidate exceeds experience target (" + formatYears(candidateYears)
                    + " yrs for " + levelLabel + " role).");
            return new ExperienceEvaluation(clampScore(score), verdict);
        }

        double deficit = reqMin - candidateYears;
        if (deficit <= 1.0) {
            double score = 100.0 - 20.0 * deficit;
            String verdict = "Close Fit · " + formatYears(candidateYears) + " yrs vs "
                    + formatYears(reqMin) + " yrs required" + levelSuffix;
            positiveReasons.add("Close experience fit (" + formatYears(candidateYears) + " yrs vs "
                    + formatYears(reqMin) + " yrs required" + levelSuffix + ").");
            return new ExperienceEvaluation(clampScore(score), verdict);
        }
        if (deficit <= 2.5) {
            double score = 80.0 - 20.0 * (deficit - 1.0);
            String verdict = "Stretch Fit · " + formatYears(candidateYears) + " yrs vs "
                    + formatYears(reqMin) + " yrs required" + levelSuffix;
            gapReasons.add("Slightly below target experience (" + formatYears(candidateYears) + " yrs vs "
                    + formatYears(reqMin) + " yrs required" + levelSuffix + ").");
            return new ExperienceEvaluation(clampScore(score), verdict);
        }
        if (deficit <= 5.0) {
            double score = 50.0 - 14.0 * (deficit - 2.5);
            String verdict = "Underqualified · " + formatYears(candidateYears) + " yrs vs "
                    + formatYears(reqMin) + " yrs required" + levelSuffix;
            gapReasons.add("Experience gap detected: " + formatYears(candidateYears) + " yrs vs "
                    + formatYears(reqMin) + " yrs expected" + levelSuffix + ".");
            return new ExperienceEvaluation(clampScore(score), verdict);
        }

        double score = Math.max(0.0, 15.0 - 5.0 * (deficit - 5.0));
        String verdict = "Underqualified · Major gap (" + formatYears(candidateYears)
                + " vs " + formatYears(reqMin) + " yrs" + (levelLabel.isEmpty() ? "" : ", " + levelLabel) + ")";
        gapReasons.add("Significant experience gap: " + formatYears(candidateYears) + " yrs vs "
                + formatYears(reqMin) + "+ yrs required" + levelSuffix + ".");
        return new ExperienceEvaluation(clampScore(score), verdict);
    }

    private double evaluateTitle(
            CandidateMatchContext context,
            Job job,
            List<String> positiveReasons,
            List<String> gapReasons
    ) {
        if (job.getTitle() == null || job.getTitle().isBlank()) {
            gapReasons.add("Job title is missing; role alignment cannot be evaluated (0%).");
            return 0.0;
        }

        LinkedHashSet<String> candidateTitlesSet = new LinkedHashSet<>();
        UserJobPreference preference = context.preference();
        if (preference != null && preference.getPreferredJobTitles() != null) {
            for (String preferredTitle : preference.getPreferredJobTitles()) {
                addTitleVariants(candidateTitlesSet, preferredTitle);
            }
        }
        if (context.profile() != null) {
            addTitleVariants(candidateTitlesSet, context.profile().getCurrentDesignation());
        }
        List<String> candidateTitles = new ArrayList<>(candidateTitlesSet);

        if (candidateTitles.isEmpty()) {
            gapReasons.add("No preferred job titles or current designation configured; title match scored 0%.");
            return 0.0;
        }

        double bestScore = 0.0;
        String bestMatchedTitle = candidateTitles.get(0);
        for (String candidateTitle : candidateTitles) {
            double score = scoreSingleTitlePair(candidateTitle, job.getTitle());
            if (score > bestScore) {
                bestScore = score;
                bestMatchedTitle = candidateTitle;
            }
        }

        if (bestScore >= 75.0) {
            positiveReasons.add("Role title aligns well with target \"" + bestMatchedTitle + "\".");
        } else if (bestScore >= 40.0) {
            positiveReasons.add("Partial role title overlap with target \"" + bestMatchedTitle + "\".");
        } else {
            gapReasons.add("Job title \"" + job.getTitle() + "\" differs from your target roles ("
                    + String.join(", ", candidateTitles) + ").");
        }
        return clampScore(bestScore);
    }

    private double evaluateLocation(
            CandidateMatchContext context,
            Job job,
            List<String> positiveReasons,
            List<String> gapReasons
    ) {
        String jobCountry = job.getCountry() != null ? jobNormalizer.normalizeCountry(job.getCountry()) : "";
        if (jobCountry.isBlank()) {
            gapReasons.add("Job country is missing; location compatibility cannot be assumed (0%).");
            return 0.0;
        }
        String jobCityKey = normalizeCityKey(job.getCity());

        UserJobPreference preference = context.preference();
        UserProfile profile = context.profile();

        Set<String> targetCountries = new LinkedHashSet<>();
        if (preference != null && preference.getPreferredCountries() != null) {
            for (String rawCountry : preference.getPreferredCountries()) {
                String normalized = jobNormalizer.normalizeCountry(rawCountry);
                if (!normalized.isBlank()) {
                    targetCountries.add(normalized.toLowerCase(Locale.ROOT));
                }
            }
        }

        Set<String> targetCities = new LinkedHashSet<>();
        if (preference != null && preference.getPreferredCities() != null) {
            for (String rawCity : preference.getPreferredCities()) {
                String key = normalizeCityKey(rawCity);
                if (!key.isBlank()) {
                    targetCities.add(key);
                }
            }
        }

        boolean usedProfileCurrentLocationFallback = false;
        if (targetCountries.isEmpty() && targetCities.isEmpty() && profile != null) {
            if (profile.getCurrentCountry() != null && !profile.getCurrentCountry().isBlank()) {
                String homeCountry = jobNormalizer.normalizeCountry(profile.getCurrentCountry());
                if (!homeCountry.isBlank()) {
                    targetCountries.add(homeCountry.toLowerCase(Locale.ROOT));
                    usedProfileCurrentLocationFallback = true;
                }
            }
            if (profile.getCurrentCity() != null && !profile.getCurrentCity().isBlank()) {
                String homeCity = normalizeCityKey(profile.getCurrentCity());
                if (!homeCity.isBlank()) {
                    targetCities.add(homeCity);
                    usedProfileCurrentLocationFallback = true;
                }
            }
        }

        boolean hasGeographyPreferences = !targetCountries.isEmpty() || !targetCities.isEmpty();
        boolean hasExplicitArrangementPreference = (preference != null
                && preference.getWorkArrangement() != null
                && preference.getWorkArrangement() != WorkArrangement.ANY)
                || (profile != null && profile.getRelocationPreference() == RelocationPreference.REMOTE_ONLY);

        if (!hasGeographyPreferences && !hasExplicitArrangementPreference) {
            gapReasons.add("Preferred countries, cities, and work mode are missing; location match scored 0%.");
            return 0.0;
        }

        WorkArrangement candidateArrangement = resolveEffectiveArrangementPreference(preference, profile);
        double geoScore = scoreGeography(
                jobCountry,
                jobCityKey,
                job.getWorkArrangement(),
                targetCountries,
                targetCities,
                candidateArrangement
        );
        double arrangementScore = scoreWorkArrangement(
                job.getWorkArrangement(),
                candidateArrangement,
                hasGeographyPreferences || hasExplicitArrangementPreference
        );

        double totalLocationScore = geoScore + arrangementScore;

        if (profile != null && profile.getRelocationPreference() == RelocationPreference.NOT_INTERESTED
                && profile.getCurrentCountry() != null && !profile.getCurrentCountry().isBlank()) {
            String homeCountry = jobNormalizer.normalizeCountry(profile.getCurrentCountry()).toLowerCase(Locale.ROOT);
            boolean sameHomeCountry = homeCountry.equals(jobCountry.toLowerCase(Locale.ROOT));
            if (!sameHomeCountry && job.getWorkArrangement() != WorkArrangement.REMOTE) {
                totalLocationScore = Math.min(totalLocationScore, 20.0);
                gapReasons.add("Role in " + jobCountry + " requires relocation while your profile indicates not open to relocation.");
                return clampScore(totalLocationScore);
            }
        }

        if (totalLocationScore >= 80.0) {
            String placeLabel = job.getCity() != null && !job.getCity().isBlank()
                    ? job.getCity() + ", " + jobCountry
                    : jobCountry;
            String arrangementNote = job.getWorkArrangement() != null ? " (" + formatArrangement(job.getWorkArrangement()) + ")" : "";
            positiveReasons.add("Location matches your target hub: " + placeLabel + arrangementNote + ".");
        } else if (totalLocationScore >= 40.0) {
            positiveReasons.add("Partial location compatibility for " + jobCountry + ".");
        } else {
            String extra = usedProfileCurrentLocationFallback
                    ? " (set target European countries in Settings if relocating)"
                    : "";
            gapReasons.add("Location " + jobCountry + " does not match your preferred countries/cities or work arrangement" + extra + ".");
        }

        return clampScore(totalLocationScore);
    }

    private double evaluateSponsorship(
            CandidateMatchContext context,
            Job job,
            Company resolvedCompany,
            List<String> positiveReasons,
            List<String> gapReasons
    ) {
        UserProfile profile = context.profile();
        UserJobPreference preference = context.preference();

        if (profile == null && preference == null) {
            gapReasons.add("Work authorization and sponsorship preference are missing; sponsorship scored 0%.");
            return 0.0;
        }

        boolean needsSponsorship = doesCandidateNeedSponsorship(profile, preference);
        if (!needsSponsorship) {
            positiveReasons.add("Your existing EU work authorization satisfies this role without visa sponsorship dependency.");
            return 100.0;
        }

        JobSponsorStatus status = job.getSponsorStatus() != null
                ? job.getSponsorStatus()
                : JobSponsorStatus.UNCONFIRMED;
        boolean companyVerified = resolvedCompany != null && resolvedCompany.isVerifiedSponsor();

        if (status == JobSponsorStatus.NO_SPONSORSHIP) {
            gapReasons.add("Posting explicitly excludes visa sponsorship while your profile requires sponsorship (overrides company accreditation).");
            return 0.0;
        }
        if (status == JobSponsorStatus.VERIFIED_SPONSOR || companyVerified) {
            positiveReasons.add(hasExplicitVacancySponsorshipMention(job)
                    ? "Employer is a verified visa sponsor and vacancy text explicitly mentions sponsorship support."
                    : "Employer is a verified visa sponsor, though this vacancy does not explicitly mention sponsorship (verify role eligibility before applying).");
            return 100.0;
        }
        if (status == JobSponsorStatus.SPONSORSHIP_MENTIONED) {
            positiveReasons.add("Job posting mentions visa sponsorship / EU work permit pathways.");
            return 80.0;
        }
        if (status == JobSponsorStatus.RELOCATION_OFFERED) {
            positiveReasons.add("Employer offers relocation support (confirm visa sponsorship explicitly before applying).");
            return 55.0;
        }

        gapReasons.add("Visa sponsorship status is unconfirmed for this employer; scored conservatively at 0%.");
        return 0.0;
    }

    private double evaluateSalary(
            CandidateMatchContext context,
            Job job,
            List<String> positiveReasons,
            List<String> gapReasons
    ) {
        SalaryTarget candidateTarget = resolveCandidateSalaryTarget(context);
        if (candidateTarget == null) {
            gapReasons.add("Minimum expected salary is not configured; salary fit cannot be assumed (0%).");
            return 0.0;
        }

        BigDecimal rawJobMin = sanitizePositive(job.getSalaryMin());
        BigDecimal rawJobMax = sanitizePositive(job.getSalaryMax());
        if (rawJobMin == null && rawJobMax == null) {
            gapReasons.add("Job posting omits salary details; salary fit scored conservatively at 0%.");
            return 0.0;
        }

        String jobCurrency = normalizeCurrency(job.getSalaryCurrency());
        SalaryPeriod period = job.getSalaryPeriod() != null ? job.getSalaryPeriod() : SalaryPeriod.YEARLY;
        double annualMultiplier = switch (period) {
            case YEARLY -> 1.0;
            case MONTHLY -> 12.0;
            case DAILY -> 220.0;
            case HOURLY -> 1760.0;
        };

        double effectiveJobMin = (rawJobMin != null ? rawJobMin : rawJobMax).doubleValue() * annualMultiplier;
        double effectiveJobMax = (rawJobMax != null ? rawJobMax : rawJobMin).doubleValue() * annualMultiplier;
        if (effectiveJobMin > effectiveJobMax) {
            double tmp = effectiveJobMin;
            effectiveJobMin = effectiveJobMax;
            effectiveJobMax = tmp;
        }

        double candidateMin = candidateTarget.annualAmount();
        String candCurrency = candidateTarget.currency();

        double comparableJobMin;
        double comparableJobMax;
        double comparableCandidateMin;
        boolean fxConverted = false;

        if (jobCurrency != null && jobCurrency.equals(candCurrency)) {
            comparableJobMin = effectiveJobMin;
            comparableJobMax = effectiveJobMax;
            comparableCandidateMin = candidateMin;
        } else {
            Double jobFx = jobCurrency != null ? EUR_FX_RATES.get(jobCurrency) : null;
            Double candFx = EUR_FX_RATES.get(candCurrency);
            if (jobFx == null || candFx == null) {
                gapReasons.add("Job salary currency (" + jobCurrency + ") cannot be reliably compared with " + candCurrency + ".");
                return 0.0;
            }
            comparableJobMin = effectiveJobMin * jobFx;
            comparableJobMax = effectiveJobMax * jobFx;
            comparableCandidateMin = candidateMin * candFx;
            fxConverted = true;
        }

        String fxNote = fxConverted ? " (estimated EUR conversion from " + jobCurrency + ")" : "";
        if (comparableJobMin >= comparableCandidateMin) {
            positiveReasons.add("Offered salary meets or exceeds your minimum target" + fxNote + ".");
            return 100.0;
        }
        if (comparableJobMax >= comparableCandidateMin) {
            double span = Math.max(1.0, comparableJobMax - comparableJobMin);
            double ratio = (comparableJobMax - comparableCandidateMin) / span;
            double score = 75.0 + 25.0 * Math.max(0.0, Math.min(1.0, ratio));
            positiveReasons.add("Salary range overlaps your minimum target" + fxNote + ".");
            return clampScore(score);
        }

        double ratio = comparableJobMax / Math.max(1.0, comparableCandidateMin);
        if (ratio >= 0.90) {
            double score = 60.0 + ((ratio - 0.90) / 0.10) * 15.0;
            gapReasons.add("Top advertised salary is within 10% below your target expectation" + fxNote + ".");
            return clampScore(score);
        }
        if (ratio >= 0.75) {
            double score = 25.0 + ((ratio - 0.75) / 0.15) * 35.0;
            gapReasons.add("Advertised salary falls 10–25% below your target expectation" + fxNote + ".");
            return clampScore(score);
        }

        gapReasons.add("Advertised salary is significantly below your minimum expectation" + fxNote + ".");
        return 0.0;
    }

    private Map<String, String> extractJobSkills(Job job) {
        Map<String, String> required = new LinkedHashMap<>();
        if (job.getSkills() != null) {
            for (String rawSkill : job.getSkills()) {
                if (rawSkill == null || rawSkill.isBlank()) {
                    continue;
                }
                String display = rawSkill.trim();
                String key = canonicalizeSkillKey(display);
                if (!key.isBlank()) {
                    required.putIfAbsent(key, display);
                }
            }
        }

        if (required.isEmpty()) {
            StringBuilder searchable = new StringBuilder();
            if (job.getTitle() != null) {
                searchable.append(job.getTitle()).append('\n');
            }
            if (job.getDescription() != null) {
                searchable.append(job.getDescription());
            }
            String text = searchable.toString();
            if (!text.isBlank()) {
                for (Map.Entry<Pattern, String> patternEntry : DESCRIPTION_SKILL_PATTERNS.entrySet()) {
                    if (patternEntry.getKey().matcher(text).find()) {
                        String display = patternEntry.getValue();
                        String key = canonicalizeSkillKey(display);
                        required.putIfAbsent(key, display);
                    }
                }
            }
        }
        return required;
    }

    private Set<String> extractCandidateCanonicalSkills(CandidateMatchContext context) {
        Set<String> skills = new HashSet<>();
        for (UserSkill skill : context.skills()) {
            if (skill == null) {
                continue;
            }
            String candidateText = skill.getSkillName() != null && !skill.getSkillName().isBlank()
                    ? skill.getSkillName()
                    : skill.getNormalizedSkill();
            String key = canonicalizeSkillKey(candidateText);
            if (!key.isBlank()) {
                skills.add(key);
            }
        }
        for (UserWorkExperience exp : context.workExperiences()) {
            if (exp == null || exp.getSkillsUsed() == null) {
                continue;
            }
            for (String usedSkill : exp.getSkillsUsed()) {
                String key = canonicalizeSkillKey(usedSkill);
                if (!key.isBlank()) {
                    skills.add(key);
                }
            }
        }
        return skills;
    }

    public String canonicalizeSkill(String rawSkill) {
        return canonicalizeSkillKey(rawSkill);
    }

    public boolean skillSetSatisfies(Set<String> candidateCanonicalSkills, String requiredCanonicalKey) {
        if (candidateCanonicalSkills == null || candidateCanonicalSkills.isEmpty()
                || requiredCanonicalKey == null || requiredCanonicalKey.isBlank()) {
            return false;
        }
        return candidateHasSkill(candidateCanonicalSkills, requiredCanonicalKey);
    }

    public Map<String, String> extractSkillsFromText(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Map<String, String> found = new LinkedHashMap<>();
        for (Map.Entry<Pattern, String> patternEntry : DESCRIPTION_SKILL_PATTERNS.entrySet()) {
            if (patternEntry.getKey().matcher(text).find()) {
                String display = patternEntry.getValue();
                String key = canonicalizeSkillKey(display);
                if (!key.isBlank()) {
                    found.putIfAbsent(key, display);
                }
            }
        }
        return Collections.unmodifiableMap(found);
    }

    private boolean candidateHasSkill(Set<String> candidateSkills, String requiredKey) {
        if (candidateSkills.contains(requiredKey)) {
            return true;
        }
        Set<String> children = PARENT_SKILL_SATISFIERS.get(requiredKey);
        if (children != null) {
            for (String child : children) {
                if (candidateSkills.contains(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String canonicalizeSkillKey(String rawSkill) {
        if (rawSkill == null) {
            return "";
        }
        String collapsed = rawSkill.trim()
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        if (collapsed.isEmpty()) {
            return "";
        }
        if (SKILL_ALIASES.containsKey(collapsed)) {
            return SKILL_ALIASES.get(collapsed);
        }
        if (collapsed.matches("java\\s*(?:8|11|17|21|22|23|ee|se)")) {
            return "java";
        }
        if (collapsed.matches("angular\\s*(?:1[0-9]|20)")) {
            return "angular";
        }
        if (collapsed.matches("spring\\s*-?\\s*boot(?:\\s*[0-9]+(?:\\.[0-9]+)?)?")) {
            return "spring boot";
        }
        if (collapsed.matches("python\\s*3(?:\\.[0-9]+)?")) {
            return "python";
        }
        return collapsed;
    }

    private ExperienceRequirement resolveJobExperienceRequirement(Job job) {
        ExperienceLevel level = job.getExperienceLevel();
        BigDecimal explicitMin = job.getMinExperienceYears();
        if (explicitMin == null && level == null) {
            return null;
        }

        double minYears;
        if (explicitMin != null) {
            minYears = Math.max(0.0, explicitMin.doubleValue());
        } else {
            minYears = switch (level) {
                case ENTRY -> 0.0;
                case JUNIOR -> 1.0;
                case MID -> 2.0;
                case SENIOR -> 5.0;
                case LEAD -> 7.0;
                case PRINCIPAL -> 10.0;
            };
        }

        double maxComfort;
        if (level != null) {
            maxComfort = switch (level) {
                case ENTRY -> Math.max(minYears + 2.5, 2.5);
                case JUNIOR -> Math.max(minYears + 3.0, 4.0);
                case MID -> Math.max(minYears + 4.5, 7.0);
                case SENIOR -> Math.max(minYears + 7.0, 12.0);
                case LEAD -> Math.max(minYears + 8.0, 16.0);
                case PRINCIPAL -> Math.max(minYears + 10.0, 22.0);
            };
        } else {
            maxComfort = Math.max(minYears + 6.0, minYears * 1.8);
        }

        String levelLabel = level != null ? switch (level) {
            case ENTRY -> "Entry";
            case JUNIOR -> "Junior";
            case MID -> "Mid-level";
            case SENIOR -> "Senior";
            case LEAD -> "Lead";
            case PRINCIPAL -> "Principal";
        } : "";

        return new ExperienceRequirement(minYears, maxComfort, levelLabel);
    }

    private Double resolveCandidateExperienceYears(CandidateMatchContext context) {
        Double workHistoryYears = calculateMergedWorkExperienceYears(context.workExperiences());
        UserProfile profile = context.profile();

        if (profile != null && profile.getYearsOfExperience() != null) {
            double profileYears = Math.max(0.0, profile.getYearsOfExperience().doubleValue());
            if (profileYears > 0.0) {
                return profileYears;
            }
            if (workHistoryYears != null && workHistoryYears > 0.0) {
                return workHistoryYears;
            }
            return profileYears;
        }
        return workHistoryYears;
    }

    private Double calculateMergedWorkExperienceYears(List<UserWorkExperience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        List<DateInterval> intervals = new ArrayList<>();
        for (UserWorkExperience exp : experiences) {
            if (exp == null || exp.getStartDate() == null) {
                continue;
            }
            LocalDate start = exp.getStartDate();
            LocalDate end = exp.getEndDate() != null ? exp.getEndDate() : today;
            if (!end.isBefore(start)) {
                intervals.add(new DateInterval(start, end));
            }
        }
        if (intervals.isEmpty()) {
            return null;
        }

        intervals.sort(Comparator.comparing(DateInterval::start).thenComparing(DateInterval::end));
        List<DateInterval> merged = new ArrayList<>();
        DateInterval current = intervals.get(0);
        for (int i = 1; i < intervals.size(); i++) {
            DateInterval next = intervals.get(i);
            if (!next.start().isAfter(current.end())) {
                LocalDate extendedEnd = next.end().isAfter(current.end()) ? next.end() : current.end();
                current = new DateInterval(current.start(), extendedEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        long totalDays = 0;
        for (DateInterval interval : merged) {
            totalDays += ChronoUnit.DAYS.between(interval.start(), interval.end());
        }
        double years = totalDays / 365.25;
        return BigDecimal.valueOf(years).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private double scoreSingleTitlePair(String candidateTitle, String jobTitle) {
        String candKey = canonicalHashGenerator.normalizeTitleKey(candidateTitle);
        String jobKey = canonicalHashGenerator.normalizeTitleKey(jobTitle);
        if (candKey.isEmpty() || jobKey.isEmpty()) {
            return 0.0;
        }
        if (candKey.equals(jobKey)) {
            return 100.0;
        }

        TitleTokens candTokens = parseTitleTokens(candKey);
        TitleTokens jobTokens = parseTitleTokens(jobKey);
        if (candTokens.coreWeights().isEmpty() || jobTokens.coreWeights().isEmpty()) {
            return 0.0;
        }

        if (candTokens.coreWeights().keySet().equals(jobTokens.coreWeights().keySet())) {
            return seniorityCompatible(candTokens.seniorities(), jobTokens.seniorities()) ? 98.0 : 88.0;
        }

        double totalCandWeight = 0.0;
        double matchedCandWeight = 0.0;
        for (Map.Entry<String, Double> entry : candTokens.coreWeights().entrySet()) {
            double weight = entry.getValue();
            totalCandWeight += weight;
            if (jobTokens.coreWeights().containsKey(entry.getKey())) {
                matchedCandWeight += weight;
            }
        }

        double unionWeight = totalCandWeight;
        for (Map.Entry<String, Double> entry : jobTokens.coreWeights().entrySet()) {
            if (!candTokens.coreWeights().containsKey(entry.getKey())) {
                unionWeight += entry.getValue();
            }
        }

        if (matchedCandWeight <= 0.0 || totalCandWeight <= 0.0) {
            return 0.0;
        }

        double recall = matchedCandWeight / totalCandWeight;
        double jaccard = matchedCandWeight / Math.max(1e-6, unionWeight);

        double score;
        if (recall >= 0.99) {
            score = seniorityCompatible(candTokens.seniorities(), jobTokens.seniorities()) ? 95.0 : 85.0;
        } else {
            score = (recall * 0.70 + jaccard * 0.30) * 90.0;
            if (!seniorityCompatible(candTokens.seniorities(), jobTokens.seniorities())) {
                score *= 0.88;
            }
        }

        if (hasOpposingTrack(candTokens.coreWeights().keySet(), jobTokens.coreWeights().keySet())) {
            score = Math.min(score, 25.0);
        }

        return clampScore(score);
    }

    private TitleTokens parseTitleTokens(String normalizedTitle) {
        String rewritten = normalizedTitle
                .replaceAll("\\bfull\\s+stack\\b", "fullstack")
                .replaceAll("\\bback\\s+end\\b", "backend")
                .replaceAll("\\bfront\\s+end\\b", "frontend")
                .replaceAll("\\bsite\\s+reliability\\s+engineer(?:ing)?\\b", "devops sre")
                .replaceAll("\\bsre\\b", "devops sre")
                .replaceAll("\\bswe\\b", "software engineer")
                .replaceAll("\\btech\\s+lead\\b", "lead")
                .replaceAll("\\bteam\\s+lead\\b", "lead")
                .replaceAll("\\bsr\\b", "senior")
                .replaceAll("\\bjr\\b", "junior")
                .replaceAll("\\bdev\\b", "developer")
                .replaceAll("\\beng\\b", "engineer")
                .replaceAll("\\bdeveloper\\b", "engineer");

        Set<String> seniorities = new HashSet<>();
        Map<String, Double> coreWeights = new HashMap<>();

        for (String token : rewritten.split("\\s+")) {
            if (token.isEmpty() || TITLE_STOP_WORDS.contains(token)) {
                continue;
            }
            if (SENIORITY_TOKENS.contains(token)) {
                seniorities.add(token);
                continue;
            }
            double weight = GENERIC_ROLE_TOKENS.contains(token) ? 0.35 : 1.0;
            coreWeights.merge(token, weight, Math::max);
        }
        return new TitleTokens(coreWeights, seniorities);
    }

    private boolean seniorityCompatible(Set<String> candidateSeniorities, Set<String> jobSeniorities) {
        if (candidateSeniorities.isEmpty() || jobSeniorities.isEmpty()) {
            return true;
        }
        for (String s : candidateSeniorities) {
            if (jobSeniorities.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOpposingTrack(Set<String> cand, Set<String> job) {
        return (cand.contains("backend") && job.contains("frontend") && !job.contains("backend") && !job.contains("fullstack"))
                || (cand.contains("frontend") && job.contains("backend") && !job.contains("frontend") && !job.contains("fullstack"))
                || (cand.contains("ios") && job.contains("android") && !job.contains("ios"))
                || (cand.contains("android") && job.contains("ios") && !job.contains("android"));
    }

    private WorkArrangement resolveEffectiveArrangementPreference(UserJobPreference preference, UserProfile profile) {
        if (profile != null && profile.getRelocationPreference() == RelocationPreference.REMOTE_ONLY) {
            if (preference == null || preference.getWorkArrangement() == null
                    || preference.getWorkArrangement() == WorkArrangement.ANY) {
                return WorkArrangement.REMOTE;
            }
        }
        return preference != null ? preference.getWorkArrangement() : null;
    }

    private double scoreGeography(
            String jobCountry,
            String jobCityKey,
            WorkArrangement jobArrangement,
            Set<String> targetCountries,
            Set<String> targetCities,
            WorkArrangement candidateArrangement
    ) {
        if (targetCountries.isEmpty() && targetCities.isEmpty()) {
            return 0.0;
        }

        String jobCountryKey = jobCountry.toLowerCase(Locale.ROOT);
        boolean countryMatched = targetCountries.contains(jobCountryKey)
                || ("europe".equals(jobCountryKey)
                && !targetCountries.isEmpty()
                && jobArrangement == WorkArrangement.REMOTE
                && (candidateArrangement == WorkArrangement.REMOTE || candidateArrangement == WorkArrangement.ANY));

        boolean cityMatched = !jobCityKey.isBlank() && targetCities.contains(jobCityKey);

        if (cityMatched && (targetCountries.isEmpty() || countryMatched)) {
            return 70.0;
        }
        if (countryMatched) {
            return targetCities.isEmpty() ? 70.0 : 55.0;
        }
        if (cityMatched) {
            return 55.0;
        }
        return 0.0;
    }

    private double scoreWorkArrangement(
            WorkArrangement jobArrangement,
            WorkArrangement candidateArrangement,
            boolean hasAnyLocationPreference
    ) {
        if (jobArrangement == null || candidateArrangement == null || !hasAnyLocationPreference) {
            return 0.0;
        }
        if (candidateArrangement == WorkArrangement.ANY) {
            return 30.0;
        }
        if (candidateArrangement == jobArrangement || jobArrangement == WorkArrangement.ANY) {
            return 30.0;
        }
        return switch (candidateArrangement) {
            case REMOTE -> jobArrangement == WorkArrangement.HYBRID ? 12.0 : 0.0;
            case HYBRID -> jobArrangement == WorkArrangement.REMOTE ? 24.0 : 12.0;
            case ONSITE -> jobArrangement == WorkArrangement.HYBRID ? 20.0 : 12.0;
            case ANY -> 30.0;
        };
    }

    private boolean doesCandidateNeedSponsorship(UserProfile profile, UserJobPreference preference) {
        if (profile != null) {
            WorkAuthorizationStatus auth = profile.getWorkAuthorizationStatus();
            if (auth == WorkAuthorizationStatus.EU_CITIZEN || auth == WorkAuthorizationStatus.PERMANENT_RESIDENT) {
                return false;
            }
            if (auth == WorkAuthorizationStatus.NEEDS_SPONSORSHIP) {
                return true;
            }
        }
        if (preference != null) {
            return preference.isRequiresSponsorship();
        }
        return true;
    }

    private SalaryTarget resolveCandidateSalaryTarget(CandidateMatchContext context) {
        if (context.preference() != null && sanitizePositive(context.preference().getMinSalary()) != null) {
            String curr = normalizeCurrency(context.preference().getSalaryCurrency());
            return new SalaryTarget(
                    context.preference().getMinSalary().doubleValue(),
                    curr != null ? curr : "EUR"
            );
        }
        if (context.profile() != null && sanitizePositive(context.profile().getExpectedSalaryMin()) != null) {
            String curr = normalizeCurrency(context.profile().getExpectedSalaryCurrency());
            return new SalaryTarget(
                    context.profile().getExpectedSalaryMin().doubleValue(),
                    curr != null ? curr : "EUR"
            );
        }
        return null;
    }

    private BigDecimal sanitizePositive(BigDecimal value) {
        return (value != null && value.compareTo(BigDecimal.ZERO) > 0) ? value : null;
    }

    private String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        if ("€".equals(trimmed)) {
            return "EUR";
        }
        if ("£".equals(trimmed)) {
            return "GBP";
        }
        if ("$".equals(trimmed)) {
            return "USD";
        }
        return trimmed;
    }

    public BigDecimal computeFinalScore(BigDecimal deterministicScore, BigDecimal existingAiScore) {
        BigDecimal safeDeterministic = deterministicScore != null
                ? toBoundedDecimal(deterministicScore.doubleValue())
                : toBoundedDecimal(0.0);
        if (existingAiScore == null) {
            return safeDeterministic;
        }
        BigDecimal clampedAi = toBoundedDecimal(existingAiScore.doubleValue());
        BigDecimal blended = safeDeterministic.add(clampedAi)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return toBoundedDecimal(blended.doubleValue());
    }

    private BigDecimal toBoundedDecimal(double rawScore) {
        return BigDecimal.valueOf(clampScore(rawScore)).setScale(2, RoundingMode.HALF_UP);
    }

    private double clampScore(double rawScore) {
        if (Double.isNaN(rawScore) || Double.isInfinite(rawScore)) {
            return MIN_SCORE;
        }
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, rawScore));
    }

    private int clampPercentage(BigDecimal score) {
        if (score == null) {
            return 0;
        }
        int rounded = score.setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, rounded));
    }

    private String formatYears(double years) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        DecimalFormat df = new DecimalFormat("0.#", symbols);
        return df.format(years);
    }

    private String formatArrangement(WorkArrangement arrangement) {
        return switch (arrangement) {
            case REMOTE -> "Remote";
            case HYBRID -> "Hybrid";
            case ONSITE -> "On-site";
            case ANY -> "Flexible";
        };
    }

    private String summarizeSkillNames(List<String> skills) {
        if (skills.isEmpty()) {
            return "";
        }
        int limit = 4;
        if (skills.size() <= limit) {
            return String.join(", ", skills);
        }
        return String.join(", ", skills.subList(0, limit)) + " +" + (skills.size() - limit) + " more";
    }

    private String normalizeCityKey(String rawCity) {
        if (rawCity == null || rawCity.isBlank()) {
            return "";
        }
        String preFolded = rawCity
                .replace("ø", "o").replace("Ø", "o")
                .replace("ł", "l").replace("Ł", "l")
                .replace("æ", "ae").replace("Æ", "ae")
                .replace("œ", "oe").replace("Œ", "oe")
                .replace("ß", "ss");
        String key = canonicalHashGenerator.normalizeGenericKey(preFolded);
        if (key.isEmpty()) {
            return "";
        }
        return CITY_ALIASES.getOrDefault(key, key);
    }

    private static Map<String, String> buildCityAliases() {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("munchen", "munich");
        aliases.put("muenchen", "munich");
        aliases.put("koln", "cologne");
        aliases.put("koeln", "cologne");
        aliases.put("nurnberg", "nuremberg");
        aliases.put("nuernberg", "nuremberg");
        aliases.put("duesseldorf", "dusseldorf");
        aliases.put("frankfurt am main", "frankfurt");
        aliases.put("frankfurt main", "frankfurt");
        aliases.put("den haag", "the hague");
        aliases.put("s gravenhage", "the hague");
        aliases.put("wien", "vienna");
        aliases.put("zuerich", "zurich");
        aliases.put("geneve", "geneva");
        aliases.put("genf", "geneva");
        aliases.put("bale", "basel");
        aliases.put("bruxelles", "brussels");
        aliases.put("brussel", "brussels");
        aliases.put("antwerpen", "antwerp");
        aliases.put("gent", "ghent");
        aliases.put("goteborg", "gothenburg");
        aliases.put("kobenhavn", "copenhagen");
        aliases.put("warszawa", "warsaw");
        aliases.put("praha", "prague");
        aliases.put("sevilla", "seville");
        aliases.put("milano", "milan");
        aliases.put("roma", "rome");
        aliases.put("torino", "turin");
        aliases.put("lisboa", "lisbon");
        return Map.copyOf(aliases);
    }

    private static Map<String, String> buildSkillAliases() {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("jdk", "java");
        aliases.put("spring-boot", "spring boot");
        aliases.put("springboot", "spring boot");
        aliases.put("angularjs", "angular");
        aliases.put("ts", "typescript");
        aliases.put("js", "javascript");
        aliases.put("ecmascript", "javascript");
        aliases.put("postgres", "postgresql");
        aliases.put("k8s", "kubernetes");
        aliases.put("amazon web services", "aws");
        aliases.put("google cloud", "gcp");
        aliases.put("google cloud platform", "gcp");
        aliases.put("reactjs", "react");
        aliases.put("react.js", "react");
        aliases.put("nodejs", "node.js");
        aliases.put("node", "node.js");
        aliases.put("vuejs", "vue.js");
        aliases.put("vue", "vue.js");
        aliases.put("nextjs", "next.js");
        aliases.put("next", "next.js");
        aliases.put("golang", "go");
        aliases.put("go language", "go");
        aliases.put("apache kafka", "kafka");
        aliases.put("rest api", "rest apis");
        aliases.put("restful api", "rest apis");
        aliases.put("restful apis", "rest apis");
        aliases.put("rest", "rest apis");
        aliases.put("cicd", "ci/cd");
        aliases.put("ci cd", "ci/cd");
        aliases.put("microservice", "microservices");
        aliases.put("microservice architecture", "microservices");
        aliases.put("csharp", "c#");
        aliases.put("cpp", "c++");
        aliases.put("dotnet", ".net");
        aliases.put(".net core", ".net");
        aliases.put("mongo", "mongodb");
        aliases.put("html5", "html");
        aliases.put("css3", "css");
        aliases.put("scss", "css");
        aliases.put("sass", "css");
        aliases.put("spring mvc", "spring framework");
        aliases.put("spring data", "spring framework");
        aliases.put("spring security", "spring framework");
        aliases.put("micro front-end architecture", "micro-frontends");
        aliases.put("micro front end architecture", "micro-frontends");
        aliases.put("micro frontends", "micro-frontends");
        aliases.put("micro-frontend", "micro-frontends");
        aliases.put("microfrontend", "micro-frontends");
        return Map.copyOf(aliases);
    }

    private static Map<Pattern, String> buildDescriptionSkillPatterns() {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("\\bspring\\s*-?\\s*boot\\b", "Spring Boot");
        specs.put("\\bjava(?:\\s*(?:8|11|17|21))?\\b", "Java");
        specs.put("\\bangular(?:\\s*(?:1[0-9]|20))?\\b", "Angular");
        specs.put("\\btypescript\\b", "TypeScript");
        specs.put("\\bjavascript\\b", "JavaScript");
        specs.put("\\bkotlin\\b", "Kotlin");
        specs.put("\\bpython\\b", "Python");
        specs.put("\\bgolang\\b|\\bgo\\s+language\\b", "Go");
        specs.put("\\bpostgresql\\b|\\bpostgres\\b", "PostgreSQL");
        specs.put("\\bmysql\\b", "MySQL");
        specs.put("\\bmongodb\\b", "MongoDB");
        specs.put("\\bredis\\b", "Redis");
        specs.put("\\bkafka\\b|\\bapache\\s+kafka\\b", "Kafka");
        specs.put("\\bdocker\\b", "Docker");
        specs.put("\\bkubernetes\\b|\\bk8s\\b", "Kubernetes");
        specs.put("\\baws\\b|\\bamazon\\s+web\\s+services\\b", "AWS");
        specs.put("\\bazure\\b", "Azure");
        specs.put("\\bgcp\\b|\\bgoogle\\s+cloud\\b", "GCP");
        specs.put("\\bterraform\\b", "Terraform");
        specs.put("\\breact(?:\\.js)?\\b", "React");
        specs.put("\\bnode(?:\\.js)?\\b", "Node.js");
        specs.put("\\bgraphql\\b", "GraphQL");
        specs.put("\\brest(?:ful)?\\s+apis?\\b", "REST APIs");
        specs.put("\\bmicroservices?\\b", "Microservices");
        specs.put("\\bci/cd\\b", "CI/CD");
        specs.put("\\bspring\\s*(?:framework|mvc|data|security)\\b", "Spring Framework");
        specs.put("\\bvue(?:\\.js|js)?\\b", "Vue.js");
        specs.put("\\bnext(?:\\.js|js)\\b", "Next.js");
        specs.put("\\brxjs\\b", "RxJS");
        specs.put("\\bngrx\\b", "NgRx");
        specs.put("\\bhtml5?\\b", "HTML");
        specs.put("\\bcss3?\\b|\\bsass\\b|\\bscss\\b", "CSS");
        specs.put("(?<!\\w)c#(?![\\w#])", "C#");
        specs.put("(?<!\\w)\\.net(?:\\s+core)?\\b", ".NET");
        specs.put("(?<!\\w)c\\+\\+(?![\\w+])", "C++");
        specs.put("\\bphp\\b", "PHP");
        specs.put("\\blaravel\\b", "Laravel");
        specs.put("\\bruby(?:\\s+on\\s+rails)?\\b", "Ruby");
        specs.put("\\bgit\\b", "Git");
        specs.put("\\bjenkins\\b", "Jenkins");
        specs.put("\\brabbitmq\\b", "RabbitMQ");
        specs.put("\\belasticsearch\\b", "Elasticsearch");
        specs.put("\\bmicro[-\\s]?front[-\\s]?ends?(?:\\s+architecture)?\\b", "Micro-Frontends");

        Map<Pattern, String> compiled = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : specs.entrySet()) {
            compiled.put(Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE), entry.getValue());
        }
        return Collections.unmodifiableMap(compiled);
    }

    private boolean hasExplicitVacancySponsorshipMention(Job job) {
        String combined = ((job.getTitle() != null ? job.getTitle() : "") + "\n"
                + (job.getDescription() != null ? job.getDescription() : "")).toLowerCase(Locale.ROOT);
        return combined.contains("visa sponsorship")
                || combined.contains("sponsor visa")
                || combined.contains("eu blue card")
                || combined.contains("highly skilled migrant")
                || combined.contains("kennismigrant")
                || combined.contains("ind sponsorship")
                || combined.contains("work permit support")
                || combined.contains("work permit sponsorship")
                || combined.contains("chancenkarte")
                || combined.contains("passeport talent")
                || combined.contains("critical skills employment permit");
    }

    private void addTitleVariants(Set<String> target, String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return;
        }
        String trimmed = rawTitle.trim();
        boolean addedAnySegment = false;
        for (String segment : trimmed.split("[|;•·—]")) {
            String clean = segment.trim();
            if (clean.length() >= 2) {
                target.add(clean);
                addedAnySegment = true;
            }
        }
        if (!addedAnySegment) {
            target.add(trimmed);
        }
    }

    private record SkillEvaluation(double score, List<String> matchingSkills, List<String> missingSkills) {
    }

    private record ExperienceEvaluation(double score, String compatibility) {
    }

    private record ExperienceRequirement(double minYears, double maxComfortYears, String levelLabel) {
    }

    private record DateInterval(LocalDate start, LocalDate end) {
    }

    private record TitleTokens(Map<String, Double> coreWeights, Set<String> seniorities) {
    }

    private record SalaryTarget(double annualAmount, String currency) {
    }
}
