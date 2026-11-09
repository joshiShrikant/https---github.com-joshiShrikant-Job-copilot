package com.eurojobcopilot.modules.sponsorship.ind;

import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipProfile;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.entity.VisaSponsorshipStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure country- and vacancy-aware sponsorship classifier across all 6 canonical statuses.
 *
 * <p>Enforces the domain rule that recognized sponsor accreditation at employer level never assumes
 * every individual vacancy offers sponsorship: explicit negative vacancy cues strictly override
 * employer registry status, and unconfirmed vacancies carry a mandatory non-assumption caveat.
 */
@Component
public class SponsorshipClassifier {

    public record VacancyAssessment(
            VisaSponsorshipStatus vacancyStatus,
            boolean explicitRejection,
            boolean explicitSponsorshipMention,
            boolean relocationMention,
            boolean plausibilityClue,
            List<String> signals
    ) {
    }

    public record ClassificationResult(
            VisaSponsorshipStatus status,
            VisaSponsorshipStatus vacancyStatus,
            boolean vacancySponsorshipConfirmed,
            boolean vacancySponsorshipAssumed,
            boolean companyRecognizedSponsor,
            SponsorConfidenceLevel confidenceLevel,
            String registrySource,
            String registryIdentifier,
            String officialRegisteredName,
            String sourceUrl,
            String evidenceSummary,
            List<String> evidenceSignals,
            String vacancyCaveat
    ) {
    }

    public VacancyAssessment assessVacancy(Job job, JobAiAnalysis aiAnalysis, String additionalPostingText) {
        StringBuilder combined = new StringBuilder();
        if (job != null) {
            if (job.getTitle() != null) {
                combined.append(job.getTitle()).append('\n');
            }
            if (job.getDescription() != null) {
                combined.append(job.getDescription()).append('\n');
            }
        }
        if (additionalPostingText != null && !additionalPostingText.isBlank()) {
            combined.append(additionalPostingText).append('\n');
        }
        String text = combined.toString().toLowerCase(Locale.ROOT);

        List<String> signals = new ArrayList<>();
        boolean jobMarkedNoSponsorship = job != null && job.getSponsorStatus() == JobSponsorStatus.NO_SPONSORSHIP;
        boolean aiRejectedSponsorship = aiAnalysis != null && Boolean.FALSE.equals(aiAnalysis.getSponsorshipOffered());
        boolean textRejected = containsAny(
                text,
                "no visa sponsorship",
                "not sponsor visas",
                "unable to sponsor",
                "without visa sponsorship",
                "sponsorship is not available",
                "no sponsorship available",
                "no sponsorship offered",
                "sponsorship unavailable",
                "must already have work authorization",
                "valid eu work permit required",
                "existing work permit required",
                "eu citizenship required",
                "citizenship required",
                "must hold valid work permit"
        );
        boolean explicitRejection = jobMarkedNoSponsorship || aiRejectedSponsorship || textRejected;
        if (explicitRejection) {
            signals.add("Vacancy posting or AI extraction indicates visa sponsorship is unavailable or existing authorization is required.");
            return new VacancyAssessment(
                    VisaSponsorshipStatus.UNAVAILABLE,
                    true,
                    false,
                    false,
                    false,
                    List.copyOf(signals)
            );
        }

        boolean aiConfirmedSponsorship = aiAnalysis != null && Boolean.TRUE.equals(aiAnalysis.getSponsorshipOffered());
        boolean textConfirmedSponsorship = containsAny(
                text,
                "visa sponsorship",
                "sponsor visa",
                "sponsorship provided",
                "visa support",
                "eu blue card",
                "blaue karte",
                "highly skilled migrant",
                "ind sponsor",
                "kennismigrant",
                "work permit support",
                "work permit sponsorship",
                "critical skills employment permit",
                "chancenkarte",
                "passeport talent",
                "red-white-red card",
                "rot-weiss-rot",
                "tech visa",
                "fast-track scheme"
        );
        boolean jobMarkedSponsorshipMentioned = job != null && job.getSponsorStatus() == JobSponsorStatus.SPONSORSHIP_MENTIONED;
        boolean explicitSponsorship = aiConfirmedSponsorship || textConfirmedSponsorship || jobMarkedSponsorshipMentioned;
        if (explicitSponsorship) {
            signals.add("Vacancy evidence explicitly mentions visa sponsorship or statutory European work permit support.");
        }

        boolean aiRelocation = aiAnalysis != null && Boolean.TRUE.equals(aiAnalysis.getRelocationAssistanceOffered());
        boolean textRelocation = containsAny(
                text,
                "relocation support",
                "relocation package",
                "relocation assistance",
                "relocation budget",
                "relocation offered",
                "relocation allowance",
                "relocation reimbursement"
        );
        boolean jobMarkedRelocation = job != null && job.getSponsorStatus() == JobSponsorStatus.RELOCATION_OFFERED;
        boolean relocationMention = aiRelocation || textRelocation || jobMarkedRelocation;
        if (relocationMention) {
            signals.add("Vacancy mentions relocation assistance or allowance.");
        }

        boolean aiEnglishLang = aiAnalysis != null
                && aiAnalysis.getLanguageRequirements() != null
                && aiAnalysis.getLanguageRequirements().stream()
                        .anyMatch(lang -> lang != null && lang.toLowerCase(Locale.ROOT).contains("english"));
        boolean textPlausibility = containsAny(
                text,
                "english speaking",
                "english-speaking",
                "english is our working language",
                "fluent english",
                "international team",
                "international candidates",
                "open to international applicants",
                "worldwide candidates",
                "eu/non-eu"
        );
        boolean plausibility = aiEnglishLang || textPlausibility;
        if (plausibility && !explicitSponsorship && !relocationMention) {
            signals.add("Posting indicates an international/English-speaking hiring environment where permit sponsorship may be possible but unconfirmed.");
        }

        VisaSponsorshipStatus vacancyStatus;
        if (explicitSponsorship) {
            vacancyStatus = VisaSponsorshipStatus.SPONSORSHIP_MENTIONED;
        } else if (relocationMention) {
            vacancyStatus = VisaSponsorshipStatus.RELOCATION_ASSISTANCE_MENTIONED;
        } else if (plausibility) {
            vacancyStatus = VisaSponsorshipStatus.POSSIBLE_UNCONFIRMED;
        } else {
            vacancyStatus = VisaSponsorshipStatus.NOT_MENTIONED;
        }

        return new VacancyAssessment(
                vacancyStatus,
                false,
                explicitSponsorship,
                relocationMention,
                plausibility,
                List.copyOf(signals)
        );
    }

