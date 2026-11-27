/**
 * Página plana de la API: `{content, page, size, totalElements, totalPages}` (page 0-based).
 * Copiada de Proto Inventario (frontend/src/app/core/http/page.ts, commit d89f6f1).
 */
export interface Page<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}
