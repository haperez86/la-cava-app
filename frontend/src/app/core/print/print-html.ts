import { DOCUMENT } from '@angular/common';
import { Service, inject } from '@angular/core';

export const PRINT_FRAME_ID = 'lacava-print-frame';
/** Espera antes de imprimir para que el iframe termine de pintar (igual que `printHTML` de la demo). */
export const PRINT_DELAY_MS = 300;
export const PRINT_ERROR = 'No se pudo abrir la impresión.';

/**
 * Imprime un documento HTML completo en un iframe oculto, sin ventanas emergentes (idea de `printHTML`
 * en demo/index.html). Quita el iframe de una impresión anterior y el propio al terminar (`afterprint`).
 * Se resuelve cuando se abrió el diálogo de impresión; se rechaza con {@link PRINT_ERROR} si no se pudo.
 * La usan la hoja de QR (HU-07), la comanda y la factura en tirilla de 80 mm.
 */
export function printHtml(html: string, doc: Document = document): Promise<void> {
  doc.getElementById(PRINT_FRAME_ID)?.remove();

  const frame = doc.createElement('iframe');
  frame.id = PRINT_FRAME_ID;
  frame.setAttribute('aria-hidden', 'true');
  frame.tabIndex = -1;
  frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden';
  doc.body.appendChild(frame);

  const win = frame.contentWindow;
  const frameDoc = frame.contentDocument ?? win?.document;
  if (!win || !frameDoc) {
    frame.remove();
    return Promise.reject(new Error(PRINT_ERROR));
  }
  frameDoc.open();
  frameDoc.write(html);
  frameDoc.close();

  return new Promise<void>((resolve, reject) => {
    setTimeout(async () => {
      try {
        await imagesReady(frameDoc);
        win.addEventListener('afterprint', () => frame.remove(), { once: true });
        win.focus();
        win.print();
        resolve();
      } catch {
        frame.remove();
        reject(new Error(PRINT_ERROR));
      }
    }, PRINT_DELAY_MS);
  });
}

/** Espera a que las imágenes (p. ej. los QR) estén decodificadas; una imagen rota no bloquea la impresión. */
async function imagesReady(doc: Document): Promise<void> {
  const images = Array.from(doc.images ?? []);
  await Promise.all(images.map((img) => (typeof img.decode === 'function' ? img.decode().catch(() => undefined) : undefined)));
}

/** Servicio inyectable sobre {@link printHtml}, para poder reemplazarlo en las pruebas de las pantallas. */
@Service()
export class HtmlPrinter {
  readonly #doc = inject(DOCUMENT);

  print(html: string): Promise<void> {
    return printHtml(html, this.#doc);
  }
}
