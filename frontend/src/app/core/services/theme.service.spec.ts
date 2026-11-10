import { TestBed } from '@angular/core/testing';
import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  let changeListeners: Array<(event: MediaQueryListEvent) => void>;

  function mockMatchMedia(initialMatches: boolean): jasmine.Spy {
    changeListeners = [];
    const mediaQueryList = {
      matches: initialMatches,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      addListener: jasmine.createSpy('addListener'),
      removeListener: jasmine.createSpy('removeListener'),
      addEventListener: (_type: string, listener: EventListenerOrEventListenerObject) => {
        if (typeof listener === 'function') {
          changeListeners.push(listener as (event: MediaQueryListEvent) => void);
        }
      },
      removeEventListener: (_type: string, listener: EventListenerOrEventListenerObject) => {
        changeListeners = changeListeners.filter(fn => fn !== listener);
      },
      dispatchEvent: () => false
    } as unknown as MediaQueryList;

    return spyOn(window, 'matchMedia').and.returnValue(mediaQueryList);
  }

  function emitSystemThemeChange(matches: boolean): void {
    const event = { matches, media: '(prefers-color-scheme: dark)' } as MediaQueryListEvent;
    changeListeners.forEach(listener => listener(event));
  }

  beforeEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.removeAttribute('data-theme-mode');
    document.documentElement.style.colorScheme = '';
  });

  afterEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.removeAttribute('data-theme-mode');
    document.documentElement.style.colorScheme = '';
  });

  it('should default to system theme and follow live OS dark/light changes when no stored preference exists', () => {
    mockMatchMedia(false);

    TestBed.configureTestingModule({});
    const service = TestBed.inject(ThemeService);
    TestBed.flushEffects();

    expect(service.theme()).toBe('system');
    expect(service.isSystemTheme()).toBeTrue();
    expect(service.systemTheme()).toBe('light');
    expect(service.resolvedTheme()).toBe('light');
    expect(service.isDarkMode()).toBeFalse();
    expect(document.documentElement.getAttribute('data-theme-mode')).toBe('system');
    expect(document.documentElement.hasAttribute('data-theme')).toBeFalse();
    expect(document.documentElement.style.colorScheme).toBe('light');

    emitSystemThemeChange(true);
    TestBed.flushEffects();

    expect(service.theme()).toBe('system');
    expect(service.systemTheme()).toBe('dark');
    expect(service.resolvedTheme()).toBe('dark');
    expect(service.isDarkMode()).toBeTrue();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('should keep explicit light or dark selection even when OS preference changes', () => {
    mockMatchMedia(true);
    localStorage.setItem(THEME_STORAGE_KEY, 'light');

    TestBed.configureTestingModule({});
    const service = TestBed.inject(ThemeService);
    TestBed.flushEffects();

    expect(service.theme()).toBe('light');
    expect(service.systemTheme()).toBe('dark');
    expect(service.resolvedTheme()).toBe('light');
    expect(service.isDarkMode()).toBeFalse();
    expect(document.documentElement.hasAttribute('data-theme')).toBeFalse();

    emitSystemThemeChange(false);
    TestBed.flushEffects();
    expect(service.resolvedTheme()).toBe('light');

    service.setTheme('system');
    TestBed.flushEffects();
    expect(service.isSystemTheme()).toBeTrue();
    expect(service.resolvedTheme()).toBe('light');
  });

  it('should initialize from localStorage when dark theme is stored', () => {
    mockMatchMedia(false);
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');

    TestBed.configureTestingModule({});
    const service = TestBed.inject(ThemeService);
    TestBed.flushEffects();

    expect(service.theme()).toBe('dark');
    expect(service.resolvedTheme()).toBe('dark');
    expect(service.isDarkMode()).toBeTrue();
    expect(document.documentElement.getAttribute('data-theme-mode')).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('should toggle between light and dark themes and cycle system -> light -> dark -> system', () => {
    mockMatchMedia(false);
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

    service.setTheme('system');
    TestBed.flushEffects();
    expect(service.theme()).toBe('system');

    service.cycleTheme();
    TestBed.flushEffects();
    expect(service.theme()).toBe('light');

    service.cycleTheme();
    TestBed.flushEffects();
    expect(service.theme()).toBe('dark');

    service.cycleTheme();
    TestBed.flushEffects();
    expect(service.theme()).toBe('system');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('system');
  });
});

