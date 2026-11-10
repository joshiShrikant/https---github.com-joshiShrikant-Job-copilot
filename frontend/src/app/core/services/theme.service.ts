import { DOCUMENT, DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';
export type ResolvedTheme = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'eurojob-theme';

const DARK_MEDIA_QUERY = '(prefers-color-scheme: dark)';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _theme = signal<ThemeMode>(this.readStoredTheme() ?? 'system');
  private readonly _systemPrefersDark = signal<boolean>(this.readSystemPrefersDark());

  readonly theme = this._theme.asReadonly();
  readonly systemTheme = computed<ResolvedTheme>(() =>
    this._systemPrefersDark() ? 'dark' : 'light'
  );
  readonly resolvedTheme = computed<ResolvedTheme>(() => {
    const mode = this._theme();
    if (mode === 'system') {
      return this.systemTheme();
    }
    return mode;
  });
  readonly isDarkMode = computed<boolean>(() => this.resolvedTheme() === 'dark');
  readonly isSystemTheme = computed<boolean>(() => this._theme() === 'system');

  constructor() {
    this.listenForSystemThemeChanges();

    effect(() => {
      const mode = this._theme();
      const resolved = this.resolvedTheme();
      const root = this.document.documentElement;

      root.setAttribute('data-theme-mode', mode);
      if (resolved === 'dark') {
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
    const next: ThemeMode = this.isDarkMode() ? 'light' : 'dark';
    this.setTheme(next);
  }

  cycleTheme(): void {
    const current = this._theme();
    const next: ThemeMode =
      current === 'system' ? 'light' : current === 'light' ? 'dark' : 'system';
    this.setTheme(next);
  }

  private readSystemPrefersDark(): boolean {
    const win = this.document.defaultView;
    if (!win?.matchMedia) {
      return false;
    }
    return win.matchMedia(DARK_MEDIA_QUERY).matches;
  }

  private listenForSystemThemeChanges(): void {
    const win = this.document.defaultView;
    if (!win?.matchMedia) {
      return;
    }

    const mediaQuery = win.matchMedia(DARK_MEDIA_QUERY);
    this._systemPrefersDark.set(mediaQuery.matches);

    const handleChange = (event: MediaQueryListEvent): void => {
      this._systemPrefersDark.set(event.matches);
    };

    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', handleChange);
      this.destroyRef.onDestroy(() => {
        mediaQuery.removeEventListener('change', handleChange);
      });
    } else if (typeof mediaQuery.addListener === 'function') {
      mediaQuery.addListener(handleChange);
      this.destroyRef.onDestroy(() => {
        mediaQuery.removeListener(handleChange);
      });
    }
  }

  private readStoredTheme(): ThemeMode | null {
    try {
      const win = this.document.defaultView;
      const value = win?.localStorage?.getItem(THEME_STORAGE_KEY);
      if (value === 'system' || value === 'light' || value === 'dark') {
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
