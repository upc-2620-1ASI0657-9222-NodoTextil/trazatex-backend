import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { TOAST_MIN_MS, UiService } from './ui';

describe('Avisos (toasts) legibles (FE-I8)', () => {
  let ui: UiService;
  beforeEach(() => { vi.useFakeTimers(); ui = TestBed.inject(UiService); });
  afterEach(() => vi.useRealTimers());

  it('un error no se retira solo, por mucho que pase el tiempo', () => {
    ui.toast('No se pudo guardar. Revisa los datos antes de repetir.', true);
    vi.advanceTimersByTime(10 * 60 * 1000);
    expect(ui.toasts()).toHaveLength(1);
  });

  it('un error desaparece solo cuando la persona lo cierra, sin tocar los demás avisos', () => {
    ui.toast('Falló A', true);
    ui.toast('Falló B', true);
    ui.dismissToast(ui.toasts()[0].id);
    expect(ui.toasts().map(t => t.text)).toEqual(['Falló B']);
  });

  it('un aviso informativo se retira tras el tiempo mínimo, y más tarde si el texto es largo', () => {
    ui.toast('Guardado');
    vi.advanceTimersByTime(TOAST_MIN_MS - 1);
    expect(ui.toasts()).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(ui.toasts()).toHaveLength(0);

    const largo = 'x'.repeat(300);
    ui.toast(largo);
    vi.advanceTimersByTime(300 * 45 - 1);
    expect(ui.toasts()).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(ui.toasts()).toHaveLength(0);
  });
});
