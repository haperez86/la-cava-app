import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import {
  FieldTree,
  FormField,
  ValidationError,
  form,
  maxLength,
  min,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { problemMessage, problemOf } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { businessToday } from '../../shared/dates/business-date';
import { Category, CreateProductRequest, Product, ProductRequest } from './product.model';
import { ProductsService } from './products.service';

export const SEASON_UNTIL_REQUIRED = 'Indica hasta qué fecha está disponible la temporada.';
export const LOT_EXPIRY_REQUIRED = 'Indica la fecha de vencimiento del lote.';
export const LOT_EXPIRY_PAST = 'El vencimiento no puede ser anterior a hoy.';

interface ProductFormModel {
  name: string;
  categoryName: string;
  price: number;
  description: string;
  icon: string;
  minStock: number;
  seasonName: string;
  seasonUntil: string;
  lotQuantity: number;
  lotExpiresOn: string;
}

const EMPTY_MODEL: ProductFormModel = {
  name: '',
  categoryName: '',
  price: 0,
  description: '',
  icon: '',
  minStock: 5,
  seasonName: '',
  seasonUntil: '',
  lotQuantity: 0,
  lotExpiresOn: '',
};

const hasText = (value: string) => value.trim() !== '';
/** Un input numérico vacío deja NaN en el modelo: se trata como "sin valor". */
const hasQuantity = (value: number) => Number.isFinite(value) && value > 0;

/** Longitudes máximas de docs/02-modelo-datos.md (product y category). */
export const MAX_LENGTHS = { name: 60, categoryName: 40, description: 120, icon: 16, seasonName: 40 } as const;
export const PRICE_REQUIRED = 'Ingresa un precio mayor que cero.';
const maxLengthMessage = (max: number) => `Máximo ${max} caracteres.`;

/**
 * Formulario de alta y edición de productos (LCV-HU-02). Con `product` edita; sin él crea y además
 * ofrece el lote inicial (CA4). Emite `saved` con el producto que devolvió la API.
 */
@Component({
  selector: 'app-product-form',
  imports: [FormField],
  templateUrl: './product-form.html',
  styleUrl: './product-form.scss',
})
export class ProductForm implements OnInit {
  readonly #products = inject(ProductsService);
  readonly #toast = inject(ToastService);

  /** Producto a editar; null para crear uno nuevo. */
  readonly product = input<Product | null>(null);
  readonly categories = input<readonly Category[]>([]);
  readonly saved = output<Product>();
  readonly cancelled = output<void>();

  protected readonly editing = computed(() => this.product() !== null);
  protected readonly submitting = signal(false);

  protected readonly model = signal<ProductFormModel>({ ...EMPTY_MODEL });
  protected readonly productForm = form(this.model, (p) => {
    required(p.name, { message: 'Ingresa el nombre del producto.' });
    maxLength(p.name, MAX_LENGTHS.name, { message: maxLengthMessage(MAX_LENGTHS.name) });
    required(p.categoryName, { message: 'Indica la categoría.' });
    maxLength(p.categoryName, MAX_LENGTHS.categoryName, { message: maxLengthMessage(MAX_LENGTHS.categoryName) });
    maxLength(p.description, MAX_LENGTHS.description, { message: maxLengthMessage(MAX_LENGTHS.description) });
    maxLength(p.icon, MAX_LENGTHS.icon, { message: maxLengthMessage(MAX_LENGTHS.icon) });
    maxLength(p.seasonName, MAX_LENGTHS.seasonName, { message: maxLengthMessage(MAX_LENGTHS.seasonName) });
    // Precio > 0 (especificación §2); 0 inicial o vacío (NaN) cuentan como faltante.
    validate(p.price, ({ value }) => (hasQuantity(value()) ? null : { kind: 'price', message: PRICE_REQUIRED }));
    validate(p.minStock, ({ value }) =>
      Number.isFinite(value()) ? null : { kind: 'required', message: 'Ingresa el stock mínimo.' },
    );
    min(p.minStock, 0, { message: 'El stock mínimo no puede ser negativo.' });

    // CA2: con nombre de temporada, "disponible hasta" es obligatoria.
    required(p.seasonUntil, {
      message: SEASON_UNTIL_REQUIRED,
      when: ({ valueOf }) => hasText(valueOf(p.seasonName)),
    });

    // CA4: solo al crear; con cantidad, el vencimiento es obligatorio y ≥ hoy (America/Bogota).
    min(p.lotQuantity, 0, { message: 'La cantidad no puede ser negativa.' });
    required(p.lotExpiresOn, {
      message: LOT_EXPIRY_REQUIRED,
      when: ({ valueOf }) => !this.editing() && hasQuantity(valueOf(p.lotQuantity)),
    });
    validate(p.lotExpiresOn, ({ value, valueOf }) =>
      !this.editing() && hasQuantity(valueOf(p.lotQuantity)) && value() !== '' && value() < businessToday()
        ? { kind: 'past', message: LOT_EXPIRY_PAST }
        : null,
    );
  });

  ngOnInit(): void {
    const product = this.product();
    if (product) {
      this.model.set({
        ...EMPTY_MODEL,
        name: product.name,
        categoryName: product.categoryName,
        price: product.price,
        description: product.description ?? '',
        icon: product.icon ?? '',
        minStock: product.minStock,
        seasonName: product.seasonName ?? '',
        seasonUntil: product.seasonUntil ?? '',
      });
    }
  }

  /** Primer error visible de un campo (tras tocarlo o al intentar guardar). */
  protected errorOf(field: FieldTree<unknown>): string {
    const state = field();
    return state.touched() && state.errors().length ? (state.errors()[0].message ?? '') : '';
  }

  protected async onSubmit(): Promise<void> {
    await submit(this.productForm, async () => {
      this.submitting.set(true);
      try {
        const product = this.editing()
          ? await this.#products.update(this.product()!.id, this.#requestBody())
          : await this.#products.create(this.#createBody());
        this.saved.emit(product);
        return undefined;
      } catch (e) {
        return this.#serverErrors(e);
      } finally {
        this.submitting.set(false);
      }
    });
  }

  #requestBody(): ProductRequest {
    const m = this.model();
    const seasonName = m.seasonName.trim();
    return {
      name: m.name.trim(),
      categoryName: m.categoryName.trim(),
      price: m.price,
      description: m.description.trim(),
      icon: m.icon.trim(),
      minStock: m.minStock,
      seasonName: seasonName || null,
      seasonUntil: seasonName ? m.seasonUntil : null,
    };
  }

  #createBody(): CreateProductRequest {
    const m = this.model();
    const body = this.#requestBody();
    return hasQuantity(m.lotQuantity)
      ? { ...body, initialLot: { quantity: m.lotQuantity, expiresOn: m.lotExpiresOn } }
      : body;
  }

  /** 409 → error en el nombre; 400 → errores por campo; lo demás → toast. */
  #serverErrors(error: unknown): ValidationError.WithOptionalFieldTree[] | undefined {
    const problem = problemOf(error);
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 409) {
      return [{ kind: 'server', message: problemMessage(error), fieldTree: this.productForm.name }];
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

  #fieldFor(apiField: string): FieldTree<unknown> | null {
    const f = this.productForm;
    const fields: Record<string, FieldTree<unknown>> = {
      name: f.name,
      categoryName: f.categoryName,
      price: f.price,
      description: f.description,
      icon: f.icon,
      minStock: f.minStock,
      seasonName: f.seasonName,
      seasonUntil: f.seasonUntil,
      'initialLot.quantity': f.lotQuantity,
      'initialLot.expiresOn': f.lotExpiresOn,
    };
    return fields[apiField] ?? null;
  }
}
