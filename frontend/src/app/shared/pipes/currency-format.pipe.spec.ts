import { CurrencyFormatPipe } from './currency-format.pipe';
import { SalaryRangePipe } from './salary-range.pipe';

describe('CurrencyFormatPipe & SalaryRangePipe', () => {
  const currencyPipe = new CurrencyFormatPipe();
  const salaryRangePipe = new SalaryRangePipe();

  it('should format currency amounts with standard and compact notation and fallback cleanly', () => {
    expect(currencyPipe.transform(null)).toBe('—');
    expect(currencyPipe.transform(undefined)).toBe('—');
    expect(currencyPipe.transform(Number.NaN)).toBe('—');

    const eurFull = currencyPipe.transform(85000, 'EUR', false);
    expect(eurFull).toContain('€');
    expect(eurFull).toContain('85,000');

    const eurCompact = currencyPipe.transform(85000, 'EUR', true);
    expect(eurCompact).toContain('€');
    expect(eurCompact.toLowerCase()).toContain('85k');
  });

  it('should handle invalid ISO currency code gracefully', () => {
    expect(currencyPipe.transform(5000, 'INVALID', false)).toBe('INVALID 5,000');
  });

  it('should format full salary ranges, single bounds, equal bounds, periods, and undisclosed state', () => {
    expect(salaryRangePipe.transform(null, null)).toBe('Salary undisclosed');
    expect(salaryRangePipe.transform(0, 0)).toBe('Salary undisclosed');

    const range = salaryRangePipe.transform(85000, 105000, 'EUR', 'YEARLY', true);
    expect(range).toContain('€');
    expect(range.toLowerCase()).toContain('85k');
    expect(range.toLowerCase()).toContain('105k');
    expect(range).toContain('–');
    expect(range).toContain('/ yr');

    const minOnly = salaryRangePipe.transform(70000, null, 'EUR', 'YEARLY', true);
    expect(minOnly).toMatch(/^From /);
    expect(minOnly).toContain('/ yr');

    const maxOnly = salaryRangePipe.transform(null, 95000, 'EUR', 'YEARLY', true);
    expect(maxOnly).toMatch(/^Up to /);
    expect(maxOnly).toContain('/ yr');

    const dailyEqual = salaryRangePipe.transform(650, 650, 'GBP', 'DAILY', false);
    expect(dailyEqual).toContain('650');
    expect(dailyEqual).toContain('/ day');
    expect(dailyEqual).not.toContain('–');
  });
});
