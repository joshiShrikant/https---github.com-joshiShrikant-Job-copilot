import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { extractApiErrorMessage } from '../../core/models/api.models';
import { PreferencesService } from '../../core/services/preferences.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { AccountSecuritySectionComponent } from './components/account-security-section/account-security-section.component';
import { SearchPreferencesSectionComponent } from './components/search-preferences-section/search-preferences-section.component';
import { WorkspaceAppearanceSectionComponent } from './components/workspace-appearance-section/workspace-appearance-section.component';

export type SettingsTabId = 'search' | 'appearance' | 'security';

const VALID_SETTINGS_TABS: readonly SettingsTabId[] = ['search', 'appearance', 'security'];

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    PageHeaderComponent,
    SearchPreferencesSectionComponent,
    WorkspaceAppearanceSectionComponent,
    AccountSecuritySectionComponent
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SettingsComponent implements OnInit {
  private readonly preferencesService = inject(PreferencesService);
  private readonly destroyRef = inject(DestroyRef);

  // Optional query param binding (?tab=appearance)
  readonly tab = input<string>('');

  readonly activeTab = signal<SettingsTabId>('search');
  readonly loadErrorMessage = signal<string | null>(null);

  readonly preferences = this.preferencesService.preferences;
  readonly isLoadingPreferences = this.preferencesService.isLoading;
  readonly hasSavedPreferences = this.preferencesService.hasSavedPreferences;

  readonly autopilotSummary = computed(() => {
    const prefs = this.preferences();
    if (!prefs) {
      return 'Loading preferences...';
    }
    const mode = prefs.autopilotEnabled ? 'Autopilot Active' : 'Manual Discovery';
    return `${mode} · ≥ ${prefs.minMatchScore}% match`;
  });

  constructor() {
    effect(() => {
      const requested = this.tab()?.trim().toLowerCase() as SettingsTabId | undefined;
      if (requested && VALID_SETTINGS_TABS.includes(requested)) {
        this.activeTab.set(requested);
      }
    });
  }

  ngOnInit(): void {
    this.loadPreferences();
  }

  selectTab(tabId: SettingsTabId): void {
    this.activeTab.set(tabId);
  }

  reload(): void {
    this.loadPreferences();
  }

  private loadPreferences(): void {
    this.loadErrorMessage.set(null);

    this.preferencesService
      .loadPreferences()
      .pipe(
        catchError((err: unknown) => {
          this.loadErrorMessage.set(
            extractApiErrorMessage(err, 'Unable to load your job search preferences right now.')
          );
          return of(null);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();
  }
}

