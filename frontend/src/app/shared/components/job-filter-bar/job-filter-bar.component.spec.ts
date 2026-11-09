import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  JobSearchFilters,
  createDefaultJobSearchFilters
} from '../../../core/models/job.models';
import { JobFilterBarComponent } from './job-filter-bar.component';

describe('JobFilterBarComponent', () => {
  let fixture: ComponentFixture<JobFilterBarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobFilterBarComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(JobFilterBarComponent);
    fixture.componentRef.setInput('filters', createDefaultJobSearchFilters());
    fixture.componentRef.setInput('debounceMs', 0);
    fixture.detectChanges();
  });

  it('should emit updated filters from search query, arrangement pills, and faceted selects', () => {
    const emitted: JobSearchFilters[] = [];
    fixture.componentInstance.filtersChange.subscribe(next => {
      emitted.push(next);
      fixture.componentRef.setInput('filters', next);
      fixture.detectChanges();
    });

    const host = fixture.nativeElement as HTMLElement;

    const searchInput = host.querySelector<HTMLInputElement>('[data-filter="query"]')!;
    searchInput.value = 'Spring Boot';
    searchInput.dispatchEvent(new Event('input'));

    host.querySelector<HTMLButtonElement>('[data-filter-arrangement="REMOTE"]')?.click();

    const countrySelect = host.querySelector<HTMLSelectElement>('[data-filter="country"]')!;
    countrySelect.value = 'Netherlands';
    countrySelect.dispatchEvent(new Event('change'));

    const sponsorSelect = host.querySelector<HTMLSelectElement>('[data-filter="sponsorStatus"]')!;
    sponsorSelect.value = 'VERIFIED_SPONSOR';
    sponsorSelect.dispatchEvent(new Event('change'));

    const experienceSelect = host.querySelector<HTMLSelectElement>('[data-filter="experienceLevel"]')!;
    experienceSelect.value = 'SENIOR';
    experienceSelect.dispatchEvent(new Event('change'));

    const minMatchSelect = host.querySelector<HTMLSelectElement>('[data-filter="minMatchScore"]')!;
    minMatchSelect.value = '80';
    minMatchSelect.dispatchEvent(new Event('change'));

    const cityInput = host.querySelector<HTMLInputElement>('[data-filter="city"]')!;
    cityInput.value = 'Amsterdam';
    cityInput.dispatchEvent(new Event('input'));

    host.querySelector<HTMLButtonElement>('[data-filter-flag="savedOnly"]')?.click();

    const last = emitted[emitted.length - 1];
    expect(last.query).toBe('Spring Boot');
    expect(last.workArrangement).toBe('REMOTE');
    expect(last.country).toBe('Netherlands');
    expect(last.sponsorStatus).toBe('VERIFIED_SPONSOR');
    expect(last.experienceLevel).toBe('SENIOR');
    expect(last.minMatchScore).toBe(80);
    expect(last.city).toBe('Amsterdam');
    expect(last.savedOnly).toBeTrue();
  });

  it('should render active filter chips, remove individual chips, and emit resetRequested on clear all', () => {
    const resetSpy = jasmine.createSpy('resetRequested');
    const emitted: JobSearchFilters[] = [];
    fixture.componentInstance.resetRequested.subscribe(resetSpy);
    fixture.componentInstance.filtersChange.subscribe(next => {
      emitted.push(next);
      fixture.componentRef.setInput('filters', next);
      fixture.detectChanges();
    });

    fixture.componentRef.setInput('totalCount', 4);
    fixture.componentRef.setInput('filters', {
      ...createDefaultJobSearchFilters(),
      query: 'Java',
      country: 'Germany',
      sponsorStatus: 'VERIFIED_SPONSOR'
    });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-filter-results-count]')?.textContent?.trim()).toBe('4 roles');
    expect(host.querySelectorAll('.active-chip').length).toBe(3);

    host
      .querySelector<HTMLButtonElement>('[data-chip-key="country"] .chip-remove-btn')
      ?.click();
    expect(emitted[emitted.length - 1].country).toBe('');
    expect(host.querySelectorAll('.active-chip').length).toBe(2);

    host.querySelector<HTMLButtonElement>('[data-filter-action="reset-all"]')?.click();
    expect(resetSpy).toHaveBeenCalledTimes(1);
    expect(emitted[emitted.length - 1]).toEqual(createDefaultJobSearchFilters());
  });
});

