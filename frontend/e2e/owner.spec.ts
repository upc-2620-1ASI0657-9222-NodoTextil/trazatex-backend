import { test, expect } from './base';
import { account, ensureAccount, env } from './support';
test('el dueño acepta su invitación, inicia sesión y conserva la identidad al recargar', async ({
  page,
  request,
}) => {
  test.setTimeout(120000);
  const owner = account('owner', env['TRAZATEX_OWNER_EMAIL']);
  await ensureAccount(request, owner);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('/login');
  await page.getByLabel('Correo', { exact: true }).fill(owner.email);
  await page.getByLabel('Contraseña', { exact: true }).fill(owner.password);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL(/empresas/);
  await expect(page.locator('app-shell')).toBeVisible();
  await expect(page.getByRole('group', { name: 'Selector de rol del prototipo' })).toHaveCount(0);
  await page.reload();
  await expect(page).toHaveURL(/empresas/);
  await page.getByRole('link', { name: 'Licencia', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Licencia');
  await expect(page.locator('body')).not.toContainText('NaN');
  await page.goto('/lotes');
  await expect(page).toHaveURL(/empresas/);
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page).toHaveURL(/login/);
  expect(errors).toEqual([]);
});
test('el Gateway impide al dueño consultar producción y detecta JWT alterado', async ({
  request,
}) => {
  const owner = account('owner', env['TRAZATEX_OWNER_EMAIL']);
  const login = await ensureAccount(request, owner);
  const headers = { Authorization: 'Bearer ' + login.token };
  expect((await request.get('/api/license', { headers })).status()).toBe(200);
  expect((await request.get('/api/batches', { headers })).status()).toBe(403);
  expect((await request.get('/api/machines', { headers })).status()).toBe(403);
  expect((await request.get('/api/users', { headers })).status()).toBe(403);
  expect(
    (
      await request.get('/api/license', { headers: { Authorization: 'Bearer invalid-token' } })
    ).status(),
  ).toBe(401);
});
