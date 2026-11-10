import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ResolvedTheme, ThemeMode } from '../../services/theme.service';
import { NAV_GROUPS, NavGroup, NavItem } from '../navigation.config';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SidebarComponent {
  readonly navGroups = input<readonly NavGroup[]>(NAV_GROUPS);
  readonly collapsed = input<boolean>(false);
  readonly mobileOpen = input<boolean>(false);
  readonly isMobileViewport = input<boolean>(false);
  readonly themeMode = input<ThemeMode>('system');
  readonly systemTheme = input<ResolvedTheme>('light');
  readonly isDarkMode = input<boolean>(false);

  readonly collapseToggle = output<void>();
  readonly mobileClose = output<void>();
  readonly themeSelect = output<ThemeMode>();
  readonly themeToggle = output<void>();
  readonly itemSelect = output<NavItem>();

  onNavItemClick(item: NavItem): void {
    this.itemSelect.emit(item);
    if (this.isMobileViewport()) {
      this.mobileClose.emit();
    }
  }
}
