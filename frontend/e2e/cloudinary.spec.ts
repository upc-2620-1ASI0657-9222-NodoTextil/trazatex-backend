import { test, expect } from './base';
import { account, ensureAccount } from './support';

test.use({ trace: 'off' });

test('Cloudinary: imágenes reales de lote y transformación, persistencia, visualización y errores', async ({
  page,
  request,
}) => {
  test.setTimeout(180000);
  const user = account('operator-a');
  const session = await ensureAccount(request, user);
  const headers = { Authorization: 'Bearer ' + session.token };
  const batchResponse = await request.post('/api/batches', {
    headers,
    data: {
      name: 'Evid real ' + String(Date.now()).slice(-6),
      materialType: 'Fibra de algodón Pima',
      quantityKg: 10,
      composition: [{ name: 'Algodón Pima', percentage: 100 }],
      origin: 'Perú',
      receptionCharacteristics: 'Prueba aislada de imágenes',
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
  await page.setContent(
    '<main style="font:24px serif;padding:40px">TrazaTex · Evidencia de prueba</main>',
  );
  const bytes = await page.screenshot();
  const references: { path: string; filename: string; url: string; route: string }[] = [];
  for (const [kind, id, route] of [
    ['batches', batch.id, '/lotes/' + batch.traceabilityId],
    ['transformations', process.id, '/procesos/' + process.id],
  ]) {
    const path = '/api/' + kind + '/' + id + '/evidence';
    const filename = 'evidence-' + kind + '-' + Date.now() + '.png';
    const uploaded = await request.post(path, {
      headers,
      multipart: { files: { name: filename, mimeType: 'image/png', buffer: bytes } },
    });
    expect(uploaded.status()).toBe(201);
    await expect
      .poll(
        async () => {
          const response = await request.get(path, { headers });
          if (!response.ok()) return 'HTTP_' + response.status();
          return (await response.json()).items.find(
            (item: { originalFilename: string }) => item.originalFilename === filename,
          )?.status;
        },
        { timeout: 90000 },
      )
      .toBe('UPLOADED');
    const response = await request.get(path, { headers });
    const body = await response.text();
    expect(body).not.toMatch(/cloudinary:\/\/|apiSecret|api_secret/);
    const item = JSON.parse(body).items.find(
      (item: { originalFilename: string }) => item.originalFilename === filename,
    );
    expect(item.url).toMatch(/^https:\/\/res\.cloudinary\.com\//);
    const image = await request.get(item.url);
    expect(image.status()).toBe(200);
    expect(image.headers()['content-type']).toMatch(/^image\//);
    references.push({ path, filename, url: item.url, route });
  }
  const invalid = await request.post(references[0].path, {
    headers,
    multipart: {
      files: {
        name: 'invalid-image.png',
        mimeType: 'image/png',
        buffer: Buffer.from('Esto no es una imagen'),
      },
    },
  });
  expect(invalid.status()).toBe(400);
  expect((await invalid.json()).code).toBe('UNSUPPORTED_FILE_FORMAT');
  await page.goto('/login');
  await page.getByLabel('Correo', { exact: true }).fill(user.email);
  await page.getByLabel('Contraseña', { exact: true }).fill(user.password);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.locator('app-shell')).toBeVisible();
  for (const item of references) {
    await page.goto(item.route);
    await page.getByRole('button', { name: /Evidencias/ }).click();
    const evidence = page.locator('img[src="' + item.url + '"]');
    await expect(evidence).toBeVisible();
    await expect
      .poll(() =>
        evidence.evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0),
      )
      .toBe(true);
    await page.reload();
    await page.getByRole('button', { name: /Evidencias/ }).click();
    await expect(page.locator('img[src="' + item.url + '"]')).toBeVisible();
  }
});
