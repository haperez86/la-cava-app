import { CdkTrapFocus } from '@angular/cdk/a11y';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import {
  FieldTree,
  FormField,
  ValidationError,
  applyEach,
  form,
  maxLength,
  required,
  submit,
} from '@angular/forms/signals';
import { ProblemDetail, problemMessage, problemOf } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { CopCurrencyPipe } from '../../shared/pipes/cop-currency.pipe';
import { BlockReason, Category, Product } from '../menu/product.model';
import { ProductsService } from '../menu/products.service';
import { DiningTable } from '../tables/tables.model';
import { TablesService } from '../tables/tables.service';
import { CreateOrderItem, CreateOrderRequest, Order, Waiter } from './order.model';
import { OrdersService } from './orders.service';

/** Observación por ítem (especificación §3.1). */
export const ITEM_NOTE_MAX_LENGTH = 80;
/** Observación general del pedido (especificación §3.1). */
export const ORDER_NOTE_MAX_LENGTH = 140;
export const TABLE_REQUIRED = 'Elige la mesa.';
export const WAITER_REQUIRED = 'Elige el mesero que atiende.';
/** Mismo texto del 403 del backend cuando el comprobante de la mesa no sirve (D37). */
export const RESCAN_TABLE = 'Escanea de nuevo el QR de la mesa.';
export const NO_PRODUCTS_MATCH = 'No hay productos que coincidan con el filtro.';
/**
 * Productos que pide la lista del pedido: una sola página de 100, ordenada por nombre.
 * Pendiente documentado: si la carta supera 100 productos, los demás no aparecen; habrá que paginar o subir el tope.
 */
export const ORDER_PRODUCTS_PAGE_SIZE = 100;

export function orderSent(id: number): string {
  return `Pedido #${id} enviado a preparación.`;
}

export function onlyAvailable(available: number): string {
  return `Solo hay ${available} disponible(s).`;
}

export function notOrderable(reason: string): string {
  return `No disponible: ${reason}.`;
}

/** Quién crea el pedido: el mesero (mesa del QR y comprobante, D37) o el personal de caja/administración. */
export type OrderDialogMode = 'waiter' | 'staff';

export function unitsLabel(units: number): string {
  if (!units) {
    return 'Sin productos';
  }
  return units === 1 ? '1 unidad' : `${units} unidades`;
}

/** Mesa fija del pedido (la del QR del mesero). */
export interface OrderTable {
  readonly id: number;
  readonly number: number;
}

interface OrderLine {
  productId: number;
  quantity: number;
  note: string;
}

interface OrderModel {
  /** Id de la mesa como texto (valor del select); vacío = sin elegir. */
  tableId: string;
  /** Id del mesero como texto (valor del select); vacío = sin elegir. */
  waiterId: string;
  note: string;
  items: OrderLine[];
}

/** 422 del backend: el detail nombra el producto y lo disponible (D38). */
interface StockProblem extends ProblemDetail {
  readonly productId?: number;
  readonly available?: number;
  /** Motivo cuando el producto no se puede pedir (retirado, fuera de temporada). */
  readonly reason?: string;
}

/** Texto sin tildes y en minúsculas para buscar. */
function fold(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}

/**
 * Modal "Nuevo pedido" (LCV-HU-10). Búsqueda y pestañas de categoría que filtran, lista compacta con −/+ y barra
 * inferior fija con unidades, total y "Enviar a preparación" (CA1); productos no disponibles deshabilitados con su
 * motivo y sin superar la existencia vigente (CA2); observaciones por ítem y general (CA3).
 * Con `table` y `tableCode` (mesero, D37) la mesa queda fija y el mesero es quien pide; sin ellos (cajero o
 * administrador) se eligen mesa y mesero (CA4).
 */
@Component({
  selector: 'app-order-dialog',
  imports: [CdkTrapFocus, CopCurrencyPipe, FormField],
  templateUrl: './order-dialog.html',
  styleUrl: './order-dialog.scss',
  host: { '(document:keydown.escape)': 'close()' },
  // Escape, la × y el fondo no cierran mientras se envía (close() lo ignora).
})
export class OrderDialog implements OnInit {
  readonly #orders = inject(OrdersService);
  readonly #products = inject(ProductsService);
  readonly #tables = inject(TablesService);
  readonly #toast = inject(ToastService);

