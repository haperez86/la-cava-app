import { CdkTrapFocus } from '@angular/cdk/a11y';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, afterNextRender, inject, output, signal, viewChild } from '@angular/core';
import { FieldTree, FormField, ValidationError, form, submit, validate } from '@angular/forms/signals';
import { problemMessage } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { QrScanError, QrScanFailure, QrScanSession, QrScanner } from '../../shared/qr-scanner/qr-scanner';
import { TABLE_NUMBER_INVALID } from './table-form';
import { ResolveTableRequest, ResolvedTable } from './tables.model';
import { TablesService } from './tables.service';

export const INVALID_TABLE_CODE = 'Código de mesa inválido';
export const PIN_INVALID = 'El PIN tiene exactamente 4 dígitos.';
export const SCAN_HINT = 'Apunta la cámara al código QR de la mesa.';
/** Cómo se cargó la mesa: leyendo su QR o con número y PIN. */
export type TableOrigin = 'qr' | 'pin';

/** Mesa cargada por el escáner junto con su origen. */
export interface LoadedTable {
  readonly table: ResolvedTable;
  readonly origin: TableOrigin;
}

export const CAMERA_FAILURE: Readonly<Record<QrScanFailure, string>> = {
  unavailable: 'No hay una cámara disponible. Escribe el número de mesa y el PIN.',
  denied: 'No se dio permiso para usar la cámara. Escribe el número de mesa y el PIN.',
  unsupported: 'Este navegador no puede leer códigos QR. Escribe el número de mesa y el PIN.',
};

/**
 * Escáner de la mesa para el mesero (LCV-HU-08): lee el QR con la cámara (CA1) o recibe número y PIN (CA2)
 * y emite la mesa cargada. Un código, PIN o mesa inválidos → "Código de mesa inválido" (CA3).
 */
@Component({
  selector: 'app-qr-scan-dialog',
  imports: [CdkTrapFocus, FormField],
  templateUrl: './qr-scan-dialog.html',
  styleUrl: './qr-scan-dialog.scss',
  host: { '(document:keydown.escape)': 'close()' },
})
export class QrScanDialog {
  readonly #scanner = inject(QrScanner);
  readonly #tables = inject(TablesService);
  readonly #toast = inject(ToastService);

  readonly resolved = output<LoadedTable>();
  readonly closed = output<void>();

  protected readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  protected readonly mode = signal<'camera' | 'manual'>('camera');
  /** Mensaje bajo el video: instrucciones, validación o error de lectura. */
  protected readonly status = signal('Activando la cámara…');
  /** Motivo por el que no hay cámara; el modo manual queda abierto. */
  protected readonly cameraError = signal('');
  protected readonly resolving = signal(false);
  /** El código leído no sirvió: se ofrece volver a escanear. */
  protected readonly canRetry = signal(false);

  #session: QrScanSession | null = null;
  #destroyed = false;
  /** Cada arranque o parada de la cámara invalida las lecturas y sesiones de los arranques anteriores. */
  #generation = 0;

  protected readonly model = signal({ number: 0, pin: '' });
  protected readonly manualForm = form(this.model, (p) => {
    validate(p.number, ({ value }) =>
      Number.isInteger(value()) && value() > 0 ? undefined : { kind: 'tableNumber', message: TABLE_NUMBER_INVALID },
    );
    validate(p.pin, ({ value }) => (/^\d{4}$/.test(value()) ? undefined : { kind: 'pin', message: PIN_INVALID }));
  });

  constructor() {
    afterNextRender(() => void this.startCamera());
    inject(DestroyRef).onDestroy(() => {
      this.#destroyed = true;
      this.#stopCamera();
    });
  }

  protected close(): void {
    this.#stopCamera();
    this.closed.emit();
  }

  protected useManual(): void {
    this.#stopCamera();
    this.mode.set('manual');
  }

  protected async startCamera(): Promise<void> {
    this.#stopCamera();
    const generation = this.#generation;
    const video = this.video()?.nativeElement;
    this.mode.set('camera');
    this.canRetry.set(false);
    this.cameraError.set('');
    this.status.set('Activando la cámara…');
    if (!video) {
      return;
    }
    try {
      const session = await this.#scanner.start(video, (text) => {
        if (generation === this.#generation) {
          void this.#onCode(text);
        }
      });
      // Se cerró, se pasó al modo manual o se volvió a arrancar mientras se pedía la cámara: esta sesión sobra.
      if (this.#destroyed || generation !== this.#generation || this.mode() !== 'camera') {
        session.stop();
        return;
      }
      this.#session = session;
      this.status.set(SCAN_HINT);
    } catch (error) {
      if (this.#destroyed || generation !== this.#generation) {
        return;
      }
      const reason = error instanceof QrScanError ? error.reason : 'unavailable';
      this.cameraError.set(CAMERA_FAILURE[reason]);
      this.mode.set('manual');
    }
  }

  /** Errores de un campo tras tocarlo o al intentar cargar la mesa. */
  protected errorOf(field: FieldTree<unknown>): string {
    const state = field();
    return state.touched() && state.errors().length ? (state.errors()[0].message ?? '') : '';
  }

  /** Error del formulario completo (p. ej. "Código de mesa inválido" devuelto por el backend). */
  protected formError(): string {
    return this.manualForm().errors()[0]?.message ?? '';
  }

  /** CA2: número y PIN; el PIN viaja como texto para conservar los ceros a la izquierda. */
  protected async onManualSubmit(): Promise<void> {
    await submit(this.manualForm, async () => {
      const { number, pin } = this.model();
      const failure = await this.#resolve({ number, pin }, 'pin');
      // CA3: el código inválido también queda en el formulario; los demás errores solo van en el toast.
      return failure === INVALID_TABLE_CODE
        ? [{ kind: 'server', message: failure } satisfies ValidationError.WithOptionalFieldTree]
        : undefined;
    });
  }

  async #onCode(text: string): Promise<void> {
    this.#session = null;
    this.status.set('Validando el código…');
    const failure = await this.#resolve({ code: text }, 'qr');
    if (failure) {
      this.status.set(failure);
      this.canRetry.set(true);
    }
  }

  /** Carga la mesa; devuelve el mensaje de error o `undefined` si se emitió la mesa. */
  async #resolve(request: ResolveTableRequest, origin: TableOrigin): Promise<string | undefined> {
    this.resolving.set(true);
    try {
      const table = await this.#tables.resolve(request);
      // Si el diálogo se cerró mientras respondía el backend, ya no hay a quién emitirle (NG0953).
      if (!this.#destroyed) {
        this.resolved.emit({ table, origin });
      }
      return undefined;
    } catch (error) {
      if (this.#destroyed) {
        return undefined;
      }
      const message =
        error instanceof HttpErrorResponse && error.status === 404 ? INVALID_TABLE_CODE : problemMessage(error);
      this.#toast.error(message);
      return message;
    } finally {
      this.resolving.set(false);
    }
  }

  #stopCamera(): void {
    this.#generation++;
    this.#session?.stop();
    this.#session = null;
  }
}
