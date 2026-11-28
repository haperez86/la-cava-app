/** Escapa texto de usuario antes de meterlo en el HTML de una tirilla. */
export const escapeHtml = (value: string | number): string =>
  String(value).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);

/**
 * Estilos de la tirilla de 80 mm (TICKET_CSS de la demo): ancho 72 mm, `Courier New` 12px, negro sobre blanco.
 * Documento independiente que se imprime en un iframe: no hereda los tokens de la app.
 */
export const TICKET_CSS =
  '@page{size:80mm auto;margin:4mm}body{font:12px/1.4 "Courier New",monospace;color:#000;background:#fff;width:72mm;margin:0 auto}' +
  'h1{font-size:18px;letter-spacing:3px;text-align:center;margin:0}.c{text-align:center}.sm{font-size:10px}' +
  'hr{border:0;border-top:1px dashed #000;margin:8px 0}table{width:100%;border-collapse:collapse}' +
  'td,th{padding:3px 0;text-align:left;vertical-align:top}th{border-bottom:1px dashed #000}.r{text-align:right}' +
  '.tot td{font-size:15px;font-weight:bold;padding-top:6px}';
