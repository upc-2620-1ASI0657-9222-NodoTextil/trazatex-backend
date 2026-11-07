import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/** FE-M1: código y recursos muertos que se quitaron no deben volver, y lo que queda en `public/` tiene que estar referenciado. */
const raiz = process.cwd();
const fuentes = (dir: string): string[] => readdirSync(dir).flatMap((n: string) => {
  const p = join(dir, n);
  return statSync(p).isDirectory() ? fuentes(p) : /\.(ts|css|html)$/.test(n) && !n.endsWith('.spec.ts') ? [p] : [];
});
const todo = fuentes(join(raiz, 'src')).map(f => readFileSync(f, 'utf8')).join('\n');

describe('Sin código ni recursos muertos (FE-M1)', () => {
  it('la página «módulo en desarrollo» y su imagen ya no existen ni se mencionan', () => {
    expect(existsSync(join(raiz, 'src/app/pages/modulo-en-desarrollo.page.ts'))).toBe(false);
    expect(existsSync(join(raiz, 'public/img/en-desarrollo.png'))).toBe(false);
    expect(todo).not.toMatch(/en-desarrollo|ModuloEnDesarrollo/);
  });

  it('los tres PNG de rol del login (≈1,5 MB) no están en public/ y nada los pide', () => {
    for (const f of ['administrador', 'dueno', 'operario']) expect(existsSync(join(raiz, 'public/login', f + '.png'))).toBe(false);
    expect(todo).not.toMatch(/login\/(administrador|dueno|operario)/);
  });

  it('las imágenes del login que usa la pantalla siguen existiendo', () => {
    for (const f of ['imagen1_login.webp', 'imagen2_login.jpg', 'imagen3_login.jpg']) {
      expect(todo).toContain('/login/' + f);
      expect(existsSync(join(raiz, 'public/login', f))).toBe(true);
    }
  });

  it('los miembros sin uso ya no están', () => {
    const lote = readFileSync(join(raiz, 'src/app/pages/lotes/lote-detalle.page.ts'), 'utf8');
    expect(lote).not.toMatch(/addEvDesdeInput|readonly tabs\b|readonly tab =|\.header-upload|\.back \{|\.big \{/);
    expect(readFileSync(join(raiz, 'src/app/pages/procesos/proceso-detalle.page.ts'), 'utf8')).not.toContain('addEvDesdeInput');
    expect(readFileSync(join(raiz, 'src/app/layout/shell.component.ts'), 'utf8')).not.toMatch(/empresasActivas|iniciales/);
    expect(readFileSync(join(raiz, 'src/app/pages/empresas.page.ts'), 'utf8')).not.toContain('lotesDe');
    expect(readFileSync(join(raiz, 'src/app/core/store.service.ts'), 'utf8')).not.toContain('proximoCodigoLote');
    expect(readFileSync(join(raiz, 'src/app/pages/fallas/falla-detalle.page.ts'), 'utf8')).not.toMatch(/async addEv\b/);
  });

  it('el responsable de un proceso se resuelve con nombreResponsable, no con nombreUsuario (FE-M14)', () => {
    expect(todo).not.toMatch(/nombreUsuario\(p\.operarioId\)/);
  });
});
