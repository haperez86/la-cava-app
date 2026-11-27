import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { businessToday } from '../../shared/dates/business-date';
import { Product } from './product.model';
import {
  LOT_EXPIRY_PAST,
  LOT_EXPIRY_REQUIRED,
  PRICE_REQUIRED,
  ProductForm,
  SEASON_UNTIL_REQUIRED,
} from './product-form';
import { PRODUCTS_URL } from './products.service';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

/** Fecha 'YYYY-MM-DD' desplazada `days` días desde hoy (zona del negocio). */
function dayFromToday(days: number): string {
  return businessToday(new Date(Date.now() + days * 86_400_000));
}

const EXISTING: Product = {
  id: 7,
  name: 'Copa de Malbec',
  categoryId: 2,
  categoryName: 'Vinos',
  price: 22000,
  description: 'Copa de la casa',
  icon: '🍷',
  minStock: 6,
  active: true,
  seasonName: null,
  seasonUntil: null,
  available: 18,
  state: 'ON_MENU',
  orderable: true,
  blockReason: null,
};

describe('ProductForm', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ProductForm>;
  let root: HTMLElement;
  let saved: Product[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductForm],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(product: Product | null = null): Promise<void> {
    fixture = TestBed.createComponent(ProductForm);
    fixture.componentRef.setInput('product', product);
    fixture.componentRef.setInput('categories', [
      { id: 1, name: 'Cervezas' },
      { id: 2, name: 'Vinos' },
    ]);
    saved = [];
    fixture.componentInstance.saved.subscribe((p) => saved.push(p));
    await fixture.whenStable();
    root = fixture.nativeElement as HTMLElement;
  }

  function type(id: string, value: string): void {
    const input = root.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function save(): Promise<void> {
    root.querySelector<HTMLButtonElement>('[data-testid="save-product"]')!.click();
    await settle();
    await fixture.whenStable();
  }

  function errorText(field: string): string {
    return root.querySelector(`[data-testid="error-${field}"]`)?.textContent?.trim() ?? '';
  }

  function fillRequired(): void {
    type('pf-name', '  Tabla de quesos  ');
    type('pf-category', 'Tablas');
    type('pf-price', '38000');
    type('pf-min', '4');
    type('pf-icon', '🧀');
    type('pf-desc', 'Quesos madurados');
  }

  it('CA1 creates a product and sends a new typed category as categoryName (CA4: no initialLot without quantity)', async () => {
    await render();
    expect(root.querySelector('#pf-category-list')?.querySelectorAll('option').length).toBe(2);
    fillRequired();
    await save();

    const req = http.expectOne(PRODUCTS_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      name: 'Tabla de quesos',
      categoryName: 'Tablas',
      price: 38000,
      description: 'Quesos madurados',
      icon: '🧀',
      minStock: 4,
      seasonName: null,
      seasonUntil: null,
    });
    expect('initialLot' in req.request.body).toBe(false);
    const created = { ...EXISTING, id: 9, name: 'Tabla de quesos' };
    req.flush(created, { status: 201, statusText: 'Created' });
    await settle();

    expect(saved).toEqual([created]);
  });

  it('CA1 edits a product with PUT and without the initial lot section', async () => {
    await render(EXISTING);
    expect(root.querySelector<HTMLInputElement>('#pf-name')!.value).toBe('Copa de Malbec');
    expect(root.querySelector('[data-testid="initial-lot"]')).toBeNull();

    type('pf-price', '24000');
    type('pf-season', 'Navidad');
    type('pf-until', '2099-12-31');
    await save();

    const req = http.expectOne(`${PRODUCTS_URL}/7`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      name: 'Copa de Malbec',
      categoryName: 'Vinos',
      price: 24000,
      description: 'Copa de la casa',
      icon: '🍷',
      minStock: 6,
      seasonName: 'Navidad',
      seasonUntil: '2099-12-31',
    });
    req.flush({ ...EXISTING, price: 24000 });
    await settle();
    expect(saved.length).toBe(1);
  });

  it('CA1 shows the 409 duplicate-name error on the name field', async () => {
    await render();
    fillRequired();
    await save();

    http
      .expectOne(PRODUCTS_URL)
      .flush(
        { status: 409, detail: 'Ya existe un producto con ese nombre.' },
        { status: 409, statusText: 'Conflict' },
      );
    await settle();
    await fixture.whenStable();

    expect(errorText('name')).toBe('Ya existe un producto con ese nombre.');
    expect(saved).toEqual([]);
  });

  it('a 409 other than duplicate name goes to a toast, not to the name field', async () => {
    const toast = vi.spyOn(TestBed.inject(ToastService), 'error');
    await render();
    fillRequired();
    await save();

    http
      .expectOne(PRODUCTS_URL)
      .flush({ status: 409, detail: 'Conflicto de versión.' }, { status: 409, statusText: 'Conflict' });
    await settle();
    await fixture.whenStable();

    expect(errorText('name')).toBe('');
    expect(toast).toHaveBeenCalledWith('Conflicto de versión.');
  });

  it('rejects a decimal initial lot quantity with a clear message and does not submit', async () => {
    await render();
    fillRequired();
    type('pf-qty', '2.5');
    type('pf-exp', dayFromToday(30));
    await save();

    http.expectNone(PRODUCTS_URL);
    expect(errorText('lotQuantity')).toBe('La cantidad debe ser un número entero.');
  });

  it('maps 400 errors[] to their fields and shows unknown ones as a toast', async () => {
    const toast = vi.spyOn(TestBed.inject(ToastService), 'error');
    await render();
    fillRequired();
    await save();

    http.expectOne(PRODUCTS_URL).flush(
      {
        status: 400,
        detail: 'Hay datos inválidos.',
        errors: [
          { field: 'price', message: 'El precio debe ser mayor que cero.' },
          { field: 'initialLot.expiresOn', message: 'La fecha de vencimiento es obligatoria.' },
          { field: 'other', message: 'Otro problema.' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    await fixture.whenStable();

    expect(errorText('price')).toBe('El precio debe ser mayor que cero.');
    expect(errorText('lotExpiresOn')).toBe('La fecha de vencimiento es obligatoria.');
    expect(toast).toHaveBeenCalledWith('Otro problema.');
  });

  it('shows other server errors as a toast', async () => {
    const toast = vi.spyOn(TestBed.inject(ToastService), 'error');
    await render();
    fillRequired();
    await save();

    http.expectOne(PRODUCTS_URL).flush({ status: 500, detail: 'Error interno.' }, { status: 500, statusText: 'Error' });
    await settle();

    expect(toast).toHaveBeenCalledWith('Error interno.');
  });

  it('does not call the API with the required fields empty', async () => {
    await render();
    await save();

    http.expectNone(PRODUCTS_URL);
    expect(errorText('name')).toBe('Ingresa el nombre del producto.');
    expect(errorText('categoryName')).toBe('Indica la categoría.');
    expect(errorText('price')).toBe(PRICE_REQUIRED);
  });

  it('rejects a price of zero', async () => {
    await render();
    fillRequired();
    type('pf-price', '0');
    await save();

    http.expectNone(PRODUCTS_URL);
    expect(errorText('price')).toBe(PRICE_REQUIRED);
  });

  it('enforces the docs/02 max lengths at their boundaries (name 60, category 40, description 120, icon 16, season 40)', async () => {
    await render();
    fillRequired();
    type('pf-name', 'n'.repeat(61));
    type('pf-category', 'c'.repeat(41));
    type('pf-desc', 'd'.repeat(121));
    type('pf-icon', 'i'.repeat(17));
    type('pf-season', 's'.repeat(41));
    type('pf-until', '2099-01-06');
    await save();

    http.expectNone(PRODUCTS_URL);
    expect(errorText('name')).toBe('Máximo 60 caracteres.');
    expect(errorText('categoryName')).toBe('Máximo 40 caracteres.');
    expect(errorText('description')).toBe('Máximo 120 caracteres.');
    expect(errorText('icon')).toBe('Máximo 16 caracteres.');
    expect(errorText('seasonName')).toBe('Máximo 40 caracteres.');

    type('pf-name', 'n'.repeat(60));
    type('pf-category', 'c'.repeat(40));
    type('pf-desc', 'd'.repeat(120));
    type('pf-icon', 'i'.repeat(16));
    type('pf-season', 's'.repeat(40));
    await save();

    const req = http.expectOne(PRODUCTS_URL);
    expect(req.request.body.name).toHaveLength(60);
    expect(req.request.body.categoryName).toHaveLength(40);
    expect(req.request.body.description).toHaveLength(120);
    expect(req.request.body.icon).toHaveLength(16);
    expect(req.request.body.seasonName).toHaveLength(40);
    req.flush(EXISTING, { status: 201, statusText: 'Created' });
    await settle();
  });

  it('CA2 is invalid with a season name and no "disponible hasta" date', async () => {
    await render();
    fillRequired();
    type('pf-season', 'Navidad');
    await save();

    http.expectNone(PRODUCTS_URL);
    expect(errorText('seasonUntil')).toBe(SEASON_UNTIL_REQUIRED);

    type('pf-until', '2099-01-06');
    await save();
    const req = http.expectOne(PRODUCTS_URL);
    expect(req.request.body.seasonName).toBe('Navidad');
    expect(req.request.body.seasonUntil).toBe('2099-01-06');
    req.flush(EXISTING);
    await settle();
  });

  it('CA4 is invalid with a quantity and no expiry date', async () => {
    await render();
    fillRequired();
    type('pf-qty', '10');
    await save();

    http.expectNone(PRODUCTS_URL);
    expect(errorText('lotExpiresOn')).toBe(LOT_EXPIRY_REQUIRED);
  });

  it('CA4 is invalid with an expiry date before today (America/Bogota)', async () => {
    await render();
    fillRequired();
    type('pf-qty', '10');
    type('pf-exp', dayFromToday(-1));
    await save();

    http.expectNone(PRODUCTS_URL);
    expect(errorText('lotExpiresOn')).toBe(LOT_EXPIRY_PAST);
  });

  it('CA4 sends initialLot with a quantity and an expiry date of today or later', async () => {
    await render();
    fillRequired();
    type('pf-qty', '10');
    type('pf-exp', businessToday());
    await save();

    const req = http.expectOne(PRODUCTS_URL);
    expect(req.request.body.initialLot).toEqual({ quantity: 10, expiresOn: businessToday() });
    req.flush(EXISTING, { status: 201, statusText: 'Created' });
    await settle();
  });
});
