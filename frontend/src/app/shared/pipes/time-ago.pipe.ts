import { Pipe, PipeTransform } from '@angular/core';

export function formatTimeAgo(
  value: string | Date | number | null | undefined,
  referenceNowMs: number = Date.now()
): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }

  const timestampMs =
    value instanceof Date
      ? value.getTime()
      : typeof value === 'number'
        ? value
        : Date.parse(value);

  if (!Number.isFinite(timestampMs)) {
    return '—';
  }

  const diffSeconds = Math.floor((referenceNowMs - timestampMs) / 1000);
  if (diffSeconds < 60) {
    return 'Just now';
  }

  const diffMinutes = Math.floor(diffSeconds / 60);
  if (diffMinutes < 60) {
    return `${diffMinutes}m ago`;
  }

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) {
    return `${diffHours}h ago`;
  }

  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) {
    return `${diffDays}d ago`;
  }

  if (diffDays < 30) {
    const weeks = Math.floor(diffDays / 7);
    return `${weeks}w ago`;
  }

  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: new Date(timestampMs).getFullYear() === new Date(referenceNowMs).getFullYear()
      ? undefined
      : 'numeric'
  }).format(new Date(timestampMs));
}

@Pipe({
  name: 'timeAgo',
  standalone: true,
  pure: true
})
export class TimeAgoPipe implements PipeTransform {
  transform(
    value: string | Date | number | null | undefined,
    referenceNowMs?: number
  ): string {
    return formatTimeAgo(value, referenceNowMs);
  }
}

