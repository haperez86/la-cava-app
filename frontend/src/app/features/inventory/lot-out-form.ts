import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, output, signal } from '@angular/core';
import {
  FieldTree,
  FormField,
  ValidationError,
  form,
  maxLength,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { problemMessage, problemOf } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { OUTPUT_TYPES, OutputRequest, OutputResult, OutputType, StockProductOption } from './inventory.model';
import { InventoryService } from './inventory.service';
import { PRODUCT_REQUIRED, QUANTITY_INTEGER, QUANTITY_POSITIVE } from './lot-in-form';

export const TYPE_REQUIRED = 'Elige el tipo de salida.';
export const NOTE_REQUIRED = 'Escribe una observación.';
/** Longitud máxima de la observación de una salida (stock_movement.note varchar(140)). */
export const NOTE_MAX_LENGTH = 140;

/** Mismo texto que el 400 del backend cuando la cantidad supera la vigente (D33). */
export function quantityOverAvailable(available: number): string {
  return `Solo hay ${available} disponibles.`;
}

interface LotOutModel {
  /** Id del producto como texto (valor del select); vacío = sin elegir. */
  productId: string;
  type: OutputType | '';
  quantity: number;
  note: string;
}

/**
 * Formulario "Registrar salida" (LCV-HU-05 CA2): producto, tipo (merma, dañado, consumo interno, ajuste),
 * cantidad entera > 0 y ≤ vigente del producto, y observación obligatoria. Emite `saved` con el reparto FEFO.
 */
@Component({
  selector: 'app-lot-out-form',
  imports: [FormField],
  templateUrl: './lot-out-form.html',
  styleUrl: './lot-out-form.scss',
})
export class LotOutForm {
  readonly #inventory = inject(InventoryService);
  readonly #toast = inject(ToastService);

  readonly saved = output<OutputResult>();
  readonly cancelled = output<void>();

  protected readonly types = OUTPUT_TYPES;
  protected readonly products = signal<readonly StockProductOption[]>([]);
  protected readonly loadingProducts = signal(true);
  protected readonly submitting = signal(false);

  protected readonly model = signal<LotOutModel>({ productId: '', type: 'WASTE_OUT', quantity: 0, note: '' });

  /** Producto elegido (o undefined) para mostrar y validar su vigente. */
  protected readonly selected = computed(() => this.#productById(this.model().productId));

  protected readonly outForm = form(this.model, (p) => {
    required(p.productId, { message: PRODUCT_REQUIRED });
    required(p.type, { message: TYPE_REQUIRED });
    // Un input numérico vacío deja NaN en el modelo: cuenta como "sin cantidad".
    validate(p.quantity, ({ value, valueOf }) => {
      const quantity = value();
      if (!Number.isFinite(quantity) || quantity <= 0) {
        return { kind: 'positive', message: QUANTITY_POSITIVE };
      }
      if (!Number.isInteger(quantity)) {
        return { kind: 'integer', message: QUANTITY_INTEGER };
      }
      const product = this.#productById(valueOf(p.productId));
      return product && quantity > product.available
        ? { kind: 'overAvailable', message: quantityOverAvailable(product.available) }
        : undefined;
    });
    validate(p.note, ({ value }) => (value().trim() ? undefined : { kind: 'required', message: NOTE_REQUIRED }));
    maxLength(p.note, NOTE_MAX_LENGTH, { message: `Máximo ${NOTE_MAX_LENGTH} caracteres.` });
  });

  constructor() {
    void this.#loadProducts();
  }

  /** Primer error visible de un campo (tras tocarlo o al intentar guardar). */
  protected errorOf(field: FieldTree<unknown>): string {
    const state = field();
    return state.touched() && state.errors().length ? (state.errors()[0].message ?? '') : '';
  }

  protected async onSubmit(): Promise<void> {
    await submit(this.outForm, async () => {
      this.submitting.set(true);
      try {
        this.saved.emit(await this.#inventory.registerOutput(this.#body()));
        return undefined;
      } catch (e) {
        return this.#serverErrors(e);
      } finally {
        this.submitting.set(false);
      }
    });
  }

  #productById(productId: string): StockProductOption | undefined {
    return productId ? this.products().find((p) => String(p.id) === productId) : undefined;
  }

  async #loadProducts(): Promise<void> {
    try {
      this.products.set(await this.#inventory.productOptions());
    } catch (e) {
      this.#toast.error(problemMessage(e));
    } finally {
      this.loadingProducts.set(false);
    }
  }

  #body(): OutputRequest {
    const m = this.model();
    return {
      productId: Number(m.productId),
      quantity: m.quantity,
      type: m.type as OutputType,
      note: m.note.trim(),
    };
  }

  /** 400 → errores por campo; lo demás (404 incluido) → toast. */
  #serverErrors(error: unknown): ValidationError.WithOptionalFieldTree[] | undefined {
    const problem = problemOf(error);
    const status = error instanceof HttpErrorResponse ? error.status : 0;
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

  #fieldFor(apiField: string): FieldTree<unknown> | null {
    const f = this.outForm;
    const fields: Record<string, FieldTree<unknown>> = {
      productId: f.productId,
      quantity: f.quantity,
      type: f.type,
      note: f.note,
    };
    return fields[apiField] ?? null;
  }
}
