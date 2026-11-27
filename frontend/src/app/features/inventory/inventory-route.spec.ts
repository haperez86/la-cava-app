import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { Role } from '../../core/auth/role';
import { storeTestSession } from '../../core/auth/testing';
import { InventoryPage } from './inventory-page';

/** D32: /inventario "Inventario" solo para ADMIN; los demás roles vuelven a su pantalla inicial. */
describe('/inventario route', () => {
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

  it('LCV-HU-04 ADMIN opens Inventario', async () => {
    storeTestSession('ADMIN');
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/inventario');
    harness.detectChanges();

    expect(harness.fixture.debugElement.query((el) => el.componentInstance instanceof InventoryPage)).not.toBeNull();
    expect(TestBed.inject(Router).url).toBe('/inventario');
  });

  it.each<[Role, string]>([
    ['CASHIER', '/dashboard'],
    ['WAITER', '/mesas'],
    ['KITCHEN', '/preparacion'],
  ])('LCV-HU-04 %s is redirected to %s', async (role, home) => {
    storeTestSession(role);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/inventario');

    expect(TestBed.inject(Router).url).toBe(home);
  });
});
