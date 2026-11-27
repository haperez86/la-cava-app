import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { Role } from '../../core/auth/role';
import { storeTestSession } from '../../core/auth/testing';
import { MenuPage } from './menu-page';

/** D25: /menu "Menú y productos" solo para ADMIN; los demás roles vuelven a su pantalla inicial. */
describe('/menu route', () => {
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

  it('ADMIN opens Menú y productos', async () => {
    storeTestSession('ADMIN');
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/menu');
    harness.detectChanges();

    expect(harness.fixture.debugElement.query((el) => el.componentInstance instanceof MenuPage)).not.toBeNull();
    expect(TestBed.inject(Router).url).toBe('/menu');
  });

  it.each<[Role, string]>([
    ['CASHIER', '/dashboard'],
    ['WAITER', '/mesas'],
    ['KITCHEN', '/preparacion'],
  ])('%s is redirected to %s', async (role, home) => {
    storeTestSession(role);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/menu');

    expect(TestBed.inject(Router).url).toBe(home);
  });
});
