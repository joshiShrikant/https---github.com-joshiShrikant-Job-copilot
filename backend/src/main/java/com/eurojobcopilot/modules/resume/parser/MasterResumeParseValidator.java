package com.eurojobcopilot.modules.resume.parser;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProfileDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProjectDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeSkillDto;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces strict validation, deduplication, date normalization, URL hygiene, and anti-corruption checks
 * on AI-extracted resume drafts prior to storing them for mandatory human review.
 */
@Component
public class MasterResumeParseValidator {

    private static final int MAX_SKILLS = 60;
    private static final int MAX_RAW_SKILLS = 120;
    private static final int MAX_EXPERIENCES = 25;
    private static final int MAX_RAW_EXPERIENCES = 40;
    private static final int MAX_EDUCATIONS = 15;
    private static final int MAX_RAW_EDUCATIONS = 25;
    private static final int MAX_PROJECTS = 15;
    private static final int MAX_RAW_PROJECTS = 25;
    private static final int MAX_LANGUAGES = 20;
    private static final int MAX_CERTIFICATIONS = 20;
    private static final int MAX_WARNINGS = 30;
    private static final int MAX_SKILLS_PER_EXP = 50;
    private static final int MAX_TECH_PER_PROJECT = 25;

    private static final BigDecimal MAX_EXPERIENCE_YEARS = new BigDecimal("70.0");

