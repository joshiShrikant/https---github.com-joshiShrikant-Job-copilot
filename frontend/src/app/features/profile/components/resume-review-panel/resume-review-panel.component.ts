import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal
} from '@angular/core';
import {
  PROFICIENCY_LEVEL_OPTIONS,
  ProficiencyLevel,
  RELOCATION_PREFERENCE_OPTIONS,
  RelocationPreference,
  WORK_AUTHORIZATION_OPTIONS,
  WorkAuthorizationStatus
} from '../../../../core/models/profile.models';
import {
  ApproveMasterResumeRequest,
  ParsedResumeEducationDto,
  ParsedResumeExperienceDto,
  ParsedResumeJsonDto,
  ParsedResumeProfileDto,
  ParsedResumeProjectDto,
  ParsedResumeSkillDto,
  ProfileSyncMode,
  ResumeParseResponse,
  ResumeResponse
} from '../../../../core/models/resume.models';

function createEmptyProfileDraft(): ParsedResumeProfileDto {
  return {
    fullName: null,
    currentDesignation: null,
    yearsOfExperience: null,
    currentCountry: null,
    currentCity: null,
    linkedinUrl: null,
    githubUrl: null,
    portfolioUrl: null,
    bio: null,
    workAuthorizationStatus: null,
    relocationPreference: null
  };
}

