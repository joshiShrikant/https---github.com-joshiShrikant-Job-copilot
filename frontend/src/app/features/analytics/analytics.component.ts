import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractApiErrorMessage } from '../../core/models/api.models';
import {
  CountryMarketInsight,
  DiscoveryTrendPoint,
  MarketInsightsResponse
} from '../../core/models/analytics.models';
import { AnalyticsService } from '../../core/services/analytics.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

export interface TrendWindowOption {
  readonly days: number;
  readonly label: string;
}

export interface TrendChartPoint {
  readonly date: string;
  readonly shortDate: string;
  readonly x: number;
  readonly yTotal: number;
  readonly yStack: number;
  readonly ySponsor: number;
  readonly totalDiscovered: number;
  readonly stackDiscovered: number;
  readonly sponsorshipDiscovered: number;
}

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, PageHeaderComponent, TimeAgoPipe],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AnalyticsComponent implements OnInit {
  private readonly analyticsService = inject(AnalyticsService);
  private readonly destroyRef = inject(DestroyRef);

  readonly windowOptions: readonly TrendWindowOption[] = [
    { days: 7, label: '7d' },
    { days: 14, label: '14d' },
    { days: 30, label: '30d' },
    { days: 90, label: '90d' },
    { days: 180, label: '180d' }
  ];

  readonly insights = signal<MarketInsightsResponse | null>(null);
  readonly selectedCountries = signal<readonly string[]>([]);
  readonly selectedDays = signal<number>(30);
  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly maxCountryTotal = computed<number>(() => {
    const rows = this.insights()?.countryComparisons ?? [];
    if (rows.length === 0) {
      return 1;
    }
    return Math.max(1, ...rows.map(r => r.totalJobs));
  });

  readonly trendChartData = computed<{
    readonly points: readonly TrendChartPoint[];
    readonly totalPath: string;
    readonly stackPath: string;
    readonly sponsorPath: string;
    readonly maxValue: number;
  }>(() => {
    const pts = this.insights()?.discoveryTrends ?? [];
    if (pts.length === 0) {
      return { points: [], totalPath: '', stackPath: '', sponsorPath: '', maxValue: 0 };
    }

    const width = 640;
    const height = 180;
    const padX = 22;
    const padY = 18;
    const plotW = width - padX * 2;
    const plotH = height - padY * 2;

    const maxValue = Math.max(
      1,
      ...pts.map(p =>
        Math.max(
          p.totalDiscovered,
          p.angularDiscovered + p.javaDiscovered,
          p.sponsorshipDiscovered
        )
      )
    );

    const mapped: TrendChartPoint[] = pts.map((p: DiscoveryTrendPoint, idx: number) => {
      const x = pts.length === 1 ? width / 2 : padX + (idx / (pts.length - 1)) * plotW;
      const stackVal = Math.min(p.totalDiscovered, p.angularDiscovered + p.javaDiscovered);
      const scaleY = (val: number) => height - padY - (val / maxValue) * plotH;
      return {
        date: p.date,
        shortDate: p.date.slice(5),
        x: Math.round(x * 10) / 10,
        yTotal: Math.round(scaleY(p.totalDiscovered) * 10) / 10,
        yStack: Math.round(scaleY(stackVal) * 10) / 10,
        ySponsor: Math.round(scaleY(p.sponsorshipDiscovered) * 10) / 10,
        totalDiscovered: p.totalDiscovered,
        stackDiscovered: stackVal,
        sponsorshipDiscovered: p.sponsorshipDiscovered
      };
    });

    const buildLine = (selector: (pt: TrendChartPoint) => number): string =>
      mapped.map((pt, idx) => `${idx === 0 ? 'M' : 'L'} ${pt.x} ${selector(pt)}`).join(' ');

    return {
      points: mapped,
      totalPath: buildLine(p => p.yTotal),
      stackPath: buildLine(p => p.yStack),
      sponsorPath: buildLine(p => p.ySponsor),
      maxValue
    };
  });

  readonly headerBadge = computed<string>(() => {
    const data = this.insights();
    if (!data) {
      return 'Market Intelligence';
    }
    return `${data.totals.totalActiveJobs} Collected Roles · ${data.countryComparisons.length} Countries`;
  });

  ngOnInit(): void {
    this.loadInsights();
  }

  loadInsights(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.analyticsService
      .getMarketInsights(this.selectedCountries(), this.selectedDays())
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: response => {
          this.insights.set(response);
        },
        error: (err: unknown) => {
          this.errorMessage.set(
            extractApiErrorMessage(err, 'Unable to load European market analytics right now.')
          );
        }
      });
  }

  onSelectDays(days: number): void {
    if (days === this.selectedDays()) {
      return;
    }
    this.selectedDays.set(days);
    this.loadInsights();
  }

  onToggleCountry(country: string): void {
    const current = this.selectedCountries();
    const exists = current.some(c => c.toLowerCase() === country.toLowerCase());
    if (exists) {
      this.selectedCountries.set(current.filter(c => c.toLowerCase() !== country.toLowerCase()));
    } else {
      this.selectedCountries.set([...current, country]);
    }
    this.loadInsights();
  }

  onClearCountries(): void {
    if (this.selectedCountries().length === 0) {
      return;
    }
    this.selectedCountries.set([]);
    this.loadInsights();
  }

  isCountrySelected(country: string): boolean {
    return this.selectedCountries().some(c => c.toLowerCase() === country.toLowerCase());
  }

  barPercent(value: number): number {
    return Math.max(4, Math.round((value / this.maxCountryTotal()) * 100));
  }

  formatSalaryRange(row: CountryMarketInsight): string {
    if (!row.salarySampleCount || row.salaryMin === null || row.salaryMax === null) {
      return 'Insufficient salary samples';
    }
    const cur = row.primarySalaryCurrency ?? 'EUR';
    const minK = Math.round(row.salaryMin / 1000);
    const maxK = Math.round(row.salaryMax / 1000);
    const medPart =
      row.salaryMedian !== null ? ` · median ${cur} ${Math.round(row.salaryMedian / 1000)}k` : '';
    return `${cur} ${minK}k–${maxK}k${medPart}`;
  }
}