    private static final Pattern INLINE_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EXCESS_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern YEAR_ONLY = Pattern.compile("^(19\\d{2}|20\\d{2})$");
    private static final Pattern YEAR_MONTH = Pattern.compile("^(19\\d{2}|20\\d{2})[-/](0?[1-9]|1[0-2])$");
    private static final Pattern MONTH_YEAR = Pattern.compile("^(0?[1-9]|1[0-2])[-/](19\\d{2}|20\\d{2})$");
    private static final Pattern HOST_PATTERN =
            Pattern.compile("^(?i)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final Pattern URL_UNSAFE_WHITESPACE = Pattern.compile("[\\s\\p{Cntrl}]");

    private static final Map<String, String> COUNTRY_ALIASES = Map.ofEntries(
            Map.entry("de", "Germany"),
            Map.entry("deu", "Germany"),
            Map.entry("germany", "Germany"),
            Map.entry("deutschland", "Germany"),
            Map.entry("nl", "Netherlands"),
            Map.entry("nld", "Netherlands"),
            Map.entry("netherlands", "Netherlands"),
            Map.entry("the netherlands", "Netherlands"),
            Map.entry("nederland", "Netherlands"),
            Map.entry("uk", "United Kingdom"),
            Map.entry("gb", "United Kingdom"),
            Map.entry("gbr", "United Kingdom"),
            Map.entry("united kingdom", "United Kingdom"),
            Map.entry("ie", "Ireland"),
            Map.entry("ireland", "Ireland"),
            Map.entry("se", "Sweden"),
            Map.entry("sweden", "Sweden"),
            Map.entry("ch", "Switzerland"),
            Map.entry("switzerland", "Switzerland"),
            Map.entry("schweiz", "Switzerland"),
            Map.entry("fr", "France"),
            Map.entry("france", "France"),
            Map.entry("es", "Spain"),
            Map.entry("spain", "Spain"),
            Map.entry("at", "Austria"),
            Map.entry("austria", "Austria"),
            Map.entry("dk", "Denmark"),
            Map.entry("denmark", "Denmark"),
            Map.entry("fi", "Finland"),
            Map.entry("finland", "Finland"),
            Map.entry("pt", "Portugal"),
            Map.entry("portugal", "Portugal"),
            Map.entry("pl", "Poland"),
            Map.entry("poland", "Poland"),
            Map.entry("be", "Belgium"),
            Map.entry("belgium", "Belgium"),
            Map.entry("lu", "Luxembourg"),
            Map.entry("luxembourg", "Luxembourg"),
            Map.entry("it", "Italy"),
            Map.entry("italy", "Italy"),
            Map.entry("no", "Norway"),
            Map.entry("norway", "Norway"),
            Map.entry("us", "United States"),
            Map.entry("usa", "United States"),
            Map.entry("united states", "United States"),
            Map.entry("in", "India"),
            Map.entry("india", "India")
    );

    public ValidatedMasterResumeParse validate(
            RawAiParsedResumePayload raw,
            ExtractedResumeText extracted,
            boolean inputTruncated
    ) {
        if (raw == null) {
            throw new AiResponseValidationException("AI resume parser returned a null payload.");
        }

        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        if (extracted != null && extracted.warnings() != null) {
            for (String w : extracted.warnings()) {
                addWarning(warnings, w);
            }
        }
        if (inputTruncated) {
            addWarning(warnings, "Resume text exceeded configured AI input length limit and was truncated; verify all tail sections carefully.");
        }
        if (raw.getWarnings() != null) {
            for (String w : raw.getWarnings()) {
                addWarning(warnings, w);
            }
        }

        ParsedResumeProfileDto profile = validateProfile(raw.getProfile(), warnings);
        String summary = cleanProse(raw.getSummary(), 2000);
        if (summary == null && profile.getBio() != null) {
            summary = profile.getBio();
        } else if (profile.getBio() == null && summary != null) {
            profile.setBio(summary);
        }

        List<ParsedResumeSkillDto> skills = validateSkills(raw.getSkills());
        List<ParsedResumeExperienceDto> experiences = validateExperiences(raw.getExperiences(), warnings);
        List<ParsedResumeEducationDto> educations = validateEducations(raw.getEducations(), warnings);
        List<ParsedResumeProjectDto> projects = validateProjects(raw.getProjects(), warnings);
        List<String> languages = sanitizeStringList(raw.getLanguages(), MAX_LANGUAGES, 100);
        List<String> certifications = sanitizeStringList(raw.getCertifications(), MAX_CERTIFICATIONS, 150);

        boolean completelyEmpty = summary == null
                && profile.getCurrentDesignation() == null
                && skills.isEmpty()
                && experiences.isEmpty()
                && educations.isEmpty()
                && projects.isEmpty();
        if (completelyEmpty) {
            throw new AiResponseValidationException(
                    "AI resume parser returned an empty extraction with no summary, skills, experience, education, or projects."
            );
        }

        List<String> finalWarnings = warnings.stream().limit(MAX_WARNINGS).toList();
        ParsedResumeJsonDto parsedDto = ParsedResumeJsonDto.builder()
                .profile(profile)
                .summary(summary)
                .skills(skills)
                .experiences(experiences)
                .educations(educations)
                .projects(projects)
                .languages(languages)
                .certifications(certifications)
                .warnings(finalWarnings)
                .build();

        return new ValidatedMasterResumeParse(parsedDto, finalWarnings);
    }

    private ParsedResumeProfileDto validateProfile(
            RawAiParsedResumePayload.RawProfile raw,
            LinkedHashSet<String> warnings
    ) {
        if (raw == null) {
            return ParsedResumeProfileDto.builder().build();
        }

        BigDecimal years = raw.getYearsOfExperience();
        if (years != null) {
            if (years.compareTo(BigDecimal.ZERO) < 0 || years.compareTo(MAX_EXPERIENCE_YEARS) > 0) {
                throw new AiResponseValidationException(
                        "AI resume parser returned invalid yearsOfExperience (" + years + "); must be between 0 and 70."
                );
            }
            years = years.setScale(1, RoundingMode.HALF_UP);
        }

        String linkedinUrl = sanitizeUrl(raw.getLinkedinUrl(), "linkedinUrl", warnings);
        String githubUrl = sanitizeUrl(raw.getGithubUrl(), "githubUrl", warnings);
        String portfolioUrl = sanitizeUrl(raw.getPortfolioUrl(), "portfolioUrl", warnings);

        WorkAuthorizationStatus workAuth = parseWorkAuthorization(raw.getWorkAuthorizationStatus(), warnings);
        RelocationPreference relocation = parseRelocationPreference(raw.getRelocationPreference(), warnings);

        return ParsedResumeProfileDto.builder()
                .fullName(cleanSingleLine(raw.getFullName(), 200))
                .currentDesignation(cleanSingleLine(raw.getCurrentDesignation(), 150))
                .yearsOfExperience(years)
                .currentCountry(normalizeCountry(raw.getCurrentCountry(), 100))
                .currentCity(cleanSingleLine(raw.getCurrentCity(), 100))
                .linkedinUrl(linkedinUrl)
                .githubUrl(githubUrl)
                .portfolioUrl(portfolioUrl)
                .bio(cleanProse(raw.getBio(), 2000))
                .workAuthorizationStatus(workAuth)
                .relocationPreference(relocation)
                .build();
    }

    private List<ParsedResumeSkillDto> validateSkills(List<RawAiParsedResumePayload.RawSkill> rawSkills) {
        if (rawSkills == null || rawSkills.isEmpty()) {
            return List.of();
        }
        if (rawSkills.size() > MAX_RAW_SKILLS) {
            throw new AiResponseValidationException(
                    "AI resume parser exceeded maximum raw skills limit (" + rawSkills.size() + ")."
            );
        }

        Map<String, ParsedResumeSkillDto> deduplicated = new LinkedHashMap<>();
        for (RawAiParsedResumePayload.RawSkill item : rawSkills) {
            if (item == null) {
                continue;
            }
            String cleanName = cleanSingleLine(item.getSkillName(), 100);
            if (cleanName == null) {
                continue;
            }

            BigDecimal years = item.getYearsExperience();
            if (years != null) {
                if (years.compareTo(BigDecimal.ZERO) < 0 || years.compareTo(MAX_EXPERIENCE_YEARS) > 0) {
                    throw new AiResponseValidationException(
                            "AI resume parser returned invalid skill yearsExperience (" + years + ") for '" + cleanName + "'."
                    );
                }
                years = years.setScale(1, RoundingMode.HALF_UP);
            }

            ProficiencyLevel level = parseProficiencyLevel(item.getProficiencyLevel());
            boolean primary = Boolean.TRUE.equals(item.getPrimary()) || Boolean.TRUE.equals(item.getIsPrimary());

            String key = cleanName.toLowerCase(Locale.ROOT);
            ParsedResumeSkillDto existing = deduplicated.get(key);
            if (existing == null) {
                if (deduplicated.size() < MAX_SKILLS) {
                    deduplicated.put(key, ParsedResumeSkillDto.builder()
                            .skillName(cleanName)
                            .yearsExperience(years)
                            .proficiencyLevel(level)
                            .primary(primary)
                            .build());
                }
            } else {
                if (primary) {
                    existing.setPrimary(true);
                }
                if (existing.getYearsExperience() == null && years != null) {
                    existing.setYearsExperience(years);
                }
                if (existing.getProficiencyLevel() == null && level != null) {
                    existing.setProficiencyLevel(level);
                }
            }
        }
        return List.copyOf(deduplicated.values());
    }

    private List<ParsedResumeExperienceDto> validateExperiences(
            List<RawAiParsedResumePayload.RawExperience> rawExperiences,
            LinkedHashSet<String> warnings
    ) {
        if (rawExperiences == null || rawExperiences.isEmpty()) {
            return List.of();
        }
        if (rawExperiences.size() > MAX_RAW_EXPERIENCES) {
            throw new AiResponseValidationException(
                    "AI resume parser exceeded maximum work experiences count (" + rawExperiences.size() + ")."
            );
        }

        List<ParsedResumeExperienceDto> result = new ArrayList<>();
        for (RawAiParsedResumePayload.RawExperience item : rawExperiences) {
            if (item == null) {
                continue;
            }
            String company = cleanSingleLine(item.getCompanyName(), 200);
            String designation = cleanSingleLine(item.getDesignation(), 150);
            if (company == null && designation == null) {
                continue;
            }
            if (company == null || designation == null) {
                addWarning(warnings, "Skipped incomplete experience entry missing company or designation ("
                        + (company != null ? company : designation) + ").");
                continue;
            }

            String label = designation + " at " + company;
            boolean current = Boolean.TRUE.equals(item.getCurrent()) || Boolean.TRUE.equals(item.getIsCurrent());
            if (isPresentMarker(item.getEndDate())) {
                current = true;
            }

            LocalDate startDate = parseFlexibleDate(item.getStartDate(), label + " startDate", warnings);
            LocalDate endDate = current ? null : parseFlexibleDate(item.getEndDate(), label + " endDate", warnings);

            if (startDate == null) {
                addWarning(warnings, "Missing start date for experience '" + label + "'; add start date before approving.");
            }
            if (!current && startDate != null && endDate != null && endDate.isBefore(startDate)) {
                throw new AiResponseValidationException(
                        "AI resume parser returned endDate (" + endDate + ") preceding startDate (" + startDate + ") for '" + label + "'."
                );
            }

            result.add(ParsedResumeExperienceDto.builder()
                    .companyName(company)
                    .designation(designation)
                    .location(cleanSingleLine(item.getLocation(), 150))
                    .startDate(startDate)
                    .endDate(endDate)
                    .current(current)
                    .description(cleanProse(item.getDescription(), 5000))
                    .skillsUsed(sanitizeStringList(item.getSkillsUsed(), MAX_SKILLS_PER_EXP, 100))
                    .build());

            if (result.size() >= MAX_EXPERIENCES) {
                addWarning(warnings, "Work experiences truncated to maximum allowed " + MAX_EXPERIENCES + " entries.");
                break;
            }
        }
        return List.copyOf(result);
    }

    private List<ParsedResumeEducationDto> validateEducations(
            List<RawAiParsedResumePayload.RawEducation> rawEducations,
            LinkedHashSet<String> warnings
    ) {
        if (rawEducations == null || rawEducations.isEmpty()) {
            return List.of();
        }
        if (rawEducations.size() > MAX_RAW_EDUCATIONS) {
            throw new AiResponseValidationException(
                    "AI resume parser exceeded maximum education count (" + rawEducations.size() + ")."
            );
        }

        List<ParsedResumeEducationDto> result = new ArrayList<>();
        for (RawAiParsedResumePayload.RawEducation item : rawEducations) {
            if (item == null) {
                continue;
            }
            String institution = cleanSingleLine(item.getInstitution(), 200);
            String degree = cleanSingleLine(item.getDegree(), 150);
            String field = cleanSingleLine(item.getFieldOfStudy(), 150);
            if (institution == null && degree == null && field == null) {
                continue;
            }
            if (institution == null || degree == null || field == null) {
                addWarning(warnings, "Skipped incomplete education entry missing institution, degree, or field of study ("
                        + (institution != null ? institution : (degree != null ? degree : field)) + ").");
                continue;
            }

            String label = degree + " at " + institution;
            LocalDate startDate = parseFlexibleDate(item.getStartDate(), label + " startDate", warnings);
            LocalDate endDate = parseFlexibleDate(item.getEndDate(), label + " endDate", warnings);
            String country = normalizeCountry(item.getCountry(), 100);

            if (startDate == null) {
                addWarning(warnings, "Missing start date for education '" + label + "'; add start date before approving.");
            }
            if (country == null) {
                addWarning(warnings, "Missing country for education '" + label + "'; specify country before approving.");
            }
            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                throw new AiResponseValidationException(
                        "AI resume parser returned education endDate (" + endDate + ") preceding startDate (" + startDate + ") for '" + label + "'."
                );
            }

            result.add(ParsedResumeEducationDto.builder()
                    .institution(institution)
                    .degree(degree)
                    .fieldOfStudy(field)
                    .startDate(startDate)
                    .endDate(endDate)
                    .gradeOrGpa(cleanSingleLine(item.getGradeOrGpa(), 50))
                    .country(country)
                    .build());

            if (result.size() >= MAX_EDUCATIONS) {
                addWarning(warnings, "Education entries truncated to maximum allowed " + MAX_EDUCATIONS + " entries.");
                break;
            }
        }
        return List.copyOf(result);
    }

