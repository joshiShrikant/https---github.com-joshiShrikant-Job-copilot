import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { THEME_STORAGE_KEY } from '../../services/theme.service';
import { AuthLayoutComponent } from './auth-layout.component';

describe('AuthLayoutComponent', () => {
  beforeEach(async () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'light');
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.removeAttribute('data-theme-mode');

    await TestBed.configureTestingModule({
      imports: [AuthLayoutComponent],
      providers: [provideRouter([])]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.removeAttribute('data-theme-mode');
  });

  it('should render centered auth card without workspace sidebar and provide system/light/dark theme switcher', () => {
    const fixture = TestBed.createComponent(AuthLayoutComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.auth-card')).toBeTruthy();
    expect(host.querySelector('.workspace-link')?.getAttribute('href')).toBe('/dashboard');
    expect(host.querySelector('app-sidebar')).toBeNull();

    const darkBtn = host.querySelector<HTMLButtonElement>('[data-theme-option="dark"]');
    const lightBtn = host.querySelector<HTMLButtonElement>('[data-theme-option="light"]');
    const systemBtn = host.querySelector<HTMLButtonElement>('[data-theme-option="system"]');
    expect(darkBtn).toBeTruthy();
    expect(lightBtn).toBeTruthy();
    expect(systemBtn).toBeTruthy();

    darkBtn?.click();
    fixture.detectChanges();
    TestBed.flushEffects();

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme-mode')).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');

    systemBtn?.click();
    fixture.detectChanges();
    TestBed.flushEffects();

    expect(document.documentElement.getAttribute('data-theme-mode')).toBe('system');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('system');

    lightBtn?.click();
    fixture.detectChanges();
    TestBed.flushEffects();

    expect(document.documentElement.hasAttribute('data-theme')).toBeFalse();
    expect(document.documentElement.getAttribute('data-theme-mode')).toBe('light');
  });
});

