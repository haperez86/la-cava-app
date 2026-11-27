import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Component, inject, signal } from '@angular/core';
import { problemMessage } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { formatIsoDate } from '../../shared/dates/business-date';
import { Pagination } from '../../shared/pagination/pagination';
import { CopCurrencyPipe } from '../../shared/pipes/cop-currency.pipe';
import { Category, Product } from './product.model';
import { ProductForm } from './product-form';
import { ProductsService } from './products.service';

export const MENU_PAGE_SIZE = 12;
const DEFAULT_ICON = '🍽';

/** Editor abierto: alta (`product` null) o edición. */
interface EditorState {
  readonly product: Product | null;
}

/** "Menú y productos" (LCV-HU-02): listado con búsqueda, categoría y paginación; alta, edición y retiro. */
@Component({
  selector: 'app-menu-page',
  imports: [CopCurrencyPipe, Pagination, ProductForm, CdkTrapFocus],
  templateUrl: './menu-page.html',
  styleUrl: './menu-page.scss',
  host: { '(document:keydown.escape)': 'closeEditor()' },
})
export class MenuPage {
  readonly #products = inject(ProductsService);
  readonly #toast = inject(ToastService);

  protected readonly pageSize = MENU_PAGE_SIZE;
  protected readonly defaultIcon = DEFAULT_ICON;
  protected readonly formatDate = formatIsoDate;

  protected readonly q = signal('');
  protected readonly categoryId = signal<number | null>(null);
  protected readonly page = signal(0);
  protected readonly items = signal<readonly Product[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(false);
  protected readonly categories = signal<readonly Category[]>([]);
  protected readonly editor = signal<EditorState | null>(null);
  protected readonly toggling = signal<number | null>(null);

  #requestId = 0;

  constructor() {
    void this.load();
    void this.loadCategories();
  }

  protected onSearch(value: string): void {
    this.q.set(value);
    this.page.set(0);
    void this.load();
  }

  protected onCategory(value: string): void {
    this.categoryId.set(value ? Number(value) : null);
    this.page.set(0);
    void this.load();
  }

  protected onPage(page: number): void {
    this.page.set(page);
    void this.load();
  }

  protected openCreate(): void {
    this.editor.set({ product: null });
  }

  protected openEdit(product: Product): void {
    this.editor.set({ product });
  }

  protected closeEditor(): void {
    this.editor.set(null);
  }

  protected onSaved(product: Product): void {
    const created = this.editor()?.product === null;
    this.editor.set(null);
    this.#toast.success(created ? `Producto «${product.name}» creado.` : `Producto «${product.name}» actualizado.`);
    void this.load();
    void this.loadCategories();
  }

  /** CA3: retirar de la carta o volver a ella. */
  protected async toggleActive(product: Product): Promise<void> {
    const active = !product.active;
    this.toggling.set(product.id);
    try {
      await this.#products.setActive(product.id, active);
      this.items.update((list) => list.map((p) => (p.id === product.id ? { ...p, active } : p)));
      this.#toast.success(
        active ? `«${product.name}» volvió a la carta.` : `«${product.name}» se retiró de la carta.`,
      );
    } catch (e) {
      this.#toast.error(problemMessage(e));
    } finally {
      this.toggling.set(null);
    }
  }

  protected async load(): Promise<void> {
    const requestId = ++this.#requestId;
    this.loading.set(true);
    try {
      const result = await this.#products.list({
        q: this.q(),
        categoryId: this.categoryId(),
        page: this.page(),
        size: MENU_PAGE_SIZE,
      });
      if (requestId === this.#requestId) {
        this.items.set(result.content);
        this.totalElements.set(result.totalElements);
      }
    } catch (e) {
      if (requestId === this.#requestId) {
        this.#toast.error(problemMessage(e));
      }
    } finally {
      if (requestId === this.#requestId) {
        this.loading.set(false);
      }
    }
  }

  protected async loadCategories(): Promise<void> {
    try {
      this.categories.set(await this.#products.categories());
    } catch {
      // Sin categorías la pantalla sigue siendo usable: el filtro queda en "Todas" y se pueden escribir.
    }
  }
}
