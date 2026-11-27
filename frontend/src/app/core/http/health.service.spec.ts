import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HealthService } from './health.service';

describe('HealthService', () => {
  let service: HealthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(HealthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('is up when /health reports status and db UP', () => {
    service.check();
    expect(service.status()).toBe('checking');

    http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' });
    expect(service.status()).toBe('up');
  });

  it('is down when the backend answers 503 or is unreachable', () => {
    service.check();
    http.expectOne('/api/v1/health').flush({ status: 'DOWN', db: 'DOWN' }, { status: 503, statusText: 'Service Unavailable' });
    expect(service.status()).toBe('down');

    service.check();
    http.expectOne('/api/v1/health').error(new ProgressEvent('error'));
    expect(service.status()).toBe('down');
  });
});
