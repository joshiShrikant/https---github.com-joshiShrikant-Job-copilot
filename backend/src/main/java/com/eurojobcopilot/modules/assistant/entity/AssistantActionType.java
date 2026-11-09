package com.eurojobcopilot.modules.assistant.entity;

public enum AssistantActionType {
    ANALYZE_JOB("Analyze Job"),
    EXPLAIN_MATCH_SCORE("Explain Match Score"),
    GENERATE_TAILORED_RESUME("Generate Tailored Resume"),
    GENERATE_COVER_LETTER("Generate Cover Letter"),
    IDENTIFY_SKILL_GAPS("Identify Skill Gaps"),
    PREPARE_RECRUITER_MESSAGE("Prepare Recruiter Message"),
    GENERATE_FOLLOW_UP_EMAIL("Generate Follow-Up Email"),
    SUGGEST_SALARY_EXPECTATIONS("Suggest Salary Expectations"),
    EXPLAIN_SPONSORSHIP("Explain Sponsorship Considerations"),
    INTERVIEW_PREP_TOPICS("Generate Interview Prep Topics"),
    CUSTOM_QUESTION("Ask Assistant");

    private final String label;

    AssistantActionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

