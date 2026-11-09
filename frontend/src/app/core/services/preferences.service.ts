import { HttpClient } from '@angular/common/http';
import { DOCUMENT, Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, finalize, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AlertCadence,
  JobPreferencesResponse,
  UpdateJobPreferencesRequest,
  WorkspaceDensity,
  WorkspacePreferences
} from '../models/preferences.models';

export const WORKSPACE_PREFS_STORAGE_KEY = 'eurojob-workspace-prefs';

const DEFAULT_WORKSPACE_PREFS: WorkspacePreferences = {
  alertCadence: 'DAILY',
  density: 'COMFORTABLE'
};

@Injectable({
  providedIn: 'root'
})
export class PreferencesService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);
  private readonly preferencesUrl = `${environment.apiUrl}/preferences`;

  private readonly _preferences = signal<JobPreferencesResponse | null>(null);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _workspacePrefs = signal<WorkspacePreferences>(this.readWorkspacePrefs());

  readonly preferences = this._preferences.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly workspacePrefs = this._workspacePrefs.asReadonly();
  readonly hasSavedPreferences = computed(() => this._preferences()?.updatedAt !== null);

  constructor() {
    effect(() => {
      const density = this._workspacePrefs().density;
      const root = this.document.documentElement;
      if (density === 'COMPACT') {
        root.setAttribute('data-density', 'compact');
      } else {
        root.removeAttribute('data-density');
      }
    });
  }

  loadPreferences(): Observable<JobPreferencesResponse> {
    this._isLoading.set(true);
    return this.http.get<JobPreferencesResponse>(this.preferencesUrl).pipe(
      tap(response => this._preferences.set(response)),
      finalize(() => this._isLoading.set(false))
    );
  }

  savePreferences(request: UpdateJobPreferencesRequest): Observable<JobPreferencesResponse> {
    return this.http
      .put<JobPreferencesResponse>(this.preferencesUrl, request)
      .pipe(tap(response => this._preferences.set(response)));
  }

  setAlertCadence(alertCadence: AlertCadence): void {
    this.updateWorkspacePrefs({ ...this._workspacePrefs(), alertCadence });
  }

  setDensity(density: WorkspaceDensity): void {
    this.updateWorkspacePrefs({ ...this._workspacePrefs(), density });
  }

  private updateWorkspacePrefs(next: WorkspacePreferences): void {
    this._workspacePrefs.set(next);
    try {
      const win = this.document.defaultView;
      win?.localStorage?.setItem(WORKSPACE_PREFS_STORAGE_KEY, JSON.stringify(next));
    } catch {
      // Ignore storage access restrictions
    }
  }

  private readWorkspacePrefs(): WorkspacePreferences {
    try {
      const win = this.document.defaultView;
      const raw = win?.localStorage?.getItem(WORKSPACE_PREFS_STORAGE_KEY);
      if (!raw) {
        return DEFAULT_WORKSPACE_PREFS;
      }
      const parsed = JSON.parse(raw) as Partial<WorkspacePreferences>;
      const alertCadence: AlertCadence =
        parsed.alertCadence === 'INSTANT' ||
        parsed.alertCadence === 'DAILY' ||
        parsed.alertCadence === 'WEEKLY'
          ? parsed.alertCadence
          : 'DAILY';
      const density: WorkspaceDensity =
        parsed.density === 'COMFORTABLE' || parsed.density === 'COMPACT'
          ? parsed.density
          : 'COMFORTABLE';
      return { alertCadence, density };
    } catch {
      return DEFAULT_WORKSPACE_PREFS;
    }
  }
}