    private List<ParsedResumeProjectDto> validateProjects(
            List<RawAiParsedResumePayload.RawProject> rawProjects,
            LinkedHashSet<String> warnings
    ) {
        if (rawProjects == null || rawProjects.isEmpty()) {
            return List.of();
        }
        if (rawProjects.size() > MAX_RAW_PROJECTS) {
            throw new AiResponseValidationException(
                    "AI resume parser exceeded maximum projects count (" + rawProjects.size() + ")."
            );
        }

        List<ParsedResumeProjectDto> result = new ArrayList<>();
        for (RawAiParsedResumePayload.RawProject item : rawProjects) {
            if (item == null) {
                continue;
            }
            String name = cleanSingleLine(item.getName(), 150);
            if (name == null) {
                continue;
            }
            String validUrl = sanitizeUrl(item.getUrl(), "project '" + name + "' url", warnings);

            result.add(ParsedResumeProjectDto.builder()
                    .name(name)
                    .description(cleanProse(item.getDescription(), 1500))
                    .technologies(sanitizeStringList(item.getTechnologies(), MAX_TECH_PER_PROJECT, 100))
                    .url(validUrl)
                    .build());

            if (result.size() >= MAX_PROJECTS) {
                addWarning(warnings, "Projects truncated to maximum allowed " + MAX_PROJECTS + " entries.");
                break;
            }
        }
        return List.copyOf(result);
    }