  /** `waiter`: el mesero es quien pide y envía `tableCode`; `staff`: se elige el mesero (CA4). */
  readonly mode = input<OrderDialogMode>('staff');
  /** Mesa fija; `null` = se elige en el formulario. */
  readonly table = input<OrderTable | null>(null);
  /** Comprobante `tablePass` de la mesa (modo `waiter`). */
  readonly tableCode = input('');
  /** Nombre de quien atiende, para el encabezado del mesero. */
  readonly waiterName = input('');

  readonly created = output<Order>();
  readonly closed = output<void>();
  /** 403: el comprobante venció o ya no sirve; el mesero debe volver a escanear. */
  readonly passRejected = output<void>();

  protected readonly noProducts = NO_PRODUCTS_MATCH;
  protected readonly picksTable = computed(() => this.table() === null);
  protected readonly picksWaiter = computed(() => this.mode() === 'staff');
  protected readonly eyebrow = computed(() => {
    const table = this.table();
    if (!table) {
      return 'Servicio en mesa';
    }
    return this.waiterName() ? `Mesa ${table.number} · ${this.waiterName()}` : `Mesa ${table.number}`;
  });

  protected readonly products = signal<readonly Product[]>([]);
  protected readonly categories = signal<readonly Category[]>([]);
  protected readonly tables = signal<readonly DiningTable[]>([]);
  protected readonly waiters = signal<readonly Waiter[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly search = signal('');
  /** Pestaña de categoría; `null` = "Todas". */
  protected readonly categoryId = signal<number | null>(null);
  /** Ítems con la observación abierta. */
  protected readonly openNotes = signal<ReadonlySet<number>>(new Set());
  /** CA5: productos que no alcanzaron (422) con el mensaje a mostrar en su fila. */
  protected readonly conflicts = signal<ReadonlyMap<number, string>>(new Map());

  protected readonly model = signal<OrderModel>({ tableId: '', waiterId: '', note: '', items: [] });
  protected readonly orderForm = form(this.model, (p) => {
    required(p.tableId, { message: TABLE_REQUIRED, when: () => this.picksTable() });
    required(p.waiterId, { message: WAITER_REQUIRED, when: () => this.picksWaiter() });
    maxLength(p.note, ORDER_NOTE_MAX_LENGTH, { message: `Máximo ${ORDER_NOTE_MAX_LENGTH} caracteres.` });
    applyEach(p.items, (item) => {
      maxLength(item.note, ITEM_NOTE_MAX_LENGTH, { message: `Máximo ${ITEM_NOTE_MAX_LENGTH} caracteres.` });
    });
  });

  /** Cantidad pedida por producto. */
  protected readonly quantities = computed(
    () => new Map(this.model().items.map((line) => [line.productId, line.quantity] as const)),
  );
  readonly #productById = computed(() => new Map(this.products().map((p) => [p.id, p] as const)));

  /** CA1: la búsqueda (nombre, descripción o categoría) y la pestaña filtran; los no disponibles van al final. */
  protected readonly visibleProducts = computed(() => {
    const q = fold(this.search());
    const categoryId = this.categoryId();
    return this.products()
      .filter((p) => categoryId === null || p.categoryId === categoryId)
      .filter((p) => !q || fold(`${p.name} ${p.description ?? ''} ${p.categoryName}`).includes(q))
      .sort((a, b) => Number(!a.orderable) - Number(!b.orderable));
  });

  protected readonly units = computed(() => this.model().items.reduce((sum, line) => sum + line.quantity, 0));
  protected readonly total = computed(() => {
    const byId = this.#productById();
    return this.model().items.reduce((sum, line) => sum + line.quantity * Number(byId.get(line.productId)?.price ?? 0), 0);
  });
  protected readonly unitsText = computed(() => unitsLabel(this.units()));
  /** Alguna línea pide más de lo disponible o un producto que ya no se puede pedir: no se puede enviar. */
  protected readonly exceedsStock = computed(() => {
    const byId = this.#productById();
    return this.model().items.some((line) => {
      const product = byId.get(line.productId);
      return !product || !product.orderable || line.quantity > product.available;
    });
  });

  #destroyed = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => (this.#destroyed = true));
  }

