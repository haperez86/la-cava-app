import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { businessToday } from '../../shared/dates/business-date';
import { InventoryPage } from './inventory-page';
import { Lot } from './inventory.model';
import { LOTS_URL, PRODUCTS_URL } from './inventory.service';

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
});
