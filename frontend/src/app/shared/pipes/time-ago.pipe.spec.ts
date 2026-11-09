import { TimeAgoPipe } from './time-ago.pipe';

describe('TimeAgoPipe', () => {
  const pipe = new TimeAgoPipe();
  const baseNow = Date.UTC(2026, 10, 9, 12, 0, 0);

  it('should handle null, empty, and invalid timestamps cleanly', () => {
    expect(pipe.transform(null, baseNow)).toBe('—');
    expect(pipe.transform('', baseNow)).toBe('—');
    expect(pipe.transform('not-a-date', baseNow)).toBe('—');
  });

  it('should format recent seconds, minutes, hours, days, weeks, and older dates accurately', () => {
    expect(pipe.transform(baseNow - 25 * 1000, baseNow)).toBe('Just now');
    expect(pipe.transform(baseNow - 12 * 60 * 1000, baseNow)).toBe('12m ago');
    expect(pipe.transform(baseNow - 5 * 60 * 60 * 1000, baseNow)).toBe('5h ago');
    expect(pipe.transform(baseNow - 3 * 24 * 60 * 60 * 1000, baseNow)).toBe('3d ago');
    expect(pipe.transform(baseNow - 14 * 24 * 60 * 60 * 1000, baseNow)).toBe('2w ago');

    const older = new Date(Date.UTC(2025, 4, 15)).toISOString();
    expect(pipe.transform(older, baseNow)).toContain('2025');
  });
});

