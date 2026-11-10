import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ResolvedTheme, ThemeMode } from '../../services/theme.service';
import { BreadcrumbsComponent } from '../breadcrumbs/breadcrumbs.component';
import { BreadcrumbItem } from '../navigation.config';
import { UserMenuComponent } from '../user-menu/user-menu.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, BreadcrumbsComponent, UserMenuComponent],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HeaderComponent {
  readonly breadcrumbs = input<readonly BreadcrumbItem[]>([]);
  readonly showMobileMenuButton = input<boolean>(false);
  readonly isMobileDrawerOpen = input<boolean>(false);
  readonly isSidebarCollapsed = input<boolean>(false);
  readonly themeMode = input<ThemeMode>('system');
  readonly systemTheme = input<ResolvedTheme>('light');
  readonly isDarkMode = input<boolean>(false);
  readonly userDisplayName = input<string>('Workspace Guest');
  readonly userSecondaryLabel = input<string>('Not signed in');
  readonly isAuthenticated = input<boolean>(false);
  readonly unreadNotificationsCount = input<number>(0);

  readonly mobileMenuToggle = output<void>();
  readonly sidebarCollapseToggle = output<void>();
  readonly themeSelect = output<ThemeMode>();
  readonly themeToggle = output<void>();
  readonly logoutRequested = output<void>();
}