  ngOnInit(): void {
    void this.#load();
  }

  protected close(): void {
    if (this.submitting()) {
      return;
    }
    this.closed.emit();
  }

  protected qtyOf(productId: number): number {
    return this.quantities().get(productId) ?? 0;
  }

  /** CA2: solo productos pedibles y sin superar la existencia vigente. */
  protected canAdd(product: Product): boolean {
    return product.orderable && this.qtyOf(product.id) < product.available;
  }

  protected increment(product: Product): void {
    if (!this.canAdd(product)) {
      return;
    }
    this.#clearConflict(product.id);
    this.model.update((m) => {
      const exists = m.items.some((line) => line.productId === product.id);
      const items = exists
        ? m.items.map((line) => (line.productId === product.id ? { ...line, quantity: line.quantity + 1 } : line))
        : [...m.items, { productId: product.id, quantity: 1, note: '' }];
      return { ...m, items };
    });
  }

  protected decrement(product: Product): void {
    const qty = this.qtyOf(product.id);
    if (qty <= 0) {
      return;
    }
    this.#clearConflict(product.id);
    if (qty === 1) {
      this.openNotes.update((set) => {
        const next = new Set(set);
        next.delete(product.id);
        return next;
      });
    }
    this.model.update((m) => ({
      ...m,
      items: m.items
        .map((line) => (line.productId === product.id ? { ...line, quantity: line.quantity - 1 } : line))
        .filter((line) => line.quantity > 0),
    }));
  }

  /** CA3: la observación del ítem se abre al tocar "Nota". */
  protected toggleNote(productId: number): void {
    this.openNotes.update((set) => {
      const next = new Set(set);
      if (!next.delete(productId)) {
        next.add(productId);
      }
      return next;
    });
  }

  /** Campo de la observación del ítem del producto, si está en el pedido. */
  protected noteField(productId: number): FieldTree<string> | undefined {
    const index = this.model().items.findIndex((line) => line.productId === productId);
    return index >= 0 ? this.orderForm.items[index].note : undefined;
  }

  protected setSearch(value: string): void {
    this.search.set(value);
  }

  protected setCategory(categoryId: number | null): void {
    this.categoryId.set(categoryId);
  }

  /** Primer error visible de un campo (tras tocarlo o al intentar enviar). */
  protected errorOf(field: FieldTree<unknown>): string {
    const state = field();
    return state.touched() && state.errors().length ? (state.errors()[0].message ?? '') : '';
  }

  protected async onSubmit(): Promise<void> {
    if (!this.units() || this.submitting() || this.exceedsStock()) {
      return;
    }
    await submit(this.orderForm, async () => {
      this.submitting.set(true);
      try {
        const order = await this.#orders.create(this.#body());
        this.#toast.success(orderSent(order.id));
        if (!this.#destroyed) {
          this.created.emit(order);
        }
        return undefined;
      } catch (e) {
        return this.#serverErrors(e);
      } finally {
        this.submitting.set(false);
      }
    });
  }

