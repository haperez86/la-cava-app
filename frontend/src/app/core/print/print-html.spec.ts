import { PRINT_DELAY_MS, PRINT_ERROR, PRINT_FRAME_ID, printHtml } from './print-html';

describe('printHtml', () => {
  afterEach(() => {
    vi.useRealTimers();
    document.getElementById(PRINT_FRAME_ID)?.remove();
  });

  /** Iframe de impresión actual; jsdom no implementa `focus()` en ventanas, así que se silencia. */
  function frame(): HTMLIFrameElement | null {
    const iframe = document.getElementById(PRINT_FRAME_ID) as HTMLIFrameElement | null;
    if (iframe?.contentWindow && !vi.isMockFunction(iframe.contentWindow.focus)) {
      vi.spyOn(iframe.contentWindow, 'focus').mockImplementation(() => undefined);
    }
    return iframe;
  }

  it('creates a hidden iframe with the HTML, prints it and removes it after printing', async () => {
    vi.useFakeTimers();
    const done = printHtml('<!doctype html><html><body><h1>Mesa 4</h1></body></html>');

    const iframe = frame()!;
    expect(iframe).not.toBeNull();
    expect(iframe.getAttribute('aria-hidden')).toBe('true');
    expect(iframe.style.visibility).toBe('hidden');
    expect(iframe.contentDocument?.querySelector('h1')?.textContent).toBe('Mesa 4');
    const print = vi.spyOn(iframe.contentWindow!, 'print').mockImplementation(() => undefined);
    expect(print).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(PRINT_DELAY_MS);
    await done;
    expect(print).toHaveBeenCalledTimes(1);

    iframe.contentWindow!.dispatchEvent(new Event('afterprint'));
    expect(frame()).toBeNull();
  });

  it('replaces the iframe of a previous print', async () => {
    vi.useFakeTimers();
    const first = printHtml('<p>uno</p>');
    const firstFrame = frame()!;
    vi.spyOn(firstFrame.contentWindow!, 'print').mockImplementation(() => undefined);
    await vi.advanceTimersByTimeAsync(PRINT_DELAY_MS);
    await first;

    void printHtml('<p>dos</p>').catch(() => undefined);

    expect(document.querySelectorAll(`#${PRINT_FRAME_ID}`)).toHaveLength(1);
    expect(firstFrame.isConnected).toBe(false);
    expect(frame()!.contentDocument?.body.textContent).toBe('dos');
  });

  it('rejects without alert() and removes the iframe when printing fails', async () => {
    vi.useFakeTimers();
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => undefined);
    const done = printHtml('<p>x</p>');
    vi.spyOn(frame()!.contentWindow!, 'print').mockImplementation(() => {
      throw new Error('blocked');
    });
    const outcome = expect(done).rejects.toThrow(PRINT_ERROR);

    await vi.advanceTimersByTimeAsync(PRINT_DELAY_MS);
    await outcome;
    expect(frame()).toBeNull();
    expect(alertSpy).not.toHaveBeenCalled();
    alertSpy.mockRestore();
  });
});
