import { test, expect } from './base';
import { appUrl, landingUrl } from './paths';
test('un visitante no puede abrir la aplicación ni elegir una identidad de demostración', async ({
  page,
  request,
}) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('/login?como=dueno&empresa=EB&ir=/empresas');
  await expect(page).toHaveURL(/login/);
  await expect(page.getByRole('button', { name: 'Ingresar', exact: true })).toBeVisible();
  // El selector de rol es solo una ayuda visual (FE-I3): la URL (?como=dueno) no preselecciona ninguna identidad de demostración.
  await expect(page.getByRole('button', { name: 'Dueño', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await expect(page.getByRole('button', { name: 'Operario', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.goto('/empresas');
  await expect(page).toHaveURL(/login/);
  expect((await request.get('/api/companies')).status()).toBe(401);
  expect((await request.get('/api/batches')).status()).toBe(401);
  expect(errors).toEqual([]);
});
test('el formulario muestra el rechazo real del servidor', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Correo', { exact: true }).fill('absent-' + Date.now() + '@example.test');
  await page.getByLabel('Contraseña', { exact: true }).fill('Incorrect-Password-9!');
  const response = page.waitForResponse((r) => r.url().endsWith('/api/auth/login'));
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  expect((await response).status()).toBe(401);
  await expect(page.getByRole('alert')).toContainText('credenciales');
  await expect(page).toHaveURL(/login/);
});
test('invitación inexistente muestra el error del backend', async ({ page }) => {
  await page.goto('/invitations/nonexistent-test-token');
  await expect(page.getByRole('alert')).not.toBeEmpty();
  await expect(page.getByRole('button', { name: 'Crear cuenta' })).toHaveCount(0);
});
test('landing conserva recursos, enlaces y navegación en móvil', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(landingUrl());
  await expect(page.getByRole('link', { name: 'Ir al login' }).first()).toHaveAttribute(
    'href',
    appUrl() + '/login',
  );
  expect(await page.locator('body').evaluate((el) => el.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole('link', { name: 'Ir al login' }).first().click();
  await expect(page).toHaveURL(appUrl() + '/login');
  expect(errors).toEqual([]);
});

test('la aplicación carga las fuentes Arvo y Bukhari aprobadas', async ({ page }) => {
  await page.goto('/login');
  const loaded = await page.evaluate(async () => {
    const arvo = await document.fonts.load('16px Arvo');
    const bukhari = await document.fonts.load('16px "Bukhari Script"');
    return { arvo: arvo.length, bukhari: bukhari.length };
  });
  expect(loaded.arvo).toBeGreaterThan(0);
  expect(loaded.bukhari).toBeGreaterThan(0);
  await expect(page.locator('.cover-copy h1')).toHaveCSS('font-family', /Bukhari Script/);
  await expect(page.locator('.login-card h2')).toHaveCSS('font-family', /Arvo/);
  await expect(page.locator('.login-card h2')).toHaveCSS('font-size', '22px');
  await expect(page.locator('body')).toHaveCSS('font-family', /Arvo/);
});

test('login: aclara que el rol lo define la cuenta y no hay un h1 duplicado (FE-M4, FE-I8)', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByText('El rol lo define tu cuenta.', { exact: true })).toBeVisible();
  await expect(page.locator('h1')).toHaveCount(1);
});
