import { businessToday, formatBusinessDate, formatBusinessDateTime, formatIsoDate } from './business-date';

describe('businessToday', () => {
  it('uses the America/Bogota calendar day, not UTC', () => {
    // 2026-03-01 03:00 UTC is still 2026-02-28 22:00 in Bogotá (UTC-5).
    expect(businessToday(new Date('2026-03-01T03:00:00Z'))).toBe('2026-02-28');
    expect(businessToday(new Date('2026-03-01T06:00:00Z'))).toBe('2026-03-01');
  });
});

describe('formatIsoDate', () => {
  it('formats calendar dates as DD/MM/YYYY and ignores invalid input', () => {
    expect(formatIsoDate('2026-12-31')).toBe('31/12/2026');
    expect(formatIsoDate(null)).toBe('');
    expect(formatIsoDate('31/12/2026')).toBe('');
  });
});

describe('formatBusinessDateTime / formatBusinessDate', () => {
  it('shows instants as Bogotá date and 24 h time, and ignores invalid input', () => {
    // 2026-03-01 03:05 UTC = 2026-02-28 22:05 en Bogotá.
    expect(formatBusinessDateTime('2026-03-01T03:05:00Z')).toBe('28/02/2026 22:05');
    expect(formatBusinessDate('2026-03-01T03:05:00Z')).toBe('28/02/2026');
    expect(formatBusinessDateTime(null)).toBe('');
    expect(formatBusinessDate('no-date')).toBe('');
  });
});
