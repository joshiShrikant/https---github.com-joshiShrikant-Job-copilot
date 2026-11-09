import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StatusBadgeComponent } from './status-badge.component';

describe('StatusBadgeComponent', () => {
  let fixture: ComponentFixture<StatusBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusBadgeComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(StatusBadgeComponent);
  });

  it('should map JobSponsorStatus values to expected tone, icon, and label', () => {
    fixture.componentRef.setInput('sponsorStatus', 'VERIFIED_SPONSOR');
    fixture.detectChanges();

    const badgeEl = (fixture.nativeElement as HTMLElement).querySelector('.status-badge');
    expect(badgeEl?.getAttribute('data-tone')).toBe('success');
    expect(badgeEl?.textContent).toContain('Verified Sponsor');
    expect(badgeEl?.querySelector('.badge-icon')?.textContent?.trim()).toBe('verified_user');

    fixture.componentRef.setInput('sponsorStatus', 'NO_SPONSORSHIP');
    fixture.detectChanges();
    expect(badgeEl?.getAttribute('data-tone')).toBe('danger');
    expect(badgeEl?.textContent).toContain('No Sponsorship');
  });

  it('should map SponsorConfidenceLevel, ApplicationStatus, and WorkArrangement inputs', () => {
    fixture.componentRef.setInput('confidenceLevel', 'MEDIUM');
    fixture.detectChanges();
    const badgeEl = (fixture.nativeElement as HTMLElement).querySelector('.status-badge');
    expect(badgeEl?.getAttribute('data-tone')).toBe('brand');
    expect(badgeEl?.textContent).toContain('Medium Confidence');

    fixture.componentRef.setInput('confidenceLevel', null);
    fixture.componentRef.setInput('applicationStatus', 'OFFER');
    fixture.detectChanges();
    expect(badgeEl?.getAttribute('data-tone')).toBe('success');
    expect(badgeEl?.textContent).toContain('Offer');

    fixture.componentRef.setInput('applicationStatus', null);
    fixture.componentRef.setInput('workArrangement', 'REMOTE');
    fixture.detectChanges();
    expect(badgeEl?.textContent).toContain('Remote');
  });

  it('should support explicit label, tone, icon, and md sizing', () => {
    fixture.componentRef.setInput('label', 'EU Blue Card');
    fixture.componentRef.setInput('tone', 'warning');
    fixture.componentRef.setInput('icon', 'verified');
    fixture.componentRef.setInput('size', 'md');
    fixture.detectChanges();

    const badgeEl = (fixture.nativeElement as HTMLElement).querySelector('.status-badge');
    expect(badgeEl?.classList.contains('size-md')).toBeTrue();
    expect(badgeEl?.getAttribute('data-tone')).toBe('warning');
    expect(badgeEl?.textContent).toContain('EU Blue Card');
  });

  it('should map all 6 VisaSponsorshipStatus classifications accurately', () => {
    const badgeEl = (fixture.nativeElement as HTMLElement).querySelector('.status-badge');

    const cases: Array<[
      'VERIFIED_SPONSOR' | 'SPONSORSHIP_MENTIONED' | 'RELOCATION_ASSISTANCE_MENTIONED' | 'POSSIBLE_UNCONFIRMED' | 'NOT_MENTIONED' | 'UNAVAILABLE',
      string,
      string
    ]> = [
      ['VERIFIED_SPONSOR', 'success', 'Verified sponsor'],
      ['SPONSORSHIP_MENTIONED', 'brand', 'Sponsorship explicitly mentioned'],
      ['RELOCATION_ASSISTANCE_MENTIONED', 'brand', 'Relocation assistance mentioned'],
      ['POSSIBLE_UNCONFIRMED', 'warning', 'Possible but unconfirmed'],
      ['NOT_MENTIONED', 'neutral', 'Not mentioned'],
      ['UNAVAILABLE', 'danger', 'Unavailable']
    ];

    for (const [status, expectedTone, expectedLabel] of cases) {
      fixture.componentRef.setInput('visaStatus', status);
      fixture.detectChanges();
      expect(badgeEl?.getAttribute('data-tone')).toBe(expectedTone);
      expect(badgeEl?.textContent).toContain(expectedLabel);
    }
  });
});

