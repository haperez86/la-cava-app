import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Component, input, output } from '@angular/core';

/**
 * Confirmación propia de la app (nunca `window.confirm`): hoja inferior en móvil y modal centrado desde tablet.
 * El padre decide cuándo mostrarlo y qué hacer con `confirmed` / `cancelled`.
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [CdkTrapFocus],
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.scss',
  host: { '(document:keydown.escape)': 'onCancel()' },
})
export class ConfirmDialog {
  readonly heading = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input('Aceptar');
  readonly cancelLabel = input('Cancelar');
  /** Deshabilita los botones mientras el padre procesa la confirmación. */
  readonly busy = input(false);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  protected onCancel(): void {
    if (!this.busy()) {
      this.cancelled.emit();
    }
  }
}
