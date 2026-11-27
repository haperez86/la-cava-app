import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { businessToday } from '../../shared/dates/business-date';
import { InventoryPage, NO_EXPIRED_LOTS, WRITE_OFF_CONFIRM } from './inventory-page';
import { Lot, OutputResult } from './inventory.model';
import { LOTS_URL, OUTPUTS_URL, PRODUCTS_URL, WRITE_OFF_URL } from './inventory.service';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

const CREATED: Lot = {
  id: 41,
  productId: 9,
  productName: 'Vino caliente',
  quantityReceived: 12,
  quantityAvailable: 12,
  expiresOn: businessToday(),
  seasonName: 'Navidad',
  receivedAt: '2026-11-27T15:00:00Z',
  status: 'EXPIRING',
};

describe('InventoryPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<InventoryPage>;
  let root: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [InventoryPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InventoryPage);
    await fixture.whenStable();
    root = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  it('shows the header, the neutral empty state and no request until the form opens', () => {
    expect(root.querySelector('h2')?.textContent?.trim()).toBe('Inventario');
    expect(root.querySelector('[data-testid="inventory-empty"]')?.textContent?.trim()).toBe(
      'Registra entradas de mercancía por lote.',
    );
    expect(root.querySelector('[role="dialog"]')).toBeNull();
  });

  it('CA1 "Registrar entrada" opens the form; saving shows "Lote registrado: N × Producto" and closes it', async () => {
    root.querySelector<HTMLButtonElement>('[data-testid="register-lot"]')!.click();
    await fixture.whenStable();
    expect(root.querySelector('[role="dialog"]')).not.toBeNull();

    http
      .expectOne((r) => r.url === PRODUCTS_URL)
      .flush({ content: [{ id: 9, name: 'Vino caliente', seasonName: 'Navidad' }], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    await settle();
    await fixture.whenStable();

    const select = root.querySelector<HTMLSelectElement>('#li-product')!;
    select.value = '9';
    select.dispatchEvent(new Event('change'));
    for (const [id, value] of [
      ['li-qty', '12'],
      ['li-exp', businessToday()],
    ]) {
      const input = root.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
    root.querySelector<HTMLButtonElement>('[data-testid="save-lot"]')!.click();
    await settle();

    http.expectOne(LOTS_URL).flush(CREATED, { status: 201, statusText: 'Created' });
    await settle();
    await fixture.whenStable();

    const toasts = TestBed.inject(ToastService).toasts();
    expect(toasts.map((t) => [t.kind, t.message])).toEqual([['success', 'Lote registrado: 12 × Vino caliente']]);
    expect(root.querySelector('[role="dialog"]')).toBeNull();
  });

  it('Cancelar closes the form without sending anything', async () => {
    root.querySelector<HTMLButtonElement>('[data-testid="register-lot"]')!.click();
    await fixture.whenStable();
    http.expectOne((r) => r.url === PRODUCTS_URL).flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    await settle();
    await fixture.whenStable();

    Array.from(root.querySelectorAll<HTMLButtonElement>('.modal-actions .btn'))
      .find((b) => b.textContent?.includes('Cancelar'))!
      .click();
    await fixture.whenStable();

    expect(root.querySelector('[role="dialog"]')).toBeNull();
  });

  const toastsOf = () => TestBed.inject(ToastService).toasts().map((t) => [t.kind, t.message]);
  const button = (testId: string) => root.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!;

  async function registerOutput(result: OutputResult): Promise<void> {
    button('register-output').click();
    await fixture.whenStable();
    expect(root.querySelector('[role="dialog"]')).not.toBeNull();
    http.expectOne((r) => r.url === PRODUCTS_URL).flush({
      content: [{ id: 3, name: 'Copa de Malbec', seasonName: null, available: 12 }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    await settle();
    await fixture.whenStable();

    const select = root.querySelector<HTMLSelectElement>('#lo-product')!;
    select.value = '3';
    select.dispatchEvent(new Event('input'));
    select.dispatchEvent(new Event('change'));
    for (const [id, value] of [
      ['lo-qty', String(result.quantity)],
      ['lo-note', 'Merma de la noche'],
    ]) {
      const input = root.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
    button('save-output').click();
    await settle();

    http.expectOne(OUTPUTS_URL).flush(result);
    await settle();
    await fixture.whenStable();
  }

  const OUTPUT: OutputResult = {
    productId: 3,
    productName: 'Copa de Malbec',
    type: 'WASTE_OUT',
    quantity: 5,
    consumed: [
      { lotId: 7, expiresOn: '2026-12-01', quantity: 3 },
      { lotId: 8, expiresOn: '2026-12-20', quantity: 2 },
    ],
    available: 7,
  };

  it('CA1 a FEFO output split across lots says so in the toast and closes the form', async () => {
    const before = fixture.componentInstance.reloadTick();
    await registerOutput(OUTPUT);

    expect(toastsOf()).toEqual([['success', 'Salida registrada: 5 × Copa de Malbec (de 2 lotes)']]);
    expect(root.querySelector('[role="dialog"]')).toBeNull();
    expect(fixture.componentInstance.reloadTick()).toBe(before + 1);
  });

  it('CA2 an output from a single lot shows "Salida registrada: N × Producto"', async () => {
    await registerOutput({ ...OUTPUT, quantity: 2, consumed: [{ lotId: 7, expiresOn: '2026-12-01', quantity: 2 }] });

    expect(toastsOf()).toEqual([['success', 'Salida registrada: 2 × Copa de Malbec']]);
  });

  async function askWriteOff(): Promise<void> {
    button('write-off-expired').click();
    await fixture.whenStable();
  }

  it('CA3 "Dar de baja vencidos" asks with the app dialog; Cancelar does not call the endpoint', async () => {
    const nativeConfirm = vi.spyOn(window, 'confirm');
    await askWriteOff();

    const dialog = root.querySelector('[role="alertdialog"]');
    expect(dialog?.textContent).toContain(WRITE_OFF_CONFIRM);
    expect(button('confirm-cancel').textContent?.trim()).toBe('Cancelar');
    expect(button('confirm-accept').textContent?.trim()).toBe('Dar de baja');

    button('confirm-cancel').click();
    await fixture.whenStable();

    http.expectNone(WRITE_OFF_URL);
    expect(root.querySelector('[role="alertdialog"]')).toBeNull();
    expect(nativeConfirm).not.toHaveBeenCalled();
    expect(toastsOf()).toEqual([]);
  });

  it('CA3 confirming writes off the expired lots and shows lots and units', async () => {
    const before = fixture.componentInstance.reloadTick();
    await askWriteOff();
    button('confirm-accept').click();
    await settle();

    const request = http.expectOne(WRITE_OFF_URL);
    expect(request.request.method).toBe('POST');
    request.flush({ lots: 2, units: 9 });
    await settle();
    await fixture.whenStable();

    expect(toastsOf()).toEqual([['success', 'Se dieron de baja 2 lote(s), 9 unidad(es).']]);
    expect(root.querySelector('[role="alertdialog"]')).toBeNull();
    expect(fixture.componentInstance.reloadTick()).toBe(before + 1);
  });

  it('CA3 with no expired lots it says "No hay lotes vencidos."', async () => {
    await askWriteOff();
    button('confirm-accept').click();
    await settle();

    http.expectOne(WRITE_OFF_URL).flush({ lots: 0, units: 0 });
    await settle();
    await fixture.whenStable();

    expect(toastsOf()).toEqual([['info', NO_EXPIRED_LOTS]]);
  });

  it('CA3 a failed write-off is shown as an error toast', async () => {
    await askWriteOff();
    button('confirm-accept').click();
    await settle();

    http.expectOne(WRITE_OFF_URL).flush({ status: 403, detail: 'No tienes permiso.' }, { status: 403, statusText: 'Forbidden' });
    await settle();
    await fixture.whenStable();

    expect(toastsOf()).toEqual([['error', 'No tienes permiso.']]);
  });
});
