import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

const HOST_PATTERN = /^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i;

export function trimToNull(value: string | null | undefined): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

export function nonBlankValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const raw = control.value;
    if (typeof raw !== 'string') {
      return null;
    }
    return raw.trim().length === 0 ? { blank: true } : null;
  };
}

export function httpUrlValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const raw = control.value;
    if (typeof raw !== 'string' || raw.trim().length === 0) {
      return null;
    }

    const trimmed = raw.trim();
    if (/\s/.test(trimmed)) {
      return { invalidUrl: true };
    }

    try {
      const parsed = new URL(trimmed);
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        return { invalidUrl: true };
      }
      if (parsed.username || parsed.password) {
        return { invalidUrl: true };
      }
      if (!parsed.hostname || !HOST_PATTERN.test(parsed.hostname)) {
        return { invalidUrl: true };
      }
      return null;
    } catch {
      return { invalidUrl: true };
    }
  };
}

export function notFutureDateValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const raw = control.value;
    if (typeof raw !== 'string' || raw.trim().length === 0) {
      return null;
    }
    const today = todayIsoDate();
    return raw.trim() > today ? { futureDate: true } : null;
  };
}

export function dateRangeValidator(
  startControlName: string,
  endControlName: string,
  currentControlName?: string
): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const startControl = group.get(startControlName);
    const endControl = group.get(endControlName);
    const currentControl = currentControlName ? group.get(currentControlName) : null;

    if (!startControl || !endControl) {
      return null;
    }

    if (currentControl?.value === true) {
      return null;
    }

    const startValue = typeof startControl.value === 'string' ? startControl.value.trim() : '';
    const endValue = typeof endControl.value === 'string' ? endControl.value.trim() : '';

    if (!startValue || !endValue) {
      return null;
    }

    return endValue < startValue ? { dateRangeInvalid: true } : null;
  };
}

export function todayIsoDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatMonthYear(isoDate: string | null | undefined): string {
  if (!isoDate) {
    return '';
  }
  const parts = isoDate.split('-');
  if (parts.length < 2) {
    return isoDate;
  }
  const year = Number(parts[0]);
  const monthIndex = Number(parts[1]) - 1;
  if (!Number.isFinite(year) || !Number.isFinite(monthIndex) || monthIndex < 0 || monthIndex > 11) {
    return isoDate;
  }
  const date = new Date(Date.UTC(year, monthIndex, 1));
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC'
  }).format(date);
}

