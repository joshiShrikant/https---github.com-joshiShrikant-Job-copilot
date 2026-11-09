import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import {
  RESUME_TEMPLATE_OPTIONS,
  ResumeTemplate,
  TailoredResumeEducationDto,
  TailoredResumeExperienceDto,
  TailoredResumeHeaderDto,
  TailoredResumeProjectDto,
  formatResumeTemplateLabel
} from '../../../../core/models/tailored-resume.models';

export interface PreviewSectionHeadings {
  readonly summary: string;
  readonly skills: string;
  readonly experience: string;
  readonly projects: string;
  readonly education: string;
  readonly languagesCerts: string;
}

@Component({
  selector: 'app-tailored-cv-preview',
  standalone: true,
  templateUrl: './tailored-cv-preview.component.html',
  styleUrl: './tailored-cv-preview.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TailoredCvPreviewComponent {
  readonly template = input<ResumeTemplate>('MODERN');
  readonly header = input<TailoredResumeHeaderDto | null>(null);
  readonly summary = input<string>('');
  readonly highlightedSkills = input<readonly string[]>([]);
  readonly additionalSkills = input<readonly string[]>([]);
  readonly experiences = input<readonly TailoredResumeExperienceDto[]>([]);
  readonly projects = input<readonly TailoredResumeProjectDto[]>([]);
  readonly educations = input<readonly TailoredResumeEducationDto[]>([]);
  readonly languages = input<readonly string[]>([]);
  readonly certifications = input<readonly string[]>([]);

  readonly templateSelected = output<ResumeTemplate>();

  readonly templateOptions = RESUME_TEMPLATE_OPTIONS;

  readonly templateLabel = computed<string>(() => formatResumeTemplateLabel(this.template()));

  readonly headings = computed<PreviewSectionHeadings>(() => {
    switch (this.template()) {
      case 'MINIMAL_ATS':
        return {
          summary: 'PROFESSIONAL SUMMARY',
          skills: 'CORE COMPETENCIES & SKILLS',
          experience: 'PROFESSIONAL EXPERIENCE',
          projects: 'SELECTED PROJECTS',
          education: 'EDUCATION',
          languagesCerts: 'LANGUAGES & CERTIFICATIONS'
        };
      case 'EUROPASS':
        return {
          summary: 'PERSONAL PROFILE & SUMMARY',
          skills: 'PERSONAL & TECHNICAL SKILLS',
          experience: 'WORK EXPERIENCE',
          projects: 'KEY PROJECTS & SYSTEMS',
          education: 'EDUCATION AND TRAINING',
          languagesCerts: 'LANGUAGES & CERTIFICATIONS'
        };
      case 'CLASSIC':
        return {
          summary: 'EXECUTIVE SUMMARY',
          skills: 'AREAS OF EXPERTISE',
          experience: 'PROFESSIONAL EXPERIENCE',
          projects: 'SELECTED INITIATIVES & PROJECTS',
          education: 'EDUCATION',
          languagesCerts: 'LANGUAGES & CREDENTIALS'
        };
      case 'MODERN':
      default:
        return {
          summary: 'PROFESSIONAL SUMMARY',
          skills: 'TECHNICAL SKILLS',
          experience: 'PROFESSIONAL EXPERIENCE',
          projects: 'SELECTED PROJECTS',
          education: 'EDUCATION',
          languagesCerts: 'LANGUAGES & CERTIFICATIONS'
        };
    }
  });

  readonly previewClass = computed<string>(() => {
    switch (this.template()) {
      case 'MINIMAL_ATS':
        return 'cv-sheet template-minimal-ats';
      case 'EUROPASS':
        return 'cv-sheet template-europass';
      case 'CLASSIC':
        return 'cv-sheet template-classic';
      case 'MODERN':
      default:
        return 'cv-sheet template-modern';
    }
  });

  selectTemplate(tpl: ResumeTemplate): void {
    if (tpl !== this.template()) {
      this.templateSelected.emit(tpl);
    }
  }

  formatDateRange(startDate: string | null, endDate: string | null, current: boolean): string {
    const start = this.formatMonthYear(startDate);
    if (current) {
      return start ? `${start} – Present` : 'Present';
    }
    const end = this.formatMonthYear(endDate);
    if (start && end) {
      return `${start} – ${end}`;
    }
    return start || end || '';
  }

  isSafeHttpUrl(raw: string | null | undefined): boolean {
    if (!raw || !raw.trim()) {
      return false;
    }
    try {
      const parsed = new URL(raw.trim());
      return (
        (parsed.protocol === 'https:' || parsed.protocol === 'http:') &&
        parsed.hostname.trim().length > 0
      );
    } catch {
      return false;
    }
  }

  private formatMonthYear(isoDate: string | null): string {
    if (!isoDate) {
      return '';
    }
    const parsed = new Date(isoDate);
    if (Number.isNaN(parsed.getTime())) {
      return isoDate;
    }
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      year: 'numeric'
    }).format(parsed);
  }
}

