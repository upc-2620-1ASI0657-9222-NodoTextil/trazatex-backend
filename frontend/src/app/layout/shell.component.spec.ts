import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ApiService } from '../core/api.service';
import { StoreService } from '../core/store.service';
import { UiService } from '../shared/ui';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { ShellComponent } from './shell.component';

@Component({ template: '<p>página</p>' })
class PaginaFalsa {}

/** ApiService de mentira: solo responde lo que el contador necesita; el resto del refresh() falla sin ruido. */
class FakeApi {
  readonly calls: string[] = [];
  count = 0;
  async request<T>(method: string, path: string): Promise<T> {
    this.calls.push(method + ' ' + path);
    if (path === '/notifications/unread-count') return { count: this.count } as T;
    if (path.startsWith('/notifications?')) return { items: [], page: 0, size: 6, totalElements: 0, totalPages: 0 } as T;
    if (path.startsWith('/notifications/')) return undefined as T;
    throw new Error('sin respuesta de prueba: ' + method + ' ' + path);
  }
  async all<T>(): Promise<T[]> { return []; }
}

describe('Contador de notificaciones en el shell (FE-I9)', () => {
  let api: FakeApi;
  let store: StoreService;
  let fixture: ComponentFixture<ShellComponent>;
  const side = () => fixture.nativeElement.querySelector('[data-testid="unread-badge"]') as HTMLElement | null;
  const top = () => fixture.nativeElement.querySelector('[data-testid="unread-badge-top"]') as HTMLElement | null;
  const links = () => [...fixture.nativeElement.querySelectorAll('a[href="/notificaciones"]')] as HTMLAnchorElement[];
  async function render() {
    await fixture.whenStable();
    fixture.detectChanges();
  }
  async function poner(count: number) {
    api.count = count;
    await store.refreshUnreadCount();
    await render();
  }

  beforeEach(async () => {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Producción', role: 'OPERATOR', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    api = new FakeApi();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: PaginaFalsa }]),
        { provide: ApiService, useValue: api },
      ],
    });
    store = TestBed.inject(StoreService);
    fixture = TestBed.createComponent(ShellComponent);
    await render();
    await new Promise(resolve => setTimeout(resolve, 20)); // deja terminar el refresh() inicial del store
    await render();
  });
  afterEach(() => { fixture.destroy(); sessionStorage.clear(); });

  it('con 0 no pinta el contador y el nombre accesible es «Notificaciones»', async () => {
    await poner(0);
    expect(side()).toBeNull();
    expect(top()).toBeNull();
    expect(links().length).toBeGreaterThanOrEqual(2);
    for (const link of links()) expect(link.getAttribute('aria-label')).toBe('Notificaciones');
  });

  it('con no leídas pinta el número en la barra lateral y en la barra superior', async () => {
    await poner(5);
    expect(side()?.textContent?.trim()).toBe('5');
    expect(top()?.textContent?.trim()).toBe('5');
    for (const link of links()) expect(link.getAttribute('aria-label')).toBe('Notificaciones, 5 sin leer');
  });

  it('el punto visual es aria-hidden: el aviso para lectores de pantalla va en el nombre del enlace', async () => {
    await poner(2);
    expect(side()?.getAttribute('aria-hidden')).toBe('true');
    expect(top()?.getAttribute('aria-hidden')).toBe('true');
  });

  it('99 se muestra tal cual y 100 o más como 99+', async () => {
    await poner(99);
    expect(side()?.textContent?.trim()).toBe('99');
    await poner(100);
    expect(side()?.textContent?.trim()).toBe('99+');
    expect(links()[0].getAttribute('aria-label')).toBe('Notificaciones, más de 99 sin leer');
    await poner(4321);
    expect(top()?.textContent?.trim()).toBe('99+');
  });

  it('se actualiza al marcar como leídas y desaparece al llegar a 0', async () => {
    await poner(3);
    expect(side()?.textContent?.trim()).toBe('3');
    api.count = 2;
    await store.marcarLeida('n1');
    await render();
    expect(side()?.textContent?.trim()).toBe('2');
    api.count = 0;
    await store.marcarTodasLeidas();
    await render();
    expect(side()).toBeNull();
    expect(top()).toBeNull();
  });

  it('sigue visible al cambiar de pantalla', async () => {
    await poner(6);
    const router = TestBed.inject(Router);
    for (const url of ['/lotes', '/trazabilidad', '/procesos']) {
      await router.navigateByUrl(url);
      await render();
      expect(side()?.textContent?.trim()).toBe('6');
      expect(top()?.textContent?.trim()).toBe('6');
    }
  });
});

