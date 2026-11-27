import { TestBed } from '@angular/core/testing';
import { QrCodeRenderer, qrSheetHtml } from './qr-sheet';

describe('qr-sheet', () => {
  it('CA2 QrCodeRenderer draws the code as an SVG with qrcode', async () => {
    const svg = await TestBed.inject(QrCodeRenderer).svg('LACAVA:MESA:5:abcDEF_-123');

    expect(svg).toContain('<svg');
    expect(svg).toContain('<path');
  });

  it('CA2 builds an A4 sheet with the table number, the QR image and the PIN below it', () => {
    const html = qrSheetHtml([
      { number: 5, code: 'LACAVA:MESA:5:x', pin: '0042', svg: '<svg id="q5"></svg>' },
      { number: 8, code: 'LACAVA:MESA:8:y', pin: '9310', svg: '<svg id="q8"></svg>' },
    ]);
    const doc = new DOMParser().parseFromString(html, 'text/html');
    const cards = Array.from(doc.querySelectorAll('.qr-card'));

    expect(html).toContain('size: A4');
    expect(cards).toHaveLength(2);
    expect(cards[0].querySelector('h1')?.textContent).toBe('Mesa 5');
    expect(cards[0].querySelector('img.qr')?.getAttribute('src')).toBe(
      `data:image/svg+xml;charset=utf-8,${encodeURIComponent('<svg id="q5"></svg>')}`,
    );
    expect(cards[0].querySelector('.pin')?.textContent).toBe('PIN: 0042');
    // El PIN va debajo del QR.
    const children = Array.from(cards[1].children).map((el) => el.className || el.tagName.toLowerCase());
    expect(children.indexOf('pin')).toBeGreaterThan(children.indexOf('qr'));
    expect(cards[1].querySelector('.pin')?.textContent).toBe('PIN: 9310');
  });
});
