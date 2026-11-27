import { TestBed } from '@angular/core/testing';
import { QrScanError, QrScanner, ZXING_QR_READER, ZxingQrReader } from './qr-scanner';

type ZxingCallback = Parameters<ZxingQrReader['decodeFromStream']>[2];

describe('QrScanner', () => {
  let track: { stop: ReturnType<typeof vi.fn> };
  let stream: MediaStream;
  let getUserMedia: ReturnType<typeof vi.fn>;
  let video: HTMLVideoElement;
  let zxingCallback: ZxingCallback | undefined;
  let zxingControls: { stop: ReturnType<typeof vi.fn> };
  let decodeFromStream: ReturnType<typeof vi.fn>;
  let zxingFactory: ReturnType<typeof vi.fn>;
  const originalMediaDevices = Object.getOwnPropertyDescriptor(navigator, 'mediaDevices');

  function setMediaDevices(value: unknown): void {
    Object.defineProperty(navigator, 'mediaDevices', { value, configurable: true });
  }

  /** Simula `BarcodeDetector` con los formatos indicados; `detect` devuelve lo que diga `codes`. */
  function stubBarcodeDetector(formats: string[], codes: { rawValue: string }[][]) {
    const detect = vi.fn(async () => codes.shift() ?? []);
    const ctor = vi.fn(function (this: unknown) {
      return { detect };
    }) as unknown as { getSupportedFormats: () => Promise<string[]> };
    ctor.getSupportedFormats = async () => formats;
    vi.stubGlobal('BarcodeDetector', ctor);
    return { ctor, detect };
  }

  beforeEach(() => {
    track = { stop: vi.fn() };
    stream = { getTracks: () => [track] } as unknown as MediaStream;
    getUserMedia = vi.fn(async () => stream);
    setMediaDevices({ getUserMedia });
    video = { srcObject: null, play: vi.fn(async () => undefined) } as unknown as HTMLVideoElement;
    zxingCallback = undefined;
    zxingControls = { stop: vi.fn() };
    decodeFromStream = vi.fn(async (_s: MediaStream, _v: HTMLVideoElement, cb: ZxingCallback) => {
      zxingCallback = cb;
      return zxingControls;
    });
    zxingFactory = vi.fn(async () => ({ decodeFromStream }) as ZxingQrReader);
    TestBed.configureTestingModule({ providers: [{ provide: ZXING_QR_READER, useValue: zxingFactory }] });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
    if (originalMediaDevices) {
      Object.defineProperty(navigator, 'mediaDevices', originalMediaDevices);
    } else {
      delete (navigator as { mediaDevices?: unknown }).mediaDevices;
    }
  });

  it('CA1 opens the rear camera', async () => {
    stubBarcodeDetector(['qr_code'], []);
    const session = await TestBed.inject(QrScanner).start(video, () => undefined);

    expect(getUserMedia).toHaveBeenCalledWith({ video: { facingMode: 'environment' } });
    expect(video.srcObject).toBe(stream);
    session.stop();
  });

  it('CA1 uses BarcodeDetector when the browser has it, reads one code and stops the camera', async () => {
    vi.useFakeTimers();
    const { ctor } = stubBarcodeDetector(['ean_13', 'qr_code'], [[], [{ rawValue: 'LACAVA:MESA:4:tok' }]]);
    const onCode = vi.fn();

    const session = await TestBed.inject(QrScanner).start(video, onCode);
    expect(session.engine).toBe('barcode-detector');
    expect(ctor).toHaveBeenCalledWith({ formats: ['qr_code'] });
    expect(zxingFactory).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(300);
    expect(onCode).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(300);
    await vi.advanceTimersByTimeAsync(900);

    expect(onCode).toHaveBeenCalledTimes(1);
    expect(onCode).toHaveBeenCalledWith('LACAVA:MESA:4:tok');
    expect(track.stop).toHaveBeenCalledTimes(1);
    expect(video.srcObject).toBeNull();
  });

  it('CA1 falls back to @zxing/browser when there is no BarcodeDetector', async () => {
    const onCode = vi.fn();

    const session = await TestBed.inject(QrScanner).start(video, onCode);
    expect(session.engine).toBe('zxing');
    expect(zxingFactory).toHaveBeenCalledTimes(1);
    expect(decodeFromStream).toHaveBeenCalledWith(stream, video, expect.any(Function));

    zxingCallback?.(undefined);
    expect(onCode).not.toHaveBeenCalled();
    zxingCallback?.({ getText: () => 'LACAVA:MESA:7:abc' });
    zxingCallback?.({ getText: () => 'LACAVA:MESA:7:abc' });

    expect(onCode).toHaveBeenCalledTimes(1);
    expect(onCode).toHaveBeenCalledWith('LACAVA:MESA:7:abc');
    expect(zxingControls.stop).toHaveBeenCalledTimes(1);
    expect(track.stop).toHaveBeenCalledTimes(1);
  });

  it('CA1 uses zxing when BarcodeDetector cannot read QR codes', async () => {
    stubBarcodeDetector(['ean_13'], []);

    const session = await TestBed.inject(QrScanner).start(video, () => undefined);

    expect(session.engine).toBe('zxing');
    expect(zxingFactory).toHaveBeenCalledTimes(1);
  });

  it('CA1 stop() turns the camera off', async () => {
    const session = await TestBed.inject(QrScanner).start(video, () => undefined);

    session.stop();
    session.stop();

    expect(zxingControls.stop).toHaveBeenCalledTimes(1);
    expect(track.stop).toHaveBeenCalledTimes(1);
  });

  it('CA2 fails with "unavailable" when the device has no camera API', async () => {
    setMediaDevices(undefined);

    await expect(TestBed.inject(QrScanner).start(video, () => undefined)).rejects.toEqual(
      new QrScanError('unavailable'),
    );
  });

  it('CA2 fails with "denied" when the camera permission is refused', async () => {
    getUserMedia.mockRejectedValue(new DOMException('no', 'NotAllowedError'));

    const error = await TestBed.inject(QrScanner)
      .start(video, () => undefined)
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(QrScanError);
    expect((error as QrScanError).reason).toBe('denied');
  });

  it('CA2 fails with "unsupported" and releases the camera when zxing cannot load', async () => {
    zxingFactory.mockRejectedValue(new Error('chunk failed'));

    const error = await TestBed.inject(QrScanner)
      .start(video, () => undefined)
      .catch((e: unknown) => e);

    expect((error as QrScanError).reason).toBe('unsupported');
    expect(track.stop).toHaveBeenCalledTimes(1);
  });
});
