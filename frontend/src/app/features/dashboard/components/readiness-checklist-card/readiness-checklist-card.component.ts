import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardReadiness } from '../../../../core/models/dashboard.models';

interface ReadinessStep {
  readonly id: string;
  readonly label: string;
  readonly detail: string;
  readonly completed: boolean;
  readonly ctaLabel: string;
  readonly route: string;
  readonly queryParams?: Record<string, string>;
}

@Component({
  selector: 'app-readiness-checklist-card',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './readiness-checklist-card.component.html',
  styleUrl: './readiness-checklist-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReadinessChecklistCardComponent {
  readonly readiness = input.required<DashboardReadiness>();

  readonly steps = computed<readonly ReadinessStep[]>(() => {
    const r = this.readiness();

    const resumeCompleted = r.resumesCount > 0 && r.masterResumeApproved;
    const resumeDetail = r.masterResumeApproved
      ? `Approved Master CV (${r.resumesCount} uploaded)`
      : r.resumesCount > 0
        ? `${r.resumesCount} resume uploaded — approve one as Master CV`
        : 'Upload your PDF or DOCX CV to unlock tailored tailoring';

    const targetCountriesText =
      r.preferredCountries.length > 0
        ? `Targets: ${r.preferredCountries.slice(0, 3).join(', ')}${r.preferredCountries.length > 3 ? ' +' + (r.preferredCountries.length - 3) : ''}`
        : 'Set European target countries, roles, and minimum salary';

    return [
      {
        id: 'profile',
        label: 'Complete Candidate Profile',
        detail: r.profileExists
          ? `${r.profileCompletenessPercent}% complete · ${r.skillsCount} skills · ${r.experiencesCount} roles`
          : 'Add your role, experience, skills, and relocation preferences',
        completed: r.profileExists && r.profileCompletenessPercent >= 75,
        ctaLabel: r.profileExists ? 'Edit Profile' : 'Create Profile',
        route: '/profile'
      },
      {
        id: 'resume',
        label: 'Approve Master Resume',
        detail: resumeDetail,
        completed: resumeCompleted,
        ctaLabel: r.resumesCount > 0 ? 'Review CVs' : 'Upload CV',
        route: '/resumes'
      },
      {
        id: 'preferences',
        label: 'Configure Search & Visa Targets',
        detail: targetCountriesText,
        completed: r.preferencesConfigured,
        ctaLabel: r.preferencesConfigured ? 'Adjust Targets' : 'Configure',
        route: '/settings'
      },
      {
        id: 'autopilot',
        label: 'Activate Copilot Autopilot',
        detail: r.autopilotEnabled
          ? `Active · alerting at ≥ ${r.minMatchScore}% match score`
          : `Manual mode · set automatic high-match threshold (≥ ${r.minMatchScore}%)`,
        completed: r.autopilotEnabled,
        ctaLabel: r.autopilotEnabled ? 'Settings' : 'Enable',
        route: '/settings',
        queryParams: { tab: 'search' }
      }
    ];
  });

  readonly completedStepsCount = computed(
    () => this.steps().filter(step => step.completed).length
  );
}

