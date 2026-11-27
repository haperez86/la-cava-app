import { businessToday, formatIsoDate } from './business-date';

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
