import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AlertCadence, WorkspaceDensity } from '../../../../core/models/preferences.models';
import { LayoutStateService } from '../../../../core/services/layout-state.service';
import { PreferencesService } from '../../../../core/services/preferences.service';
import { ThemeMode, ThemeService } from '../../../../core/services/theme.service';

interface ChoiceCard<T extends string> {
  readonly value: T;
  readonly label: string;
  readonly description: string;
  readonly icon: string;
}

const ALERT_CADENCE_OPTIONS: readonly ChoiceCard<AlertCadence>[] = [
  {
    value: 'INSTANT',
    label: 'Instant Alerts',
    description: 'Notify immediately when a high-match sponsored role is discovered.',
    icon: 'bolt'
  },
  {
    value: 'DAILY',
    label: 'Daily Digest',
    description: 'Receive one curated morning summary of top European matches.',
    icon: 'today'
  },
  {
    value: 'WEEKLY',
    label: 'Weekly Roundup',
    description: 'Quiet mode with a single weekly digest of top opportunities.',
    icon: 'calendar_view_week'
  }
];

const DENSITY_OPTIONS: readonly ChoiceCard<WorkspaceDensity>[] = [
  {
    value: 'COMFORTABLE',
    label: 'Comfortable',
    description: 'Generous breathing room for editorial reading and profile forms.',
    icon: 'view_agenda'
  },
  {
    value: 'COMPACT',
    label: 'Compact',
    description: 'Tighter card padding and denser layout spacing across pages.',
    icon: 'view_compact'
  }
];

@Component({
  selector: 'app-workspace-appearance-section',
  standalone: true,
  templateUrl: './workspace-appearance-section.component.html',
  styleUrl: './workspace-appearance-section.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkspaceAppearanceSectionComponent {
  readonly themeService = inject(ThemeService);
  readonly layoutService = inject(LayoutStateService);
  readonly preferencesService = inject(PreferencesService);

  readonly cadenceOptions = ALERT_CADENCE_OPTIONS;
  readonly densityOptions = DENSITY_OPTIONS;
  readonly statusMessage = signal<string | null>(null);

  readonly activeTheme = this.themeService.theme;
  readonly resolvedTheme = this.themeService.resolvedTheme;
  readonly systemTheme = this.themeService.systemTheme;
  readonly workspacePrefs = this.preferencesService.workspacePrefs;
  readonly isSidebarCollapsed = this.layoutService.isSidebarCollapsed;
  readonly activeDensity = computed(() => this.workspacePrefs().density);
  readonly activeCadence = computed(() => this.workspacePrefs().alertCadence);

  readonly themeStatusLabel = computed(() => {
    const mode = this.activeTheme();
    if (mode === 'system') {
      return `System (${this.systemTheme() === 'dark' ? 'Dark' : 'Light'})`;
    }
    return mode === 'dark' ? 'Dark Mode' : 'Light Mode';
  });

  selectTheme(mode: ThemeMode): void {
    this.themeService.setTheme(mode);
    if (mode === 'system') {
      const osMode = this.systemTheme() === 'dark' ? 'dark' : 'light';
      this.statusMessage.set(`Workspace theme synced to system preference (currently ${osMode}).`);
    } else if (mode === 'dark') {
      this.statusMessage.set('Dark workspace theme activated.');
    } else {
      this.statusMessage.set('Light workspace theme activated.');
    }
  }

  onSidebarCollapseToggle(event: Event): void {
    const collapsed = (event.target as HTMLInputElement).checked;
    this.layoutService.setSidebarCollapsed(collapsed);
    this.statusMessage.set(
      collapsed
        ? 'Desktop navigation sidebar set to collapsed by default.'
        : 'Desktop navigation sidebar set to expanded by default.'
    );
  }

  selectDensity(density: WorkspaceDensity): void {
    this.preferencesService.setDensity(density);
    this.statusMessage.set(
      density === 'COMPACT'
        ? 'Compact workspace density enabled.'
        : 'Comfortable workspace density enabled.'
    );
  }

  selectCadence(cadence: AlertCadence): void {
    this.preferencesService.setAlertCadence(cadence);
    this.statusMessage.set(`Alert digest cadence updated to ${cadence.toLowerCase()}.`);
  }
}
