import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { Role } from '../../core/auth/role';
import { storeTestSession } from '../../core/auth/testing';
import { CATEGORIES_URL, PRODUCTS_URL } from '../menu/products.service';
import { TABLES_URL } from '../tables/tables.service';
import { ORDERS_EMPTY, OrdersPage } from './orders-page';
import { ORDERS_URL, WAITERS_URL } from './orders.service';

/** D38: /pedidos "Pedidos" solo para ADMIN y CASHIER; los demás roles vuelven a su pantalla inicial. */
describe('/pedidos route', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
    localStorage.clear();
  });

  it.each<Role>(['ADMIN', 'CASHIER'])('%s opens Pedidos with a neutral empty state', async (role) => {
    storeTestSession(role);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/pedidos');
    harness.detectChanges();
    http.expectOne((r) => r.url === ORDERS_URL).flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    http.expectOne(`${ORDERS_URL}/summary`).flush({ all: 0, PENDING: 0, IN_PREPARATION: 0, READY: 0, DELIVERED: 0, CLOSED: 0, CANCELLED: 0 });
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(harness.fixture.debugElement.query((el) => el.componentInstance instanceof OrdersPage)).not.toBeNull();
    expect(TestBed.inject(Router).url).toBe('/pedidos');
    const root = harness.fixture.nativeElement as HTMLElement;
    expect(root.querySelector('h2')?.textContent).toBe('Pedidos');
    expect(root.querySelector('[data-testid="orders-empty"]')?.textContent?.trim()).toBe(ORDERS_EMPTY);
  });

  it('CA4 "Nuevo pedido" opens the order modal asking for table and waiter', async () => {
    storeTestSession('CASHIER');
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/pedidos');
    harness.detectChanges();
    const root = harness.fixture.nativeElement as HTMLElement;

    root.querySelector<HTMLButtonElement>('[data-testid="new-order"]')!.click();
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect(root.querySelector('[data-testid="order-dialog"]')).not.toBeNull();
    expect(root.querySelector('#order-table')).not.toBeNull();
    expect(root.querySelector('#order-waiter')).not.toBeNull();
    expect(http.match((r) => r.url === PRODUCTS_URL)).toHaveLength(1);
    expect(http.match(CATEGORIES_URL)).toHaveLength(1);
    expect(http.match(TABLES_URL)).toHaveLength(1);
    expect(http.match(WAITERS_URL)).toHaveLength(1);
  });

  it.each<[Role, string]>([
    ['WAITER', '/mesas'],
    ['KITCHEN', '/preparacion'],
  ])('%s is redirected to %s', async (role, home) => {
    storeTestSession(role);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/pedidos');

    expect(TestBed.inject(Router).url).toBe(home);
  });
});
