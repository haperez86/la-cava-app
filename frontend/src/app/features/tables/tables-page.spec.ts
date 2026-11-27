import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Role } from '../../core/auth/role';
import { storeTestSession } from '../../core/auth/testing';
import { TablesPage } from './tables-page';
import { TABLES_URL } from './tables.service';

/** CA1: solo ADMIN ve la gestión de mesas; cajero y mesero siguen con el aviso (HU-08/HU-09). */
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

  it.each<Role>(['CASHIER', 'WAITER'])('CA1 %s does not see the management (placeholder, no API calls)', async (role) => {
    storeTestSession(role);
    const fixture = TestBed.createComponent(TablesPage);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('app-tables-admin')).toBeNull();
    expect(root.querySelector('[data-testid="new-table"]')).toBeNull();
    expect(root.querySelector('[data-testid="tables-placeholder"]')?.textContent).toContain('próximamente');
    http.expectNone(TABLES_URL);
  });
});
