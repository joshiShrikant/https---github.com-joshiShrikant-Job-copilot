package com.eurojobcopilot.modules.resume.template;

import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;

/**
 * Shared visual typography, color palette, and section hierarchy specification for all
 * tailored resume templates across PDF and DOCX exporters.
 */
public record TailoredResumeTemplateSpec(
        ResumeTemplate template,
        String displayName,
        boolean serifFont,
        String docxFontFamily,
        boolean centerHeader,
        boolean showTopBanner,
        boolean showHeaderShading,
        boolean showSectionAccentBar,
        boolean doubleHeaderRule,
        boolean europassLayout,
        String primaryHex,
        String accentHex,
        String darkInkHex,
        String mutedInkHex,
        String ruleHex,
        String tintBgHex,
        String summaryHeading,
        String skillsHeading,
        String experienceHeading,
        String projectsHeading,
        String educationHeading,
        String languagesCertsHeading
) {

    public static TailoredResumeTemplateSpec forTemplate(ResumeTemplate template) {
        ResumeTemplate effective = template != null ? template : ResumeTemplate.MODERN;
        return switch (effective) {
            case MINIMAL_ATS -> new TailoredResumeTemplateSpec(
                    ResumeTemplate.MINIMAL_ATS,
                    "Minimal ATS",
                    false,
                    "Arial",
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "0F172A",
                    "0F172A",
                    "111827",
                    "4B5563",
                    "CBD5E1",
                    "F8FAFC",
                    "PROFESSIONAL SUMMARY",
                    "CORE COMPETENCIES & SKILLS",
                    "PROFESSIONAL EXPERIENCE",
                    "SELECTED PROJECTS",
                    "EDUCATION",
                    "LANGUAGES & CERTIFICATIONS"
            );
            case EUROPASS -> new TailoredResumeTemplateSpec(
                    ResumeTemplate.EUROPASS,
                    "Europass",
                    false,
                    "Calibri",
                    false,
                    true,
                    true,
                    false,
                    false,
                    true,
                    "1E40AF",
                    "1D4ED8",
                    "0F172A",
                    "475569",
                    "93C5FD",
                    "EFF6FF",
                    "PERSONAL PROFILE & SUMMARY",
                    "PERSONAL & TECHNICAL SKILLS",
                    "WORK EXPERIENCE",
                    "KEY PROJECTS & SYSTEMS",
                    "EDUCATION AND TRAINING",
                    "LANGUAGES & CERTIFICATIONS"
            );
            case CLASSIC -> new TailoredResumeTemplateSpec(
                    ResumeTemplate.CLASSIC,
                    "Classic Executive",
                    true,
                    "Georgia",
                    true,
                    false,
                    false,
                    false,
                    true,
                    false,
                    "1E293B",
                    "334155",
                    "111827",
                    "475569",
                    "94A3B8",
                    "F1F5F9",
                    "EXECUTIVE SUMMARY",
                    "AREAS OF EXPERTISE",
                    "PROFESSIONAL EXPERIENCE",
                    "SELECTED INITIATIVES & PROJECTS",
                    "EDUCATION",
                    "LANGUAGES & CREDENTIALS"
            );
            case MODERN -> new TailoredResumeTemplateSpec(
                    ResumeTemplate.MODERN,
                    "Modern Clean",
                    false,
                    "Calibri",
                    false,
                    true,
                    true,
                    true,
                    false,
                    false,
                    "1D4ED8",
                    "2563EB",
                    "0F172A",
                    "475569",
                    "CBD5E1",
                    "F1F5F9",
                    "PROFESSIONAL SUMMARY",
                    "TECHNICAL SKILLS",
                    "PROFESSIONAL EXPERIENCE",
                    "SELECTED PROJECTS",
                    "EDUCATION",
                    "LANGUAGES & CERTIFICATIONS"
            );
        };
    }

    public float[] rgbComponents(String hex) {
        if (hex == null || hex.length() != 6) {
            return new float[]{0.06f, 0.09f, 0.16f};
        }
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new float[]{r / 255f, g / 255f, b / 255f};
    }
}

