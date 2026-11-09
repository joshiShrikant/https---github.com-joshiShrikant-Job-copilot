import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-account-security-section',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './account-security-section.component.html',
  styleUrl: './account-security-section.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AccountSecuritySectionComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly currentUser = this.authService.currentUser;
  readonly fullName = this.authService.userFullName;
  readonly isLoggingOut = signal<boolean>(false);
  readonly confirmRevokeAll = signal<boolean>(false);

  readonly roleLabel = computed(() => {
    const role = this.currentUser()?.role;
    if (role === 'ROLE_ADMIN') {
      return 'Administrator';
    }
    if (role === 'ROLE_USER') {
      return 'Candidate';
    }
    return role ?? 'Guest';
  });

  requestRevokeAll(): void {
    this.confirmRevokeAll.set(true);
  }

  cancelRevokeAll(): void {
    this.confirmRevokeAll.set(false);
  }

  signOutCurrentDevice(): void {
    this.performLogout(false, '/auth/login');
  }

  signOutAllDevices(): void {
    this.performLogout(true, '/auth/login');
  }

  signOutToResetPassword(): void {
    this.performLogout(false, '/auth/forgot-password');
  }

  private performLogout(revokeAllSessions: boolean, targetRoute: string): void {
    if (this.isLoggingOut()) {
      return;
    }

    this.isLoggingOut.set(true);
    this.authService
      .logout(revokeAllSessions)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoggingOut.set(false))
      )
      .subscribe(() => {
        this.confirmRevokeAll.set(false);
        void this.router.navigateByUrl(targetRoute);
      });
  }
}