    private LocalDate parseFlexibleDate(String rawDate, String contextLabel, LinkedHashSet<String> warnings) {
        if (rawDate == null || rawDate.isBlank() || isPresentMarker(rawDate)) {
            return null;
        }
        String token = rawDate.trim();

        try {
            if (token.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                LocalDate parsed = LocalDate.parse(token, DateTimeFormatter.ISO_LOCAL_DATE);
                if (parsed.getYear() >= 1950 && parsed.getYear() <= 2040) {
                    return parsed;
                }
            }
            Matcher ym = YEAR_MONTH.matcher(token);
            if (ym.matches()) {
                int year = Integer.parseInt(ym.group(1));
                int month = Integer.parseInt(ym.group(2));
                if (year >= 1950 && year <= 2040) {
                    addWarning(warnings, "Normalized partial date '" + token + "' to first day of month for " + contextLabel + "; please verify.");
                    return YearMonth.of(year, month).atDay(1);
                }
            }
            Matcher my = MONTH_YEAR.matcher(token);
            if (my.matches()) {
                int month = Integer.parseInt(my.group(1));
                int year = Integer.parseInt(my.group(2));
                if (year >= 1950 && year <= 2040) {
                    addWarning(warnings, "Normalized partial date '" + token + "' to first day of month for " + contextLabel + "; please verify.");
                    return YearMonth.of(year, month).atDay(1);
                }
            }
            Matcher y = YEAR_ONLY.matcher(token);
            if (y.matches()) {
                int year = Integer.parseInt(y.group(1));
                if (year >= 1950 && year <= 2040) {
                    addWarning(warnings, "Normalized year-only date '" + token + "' to January 1 for " + contextLabel + "; please verify.");
                    return LocalDate.of(year, 1, 1);
                }
            }
        } catch (DateTimeParseException | NumberFormatException ignored) {
            // Fall through to warning
        }

        addWarning(warnings, "Could not parse date '" + truncate(token, 40) + "' for " + contextLabel + "; please enter manually.");
        return null;
    }

