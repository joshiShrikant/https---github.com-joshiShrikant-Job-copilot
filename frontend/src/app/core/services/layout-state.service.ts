import { BreakpointObserver } from '@angular/cdk/layout';
import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter, map, startWith, tap } from 'rxjs';
import { BreadcrumbItem, findNavItemByPath } from '../layout/navigation.config';

export const SIDEBAR_COLLAPSE_STORAGE_KEY = 'eurojob-sidebar-collapsed';
const MOBILE_TABLET_QUERY = '(max-width: 1023.98px)';

@Injectable({
  providedIn: 'root'
})
export class LayoutStateService {
  private readonly router = inject(Router);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly document = inject(DOCUMENT);

  private readonly _desktopSidebarCollapsed = signal<boolean>(this.readInitialCollapseState());
  private readonly _mobileDrawerOpen = signal<boolean>(false);

  readonly isMobileOrTablet = toSignal(
    this.breakpointObserver.observe(MOBILE_TABLET_QUERY).pipe(
      map(state => state.matches),
      tap(isMobile => {
        if (!isMobile) {
          this._mobileDrawerOpen.set(false);
        }
      })
    ),
    { initialValue: false }
  );

  readonly isSidebarCollapsed = computed(
    () => !this.isMobileOrTablet() && this._desktopSidebarCollapsed()
  );

  readonly isMobileDrawerOpen = this._mobileDrawerOpen.asReadonly();

  private readonly navigationUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(event => event.urlAfterRedirects),
      startWith(this.router.url)
    ),
    { initialValue: this.router.url }
  );

  readonly breadcrumbs = computed<readonly BreadcrumbItem[]>(() => {
    const url = this.navigationUrl();
    const deepestSnapshot = this.getDeepestSnapshot(this.router.routerState.snapshot.root);
    const routeBreadcrumb = deepestSnapshot.data['breadcrumb'] as string | undefined;
    const routeSection = deepestSnapshot.data['section'] as string | undefined;
    const matchedNav = findNavItemByPath(url);

    const currentLabel = routeBreadcrumb ?? matchedNav?.label ?? 'Dashboard';
    const sectionLabel = routeSection ?? matchedNav?.section;

    const crumbs: BreadcrumbItem[] = [
      {
        label: 'EuroJob Copilot',
        route: '/dashboard',
        isCurrent: false
      }
    ];

    if (sectionLabel && sectionLabel !== 'Workspace' && sectionLabel !== currentLabel) {
      crumbs.push({
        label: sectionLabel,
        isCurrent: false
      });
    }

    crumbs.push({
      label: currentLabel,
      isCurrent: true
    });

    return crumbs;
  });

  readonly currentPageTitle = computed(() => {
    const items = this.breadcrumbs();
    return items[items.length - 1]?.label ?? 'Dashboard';
  });

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe(() => {
        this._mobileDrawerOpen.set(false);
      });
  }

  toggleSidebarCollapse(): void {
    const next = !this._desktopSidebarCollapsed();
    this.setSidebarCollapsed(next);
  }

  setSidebarCollapsed(collapsed: boolean): void {
    this._desktopSidebarCollapsed.set(collapsed);
    this.persistCollapseState(collapsed);
  }

  toggleMobileDrawer(): void {
    this._mobileDrawerOpen.update(open => !open);
  }

  openMobileDrawer(): void {
    this._mobileDrawerOpen.set(true);
  }

  closeMobileDrawer(): void {
    this._mobileDrawerOpen.set(false);
  }

  private getDeepestSnapshot(snapshot: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
    let current = snapshot;
    while (current.firstChild) {
      current = current.firstChild;
    }
    return current;
  }

  private readInitialCollapseState(): boolean {
    try {
      const win = this.document.defaultView;
      return win?.localStorage?.getItem(SIDEBAR_COLLAPSE_STORAGE_KEY) === 'true';
    } catch {
      return false;
    }
  }

  private persistCollapseState(collapsed: boolean): void {
    try {
      const win = this.document.defaultView;
      win?.localStorage?.setItem(SIDEBAR_COLLAPSE_STORAGE_KEY, String(collapsed));
    } catch {
      // Ignore storage failures
    }
  }
}
