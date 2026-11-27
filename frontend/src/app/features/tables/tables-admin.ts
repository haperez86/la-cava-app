import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Component, computed, inject, signal } from '@angular/core';
import { problemMessage } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { HtmlPrinter } from '../../core/print/print-html';
import { Icon } from '../../shared/icons/icon';
import { ConfirmDialog } from '../inventory/confirm-dialog';
import { QrCodeRenderer, qrSheetHtml } from './qr-sheet';
import { TableForm } from './table-form';
import { DiningTable } from './tables.model';
import { TablesService } from './tables.service';

export const NO_ACTIVE_TABLES = 'No hay mesas activas para imprimir.';
export const PRINT_PREPARE_ERROR = 'No se pudo preparar la impresión de los QR.';
export const rotateConfirmMessage = (n: number) =>
  `Se generará un código QR y un PIN nuevos para la mesa ${n}. El QR impreso que está en la mesa dejará de servir: ` +
  'tendrás que imprimir el nuevo y reemplazarlo.';
export const rotatedMessage = (n: number) => `QR de la mesa ${n} regenerado. Imprime el nuevo.`;

/** Gestión de mesas y QR para el administrador (LCV-HU-07). */
@Component({
  selector: 'app-tables-admin',
  imports: [CdkTrapFocus, ConfirmDialog, Icon, TableForm],
  templateUrl: './tables-admin.html',
  styleUrl: './tables-admin.scss',
  host: { '(document:keydown.escape)': 'closeForm()' },
})
export class TablesAdmin {
  readonly #tables = inject(TablesService);
  readonly #toast = inject(ToastService);
  readonly #qr = inject(QrCodeRenderer);
  readonly #printer = inject(HtmlPrinter);

  readonly #list = signal<readonly DiningTable[]>([]);
  protected readonly tables = computed(() => [...this.#list()].sort((a, b) => a.number - b.number));
  protected readonly loading = signal(false);
  protected readonly creating = signal(false);
  /** Mesa cuya regeneración de QR espera confirmación. */
  protected readonly rotating = signal<DiningTable | null>(null);
  protected readonly rotateBusy = signal(false);
  /** Id de la mesa que se está activando o desactivando. */
  protected readonly toggling = signal<number | null>(null);
  protected readonly printing = signal(false);
  protected readonly rotateMessage = computed(() => rotateConfirmMessage(this.rotating()?.number ?? 0));

  constructor() {
    void this.load();
  }

  protected openCreate(): void {
    this.creating.set(true);
  }

  /** Escape: cierra el formulario de alta; la confirmación maneja su propio Escape. */
  protected closeForm(): void {
    this.creating.set(false);
  }

  protected onCreated(number: number): void {
    this.creating.set(false);
    this.#toast.success(`Mesa ${number} creada.`);
    void this.load();
  }

  /** CA1: activar o desactivar; la tarjeta se actualiza al responder el backend. */
  protected async toggleActive(table: DiningTable): Promise<void> {
    const active = !table.active;
    this.toggling.set(table.id);
    try {
      await this.#tables.setActive(table.id, active);
      this.#list.update((list) => list.map((t) => (t.id === table.id ? { ...t, active } : t)));
      this.#toast.success(active ? `Mesa ${table.number} activada.` : `Mesa ${table.number} desactivada.`);
    } catch (e) {
      this.#toast.error(problemMessage(e));
    } finally {
      this.toggling.set(null);
    }
  }

  protected askRotate(table: DiningTable): void {
    this.rotating.set(table);
  }

  protected cancelRotate(): void {
    this.rotating.set(null);
  }

  /** CA3: nuevo token; el QR anterior deja de servir. */
  protected async confirmRotate(): Promise<void> {
    const table = this.rotating();
    if (!table) {
      return;
    }
    this.rotateBusy.set(true);
    try {
      await this.#tables.rotateToken(table.id);
      this.#toast.success(rotatedMessage(table.number));
    } catch (e) {
      this.#toast.error(problemMessage(e));
    } finally {
      this.rotateBusy.set(false);
      this.rotating.set(null);
    }
  }

  /** CA2: hoja de QR de todas las mesas activas o, con `number`, solo de esa mesa. */
  protected async printQr(number?: number): Promise<void> {
    this.printing.set(true);
    try {
      const sheet = await this.#tables.qrSheet();
      const entries = number === undefined ? sheet : sheet.filter((entry) => entry.number === number);
      if (entries.length === 0) {
        this.#toast.info(NO_ACTIVE_TABLES);
        return;
      }
      const svgs = await Promise.all(entries.map((entry) => this.#qr.svg(entry.code)));
      await this.#printer.print(qrSheetHtml(entries.map((entry, i) => ({ ...entry, svg: svgs[i] }))));
    } catch (e) {
      this.#toast.error(problemMessage(e, PRINT_PREPARE_ERROR));
    } finally {
      this.printing.set(false);
    }
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.#list.set(await this.#tables.list());
    } catch (e) {
      this.#toast.error(problemMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
}
