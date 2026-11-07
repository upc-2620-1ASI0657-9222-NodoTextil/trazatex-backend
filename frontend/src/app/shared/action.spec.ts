import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActionComponent } from './ui';

@Component({
  imports: [ActionComponent],
  template: `
    <app-action id="a" [reason]="razon()" (run)="clics = clics + 1">Aceptar</app-action>
    <app-action id="b" [reason]="'Otra razón distinta.'">Rechazar</app-action>`,
})
class Anfitrion {
  razon = signal<string | null>('Falta un dato obligatorio.');
  clics = 0;
}

describe('Razón visible de un botón deshabilitado (FE-I8)', () => {
  let fixture: ComponentFixture<Anfitrion>;
  const boton = (id: string) => fixture.nativeElement.querySelector('#' + id + ' button') as HTMLButtonElement;
  const razon = (id: string) => fixture.nativeElement.querySelector('#' + id + ' .action-reason') as HTMLElement | null;
  beforeEach(() => { fixture = TestBed.createComponent(Anfitrion); fixture.detectChanges(); });

  it('con razón, el botón está deshabilitado y la razón está escrita en pantalla, enlazada por aria-describedby', () => {
    expect(boton('a').disabled).toBe(true);
    expect(razon('a')?.textContent?.trim()).toBe('Falta un dato obligatorio.');
    expect(boton('a').getAttribute('aria-describedby')).toBe(razon('a')!.id);
  });

  it('cada botón enlaza SU razón (ids distintos)', () => {
    expect(razon('a')!.id).not.toBe(razon('b')!.id);
    expect(boton('b').getAttribute('aria-describedby')).toBe(razon('b')!.id);
  });

  it('sin razón el botón se puede usar y no queda texto ni aria-describedby', () => {
    fixture.componentInstance.razon.set(null);
    fixture.detectChanges();
    expect(boton('a').disabled).toBe(false);
    expect(razon('a')).toBeNull();
    expect(boton('a').hasAttribute('aria-describedby')).toBe(false);
    boton('a').click();
    expect(fixture.componentInstance.clics).toBe(1);
  });
});
