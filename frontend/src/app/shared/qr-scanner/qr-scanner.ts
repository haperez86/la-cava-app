import { InjectionToken, Service, inject } from '@angular/core';

/** Motor con el que se lee el QR: el nativo del navegador o `@zxing/browser` como respaldo. */
export type QrEngine = 'barcode-detector' | 'zxing';

/** Por qué no se pudo usar la cámara: sin cámara, permiso negado o navegador sin lector. */
export type QrScanFailure = 'unavailable' | 'denied' | 'unsupported';

export class QrScanError extends Error {
  constructor(readonly reason: QrScanFailure) {
    super(`QR scanner ${reason}`);
    this.name = 'QrScanError';
  }
}

/** Lectura en curso; `stop()` apaga la cámara y es idempotente. */
export interface QrScanSession {
  readonly engine: QrEngine;
  stop(): void;
}

/** Parte de `BrowserQRCodeReader` (@zxing/browser) que se usa. */
export interface ZxingQrReader {
  decodeFromStream(
    stream: MediaStream,
    preview: HTMLVideoElement,
    callback: (result: { getText(): string } | undefined) => void,
  ): Promise<{ stop(): void }>;
}

/** Carga perezosa de zxing: solo se descarga en navegadores sin `BarcodeDetector` (p. ej. Safari en iPhone). */
export const ZXING_QR_READER = new InjectionToken<() => Promise<ZxingQrReader>>('ZXING_QR_READER', {
  factory: () => async () => new (await import('@zxing/browser')).BrowserQRCodeReader(),
});

/** `BarcodeDetector` aún no está en los tipos del DOM de TypeScript. */
interface BarcodeDetectorLike {
  detect(source: HTMLVideoElement): Promise<readonly { rawValue: string }[]>;
}
interface BarcodeDetectorCtor {
  new (options: { formats: string[] }): BarcodeDetectorLike;
  getSupportedFormats?(): Promise<string[]>;
}

const DETECT_INTERVAL_MS = 300;

/** `BarcodeDetector` si el navegador lo trae y lee 'qr_code'; si no, `undefined`. */
async function nativeDetector(): Promise<BarcodeDetectorCtor | undefined> {
  if (!('BarcodeDetector' in globalThis)) {
    return undefined;
  }
  const ctor = (globalThis as unknown as { BarcodeDetector: BarcodeDetectorCtor }).BarcodeDetector;
  try {
    const formats = (await ctor.getSupportedFormats?.()) ?? ['qr_code'];
    return formats.includes('qr_code') ? ctor : undefined;
  } catch {
    return undefined;
  }
}

function failureOf(error: unknown): QrScanFailure {
  const name = error instanceof Error || error instanceof DOMException ? error.name : '';
  return name === 'NotAllowedError' || name === 'SecurityError' ? 'denied' : 'unavailable';
}

/**
 * Lector de QR con la cámara trasera (LCV-HU-08 CA1). Usa `BarcodeDetector` cuando existe y soporta QR; si no,
 * `@zxing/browser`. Entrega un único texto leído y apaga la cámara al leerlo o al llamar a `stop()`.
 * Es un servicio para poder sustituirlo en las pruebas de las pantallas.
 */
@Service()
export class QrScanner {
  readonly #zxing = inject(ZXING_QR_READER);

  async start(video: HTMLVideoElement, onCode: (text: string) => void): Promise<QrScanSession> {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new QrScanError('unavailable');
    }
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
    } catch (error) {
      throw new QrScanError(failureOf(error));
    }

    let timer: ReturnType<typeof setInterval> | undefined;
    let controls: { stop(): void } | undefined;
    let stopped = false;
    const stop = () => {
      if (stopped) {
        return;
      }
      stopped = true;
      clearInterval(timer);
      controls?.stop();
      stream.getTracks().forEach((track) => track.stop());
      video.srcObject = null;
    };
    const finish = (text: string) => {
      if (!stopped && text) {
        stop();
        onCode(text);
      }
    };

    try {
      const Detector = await nativeDetector();
      if (Detector) {
        const detector = new Detector({ formats: ['qr_code'] });
        video.srcObject = stream;
        await video.play();
        let busy = false;
        timer = setInterval(async () => {
          if (busy || stopped) {
            return;
          }
          busy = true;
          try {
            const codes = await detector.detect(video);
            finish(codes[0]?.rawValue ?? '');
          } catch {
            // Un cuadro que no se pudo analizar: se intenta con el siguiente.
          } finally {
            busy = false;
          }
        }, DETECT_INTERVAL_MS);
        return { engine: 'barcode-detector', stop };
      }

      const reader = await this.#zxing();
      controls = await reader.decodeFromStream(stream, video, (result) => finish(result?.getText() ?? ''));
      if (stopped) {
        controls.stop();
      }
      return { engine: 'zxing', stop };
    } catch {
      stop();
      throw new QrScanError('unsupported');
    }
  }
}
