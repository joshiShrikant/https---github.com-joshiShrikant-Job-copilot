import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmDialogComponent } from './confirm-dialog.component';

describe('ConfirmDialogComponent', () => {
  let fixture: ComponentFixture<ConfirmDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(ConfirmDialogComponent);
  });

  it('should remain hidden when closed and render accessible dialog when opened', () => {
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="dialog"]')).toBeNull();

    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('title', 'Delete draft');
    fixture.componentRef.setInput('message', 'This cannot be undone.');
    fixture.componentRef.setInput('tone', 'danger');
    fixture.detectChanges();

    const dialog = (fixture.nativeElement as HTMLElement).querySelector('[role="dialog"]');
    expect(dialog).toBeTruthy();
    expect(dialog?.textContent).toContain('Delete draft');
    expect(dialog?.textContent).toContain('This cannot be undone.');
    expect(dialog?.querySelector('[data-dialog-action="confirm"]')?.classList.contains('is-danger')).toBeTrue();
  });

  it('should emit confirmed and cancelled events from buttons, backdrop, and Escape key', () => {
    const confirmSpy = jasmine.createSpy('confirmed');
    const cancelSpy = jasmine.createSpy('cancelled');
    fixture.componentInstance.confirmed.subscribe(confirmSpy);
    fixture.componentInstance.cancelled.subscribe(cancelSpy);

    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    host.querySelector<HTMLButtonElement>('[data-dialog-action="confirm"]')?.click();
    expect(confirmSpy).toHaveBeenCalledTimes(1);

    host.querySelector<HTMLButtonElement>('[data-dialog-action="cancel"]')?.click();
    host.querySelector<HTMLDivElement>('.dialog-backdrop')?.click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(cancelSpy).toHaveBeenCalledTimes(3);
  });

  it('should prevent confirmation or cancellation while busy', () => {
    const confirmSpy = jasmine.createSpy('confirmed');
    const cancelSpy = jasmine.createSpy('cancelled');
    fixture.componentInstance.confirmed.subscribe(confirmSpy);
    fixture.componentInstance.cancelled.subscribe(cancelSpy);

    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('busy', true);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    host.querySelector<HTMLDivElement>('.dialog-backdrop')?.click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(confirmSpy).not.toHaveBeenCalled();
    expect(cancelSpy).not.toHaveBeenCalled();
    expect(host.querySelector('[data-dialog-action="confirm"]')?.textContent?.trim()).toBe('Working...');
  });
});

