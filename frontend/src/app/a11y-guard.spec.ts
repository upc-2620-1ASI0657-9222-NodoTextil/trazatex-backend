import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

/** Recorre `src/app` y devuelve los .ts de producción (sin specs). */
function fuentes(dir: string): { file: string; text: string }[] {
  return readdirSync(dir).flatMap((name: string) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return fuentes(path);
    return name.endsWith('.ts') && !name.endsWith('.spec.ts') ? [{ file: path, text: readFileSync(path, 'utf8') }] : [];
  });
}

describe('Guardas de accesibilidad sobre las plantillas (FE-I8)', () => {
  const todas = fuentes(join(process.cwd(), 'src', 'app'));

  it('hay plantillas que revisar', () => expect(todas.length).toBeGreaterThan(30));

  it('todo <p class="err"> es un role="alert": los errores se anuncian a lectores de pantalla', () => {
    const sin = todas.flatMap(({ file, text }) => [...text.matchAll(/<p class="err"[^>]*>/g)].filter(m => !m[0].includes('role="alert"')).map(() => file));
    expect(sin).toEqual([]);
    expect(todas.some(({ text }) => text.includes('<p class="err" role="alert"'))).toBe(true);
  });

  it('ningún <main> se vuelve inerte mientras se guarda (el foco no se pierde)', () => {
    expect(todas.filter(({ text }) => /<main[^>]*inert/.test(text)).map(({ file }) => file)).toEqual([]);
  });

  it('cada app-subnav declara el panel que controla', () => {
    const sin = todas.flatMap(({ file, text }) => [...text.matchAll(/<app-subnav\b[^>]*?(?:\/>|>)/gs)].filter(m => !/\bpanel="/.test(m[0])).map(() => file));
    expect(sin).toEqual([]);
  });

  it('un solo h1 por página: el del shell (role=heading nivel 1); las páginas del shell usan h2', () => {
    const h1 = todas.filter(({ text }) => /<h1\b/.test(text)).map(({ file }) => file.split('/').pop());
    // Fuera del shell, con su propio h1: la portada de acceso y la invitación.
    expect(h1.sort()).toEqual(['invitation.page.ts', 'login.page.ts']);
    const shell = todas.find(({ file }) => file.endsWith('shell.component.ts'))!;
    expect(shell.text).toContain('role="heading" aria-level="1"');
  });
});

/** Hojas de estilo reales de `src/`: el contraste se calcula con los valores que de verdad se publican. */
const css = (name: string) => readFileSync(join(process.cwd(), 'src', name), 'utf8');
const luminancia = (hex: string) => {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(c => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const contraste = (a: string, b: string) => {
  const [claro, oscuro] = [luminancia(a), luminancia(b)].sort((x, y) => y - x);
  return (claro + 0.05) / (oscuro + 0.05);
};
const token = (name: string) => new RegExp('--' + name + ':\\s*(#[0-9a-fA-F]{6})').exec(css('styles.css'))![1];

describe('Contraste de texto >= 4,5:1 (FE-I8)', () => {
  it('el placeholder del login sobre su campo', () => {
    const placeholder = /\.login-field input::placeholder \{ color: (#[0-9a-f]{6})/.exec(readFileSync(join(process.cwd(), 'src/app/pages/login.page.ts'), 'utf8'))![1];
    const campo = /background: (#f9f6ef)/.exec(readFileSync(join(process.cwd(), 'src/app/pages/login.page.ts'), 'utf8'))![1];
    expect(contraste(placeholder, campo)).toBeGreaterThanOrEqual(4.5);
  });

  it('el valor de la primera tarjeta de KPI (p. ej. «Lotes del periodo») va en crema sobre el verde oscuro de la tarjeta', () => {
    const fondo = token('green-800');
    expect(css('polish.css')).toMatch(/\.kpi:nth-child\(1\) \{\s*background: var\(--green-800\)/);
    expect(css('section-finishes.css')).toContain('app-shell .kpi:nth-child(1) :is(.v, small, .capacity-percent) { color: var(--cream); }');
    expect(contraste(token('cream'), fondo)).toBeGreaterThanOrEqual(4.5);
    // lo que había antes: verde oscuro sobre verde oscuro
    expect(contraste(token('green-900'), fondo)).toBeLessThan(1.2);
  });

  it('el kraft como texto sobre crema es la variante oscura --kraft-ink; el kraft claro solo se usa sobre verde', () => {
    expect(contraste(token('kraft-ink'), token('cream-50'))).toBeGreaterThanOrEqual(4.5);
    expect(contraste(token('kraft-ink'), token('cream'))).toBeGreaterThanOrEqual(4.5);
    expect(contraste(token('kraft'), token('cream-50'))).toBeLessThan(3);
    expect(contraste(token('kraft'), token('green-800'))).toBeGreaterThanOrEqual(4.5);
    expect(css('density.css')).toMatch(/\.subnav \.n \{[^}]*color: var\(--kraft-ink\)/);
  });

  it('la razón de un botón deshabilitado (carmín) se lee sobre crema', () => {
    expect(contraste(token('carmine'), token('cream-50'))).toBeGreaterThanOrEqual(4.5);
  });
});

describe('Foco visible (FE-I8)', () => {
  const hojas = ['styles.css', 'polish.css', 'density.css', 'section-finishes.css', 'ux-finish.css', 'app-theme-refined.css', 'landing-theme.css', 'app/pages/trazabilidad/trazabilidad.page.css'];

  it('hay un :focus-visible global y el cierre de los avisos de error tiene el suyo, legible sobre fondo oscuro', () => {
    expect(css('styles.css')).toMatch(/\n:focus-visible \{\s*outline: 2px solid/);
    expect(css('styles.css')).toMatch(/\.toast-close:focus-visible \{\s*outline: 2px solid var\(--cream\)/);
  });

  it('solo se quita el contorno donde el contenedor ya lo dibuja (focus-within / focus-visible propio) o no es interactivo', () => {
    const quitan = hojas.flatMap(h => css(h).split('\n').filter(l => /outline:\s*(none|0)\s*;/.test(l)).map(l => h + ' :: ' + l.trim().slice(0, 60)));
    expect(quitan).toEqual([
      'polish.css :: outline: none;',
      'landing-theme.css :: .trace-page .lot, .trace-page .proc, .trace-page .div, .trac',
      'app/pages/trazabilidad/trazabilidad.page.css :: outline: 0;',
    ]);
    expect(css('landing-theme.css')).toContain('.trace-page .node:focus-visible { outline: 2px solid var(--green-600)');
    expect(css('app/pages/trazabilidad/trazabilidad.page.css')).toContain('.search-field:focus-within { outline: 2px solid');
  });
});
