import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { QrTagComponent } from './ui';

const LOTE = { id: 'TT-ALG-0001' } as never;

describe('Etiqueta QR imprimible y descargable (FE-M15)', () => {
  function crear(actions: boolean) {
    const fixture = TestBed.createComponent(QrTagComponent);
    fixture.componentRef.setInput('lote', LOTE);
    fixture.componentRef.setInput('actions', actions);
    fixture.detectChanges();
    return fixture;
  }
  afterEach(() => { vi.restoreAllMocks(); document.body.classList.remove('printing-qr'); });

  it('la etiqueta lleva el código del lote en texto, bajo el QR', () => {
    const dom = crear(false).nativeElement as HTMLElement;
    expect(dom.querySelector('figcaption b')?.textContent).toBe('TT-ALG-0001');
    expect(dom.querySelector('figure.qr-printable svg path')).not.toBeNull();
  });

  it('los botones de imprimir y descargar solo aparecen cuando se piden', () => {
    expect((crear(false).nativeElement as HTMLElement).querySelector('.qr-actions')).toBeNull();
    const botones = [...(crear(true).nativeElement as HTMLElement).querySelectorAll('.qr-actions button')].map(b => b.textContent?.trim());
    expect(botones).toEqual(['Imprimir etiqueta', 'Descargar QR (SVG)']);
  });

  it('descargar entrega un SVG completo con el nombre qr-<lote>.svg', async () => {
    const fixture = crear(true);
    const blobs: Blob[] = [];
    (URL as unknown as { createObjectURL: (b: Blob) => string }).createObjectURL = (b: Blob) => { blobs.push(b); return 'blob:x'; };
    (URL as unknown as { revokeObjectURL: () => void }).revokeObjectURL = () => undefined;
    const nombres: string[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { nombres.push(this.download); });
    ((fixture.nativeElement as HTMLElement).querySelectorAll('.qr-actions button')[1] as HTMLButtonElement).click();
    expect(nombres).toEqual(['qr-TT-ALG-0001.svg']);
    expect(blobs[0].type).toBe('image/svg+xml');
    const texto = await blobs[0].text();
    expect(texto).toMatch(/^<svg xmlns="http:\/\/www\.w3\.org\/2000\/svg" viewBox="0 0 \d+ \d+"/);
    expect(texto).toContain('<rect');
    expect(texto).toContain('fill="#ffffff"');
    expect(texto).toMatch(/<path d="M\d+ \d+h1v1h-1z/);
  });

  it('imprimir marca el body solo mientras dura la impresión y llama a window.print', () => {
    const fixture = crear(true);
    let durante = false;
    const print = vi.spyOn(window, 'print').mockImplementation(() => { durante = document.body.classList.contains('printing-qr'); });
    ((fixture.nativeElement as HTMLElement).querySelectorAll('.qr-actions button')[0] as HTMLButtonElement).click();
    expect(print).toHaveBeenCalledTimes(1);
    expect(durante).toBe(true);
    expect(document.body.classList.contains('printing-qr')).toBe(true);
    window.dispatchEvent(new Event('afterprint'));
    expect(document.body.classList.contains('printing-qr')).toBe(false);
  });

  it('si window.print falla no deja el body marcado', () => {
    const fixture = crear(true);
    vi.spyOn(window, 'print').mockImplementation(() => { throw new Error('bloqueado'); });
    fixture.componentInstance.imprimir();
    expect(document.body.classList.contains('printing-qr')).toBe(false);
  });

  it('styles.css tiene @media print que oculta todo salvo la etiqueta y los botones', () => {
    const css = readFileSync(join(process.cwd(), 'src', 'styles.css'), 'utf8');
    expect(css).toMatch(/@media print \{[^]*body\.printing-qr \* \{ visibility: hidden !important; \}/);
    expect(css).toContain('body.printing-qr .qr-printable, body.printing-qr .qr-printable * { visibility: visible !important; }');
    expect(css).toContain('body.printing-qr .qr-actions { display: none !important; }');
    // el resto de la aplicación sale del flujo (visibility:hidden dejaba varias páginas y repetía la etiqueta fixed)
    expect(css).toContain('body.printing-qr :not(.qr-printable):not(.qr-printable *):not(:has(.qr-printable)) { display: none !important; }');
  });
});
