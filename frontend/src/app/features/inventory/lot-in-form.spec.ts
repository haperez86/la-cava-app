import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { businessToday } from '../../shared/dates/business-date';
import { Lot, ProductOption } from './inventory.model';
import { LOTS_URL, PRODUCTS_URL } from './inventory.service';
import { EXPIRY_PAST, EXPIRY_REQUIRED, LotInForm, PRODUCT_REQUIRED, QUANTITY_INTEGER, QUANTITY_POSITIVE } from './lot-in-form';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

/** Fecha 'YYYY-MM-DD' desplazada `days` días desde hoy (zona del negocio). */
function dayFromToday(days: number): string {
  return businessToday(new Date(Date.now() + days * 86_400_000));
}

const PRODUCTS: ProductOption[] = [
  { id: 3, name: 'Copa de Malbec', seasonName: null },
  { id: 9, name: 'Vino caliente', seasonName: 'Navidad' },
];

const CREATED: Lot = {
  id: 41,
  productId: 9,
  productName: 'Vino caliente',
  quantityReceived: 12,
  quantityAvailable: 12,
  expiresOn: '2026-12-31',
  seasonName: 'Navidad',
  receivedAt: '2026-11-27T15:00:00Z',
  status: 'ACTIVE',
};

describe('LotInForm', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<LotInForm>;
  let root: HTMLElement;
  let saved: Lot[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [LotInForm],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LotInForm);
    saved = [];
    fixture.componentInstance.saved.subscribe((lot) => saved.push(lot));
    const request = http.expectOne((r) => r.url === PRODUCTS_URL);
    expect(request.request.params.get('size')).toBe('100');
    expect(request.request.params.get('sort')).toBe('name,asc');
    request.flush({ content: PRODUCTS, page: 0, size: 100, totalElements: 2, totalPages: 1 });
    await settle();
    await fixture.whenStable();
    root = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function type(id: string, value: string): void {
    const input = root.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function chooseProduct(id: number): Promise<void> {
    const select = root.querySelector<HTMLSelectElement>('#li-product')!;
    select.value = String(id);
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function save(): Promise<void> {
    root.querySelector<HTMLButtonElement>('[data-testid="save-lot"]')!.click();
    await settle();
    await fixture.whenStable();
  }

  function errorText(field: string): string {
    return root.querySelector(`[data-testid="error-${field}"]`)?.textContent?.trim() ?? '';
  }

  const seasonInput = () => root.querySelector<HTMLInputElement>('#li-season')!;

  it('lists the products in the select', () => {
    const options = Array.from(root.querySelectorAll<HTMLOptionElement>('#li-product option')).map((o) => o.textContent?.trim());
    expect(options).toEqual(['Elige un producto', 'Copa de Malbec', 'Vino caliente']);
  });

  it('CA1 sends {productId, quantity, expiresOn, seasonName} and emits the created lot', async () => {
    await chooseProduct(9);
    type('li-qty', '12');
    type('li-exp', dayFromToday(30));
    await fixture.whenStable();
    await save();

    const request = http.expectOne(LOTS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      productId: 9,
      quantity: 12,
      expiresOn: dayFromToday(30),
      seasonName: 'Navidad',
    });
    request.flush(CREATED, { status: 201, statusText: 'Created' });
    await settle();
    expect(saved).toEqual([CREATED]);
  });

  it('CA1 preloads the product season and lets the user edit it', async () => {
    await chooseProduct(9);
    expect(seasonInput().value).toBe('Navidad');

    type('li-season', 'Fin de año');
    type('li-qty', '6');
    type('li-exp', businessToday());
    await fixture.whenStable();
    await save();

    const request = http.expectOne(LOTS_URL);
    expect(request.request.body.seasonName).toBe('Fin de año');
    expect(request.request.body.expiresOn).toBe(businessToday());
    request.flush(CREATED, { status: 201, statusText: 'Created' });
    await settle();
  });

  it('CA1 a product without season leaves it empty and sends null', async () => {
    await chooseProduct(9);
    await chooseProduct(3);
    expect(seasonInput().value).toBe('');

    type('li-qty', '4');
    type('li-exp', dayFromToday(5));
    await fixture.whenStable();
    await save();

    const request = http.expectOne(LOTS_URL);
    expect(request.request.body).toEqual({ productId: 3, quantity: 4, expiresOn: dayFromToday(5), seasonName: null });
    request.flush({ ...CREATED, productId: 3, productName: 'Copa de Malbec', seasonName: null });
    await settle();
  });

  it('CA1 without product, quantity and expiry it shows the errors and sends nothing', async () => {
    await save();

    http.expectNone(LOTS_URL);
    expect(errorText('productId')).toBe(PRODUCT_REQUIRED);
    expect(errorText('quantity')).toBe(QUANTITY_POSITIVE);
    expect(errorText('expiresOn')).toBe(EXPIRY_REQUIRED);
    expect(saved).toEqual([]);
  });

  it.each<[string, string]>([
    ['0', QUANTITY_POSITIVE],
    ['-3', QUANTITY_POSITIVE],
    ['2.5', QUANTITY_INTEGER],
  ])('CA1 quantity %s is invalid and nothing is sent', async (quantity, message) => {
    await chooseProduct(3);
    type('li-qty', quantity);
    type('li-exp', dayFromToday(10));
    await fixture.whenStable();
    await save();

    http.expectNone(LOTS_URL);
    expect(errorText('quantity')).toBe(message);
  });

  it('CA1 an expiry before today (America/Bogota) is invalid and nothing is sent', async () => {
    await chooseProduct(3);
    type('li-qty', '5');
    type('li-exp', dayFromToday(-1));
    await fixture.whenStable();
    await save();

    http.expectNone(LOTS_URL);
    expect(errorText('expiresOn')).toBe(EXPIRY_PAST);
  });

  it('CA1 a 400 with errors[] is shown next to each field', async () => {
    await chooseProduct(9);
    type('li-qty', '12');
    type('li-exp', dayFromToday(3));
    await fixture.whenStable();
    await save();

    http.expectOne(LOTS_URL).flush(
      {
        status: 400,
        title: 'Datos inválidos',
        errors: [
          { field: 'expiresOn', message: 'La fecha de vencimiento no puede ser anterior a hoy.' },
          { field: 'seasonName', message: 'La temporada admite máximo 40 caracteres.' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    await fixture.whenStable();

    expect(errorText('expiresOn')).toBe('La fecha de vencimiento no puede ser anterior a hoy.');
    expect(errorText('seasonName')).toBe('La temporada admite máximo 40 caracteres.');
    expect(saved).toEqual([]);
  });

  it('CA1 other errors (404 product) are shown as a toast', async () => {
    const toast = TestBed.inject(ToastService);
    await chooseProduct(9);
    type('li-qty', '1');
    type('li-exp', dayFromToday(3));
    await fixture.whenStable();
    await save();

    http
      .expectOne(LOTS_URL)
      .flush({ status: 404, detail: 'El producto no existe.' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(toast.toasts().map((t) => [t.kind, t.message])).toEqual([['error', 'El producto no existe.']]);
    expect(saved).toEqual([]);
  });
});
