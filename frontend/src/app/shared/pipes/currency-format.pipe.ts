import { Pipe, PipeTransform } from '@angular/core';

export function formatCurrencyAmount(
  amount: number | null | undefined,
  currency = 'EUR',
  compact = false
): string {
  if (amount === null || amount === undefined || !Number.isFinite(amount)) {
    return '—';
  }

  const normalizedCurrency = currency.trim().toUpperCase() || 'EUR';

  try {
    return new Intl.NumberFormat('en-IE', {
      style: 'currency',
      currency: normalizedCurrency,
      notation: compact && Math.abs(amount) >= 1000 ? 'compact' : 'standard',
      maximumFractionDigits: compact && Math.abs(amount) >= 1000 ? 1 : 0
    }).format(amount);
  } catch {
    const rounded = Math.round(amount).toLocaleString('en-US');
    return `${normalizedCurrency} ${rounded}`;
  }
}

@Pipe({
  name: 'currencyFormat',
  standalone: true,
  pure: true
})
export class CurrencyFormatPipe implements PipeTransform {
  transform(
    amount: number | null | undefined,
    currency = 'EUR',
    compact = false
  ): string {
    return formatCurrencyAmount(amount, currency, compact);
  }
}

