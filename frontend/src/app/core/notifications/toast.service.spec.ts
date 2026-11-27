import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ToastContainer } from './toast-container';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    service = TestBed.inject(ToastService);
  });

  afterEach(() => vi.useRealTimers());

  it('adds a toast and removes it after its duration', () => {
    service.success('Producto guardado.');
    expect(service.toasts()).toEqual([{ id: 1, kind: 'success', message: 'Producto guardado.' }]);

    vi.advanceTimersByTime(4500);
    expect(service.toasts()).toEqual([]);
  });

  it('keeps error toasts longer and allows manual dismiss', () => {
    const id = service.error('No hay existencias suficientes.');
    vi.advanceTimersByTime(4500);
    expect(service.toasts().length).toBe(1);

    service.dismiss(id);
    expect(service.toasts()).toEqual([]);
  });

  it('renders toasts with an accessible role', async () => {
    const fixture = TestBed.createComponent(ToastContainer);
    service.info('Hola');
    service.error('Falló');
    fixture.detectChanges();
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    const toasts = el.querySelectorAll('.toast');
    expect(toasts.length).toBe(2);
    expect(toasts[0].getAttribute('role')).toBe('status');
    expect(toasts[1].getAttribute('role')).toBe('alert');
    expect(toasts[1].textContent).toContain('Falló');
  });
});
