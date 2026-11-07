import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { vi } from 'vitest';
import { ApiService, filenameFromDisposition } from './api.service';

describe('Reintentos HTTP del Gateway', () => {
  let api: ApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });
  it('respeta Retry-After antes de repetir una consulta rechazada por límite', async () => {
    vi.useFakeTimers();
    const result = api.request<{ id: string }>('GET', '/machines');
    http
      .expectOne('/api/machines')
      .flush({}, { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '2' } });
    await vi.advanceTimersByTimeAsync(1999);
    http.expectNone('/api/machines');
    await vi.advanceTimersByTimeAsync(1);
    http.expectOne('/api/machines').flush({ id: 'machine-a' });
    expect(await result).toEqual({ id: 'machine-a' });
  });
  it('no repite una escritura rechazada por límite', async () => {
    const result = api.request('POST', '/invitations/operators', { email: 'unit@example.test' });
    const rejected = expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
    http
      .expectOne('/api/invitations/operators')
      .flush({}, { status: 429, statusText: 'Too Many Requests' });
    await rejected;
    http.expectNone('/api/invitations/operators');
  });
});

describe('Plazos del Gateway por tipo de operación (FE-I2)', () => {
  let api: ApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  const casos: [string, string, string, () => unknown, number][] = [
    ['lectura', 'GET', '/batches', () => undefined, 2500],
    ['escritura JSON normal', 'POST', '/transfers/t1/accept', () => ({}), 2500],
    ['lectura de Quality', 'GET', '/quality/controls', () => undefined, 2500],
    ['subida multipart', 'POST', '/batches/b1/evidence', () => new FormData(), 30000],
    ['subida multipart a Quality', 'POST', '/quality/evidence', () => new FormData(), 30000],
    ['alta de empresa', 'POST', '/companies', () => ({ businessName: 'Textil SAC' }), 30000],
    ['comando de Quality', 'POST', '/quality/controls/c1/finish', () => ({}), 8000],
    ['borrado de evidencia de Quality', 'DELETE', '/quality/failures/f1/evidence/e1', () => undefined, 8000],
  ];
  for (const [nombre, method, path, body, ms] of casos) {
    it(`${nombre}: espera ${ms} ms y luego falla como «sin respuesta» (status 0)`, async () => {
      const result = api.request(method, path, body());
      const rejected = expect(result).rejects.toMatchObject({ status: 0 });
      const request = http.expectOne('/api' + path);
      await vi.advanceTimersByTimeAsync(ms - 1);
      expect(request.cancelled).toBe(false);
      await vi.advanceTimersByTimeAsync(1);
      await rejected;
      expect(request.cancelled).toBe(true);
    });
  }

  it('el informe PDF espera 15 s', async () => {
    const result = api.download('/quality/controls/c1/report', 'informe.pdf');
    const rejected = expect(result).rejects.toMatchObject({ status: 0 });
    const request = http.expectOne('/api/quality/controls/c1/report');
    await vi.advanceTimersByTimeAsync(14999);
    expect(request.cancelled).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    await rejected;
  });

  it('un plazo largo no impide recibir la respuesta a tiempo', async () => {
    const result = api.request<{ id: string }>('POST', '/companies', { businessName: 'Textil SAC' });
    const request = http.expectOne('/api/companies');
    await vi.advanceTimersByTimeAsync(20000);
    request.flush({ id: 'c1' });
    expect(await result).toEqual({ id: 'c1' });
  });

  it('reintentar con la misma Idempotency-Key tras un plazo agotado envía exactamente la misma clave', async () => {
    const first = api.request('POST', '/quality/controls/c1/finish', {}, 'clave-pendiente-1');
    const firstRejected = expect(first).rejects.toMatchObject({ status: 0 });
    const firstRequest = http.expectOne('/api/quality/controls/c1/finish');
    expect(firstRequest.request.headers.get('Idempotency-Key')).toBe('clave-pendiente-1');
    await vi.advanceTimersByTimeAsync(8000);
    await firstRejected;
    const retry = api.request('POST', '/quality/controls/c1/finish', {}, 'clave-pendiente-1');
    const retryRequest = http.expectOne('/api/quality/controls/c1/finish');
    expect(retryRequest.request.headers.get('Idempotency-Key')).toBe('clave-pendiente-1');
    retryRequest.flush({ ok: true });
    await retry;
  });
});

