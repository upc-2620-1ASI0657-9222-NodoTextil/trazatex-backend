import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DENSITY } from './density';

@Component({
  imports: [...DENSITY],
  template: `
    <app-subnav panel="demo" [items]="items" [(active)]="vista" label="Vistas de prueba" />
    <div appTabPanel="demo" [tabActive]="vista()"><p>contenido {{ vista() }}</p></div>`,
})
class Anfitrion {
  items = [{ id: 'a', label: 'Alfa', count: 2 }, { id: 'b c', label: 'Beta' }, { id: 'g', label: 'Gamma' }];
  vista = signal('a');
}

describe('Pestañas ARIA completas (FE-I8)', () => {
  let fixture: ComponentFixture<Anfitrion>;
  const tabs = () => [...fixture.nativeElement.querySelectorAll('[role="tab"]')] as HTMLButtonElement[];
  const panel = () => fixture.nativeElement.querySelector('[role="tabpanel"]') as HTMLElement;
  const key = (index: number, k: string) => {
    tabs()[index].dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true }));
    fixture.detectChanges();
  };

  beforeEach(() => {
    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
  });

  it('cada pestaña tiene id, aria-selected y aria-controls hacia el panel; el panel apunta a la pestaña activa', () => {
    expect(tabs().map(t => t.getAttribute('aria-selected'))).toEqual(['true', 'false', 'false']);
    expect(new Set(tabs().map(t => t.id)).size).toBe(3);
    expect(tabs().every(t => t.getAttribute('aria-controls') === panel().id)).toBe(true);
    expect(panel().id).toBe('panel-demo');
    expect(panel().getAttribute('aria-labelledby')).toBe(tabs()[0].id);
    fixture.componentInstance.vista.set('b c');
    fixture.detectChanges();
    expect(panel().getAttribute('aria-labelledby')).toBe(tabs()[1].id);
    expect(tabs()[1].id).not.toContain(' ');
  });

  it('solo la pestaña activa entra en el orden de tabulación', () => {
    expect(tabs().map(t => t.getAttribute('tabindex'))).toEqual(['0', '-1', '-1']);
  });

  it('las flechas, Inicio y Fin mueven la selección y el foco, con vuelta circular', () => {
    key(0, 'ArrowRight');
    expect(fixture.componentInstance.vista()).toBe('b c');
    expect(document.activeElement).toBe(tabs()[1]);
    key(1, 'End');
    expect(fixture.componentInstance.vista()).toBe('g');
    key(2, 'ArrowRight');
    expect(fixture.componentInstance.vista()).toBe('a');
    key(0, 'ArrowLeft');
    expect(fixture.componentInstance.vista()).toBe('g');
    key(2, 'Home');
    expect(fixture.componentInstance.vista()).toBe('a');
  });

  it('otras teclas no cambian la selección', () => {
    key(0, 'x');
    expect(fixture.componentInstance.vista()).toBe('a');
  });

  it('el clic selecciona la pestaña', () => {
    tabs()[2].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.vista()).toBe('g');
    expect(tabs()[2].getAttribute('aria-selected')).toBe('true');
  });
});
