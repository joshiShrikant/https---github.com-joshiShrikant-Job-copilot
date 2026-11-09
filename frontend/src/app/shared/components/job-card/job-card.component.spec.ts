import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JobSummary } from '../../../core/models/job.models';
import { JobCardComponent } from './job-card.component';

describe('JobCardComponent', () => {
  let fixture: ComponentFixture<JobCardComponent>;

  const sampleJob: JobSummary = {
    id: 42,
    title: 'Senior Java Platform Engineer',
    companyName: 'Adyen Payments',
    location: 'Amsterdam, Netherlands',
    city: 'Amsterdam',
    country: 'Netherlands',
    workArrangement: 'HYBRID',
    employmentType: 'FULL_TIME',
    experienceLevel: 'SENIOR',
    salaryMin: 85000,
    salaryMax: 110000,
    salaryCurrency: 'EUR',
    salaryPeriod: 'YEARLY',
    sponsorStatus: 'VERIFIED_SPONSOR',
    matchPercentage: 91,
    sourceProvider: 'ARBEITNOW',
    postedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    isSaved: true,
    isInterested: false,
    skills: ['Java', 'Spring Boot', 'PostgreSQL', 'Kafka', 'Kubernetes', 'Docker', 'AWS'],
    descriptionSnippet: 'Build resilient European settlement infrastructure with visa sponsorship.'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobCardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(JobCardComponent);
    fixture.componentRef.setInput('job', sampleJob);
    fixture.detectChanges();
  });

  it('should render company monogram, title, badges, salary, skills overflow, and match gauge', () => {
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.company-avatar')?.textContent?.trim()).toBe('AP');
    expect(host.querySelector('.job-title-btn')?.textContent?.trim()).toBe(
      'Senior Java Platform Engineer'
    );
    expect(host.querySelector('.meta-top')?.textContent).toContain('Amsterdam, Netherlands');
    expect(host.querySelector('.salary-chip')?.textContent).toContain('/ yr');
    expect(host.querySelector('app-match-score-gauge')?.textContent).toContain('91%');

    const skillPills = Array.from(host.querySelectorAll('.skill-pill')).map(el =>
      el.textContent?.trim()
    );
    expect(skillPills.length).toBe(6);
    expect(skillPills[5]).toBe('+2 more');
  });

  it('should emit save, interested, hide, inspect, and apply actions with ARIA active states', () => {
    const inspectSpy = jasmine.createSpy('inspect');
    const saveSpy = jasmine.createSpy('save');
    const interestedSpy = jasmine.createSpy('interested');
    const hideSpy = jasmine.createSpy('hide');
    const applySpy = jasmine.createSpy('apply');

    fixture.componentInstance.inspectRequested.subscribe(inspectSpy);
    fixture.componentInstance.saveToggle.subscribe(saveSpy);
    fixture.componentInstance.interestedToggle.subscribe(interestedSpy);
    fixture.componentInstance.hideRequested.subscribe(hideSpy);
    fixture.componentInstance.applyRequested.subscribe(applySpy);

    const host = fixture.nativeElement as HTMLElement;
    const saveBtn = host.querySelector<HTMLButtonElement>('[data-card-action="save"]');
    expect(saveBtn?.getAttribute('aria-pressed')).toBe('true');

    saveBtn?.click();
    host.querySelector<HTMLButtonElement>('[data-card-action="interested"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-card-action="hide"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-card-action="inspect"]')?.click();
    host.querySelector<HTMLButtonElement>('[data-card-action="apply"]')?.click();

    expect(saveSpy).toHaveBeenCalledWith(sampleJob);
    expect(interestedSpy).toHaveBeenCalledWith(sampleJob);
    expect(hideSpy).toHaveBeenCalledWith(sampleJob);
    expect(inspectSpy).toHaveBeenCalledWith(sampleJob);
    expect(applySpy).toHaveBeenCalledWith(sampleJob);
  });

  it('should hide snippet and apply compact gauge size when compact input is true', () => {
    fixture.componentRef.setInput('compact', true);
    fixture.componentRef.setInput('selected', true);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const card = host.querySelector('.job-card');
    expect(card?.classList.contains('is-compact')).toBeTrue();
    expect(card?.classList.contains('is-selected')).toBeTrue();
    expect(host.querySelector('.snippet-text')).toBeNull();
    expect(host.querySelector('.match-gauge.size-sm')).toBeTruthy();
  });

  it('should display Hidden badge and emit unhideRequested when job is hidden', () => {
    const unhideSpy = jasmine.createSpy('unhide');
    fixture.componentInstance.unhideRequested.subscribe(unhideSpy);
    const hiddenJob: JobSummary = { ...sampleJob, isHidden: true };
    fixture.componentRef.setInput('job', hiddenJob);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.hidden-badge')?.textContent).toContain('Hidden');
    const unhideBtn = host.querySelector<HTMLButtonElement>('[data-card-action="unhide"]');
    expect(unhideBtn).toBeTruthy();
    unhideBtn?.click();
    expect(unhideSpy).toHaveBeenCalledWith(hiddenJob);
  });
});

