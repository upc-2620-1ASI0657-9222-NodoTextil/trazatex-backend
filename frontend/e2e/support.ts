import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { randomBytes } from 'node:crypto';
import { APIRequestContext, Page, expect } from '@playwright/test';
import { authDir, composeProject, credentialsFile } from './paths';
const root = resolve(process.cwd(), '..');
const backendEnv = readFileSync(process.env['TRAZATEX_TEST_ENV_FILE'] ?? resolve(root, '.env'), 'utf8');
export const env = Object.fromEntries(
  backendEnv
    .split(/\r?\n/)
    .filter((l) => /^[A-Z0-9_]+=/.test(l))
    .map((l) => {
      const index = l.indexOf('=');
      return [l.slice(0, index), l.slice(index + 1).replace(/^['"]|['"]$/g, '')];
    }),
);
export async function chooseLoginRole(page: Page, role: string) {
  const label = /^(LICENSE_OWNER|owner)/.test(role) ? 'Dueño' : /^(COMPANY_ADMIN|admin)/.test(role) ? 'Administrador' : 'Operario';
  await page.getByRole('button', { name: label, exact: true }).click();
}
interface Account {
  email: string;
  password: string;
}
export function account(name: string, email?: string): Account {
  const fixture = process.env['TRAZATEX_CREDENTIALS_PATH'];
  if (fixture) {
    const aliases: Record<string, string> = { owner: 'owner', 'admin-a': 'admin/hilanderia', 'admin-b': 'admin/tejeduria',
      'operator-a': 'operator/hilanderia/1', 'operator-b': 'operator/tejeduria/1' };
    const found = JSON.parse(readFileSync(credentialsFile(), 'utf8')).accounts.find((item: { key: string }) => item.key === (aliases[name] ?? name));
    if (!found) throw new Error('Falta la cuenta requerida en la instalación de pruebas: ' + name);
    return { email: found.email, password: found.password };
  }
  const dir = authDir();
  mkdirSync(dir, { recursive: true, mode: 0o700 });
  const path = resolve(dir, 'accounts.json');
  const all = existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : {};
  if (!all[name]) {
    all[name] = {
      email: email ?? name + '.' + Date.now() + '@example.test',
      password: 'Tx9!' + randomBytes(24).toString('hex'),
    };
    writeFileSync(path, JSON.stringify(all), { mode: 0o600 });
  }
  return all[name];
}

export function psql(db: string, sql: string): string {
  const project = composeProject();
  const id = execFileSync('docker', ['ps', '-q', '--filter', 'label=com.docker.compose.project=' + project,
    '--filter', 'label=com.docker.compose.service=postgres'], { encoding: 'utf8' }).trim();
  if (!id || id.includes('\n')) throw new Error('No hay exactamente un PostgreSQL en ejecución en el proyecto ' + project + '.');
  return execFileSync('docker', ['exec', '-i', id, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', env['POSTGRES_USER'], '-d', db, '-tA', '-c', sql],
    { encoding: 'utf8' }).trim();
}
function invitationEvents(): unknown[] {
  const output = psql('orgaccess_db', "SELECT payload::text FROM outbox WHERE topic = 'orgaccess.invitation' ORDER BY created_at DESC");
  return output
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => JSON.parse(line));
}
function findInvitation(value: unknown, email: string): string | null {
  if (!value || typeof value !== 'object') return null;
  const object = value as Record<string, unknown>;
  if (object['email'] === email && typeof object['acceptUrl'] === 'string')
    return object['acceptUrl'];
  for (const child of Object.values(object)) {
    const found = findInvitation(child, email);
    if (found) return found;
  }
  return null;
}
export function invitationUrl(email: string): string {
  for (const event of invitationEvents()) {
    const found = findInvitation(event, email);
    if (found) return found;
  }
  throw new Error('No se encontró la invitación de la cuenta de prueba en el outbox aislado.');
}
export async function ensureAccount(request: APIRequestContext, user: Account) {
  const login = await request.post('/api/auth/login', { data: user });
  if (login.ok()) return await login.json();
  const token = invitationUrl(user.email).split('/').pop()!;
  const response = await request.post('/api/invitations/' + token + '/accept', {
    data: {
      firstName: 'Prueba',
      lastName: 'Integración',
      password: user.password,
      passwordConfirmation: user.password,
    },
  });
  expect(response.ok(), 'Aceptación de cuenta de prueba: ' + (await response.text())).toBe(true);
  const result = await request.post('/api/auth/login', { data: user });
  expect(result.ok()).toBe(true);
  return await result.json();
}

export function invitationId(email: string): string {
  const find = (value: unknown): string | null => {
    if (!value || typeof value !== 'object') return null;
    const object = value as Record<string, unknown>;
    if (object['email'] === email && typeof object['invitationId'] === 'string')
      return object['invitationId'];
    for (const child of Object.values(object)) {
      const id = find(child);
      if (id) return id;
    }
    return null;
  };
  for (const event of invitationEvents()) {
    const id = find(event);
    if (id) return id;
  }
  throw new Error('No se encontró la invitación de prueba en el outbox aislado.');
}

export async function confirmNext(page: Page, accept = true) {
  void page
    .getByRole('dialog', { name: 'Confirmar acción', exact: true })
    .getByRole('button', { name: accept ? 'Confirmar' : 'Cancelar', exact: true })
    .click({ timeout: 30000 })
    .catch(() => undefined);
}

export async function unfold(page: Page, title: string | RegExp) {
  const head = page.locator('button.fold-head').filter({ has: page.locator('.fold-t', { hasText: title }) }).first();
  await head.waitFor({ state: 'visible', timeout: 30000 });
  if ((await head.getAttribute('aria-expanded')) !== 'true') await head.click();
  await expect(head).toHaveAttribute('aria-expanded', 'true');
}

export async function saveEvidence(page: Page) {
  await page.locator('app-evidence').getByRole('button', { name: 'Guardar evidencia', exact: true }).click();
}

export async function clickAndDecide(page: Page, button: ReturnType<Page['locator']>, accept = true) {
  await button.click();
  const dialog = page.getByRole('dialog', { name: 'Confirmar acción', exact: true });
  await dialog.getByRole('button', { name: accept ? 'Confirmar' : 'Cancelar', exact: true }).click();
  await expect(dialog).toHaveCount(0);
}
