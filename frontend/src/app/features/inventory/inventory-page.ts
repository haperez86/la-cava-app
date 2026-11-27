import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Component, inject, signal } from '@angular/core';
import { ToastService } from '../../core/notifications/toast.service';
import { Lot } from './inventory.model';
import { LotInForm } from './lot-in-form';

/** Mensaje de éxito de una entrada (LCV-HU-04 CA1). */
export function lotRegisteredMessage(lot: Lot): string {
  return `Lote registrado: ${lot.quantityReceived} × ${lot.productName}`;
}

/** "Inventario" (D32). HU-04: encabezado y "Registrar entrada"; la tabla, contadores y filtros llegan en HU-06. */
@Component({
  selector: 'app-inventory-page',
  imports: [LotInForm, CdkTrapFocus],
  templateUrl: './inventory-page.html',
  styleUrl: './inventory-page.scss',
  host: { '(document:keydown.escape)': 'closeEntry()' },
})
export class InventoryPage {
  readonly #toast = inject(ToastService);

  protected readonly entryOpen = signal(false);

  protected openEntry(): void {
    this.entryOpen.set(true);
  }

  protected closeEntry(): void {
    this.entryOpen.set(false);
  }

  protected onSaved(lot: Lot): void {
    this.entryOpen.set(false);
    this.#toast.success(lotRegisteredMessage(lot));
  }
}
