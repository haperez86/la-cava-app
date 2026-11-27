import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Component, inject, signal } from '@angular/core';
import { problemMessage } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { ConfirmDialog } from './confirm-dialog';
import { Lot, OutputResult, WriteOffResult } from './inventory.model';
import { InventoryService } from './inventory.service';
import { LotInForm } from './lot-in-form';
import { LotOutForm } from './lot-out-form';

export const WRITE_OFF_CONFIRM = 'Se dejarán en 0 todos los lotes vencidos. ¿Continuar?';
export const NO_EXPIRED_LOTS = 'No hay lotes vencidos.';

/** Mensaje de éxito de una entrada (LCV-HU-04 CA1). */
export function lotRegisteredMessage(lot: Lot): string {
  return `Lote registrado: ${lot.quantityReceived} × ${lot.productName}`;
}

/** Mensaje de éxito de una salida (LCV-HU-05 CA1/CA2): menciona el reparto si tocó varios lotes. */
export function outputRegisteredMessage(result: OutputResult): string {
  const lots = result.consumed.length;
  const split = lots > 1 ? ` (de ${lots} lotes)` : '';
  return `Salida registrada: ${result.quantity} × ${result.productName}${split}`;
}

/** Mensaje de la baja de vencidos (LCV-HU-05 CA3). */
export function writeOffMessage(result: WriteOffResult): string {
  return result.lots === 0
    ? NO_EXPIRED_LOTS
    : `Se dieron de baja ${result.lots} lote(s), ${result.units} unidad(es).`;
}

type OpenDialog = 'none' | 'entry' | 'output' | 'writeOff';

/**
 * "Inventario" (D32). HU-04: "Registrar entrada"; HU-05: "Registrar salida" y "Dar de baja vencidos".
 * La tabla, contadores y filtros llegan en HU-06 y se refrescan con `reload()`.
 */
@Component({
  selector: 'app-inventory-page',
  imports: [LotInForm, LotOutForm, ConfirmDialog, CdkTrapFocus],
  templateUrl: './inventory-page.html',
  styleUrl: './inventory-page.scss',
  host: { '(document:keydown.escape)': 'closeForm()' },
})
export class InventoryPage {
  readonly #inventory = inject(InventoryService);
  readonly #toast = inject(ToastService);

  protected readonly writeOffConfirm = WRITE_OFF_CONFIRM;
  protected readonly open = signal<OpenDialog>('none');
  protected readonly writingOff = signal(false);
  /**
   * Se incrementa tras cada cambio de existencias. HU-06 hará que la tabla y los contadores
   * dependan de esta señal (p. ej. como `params` de un resource) para volver a cargarse.
   */
  readonly reloadTick = signal(0);

  /** Pide a la tabla y los contadores (HU-06) que se vuelvan a cargar. */
  reload(): void {
    this.reloadTick.update((n) => n + 1);
  }

  protected openEntry(): void {
    this.open.set('entry');
  }

  protected openOutput(): void {
    this.open.set('output');
  }

  protected askWriteOff(): void {
    this.open.set('writeOff');
  }

  /** Cierra el formulario abierto (la confirmación maneja su propio Escape). */
  protected closeForm(): void {
    if (this.open() === 'entry' || this.open() === 'output') {
      this.open.set('none');
    }
  }

  protected cancelWriteOff(): void {
    this.open.set('none');
  }

  protected onSaved(lot: Lot): void {
    this.open.set('none');
    this.#toast.success(lotRegisteredMessage(lot));
    this.reload();
  }

  protected onOutputSaved(result: OutputResult): void {
    this.open.set('none');
    this.#toast.success(outputRegisteredMessage(result));
    this.reload();
  }

  protected async confirmWriteOff(): Promise<void> {
    this.writingOff.set(true);
    try {
      const result = await this.#inventory.writeOffExpired();
      this.open.set('none');
      if (result.lots === 0) {
        this.#toast.info(writeOffMessage(result));
      } else {
        this.#toast.success(writeOffMessage(result));
        this.reload();
      }
    } catch (e) {
      this.open.set('none');
      this.#toast.error(problemMessage(e));
    } finally {
      this.writingOff.set(false);
    }
  }
}
