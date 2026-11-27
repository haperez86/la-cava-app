import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, output, signal } from '@angular/core';
import { FieldTree, FormField, ValidationError, form, submit, validate } from '@angular/forms/signals';
import { problemMessage, problemOf } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { TablesService } from './tables.service';

export const TABLE_NUMBER_INVALID = 'Ingresa un número de mesa entero mayor que cero.';
export const DUPLICATE_TABLE_DETAIL = 'Ya existe una mesa con ese número.';

/** Alta de mesa (LCV-HU-07 CA1): número entero > 0; el backend genera el token HMAC y el PIN. */
@Component({
  selector: 'app-table-form',
  imports: [FormField],
  templateUrl: './table-form.html',
  styleUrl: './table-form.scss',
})
export class TableForm {
  readonly #tables = inject(TablesService);
  readonly #toast = inject(ToastService);

  /** Emite el número de la mesa creada. */
  readonly saved = output<number>();
  readonly cancelled = output<void>();

  protected readonly submitting = signal(false);
  protected readonly model = signal({ number: 0 });
  protected readonly tableForm = form(this.model, (p) => {
    // Un input numérico vacío deja NaN: cuenta como inválido, igual que 0, negativos o decimales.
    validate(p.number, ({ value }) =>
      Number.isInteger(value()) && value() > 0 ? undefined : { kind: 'tableNumber', message: TABLE_NUMBER_INVALID },
    );
  });

  /** Primer error visible del campo (tras tocarlo o al intentar guardar). */
  protected errorOf(field: FieldTree<unknown>): string {
    const state = field();
    return state.touched() && state.errors().length ? (state.errors()[0].message ?? '') : '';
  }

  protected async onSubmit(): Promise<void> {
    await submit(this.tableForm, async () => {
      const number = this.model().number;
      this.submitting.set(true);
      try {
        await this.#tables.create(number);
        this.saved.emit(number);
        return undefined;
      } catch (e) {
        return this.#serverErrors(e);
      } finally {
        this.submitting.set(false);
      }
    });
  }

  /** 409 y 400 en `number` → error en el campo; lo demás → toast. */
  #serverErrors(error: unknown): ValidationError.WithOptionalFieldTree[] | undefined {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 409) {
      return [{ kind: 'server', message: problemMessage(error, DUPLICATE_TABLE_DETAIL), fieldTree: this.tableForm.number }];
    }
    const fieldErrors = problemOf(error)?.errors ?? [];
    if (status === 400 && fieldErrors.length) {
      const mapped: ValidationError.WithOptionalFieldTree[] = [];
      for (const { field, message } of fieldErrors) {
        if (field === 'number') {
          mapped.push({ kind: 'server', message, fieldTree: this.tableForm.number });
        } else {
          this.#toast.error(message);
        }
      }
      return mapped;
    }
    this.#toast.error(problemMessage(error));
    return undefined;
  }
}