describe('Progreso de subida (FE-I2)', () => {
  let api: ApiService;
  let http: HttpTestingController;
  const archivo = () => { const body = new FormData(); body.append('files', new File(['x'], 'a.jpg', { type: 'image/jpeg' })); return body; };
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { vi.useRealTimers(); });

  it('una subida con FormData informa el porcentaje enviado y devuelve el cuerpo de la respuesta final', async () => {
    const percents: number[] = [];
    const result = api.request<{ ok: boolean }>('POST', '/quality/evidence', archivo(), 'k1', percent => percents.push(percent));
    const request = http.expectOne('/api/quality/evidence');
    expect(request.request.reportProgress).toBe(true);
    request.event({ type: HttpEventType.UploadProgress, loaded: 25, total: 100 });
    request.event({ type: HttpEventType.UploadProgress, loaded: 100, total: 100 });
    request.flush({ ok: true });
    expect(await result).toEqual({ ok: true });
    expect(percents).toEqual([25, 100]);
  });

  it('una escritura sin archivos no pide eventos de progreso', async () => {
    const percents: number[] = [];
    const result = api.request('POST', '/transfers/t1/accept', { reason: 'x' }, 'k2', percent => percents.push(percent));
    const request = http.expectOne('/api/transfers/t1/accept');
    expect(request.request.reportProgress).toBe(false);
    request.flush({});
    await result;
    expect(percents).toEqual([]);
  });

  it('el progreso no reinicia el plazo: la subida se corta a los 30 s aunque siga enviando bytes', async () => {
    vi.useFakeTimers();
    const result = api.request('POST', '/quality/evidence', archivo(), 'k3', () => undefined);
    const rejected = expect(result).rejects.toMatchObject({ status: 0 });
    const request = http.expectOne('/api/quality/evidence');
    await vi.advanceTimersByTimeAsync(20000);
    request.event({ type: HttpEventType.UploadProgress, loaded: 50, total: 100 });
    await vi.advanceTimersByTimeAsync(9999);
    expect(request.cancelled).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    await rejected;
    expect(request.cancelled).toBe(true);
  });
});

describe('Motivo de cancelación de un control en lenguaje de persona (FE-M7)', () => {
  it('traduce los códigos de Production y nunca devuelve el código en crudo', async () => {
    const { cancelReasonText, CANCEL_REASON_TEXT } = await import('./api-models');
    for (const code of Object.keys(CANCEL_REASON_TEXT)) {
      const texto = cancelReasonText(code);
      expect(texto).not.toContain(code);
      expect(texto).not.toMatch(/[A-Z]{3,}_[A-Z_]+/);
    }
    expect(cancelReasonText('INVALID_PHASE_TRANSITION')).toContain('cambió de fase');
  });
  it('un código desconocido, vacío o nulo cae en una frase genérica sin exponer el código', async () => {
    const { cancelReasonText } = await import('./api-models');
    for (const code of ['CODIGO_NUEVO_DE_PRODUCTION', '', null, undefined]) expect(cancelReasonText(code)).toBe('Producción no aceptó el inicio');
  });
});

describe('Nombre del PDF descargado (FE-M8)', () => {
  it('toma el nombre de Content-Disposition (filename entre comillas)', () => {
    expect(filenameFromDisposition('attachment; filename="informe-control-123.pdf"', 'control-x.pdf')).toBe('informe-control-123.pdf');
  });
  it('prefiere filename* (UTF-8) y lo decodifica', () => {
    expect(filenameFromDisposition("attachment; filename=\"informe.pdf\"; filename*=UTF-8''Informe%20de%20calidad%20%C3%B1.pdf", 'x.pdf')).toBe('Informe de calidad ñ.pdf');
  });
  it('acepta filename sin comillas', () => {
    expect(filenameFromDisposition('attachment; filename=informe.pdf', 'x.pdf')).toBe('informe.pdf');
  });
  it('quita rutas y caracteres peligrosos: nunca escribe fuera de la carpeta de descargas', () => {
    expect(filenameFromDisposition('attachment; filename="../../etc/passwd"', 'x.pdf')).toBe('passwd');
    expect(filenameFromDisposition('attachment; filename="C:\\\\Windows\\\\a<b>.pdf"', 'x.pdf')).toBe('ab.pdf');
    expect(filenameFromDisposition('attachment; filename=".hidden.pdf"', 'x.pdf')).toBe('hidden.pdf');
  });
  it('sin cabecera, vacía o ilegible usa el nombre de reserva', () => {
    for (const header of [null, undefined, '', 'inline', 'attachment; filename=""', "attachment; filename*=UTF-8''%E0%A4%A"]) expect(filenameFromDisposition(header, 'control-x.pdf')).toBe('control-x.pdf');
  });
});

describe('download() usa el nombre que manda el servidor (FE-M8)', () => {
  let api: ApiService;
  let http: HttpTestingController;
  let nombres: string[];
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
    nombres = [];
    (URL as unknown as { createObjectURL: () => string; revokeObjectURL: () => void }).createObjectURL = () => 'blob:x';
    (URL as unknown as { revokeObjectURL: () => void }).revokeObjectURL = () => undefined;
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { nombres.push(this.download); });
  });
  afterEach(() => vi.restoreAllMocks());

  it('con Content-Disposition guarda con ese nombre', async () => {
    const result = api.download('/quality/controls/c1/report', 'control-c1.pdf');
    http.expectOne('/api/quality/controls/c1/report').flush(new Blob(['%PDF']), { headers: { 'Content-Disposition': 'attachment; filename="informe-calidad-LOTE-7.pdf"' } });
    await result;
    expect(nombres).toEqual(['informe-calidad-LOTE-7.pdf']);
  });

  it('sin Content-Disposition usa el nombre de reserva', async () => {
    const result = api.download('/quality/controls/c1/report', 'control-c1.pdf');
    http.expectOne('/api/quality/controls/c1/report').flush(new Blob(['%PDF']));
    await result;
    expect(nombres).toEqual(['control-c1.pdf']);
  });
});
