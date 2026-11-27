/** Mesa de GET /api/v1/tables (HU-07; los campos de estado llegan con HU-09, D35). */
export interface DiningTable {
  readonly id: number;
  readonly number: number;
  readonly active: boolean;
}

/** Fila de GET /api/v1/tables/qr-sheet: solo mesas activas; `code` = `LACAVA:MESA:<n>:<token>` (D12). */
export interface QrSheetEntry {
  readonly number: number;
  readonly code: string;
  readonly pin: string;
}
