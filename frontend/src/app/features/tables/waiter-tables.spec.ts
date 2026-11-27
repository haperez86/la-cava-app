import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { storeTestSession } from '../../core/auth/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { QrScanError, QrScanFailure, QrScanSession, QrScanner } from '../../shared/qr-scanner/qr-scanner';
import { CAMERA_FAILURE, INVALID_TABLE_CODE, PIN_INVALID } from './qr-scan-dialog';
import { DiningTable, ResolvedTable } from './tables.model';
import { RESOLVE_TABLE_URL, TABLES_URL } from './tables.service';
import { EMPTY_ACCOUNT, LOADED_BY, ORDERS_PENDING_NOTE, SCAN_TO_OPEN, WaiterTables } from './waiter-tables';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

const TABLES: DiningTable[] = [
  { id: 13, number: 3, active: true },
  { id: 12, number: 2, active: false },
  { id: 11, number: 1, active: true },
];

const TABLE_4: ResolvedTable = { id: 40, number: 4, account: { orders: [], total: 0 } };

const INVALID_CODE_PROBLEM = { status: 404, statusText: 'Not Found' };

/** Escáner simulado: guarda el callback para "leer" un código y cuenta cuántas veces se apagó la cámara. */
class FakeQrScanner {
  failure: QrScanFailure | null = null;
  onCode: ((text: string) => void) | null = null;
  readonly stop = vi.fn();
  readonly start = vi.fn(async (_video: HTMLVideoElement, onCode: (text: string) => void): Promise<QrScanSession> => {
    if (this.failure) {
      throw new QrScanError(this.failure);
    }
    this.onCode = onCode;
    return { engine: 'barcode-detector', stop: this.stop };
  });
}

