import { ChangeDetectionStrategy, Component, HostListener, input, output } from '@angular/core';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfirmDialogComponent {
  readonly open = input<boolean>(false);
  readonly title = input<string>('Confirm action');
  readonly message = input<string>('Are you sure you want to continue?');
  readonly confirmLabel = input<string>('Confirm');
  readonly cancelLabel = input<string>('Cancel');
  readonly tone = input<'default' | 'danger'>('default');
  readonly icon = input<string>('help_outline');
  readonly busy = input<boolean>(false);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.open() && !this.busy()) {
      this.cancelled.emit();
    }
  }

  onBackdropClick(): void {
    if (!this.busy()) {
      this.cancelled.emit();
    }
  }

  onCancel(): void {
    if (!this.busy()) {
      this.cancelled.emit();
    }
  }

  onConfirm(): void {
    if (!this.busy()) {
      this.confirmed.emit();
    }
  }
}

