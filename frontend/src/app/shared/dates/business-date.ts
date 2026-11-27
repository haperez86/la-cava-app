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
