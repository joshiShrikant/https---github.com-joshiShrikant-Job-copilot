import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatchScoreGaugeComponent } from './match-score-gauge.component';

describe('MatchScoreGaugeComponent', () => {
  let fixture: ComponentFixture<MatchScoreGaugeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MatchScoreGaugeComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(MatchScoreGaugeComponent);
  });

  it('should render unscored fallback state cleanly when score is null', () => {
    fixture.componentRef.setInput('score', null);
    fixture.componentRef.setInput('showLabel', true);
    fixture.detectChanges();

    const meter = (fixture.nativeElement as HTMLElement).querySelector('.match-gauge');
    expect(meter?.getAttribute('data-tier')).toBe('unscored');
    expect(meter?.querySelector('.score-value')?.textContent?.trim()).toBe('—');
    expect(meter?.querySelector('.meta-tier')?.textContent?.trim()).toBe('Score pending');
  });

  it('should assign semantic tier colors and clamp extreme numeric inputs', () => {
    fixture.componentRef.setInput('score', 89.6);
    fixture.detectChanges();
    const meter = (fixture.nativeElement as HTMLElement).querySelector('.match-gauge');
    expect(meter?.getAttribute('data-tier')).toBe('high');
    expect(meter?.getAttribute('aria-valuenow')).toBe('90');
    expect(meter?.querySelector('.score-value')?.textContent).toContain('90%');

    fixture.componentRef.setInput('score', 65);
    fixture.detectChanges();
    expect(meter?.getAttribute('data-tier')).toBe('good');

    fixture.componentRef.setInput('score', 45);
    fixture.detectChanges();
    expect(meter?.getAttribute('data-tier')).toBe('fair');

    fixture.componentRef.setInput('score', -15);
    fixture.detectChanges();
    expect(meter?.getAttribute('data-tier')).toBe('low');
    expect(meter?.getAttribute('aria-valuenow')).toBe('0');

    fixture.componentRef.setInput('score', 142);
    fixture.detectChanges();
    expect(meter?.getAttribute('aria-valuenow')).toBe('100');
  });
});

