import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { HtmlPrinter } from '../../core/print/print-html';
import { QrCodeRenderer } from './qr-sheet';
import { DUPLICATE_TABLE_DETAIL, TABLE_NUMBER_INVALID } from './table-form';
import { NO_ACTIVE_TABLES, TablesAdmin, rotatedMessage } from './tables-admin';
import { DiningTable, QrSheetEntry } from './tables.model';
import { QR_SHEET_URL, TABLES_URL, rotateTokenUrl, tableAccountUrl, tableActiveUrl } from './tables.service';
import { freeTable, occupiedTable } from './testing';
import { formatCop } from '../../shared/pipes/cop-currency.pipe';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

const TABLES: DiningTable[] = [
  freeTable(12, 2, false),
  freeTable(11, 1),
  freeTable(13, 3),
];

const SHEET: QrSheetEntry[] = [
  { number: 1, code: 'LACAVA:MESA:1:tok-uno', pin: '4821' },
  { number: 3, code: 'LACAVA:MESA:3:tok-tres', pin: '0357' },
];

describe('TablesAdmin', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<TablesAdmin>;
  let root: HTMLElement;
  let toast: ToastService;
  let qrSvg: ReturnType<typeof vi.fn<(code: string) => Promise<string>>>;
  let print: ReturnType<typeof vi.fn<(html: string) => Promise<void>>>;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    // Sustituto de `qrcode`: un SVG que deja ver qué código dibujó.
    qrSvg = vi.fn(async (code: string) => `<svg data-code="${code}"></svg>`);
    print = vi.fn(async () => undefined);
    TestBed.configureTestingModule({
      imports: [TablesAdmin],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: QrCodeRenderer, useValue: { svg: qrSvg } },
        { provide: HtmlPrinter, useValue: { print } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
    confirmSpy = vi.spyOn(window, 'confirm');
  });

  afterEach(() => {
    http.verify();
    confirmSpy.mockRestore();
  });

  async function refresh(): Promise<void> {
    await settle();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function render(tables: DiningTable[] = TABLES): Promise<void> {
    fixture = TestBed.createComponent(TablesAdmin);
    root = fixture.nativeElement as HTMLElement;
    http.expectOne((r) => r.url === TABLES_URL && r.method === 'GET').flush(tables);
    await refresh();
  }

  function tiles(): HTMLElement[] {
    return Array.from(root.querySelectorAll<HTMLElement>('[data-testid="table"]'));
  }

  function tile(number: number): HTMLElement {
    return tiles().find((t) => t.textContent?.includes(`Mesa ${number}`))!;
  }

  function click(el: Element | null | undefined): void {
    (el as HTMLElement).click();
  }

  function typeNumber(value: string): void {
    const input = root.querySelector<HTMLInputElement>('#tf-number')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function save(): Promise<void> {
    click(root.querySelector('[data-testid="save-table"]'));
    await refresh();
  }

  it('lists tables sorted by number with their Activa/Inactiva state', async () => {
    await render();

    expect(tiles().map((t) => t.querySelector('.num')?.textContent?.trim())).toEqual(['Mesa 1', 'Mesa 2', 'Mesa 3']);
    expect(tile(1).querySelector('[data-testid="table-state"]')?.textContent?.trim()).toBe('Activa');
    expect(tile(2).querySelector('[data-testid="table-state"]')?.textContent?.trim()).toBe('Inactiva');
    expect(tile(2).querySelector('[data-testid="toggle-active"]')?.textContent?.trim()).toBe('Activar');
    expect(tile(1).querySelector('[data-testid="toggle-active"]')?.textContent?.trim()).toBe('Desactivar');
  });

  it('LCV-HU-09 CA1/CA2 each card shows state, stage, orders, total and waiter, with the legend', async () => {
    await render([occupiedTable(11, 1, 'READY', 2, 54000, 'Mateo Ruiz'), freeTable(13, 3), freeTable(12, 2, false)]);

    expect(root.querySelector('[data-testid="table-legend"]')).not.toBeNull();
    expect(tile(1).querySelector('[data-testid="table-status"]')?.textContent?.trim()).toBe('Ocupada');
    expect(tile(1).querySelector('[data-testid="table-stage"]')?.textContent?.trim()).toBe('Listo para recoger');
    expect(tile(1).querySelector('[data-testid="table-orders"]')?.textContent?.trim()).toBe('2 pedidos');
    expect(tile(1).querySelector('[data-testid="table-total"]')?.textContent?.trim()).toBe(formatCop(54000));
    expect(tile(1).querySelector('[data-testid="table-waiter"]')?.textContent?.trim()).toBe('Mateo Ruiz');
    expect(tile(3).querySelector('[data-testid="table-status"]')?.textContent?.trim()).toBe('Disponible');
    expect(tile(3).querySelector('[data-testid="table-stage"]')).toBeNull();
    expect(tile(2).querySelector<HTMLButtonElement>('[data-testid="view-account"]')!.disabled).toBe(true);
  });

  it('LCV-HU-09 "Ver cuenta": inactive without open orders stays disabled; inactive with open orders is enabled and opens', async () => {
    const inactiveWithOrders = { ...occupiedTable(12, 2, 'SERVED', 1, 18000), active: false };
    await render([freeTable(11, 1, false), inactiveWithOrders]);

    expect(tile(1).querySelector<HTMLButtonElement>('[data-testid="view-account"]')!.disabled).toBe(true);
    const button = tile(2).querySelector<HTMLButtonElement>('[data-testid="view-account"]')!;
    expect(button.disabled).toBe(false);

    click(button);
    await refresh();
    http.expectOne(tableAccountUrl(12)).flush({ id: 12, number: 2, account: { orders: [], total: 0 } });
    await refresh();
    expect(root.querySelector('[data-testid="account-dialog"] h3')?.textContent).toBe('Mesa 2');
  });

  it('LCV-HU-09 "Ver cuenta" opens the table account', async () => {
    await render([occupiedTable(11, 1, 'PENDING', 1, 18000, 'Mateo Ruiz'), freeTable(13, 3)]);

    click(tile(1).querySelector('[data-testid="view-account"]'));
    await refresh();
    http.expectOne(tableAccountUrl(11)).flush({ id: 11, number: 1, account: { orders: [], total: 0 } });
    await refresh();

    expect(root.querySelector('[data-testid="account-dialog"] h3')?.textContent).toBe('Mesa 1');
    expect(root.querySelector('[data-testid="account-new-order"]')).not.toBeNull();
  });

  it('CA1 creates a table sending {number} and reloads the grid', async () => {
    await render();
    const success = vi.spyOn(toast, 'success');
    click(root.querySelector('[data-testid="new-table"]'));
    await refresh();

    typeNumber('4');
    await save();

    const req = http.expectOne((r) => r.url === TABLES_URL && r.method === 'POST');
    expect(req.request.body).toEqual({ number: 4 });
    req.flush({ id: 14, number: 4, active: true }, { status: 201, statusText: 'Created' });
    await refresh();

    http.expectOne((r) => r.url === TABLES_URL && r.method === 'GET').flush([...TABLES, freeTable(14, 4)]);
    await refresh();

    expect(root.querySelector('#tf-number')).toBeNull();
    expect(success).toHaveBeenCalledWith('Mesa 4 creada.');
    expect(tiles()).toHaveLength(4);
  });

  it.each(['0', '-2', '2.5', ''])('CA1 does not send an invalid table number (%j)', async (value) => {
    await render();
    click(root.querySelector('[data-testid="new-table"]'));
    await refresh();

    typeNumber(value);
    await save();

    http.expectNone((r) => r.method === 'POST');
    expect(root.querySelector('[data-testid="error-number"]')?.textContent?.trim()).toBe(TABLE_NUMBER_INVALID);
  });

  it('CA1 shows the 409 duplicate number on the field and keeps the form open', async () => {
    await render();
    click(root.querySelector('[data-testid="new-table"]'));
    await refresh();

    typeNumber('1');
    await save();
    http
      .expectOne((r) => r.url === TABLES_URL && r.method === 'POST')
      .flush({ status: 409, detail: DUPLICATE_TABLE_DETAIL }, { status: 409, statusText: 'Conflict' });
    await refresh();

    expect(root.querySelector('[data-testid="error-number"]')?.textContent?.trim()).toBe(DUPLICATE_TABLE_DETAIL);
    expect(root.querySelector('#tf-number')).not.toBeNull();
  });

  it('CA1 shows a 400 error on number from errors[]', async () => {
    await render();
    click(root.querySelector('[data-testid="new-table"]'));
    await refresh();

    typeNumber('7');
    await save();
    http
      .expectOne((r) => r.url === TABLES_URL && r.method === 'POST')
      .flush(
        { status: 400, errors: [{ field: 'number', message: 'El número debe ser mayor que cero.' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    await refresh();

    expect(root.querySelector('[data-testid="error-number"]')?.textContent?.trim()).toBe('El número debe ser mayor que cero.');
  });

  it('CA1 deactivates and activates a table with PATCH and updates its card', async () => {
    await render();

    click(tile(1).querySelector('[data-testid="toggle-active"]'));
    const off = http.expectOne(tableActiveUrl(11));
    expect(off.request.method).toBe('PATCH');
    expect(off.request.body).toEqual({ active: false });
    off.flush(null);
    await refresh();
    expect(tile(1).querySelector('[data-testid="table-state"]')?.textContent?.trim()).toBe('Inactiva');
    expect(tile(1).querySelector('[data-testid="toggle-active"]')?.textContent?.trim()).toBe('Activar');

    click(tile(2).querySelector('[data-testid="toggle-active"]'));
    const on = http.expectOne(tableActiveUrl(12));
    expect(on.request.method).toBe('PATCH');
    expect(on.request.body).toEqual({ active: true });
    on.flush(null);
    await refresh();
    expect(tile(2).querySelector('[data-testid="table-state"]')?.textContent?.trim()).toBe('Activa');
  });

  it('CA2 prints all active tables: asks qr-sheet, draws one QR per table and prints number, QR image and PIN', async () => {
    await render();

    click(root.querySelector('[data-testid="print-all"]'));
    http.expectOne(QR_SHEET_URL).flush(SHEET);
    await refresh();

    expect(qrSvg.mock.calls.map(([code]) => code)).toEqual(['LACAVA:MESA:1:tok-uno', 'LACAVA:MESA:3:tok-tres']);
    expect(print).toHaveBeenCalledTimes(1);
    const html = print.mock.calls[0][0];
    expect(html).toContain('Mesa 1');
    expect(html).toContain('Mesa 3');
    expect(html).toContain('PIN: 4821');
    expect(html).toContain('PIN: 0357');
    expect(html).toContain('<img class="qr" alt="QR de la mesa 1" src="data:image/svg+xml');
    expect(html).toContain(encodeURIComponent('data-code="LACAVA:MESA:3:tok-tres"'));
  });

  it('CA2 prints only the chosen table', async () => {
    await render();

    click(tile(3).querySelector('[data-testid="print-one"]'));
    http.expectOne(QR_SHEET_URL).flush(SHEET);
    await refresh();

    expect(qrSvg).toHaveBeenCalledTimes(1);
    expect(qrSvg).toHaveBeenCalledWith('LACAVA:MESA:3:tok-tres');
    const html = print.mock.calls[0][0];
    expect(html).toContain('Mesa 3');
    expect(html).toContain('PIN: 0357');
    expect(html).not.toContain('Mesa 1');
    expect(html).not.toContain('4821');
    expect(tile(2).querySelector<HTMLButtonElement>('[data-testid="print-one"]')!.disabled).toBe(true);
  });

  it('CA2 tells when there are no active tables to print', async () => {
    await render();
    const info = vi.spyOn(toast, 'info');

    click(root.querySelector('[data-testid="print-all"]'));
    http.expectOne(QR_SHEET_URL).flush([]);
    await refresh();

    expect(print).not.toHaveBeenCalled();
    expect(info).toHaveBeenCalledWith(NO_ACTIVE_TABLES);
  });

  it('CA3 regenerate asks for the app confirmation (no window.confirm); Cancelar does not call the API', async () => {
    await render();

    click(tile(1).querySelector('[data-testid="rotate"]'));
    await refresh();

    const dialog = root.querySelector('[role="alertdialog"]');
    expect(dialog?.textContent).toContain('Regenerar QR de la mesa 1');
    expect(dialog?.textContent).toContain('dejará de servir');
    expect(confirmSpy).not.toHaveBeenCalled();

    click(root.querySelector('[data-testid="confirm-cancel"]'));
    await refresh();

    http.expectNone((r) => r.method === 'POST');
    expect(root.querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('CA3 Confirmar calls rotate-token and shows the toast', async () => {
    await render();
    const success = vi.spyOn(toast, 'success');

    click(tile(3).querySelector('[data-testid="rotate"]'));
    await refresh();
    click(root.querySelector('[data-testid="confirm-accept"]'));

    const req = http.expectOne(rotateTokenUrl(13));
    expect(req.request.method).toBe('POST');
    req.flush(null);
    await refresh();

    expect(success).toHaveBeenCalledWith(rotatedMessage(3));
    expect(rotatedMessage(3)).toBe('QR de la mesa 3 regenerado. Imprime el nuevo.');
    expect(root.querySelector('[role="alertdialog"]')).toBeNull();
    expect(confirmSpy).not.toHaveBeenCalled();
  });
});
