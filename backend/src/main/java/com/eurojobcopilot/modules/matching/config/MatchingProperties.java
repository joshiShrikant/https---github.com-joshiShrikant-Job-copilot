package com.eurojobcopilot.modules.matching.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration properties for deterministic rule-based job matching weights and batch bounds.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.matching")
public class MatchingProperties {

    @Valid
    private Weights weights = new Weights();

    @Min(1)
    @Max(2000)
    private int maxBatchJobs = 500;

    @Getter
    @Setter
    public static class Weights {

        @DecimalMin("0.0")
        private double technicalSkills = 40.0;

        @DecimalMin("0.0")
        private double experience = 20.0;

        @DecimalMin("0.0")
        private double jobTitle = 15.0;

        @DecimalMin("0.0")
        private double location = 10.0;

        @DecimalMin("0.0")
        private double sponsorship = 10.0;

        @DecimalMin("0.0")
        private double salary = 5.0;

        @AssertTrue(message = "Sum of matching weights must be strictly greater than zero")
        public boolean isTotalWeightPositive() {
            return totalWeight() > 0.0;
        }

        public double totalWeight() {
            return technicalSkills + experience + jobTitle + location + sponsorship + salary;
        }
    }
}

