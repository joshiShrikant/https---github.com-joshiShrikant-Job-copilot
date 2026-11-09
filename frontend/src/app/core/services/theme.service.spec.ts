import { TestBed } from '@angular/core/testing';
import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.style.colorScheme = '';
  });

  afterEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.style.colorScheme = '';
  });

  it('should default to light theme when no stored preference exists and media query does not prefer dark', () => {
    spyOn(window, 'matchMedia').and.returnValue({
      matches: false,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false
    } as MediaQueryList);

    TestBed.configureTestingModule({});
    const service = TestBed.inject(ThemeService);
    TestBed.flushEffects();

    expect(service.theme()).toBe('light');
    expect(service.isDarkMode()).toBeFalse();
    expect(document.documentElement.hasAttribute('data-theme')).toBeFalse();
  });

  it('should initialize from localStorage when dark theme is stored', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');

    TestBed.configureTestingModule({});
    const service = TestBed.inject(ThemeService);
    TestBed.flushEffects();

    expect(service.theme()).toBe('dark');
    expect(service.isDarkMode()).toBeTrue();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('should toggle between light and dark themes and persist selection', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'light');

    TestBed.configureTestingModule({});
    const service = TestBed.inject(ThemeService);
    TestBed.flushEffects();

    expect(service.theme()).toBe('light');

    service.toggleTheme();
    TestBed.flushEffects();

    expect(service.theme()).toBe('dark');
    expect(service.isDarkMode()).toBeTrue();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');

    service.toggleTheme();
    TestBed.flushEffects();

    expect(service.theme()).toBe('light');
    expect(service.isDarkMode()).toBeFalse();
    expect(document.documentElement.hasAttribute('data-theme')).toBeFalse();
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });
});

