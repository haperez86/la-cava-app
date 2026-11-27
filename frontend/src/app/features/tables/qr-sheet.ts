import { Service } from '@angular/core';
import * as QRCode from 'qrcode';
import { QrSheetEntry } from './tables.model';

/** Dibuja el QR de un código como SVG (D35: los QR se generan en el frontend con `qrcode`). */
@Service()
export class QrCodeRenderer {
  svg(code: string): Promise<string> {
    return QRCode.toString(code, { type: 'svg', errorCorrectionLevel: 'M', margin: 1 });
  }
}

/** Una mesa lista para la hoja: datos de `qr-sheet` más el SVG de su QR. */
export interface QrSheetItem extends QrSheetEntry {
  readonly svg: string;
}

const escapeHtml = (value: string | number) =>
  String(value).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);

/**
 * CA2: hoja A4 imprimible, 6 mesas por página (2 × 3), con "Mesa N" en grande, el QR y "PIN: 1234" debajo
 * para el respaldo manual (D12). Documento independiente: no hereda los tokens de la app, solo blanco y negro.
 */
export function qrSheetHtml(items: readonly QrSheetItem[]): string {
  const cards = items
    .map(
      (item) => `
    <section class="qr-card">
      <p class="brand">LA CAVA · COPAS Y TABLAS</p>
      <h1>Mesa ${escapeHtml(item.number)}</h1>
      <img class="qr" alt="QR de la mesa ${escapeHtml(item.number)}" src="data:image/svg+xml;charset=utf-8,${encodeURIComponent(item.svg)}" />
      <p class="pin">PIN: ${escapeHtml(item.pin)}</p>
      <p class="hint">Escanea el código para tomar el pedido de esta mesa.</p>
    </section>`,
    )
    .join('');

  return `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8" />
<title>Códigos QR de mesas · La Cava</title>
<style>
  @page { size: A4; margin: 12mm; }
  * { box-sizing: border-box; }
  body { margin: 0; color: black; background: white; font-family: Georgia, serif; }
  .sheet { display: grid; grid-template-columns: repeat(2, 1fr); grid-auto-rows: 85mm; gap: 6mm; }
  .qr-card {
    border: 1px dashed gray; border-radius: 4mm; padding: 4mm;
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    text-align: center; break-inside: avoid; page-break-inside: avoid;
  }
  .brand { margin: 0; font: 600 8pt Georgia, serif; letter-spacing: 2px; }
  h1 { margin: 1mm 0 2mm; font-size: 26pt; }
  .qr { width: 52mm; height: 52mm; image-rendering: pixelated; }
  .pin { margin: 2mm 0 0; font: 700 15pt "Courier New", monospace; letter-spacing: 2px; }
  .hint { margin: 1mm 0 0; font: 8pt system-ui, sans-serif; }
</style>
</head>
<body>
<main class="sheet">${cards}
</main>
</body>
</html>`;
}