    private boolean isPresentMarker(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        return norm.equals("present")
                || norm.equals("current")
                || norm.equals("ongoing")
                || norm.equals("now")
                || norm.equals("today")
                || norm.equals("heute")
                || norm.equals("aktuell");
    }

    private String sanitizeUrl(String rawUrl, String fieldLabel, LinkedHashSet<String> warnings) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String candidate = rawUrl.trim();
        if (!candidate.toLowerCase(Locale.ROOT).startsWith("http://")
                && !candidate.toLowerCase(Locale.ROOT).startsWith("https://")) {
            if (candidate.startsWith("www.") || candidate.matches("^(?i)[a-z0-9.-]+\\.[a-z]{2,63}/.*$")) {
                candidate = "https://" + candidate;
            }
        }
        candidate = truncate(candidate, 255);
        if (!isValidHttpUrl(candidate)) {
            addWarning(warnings, "Discarded malformed URL for " + fieldLabel + "; enter a valid https:// URL manually if needed.");
            return null;
        }
        return candidate;
    }

    private boolean isValidHttpUrl(String value) {
        if (value == null || value.isEmpty() || URL_UNSAFE_WHITESPACE.matcher(value).find()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return false;
            }
            if (uri.getUserInfo() != null) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || !HOST_PATTERN.matcher(host).matches()) {
                return false;
            }
            int port = uri.getPort();
            return port == -1 || (port >= 1 && port <= 65535);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private ProficiencyLevel parseProficiencyLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = normalizeEnumToken(raw);
        return switch (norm) {
            case "BEGINNER", "BASIC", "NOVICE", "JUNIOR" -> ProficiencyLevel.BEGINNER;
            case "INTERMEDIATE", "COMPETENT", "WORKING" -> ProficiencyLevel.INTERMEDIATE;
            case "ADVANCED", "PROFICIENT", "SENIOR", "STRONG" -> ProficiencyLevel.ADVANCED;
            case "EXPERT", "PRINCIPAL", "MASTER", "ARCHITECT" -> ProficiencyLevel.EXPERT;
            default -> null;
        };
    }

    private WorkAuthorizationStatus parseWorkAuthorization(String raw, LinkedHashSet<String> warnings) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = normalizeEnumToken(raw);
        return switch (norm) {
            case "EU_CITIZEN", "EU", "EEA_CITIZEN", "EU_PASSPORT" -> WorkAuthorizationStatus.EU_CITIZEN;
            case "BLUE_CARD_HOLDER", "BLUE_CARD", "EU_BLUE_CARD" -> WorkAuthorizationStatus.BLUE_CARD_HOLDER;
            case "PERMANENT_RESIDENT", "PR", "PERMANENT_RESIDENCY" -> WorkAuthorizationStatus.PERMANENT_RESIDENT;
            case "NEEDS_SPONSORSHIP", "REQUIRES_SPONSORSHIP", "VISA_REQUIRED" -> WorkAuthorizationStatus.NEEDS_SPONSORSHIP;
            case "OTHER", "WORK_PERMIT", "LOCAL_PERMIT", "VISA_HOLDER" -> WorkAuthorizationStatus.OTHER;
            default -> {
                addWarning(warnings, "Unrecognized work authorization status '" + truncate(raw.trim(), 50) + "'; verify in review.");
                yield null;
            }
        };
    }

    private RelocationPreference parseRelocationPreference(String raw, LinkedHashSet<String> warnings) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = normalizeEnumToken(raw);
        return switch (norm) {
            case "WILLING_TO_RELOCATE", "OPEN_TO_RELOCATE", "YES" -> RelocationPreference.WILLING_TO_RELOCATE;
            case "RELOCATING_SOON" -> RelocationPreference.RELOCATING_SOON;
            case "ALREADY_IN_EUROPE", "IN_EUROPE", "EU_BASED" -> RelocationPreference.ALREADY_IN_EUROPE;
            case "REMOTE_ONLY" -> RelocationPreference.REMOTE_ONLY;
            case "NOT_INTERESTED", "NOT_WILLING", "NO_RELOCATION", "NO" -> RelocationPreference.NOT_INTERESTED;
            default -> {
                addWarning(warnings, "Unrecognized relocation preference '" + truncate(raw.trim(), 50) + "'; verify in review.");
                yield null;
            }
        };
    }

    private List<String> sanitizeStringList(List<String> rawItems, int maxKeep, int maxItemLength) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String item : rawItems) {
            String cleaned = cleanSingleLine(item, maxItemLength);
            if (cleaned != null) {
                unique.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
                if (unique.size() >= maxKeep) {
                    break;
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private String normalizeCountry(String raw, int maxLength) {
        String cleaned = cleanSingleLine(raw, maxLength);
        if (cleaned == null) {
            return null;
        }
        String alias = COUNTRY_ALIASES.get(cleaned.toLowerCase(Locale.ROOT));
        return alias != null ? alias : truncate(cleaned, maxLength);
    }

    private void addWarning(LinkedHashSet<String> warnings, String rawWarning) {
        String cleaned = cleanSingleLine(rawWarning, 300);
        if (cleaned != null && warnings.size() < MAX_WARNINGS) {
            warnings.add(cleaned);
        }
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

    private String normalizeEnumToken(String raw) {
        return raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("_+", "_")
                .toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}