  #body(): CreateOrderRequest {
    const m = this.model();
    const table = this.table();
    const items: CreateOrderItem[] = m.items
      .filter((line) => line.quantity > 0)
      .map((line) => {
        const note = line.note.trim();
        return note
          ? { productId: line.productId, quantity: line.quantity, note }
          : { productId: line.productId, quantity: line.quantity };
      });
    const note = m.note.trim();
    return {
      tableId: table ? table.id : Number(m.tableId),
      ...(this.picksWaiter() ? { waiterId: Number(m.waiterId) } : { tableCode: this.tableCode() }),
      items,
      ...(note ? { note } : {}),
    };
  }

  /** 422 → marca el producto y conserva el pedido; 403 del comprobante → volver a escanear; 400 → errores. */
  #serverErrors(error: unknown): ValidationError.WithOptionalFieldTree[] | undefined {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    const problem = problemOf(error) as StockProblem | null;
    if (status === 422) {
      this.#toast.error(problemMessage(error));
      this.#markConflict(problem);
      return undefined;
    }
    if (status === 403 && this.mode() === 'waiter') {
      this.#toast.error(problemMessage(error, RESCAN_TABLE));
      if (!this.#destroyed) {
        this.passRejected.emit();
      }
      return undefined;
    }
    if (status === 400 && problem?.errors?.length) {
      const mapped: ValidationError.WithOptionalFieldTree[] = [];
      for (const { field, message } of problem.errors) {
        const target = this.#fieldFor(field);
        if (target) {
          mapped.push({ kind: 'server', message, fieldTree: target });
        } else {
          this.#toast.error(message);
        }
      }
      return mapped;
    }
    this.#toast.error(problemMessage(error));
    return undefined;
  }

  /**
   * El producto queda marcado con su mensaje; si no se puede pedir (`reason`) queda deshabilitado con ese motivo, y
   * su cantidad en el pedido baja a lo disponible (o sale si no queda nada), para no reenviar el mismo pedido.
   * El resto del pedido sigue igual.
   */
  #markConflict(problem: StockProblem | null): void {
    const productId = problem?.productId;
    if (typeof productId !== 'number') {
      return;
    }
    const available = typeof problem?.available === 'number' ? problem.available : 0;
    const reason = typeof problem?.reason === 'string' && problem.reason.trim() ? problem.reason.trim() : '';
    this.products.update((list) =>
      list.map((p) => {
        if (p.id !== productId) {
          return p;
        }
        if (reason) {
          return { ...p, available, orderable: false, blockReason: reason as BlockReason };
        }
        return {
          ...p,
          available,
          orderable: p.orderable && available > 0,
          blockReason: available > 0 ? p.blockReason : (p.blockReason ?? 'Agotado'),
        };
      }),
    );
    const limit = reason ? 0 : available;
    this.model.update((m) => ({
      ...m,
      items: m.items
        .map((line) => (line.productId === productId ? { ...line, quantity: Math.min(line.quantity, limit) } : line))
        .filter((line) => line.quantity > 0),
    }));
    if (limit === 0) {
      this.openNotes.update((set) => {
        const next = new Set(set);
        next.delete(productId);
        return next;
      });
    }
    this.conflicts.update((map) => new Map(map).set(productId, reason ? notOrderable(reason) : onlyAvailable(available)));
  }

  #clearConflict(productId: number): void {
    if (this.conflicts().has(productId)) {
      this.conflicts.update((map) => {
        const next = new Map(map);
        next.delete(productId);
        return next;
      });
    }
  }

  #fieldFor(apiField: string): FieldTree<unknown> | null {
    const f = this.orderForm;
    const fields: Record<string, FieldTree<unknown>> = {
      tableId: f.tableId,
      waiterId: f.waiterId,
      note: f.note,
    };
    const item = /^items\[(\d+)]\.note$/.exec(apiField);
    if (item) {
      const index = Number(item[1]);
      return index < this.model().items.length ? f.items[index].note : null;
    }
    return fields[apiField] ?? null;
  }

  async #load(): Promise<void> {
    try {
      const [page, categories, tables, waiters] = await Promise.all([
        this.#products.list({ q: '', categoryId: null, page: 0, size: ORDER_PRODUCTS_PAGE_SIZE }),
        this.#products.categories(),
        this.picksTable() ? this.#tables.list() : Promise.resolve([]),
        this.picksWaiter() ? this.#orders.waiters() : Promise.resolve([]),
      ]);
      this.products.set(page.content);
      this.categories.set(categories);
      this.tables.set(tables.filter((t) => t.active).sort((a, b) => a.number - b.number));
      this.waiters.set(waiters);
    } catch (e) {
      this.#toast.error(problemMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
}