describe('WaiterTables', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<WaiterTables>;
  let root: HTMLElement;
  let scanner: FakeQrScanner;
  let toastError: ReturnType<typeof vi.spyOn>;
  let vibrate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    localStorage.clear();
    storeTestSession('WAITER');
    scanner = new FakeQrScanner();
    vibrate = vi.fn(() => true);
    Object.defineProperty(navigator, 'vibrate', { value: vibrate, configurable: true });
    TestBed.configureTestingModule({
      imports: [WaiterTables],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: QrScanner, useValue: scanner }],
    });
    http = TestBed.inject(HttpTestingController);
    toastError = vi.spyOn(TestBed.inject(ToastService), 'error');
  });

  afterEach(() => {
    http.verify();
    delete (navigator as { vibrate?: unknown }).vibrate;
    localStorage.clear();
  });

  async function refresh(): Promise<void> {
    await settle();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(WaiterTables);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    http.expectOne(TABLES_URL).flush(TABLES);
    await refresh();
  }

  const q = (testId: string) => root.querySelector<HTMLElement>(`[data-testid="${testId}"]`);

  async function click(testId: string): Promise<void> {
    q(testId)!.click();
    await refresh();
  }

  async function openScanner(): Promise<void> {
    await click('scan-qr');
    await refresh();
  }

  async function type(selector: string, value: string): Promise<void> {
    const input = root.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await refresh();
  }

  async function submitManual(number: string, pin: string): Promise<void> {
    await openScanner();
    await click('use-manual');
    await type('#qr-number', number);
    await type('#qr-pin', pin);
    root.querySelector<HTMLFormElement>('[data-testid="manual-form"]')!.dispatchEvent(new Event('submit'));
    await refresh();
  }

  it('CA4 shows the active tables in order, as plain tiles with the scan hint', async () => {
    await render();

    const tiles = Array.from(root.querySelectorAll('[data-testid="waiter-table"]'));
    expect(tiles.map((t) => t.textContent?.trim())).toEqual(['Mesa 1', 'Mesa 3']);
    expect(q('scan-help')?.textContent).toBe(SCAN_TO_OPEN);
    expect(q('scan-qr')?.textContent).toContain('Escanear QR de mesa');
  });

  it('CA4 table tiles are not buttons and do nothing when tapped', async () => {
    await render();

    for (const tile of Array.from(root.querySelectorAll<HTMLElement>('[data-testid="waiter-table"]'))) {
      expect(tile.tagName).not.toBe('BUTTON');
      expect(tile.getAttribute('role')).toBeNull();
      expect(tile.hasAttribute('tabindex')).toBe(false);
      expect(tile.querySelector('button, a, [role="button"]')).toBeNull();
      tile.click();
    }
    await refresh();

    expect(q('table-modal')).toBeNull();
    expect(root.querySelector('app-qr-scan-dialog')).toBeNull();
    expect(scanner.start).not.toHaveBeenCalled();
    http.expectNone(RESOLVE_TABLE_URL);
  });

  it('CA1 reading a QR resolves it with {code} and opens the table with its account', async () => {
    await render();
    await openScanner();
    expect(scanner.start).toHaveBeenCalledTimes(1);
    expect(scanner.start.mock.calls[0][0]).toBe(root.querySelector('[data-testid="scan-video"]'));

    scanner.onCode!('LACAVA:MESA:4:tok_-ABC');
    const req = http.expectOne(RESOLVE_TABLE_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'LACAVA:MESA:4:tok_-ABC' });
    req.flush(TABLE_4);
    await refresh();

    expect(root.querySelector('app-qr-scan-dialog')).toBeNull();
    expect(q('table-modal')?.querySelector('h3')?.textContent).toBe('Mesa 4');
    expect(q('table-origin')?.textContent).toContain(LOADED_BY.qr);
    expect(q('account-state')?.textContent).toBe(EMPTY_ACCOUNT);
    expect((q('assign-order') as HTMLButtonElement).disabled).toBe(true);
    expect(q('assign-note')?.textContent).toBe(ORDERS_PENDING_NOTE);
    expect(vibrate).toHaveBeenCalledTimes(1);

    await click('table-close');
    expect(q('table-modal')).toBeNull();
  });

  it('CA1 closing the scanner turns the camera off', async () => {
    await render();
    await openScanner();

    await click('scan-close');

    expect(scanner.stop).toHaveBeenCalledTimes(1);
    expect(root.querySelector('app-qr-scan-dialog')).toBeNull();
  });

  it('CA2 the manual mode sends the number and the PIN as text, keeping leading zeros', async () => {
    await render();
    await submitManual('12', '0427');

    expect(scanner.stop).toHaveBeenCalledTimes(1);
    const req = http.expectOne(RESOLVE_TABLE_URL);
    expect(req.request.body).toEqual({ number: 12, pin: '0427' });
    req.flush({ ...TABLE_4, id: 120, number: 12 });
    await refresh();

    expect(q('table-modal')?.querySelector('h3')?.textContent).toBe('Mesa 12');
    expect(q('table-origin')?.textContent).toContain(LOADED_BY.pin);
    expect(q('table-origin')?.textContent).not.toContain(LOADED_BY.qr);
  });

  it.each(['123', '12345', '12a4', ''])('CA2 PIN "%s" is invalid and nothing is sent', async (pin) => {
    await render();
    await submitManual('5', pin);

    expect(q('error-pin')?.textContent).toBe(PIN_INVALID);
    http.expectNone(RESOLVE_TABLE_URL);
  });

  it('CA2 a table number that is not a positive integer is invalid and nothing is sent', async () => {
    await render();
    await submitManual('0', '0427');

    expect(q('error-number')).not.toBeNull();
    http.expectNone(RESOLVE_TABLE_URL);
  });

  it.each<QrScanFailure>(['unavailable', 'denied', 'unsupported'])(
    'CA2 without a usable camera (%s) it explains why and offers the manual mode',
    async (failure) => {
      scanner.failure = failure;
      await render();
      await openScanner();

      expect(q('camera-error')?.textContent).toBe(CAMERA_FAILURE[failure]);
      expect(q('manual-form')).not.toBeNull();
      expect(root.querySelector('#qr-pin')?.getAttribute('inputmode')).toBe('numeric');
    },
  );

  it('CA3 an invalid scanned code shows "Código de mesa inválido" and lets the waiter scan again', async () => {
    await render();
    await openScanner();

    scanner.onCode!('LACAVA:MESA:4:forged');
    http.expectOne(RESOLVE_TABLE_URL).flush({ detail: INVALID_TABLE_CODE }, INVALID_CODE_PROBLEM);
    await refresh();

    expect(toastError).toHaveBeenCalledWith(INVALID_TABLE_CODE);
    expect(q('scan-status')?.textContent).toBe(INVALID_TABLE_CODE);
    expect(q('table-modal')).toBeNull();
    expect(vibrate).not.toHaveBeenCalled();

    await click('scan-retry');
    await refresh();
    expect(scanner.start).toHaveBeenCalledTimes(2);
  });

  it('CA3 a wrong PIN or inactive table shows "Código de mesa inválido" in the form and as a toast', async () => {
    await render();
    await submitManual('2', '9999');

    http.expectOne(RESOLVE_TABLE_URL).flush({ detail: INVALID_TABLE_CODE }, INVALID_CODE_PROBLEM);
    await refresh();

    expect(q('manual-error')?.textContent).toBe(INVALID_TABLE_CODE);
    expect(toastError).toHaveBeenCalledWith(INVALID_TABLE_CODE);
    expect(q('table-modal')).toBeNull();
  });

  it('CA3 other errors only show a toast', async () => {
    await render();
    await submitManual('2', '1234');

    http.expectOne(RESOLVE_TABLE_URL).flush({ detail: 'Algo falló.' }, { status: 500, statusText: 'Error' });
    await refresh();

    expect(toastError).toHaveBeenCalledWith('Algo falló.');
    expect(q('manual-error')).toBeNull();
  });

  it('CA1 switching to manual and back while the camera is starting stops the stale stream and ignores its code', async () => {
    // Cada arranque queda pendiente hasta que la prueba lo resuelve, como un getUserMedia lento.
    const starts: { onCode: (text: string) => void; resolve: (s: QrScanSession) => void }[] = [];
    scanner.start.mockImplementation(
      (_video, onCode) => new Promise<QrScanSession>((resolve) => starts.push({ onCode, resolve })),
    );
    const staleStop = vi.fn();
    const liveStop = vi.fn();
    await render();
    await openScanner();
    await click('use-manual');
    await click('use-camera');
    expect(starts).toHaveLength(2);

    starts[0].resolve({ engine: 'barcode-detector', stop: staleStop });
    starts[1].resolve({ engine: 'barcode-detector', stop: liveStop });
    await refresh();

    expect(staleStop).toHaveBeenCalledTimes(1);
    expect(liveStop).not.toHaveBeenCalled();
    starts[0].onCode('LACAVA:MESA:9:stale');
    http.expectNone(RESOLVE_TABLE_URL);

    starts[1].onCode('LACAVA:MESA:4:live');
    expect(http.expectOne(RESOLVE_TABLE_URL).request.body).toEqual({ code: 'LACAVA:MESA:4:live' });
  });

  it('CA1 closing the scanner while the table is loading does not emit after destroy', async () => {
    await render();
    await submitManual('4', '0427');
    const req = http.expectOne(RESOLVE_TABLE_URL);
    // Angular avisa NG0953 con console.warn cuando un output emite tras destruirse su componente.
    const warn = vi.spyOn(console, 'warn');

    await click('scan-close');
    req.flush(TABLE_4);
    await refresh();

    expect(warn.mock.calls.flat().join(' ')).not.toContain('NG0953');
    warn.mockRestore();

    expect(root.querySelector('app-qr-scan-dialog')).toBeNull();
    expect(q('table-modal')).toBeNull();
    expect(vibrate).not.toHaveBeenCalled();
  });
});
