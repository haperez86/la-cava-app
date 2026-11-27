/** Zona horaria del negocio (CLAUDE.md: America/Bogota). */
export const BUSINESS_TIME_ZONE = 'America/Bogota';

const ISO_DAY = new Intl.DateTimeFormat('en-CA', {
  timeZone: BUSINESS_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** Fecha de "hoy" en la zona del negocio como 'YYYY-MM-DD'. */
export function businessToday(now: Date = new Date()): string {
  return ISO_DAY.format(now);
}

/** 'YYYY-MM-DD' → 'DD/MM/YYYY' (sin conversión de zona: es una fecha de calendario). */
export function formatIsoDate(value: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '');
  return match ? `${match[3]}/${match[2]}/${match[1]}` : '';
}

const DATE_TIME_PARTS = new Intl.DateTimeFormat('en-CA', {
  timeZone: BUSINESS_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** Instante ISO-8601 → 'DD/MM/YYYY HH:mm' en hora de Bogotá; '' si no es válido. */
export function formatBusinessDateTime(value: string | null | undefined): string {
  const date = new Date(value ?? '');
  if (!value || Number.isNaN(date.getTime())) {
    return '';
  }
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    DATE_TIME_PARTS.formatToParts(date).find((p) => p.type === type)?.value ?? '';
  return `${part('day')}/${part('month')}/${part('year')} ${part('hour')}:${part('minute')}`;
}

/** Instante ISO-8601 → 'DD/MM/YYYY' del día de calendario en Bogotá; '' si no es válido. */
export function formatBusinessDate(value: string | null | undefined): string {
  const date = new Date(value ?? '');
  return value && !Number.isNaN(date.getTime()) ? formatIsoDate(businessToday(date)) : '';
}
