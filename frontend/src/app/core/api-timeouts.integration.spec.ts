import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpEventType } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { fakeIndexedDB } from '../testing/fake-indexeddb';
import { authInterceptor } from './auth.interceptor';
import { StoreService } from './store.service';

const SIN_RESPUESTA = 'No se pudo confirmar la respuesta del servidor. Conserva los datos y vuelve a intentarlo.';

/** ApiService, StoreService y DraftStorage reales; solo HTTP e IndexedDB son de prueba. No se ejecuta el efecto inicial del store. */
describe('Plazo agotado en una escritura: mensaje, datos y clave pendiente (FE-I2)', () => {
  let http: HttpTestingController;
  let store: StoreService;
  let idb: ReturnType<typeof fakeIndexedDB>;
  const operaciones = () => [...idb.data.entries()].filter(([key]) => key.startsWith('operation/'));

  /** Espera con setTimeout real a que la petición aparezca (la huella SHA-256 y IndexedDB son asíncronas). */
  async function peticion(url: string): Promise<TestRequest> {
    for (let i = 0; i < 400; i++) {
      try { return http.expectOne(url); } catch { await new Promise(resolve => setTimeout(resolve, 5)); }
    }
    return http.expectOne(url);
  }

  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Admin', role: 'COMPANY_ADMIN', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    idb = fakeIndexedDB();
    vi.stubGlobal('indexedDB', idb);
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(StoreService);
  });
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); sessionStorage.clear(); });

  it('al agotarse el plazo muestra el mismo mensaje de «no se pudo confirmar», libera la pantalla y no descarta la clave pendiente', async () => {
    const result = store.aceptarTransferencia('t1');
    const request = await peticion('/api/transfers/t1/accept');
    const key = request.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    expect(operaciones()).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(2500);
    expect(await result).toEqual({ ok: false, error: SIN_RESPUESTA });
    expect(request.cancelled).toBe(true);
    expect(store.saving()).toBe(false);
    expect(operaciones()).toHaveLength(1);
    expect(operaciones()[0][1].id).toBe(key);
  });

  it('el reintento tras el timeout usa la MISMA Idempotency-Key y, al confirmarse, se limpia el registro', async () => {
    const first = store.aceptarTransferencia('t1');
    const firstRequest = await peticion('/api/transfers/t1/accept');
    const key = firstRequest.request.headers.get('Idempotency-Key');
    await vi.advanceTimersByTimeAsync(2500);
    await first;

    const retry = store.aceptarTransferencia('t1');
    const retryRequest = await peticion('/api/transfers/t1/accept');
    expect(retryRequest.request.headers.get('Idempotency-Key')).toBe(key);
    expect(operaciones()).toHaveLength(1);

    retryRequest.flush({ id: 't1' });
    // Tras el éxito, mutate() espera a refresh() (sus GET no se responden aquí): se comprueba lo que ocurre antes.
    for (let i = 0; i < 400 && operaciones().length; i++) await new Promise(resolve => setTimeout(resolve, 5));
    expect(operaciones()).toHaveLength(0);
    void retry;
  });

  it('un comando de Quality espera 8 s en la acción real y sigue conservando la clave si no responde', async () => {
    const result = store.finalizarControl('ctrl-1');
    const request = await peticion('/api/quality/controls/ctrl-1/finish');
    await vi.advanceTimersByTimeAsync(7999);
    expect(request.cancelled).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    expect(await result).toEqual({ ok: false, error: SIN_RESPUESTA });
    expect(operaciones()).toHaveLength(1);
  });

  it('una subida expone su progreso en el store, deja la pantalla libre y lo borra al terminar (FE-I2)', async () => {
    expect(store.uploadProgress()).toBeNull();
    const result = store.agregarEvidencia('control', 'c1', new File(['x'], 'a.jpg', { type: 'image/jpeg' }));
    const request = await peticion('/api/quality/evidence');
    expect(store.saving()).toBe(true);
    expect(store.uploadProgress()).toBe(0);
    request.event({ type: HttpEventType.UploadProgress, loaded: 40, total: 100 });
    expect(store.uploadProgress()).toBe(40);

    request.flush({ message: 'Formato no admitido' }, { status: 400, statusText: 'Bad Request' });
    expect((await result).ok).toBe(false);
    expect(store.saving()).toBe(false);
    expect(store.uploadProgress()).toBeNull();
  });

  it('una escritura sin archivos nunca muestra progreso de subida', async () => {
    const result = store.aceptarTransferencia('t1');
    const request = await peticion('/api/transfers/t1/accept');
    expect(store.saving()).toBe(true);
    expect(store.uploadProgress()).toBeNull();
    request.flush({ message: 'No autorizado' }, { status: 403, statusText: 'Forbidden' });
    await result;
  });

  it('un rechazo definitivo del servidor (4xx) sí descarta la clave: no es una incertidumbre', async () => {
    const result = store.aceptarTransferencia('t1');
    (await peticion('/api/transfers/t1/accept')).flush({ message: 'No autorizado' }, { status: 403, statusText: 'Forbidden' });
    expect((await result).ok).toBe(false);
    expect(operaciones()).toHaveLength(0);
  });
});
