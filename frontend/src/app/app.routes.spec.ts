import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';

@Component({ template: '' })
class Destino {}

describe('rutas', () => {
  it('/fallas redirige a la pestaña Fallas de analítica sin perder ?seccion=fallas', async () => {
    const find = (list: Route[]): Route | undefined => { for (const r of list) { if (r.path === 'fallas' && r.redirectTo) return r; const c = r.children && find(r.children); if (c) return c; } return undefined; };
    const redirect = find(routes);
    expect(redirect, 'existe la ruta de redirección /fallas').toBeTruthy();
    // la ruta real de redirección, con el router real; solo el destino se sustituye por un componente vacío
    TestBed.configureTestingModule({ providers: [provideRouter([redirect!, { path: 'analitica', component: Destino }])] });
    await RouterTestingHarness.create('/fallas');
    const router = TestBed.inject(Router);
    expect(router.url).toBe('/analitica?seccion=fallas');
  });
});
