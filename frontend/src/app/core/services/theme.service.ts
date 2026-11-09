import { DOCUMENT, Injectable, computed, effect, inject, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'eurojob-theme';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly _theme = signal<ThemeMode>(this.resolveInitialTheme());

  readonly theme = this._theme.asReadonly();
  readonly isDarkMode = computed(() => this._theme() === 'dark');

  constructor() {
    effect(() => {
      const mode = this._theme();
      const root = this.document.documentElement;
      if (mode === 'dark') {
        root.setAttribute('data-theme', 'dark');
        root.style.colorScheme = 'dark';
      } else {
        root.removeAttribute('data-theme');
        root.style.colorScheme = 'light';
      }
    });
  }

  setTheme(mode: ThemeMode): void {
    this._theme.set(mode);
    this.persistTheme(mode);
  }

  toggleTheme(): void {
    const next: ThemeMode = this._theme() === 'dark' ? 'light' : 'dark';
    this.setTheme(next);
  }

  private resolveInitialTheme(): ThemeMode {
    const stored = this.readStoredTheme();
    if (stored) {
      return stored;
    }

    const win = this.document.defaultView;
    if (win?.matchMedia && win.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }

    return 'light';
  }

  private readStoredTheme(): ThemeMode | null {
    try {
      const win = this.document.defaultView;
      const value = win?.localStorage?.getItem(THEME_STORAGE_KEY);
      if (value === 'light' || value === 'dark') {
        return value;
      }
    } catch {
      // Ignore storage access errors in restricted browser modes
    }
    return null;
  }

  private persistTheme(mode: ThemeMode): void {
    try {
      const win = this.document.defaultView;
      win?.localStorage?.setItem(THEME_STORAGE_KEY, mode);
    } catch {
      // Ignore storage access errors in restricted browser modes
    }
  }
}
