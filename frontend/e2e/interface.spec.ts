import { test, expect, Page } from './base';
import { readFileSync } from 'node:fs';
import { account, env, ensureAccount, invitationId, confirmNext, unfold } from './support';
import { credentialsFile } from './paths';
async function login(page: Page, name: string) {
  const user = account(name, name === 'owner' ? env['TRAZATEX_OWNER_EMAIL'] : undefined);
  await page.goto('/login');
  await page.getByLabel('Correo', { exact: true }).fill(user.email);
  await page.getByLabel('Contraseña', { exact: true }).fill(user.password);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.locator('app-shell')).toBeVisible();
  await expect(page.getByText('Cargando datos…', { exact: true })).toHaveCount(0);
}
for (const name of ['owner', 'admin-a', 'operator-a']) {
  test(
    name + ': navegación, buscadores, pestañas, filtros y paneles visibles',
    async ({ page }) => {
      test.setTimeout(180000);
      const errors: string[] = [];
      page.on('pageerror', (error) => errors.push(error.message));
      await login(page, name);
      const hrefs = await page
        .locator('.rail a.nav-item')
        .evaluateAll((links) => links.map((a) => a.getAttribute('href')!));
      for (const href of [...new Set(hrefs)]) {
        await page
          .locator('.rail a.nav-item[href="' + href + '"]')
          .first()
          .click();
        await expect(page).toHaveURL(new RegExp(href + '$'));
        // El único h1 es el del shell (<span role="heading" aria-level="1">), no un elemento <h1>: FE-I8.
        await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
        await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
        const searches = page.locator('input[aria-label^="Buscar"]:visible');
        for (let i = 0; i < (await searches.count()); i++) {
          await searches.nth(i).fill('zz-no-results-e2e');
          await searches.nth(i).fill('');
        }
        const tabs = page.locator('app-subnav button');
        const count = await tabs.count();
        for (let i = 0; i < count; i++) {
          await tabs.nth(i).click();
          await expect(tabs.nth(i)).toHaveAttribute('aria-selected', 'true');
        }
        if (count) await tabs.first().click();
        const filters = page.getByRole('button', { name: /^Filtros/ });
        if (await filters.count()) {
          await filters.first().click();
          await expect(page.locator('app-drawer .drawer')).toBeVisible();
          const selects = page.locator('app-drawer select:visible');
          for (let i = 0; i < (await selects.count()); i++) {
            const values = await selects
              .nth(i)
              .locator('option')
              .evaluateAll((options) => options.map((o) => (o as HTMLOptionElement).value));
            if (values.length > 1) {
              await selects.nth(i).selectOption(values.at(-1)!);
              await selects.nth(i).selectOption(values[0]);
            }
          }
        }
        const apply = page.getByRole('button', { name: /^(Aplicar|Ver resultados)$/ });
        if (await apply.count()) await apply.click();
        const rows = page.locator('button.row-card:visible');
        if (await rows.count()) {
          await rows.first().click();
          const drawer = page.locator('app-drawer .drawer');
          await expect(drawer).toBeVisible();
          await drawer.getByRole('button', { name: 'Cerrar', exact: true }).click();
        }
        const pager = page.locator('app-pager:visible');
        if (await pager.count()) {
          // el selector de tamaño solo existe cuando el total admite más de un tamaño (app-pager: sizes().length > 1)
          if (await pager.getByLabel('Elementos por página').count()) {
            await pager.getByLabel('Elementos por página').selectOption('20');
            await pager.getByLabel('Elementos por página').selectOption('6');
          }
          const next = pager.getByRole('button', { name: 'Página siguiente' });
          if (await next.isEnabled()) {
            await next.click();
            await pager.getByRole('button', { name: 'Página anterior' }).click();
          }
        }
        expect(await page.locator('body').innerText()).not.toContain('NaN');
      }
      expect(errors).toEqual([]);
    },
  );
}
test('administrador registra maquinaria y consulta su persistencia', async ({ page }) => {
  await login(page, 'admin-a');
  await page.getByRole('link', { name: 'Maquinaria', exact: true }).click();
  await page.getByRole('button', { name: 'Registrar maquinaria', exact: true }).click();
  const dialog = page.getByRole('dialog');
  const code = 'UI-' + Date.now();
  await dialog.getByLabel('Código interno', { exact: true }).fill(code);
  await dialog.getByLabel('Nombre', { exact: true }).fill('Hiladora desde interfaz');
  await dialog.getByLabel('Tipo', { exact: true }).fill('Hiladora');
  await dialog.getByRole('button', { name: 'Guardar', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByLabel('Buscar maquinaria').fill(code);
  await expect(page.locator('button.row-card')).toHaveCount(1);
  await page.reload();
  await expect(page.getByText('Cargando datos…', { exact: true })).toHaveCount(0);
  await page.getByLabel('Buscar maquinaria').fill(code);
  await expect(page.locator('button.row-card')).toHaveCount(1);
});
test('operario valida, registra un lote con imagen y consulta su detalle después de recargar', async ({
  page,
}) => {
  test.setTimeout(120000);
  await login(page, 'operator-a');
  await page.getByRole('link', { name: 'Lotes', exact: true }).click();
  await page.getByRole('button', { name: 'Registrar lote', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  await expect(dialog.locator('.err')).toContainText('cantidad');
  const name = 'Lote UI ' + String(Date.now()).slice(-6);
  await dialog.getByLabel('Nombre del lote', { exact: true }).fill(name);
  await dialog.getByLabel('Cantidad (kg)', { exact: true }).fill('20');
  await dialog.getByLabel('Origen geográfico', { exact: true }).fill('Perú');
  await dialog
    .getByLabel('Características técnicas de recepción', { exact: true })
    .fill('Inspección de recepción');
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  const png = await page.screenshot();
  await dialog
    .locator('input[type="file"]')
    .setInputFiles({ name: 'evidencia-ui.png', mimeType: 'image/png', buffer: png });
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  await dialog.getByRole('button', { name: 'Registrar lote', exact: true }).click();
  await expect(page).toHaveURL(/\/lotes\//);
  await page.getByRole('button', { name: /Identificación/ }).click();
  await expect(page.locator('body')).toContainText(name);
  await page.getByRole('button', { name: /Evidencias/ }).click();
  await expect(page.locator('body')).toContainText('evidencia-ui.png');
  await page.reload();
  await page.getByRole('button', { name: /Identificación/ }).click();
  await expect(page.locator('body')).toContainText(name);
  await page.getByRole('button', { name: /Evidencias/ }).click();
  await expect(page.locator('body')).toContainText('evidencia-ui.png');
});

test('administrador invita y cancela un operario con respuesta real', async ({ page }) => {
  await login(page, 'admin-a');
  await page.getByRole('link', { name: 'Usuarios', exact: true }).click();
  await page.getByRole('button', { name: 'Invitar Operario', exact: true }).click();
  const dialog = page.getByRole('dialog');
  const email = 'ui-invitation-' + Date.now() + '@example.test';
  await dialog.getByLabel('Nombre del invitado', { exact: true }).fill('Operario Invitado');
  await dialog.getByLabel('Correo', { exact: true }).fill(email);
  await dialog.getByLabel('Cargo', { exact: true }).fill('Producción');
  const created = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/invitations/operators') &&
      response.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Enviar invitación', exact: true }).click();
  expect((await created).status()).toBe(201);
  const row = page.locator('.row-card').filter({ hasText: email });
  await expect(row).toContainText('Pendiente');
  const cancel = async () => {
    const response = page.waitForResponse(
      (response) => response.url().endsWith('/cancel') && response.request().method() === 'POST',
    );
    confirmNext(page);
    await row.getByRole('button', { name: 'Cancelar invitación', exact: true }).click();
    return await response;
  };
  let cancelled = await cancel();
  if (cancelled.status() === 429) {
    await expect(page.locator('body')).toContainText('Demasiadas solicitudes');
    const seconds = Number(cancelled.headers()['retry-after']) || 1;
    await new Promise((resolve) => setTimeout(resolve, (seconds + 1) * 1000));
    cancelled = await cancel();
  }
  expect(cancelled.status()).toBe(200);
  await expect(row).toContainText('Cancelada');
  await expect(row.getByRole('button', { name: 'Cancelar invitación' })).toHaveCount(0);
});
test('administrador cambia el estado de su maquinaria y lo conserva al recargar', async ({
  page,
}) => {
  await login(page, 'admin-a');
  await page.getByRole('link', { name: 'Maquinaria', exact: true }).click();
  // La prueba registra su propia máquina (nueva = Activa): no depende de lo que dejaron otras pruebas ni de otra pasada.
  await page.getByRole('button', { name: 'Registrar maquinaria', exact: true }).click();
  const form = page.getByRole('dialog');
  const own = 'UI-' + Date.now();
  await form.getByLabel('Código interno', { exact: true }).fill(own);
  await form.getByLabel('Nombre', { exact: true }).fill('Hiladora de estado');
  await form.getByLabel('Tipo', { exact: true }).fill('Hiladora');
  await form.getByRole('button', { name: 'Guardar', exact: true }).click();
  await expect(form).toHaveCount(0);
  await page.getByRole('tab', { name: /^Todas/ }).click();
  await page.getByLabel('Buscar maquinaria').fill(own);
  await page.locator('button.row-card').first().click();
  const drawer = page.getByRole('dialog');
  await expect(drawer).toBeVisible();
  const change = async (name: string) => {
    const response = page.waitForResponse(
      (response) =>
        response.url().includes('/api/machines/') && response.request().method() === 'PATCH',
    );
    await drawer.getByRole('button', { name, exact: true }).click();
    expect((await response).status()).toBe(200);
    await expect(drawer.getByRole('button', { name, exact: true })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  };
  await change('Inactiva');
  await change('Activa');
  const code = await drawer.locator('.id').first().textContent();
  await page.reload();
  await page.getByRole('tab', { name: /^Todas/ }).click();
  await page.getByLabel('Buscar maquinaria').fill(code!.trim());
  await expect(page.locator('button.row-card')).toHaveCount(1);
  await expect(page.locator('button.row-card')).toContainText('Activa');
});
test('operario divide un lote y completa una transformación desde la interfaz', async ({
  page,
  request,
}) => {
  test.setTimeout(180000);
  const session = await ensureAccount(request, account('operator-a'));
  const response = await request.post('/api/batches', {
    headers: { Authorization: 'Bearer ' + session.token },
    data: {
      name: 'División UI ' + String(Date.now()).slice(-6),
      materialType: 'Fibra de algodón Pima',
      quantityKg: 20,
      composition: [{ name: 'Algodón Pima', percentage: 100 }],
      origin: 'Perú',
      receptionCharacteristics: 'Prueba aislada de interfaz',
    },
  });
  expect(response.status()).toBe(201);
  const batch = await response.json();
  await login(page, 'operator-a');
  await page.goto('/lotes/' + batch.traceabilityId);
  await page.getByRole('button', { name: 'Dividir', exact: true }).click();
  let dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  await dialog.getByLabel('Cantidad hijo 1', { exact: true }).fill('12');
  await dialog.getByLabel('Cantidad hijo 2', { exact: true }).fill('8');
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  const divided = page.waitForResponse(
    (r) => r.url().endsWith('/split') && r.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Dividir lote', exact: true }).click();
  const divisionResponse = await divided;
  expect(divisionResponse.status()).toBe(201);
  const division = await divisionResponse.json();
  await expect(dialog).toHaveCount(0);
  await page.goto('/lotes/' + division.children[0].traceabilityId);
  await expect(page.locator('.topbar')).toContainText(division.children[0].traceabilityId);
  await expect(page.getByRole('button', { name: 'Transformar', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Transformar', exact: true }).click();
  dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  await dialog
    .getByRole('combobox', { name: 'Tipo de transformación', exact: true })
    .selectOption('Hilado');
  await dialog.getByRole('combobox', { name: /^Maquinaria activa/ }).selectOption('E2E-MQ');
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  await dialog.getByRole('button', { name: 'Iniciar transformación', exact: true }).click();
  await expect(page).toHaveURL(/\/procesos\//);
  await page.getByRole('button', { name: 'Finalizar transformación', exact: true }).click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel('Cantidad salida 1', { exact: true }).fill('10');
  await dialog
    .getByLabel('Nombre del lote de salida', { exact: true })
    .fill('Hilado interfaz');
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  await expect(dialog).toContainText('Composición resultante');
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  const completed = page.waitForResponse(
    (r) => r.url().endsWith('/complete') && r.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Finalizar transformación', exact: true }).click();
  const completionResponse = await completed;
  expect(completionResponse.status()).toBe(200);
  const completedProcess = await completionResponse.json();
  expect(completedProcess.outputs[0].quantityKg).toBe(10);
  await expect(dialog).toHaveCount(0);
  await page.reload();
  await expect(
    page.getByRole('button', { name: 'Finalizar transformación', exact: true }),
  ).toHaveCount(0);
  await expect(page.locator('.topbar')).toContainText('Hilado');
});
test('administrador solicita otro administrador y el dueño rechaza la solicitud', async ({
  page,
}) => {
  test.setTimeout(120000);
  await login(page, 'admin-a');
  await page.getByRole('link', { name: 'Usuarios', exact: true }).click();
  await page.getByRole('button', { name: 'Solicitar administrador', exact: true }).click();
  const dialog = page.getByRole('dialog');
  const email = 'admin-request-ui-' + Date.now() + '@example.test';
  await dialog.getByLabel('Nombre', { exact: true }).fill('Administrador Solicitado');
  await dialog.getByLabel('Correo', { exact: true }).fill(email);
  await dialog.getByLabel('Cargo', { exact: true }).fill('Supervisión');
  const created = page.waitForResponse(
    (r) => r.url().endsWith('/api/admin-requests') && r.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Enviar solicitud', exact: true }).click();
  const createdResponse = await created;
  expect(createdResponse.status()).toBe(201);
  const requestId = (await createdResponse.json()).id;
  await expect(dialog).toHaveCount(0);
  await page.getByLabel('Buscar', { exact: true }).fill(email);
  const requestRow = page
    .locator('.row-card')
    .filter({ hasText: 'Administrador Solicitado' })
    .first();
  await expect(requestRow).toContainText('Pendiente');
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await login(page, 'owner');
  // el dueño ya puede tener avisos de otras solicitudes: se espera a que el de ESTA exista antes de abrir la lista (la más reciente va primero)
  const ownerToken = (await ensureAccount(page.request, account('owner', env['TRAZATEX_OWNER_EMAIL']))).token;
  await expect.poll(async () => {
    const response = await page.request.get('/api/notifications?size=100', { headers: { Authorization: 'Bearer ' + ownerToken } });
    return response.ok() && JSON.stringify(await response.json()).includes(requestId);
  }, { timeout: 60000, intervals: [2000] }).toBe(true);
  await page.getByRole('complementary', { name: 'Navegación principal' }).getByRole('link', { name: /^Notificaciones/ }).click();
  await expect(page).toHaveURL(/\/notificaciones/);
  await page.reload();
  const notice = page.locator('li').filter({ has: page.locator('button.body') }).filter({ hasText: 'Nueva solicitud de administrador' }).first();
  await expect(notice).toBeVisible({ timeout: 45000 });
  await notice.locator('button.body').click();
  await expect(page).toHaveURL(new RegExp('solicitud=' + requestId));
  await expect(page.getByLabel('Buscar', { exact: true })).toHaveValue(email);
  const row = page.locator('.row-card');
  await expect(row).toHaveCount(1);
  const decided = page.waitForResponse(
    (r) => r.url().endsWith('/decision') && r.request().method() === 'PATCH',
  );
  await confirmNext(page);
  await row.getByRole('button', { name: 'Rechazar', exact: true }).click();
  expect((await decided).status()).toBe(200);
  await expect(row).toContainText('Rechazada');
  await page.reload();
  await page.getByLabel('Buscar', { exact: true }).fill(email);
  await expect(page.locator('.row-card')).toContainText('Rechazada');
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await login(page, 'admin-a');
  await page.getByRole('complementary', { name: 'Navegación principal' }).getByRole('link', { name: /^Notificaciones/ }).click();
  const resolved = page.locator('li').filter({ hasText: 'Solicitud revisada' }).first();
  await expect(resolved).toBeVisible({ timeout: 45000 });
  await resolved.locator('button.body').click();
  await expect(page).toHaveURL(new RegExp('solicitud=' + requestId));
  await expect(page.locator('.row-card')).toContainText('Rechazada');
});
test('administrador cambia la privacidad y conserva su configuración al recargar', async ({
  page,
}) => {
  await login(page, 'admin-a');
  await page.getByRole('link', { name: 'Privacidad B2B', exact: true }).click();
  const group = page.getByRole('group', { name: 'Origen geográfico', exact: true });
  const originalPrivate =
    (await group
      .getByRole('button', { name: 'Privado', exact: true })
      .getAttribute('aria-pressed')) === 'true';
  const target = originalPrivate ? 'Compartido' : 'Privado';
  const changed = page.waitForResponse(
    (r) => r.url().endsWith('/api/privacy-settings') && r.request().method() === 'PUT',
  );
  await group.getByRole('button', { name: target, exact: true }).click();
  expect((await changed).status()).toBe(200);
  await expect(group.getByRole('button', { name: target, exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.reload();
  await expect(group.getByRole('button', { name: target, exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  const restored = page.waitForResponse(
    (r) => r.url().endsWith('/api/privacy-settings') && r.request().method() === 'PUT',
  );
  await group
    .getByRole('button', { name: originalPrivate ? 'Privado' : 'Compartido', exact: true })
    .click();
  expect((await restored).status()).toBe(200);
});

test('operario envía una transferencia y el administrador destino la acepta en pantalla', async ({
  page,
  request,
}) => {
  test.setTimeout(120000);
  const operator = await ensureAccount(request, account('operator-a'));
  const destination = await ensureAccount(request, account('admin-b'));
  const created = await request.post('/api/batches', {
    headers: { Authorization: 'Bearer ' + operator.token },
    data: {
      name: 'Transfer UI ' + String(Date.now()).slice(-6),
      materialType: 'Fibra de algodón Pima',
      quantityKg: 15,
      composition: [{ name: 'Algodón Pima', percentage: 100 }],
      origin: 'Perú',
      receptionCharacteristics: 'Prueba aislada',
    },
  });
  expect(created.status()).toBe(201);
  const batch = await created.json();
  await login(page, 'operator-a');
  await page.goto('/lotes/' + batch.traceabilityId);
  await page.getByRole('button', { name: 'Transferir', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Iniciar transferencia', exact: true });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  await dialog.getByRole('combobox', { name: /^Empresa destino/ }).selectOption(destination.user.companyId);
  await dialog.getByRole('button', { name: 'Continuar' }).click();
  const sent = page.waitForResponse(
    (r) => r.url().endsWith('/api/transfers') && r.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Enviar transferencia', exact: true }).click();
  const sentResponse = await sent;
  expect(sentResponse.status()).toBe(201);
  const transfer = await sentResponse.json();
  await expect(dialog).toHaveCount(0);
  await page.getByRole('dialog').getByRole('button', { name: 'Cerrar', exact: true }).click();
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await login(page, 'admin-b');
  await page.getByRole('link', { name: 'Transferencias', exact: true }).click();
  await page.getByRole('tab', { name: /^Todas/ }).click();
  await page.getByLabel('Buscar transferencias', { exact: true }).fill(transfer.id);
  await expect(page.locator('button.row-card')).toHaveCount(1);
  await page.locator('button.row-card').click();
  const drawer = page.getByRole('dialog');
  const accepted = page.waitForResponse(
    (r) => r.url().endsWith('/accept') && r.request().method() === 'POST',
  );
  await confirmNext(page);
  await drawer.getByRole('button', { name: 'Aceptar', exact: true }).click();
  expect((await accepted).status()).toBe(200);
  await expect(drawer).toContainText('Aceptada');
  await page.reload();
  await page.getByRole('tab', { name: /^Todas/ }).click();
  await page.getByLabel('Buscar transferencias', { exact: true }).fill(transfer.id);
  await expect(page.locator('button.row-card')).toHaveCount(1);
  await expect(page.locator('button.row-card')).toContainText('Aceptada');
  const persisted = await request.get('/api/batches/' + batch.id, {
    headers: { Authorization: 'Bearer ' + destination.token },
  });
  expect(persisted.status()).toBe(200);
  expect((await persisted.json()).companyId).toBe(destination.user.companyId);
});
test('genealogía busca datos reales y permite recorrer, filtrar y ampliar el árbol', async ({
  page,
  request,
}) => {
  test.setTimeout(120000);
  const session = await ensureAccount(request, account('operator-a'));
  const headers = { Authorization: 'Bearer ' + session.token };
  const response = await request.post('/api/batches', {
    headers,
    data: {
      name: 'Genealogía UI ' + String(Date.now()).slice(-6),
      materialType: 'Fibra de algodón Pima',
      quantityKg: 20,
      composition: [{ name: 'Algodón Pima', percentage: 100 }],
      origin: 'Perú',
      receptionCharacteristics: 'Prueba aislada',
    },
  });
  expect(response.status()).toBe(201);
  const batch = await response.json();
  const split = await request.post('/api/batches/' + batch.id + '/split', {
    headers,
    data: {
      children: [
        { name: 'Genealogía hijo A', quantityKg: 12 },
        { name: 'Genealogía hijo B', quantityKg: 8 },
      ],
    },
  });
  expect(split.status()).toBe(201);
  await expect
    .poll(
      async () => {
        const graph = await request.get('/api/traceability/batches/' + batch.id + '/lineage', {
          headers,
        });
        return graph.ok() && (await graph.json()).nodes.length >= 3;
      },
      { timeout: 45000 },
    )
    .toBe(true);
  await login(page, 'operator-a');
  await page.getByRole('link', { name: 'Trazabilidad', exact: true }).click();
  await page.getByLabel('Buscar lote', { exact: true }).fill(batch.traceabilityId);
  await page.getByRole('button', { name: 'Trazar', exact: true }).click();
  const root = page.locator('button.node.lot').filter({ hasText: batch.traceabilityId });
  await expect(root).toHaveCount(1);
  await expect(root).toHaveClass(/is-sel/);
  await expect(page.locator('.trace-detail')).toContainText('Dividido');
  await page.getByRole('button', { name: 'Ver origen', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Ver origen', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.getByRole('button', { name: 'Ver descendencia', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Ver descendencia', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.getByRole('button', { name: '70 %', exact: true }).click();
  await expect(page.getByRole('button', { name: '70 %', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.getByRole('button', { name: '100 %', exact: true }).click();
  const stage = page.locator('.stage-filter input').first();
  await stage.uncheck();
  await expect(stage).not.toBeChecked();
  await stage.check();
  await page.getByText('Más filtros', { exact: false }).click();
  const quality = page.getByLabel('Filtrar por calidad', { exact: true });
  await quality.selectOption('Conforme');
  await page.getByRole('button', { name: 'Restablecer filtros', exact: true }).click();
  await expect(quality).toHaveValue('');
  await page.getByRole('button', { name: 'Limpiar selección', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Ver origen', exact: true })).toBeDisabled();
});
test('administrador inactiva y reactiva un operario y el Gateway bloquea su sesión', async ({
  page,
  request,
}) => {
  test.setTimeout(90000);
  const operatorAccount = account('operator-a');
  const operator = await ensureAccount(request, operatorAccount);
  const admin = await ensureAccount(request, account('admin-a'));
  await login(page, 'admin-a');
  await page.getByRole('link', { name: 'Usuarios', exact: true }).click();
  await page.getByLabel('Buscar', { exact: true }).fill(operatorAccount.email);
  await expect(page.locator('button.row-card')).toHaveCount(1);
  await page.locator('button.row-card').click();
  const drawer = page.getByRole('dialog');
  try {
    const inactive = page.waitForResponse(
      (r) => r.url().endsWith('/status') && r.request().method() === 'PATCH',
    );
    await confirmNext(page);
    await drawer.getByRole('button', { name: 'Inactivar acceso', exact: true }).click();
    expect((await inactive).status()).toBe(200);
    await expect(drawer).toContainText('Inactivo');
    await expect
      .poll(
        async () =>
          (
            await request.get('/api/batches', {
              headers: { Authorization: 'Bearer ' + operator.token },
            })
          ).status(),
        { timeout: 30000 },
      )
      .toBe(401);
    const active = page.waitForResponse(
      (r) => r.url().endsWith('/status') && r.request().method() === 'PATCH',
    );
    await confirmNext(page);
    await drawer.getByRole('button', { name: 'Activar acceso', exact: true }).click();
    expect((await active).status()).toBe(200);
    await expect(drawer).toContainText('Activo');
    await expect.poll(async () => (await request.get('/api/batches', {
      headers: { Authorization: 'Bearer ' + operator.token },
    })).status(), { timeout: 30000 }).toBe(200);
  } finally {
    const users = await request.get('/api/users', {
      headers: { Authorization: 'Bearer ' + admin.token },
    });
    expect(users.ok()).toBe(true);
    const user = (await users.json()).find((user: { id: string }) => user.id === operator.user.id);
    if (user.status === 'INACTIVE') {
      const restored = await request.patch('/api/users/' + operator.user.id + '/status', {
        headers: { Authorization: 'Bearer ' + admin.token },
        data: { status: 'ACTIVE' },
      });
      expect(restored.ok()).toBe(true);
    }
  }
});
test('operario actualiza datos comerciales opcionales sin inventar un precio', async ({
  page,
  request,
}) => {
  test.setTimeout(120000);
  const session = await ensureAccount(request, account('operator-a'));
  const headers = { Authorization: 'Bearer ' + session.token };
  const batchResponse = await request.post('/api/batches', {
    headers,
    data: {
      name: 'Prod final UI ' + String(Date.now()).slice(-6),
      materialType: 'Fibra de algodón Pima',
      quantityKg: 10,
      composition: [{ name: 'Algodón Pima', percentage: 100 }],
      origin: 'Perú',
      receptionCharacteristics: 'Prueba aislada',
    },
  });
  expect(batchResponse.status()).toBe(201);
  const batch = await batchResponse.json();
  const machines = await (await request.get('/api/machines', { headers })).json();
  const machine = machines.find((item: { internalCode: string }) => item.internalCode === 'E2E-MQ');
  const processResponse = await request.post('/api/transformations', {
    headers,
    data: { type: 'SPINNING', machineId: machine.id, batchIds: [batch.id] },
  });
  expect(processResponse.status()).toBe(201);
  const process = await processResponse.json();
  const completeResponse = await request.post('/api/transformations/' + process.id + '/complete', {
    headers,
    data: {
      outputs: [
        {
          name: 'Producto comercial',
          materialType: 'Hilo peinado',
          quantityKg: 9,
          finalProduct: true,
        },
      ],
      wasteKg: 0,
    },
  });
  expect(completeResponse.status()).toBe(200);
  const output = (await completeResponse.json()).outputs[0];
  await login(page, 'operator-a');
  await page.goto('/lotes/' + output.traceabilityId);
  await unfold(page, 'Información comercial');
  await expect(page.locator('body')).toContainText('Sin datos comerciales registrados.');
  await page.getByRole('button', { name: 'Datos comerciales', exact: true }).click();
  const dialog = page.getByRole('dialog');
  const buyer = 'Comprador UI ' + Date.now();
  await dialog.getByLabel('Distribuidor o comprador', { exact: true }).fill(buyer);
  const saved = page.waitForResponse(
    (r) => r.url().endsWith('/commercial-data') && r.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Guardar datos comerciales', exact: true }).click();
  const savedResponse = await saved;
  expect(savedResponse.status()).toBe(200);
  expect((await savedResponse.json()).commercialData.salePrice).toBeNull();
  await expect(dialog).toHaveCount(0);
  await expect(page.locator('body')).toContainText(buyer);
  await page.reload();
  await unfold(page, 'Información comercial');
  await expect(page.locator('body')).toContainText(buyer);
  await expect(page.getByRole('button', { name: 'Dividir', exact: true })).toBeDisabled();
});

test('el dueño aprueba una solicitud y el backend emite su invitación de administrador', async ({
  page,
  request,
}) => {
  const admin = await ensureAccount(request, account('admin-a'));
  const owner = await ensureAccount(request, account('owner', env['TRAZATEX_OWNER_EMAIL']));
  const email = 'approved-admin-ui-' + Date.now() + '@example.test';
  const created = await request.post('/api/admin-requests', {
    headers: { Authorization: 'Bearer ' + admin.token },
    data: { fullName: 'Administrador Aprobado', email, position: 'Supervisión' },
  });
  expect(created.status()).toBe(201);
  await login(page, 'owner');
  await page.goto('/usuarios');
  await page.getByLabel('Buscar', { exact: true }).fill(email);
  const row = page.locator('.row-card');
  await expect(row).toHaveCount(1);
  const approved = page.waitForResponse(
    (r) => r.url().endsWith('/decision') && r.request().method() === 'PATCH',
  );
  await confirmNext(page);
  await row.getByRole('button', { name: 'Aprobar', exact: true }).click();
  expect((await approved).status()).toBe(200);
  await expect(row).toContainText('Aprobada');
  const id = invitationId(email);
  const cancelled = await request.post('/api/invitations/' + id + '/cancel', {
    headers: { Authorization: 'Bearer ' + owner.token },
  });
  expect(cancelled.status()).toBe(200);
  expect((await cancelled.json()).role).toBe('COMPANY_ADMIN');
});
test('administrador destino valida el motivo y rechaza una transferencia en pantalla', async ({
  page,
  request,
}) => {
  test.setTimeout(90000);
  const operator = await ensureAccount(request, account('operator-a'));
  const destination = await ensureAccount(request, account('admin-b'));
  const headers = { Authorization: 'Bearer ' + operator.token };
  const created = await request.post('/api/batches', {
    headers,
    data: {
      name: 'Rechazo UI ' + String(Date.now()).slice(-6),
      materialType: 'Fibra de algodón Pima',
      quantityKg: 5,
      composition: [{ name: 'Algodón Pima', percentage: 100 }],
      origin: 'Perú',
      receptionCharacteristics: 'Prueba aislada',
    },
  });
  expect(created.status()).toBe(201);
  const batch = await created.json();
  const sent = await request.post('/api/transfers', {
    headers,
    data: { targetCompanyId: destination.user.companyId, batchIds: [batch.id] },
  });
  expect(sent.status()).toBe(201);
  const transfer = await sent.json();
  await login(page, 'admin-b');
  await page.getByRole('link', { name: 'Transferencias', exact: true }).click();
  await page.getByRole('tab', { name: /^Todas/ }).click();
  await page.getByLabel('Buscar transferencias', { exact: true }).fill(transfer.id);
  await expect(page.locator('button.row-card')).toHaveCount(1);
  await page.locator('button.row-card').click();
  await page.getByRole('dialog').getByRole('button', { name: 'Rechazar', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Rechazar transferencia ' + transfer.id.slice(0, 8).toUpperCase(), exact: true });
  await dialog.getByRole('button', { name: 'Rechazar transferencia', exact: true }).click();
  await expect(dialog.locator('.err')).not.toBeEmpty();
  await dialog
    .getByLabel('Motivo del rechazo (obligatorio)', { exact: true })
    .fill('Recepción rechazada en prueba de interfaz');
  const rejected = page.waitForResponse(
    (r) => r.url().endsWith('/reject') && r.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Rechazar transferencia', exact: true }).click();
  expect((await rejected).status()).toBe(200);
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole('dialog')).toContainText('Rechazada');
  const persisted = await request.get('/api/batches/' + batch.id, { headers });
  expect(persisted.status()).toBe(200);
  expect((await persisted.json()).companyId).toBe(operator.user.companyId);
});

test('el dueño inactiva y reactiva una empresa sin perder su información', async ({
  page,
  request,
}) => {
  test.setTimeout(90000);
  const owner = await ensureAccount(request, account('owner', env['TRAZATEX_OWNER_EMAIL']));
  const headers = { Authorization: 'Bearer ' + owner.token };
  const companies = await (await request.get('/api/companies', { headers })).json();
  // Instalación sembrada: la empresa «a» es la de la administración sembrada de la hilandería; si no, la que registra production.spec.
  const seededId = process.env['TRAZATEX_CREDENTIALS_PATH']
    ? JSON.parse(readFileSync(credentialsFile(), 'utf8')).accounts.find((x: { key: string }) => x.key === 'admin/hilanderia')?.companyId
    : undefined;
  const company = companies.find((item: { ruc: string; id: string }) => (seededId ? item.id === seededId : item.ruc === '20100070970'));
  await login(page, 'owner');
  await page.getByRole('link', { name: 'Empresas', exact: true }).click();
  await page.getByLabel('Buscar empresas', { exact: true }).fill(company.ruc);
  await expect(page.locator('button.row-card')).toHaveCount(1);
  await page.locator('button.row-card').click();
  const drawer = page.getByRole('dialog');
  try {
    await drawer.getByRole('button', { name: 'Inactivar', exact: true }).click();
    const confirmation = page.getByRole('dialog', {
      name: 'Inactivar ' + company.businessName,
      exact: true,
    });
    const inactive = page.waitForResponse(
      (r) => r.url().includes('/api/companies/') && r.request().method() === 'PATCH',
    );
    await confirmation.getByRole('button', { name: 'Inactivar', exact: true }).click();
    expect((await inactive).status()).toBe(200);
    await expect(confirmation).toHaveCount(0);
    await expect(drawer).toContainText('Inactiva');
    const active = page.waitForResponse(
      (r) => r.url().includes('/api/companies/') && r.request().method() === 'PATCH',
    );
    await confirmNext(page);
    await drawer.getByRole('button', { name: 'Activar', exact: true }).click();
    expect((await active).status()).toBe(200);
    await expect(drawer).toContainText('Activa');
  } finally {
    const current = await (await request.get('/api/companies', { headers })).json();
    if (current.find((item: { id: string }) => item.id === company.id).status === 'INACTIVE') {
      expect(
        (
          await request.patch('/api/companies/' + company.id + '/status', {
            headers,
            data: { status: 'ACTIVE' },
          })
        ).ok(),
      ).toBe(true);
    }
  }
  await page.reload();
  await page.getByLabel('Buscar empresas', { exact: true }).fill(company.ruc);
  await expect(page.locator('button.row-card')).toContainText('Activa');
});
