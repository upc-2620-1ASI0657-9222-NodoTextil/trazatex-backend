import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/** La configuración del Nginx de `web` y su Dockerfile, tal como se publican (FE-I6, FE-M10). */
const plataforma = (archivo: string) => readFileSync(join(process.cwd(), '..', 'backend', 'infra', 'platform', archivo), 'utf8').replace(/\r\n/g, '\n');
const cabeceras = plataforma('web.security-headers.conf');
const nginx = plataforma('web.nginx.conf');
const dockerfile = plataforma('web.Dockerfile');
const directivas = () => /add_header Content-Security-Policy "([^"]+)"/.exec(cabeceras)![1].split(';').map(d => d.trim());
const sinComentarios = (texto: string) => texto.split('\n').filter(l => !l.trim().startsWith('#')).join('\n');

describe('Cabeceras de seguridad del Nginx de la web (FE-I6)', () => {
  it('CSP con lo que la app necesita y nada más: mismo origen, sin scripts en línea, sin marcos, sin plugins', () => {
    const d = directivas();
    expect(d).toContain("default-src 'self'");
    expect(d.find(x => x.startsWith('script-src'))).not.toMatch(/'unsafe-inline'|'unsafe-eval'|\*/);
    expect(d).toContain("frame-ancestors 'none'");
    expect(d).toContain("object-src 'none'");
    expect(d).toContain("base-uri 'self'");
    expect(d).toContain("connect-src 'self'");
    expect(d).toContain("style-src 'self' 'unsafe-inline'");
    expect(d.find(x => x.startsWith('img-src'))).toContain('blob:');
  });

  it('el hash del script es el SHA-256 real del manejador onload que Angular escribe en index.html', () => {
    const hash = createHash('sha256').update("this.media='all'").digest('base64');
    expect(directivas().find(x => x.startsWith('script-src'))).toContain("'unsafe-hashes' 'sha256-" + hash + "'");
  });

  it('X-Content-Type-Options, Referrer-Policy y X-Frame-Options siempre (también en respuestas de error)', () => {
    const activas = sinComentarios(cabeceras);
    expect(activas).toContain('add_header X-Content-Type-Options "nosniff" always;');
    expect(activas).toContain('add_header Referrer-Policy "no-referrer" always;');
    expect(activas).toContain('add_header X-Frame-Options "DENY" always;');
    expect(activas.match(/add_header /g)!.length).toBe(4);
    expect(activas.match(/ always;/g)!.length).toBe(4);
  });

  it('sin HSTS: la web va por HTTP y Strict-Transport-Security solo vale sobre TLS', () => {
    expect(sinComentarios(cabeceras) + sinComentarios(nginx)).not.toMatch(/Strict-Transport-Security/i);
  });

  it('las cabeceras se incluyen en el server y en todo location que declare sus propios add_header (nginx no los hereda)', () => {
    const conf = sinComentarios(nginx);
    expect(conf).toMatch(/server \{[^]*?include \/etc\/nginx\/web-security-headers\.conf;/);
    const bloques = [...conf.matchAll(/location [^{]+\{([^}]*)\}/g)].map(m => m[1]);
    const conAddHeader = bloques.filter(b => b.includes('add_header'));
    expect(conAddHeader.length).toBeGreaterThan(0);
    for (const b of conAddHeader) expect(b).toContain('include /etc/nginx/web-security-headers.conf;');
  });

  it('index.html (y toda ruta de la SPA, que termina allí) va con Cache-Control: no-cache', () => {
    const conf = sinComentarios(nginx);
    expect(/location = \/index\.html \{([^}]*)\}/.exec(conf)![1]).toContain('add_header Cache-Control "no-cache" always;');
    expect(conf).toContain('location / { try_files $uri /index.html; }');
  });

  it('el Dockerfile copia el archivo de cabeceras a la ruta que el Nginx incluye', () => {
    expect(dockerfile).toContain('COPY backend/infra/platform/web.security-headers.conf /etc/nginx/web-security-headers.conf');
    expect(dockerfile).toContain('COPY backend/infra/platform/web.nginx.conf /etc/nginx/conf.d/default.conf');
  });
});

describe('Imágenes del Dockerfile web con etiqueta fija (FE-M10)', () => {
  const imagenes = [...dockerfile.matchAll(/^FROM (\S+)/gm)].map(m => m[1]);
  it('hay dos imágenes, ninguna flotante (sin latest, sin solo «alpine»)', () => {
    expect(imagenes).toHaveLength(2);
    for (const imagen of imagenes) {
      const etiqueta = imagen.split(':')[1] ?? '';
      expect(etiqueta, imagen).toMatch(/^\d+\.\d+/);
      expect(etiqueta, imagen).not.toBe('latest');
    }
  });
});
