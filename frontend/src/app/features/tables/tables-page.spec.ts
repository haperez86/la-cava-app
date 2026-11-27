import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Role } from '../../core/auth/role';
import { storeTestSession } from '../../core/auth/testing';
import { TablesPage } from './tables-page';
import { TABLES_URL } from './tables.service';

/** HU-07 CA1: solo ADMIN ve la gestión; HU-08: el mesero ve "Mis mesas"; el cajero sigue con el aviso (HU-09). */
describe('TablesPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [TablesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('CA1 ADMIN sees the tables management', async () => {
    storeTestSession('ADMIN');
    const fixture = TestBed.createComponent(TablesPage);
    await fixture.whenStable();
    http.expectOne(TABLES_URL).flush([]);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('app-tables-admin')).not.toBeNull();
    expect(root.querySelector('[data-testid="new-table"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="tables-placeholder"]')).toBeNull();
  });

  it('CA1 CASHIER does not see the management (placeholder, no API calls)', async () => {
    storeTestSession('CASHIER');
    const fixture = TestBed.createComponent(TablesPage);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('app-tables-admin')).toBeNull();
    expect(root.querySelector('app-waiter-tables')).toBeNull();
    expect(root.querySelector('[data-testid="new-table"]')).toBeNull();
    expect(root.querySelector('[data-testid="tables-placeholder"]')?.textContent).toContain('próximamente');
    http.expectNone(TABLES_URL);
  });

  it('HU-08 only WAITER sees "Mis mesas" with the QR scanner', async () => {
    storeTestSession('WAITER');
    const fixture = TestBed.createComponent(TablesPage);
    await fixture.whenStable();
    http.expectOne(TABLES_URL).flush([]);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('app-waiter-tables')).not.toBeNull();
    expect(root.querySelector('h2')?.textContent).toBe('Mis mesas');
    expect(root.querySelector('[data-testid="scan-qr"]')).not.toBeNull();
    expect(root.querySelector('app-tables-admin')).toBeNull();
    expect(root.querySelector('[data-testid="tables-placeholder"]')).toBeNull();
  });

  it.each<Role>(['ADMIN', 'CASHIER'])('HU-08 %s does not see "Mis mesas"', async (role) => {
    storeTestSession(role);
    const fixture = TestBed.createComponent(TablesPage);
    await fixture.whenStable();
    http.match(TABLES_URL).forEach((req) => req.flush([]));
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('app-waiter-tables')).toBeNull();
  });
});
