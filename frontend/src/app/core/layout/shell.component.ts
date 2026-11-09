import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  effect,
  inject,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { LayoutStateService } from '../services/layout-state.service';
import { NotificationService } from '../services/notification.service';
import { ThemeService } from '../services/theme.service';
import { HeaderComponent } from './header/header.component';
import { SidebarComponent } from './sidebar/sidebar.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, HeaderComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShellComponent {
  readonly layout = inject(LayoutStateService);
  readonly theme = inject(ThemeService);
  readonly auth = inject(AuthService);
  private readonly notificationService = inject(NotificationService);

  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly mainContentRef = viewChild<ElementRef<HTMLElement>>('mainContent');

  readonly unreadCount = this.notificationService.unreadCount;

  constructor() {
    effect(() => {
      if (this.auth.isAuthenticated()) {
        this.notificationService
          .loadUnreadCount()
          .pipe(
            catchError(() => of(null)),
            takeUntilDestroyed(this.destroyRef)
          )
          .subscribe();
      } else {
        this.notificationService.resetState();
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.layout.isMobileDrawerOpen()) {
      this.layout.closeMobileDrawer();
    }
  }

  focusMainContent(event: Event): void {
    event.preventDefault();
    this.mainContentRef()?.nativeElement.focus();
  }

  onLogoutRequested(): void {
    this.auth
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.notificationService.resetState();
        void this.router.navigateByUrl('/auth/login');
      });
  }
}
