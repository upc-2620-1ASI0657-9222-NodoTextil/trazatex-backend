// Detecta rutas del backend sin clasificar: lee los controladores de ../backend (solo lectura, sin dependencias) y
// comprueba que cada ruta /api/** esté en src/app/core/service-routes.ts. También avisa de entradas del catálogo que
// ya no existen en el backend. Salida 1 si falta alguna; 0 si todo coincide. Si no hay carpeta ../backend, se omite.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const backend = join(root, '..', 'backend');
if (!existsSync(backend)) {
  console.log('check-routes: no hay carpeta ../backend; se omite la comprobación.');
  process.exit(0);
}

const walk = (dir) => readdirSync(dir).flatMap((name) => {
  if (['node_modules', 'target', '.git'].includes(name)) return [];
  const full = join(dir, name);
  return statSync(full).isDirectory() ? walk(full) : [full];
});
const normalize = (path) => path.replace(/\{[^}]*\}/g, '{}').replace(/\/+$/, '') || '/';

// Rutas del backend: @RequestMapping de clase + @Get/Post/Put/Patch/DeleteMapping de método.
const found = new Map();
for (const file of walk(backend)) {
  if (!/Controller[^/]*\.java$/.test(file) || file.includes(`${join('src', 'test')}`)) continue;
  const source = readFileSync(file, 'utf8');
  const base = /@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"/.exec(source)?.[1] ?? '';
  const mapping = /@(?:org\.springframework\.web\.bind\.annotation\.)?(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?[^)]*\))?/g;
  for (const match of source.matchAll(mapping)) {
    const own = match[2] ?? '';
    const path = own.startsWith('/api') ? own : base + own;
    if (!path.startsWith('/api')) continue; // /internal/** no sale por el Gateway
    found.set(`${match[1].toUpperCase()} ${normalize(path.replace(/^\/api/, ''))}`, `${match[1].toUpperCase()} ${path}  (${file.slice(backend.length + 1)})`);
  }
}

// Catálogo del front: una entrada por línea con el formato de service-routes.ts.
const catalog = new Map();
const text = readFileSync(join(root, 'src', 'app', 'core', 'service-routes.ts'), 'utf8');
for (const match of text.matchAll(/\{\s*method:\s*'([A-Z]+)',\s*path:\s*'([^']+)',\s*clase:\s*'([a-z-]+)'/g)) {
  catalog.set(`${match[1]} ${normalize(match[2])}`, match[3]);
}

const missing = [...found].filter(([key]) => !catalog.has(key));
const stale = [...catalog.keys()].filter((key) => !found.has(key));
console.log(`check-routes: ${found.size} rutas en el backend, ${catalog.size} en el catálogo.`);
if (stale.length) console.warn('Entradas del catálogo que ya no están en el backend:\n  ' + stale.join('\n  '));
if (missing.length) {
  console.error('\nRutas del backend SIN CLASIFICAR (añádelas a src/app/core/service-routes.ts y, si necesitan un plazo propio, a timeouts.ts):');
  for (const [, label] of missing) console.error('  ' + label);
  process.exit(1);
}
console.log('check-routes: todas las rutas del backend están clasificadas.');
