import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { Page } from '../../shared/http/page';
import { MENU_PAGE_SIZE, MenuPage } from './menu-page';
import { Product } from './product.model';
import { CATEGORIES_URL, PRODUCTS_URL } from './products.service';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

const MALBEC: Product = {
  id: 1,
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

const PONCHE: Product = {
  ...MALBEC,
  id: 2,
  name: 'Ponche navideño',
  categoryName: 'Cócteles',
  price: 18500,
  active: false,
  seasonName: 'Navidad',
  seasonUntil: '2026-12-31',
  available: 0,
  state: 'INACTIVE',
  orderable: false,
  blockReason: 'Retirado de la carta',
};

function page(content: Product[], totalElements = content.length): Page<Product> {
  return { content, page: 0, size: MENU_PAGE_SIZE, totalElements, totalPages: Math.ceil(totalElements / MENU_PAGE_SIZE) };
}

describe('MenuPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<MenuPage>;
  let root: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MenuPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function expectList(): TestRequest {
    return http.expectOne((r) => r.url === PRODUCTS_URL && r.method === 'GET');
  }

  async function flushAndRender(): Promise<void> {
    await settle();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function render(products: Product[] = [MALBEC, PONCHE]): Promise<void> {
    fixture = TestBed.createComponent(MenuPage);
    root = fixture.nativeElement as HTMLElement;
    expectList().flush(page(products));
    http.expectOne(CATEGORIES_URL).flush([
      { id: 1, name: 'Cócteles' },
      { id: 2, name: 'Vinos' },
    ]);
    await flushAndRender();
  }

  function cards(): HTMLElement[] {
    return Array.from(root.querySelectorAll<HTMLElement>('[data-testid="product"]'));
  }

  it('lists products with icon, category, COP price without decimals, stock, season and state pill', async () => {
    fixture = TestBed.createComponent(MenuPage);
    root = fixture.nativeElement as HTMLElement;
    const req = expectList();
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe(String(MENU_PAGE_SIZE));
    expect(req.request.params.get('sort')).toBe('name,asc');
    expect(req.request.params.has('q')).toBe(false);
    req.flush(page([MALBEC, PONCHE]));
    http.expectOne(CATEGORIES_URL).flush([]);
    await flushAndRender();

    const [malbec, ponche] = cards();
    expect(malbec.textContent).toContain('🍷');
    expect(malbec.textContent).toContain('Vinos');
    expect(malbec.textContent).toContain('$ 22.000');
    expect(malbec.querySelector('[data-testid="product-available"]')?.textContent).toContain('18 disponible(s)');
    expect(malbec.querySelector('[data-testid="product-state"]')?.textContent?.trim()).toBe('En la carta');
    expect(malbec.querySelector('[data-testid="product-season"]')).toBeNull();
    expect(ponche.querySelector('[data-testid="product-season"]')?.textContent).toContain('Navidad · hasta 31/12/2026');
    expect(ponche.querySelector('[data-testid="product-state"]')?.textContent?.trim()).toBe('Retirado de la carta');
    expect(root.querySelector('[data-testid="pagination-summary"]')?.textContent).toContain('Mostrando 1–2 de 2');
  });

  it('searches by text and filters by category from the first page', async () => {
    await render();

    const search = root.querySelector<HTMLInputElement>('#menu-search')!;
    search.value = 'malbec';
    search.dispatchEvent(new Event('input'));
    const byText = expectList();
    expect(byText.request.params.get('q')).toBe('malbec');
    expect(byText.request.params.get('page')).toBe('0');
    byText.flush(page([MALBEC]));
    await flushAndRender();
    expect(cards().length).toBe(1);

    const select = root.querySelector<HTMLSelectElement>('#menu-category')!;
    select.value = '2';
    select.dispatchEvent(new Event('change'));
    const byCategory = expectList();
    expect(byCategory.request.params.get('categoryId')).toBe('2');
    expect(byCategory.request.params.get('q')).toBe('malbec');
    byCategory.flush(page([MALBEC]));
    await flushAndRender();
  });

  it('requests the next page from the paginator', async () => {
    fixture = TestBed.createComponent(MenuPage);
    root = fixture.nativeElement as HTMLElement;
    expectList().flush(page([MALBEC], 30));
    http.expectOne(CATEGORIES_URL).flush([]);
    await flushAndRender();

    root.querySelector<HTMLButtonElement>('[data-testid="pagination-next"]')!.click();
    const next = expectList();
    expect(next.request.params.get('page')).toBe('1');
    next.flush({ ...page([PONCHE], 30), page: 1 });
    await flushAndRender();
  });

  it('CA3 "Retirar de la carta" calls PATCH {active:false}, updates the card and confirms with a toast', async () => {
    const toast = vi.spyOn(TestBed.inject(ToastService), 'success');
    await render();
    const toggle = cards()[0].querySelector<HTMLButtonElement>('[data-testid="toggle-active"]')!;
    expect(toggle.textContent?.trim()).toBe('Retirar de la carta');

    toggle.click();
    const req = http.expectOne(`${PRODUCTS_URL}/1/active`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ active: false });
    req.flush({ ...MALBEC, active: false });
    await settle();
    expectList().flush(page([{ ...MALBEC, active: false, state: 'INACTIVE', orderable: false, blockReason: 'Retirado de la carta' }, PONCHE]));
    await flushAndRender();

    expect(cards()[0].querySelector('[data-testid="product-state"]')?.textContent?.trim()).toBe('Retirado de la carta');
    expect(cards()[0].querySelector('[data-testid="toggle-active"]')?.textContent?.trim()).toBe('Volver a la carta');
    expect(toast).toHaveBeenCalledWith('«Copa de Malbec» se retiró de la carta.');
  });

  it('CA3 "Volver a la carta" calls PATCH {active:true} and marks the product active', async () => {
    const toast = vi.spyOn(TestBed.inject(ToastService), 'success');
    await render();

    cards()[1].querySelector<HTMLButtonElement>('[data-testid="toggle-active"]')!.click();
    const req = http.expectOne(`${PRODUCTS_URL}/2/active`);
    expect(req.request.body).toEqual({ active: true });
    req.flush({ ...PONCHE, active: true });
    await settle();
    expectList().flush(page([MALBEC, { ...PONCHE, active: true, state: 'OUT_OF_STOCK', blockReason: 'Agotado' }]));
    await flushAndRender();

    expect(cards()[1].querySelector('[data-testid="product-state"]')?.textContent?.trim()).toBe('Agotado');
    expect(toast).toHaveBeenCalledWith('«Ponche navideño» volvió a la carta.');
  });

  it('CA3 keeps the state and shows a toast when the PATCH fails', async () => {
    const toast = vi.spyOn(TestBed.inject(ToastService), 'error');
    await render();

    cards()[0].querySelector<HTMLButtonElement>('[data-testid="toggle-active"]')!.click();
    http
      .expectOne(`${PRODUCTS_URL}/1/active`)
      .flush({ status: 403, detail: 'No tienes permiso.' }, { status: 403, statusText: 'Forbidden' });
    await flushAndRender();

    expect(cards()[0].querySelector('[data-testid="product-state"]')?.textContent?.trim()).toBe('En la carta');
    expect(toast).toHaveBeenCalledWith('No tienes permiso.');
  });

  it('"Nuevo producto" opens the create form; after saving it closes, confirms and reloads list and categories', async () => {
    const toast = vi.spyOn(TestBed.inject(ToastService), 'success');
    await render();

    root.querySelector<HTMLButtonElement>('[data-testid="new-product"]')!.click();
    await flushAndRender();
    expect(root.querySelector('#product-modal-title')?.textContent).toContain('Nuevo producto');
    expect(root.querySelector('[data-testid="initial-lot"]')).not.toBeNull();

    const set = (id: string, value: string) => {
      const input = root.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    set('pf-name', 'Tabla mixta');
    set('pf-category', 'Tablas');
    set('pf-price', '45000');
    root.querySelector<HTMLButtonElement>('[data-testid="save-product"]')!.click();
    await settle();
    http.expectOne((r) => r.url === PRODUCTS_URL && r.method === 'POST').flush({ ...MALBEC, id: 3, name: 'Tabla mixta' });
    await settle();

    expectList().flush(page([MALBEC, PONCHE]));
    http.expectOne(CATEGORIES_URL).flush([]);
    await flushAndRender();
    expect(root.querySelector('[role="dialog"]')).toBeNull();
    expect(toast).toHaveBeenCalledWith('Producto «Tabla mixta» creado.');
  });

  it('"Editar" opens the edit form prefilled and without the initial lot', async () => {
    await render();

    cards()[0].querySelector<HTMLButtonElement>('[data-testid="edit-product"]')!.click();
    await flushAndRender();

    expect(root.querySelector('#product-modal-title')?.textContent).toContain('Editar producto');
    expect(root.querySelector<HTMLInputElement>('#pf-name')!.value).toBe('Copa de Malbec');
    expect(root.querySelector('[data-testid="initial-lot"]')).toBeNull();

    root.querySelector<HTMLButtonElement>('.close')!.click();
    await flushAndRender();
    expect(root.querySelector('[role="dialog"]')).toBeNull();
  });

  it('CA1 each card shows the pill for its state: En la carta, Retirado de la carta, Fuera de temporada, Agotado', async () => {
    await render([
      MALBEC,
      PONCHE,
      { ...MALBEC, id: 3, name: 'Sangría', state: 'OUT_OF_SEASON', orderable: false, blockReason: 'Fuera de temporada' },
      { ...MALBEC, id: 4, name: 'Cerveza', available: 0, state: 'OUT_OF_STOCK', orderable: false, blockReason: 'Agotado' },
    ]);
    const pills = cards().map((c) => c.querySelector('[data-testid="product-state"]')!);
    expect(pills.map((p) => p.textContent?.trim())).toEqual([
      'En la carta',
      'Retirado de la carta',
      'Fuera de temporada',
      'Agotado',
    ]);
    expect(pills[0].classList.contains('danger')).toBe(false);
    pills.slice(1).forEach((p) => expect(p.classList.contains('danger')).toBe(true));
  });

  it('CA2 each state option sends its `state` value from page 0 and "Todos" sends none', async () => {
    await render();
    const select = root.querySelector<HTMLSelectElement>('#menu-state')!;
    expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual([
      'Todos',
      'En la carta',
      'Agotados',
      'De temporada',
      'Fuera de temporada',
      'Inactivos',
    ]);
    for (const value of ['ON_MENU', 'OUT_OF_STOCK', 'SEASONAL', 'OUT_OF_SEASON', 'INACTIVE']) {
      select.value = value;
      select.dispatchEvent(new Event('change'));
      const req = expectList();
      expect(req.request.params.get('state')).toBe(value);
      expect(req.request.params.get('page')).toBe('0');
      req.flush(page([MALBEC]));
      await flushAndRender();
    }
    select.value = '';
    select.dispatchEvent(new Event('change'));
    const all = expectList();
    expect(all.request.params.has('state')).toBe(false);
    all.flush(page([MALBEC]));
    await flushAndRender();
  });

  it('CA2 changing the state filter returns to page 0 and combines with q and category', async () => {
    fixture = TestBed.createComponent(MenuPage);
    root = fixture.nativeElement as HTMLElement;
    expectList().flush(page([MALBEC], 30));
    http.expectOne(CATEGORIES_URL).flush([{ id: 2, name: 'Vinos' }]);
    await flushAndRender();

    const search = root.querySelector<HTMLInputElement>('#menu-search')!;
    search.value = 'mal';
    search.dispatchEvent(new Event('input'));
    expectList().flush(page([MALBEC], 30));
    await flushAndRender();
    const category = root.querySelector<HTMLSelectElement>('#menu-category')!;
    category.value = '2';
    category.dispatchEvent(new Event('change'));
    expectList().flush(page([MALBEC], 30));
    await flushAndRender();

    root.querySelector<HTMLButtonElement>('[data-testid="pagination-next"]')!.click();
    expectList().flush({ ...page([MALBEC], 30), page: 1 });
    await flushAndRender();

    const select = root.querySelector<HTMLSelectElement>('#menu-state')!;
    select.value = 'OUT_OF_STOCK';
    select.dispatchEvent(new Event('change'));
    const req = expectList();
    expect(req.request.params.get('state')).toBe('OUT_OF_STOCK');
    expect(req.request.params.get('q')).toBe('mal');
    expect(req.request.params.get('categoryId')).toBe('2');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
    await flushAndRender();
  });
});
