package com.eurojobcopilot.support;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

public final class TestEntityFactory {

    private TestEntityFactory() {
    }

    public static User createUser(String email) {
        return User.builder()
                .email(email)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv1234567890ABCDEFGHIJKL")
                .firstName("Lukas")
                .lastName("Mueller")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build();
    }

    public static Company createCompany(String name, String country, boolean verifiedSponsor) {
        return Company.builder()
                .name(name)
                .headquartersCountry(country)
                .headquartersCity("Amsterdam")
                .industry("FinTech")
                .companySize("1000+")
                .verifiedSponsor(verifiedSponsor)
                .sponsorConfidenceLevel(verifiedSponsor ? SponsorConfidenceLevel.VERIFIED : SponsorConfidenceLevel.UNVERIFIED)
                .build();
    }

    public static Job createJob(Company company, String title, String country, String uniqueSeed) {
        return Job.builder()
                .canonicalHash(sha256Hex(title + "|" + country + "|" + uniqueSeed))
                .company(company)
                .companyName(company != null ? company.getName() : "Booking.com")
                .title(title)
                .location("Amsterdam, " + country)
                .city("Amsterdam")
                .country(country)
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .salaryMin(new BigDecimal("85000.00"))
                .salaryMax(new BigDecimal("110000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .description("Full stack Java and Angular engineering role with visa sponsorship support.")
                .descriptionSnippet("Full stack Java and Angular engineering role.")
                .skills(List.of("Java 21", "Spring Boot", "Angular", "MySQL"))
                .sourceProvider("ARBEITNOW")
                .sourceJobId("job-" + uniqueSeed)
                .originalUrl("https://example.com/jobs/" + uniqueSeed)
                .applicationUrl("https://example.com/apply/" + uniqueSeed)
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.now().minusSeconds(3600))
                .active(true)
                .build();
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}

