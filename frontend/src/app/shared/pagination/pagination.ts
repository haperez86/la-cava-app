import { Component, computed, input, output } from '@angular/core';

/** Tamaño de página por defecto de los listados paginados . */
export const DEFAULT_PAGE_SIZE = 10;

/** Número de página (0-based) o separador de la ventana de páginas. */
export type PageItem = number | 'ellipsis';

/**
 * Ventana de páginas visible: todas si hay pocas; si no, primera, última y las vecinas de la actual, con
 * separadores. Mantiene el ancho del paginador estable aunque el listado tenga cientos de páginas.
 */
export function pageWindow(current: number, totalPages: number): PageItem[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index);
  }
  const visible = new Set<number>([0, totalPages - 1, current - 1, current, current + 1]);
  if (current <= 2) {
    [1, 2, 3].forEach((page) => visible.add(page));
  }
  if (current >= totalPages - 3) {
    [totalPages - 2, totalPages - 3, totalPages - 4].forEach((page) => visible.add(page));
  }
  const pages = [...visible].filter((page) => page >= 0 && page < totalPages).sort((a, b) => a - b);
  const items: PageItem[] = [];
  pages.forEach((page, index) => {
    if (index > 0 && page - pages[index - 1] > 1) {
      items.push('ellipsis');
    }
    items.push(page);
  });
  return items;
}

/**
 * Paginador compartido : «Mostrando 1–10 de N», anterior/siguiente y números de página.
 * La página es 0-based, como la API; en móvil solo muestra anterior/siguiente y «Página X de Y».
 */
@Component({
  selector: 'app-pagination',
  templateUrl: './pagination.html',
  styleUrl: './pagination.scss',
})
export class Pagination {
  /** Página actual, 0-based. */
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly totalElements = input.required<number>();
  /** Página solicitada, 0-based. */
  readonly pageChange = output<number>();

  protected readonly totalPages = computed(() => {
    const size = Math.max(1, this.size());
    return Math.max(1, Math.ceil(this.totalElements() / size));
  });
  /** Página efectiva: acota una página fuera de rango (p. ej. tras borrar el último elemento). */
  protected readonly current = computed(() => Math.min(Math.max(0, this.page()), this.totalPages() - 1));
  protected readonly atStart = computed(() => this.current() === 0);
  protected readonly atEnd = computed(() => this.current() >= this.totalPages() - 1);
  protected readonly firstItem = computed(() =>
    this.totalElements() === 0 ? 0 : this.current() * this.size() + 1,
  );
  protected readonly lastItem = computed(() =>
    Math.min(this.totalElements(), (this.current() + 1) * this.size()),
  );
  protected readonly summary = computed(() =>
    this.totalElements() === 0
      ? 'Mostrando 0 de 0'
      : `Mostrando ${this.firstItem()}–${this.lastItem()} de ${this.totalElements()}`,
  );
  protected readonly pages = computed(() => pageWindow(this.current(), this.totalPages()));

  protected go(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.current()) {
      return;
    }
    this.pageChange.emit(page);
  }
}
