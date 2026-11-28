import { CdkTrapFocus } from '@angular/cdk/a11y';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, output, signal } from '@angular/core';
import { FormField, ValidationError, form, maxLength, submit, validate } from '@angular/forms/signals';
import { problemMessage, problemOf } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { Order } from './order.model';
import { OrdersService } from './orders.service';

export const CANCEL_REASON_REQUIRED = 'Escribe el motivo de la cancelación.';
/** Longitud máxima del motivo (customer_order.cancel_reason varchar(120), D39). */
export const CANCEL_REASON_MAX_LENGTH = 120;

/**
 * Modal "Cancelar pedido" (LCV-HU-11 CA2): motivo obligatorio de 1 a 120 caracteres. Emite `cancelled` con el
 * pedido actualizado; los errores del backend (400 por campo, 409 si ya no está pendiente) se muestran sin romper.
 */
@Component({
  selector: 'app-cancel-order-dialog',
  imports: [FormField, CdkTrapFocus],
  templateUrl: './cancel-order-dialog.html',
  styleUrl: './cancel-order-dialog.scss',
  host: { '(document:keydown.escape)': 'onClose()' },
})
export class CancelOrderDialog {
  readonly #orders = inject(OrdersService);
  readonly #toast = inject(ToastService);

  readonly order = input.required<Order>();
  readonly cancelled = output<Order>();
  readonly closed = output<void>();

  protected readonly submitting = signal(false);
  protected readonly maxLength = CANCEL_REASON_MAX_LENGTH;
  protected readonly model = signal({ reason: '' });

  protected readonly cancelForm = form(this.model, (p) => {
    validate(p.reason, ({ value }) =>
      value().trim() ? undefined : { kind: 'required', message: CANCEL_REASON_REQUIRED },
    );
    maxLength(p.reason, CANCEL_REASON_MAX_LENGTH, { message: `Máximo ${CANCEL_REASON_MAX_LENGTH} caracteres.` });
  });

  protected errorOf(): string {
    const state = this.cancelForm.reason();
    return state.touched() && state.errors().length ? (state.errors()[0].message ?? '') : '';
  }

  protected onClose(): void {
    if (!this.submitting()) {
      this.closed.emit();
    }
  }

  protected async onSubmit(): Promise<void> {
    await submit(this.cancelForm, async () => {
      this.submitting.set(true);
      try {
        this.cancelled.emit(await this.#orders.cancel(this.order().id, this.model().reason.trim()));
        return undefined;
      } catch (e) {
        return this.#serverErrors(e);
      } finally {
        this.submitting.set(false);
      }
    });
  }

  /** 400 en `reason` → error del campo; lo demás (409, 403, red) → toast. */
  #serverErrors(error: unknown): ValidationError.WithOptionalFieldTree[] | undefined {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    const fieldError = status === 400 ? problemOf(error)?.errors?.find((e) => e.field === 'reason') : undefined;
    if (fieldError) {
      return [{ kind: 'server', message: fieldError.message, fieldTree: this.cancelForm.reason }];
    }
    this.#toast.error(problemMessage(error));
    // Ya no está pendiente (409) u otro fallo: el padre refresca la lista al cerrar.
    this.closed.emit();
    return undefined;
  }
}
