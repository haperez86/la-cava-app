import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { OutputResult, OutputType, StockProductOption } from './inventory.model';
import { OUTPUTS_URL, PRODUCTS_URL } from './inventory.service';
import { PRODUCT_REQUIRED, QUANTITY_INTEGER, QUANTITY_POSITIVE } from './lot-in-form';
import { LotOutForm, NOTE_REQUIRED, quantityOverAvailable } from './lot-out-form';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

const PRODUCTS: StockProductOption[] = [
  { id: 3, name: 'Copa de Malbec', seasonName: null, available: 12 },
  { id: 9, name: 'Vino caliente', seasonName: 'Navidad', available: 4 },
];

const RESULT: OutputResult = {
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

describe('LotOutForm', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<LotOutForm>;
  let root: HTMLElement;
  let saved: OutputResult[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [LotOutForm],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LotOutForm);
    saved = [];
    fixture.componentInstance.saved.subscribe((result) => saved.push(result));
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
    const input = root.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function choose(id: string, value: string): Promise<void> {
    const select = root.querySelector<HTMLSelectElement>(`#${id}`)!;
    select.value = value;
    select.dispatchEvent(new Event('input'));
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function fill(productId: number, quantity: string, note: string): Promise<void> {
    await choose('lo-product', String(productId));
    type('lo-qty', quantity);
    type('lo-note', note);
    await fixture.whenStable();
  }

  async function save(): Promise<void> {
    root.querySelector<HTMLButtonElement>('[data-testid="save-output"]')!.click();
    await settle();
    await fixture.whenStable();
  }

  function errorText(field: string): string {
    return root.querySelector(`[data-testid="error-${field}"]`)?.textContent?.trim() ?? '';
  }

  it('lists the 4 output types with their Spanish labels', () => {
    const labels = Array.from(root.querySelectorAll<HTMLOptionElement>('#lo-type option')).map((o) => o.textContent?.trim());
    expect(labels).toEqual(['Merma', 'Producto dañado', 'Consumo interno', 'Ajuste']);
  });

  it('CA2 shows the available quantity of the chosen product', async () => {
    expect(root.querySelector('[data-testid="available"]')).toBeNull();
    await choose('lo-product', '3');
    expect(root.querySelector('[data-testid="available"]')?.textContent?.trim()).toBe('Disponible: 12');
    await choose('lo-product', '9');
    expect(root.querySelector('[data-testid="available"]')?.textContent?.trim()).toBe('Disponible: 4');
  });

  it('CA2 sends {productId, quantity, type, note} and emits the FEFO result', async () => {
    await fill(3, '5', '  Botellas rotas al descargar  ');
    await save();

    const request = http.expectOne(OUTPUTS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      productId: 3,
      quantity: 5,
      type: 'WASTE_OUT',
      note: 'Botellas rotas al descargar',
    });
    request.flush(RESULT);
    await settle();
    expect(saved).toEqual([RESULT]);
  });

  it.each<[string, OutputType]>([
    ['WASTE_OUT', 'WASTE_OUT'],
    ['DAMAGED_OUT', 'DAMAGED_OUT'],
    ['INTERNAL_USE_OUT', 'INTERNAL_USE_OUT'],
    ['ADJUSTMENT_OUT', 'ADJUSTMENT_OUT'],
  ])('CA2 the type option %s sends type %s', async (option, expected) => {
    await fill(9, '1', 'Motivo');
    await choose('lo-type', option);
    await save();

    const request = http.expectOne(OUTPUTS_URL);
    expect(request.request.body.type).toBe(expected);
    request.flush({ ...RESULT, type: expected });
    await settle();
  });

  it('CA2 an empty observation is invalid and nothing is sent', async () => {
    await fill(3, '2', '   ');
    await save();

    http.expectNone(OUTPUTS_URL);
    expect(errorText('note')).toBe(NOTE_REQUIRED);
    expect(saved).toEqual([]);
  });

  it('CA2 an observation of 140 characters is accepted', async () => {
    const note = 'a'.repeat(140);
    await fill(3, '1', note);
    await save();

    const request = http.expectOne(OUTPUTS_URL);
    expect(request.request.body.note).toBe(note);
    expect(errorText('note')).toBe('');
    request.flush(RESULT);
    await settle();
  });

  it('CA2 an observation of 141 characters is invalid and nothing is sent', async () => {
    await fill(3, '1', 'a'.repeat(141));
    await save();

    http.expectNone(OUTPUTS_URL);
    expect(errorText('note')).toBe('Máximo 140 caracteres.');
    expect(saved).toEqual([]);
  });

  it('CA2 without product, quantity and observation it shows the errors and sends nothing', async () => {
    await save();

    http.expectNone(OUTPUTS_URL);
    expect(errorText('productId')).toBe(PRODUCT_REQUIRED);
    expect(errorText('quantity')).toBe(QUANTITY_POSITIVE);
    expect(errorText('note')).toBe(NOTE_REQUIRED);
  });

  it.each<[string, string]>([
    ['0', QUANTITY_POSITIVE],
    ['2.5', QUANTITY_INTEGER],
    ['5', quantityOverAvailable(4)],
  ])('CA2 quantity %s (available 4) is invalid and nothing is sent', async (quantity, message) => {
    await fill(9, quantity, 'Merma de la noche');
    await save();

    http.expectNone(OUTPUTS_URL);
    expect(errorText('quantity')).toBe(message);
    expect(saved).toEqual([]);
  });

  it('CA2 a quantity equal to the available one is valid', async () => {
    await fill(9, '4', 'Ajuste de conteo');
    await choose('lo-type', 'ADJUSTMENT_OUT');
    await save();

    const request = http.expectOne(OUTPUTS_URL);
    expect(request.request.body).toEqual({ productId: 9, quantity: 4, type: 'ADJUSTMENT_OUT', note: 'Ajuste de conteo' });
    request.flush({ ...RESULT, productId: 9, productName: 'Vino caliente', quantity: 4, available: 0 });
    await settle();
  });

  it('CA2 a 400 on quantity from the server is shown in the field', async () => {
    await fill(3, '5', 'Merma');
    await save();

    http.expectOne(OUTPUTS_URL).flush(
      { status: 400, title: 'Datos inválidos', errors: [{ field: 'quantity', message: 'Solo hay 3 disponibles.' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    await fixture.whenStable();

    expect(errorText('quantity')).toBe('Solo hay 3 disponibles.');
    expect(saved).toEqual([]);
  });

  it('CA2 other errors (404 product) are shown as a toast', async () => {
    const toast = TestBed.inject(ToastService);
    await fill(3, '1', 'Merma');
    await save();

    http
      .expectOne(OUTPUTS_URL)
      .flush({ status: 404, detail: 'El producto no existe.' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(toast.toasts().map((t) => [t.kind, t.message])).toEqual([['error', 'El producto no existe.']]);
    expect(saved).toEqual([]);
  });
});