@Component({
  selector: 'app-resume-review-panel',
  standalone: true,
  templateUrl: './resume-review-panel.component.html',
  styleUrl: './resume-review-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResumeReviewPanelComponent {
  readonly resume = input.required<ResumeResponse>();
  readonly parseResult = input<ResumeParseResponse | null>(null);
  readonly isLoadingDraft = input<boolean>(false);
  readonly isParsing = input<boolean>(false);
  readonly isApproving = input<boolean>(false);

  readonly parseRequested = output<boolean>();
  readonly approveRequested = output<ApproveMasterResumeRequest>();
  readonly closed = output<void>();

  readonly workAuthOptions = WORK_AUTHORIZATION_OPTIONS;
  readonly relocationOptions = RELOCATION_PREFERENCE_OPTIONS;
  readonly proficiencyOptions = PROFICIENCY_LEVEL_OPTIONS;

  readonly hasEditableDraft = signal<boolean>(false);
  readonly profileDraft = signal<ParsedResumeProfileDto>(createEmptyProfileDraft());
  readonly summaryDraft = signal<string>('');
  readonly skillsDraft = signal<ParsedResumeSkillDto[]>([]);
  readonly experiencesDraft = signal<ParsedResumeExperienceDto[]>([]);
  readonly educationsDraft = signal<ParsedResumeEducationDto[]>([]);
  readonly projectsDraft = signal<ParsedResumeProjectDto[]>([]);
  readonly languagesText = signal<string>('');
  readonly certificationsText = signal<string>('');
  readonly syncMode = signal<ProfileSyncMode>('MERGE');

  readonly newSkillName = signal<string>('');
  readonly newSkillPrimary = signal<boolean>(true);
  readonly validationError = signal<string | null>(null);

  readonly warnings = computed(() => this.parseResult()?.warnings ?? []);
  readonly effectiveStatus = computed(
    () => this.parseResult()?.parseStatus ?? this.resume().parseStatus ?? 'NOT_PARSED'
  );

  constructor() {
    effect(() => {
      const result = this.parseResult();
      this.validationError.set(null);

      if (result?.parsedData) {
        this.hydrateFromParsedData(result.parsedData);
        this.hasEditableDraft.set(true);
      } else {
        this.resetDraft();
        this.hasEditableDraft.set(false);
      }
    });
  }

  startManualEntry(): void {
    this.resetDraft();
    this.hasEditableDraft.set(true);
    this.validationError.set(null);
  }

  requestAiParse(forceRefresh: boolean): void {
    this.validationError.set(null);
    this.parseRequested.emit(forceRefresh);
  }

  closePanel(): void {
    this.closed.emit();
  }

  updateProfileText(
    field:
      | 'fullName'
      | 'currentDesignation'
      | 'currentCountry'
      | 'currentCity'
      | 'linkedinUrl'
      | 'githubUrl'
      | 'portfolioUrl'
      | 'bio',
    event: Event
  ): void {
    const raw = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.profileDraft.update(prev => ({
      ...prev,
      [field]: raw.trim().length > 0 ? raw : null
    }));
  }

  updateYearsOfExperience(event: Event): void {
    const raw = (event.target as HTMLInputElement).value.trim();
    const num = raw === '' ? null : Number(raw);
    this.profileDraft.update(prev => ({
      ...prev,
      yearsOfExperience: num !== null && Number.isFinite(num) ? num : null
    }));
  }

  updateWorkAuth(event: Event): void {
    const val = (event.target as HTMLSelectElement).value as WorkAuthorizationStatus | '';
    this.profileDraft.update(prev => ({
      ...prev,
      workAuthorizationStatus: val ? val : null
    }));
  }

  updateRelocation(event: Event): void {
    const val = (event.target as HTMLSelectElement).value as RelocationPreference | '';
    this.profileDraft.update(prev => ({
      ...prev,
      relocationPreference: val ? val : null
    }));
  }

  updateSummary(event: Event): void {
    this.summaryDraft.set((event.target as HTMLTextAreaElement).value);
  }

  addSkillFromInput(): void {
    const clean = this.newSkillName().trim();
    if (!clean) {
      return;
    }
    const duplicate = this.skillsDraft().some(
      s => s.skillName.trim().toLowerCase() === clean.toLowerCase()
    );
    if (duplicate) {
      this.validationError.set(`Skill "${clean}" is already in the list.`);
      return;
    }
    this.validationError.set(null);
    this.skillsDraft.update(items => [
      ...items,
      {
        skillName: clean,
        yearsExperience: 1,
        proficiencyLevel: 'INTERMEDIATE',
        primary: this.newSkillPrimary()
      }
    ]);
    this.newSkillName.set('');
  }

  updateSkillName(index: number, event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    this.skillsDraft.update(items =>
      items.map((item, i) => (i === index ? { ...item, skillName: val } : item))
    );
  }

  updateSkillYears(index: number, event: Event): void {
    const raw = (event.target as HTMLInputElement).value.trim();
    const num = raw === '' ? null : Number(raw);
    this.skillsDraft.update(items =>
      items.map((item, i) =>
        i === index ? { ...item, yearsExperience: num !== null && Number.isFinite(num) ? num : null } : item
      )
    );
  }

  updateSkillProficiency(index: number, event: Event): void {
    const val = (event.target as HTMLSelectElement).value as ProficiencyLevel | '';
    this.skillsDraft.update(items =>
      items.map((item, i) => (i === index ? { ...item, proficiencyLevel: val ? val : null } : item))
    );
  }

  toggleSkillPrimary(index: number, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.skillsDraft.update(items =>
      items.map((item, i) => (i === index ? { ...item, primary: checked } : item))
    );
  }

  removeSkill(index: number): void {
    this.skillsDraft.update(items => items.filter((_, i) => i !== index));
  }

  addExperience(): void {
    this.experiencesDraft.update(items => [
      ...items,
      {
        companyName: '',
        designation: '',
        location: null,
        startDate: null,
        endDate: null,
        current: false,
        description: null,
        skillsUsed: []
      }
    ]);
  }

  updateExperienceText(
    index: number,
    field: 'companyName' | 'designation' | 'location' | 'startDate' | 'endDate' | 'description',
    event: Event
  ): void {
    const val = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.experiencesDraft.update(items =>
      items.map((item, i) => {
        if (i !== index) {
          return item;
        }
        if (field === 'companyName' || field === 'designation') {
          return { ...item, [field]: val };
        }
        return { ...item, [field]: val.trim() ? val : null };
      })
    );
  }

  updateExperienceCurrent(index: number, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.experiencesDraft.update(items =>
      items.map((item, i) =>
        i === index ? { ...item, current: checked, endDate: checked ? null : item.endDate } : item
      )
    );
  }

  updateExperienceSkillsUsed(index: number, event: Event): void {
    const parsed = this.splitCommaList((event.target as HTMLInputElement).value);
    this.experiencesDraft.update(items =>
      items.map((item, i) => (i === index ? { ...item, skillsUsed: parsed } : item))
    );
  }

  removeExperience(index: number): void {
    this.experiencesDraft.update(items => items.filter((_, i) => i !== index));
  }

  addEducation(): void {
    this.educationsDraft.update(items => [
      ...items,
      {
        institution: '',
        degree: '',
        fieldOfStudy: '',
        startDate: null,
        endDate: null,
        gradeOrGpa: null,
        country: null
      }
    ]);
  }

  updateEducationText(
    index: number,
    field: 'institution' | 'degree' | 'fieldOfStudy' | 'startDate' | 'endDate' | 'gradeOrGpa' | 'country',
    event: Event
  ): void {
    const val = (event.target as HTMLInputElement).value;
    this.educationsDraft.update(items =>
      items.map((item, i) => {
        if (i !== index) {
          return item;
        }
        if (field === 'institution' || field === 'degree' || field === 'fieldOfStudy') {
          return { ...item, [field]: val };
        }
        return { ...item, [field]: val.trim() ? val : null };
      })
    );
  }

  removeEducation(index: number): void {
    this.educationsDraft.update(items => items.filter((_, i) => i !== index));
  }

  addProject(): void {
    this.projectsDraft.update(items => [
      ...items,
      {
        name: '',
        description: null,
        technologies: [],
        url: null
      }
    ]);
  }

  updateProjectText(
    index: number,
    field: 'name' | 'description' | 'url',
    event: Event
  ): void {
    const val = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.projectsDraft.update(items =>
      items.map((item, i) => {
        if (i !== index) {
          return item;
        }
        if (field === 'name') {
          return { ...item, name: val };
        }
        return { ...item, [field]: val.trim() ? val : null };
      })
    );
  }

  updateProjectTechnologies(index: number, event: Event): void {
    const parsed = this.splitCommaList((event.target as HTMLInputElement).value);
    this.projectsDraft.update(items =>
      items.map((item, i) => (i === index ? { ...item, technologies: parsed } : item))
    );
  }

  removeProject(index: number): void {
    this.projectsDraft.update(items => items.filter((_, i) => i !== index));
  }

  submitApproval(): void {
    if (this.isApproving()) {
      return;
    }

    const validationIssue = this.validateDraftBeforeApproval();
    if (validationIssue) {
      this.validationError.set(validationIssue);
      return;
    }

    this.validationError.set(null);
    const payload = this.buildNormalizedPayload();
    this.approveRequested.emit({
      resumeUuid: this.resume().uuid,
      approvedParsedData: payload,
      syncMode: this.syncMode()
    });
  }

  private hydrateFromParsedData(data: ParsedResumeJsonDto): void {
    const prof = data.profile ?? createEmptyProfileDraft();
    this.profileDraft.set({ ...createEmptyProfileDraft(), ...prof });
    this.summaryDraft.set(data.summary ?? prof.bio ?? '');
    this.skillsDraft.set((data.skills ?? []).map(s => ({ ...s })));
    this.experiencesDraft.set(
      (data.experiences ?? []).map(e => ({
        ...e,
        skillsUsed: [...(e.skillsUsed ?? [])]
      }))
    );
    this.educationsDraft.set((data.educations ?? []).map(ed => ({ ...ed })));
    this.projectsDraft.set(
      (data.projects ?? []).map(p => ({
        ...p,
        technologies: [...(p.technologies ?? [])]
      }))
    );
    this.languagesText.set((data.languages ?? []).join(', '));
    this.certificationsText.set((data.certifications ?? []).join(', '));
  }

  private resetDraft(): void {
    this.profileDraft.set(createEmptyProfileDraft());
    this.summaryDraft.set('');
    this.skillsDraft.set([]);
    this.experiencesDraft.set([]);
    this.educationsDraft.set([]);
    this.projectsDraft.set([]);
    this.languagesText.set('');
    this.certificationsText.set('');
    this.newSkillName.set('');
  }

  private validateDraftBeforeApproval(): string | null {
    const p = this.profileDraft();
    if (p.yearsOfExperience !== null && (p.yearsOfExperience < 0 || p.yearsOfExperience > 70)) {
      return 'Total years of experience must be between 0 and 70.';
    }
    if (!this.isValidOptionalHttpUrl(p.linkedinUrl)) {
      return 'LinkedIn URL must start with http:// or https://.';
    }
    if (!this.isValidOptionalHttpUrl(p.githubUrl)) {
      return 'GitHub URL must start with http:// or https://.';
    }
    if (!this.isValidOptionalHttpUrl(p.portfolioUrl)) {
      return 'Portfolio URL must start with http:// or https://.';
    }

    for (let i = 0; i < this.skillsDraft().length; i++) {
      const skill = this.skillsDraft()[i];
      if (!skill.skillName.trim()) {
        return `Skill #${i + 1} must have a non-empty name.`;
      }
      if (skill.yearsExperience !== null && (skill.yearsExperience < 0 || skill.yearsExperience > 70)) {
        return `Years of experience for skill "${skill.skillName}" must be between 0 and 70.`;
      }
    }

    for (let i = 0; i < this.experiencesDraft().length; i++) {
      const exp = this.experiencesDraft()[i];
      const label = exp.designation.trim() || exp.companyName.trim() || `Experience #${i + 1}`;
      if (!exp.companyName.trim() || !exp.designation.trim()) {
        return `Company name and designation are required for "${label}".`;
      }
      if (!exp.startDate) {
        return `Start date is required for experience "${label}" before approving.`;
      }
      if (!exp.current && exp.endDate && exp.endDate < exp.startDate) {
        return `End date cannot precede start date for experience "${label}".`;
      }
    }

    for (let i = 0; i < this.educationsDraft().length; i++) {
      const edu = this.educationsDraft()[i];
      const label = edu.degree.trim() || edu.institution.trim() || `Education #${i + 1}`;
      if (!edu.institution.trim() || !edu.degree.trim() || !edu.fieldOfStudy.trim()) {
        return `Institution, degree, and field of study are required for "${label}".`;
      }
      if (!edu.country?.trim()) {
        return `Country is required for education "${label}" before approving.`;
      }
      if (!edu.startDate) {
        return `Start date is required for education "${label}" before approving.`;
      }
      if (edu.endDate && edu.endDate < edu.startDate) {
        return `End date cannot precede start date for education "${label}".`;
      }
    }

    for (let i = 0; i < this.projectsDraft().length; i++) {
      const proj = this.projectsDraft()[i];
      if (!proj.name.trim()) {
        return `Project #${i + 1} must have a non-empty name.`;
      }
      if (!this.isValidOptionalHttpUrl(proj.url)) {
        return `URL for project "${proj.name}" must start with http:// or https://.`;
      }
    }

    return null;
  }

  private isValidOptionalHttpUrl(raw: string | null | undefined): boolean {
    const trimmed = this.trimToNull(raw);
    if (!trimmed) {
      return true;
    }
    try {
      const parsed = new URL(trimmed);
      return parsed.protocol === 'http:' || parsed.protocol === 'https:';
    } catch {
      return false;
    }
  }

  private buildNormalizedPayload(): ParsedResumeJsonDto {
    const prof = this.profileDraft();
    const cleanSummary = this.trimToNull(this.summaryDraft());
    return {
      profile: {
        fullName: this.trimToNull(prof.fullName),
        currentDesignation: this.trimToNull(prof.currentDesignation),
        yearsOfExperience: prof.yearsOfExperience,
        currentCountry: this.trimToNull(prof.currentCountry),
        currentCity: this.trimToNull(prof.currentCity),
        linkedinUrl: this.trimToNull(prof.linkedinUrl),
        githubUrl: this.trimToNull(prof.githubUrl),
        portfolioUrl: this.trimToNull(prof.portfolioUrl),
        bio: this.trimToNull(prof.bio) ?? cleanSummary,
        workAuthorizationStatus: prof.workAuthorizationStatus,
        relocationPreference: prof.relocationPreference
      },
      summary: cleanSummary,
      skills: this.skillsDraft().map(s => ({
        skillName: s.skillName.trim(),
        yearsExperience: s.yearsExperience,
        proficiencyLevel: s.proficiencyLevel,
        primary: s.primary
      })),
      experiences: this.experiencesDraft().map(e => ({
        companyName: e.companyName.trim(),
        designation: e.designation.trim(),
        location: this.trimToNull(e.location),
        startDate: e.startDate,
        endDate: e.current ? null : e.endDate,
        current: e.current,
        description: this.trimToNull(e.description),
        skillsUsed: e.skillsUsed
      })),
      educations: this.educationsDraft().map(ed => ({
        institution: ed.institution.trim(),
        degree: ed.degree.trim(),
        fieldOfStudy: ed.fieldOfStudy.trim(),
        startDate: ed.startDate,
        endDate: ed.endDate,
        gradeOrGpa: this.trimToNull(ed.gradeOrGpa),
        country: this.trimToNull(ed.country)
      })),
      projects: this.projectsDraft().map(p => ({
        name: p.name.trim(),
        description: this.trimToNull(p.description),
        technologies: p.technologies,
        url: this.trimToNull(p.url)
      })),
      languages: this.splitCommaList(this.languagesText()),
      certifications: this.splitCommaList(this.certificationsText())
    };
  }

  private splitCommaList(raw: string): string[] {
    if (!raw) {
      return [];
    }
    const seen = new Set<string>();
    const out: string[] = [];
    for (const part of raw.split(',')) {
      const cleaned = part.trim();
      if (cleaned && !seen.has(cleaned.toLowerCase())) {
        seen.add(cleaned.toLowerCase());
        out.push(cleaned);
      }
    }
    return out;
  }

  private trimToNull(val: string | null | undefined): string | null {
    if (val == null) {
      return null;
    }
    const trimmed = val.trim();
    return trimmed.length > 0 ? trimmed : null;
  }
}
