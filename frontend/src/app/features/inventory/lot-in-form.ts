import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, output, signal } from '@angular/core';
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
import { businessToday } from '../../shared/dates/business-date';
import { Lot, LotInRequest, ProductOption } from './inventory.model';
import { InventoryService } from './inventory.service';

export const PRODUCT_REQUIRED = 'Elige un producto.';
export const QUANTITY_POSITIVE = 'Ingresa una cantidad mayor que cero.';
export const QUANTITY_INTEGER = 'La cantidad debe ser un número entero.';
export const EXPIRY_REQUIRED = 'Indica la fecha de vencimiento del lote.';
export const EXPIRY_PAST = 'El vencimiento no puede ser anterior a hoy.';
/** Longitud máxima de la temporada (docs/02-modelo-datos.md). */
export const SEASON_MAX_LENGTH = 40;

interface LotInModel {
  /** Id del producto como texto (valor del select); vacío = sin elegir. */
  productId: string;
  quantity: number;
  expiresOn: string;
  seasonName: string;
}

/**
 * Formulario "Registrar entrada" (LCV-HU-04 CA1): producto, cantidad entera > 0, vencimiento >= hoy
 * (America/Bogota) y temporada opcional precargada con la del producto. Emite `saved` con el lote creado.
 */
@Component({
  selector: 'app-lot-in-form',
  imports: [FormField],
  templateUrl: './lot-in-form.html',
  styleUrl: './lot-in-form.scss',
})
export class LotInForm {
  readonly #inventory = inject(InventoryService);
  readonly #toast = inject(ToastService);

  readonly saved = output<Lot>();
  readonly cancelled = output<void>();

  protected readonly products = signal<readonly ProductOption[]>([]);
  protected readonly loadingProducts = signal(true);
  protected readonly submitting = signal(false);

  protected readonly model = signal<LotInModel>({ productId: '', quantity: 0, expiresOn: '', seasonName: '' });
  protected readonly lotForm = form(this.model, (p) => {
    required(p.productId, { message: PRODUCT_REQUIRED });
    // Un input numérico vacío deja NaN en el modelo: cuenta como "sin cantidad".
    validate(p.quantity, ({ value }) => {
      const quantity = value();
      if (!Number.isFinite(quantity) || quantity <= 0) {
        return { kind: 'positive', message: QUANTITY_POSITIVE };
      }
      return Number.isInteger(quantity) ? undefined : { kind: 'integer', message: QUANTITY_INTEGER };
    });
    required(p.expiresOn, { message: EXPIRY_REQUIRED });
    validate(p.expiresOn, ({ value }) =>
      value() !== '' && value() < businessToday() ? { kind: 'past', message: EXPIRY_PAST } : undefined,
    );
    maxLength(p.seasonName, SEASON_MAX_LENGTH, { message: `Máximo ${SEASON_MAX_LENGTH} caracteres.` });
  });

  constructor() {
    void this.#loadProducts();
  }

  /** Al elegir producto se precarga su temporada (editable). */
  protected onProductChange(productId: string): void {
    const product = this.products().find((p) => String(p.id) === productId);
    this.model.update((m) => ({ ...m, productId, seasonName: product?.seasonName ?? '' }));
  }

  /** Primer error visible de un campo (tras tocarlo o al intentar guardar). */
  protected errorOf(field: FieldTree<unknown>): string {
    const state = field();
    return state.touched() && state.errors().length ? (state.errors()[0].message ?? '') : '';
  }

  protected async onSubmit(): Promise<void> {
    await submit(this.lotForm, async () => {
      this.submitting.set(true);
      try {
        this.saved.emit(await this.#inventory.registerLot(this.#body()));
        return undefined;
      } catch (e) {
        return this.#serverErrors(e);
      } finally {
        this.submitting.set(false);
      }
    });
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

  #body(): LotInRequest {
    const m = this.model();
    return {
      productId: Number(m.productId),
      quantity: m.quantity,
      expiresOn: m.expiresOn,
      seasonName: m.seasonName.trim() || null,
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
    const f = this.lotForm;
    const fields: Record<string, FieldTree<unknown>> = {
      productId: f.productId,
      quantity: f.quantity,
      expiresOn: f.expiresOn,
      seasonName: f.seasonName,
    };
    return fields[apiField] ?? null;
  }
}