describe('El shell mientras se guarda (FE-I2)', () => {
  let store: StoreService;
  let fixture: ComponentFixture<ShellComponent>;
  const main = () => fixture.nativeElement.querySelector('main') as HTMLElement;
  const estado = () => fixture.nativeElement.querySelector('[data-testid="saving"]') as HTMLElement | null;
  const barra = () => fixture.nativeElement.querySelector('progress') as HTMLProgressElement | null;
  async function render() { await fixture.whenStable(); fixture.detectChanges(); }

  beforeEach(async () => {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Producción', role: 'OPERATOR', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', component: PaginaFalsa }]), { provide: ApiService, useValue: new FakeApi() }] });
    store = TestBed.inject(StoreService);
    fixture = TestBed.createComponent(ShellComponent);
    await render();
    await new Promise(resolve => setTimeout(resolve, 20));
    await render();
  });

  it('no deja el contenido inerte: el foco y los controles siguen disponibles mientras se guarda', async () => {
    store.saving.set(true);
    await render();
    expect(estado()?.textContent).toContain('Guardando cambios…');
    expect(main().hasAttribute('inert')).toBe(false);
    expect(main().getAttribute('aria-busy')).toBe('true');
  });

  it('muestra una barra de progreso y el porcentaje solo durante una subida', async () => {
    store.saving.set(true);
    await render();
    expect(barra()).toBeNull();
    store.uploadProgress.set(45);
    await render();
    expect(estado()?.textContent).toContain('Subiendo… 45 %');
    expect(barra()?.value).toBe(45);
    expect(barra()?.getAttribute('aria-label')).toBe('Progreso de la subida');
    store.saving.set(false);
    store.uploadProgress.set(null);
    await render();
    expect(estado()).toBeNull();
    expect(barra()).toBeNull();
  });
});

describe('Avisos en el shell (FE-I8)', () => {
  let fixture: ComponentFixture<ShellComponent>;
  let ui: UiService;
  const avisos = () => [...fixture.nativeElement.querySelectorAll('.toast')] as HTMLElement[];
  async function render() { await fixture.whenStable(); fixture.detectChanges(); }

  beforeEach(async () => {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Producción', role: 'OPERATOR', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', component: PaginaFalsa }]), { provide: ApiService, useValue: new FakeApi() }] });
    ui = TestBed.inject(UiService);
    fixture = TestBed.createComponent(ShellComponent);
    await render();
  });

  it('un error es role="alert" y trae un botón para cerrarlo; uno informativo es role="status" y no lo trae', async () => {
    ui.toast('Fallo grave', true);
    ui.toast('Guardado');
    await render();
    const [error, info] = avisos();
    expect(error.getAttribute('role')).toBe('alert');
    expect(error.querySelector('button.toast-close')).not.toBeNull();
    expect(info.getAttribute('role')).toBe('status');
    expect(info.querySelector('button')).toBeNull();
  });

  it('cerrar un error lo quita de pantalla', async () => {
    ui.toast('Fallo grave', true);
    await render();
    (avisos()[0].querySelector('button.toast-close') as HTMLButtonElement).click();
    await render();
    expect(avisos()).toHaveLength(0);
  });
});

describe('Menú lateral y campana (FE-I8)', () => {
  let fixture: ComponentFixture<ShellComponent>;
  async function render() { await fixture.whenStable(); fixture.detectChanges(); }
  beforeEach(async () => {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Producción', role: 'OPERATOR', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', component: PaginaFalsa }]), { provide: ApiService, useValue: new FakeApi() }] });
    fixture = TestBed.createComponent(ShellComponent);
    await render();
  });

  it('el nombre de la empresa, que se recorta con puntos suspensivos, repite su texto completo en title', () => {
    const nombre = fixture.nativeElement.querySelector('.ctx-text b') as HTMLElement;
    expect(nombre.textContent!.trim().length).toBeGreaterThan(0);
    expect(nombre.getAttribute('title')).toBe(nombre.textContent!.trim());
  });

  it('el contador va junto a la campana (flex), no encima de ella (position: absolute)', () => {
    const fuente = readFileSync(join(process.cwd(), 'src/app/layout/shell.component.ts'), 'utf8');
    const regla = (selector: string) => new RegExp('\\n\\s*' + selector.replace(/[.]/g, '\\.') + ' \\{([^}]*)\\}').exec(fuente)![1];
    expect(regla('.dot')).not.toContain('position: absolute');
    expect(regla('.bell')).toContain('display: inline-flex');
    expect(regla('.bell-top')).not.toContain('position');
    expect(fuente).not.toMatch(/\.bell-top \.dot/);
  });
});
