import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { THEME_STORAGE_KEY } from '../../services/theme.service';
import { AuthLayoutComponent } from './auth-layout.component';

describe('AuthLayoutComponent', () => {
  beforeEach(async () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'light');
    document.documentElement.removeAttribute('data-theme');

    await TestBed.configureTestingModule({
      imports: [AuthLayoutComponent],
      providers: [provideRouter([])]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
  });

  it('should render centered auth card without workspace sidebar and provide theme toggle', () => {
    const fixture = TestBed.createComponent(AuthLayoutComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.auth-card')).toBeTruthy();
    expect(host.querySelector('.workspace-link')?.getAttribute('href')).toBe('/dashboard');
    expect(host.querySelector('app-sidebar')).toBeNull();

    const toggleBtn = host.querySelector<HTMLButtonElement>('.theme-toggle-btn');
    toggleBtn?.click();
    fixture.detectChanges();
    TestBed.flushEffects();

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});

