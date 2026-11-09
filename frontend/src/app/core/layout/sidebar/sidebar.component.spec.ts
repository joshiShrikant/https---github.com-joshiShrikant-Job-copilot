import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { ALL_NAV_ITEMS } from '../navigation.config';
import { SidebarComponent } from './sidebar.component';

describe('SidebarComponent', () => {
  const expectedItems: Array<{ label: string; route: string }> = [
    { label: 'Dashboard', route: '/dashboard' },
    { label: 'Discover Jobs', route: '/jobs' },
    { label: 'Recommended Jobs', route: '/recommended-jobs' },
    { label: 'Visa Sponsorship', route: '/sponsorship' },
    { label: 'AI Resume Builder', route: '/resume-builder' },
    { label: 'Cover Letter Builder', route: '/cover-letter' },
    { label: 'My Resumes', route: '/resumes' },
    { label: 'My Applications', route: '/applications' },
    { label: 'Companies', route: '/companies' },
    { label: 'Analytics', route: '/analytics' },
    { label: 'Interview Preparation', route: '/interview-prep' },
    { label: 'Notifications', route: '/notifications' },
    { label: 'My Profile', route: '/profile' },
    { label: 'Settings', route: '/settings' }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [provideRouter([]), provideNoopAnimations()]
    }).compileComponents();
  });

  it('should define and render all 14 required sidebar navigation items with expected routes', () => {
    expect(ALL_NAV_ITEMS.length).toBe(14);

    const fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();

    const navLinks = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('a.nav-item')
    );
    expect(navLinks.length).toBe(14);

    for (const expected of expectedItems) {
      const link = navLinks.find(el => el.getAttribute('aria-label') === expected.label);
      expect(link).withContext(`Missing sidebar link for ${expected.label}`).toBeTruthy();
      expect(link?.getAttribute('href')).toBe(expected.route);
    }
  });

  it('should hide group headings and labels when collapsed on desktop and emit collapseToggle', () => {
    const fixture = TestBed.createComponent(SidebarComponent);
    fixture.componentRef.setInput('collapsed', true);
    fixture.componentRef.setInput('isMobileViewport', false);
    fixture.detectChanges();

    const hostEl = fixture.nativeElement as HTMLElement;
    expect(hostEl.querySelector('.sidebar')?.classList.contains('collapsed')).toBeTrue();
    expect(hostEl.querySelector('.nav-group-label')).toBeNull();
    expect(hostEl.querySelector('.nav-label')).toBeNull();

    let toggled = false;
    fixture.componentInstance.collapseToggle.subscribe(() => {
      toggled = true;
    });

    const toggleBtn = hostEl.querySelector<HTMLButtonElement>('.header-icon-btn');
    expect(toggleBtn?.getAttribute('aria-label')).toBe('Expand sidebar');
    toggleBtn?.click();
    expect(toggled).toBeTrue();
  });

  it('should support mobile drawer open state and emit mobileClose when an item is clicked', () => {
    const fixture = TestBed.createComponent(SidebarComponent);
    fixture.componentRef.setInput('collapsed', true);
    fixture.componentRef.setInput('isMobileViewport', true);
    fixture.componentRef.setInput('mobileOpen', true);
    fixture.detectChanges();

    const hostEl = fixture.nativeElement as HTMLElement;
    const aside = hostEl.querySelector('.sidebar');
    expect(aside?.classList.contains('mobile-viewport')).toBeTrue();
    expect(aside?.classList.contains('mobile-open')).toBeTrue();
    // Mobile drawer always displays labels cleanly even if desktop collapse preference is true
    expect(aside?.classList.contains('collapsed')).toBeFalse();
    expect(hostEl.querySelectorAll('.nav-label').length).toBe(14);

    let closed = false;
    fixture.componentInstance.mobileClose.subscribe(() => {
      closed = true;
    });

    const firstNavLink = hostEl.querySelector<HTMLAnchorElement>('a.nav-item');
    firstNavLink?.click();
    expect(closed).toBeTrue();
  });
});

