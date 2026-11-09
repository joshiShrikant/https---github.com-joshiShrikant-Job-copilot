package com.eurojobcopilot.modules.job.normalization;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.ingestion.RawJobPosting;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizes, normalizes, and enriches {@link RawJobPosting} payloads from diverse external job boards
 * into validated {@link NormalizedJobData} records adhering to database column constraints.
 */
@Component
@RequiredArgsConstructor
public class JobNormalizer {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_COMPANY_LENGTH = 255;
    private static final int MAX_LOCATION_LENGTH = 255;
    private static final int MAX_CITY_LENGTH = 100;
    private static final int MAX_COUNTRY_LENGTH = 100;
    private static final int MAX_SNIPPET_LENGTH = 500;
    private static final int MAX_PROVIDER_LENGTH = 50;
    private static final int MAX_SOURCE_ID_LENGTH = 255;
    private static final int MAX_URL_LENGTH = 1000;
    private static final int MAX_SKILLS_COUNT = 20;

    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "<(?:script|style)[^>]*>.*?</(?:script|style)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern BLOCK_BREAK_PATTERN = Pattern.compile(
            "</?(?:p|div|li|br|h[1-6]|tr)[^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ANY_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern HORIZONTAL_WHITESPACE_PATTERN = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern EXCESS_NEWLINES_PATTERN = Pattern.compile("\\n{3,}");
    private static final Pattern INLINE_WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern ISO_CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

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
            Map.entry("great britain", "United Kingdom"),
            Map.entry("england", "United Kingdom"),
            Map.entry("ie", "Ireland"),
            Map.entry("irl", "Ireland"),
            Map.entry("ireland", "Ireland"),
            Map.entry("se", "Sweden"),
            Map.entry("swe", "Sweden"),
            Map.entry("sweden", "Sweden"),
            Map.entry("sverige", "Sweden"),
            Map.entry("ch", "Switzerland"),
            Map.entry("che", "Switzerland"),
            Map.entry("switzerland", "Switzerland"),
            Map.entry("schweiz", "Switzerland"),
            Map.entry("suisse", "Switzerland"),
            Map.entry("fr", "France"),
            Map.entry("fra", "France"),
            Map.entry("france", "France"),
            Map.entry("es", "Spain"),
            Map.entry("esp", "Spain"),
            Map.entry("spain", "Spain"),
            Map.entry("at", "Austria"),
            Map.entry("aut", "Austria"),
            Map.entry("austria", "Austria"),
            Map.entry("osterreich", "Austria"),
            Map.entry("dk", "Denmark"),
            Map.entry("dnk", "Denmark"),
            Map.entry("denmark", "Denmark"),
            Map.entry("fi", "Finland"),
            Map.entry("fin", "Finland"),
            Map.entry("finland", "Finland"),
            Map.entry("pt", "Portugal"),
            Map.entry("prt", "Portugal"),
            Map.entry("portugal", "Portugal"),
            Map.entry("pl", "Poland"),
            Map.entry("pol", "Poland"),
            Map.entry("poland", "Poland"),
            Map.entry("be", "Belgium"),
            Map.entry("bel", "Belgium"),
            Map.entry("belgium", "Belgium"),
            Map.entry("lu", "Luxembourg"),
            Map.entry("luxembourg", "Luxembourg")
    );

    private static final Map<String, String> CITY_TO_COUNTRY = Map.ofEntries(
            Map.entry("berlin", "Germany"),
            Map.entry("munich", "Germany"),
            Map.entry("munchen", "Germany"),
            Map.entry("hamburg", "Germany"),
            Map.entry("frankfurt", "Germany"),
            Map.entry("cologne", "Germany"),
            Map.entry("stuttgart", "Germany"),
            Map.entry("dusseldorf", "Germany"),
            Map.entry("frankfurt am main", "Germany"),
            Map.entry("karlsruhe", "Germany"),
            Map.entry("leipzig", "Germany"),
            Map.entry("nuremberg", "Germany"),
            Map.entry("nurnberg", "Germany"),
            Map.entry("bonn", "Germany"),
            Map.entry("dresden", "Germany"),
            Map.entry("amsterdam", "Netherlands"),
            Map.entry("rotterdam", "Netherlands"),
            Map.entry("utrecht", "Netherlands"),
            Map.entry("eindhoven", "Netherlands"),
            Map.entry("the hague", "Netherlands"),
            Map.entry("den haag", "Netherlands"),
            Map.entry("dublin", "Ireland"),
            Map.entry("cork", "Ireland"),
            Map.entry("stockholm", "Sweden"),
            Map.entry("gothenburg", "Sweden"),
            Map.entry("malmo", "Sweden"),
            Map.entry("zurich", "Switzerland"),
            Map.entry("geneva", "Switzerland"),
            Map.entry("basel", "Switzerland"),
            Map.entry("london", "United Kingdom"),
            Map.entry("manchester", "United Kingdom"),
            Map.entry("edinburgh", "United Kingdom"),
            Map.entry("paris", "France"),
            Map.entry("lyon", "France"),
            Map.entry("madrid", "Spain"),
            Map.entry("barcelona", "Spain"),
            Map.entry("copenhagen", "Denmark"),
            Map.entry("aarhus", "Denmark"),
            Map.entry("vienna", "Austria"),
            Map.entry("helsinki", "Finland"),
            Map.entry("lisbon", "Portugal"),
            Map.entry("porto", "Portugal"),
            Map.entry("warsaw", "Poland"),
            Map.entry("krakow", "Poland"),
            Map.entry("wroclaw", "Poland"),
            Map.entry("brussels", "Belgium"),
            Map.entry("antwerp", "Belgium"),
            Map.entry("ghent", "Belgium"),
            Map.entry("luxembourg", "Luxembourg"),
            Map.entry("esch sur alzette", "Luxembourg")
    );

    private static final Map<Pattern, String> CANONICAL_SKILLS = buildCanonicalSkillsDictionary();

    private final CanonicalHashGenerator canonicalHashGenerator;

    public Optional<NormalizedJobData> normalize(RawJobPosting raw) {
        if (raw == null) {
            return Optional.empty();
        }

        String title = cleanSingleLine(raw.title(), MAX_TITLE_LENGTH);
        String companyName = cleanSingleLine(raw.companyName(), MAX_COMPANY_LENGTH);
        String originalUrl = cleanUrl(raw.originalUrl());
        String sourceProvider = cleanSingleLine(raw.sourceProvider(), MAX_PROVIDER_LENGTH).toUpperCase(Locale.ROOT);

        if (title.isEmpty() || companyName.isEmpty() || originalUrl.isEmpty() || sourceProvider.isEmpty()) {
            return Optional.empty();
        }

        LocationParts locationParts = resolveLocation(raw.location(), raw.city(), raw.country(), raw.remoteFlag());
        if (locationParts.country().isEmpty()) {
            return Optional.empty();
        }

        String cleanDescription = sanitizeHtmlToText(raw.rawDescription());
        if (cleanDescription.isEmpty()) {
            cleanDescription = title + " at " + companyName + " (" + locationParts.location() + ")";
        }

        WorkArrangement workArrangement = inferWorkArrangement(
                raw.remoteFlag(),
                raw.workArrangementHint(),
                title,
                locationParts.location(),
                cleanDescription
        );
        EmploymentType employmentType = inferEmploymentType(raw.employmentTypeHint(), title, cleanDescription);
        ExperienceLevel experienceLevel = inferExperienceLevel(raw.experienceLevelHint(), title);
        SalaryData salary = normalizeSalary(
                raw.salaryMin(),
                raw.salaryMax(),
                raw.salaryCurrency(),
                raw.salaryPeriodHint()
        );
        JobSponsorStatus sponsorStatus = inferSponsorStatus(raw.visaSponsoredHint(), title, cleanDescription);
        List<String> skills = extractAndNormalizeSkills(raw.tags(), title + "\n" + cleanDescription);

        String normalizedCompanyName = canonicalHashGenerator.normalizeCompanyKey(companyName);
        String normalizedTitle = truncate(canonicalHashGenerator.normalizeTitleKey(title), MAX_TITLE_LENGTH);
        if (normalizedTitle.isEmpty()) {
            normalizedTitle = title.toLowerCase(Locale.ROOT);
        }

        String canonicalHash = canonicalHashGenerator.computeCanonicalHash(
                companyName,
                title,
                locationParts.country(),
                locationParts.city(),
                workArrangement
        );

        String applicationUrl = cleanUrl(raw.applicationUrl());

        String sourceJobId = cleanSingleLine(raw.sourceJobId(), MAX_SOURCE_ID_LENGTH);
        Instant postedAt = raw.postedAt() != null ? raw.postedAt() : Instant.now();

        return Optional.of(NormalizedJobData.builder()
                .canonicalHash(canonicalHash)
                .companyName(companyName)
                .normalizedCompanyName(normalizedCompanyName.isEmpty()
                        ? companyName.toLowerCase(Locale.ROOT)
                        : normalizedCompanyName)
                .title(title)
                .normalizedTitle(normalizedTitle)
                .location(locationParts.location())
                .city(locationParts.city().isEmpty() ? null : locationParts.city())
                .country(locationParts.country())
                .workArrangement(workArrangement)
                .employmentType(employmentType)
                .experienceLevel(experienceLevel)
                .salaryMin(salary.min())
                .salaryMax(salary.max())
                .salaryCurrency(salary.currency())
                .salaryPeriod(salary.period())
                .description(cleanDescription)
                .descriptionSnippet(buildSnippet(cleanDescription))
                .skills(skills)
                .sourceProvider(sourceProvider)
                .sourceJobId(sourceJobId.isEmpty() ? null : sourceJobId)
                .originalUrl(originalUrl)
                .applicationUrl(applicationUrl.isEmpty() ? null : applicationUrl)
                .sponsorStatus(sponsorStatus)
                .postedAt(postedAt)
                .expiresAt(raw.expiresAt())
                .build());
    }

    public String sanitizeHtmlToText(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }

        String cleaned = SCRIPT_STYLE_PATTERN.matcher(rawHtml).replaceAll(" ");
        cleaned = BLOCK_BREAK_PATTERN.matcher(cleaned).replaceAll("\n");
        cleaned = ANY_TAG_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = decodeHtmlEntities(cleaned);

        String[] lines = cleaned.split("\\R");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String normalizedLine = HORIZONTAL_WHITESPACE_PATTERN.matcher(line).replaceAll(" ").trim();
            if (!normalizedLine.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(normalizedLine);
            }
        }
        return EXCESS_NEWLINES_PATTERN.matcher(builder.toString()).replaceAll("\n\n").trim();
    }

    public String normalizeCountry(String rawCountry) {
        if (rawCountry == null || rawCountry.isBlank()) {
            return "";
        }
        String key = canonicalHashGenerator.normalizeGenericKey(rawCountry);
        if (COUNTRY_ALIASES.containsKey(key)) {
            return COUNTRY_ALIASES.get(key);
        }
        return truncate(cleanSingleLine(rawCountry, MAX_COUNTRY_LENGTH), MAX_COUNTRY_LENGTH);
    }

    private LocationParts resolveLocation(String rawLocation, String rawCity, String rawCountry, Boolean remoteFlag) {
        String city = cleanSingleLine(rawCity, MAX_CITY_LENGTH);
        String country = normalizeCountry(rawCountry);
        String location = cleanSingleLine(rawLocation, MAX_LOCATION_LENGTH);

        if ((city.isEmpty() || country.isEmpty()) && !location.isEmpty()) {
            String[] segments = location.split("[,;|/]");
            for (String segment : segments) {
                String token = segment.trim();
                if (token.isEmpty()) {
                    continue;
                }
                String tokenKey = canonicalHashGenerator.normalizeGenericKey(token);
                if (COUNTRY_ALIASES.containsKey(tokenKey)) {
                    if (country.isEmpty()) {
                        country = COUNTRY_ALIASES.get(tokenKey);
                    }
                } else if (!"remote".equals(tokenKey) && !"europe".equals(tokenKey) && city.isEmpty()) {
                    city = truncate(token, MAX_CITY_LENGTH);
                }
            }
        }

        if (country.isEmpty() && !city.isEmpty()) {
            String cityKey = canonicalHashGenerator.normalizeGenericKey(city);
            if (CITY_TO_COUNTRY.containsKey(cityKey)) {
                country = CITY_TO_COUNTRY.get(cityKey);
            }
        }

        if (country.isEmpty() && Boolean.TRUE.equals(remoteFlag)) {
            country = "Europe";
        }

        if (location.isEmpty()) {
            if (!city.isEmpty() && !country.isEmpty()) {
                location = city + ", " + country;
            } else if (!country.isEmpty()) {
                location = country;
            }
        }

        return new LocationParts(
                truncate(location, MAX_LOCATION_LENGTH),
                truncate(city, MAX_CITY_LENGTH),
                truncate(country, MAX_COUNTRY_LENGTH)
        );
    }

    private WorkArrangement inferWorkArrangement(
            Boolean remoteFlag,
            String hint,
            String title,
            String location,
            String description
    ) {
        String hintLower = safeLower(hint);
        if (hintLower.contains("hybrid")) {
            return WorkArrangement.HYBRID;
        }
        if (hintLower.contains("remote")) {
            return WorkArrangement.REMOTE;
        }
        if (hintLower.contains("onsite") || hintLower.contains("on-site") || hintLower.contains("office")) {
            return WorkArrangement.ONSITE;
        }
        if (Boolean.TRUE.equals(remoteFlag)) {
            return WorkArrangement.REMOTE;
        }

        String combined = safeLower(title + " " + location);
        if (combined.contains("hybrid")) {
            return WorkArrangement.HYBRID;
        }
        if (combined.contains("remote") || combined.contains("home office")) {
            return WorkArrangement.REMOTE;
        }

        String descLower = safeLower(description);
        if (descLower.contains("hybrid work") || descLower.contains("days in office") || descLower.contains("hybrid model")) {
            return WorkArrangement.HYBRID;
        }
        if (descLower.contains("100% remote") || descLower.contains("fully remote") || descLower.contains("remote-first")) {
            return WorkArrangement.REMOTE;
        }
        return WorkArrangement.ONSITE;
    }

    private EmploymentType inferEmploymentType(String hint, String title, String description) {
        String combined = safeLower(hint + " " + title);
        if (combined.contains("intern") || combined.contains("working student") || combined.contains("werkstudent")) {
            return EmploymentType.INTERNSHIP;
        }
        if (combined.contains("freelance")) {
            return EmploymentType.FREELANCE;
        }
        if (combined.contains("contract") || combined.contains("contractor")) {
            return EmploymentType.CONTRACT;
        }
        if (combined.contains("part-time")
                || combined.contains("part time")
                || combined.contains("part_time")
                || combined.contains("teilzeit")) {
            return EmploymentType.PART_TIME;
        }

        String descLower = safeLower(description);
        if (descLower.contains("fixed-term contract") || descLower.contains("freelance contract")) {
            return EmploymentType.CONTRACT;
        }
        return EmploymentType.FULL_TIME;
    }

    private ExperienceLevel inferExperienceLevel(String hint, String title) {
        String combined = safeLower(hint + " " + title);
        if (combined.contains("principal") || combined.contains("distinguished") || combined.contains("staff ")) {
            return ExperienceLevel.PRINCIPAL;
        }
        if (combined.contains("lead") || combined.contains("engineering manager") || combined.contains("architect")) {
            return ExperienceLevel.LEAD;
        }
        if (combined.contains("senior") || combined.contains("sr.") || combined.contains("sr ")) {
            return ExperienceLevel.SENIOR;
        }
        if (combined.contains("junior") || combined.contains("jr.") || combined.contains("jr ")) {
            return ExperienceLevel.JUNIOR;
        }
        if (combined.contains("entry") || combined.contains("graduate") || combined.contains("intern")) {
            return ExperienceLevel.ENTRY;
        }
        return ExperienceLevel.MID;
    }

    private SalaryData normalizeSalary(
            BigDecimal rawMin,
            BigDecimal rawMax,
            String rawCurrency,
            String periodHint
    ) {
        BigDecimal min = sanitizeAmount(rawMin);
        BigDecimal max = sanitizeAmount(rawMax);

        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal swap = min;
            min = max;
            max = swap;
        }

        String currency = null;
        if (min != null || max != null) {
            String candidate = rawCurrency == null ? "" : rawCurrency.trim().toUpperCase(Locale.ROOT);
            if ("€".equals(candidate)) {
                currency = "EUR";
            } else if ("£".equals(candidate)) {
                currency = "GBP";
            } else if (ISO_CURRENCY_PATTERN.matcher(candidate).matches()) {
                currency = candidate;
            } else {
                currency = "EUR";
            }
        }

        SalaryPeriod period = SalaryPeriod.YEARLY;
        String hintLower = safeLower(periodHint);
        if (hintLower.contains("month")) {
            period = SalaryPeriod.MONTHLY;
        } else if (hintLower.contains("day") || hintLower.contains("daily")) {
            period = SalaryPeriod.DAILY;
        } else if (hintLower.contains("hour")) {
            period = SalaryPeriod.HOURLY;
        }

        return new SalaryData(min, max, currency, period);
    }

    private BigDecimal sanitizeAmount(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return value;
    }

    private JobSponsorStatus inferSponsorStatus(Boolean visaSponsoredHint, String title, String description) {
        String text = safeLower(title + "\n" + description);

        if (text.contains("no visa sponsorship")
                || text.contains("not sponsor visas")
                || text.contains("unable to sponsor")
                || text.contains("without visa sponsorship")
                || text.contains("sponsorship is not available")
                || text.contains("no sponsorship available")
                || text.contains("no sponsorship offered")
                || text.contains("sponsorship unavailable")
                || text.contains("must already have work authorization")
                || text.contains("valid eu work permit required")
                || text.contains("citizenship required")) {
            return JobSponsorStatus.NO_SPONSORSHIP;
        }

        if (Boolean.TRUE.equals(visaSponsoredHint)
                || text.contains("visa sponsorship")
                || text.contains("sponsor visa")
                || text.contains("eu blue card")
                || text.contains("blaue karte")
                || text.contains("highly skilled migrant")
                || text.contains("ind sponsor")
                || text.contains("kennismigrant")
                || text.contains("work permit support")
                || text.contains("work permit sponsorship")
                || text.contains("critical skills employment permit")
                || text.contains("chancenkarte")
                || text.contains("passeport talent")
                || text.contains("red-white-red card")
                || text.contains("rot-weiss-rot")) {
            return JobSponsorStatus.SPONSORSHIP_MENTIONED;
        }

        if (text.contains("relocation support")
                || text.contains("relocation package")
                || text.contains("relocation assistance")
                || text.contains("relocation budget")
                || text.contains("relocation offered")
                || text.contains("relocation allowance")
                || text.contains("relocation reimbursement")) {
            return JobSponsorStatus.RELOCATION_OFFERED;
        }

        return JobSponsorStatus.UNCONFIRMED;
    }

    private List<String> extractAndNormalizeSkills(List<String> rawTags, String searchableText) {
        Map<String, String> uniqueSkills = new LinkedHashMap<>();

        if (rawTags != null) {
            for (String tag : rawTags) {
                String cleaned = cleanSingleLine(tag, 60);
                if (cleaned.length() >= 2 && cleaned.length() <= 40) {
                    String canonical = matchCanonicalSkill(cleaned).orElse(cleaned);
                    uniqueSkills.putIfAbsent(canonical.toLowerCase(Locale.ROOT), canonical);
                    if (uniqueSkills.size() >= MAX_SKILLS_COUNT) {
                        return List.copyOf(uniqueSkills.values());
                    }
                }
            }
        }

        if (searchableText != null && !searchableText.isBlank()) {
            for (Map.Entry<Pattern, String> entry : CANONICAL_SKILLS.entrySet()) {
                if (entry.getKey().matcher(searchableText).find()) {
                    String canonical = entry.getValue();
                    uniqueSkills.putIfAbsent(canonical.toLowerCase(Locale.ROOT), canonical);
                    if (uniqueSkills.size() >= MAX_SKILLS_COUNT) {
                        break;
                    }
                }
            }
        }

        return List.copyOf(uniqueSkills.values());
    }

    private Optional<String> matchCanonicalSkill(String tag) {
        for (Map.Entry<Pattern, String> entry : CANONICAL_SKILLS.entrySet()) {
            if (entry.getKey().matcher(tag).matches()) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private String buildSnippet(String cleanText) {
        String singleLine = INLINE_WHITESPACE_PATTERN.matcher(cleanText).replaceAll(" ").trim();
        if (singleLine.length() <= MAX_SNIPPET_LENGTH) {
            return singleLine;
        }
        int cutoff = singleLine.lastIndexOf(' ', MAX_SNIPPET_LENGTH - 3);
        if (cutoff < 100) {
            cutoff = MAX_SNIPPET_LENGTH - 3;
        }
        return singleLine.substring(0, cutoff).trim() + "...";
    }

    private String decodeHtmlEntities(String text) {
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&ndash;", "-")
                .replace("&mdash;", "—")
                .replace("&euro;", "€");
    }

    private String cleanSingleLine(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String collapsed = INLINE_WHITESPACE_PATTERN.matcher(raw).replaceAll(" ").trim();
        return truncate(collapsed, maxLength);
    }

    private String cleanUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String trimmed = rawUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "";
        }
        return truncate(trimmed, MAX_URL_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static Map<Pattern, String> buildCanonicalSkillsDictionary() {
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
        return compiled;
    }

    private record LocationParts(String location, String city, String country) {
    }

    private record SalaryData(BigDecimal min, BigDecimal max, String currency, SalaryPeriod period) {
    }
}

