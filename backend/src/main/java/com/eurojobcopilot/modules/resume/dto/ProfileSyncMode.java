package com.eurojobcopilot.modules.resume.dto;

/**
 * Controls how confirmed master resume qualifications sync into existing candidate profile collections.
 */
public enum ProfileSyncMode {
    /**
     * Preserves existing profile entries and merges or updates matching skills, experiences, and educations.
     */
    MERGE,

    /**
     * Replaces existing skills, experiences, and educations with the confirmed master resume items.
     */
    REPLACE
}