    public ClassificationResult classify(
            String requestedCompanyName,
            CountrySponsorshipProfile countryProfile,
            Company company,
            List<CompanySponsorshipRecord> countryCompanyRecords,
            OfficialSponsorRegistryEntry officialRegistryEntry,
            VacancyAssessment vacancyAssessment,
            boolean officialRegisterSyncedForCountry,
            Job linkedJob
    ) {
        List<CompanySponsorshipRecord> safeRecords = countryCompanyRecords == null ? List.of() : countryCompanyRecords;

        CompanySponsorshipRecord activeRecord = safeRecords.stream()
                .filter(rec -> rec.getStatus() == SponsorshipRecordStatus.RECOGNIZED_SPONSOR
                        || rec.getStatus() == SponsorshipRecordStatus.VISA_CAPABLE)
                .findFirst()
                .orElse(null);

        CompanySponsorshipRecord revokedOrSuspended = safeRecords.stream()
                .filter(rec -> rec.getStatus() == SponsorshipRecordStatus.REVOKED
                        || rec.getStatus() == SponsorshipRecordStatus.SUSPENDED)
                .findFirst()
                .orElse(null);

        boolean registryEntryActive = officialRegistryEntry != null
                && officialRegistryEntry.isActive()
                && (officialRegistryEntry.getStatus() == SponsorshipRecordStatus.RECOGNIZED_SPONSOR
                || officialRegistryEntry.getStatus() == SponsorshipRecordStatus.VISA_CAPABLE);

        boolean companyVerifiedInCountry = company != null
                && company.isVerifiedSponsor()
                && (activeRecord != null
                || registryEntryActive
                || countryProfile.country().equalsIgnoreCase(company.getHeadquartersCountry())
                || (linkedJob != null && linkedJob.getSponsorStatus() == JobSponsorStatus.VERIFIED_SPONSOR));

        boolean companyRecognizedSponsor = registryEntryActive || activeRecord != null || companyVerifiedInCountry;

        String registrySource = firstNonBlank(
                officialRegistryEntry != null ? officialRegistryEntry.getRegistrySource() : null,
                activeRecord != null ? activeRecord.getRegistrySource() : null,
                revokedOrSuspended != null ? revokedOrSuspended.getRegistrySource() : null,
                countryProfile.registrySource()
        );
        String registryIdentifier = firstNonBlank(
                officialRegistryEntry != null ? officialRegistryEntry.getRegistryIdentifier() : null,
                activeRecord != null ? activeRecord.getRegistryIdentifier() : null,
                revokedOrSuspended != null ? revokedOrSuspended.getRegistryIdentifier() : null
        );
        String officialName = firstNonBlank(
                officialRegistryEntry != null ? officialRegistryEntry.getOfficialName() : null,
                activeRecord != null ? activeRecord.getOfficialRegisteredName() : null,
                company != null ? company.getName() : null,
                requestedCompanyName
        );
        String sourceUrl = firstNonBlank(
                officialRegistryEntry != null ? officialRegistryEntry.getSourceUrl() : null,
                activeRecord != null ? activeRecord.getSourceUrl() : null,
                revokedOrSuspended != null ? revokedOrSuspended.getSourceUrl() : null,
                countryProfile.officialSourceUrl()
        );

        List<String> signals = new ArrayList<>();
        if (officialRegistryEntry != null && officialRegistryEntry.isActive()) {
            signals.add("Matched active entry in " + officialRegistryEntry.getRegistrySource()
                    + " for " + countryProfile.country()
                    + " (" + officialRegistryEntry.getOfficialName()
                    + (officialRegistryEntry.getRegistryIdentifier() != null
                    ? ", " + officialRegistryEntry.getRegistryIdentifier() : "") + ").");
        }
        for (CompanySponsorshipRecord record : safeRecords) {
            if (record.getStatus() == SponsorshipRecordStatus.RECOGNIZED_SPONSOR
                    || record.getStatus() == SponsorshipRecordStatus.VISA_CAPABLE) {
                signals.add("Employer sponsorship record: " + record.getStatus().name()
                        + " in " + record.getCountry() + " (" + record.getRegistrySource() + ").");
            } else {
                signals.add("Employer sponsorship notice: " + record.getStatus().name()
                        + " in " + record.getCountry() + " (" + record.getRegistrySource() + ").");
            }
        }
        if (countryProfile.officialRegisterAvailable() && !officialRegisterSyncedForCountry && !registryEntryActive && activeRecord == null) {
            signals.add("Local snapshot for " + countryProfile.registryName() + " has not been synchronized yet.");
        } else if (!countryProfile.officialRegisterAvailable()) {
            signals.add(countryProfile.country() + " evaluates work permits per job offer via " + countryProfile.registryName() + ".");
        }
        signals.addAll(vacancyAssessment.signals());

        // 1. Explicit vacancy refusal ALWAYS overrides employer-level sponsor accreditation
        if (vacancyAssessment.explicitRejection()) {
            String caveat = companyRecognizedSponsor
                    ? "Although this employer holds recognized sponsor accreditation, the specific vacancy states visa sponsorship is unavailable. Recognized sponsorship never guarantees sponsorship for every opening."
                    : "Vacancy explicitly declines visa sponsorship or mandates existing work authorization.";
            return new ClassificationResult(
                    VisaSponsorshipStatus.UNAVAILABLE,
                    VisaSponsorshipStatus.UNAVAILABLE,
                    false,
                    false,
                    companyRecognizedSponsor,
                    SponsorConfidenceLevel.LOW,
                    registrySource,
                    registryIdentifier,
                    officialName,
                    sourceUrl,
                    caveat,
                    List.copyOf(signals),
                    caveat
            );
        }

        // 2. Revoked sponsor status without explicit positive vacancy override
        if (revokedOrSuspended != null && revokedOrSuspended.getStatus() == SponsorshipRecordStatus.REVOKED && !companyRecognizedSponsor) {
            String summary = "Official sponsor record in " + countryProfile.country() + " is marked REVOKED.";
            return new ClassificationResult(
                    VisaSponsorshipStatus.UNAVAILABLE,
                    vacancyAssessment.vacancyStatus(),
                    false,
                    false,
                    false,
                    SponsorConfidenceLevel.LOW,
                    registrySource,
                    registryIdentifier,
                    officialName,
                    sourceUrl,
                    summary,
                    List.copyOf(signals),
                    "Sponsor accreditation has been revoked; treat visa sponsorship as unavailable unless newly re-licensed."
            );
        }

        // 3. Verified official sponsor match (while explicitly refusing to assume all vacancies sponsor)
        if (companyRecognizedSponsor) {
            boolean vacancyConfirmed = vacancyAssessment.explicitSponsorshipMention();
            SponsorConfidenceLevel confidence = (company != null
                    && (company.getSponsorConfidenceLevel() == SponsorConfidenceLevel.VERIFIED
                    || company.getSponsorConfidenceLevel() == SponsorConfidenceLevel.HIGH))
                    ? company.getSponsorConfidenceLevel()
                    : SponsorConfidenceLevel.VERIFIED;

            String summary = vacancyConfirmed
                    ? "Verified sponsor in " + countryProfile.country() + " and vacancy text explicitly confirms sponsorship support."
                    : "Employer is a verified sponsor in " + countryProfile.country()
                    + "; individual vacancy sponsorship is not explicitly confirmed and must be verified per opening.";

            String caveat = vacancyConfirmed
                    ? "Vacancy wording mentions sponsorship and the employer holds recognized sponsor status; confirm salary thresholds and role contract eligibility."
                    : countryProfile.vacancyRuleWarning();

            return new ClassificationResult(
                    VisaSponsorshipStatus.VERIFIED_SPONSOR,
                    vacancyAssessment.vacancyStatus(),
                    vacancyConfirmed,
                    false,
                    true,
                    confidence,
                    registrySource,
                    registryIdentifier,
                    officialName,
                    sourceUrl,
                    summary,
                    List.copyOf(signals),
                    caveat
            );
        }

        // 4. Explicit sponsorship mention in vacancy or AI extraction
        if (vacancyAssessment.explicitSponsorshipMention()) {
            String summary = "Vacancy explicitly mentions visa sponsorship or EU work permit pathways in "
                    + countryProfile.country() + ", though no active official sponsor register match is linked.";
            return new ClassificationResult(
                    VisaSponsorshipStatus.SPONSORSHIP_MENTIONED,
                    VisaSponsorshipStatus.SPONSORSHIP_MENTIONED,
                    true,
                    false,
                    false,
                    SponsorConfidenceLevel.MEDIUM,
                    registrySource,
                    registryIdentifier,
                    officialName,
                    sourceUrl,
                    summary,
                    List.copyOf(signals),
                    "Confirm during screening which permit route (" + String.join(", ", countryProfile.visaPathways()) + ") the employer will sponsor."
            );
        }

        // 5. Relocation assistance mentioned only
        if (vacancyAssessment.relocationMention()) {
            String summary = "Relocation assistance is mentioned, which does not automatically confirm visa sponsorship in "
                    + countryProfile.country() + ".";
            return new ClassificationResult(
                    VisaSponsorshipStatus.RELOCATION_ASSISTANCE_MENTIONED,
                    VisaSponsorshipStatus.RELOCATION_ASSISTANCE_MENTIONED,
                    false,
                    false,
                    false,
                    SponsorConfidenceLevel.MEDIUM,
                    registrySource,
                    registryIdentifier,
                    officialName,
                    sourceUrl,
                    summary,
                    List.copyOf(signals),
                    "Relocation support often applies only to candidates who already hold work authorization; verify visa sponsorship explicitly."
            );
        }

        // 6. Possible but unconfirmed (suspended historical record, international role clues, or known non-verified company with medium/low plausibility)
        boolean suspendedRecord = revokedOrSuspended != null && revokedOrSuspended.getStatus() == SponsorshipRecordStatus.SUSPENDED;
        boolean knownEmployerMediumLow = company != null
                && (company.getSponsorConfidenceLevel() == SponsorConfidenceLevel.MEDIUM
                || company.getSponsorConfidenceLevel() == SponsorConfidenceLevel.LOW);
        if (suspendedRecord || vacancyAssessment.plausibilityClue() || knownEmployerMediumLow) {
            String reason = suspendedRecord
                    ? "Employer has a suspended sponsor record in " + countryProfile.country() + "; current sponsorship eligibility is possible but unconfirmed."
                    : "International role clues or employer history suggest visa sponsorship in "
                    + countryProfile.country() + " may be possible via statutory pathways (" + String.join(", ", countryProfile.visaPathways()) + "), but remains unconfirmed.";
            return new ClassificationResult(
                    VisaSponsorshipStatus.POSSIBLE_UNCONFIRMED,
                    vacancyAssessment.vacancyStatus(),
                    false,
                    false,
                    false,
                    SponsorConfidenceLevel.LOW,
                    registrySource,
                    registryIdentifier,
                    officialName,
                    sourceUrl,
                    reason,
                    List.copyOf(signals),
                    countryProfile.vacancyRuleWarning()
            );
        }

        // 7. Not mentioned
        String notMentionedSummary = "No official sponsor register match and no visa sponsorship or relocation cues were detected for "
                + requestedCompanyName + " in " + countryProfile.country() + ".";
        return new ClassificationResult(
                VisaSponsorshipStatus.NOT_MENTIONED,
                VisaSponsorshipStatus.NOT_MENTIONED,
                false,
                false,
                false,
                SponsorConfidenceLevel.UNVERIFIED,
                registrySource,
                registryIdentifier,
                officialName,
                sourceUrl,
                notMentionedSummary,
                List.copyOf(signals),
                countryProfile.vacancyRuleWarning()
        );
    }

    private boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}

