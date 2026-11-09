import { Pipe, PipeTransform } from '@angular/core';
import { SalaryPeriod } from '../../core/models/job.models';
import { formatCurrencyAmount } from './currency-format.pipe';

function formatSalaryPeriodSuffix(period: SalaryPeriod | null | undefined): string {
  switch (period) {
    case 'MONTHLY':
      return '/ mo';
    case 'DAILY':
      return '/ day';
    case 'HOURLY':
      return '/ hr';
    case 'YEARLY':
    default:
      return '/ yr';
  }
}

export function formatSalaryRangeText(
  salaryMin: number | null | undefined,
  salaryMax: number | null | undefined,
  currency: string | null | undefined = 'EUR',
  period: SalaryPeriod | null | undefined = 'YEARLY',
  compact = true
): string {
  const hasMin = salaryMin !== null && salaryMin !== undefined && Number.isFinite(salaryMin) && salaryMin > 0;
  const hasMax = salaryMax !== null && salaryMax !== undefined && Number.isFinite(salaryMax) && salaryMax > 0;

  if (!hasMin && !hasMax) {
    return 'Salary undisclosed';
  }

  const resolvedCurrency = currency?.trim() ? currency.trim().toUpperCase() : 'EUR';
  const periodSuffix = formatSalaryPeriodSuffix(period);

  if (hasMin && hasMax) {
    const minVal = salaryMin as number;
    const maxVal = salaryMax as number;
    if (Math.round(minVal) === Math.round(maxVal)) {
      return `${formatCurrencyAmount(minVal, resolvedCurrency, compact)} ${periodSuffix}`;
    }
    return `${formatCurrencyAmount(minVal, resolvedCurrency, compact)} – ${formatCurrencyAmount(
      maxVal,
      resolvedCurrency,
      compact
    )} ${periodSuffix}`;
  }

  if (hasMin) {
    return `From ${formatCurrencyAmount(salaryMin, resolvedCurrency, compact)} ${periodSuffix}`;
  }

  return `Up to ${formatCurrencyAmount(salaryMax, resolvedCurrency, compact)} ${periodSuffix}`;
}

@Pipe({
  name: 'salaryRange',
  standalone: true,
  pure: true
})
export class SalaryRangePipe implements PipeTransform {
  transform(
    salaryMin: number | null | undefined,
    salaryMax: number | null | undefined,
    currency: string | null | undefined = 'EUR',
    period: SalaryPeriod | null | undefined = 'YEARLY',
    compact = true
  ): string {
    return formatSalaryRangeText(salaryMin, salaryMax, currency, period, compact);
  }
}

