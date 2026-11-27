import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService, LOGIN_URL, SESSION_STORAGE_KEY } from './auth.service';
import { storeTestSession } from './testing';

describe('AuthService', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  function create(): { auth: AuthService; http: HttpTestingController } {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    return { auth: TestBed.inject(AuthService), http: TestBed.inject(HttpTestingController) };
  }

  it('login stores the session under lacava.session and exposes user and role', async () => {
    const { auth, http } = create();
    const promise = auth.login('admin', 'admin123');
    const session = {
      token: 'jwt',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      user: { id: 1, fullName: 'Administrador demo', role: 'ADMIN' },
    };
    http.expectOne(LOGIN_URL).flush(session);
    await promise;

    expect(auth.role()).toBe('ADMIN');
    expect(auth.token()).toBe('jwt');
    expect(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!)).toEqual(session);
  });

  it('restores a stored session and logout clears it', () => {
    storeTestSession('WAITER');
    const { auth } = create();
    expect(auth.isSessionValid()).toBe(true);
    expect(auth.role()).toBe('WAITER');

    auth.logout();
    expect(auth.hasSession()).toBe(false);
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('treats an expired session as invalid', () => {
    storeTestSession('ADMIN', new Date(Date.now() - 1000).toISOString());
    const { auth } = create();
    expect(auth.hasSession()).toBe(true);
    expect(auth.isSessionValid()).toBe(false);
  });

  it('survives a storage that throws', () => {
    const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });
    const { auth } = create();
    expect(auth.hasSession()).toBe(false);
    spy.mockRestore();
  });
});
