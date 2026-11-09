import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  inject,
  input,
  output,
  signal
} from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-user-menu',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './user-menu.component.html',
  styleUrl: './user-menu.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserMenuComponent {
  private readonly hostRef = inject(ElementRef<HTMLElement>);

  readonly displayName = input<string>('Workspace Guest');
  readonly secondaryLabel = input<string>('Not signed in');
  readonly isAuthenticated = input<boolean>(false);
  readonly isDarkMode = input<boolean>(false);

  readonly themeToggle = output<void>();
  readonly logoutRequested = output<void>();

  readonly isOpen = signal<boolean>(false);

  readonly initials = computed(() => {
    const parts = this.displayName().trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) {
      return 'EJ';
    }
    if (parts.length === 1) {
      return parts[0].slice(0, 2).toUpperCase();
    }
    return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
  });

  toggleMenu(): void {
    this.isOpen.update(open => !open);
  }

  closeMenu(): void {
    this.isOpen.set(false);
  }

  onThemeToggleClick(): void {
    this.themeToggle.emit();
    this.closeMenu();
  }

  onLogoutClick(): void {
    this.logoutRequested.emit();
    this.closeMenu();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isOpen()) {
      return;
    }
    const target = event.target as Node | null;
    if (target && !this.hostRef.nativeElement.contains(target)) {
      this.closeMenu();
    }
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.isOpen()) {
      this.closeMenu();
    }
  }
}
