import { Component, inject } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ApiService } from '../core/api.service';
import { StoreService } from '../core/store.service';
import { UiService } from '../shared/ui';
import { NotificacionesPage } from './notificaciones.page';

class FakeApi {
  async request<T>(_method: string, path: string): Promise<T> {
    if (path === '/notifications/unread-count') return { count: 0 } as T;
    if (path.startsWith('/notifications?')) return { items: [], page: 0, size: 6, totalElements: 0, totalPages: 0 } as T;
    throw new Error('sin respuesta de prueba: ' + path);
  }
  async all<T>(): Promise<T[]> { return []; }
}

@Component({ imports: [NotificacionesPage, NgTemplateOutlet], template: `<app-notificaciones /><ng-container [ngTemplateOutlet]="ui.headerActions()" />` })
class Anfitrion { readonly ui = inject(UiService); }

describe('Marcar como leídas en la bandeja: razón visible (FE-I8)', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let store: StoreService;
  const boton = () => [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent?.includes('Marcar como leídas')) as HTMLButtonElement;
  const razon = () => fixture.nativeElement.querySelector('#leidas-razon') as HTMLElement | null;
  async function render() { await fixture.whenStable(); fixture.detectChanges(); }

  beforeEach(async () => {
    sessionStorage.clear();
    sessionStorage.setItem('trazatex.auth', JSON.stringify({
      token: 'unit-test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(),
      user: { id: 'u1', companyId: 'c1', email: 'a@example.test', firstName: 'Ana', lastName: 'Pérez', position: 'Producción', role: 'OPERATOR', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
    }));
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: new FakeApi() }] });
    store = TestBed.inject(StoreService);
    fixture = TestBed.createComponent(Anfitrion);
    await render();
    await render();
  });

  it('sin notificaciones sin leer, el botón está deshabilitado y el motivo se lee en pantalla', () => {
    expect(boton().disabled).toBe(true);
    expect(razon()?.textContent).toContain('No hay notificaciones sin leer.');
    expect(boton().getAttribute('aria-describedby')).toBe('leidas-razon');
  });

  it('con notificaciones sin leer, el botón se activa y la razón desaparece', async () => {
    store.unreadCount.set(3);
    await render();
    expect(boton().disabled).toBe(false);
    expect(razon()).toBeNull();
    expect(boton().hasAttribute('aria-describedby')).toBe(false);
  });
});
