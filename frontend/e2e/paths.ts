import { existsSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.cwd(), '..');

export const composeProject = (): string => process.env['TRAZATEX_COMPOSE_PROJECT'] ?? 'trazatex-platform';

export const isolatedProject = (): boolean =>
  /^trazatex-e2e-final/.test(composeProject()) || composeProject() === 'trazatex-portability-proof';

export function localDir(): string {
  const explicit = process.env['TRAZATEX_LOCAL_DIR'];
  if (explicit) return resolve(explicit);
  if (process.env['TRAZATEX_COMPOSE_PROJECT'])
    throw new Error('Con TRAZATEX_COMPOSE_PROJECT definido hay que fijar TRAZATEX_LOCAL_DIR (carpeta privada fuera del proyecto): no se usa demo/.local.');
  return resolve(root, 'demo/.local');
}
export const localFile = (name: string): string => resolve(localDir(), name);

export const credentialsFile = (): string => process.env['TRAZATEX_CREDENTIALS_PATH'] ?? localFile('credentials.json');

export const authDir = (): string => resolve(process.env['TRAZATEX_AUTH_DIR'] ?? '.auth');

export const appUrl = (): string => (process.env['TRAZATEX_APP_URL'] ?? 'http://127.0.0.1:14200').replace(/\/$/, '');
export const landingUrl = (): string => (process.env['TRAZATEX_LANDING_URL'] ?? 'http://127.0.0.1:15173').replace(/\/$/, '');

export function resultsDir(): string {
  const dir = resolve(localDir(), 'results');
  mkdirSync(dir, { recursive: true, mode: 0o700 });
  return dir;
}

export const hasFile = (path: string): boolean => existsSync(path);
