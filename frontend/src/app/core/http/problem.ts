import { HttpErrorResponse } from '@angular/common/http';

/** Cuerpo ProblemDetail (RFC 9457) que devuelve el backend (docs/03-api.md). */
export interface ProblemDetail {
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly errors?: readonly { field: string; message: string }[];
}

export const NETWORK_ERROR = 'No se pudo conectar con el servidor. Revisa tu conexión.';
export const UNEXPECTED_ERROR = 'Ocurrió un error inesperado. Intenta de nuevo.';

export function problemOf(error: unknown): ProblemDetail | null {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    return error.error as ProblemDetail;
  }
  return null;
}

/** Mensaje en español para mostrar al usuario a partir de un error HTTP. */
export function problemMessage(error: unknown, fallback = UNEXPECTED_ERROR): string {
  if (error instanceof HttpErrorResponse && error.status === 0) {
    return NETWORK_ERROR;
  }
  const detail = problemOf(error)?.detail;
  return typeof detail === 'string' && detail.trim() ? detail : fallback;
}
