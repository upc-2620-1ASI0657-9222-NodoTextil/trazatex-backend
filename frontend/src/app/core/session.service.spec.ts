import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { SessionService } from './session.service';
import { ApiService, errorMessage } from './api.service';
import { authInterceptor } from './auth.interceptor';
import { HttpErrorResponse } from '@angular/common/http';
import { vi } from 'vitest';
import { DraftStorage } from './draft-storage.service';

describe('Sesión y acceso HTTP', () => {
  let http: HttpTestingController;
  let session: SessionService;
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
  });
  afterEach(() => {
    session.logout();
    http.verify();
  });
  const result = (role = 'OPERATOR') => ({
    token: 'unit-test-token',
    tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
    user: {
      id: 'user-a',
      companyId: 'company-a',
      email: 'operator@example.test',
      firstName: 'Ana',
      lastName: 'Pérez',
      position: 'Producción',
      role,
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
    },
  });
  it('rechaza la sesión de demostración anterior', () => {
    localStorage.setItem(
      'trazatex.session',
      JSON.stringify({ rol: 'Dueño de Licencia', empresaId: 'EB' }),
    );
    expect(session.loggedIn()).toBe(false);
    expect(session.empresaId()).toBeNull();
  });
  it('obtiene identidad del login y envía Bearer solo al Gateway', async () => {
    const login = session.login('operator@example.test', 'unit-test-password');
    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({
      email: 'operator@example.test',
      password: 'unit-test-password',
    });
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(result());
    await login;
    expect(session.rol()).toBe('Operario');
    expect(session.empresaId()).toBe('company-a');
    const read = TestBed.inject(ApiService).request('GET', '/batches');
    const batches = http.expectOne('/api/batches');
    expect(batches.request.headers.get('Authorization')).toBe('Bearer unit-test-token');
    batches.flush({ items: [] });
    await read;
    session.logout();
    expect(session.loggedIn()).toBe(false);
    expect(sessionStorage.getItem('trazatex.auth')).toBeNull();
  });
  it('no crea una sesión con contraseña incorrecta', async () => {
    const login = session.login('operator@example.test', 'incorrect');
    const assertion = expect(login).rejects.toBeDefined();
    http
      .expectOne('/api/auth/login')
      .flush({ message: 'Credenciales inválidas' }, { status: 401, statusText: 'Unauthorized' });
    await assertion;
    expect(session.loggedIn()).toBe(false);
  });
  it('traduce errores de autorización y conexión al español', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 403 }))).toContain('permiso');
    // status 0 es también el resultado de un plazo agotado (ADR-010): el mensaje debe indicar que la respuesta
    // no se pudo confirmar y que los datos se conservan para reintentar.
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toBe(
      'No se pudo confirmar la respuesta del servidor. Conserva los datos y vuelve a intentarlo.',
    );
    expect(errorMessage(new HttpErrorResponse({ status: 429 }))).toContain('límite de solicitudes');
  });
  it('descarta una sesión persistida vencida', () => {
    TestBed.resetTestingModule();
    const saved = result();
    saved.expiresAt = '2020-01-01T00:00:00Z';
    sessionStorage.setItem('trazatex.auth', JSON.stringify(saved));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    session = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
    expect(session.loggedIn()).toBe(false);
  });
});

describe('La sesión borra los borradores al terminar (FE-M5)', () => {
  let session: SessionService;
  let clear: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    sessionStorage.clear();
    clear = vi.fn(async () => undefined);
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: DraftStorage, useValue: { clearDrafts: clear } }] });
    session = TestBed.inject(SessionService);
  });

  it('cerrar sesión borra los borradores', () => {
    session.logout();
    expect(clear).toHaveBeenCalledTimes(1);
  });

  it('expirar la sesión también los borra (expire() pasa por logout())', () => {
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    session.expire();
    expect(clear).toHaveBeenCalledTimes(1);
  });

  it('un fallo al borrar no rompe el cierre de sesión', () => {
    clear.mockRejectedValueOnce(new Error('sin IndexedDB'));
    expect(() => session.logout()).not.toThrow();
  });
});

describe('El 401 del Gateway se ramifica por code (FE-M7)', () => {
  let http: HttpTestingController;
  let session: SessionService;
  let api: ApiService;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(), { provide: DraftStorage, useValue: { clearDrafts: async () => undefined } }] });
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
    api = TestBed.inject(ApiService);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });
  async function rechazo(code: string) {
    const llamada = api.request('GET', '/batches').catch(() => undefined);
    http.expectOne('/api/batches').flush({ code, message: 'texto del servidor' }, { status: 401, statusText: 'Unauthorized' });
    await llamada;
  }

  it.each([
    ['TOKEN_EXPIRED', 'Tu sesión venció. Inicia sesión de nuevo.'],
    ['USER_INACTIVE', 'Tu cuenta está inactiva. Consulta con el administrador de tu empresa.'],
    ['INVALID_TOKEN', 'Tu sesión ya no es válida. Inicia sesión de nuevo.'],
    ['MISSING_TOKEN', 'Tu sesión ya no es válida. Inicia sesión de nuevo.'],
  ])('%s: la sesión termina y el login dice por qué', async (code, texto) => {
    await rechazo(code);
    expect(session.endNotice()).toBe(texto);
    expect(session.loggedIn()).toBe(false);
  });

  it('un 401 del propio login no termina ninguna sesión ni deja aviso', async () => {
    const llamada = api.request('POST', '/auth/login', {}).catch(() => undefined);
    http.expectOne('/api/auth/login').flush({ code: 'TOKEN_EXPIRED' }, { status: 401, statusText: 'Unauthorized' });
    await llamada;
    expect(session.endNotice()).toBe('');
  });

  it('el aviso se borra al volver a entrar', async () => {
    await rechazo('TOKEN_EXPIRED');
    const entrada = session.login('a@example.test', 'x');
    http.expectOne('/api/auth/login').flush({ token: 't', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(), user: { id: 'u', companyId: 'c', email: 'a@example.test', firstName: 'A', lastName: 'B', position: 'P', role: 'OPERATOR', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' } });
    await entrada;
    expect(session.endNotice()).toBe('');
  });
});
