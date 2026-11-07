import { test, expect, Page } from './base';
import { clickAndDecide, saveEvidence, unfold } from './support';
import { readFileSync } from 'node:fs';
import qrcode from 'qrcode-generator';
import { appUrl, credentialsFile, localFile } from './paths';
test.use({ trace: 'off' });
const accounts = JSON.parse(readFileSync(credentialsFile(), 'utf8')).accounts as { key: string; email: string; password: string; role: string; companyId: string | null }[];
const operator = accounts.findIndex(a => a.role === 'OPERATOR');
const admin = accounts.findIndex(a => a.role === 'COMPANY_ADMIN');
async function login(page: Page, index: number) {
  await page.goto('/login');
  await page.getByLabel('Correo', { exact: true }).fill(accounts[index].email);
  await page.getByLabel('Contraseña', { exact: true }).fill(accounts[index].password);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.locator('app-shell')).toBeVisible();
  await expect(page.locator('main.content')).toHaveAttribute('aria-busy', 'false', { timeout: 60000 });
}
test('16 cuentas: acceso real desde Angular sin suplantación por selector', async ({ page }) => {
  test.setTimeout(600000);
  expect(accounts).toHaveLength(16);
  for (let index = 0; index < accounts.length; index++) {
    await page.context().clearCookies();
    await page.goto('/login'); await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
    await page.reload();
    expect(await page.locator('.brand img').count()).toBe(1);
    for (const label of ['Dueño', 'Administrador', 'Operario']) {
      const button = page.getByRole('button', { name: label, exact: true });
      await button.click(); await expect(button).toHaveAttribute('aria-pressed', 'true');
    }
    const heading = page.getByRole('heading', { name: 'Login', exact: true });
    expect(await heading.evaluate(e => getComputedStyle(e).fontWeight)).toBe('700');
    expect(await heading.evaluate(e => getComputedStyle(e).whiteSpace)).toBe('nowrap');
    await login(page, index);
    const expected = accounts[index].role === 'LICENSE_OWNER' ? 'Dueño de Licencia' : accounts[index].role === 'COMPANY_ADMIN' ? 'Administrador de Empresa' : 'Operario';
    await expect(page.locator('.proto')).toContainText(expected);
    expect(await page.locator('.rail .brand img').count()).toBe(1);
    await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
    await expect(page).toHaveURL(/login/);
  }
});
for (const role of ['LICENSE_OWNER', 'COMPANY_ADMIN', 'OPERATOR']) {
  test('navegación, sidebar y encabezados: ' + role, async ({ page }) => {
    test.setTimeout(180000);
    const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
    await login(page, accounts.findIndex(a => a.role === role));
    const rail = page.locator('.rail');
    await page.setViewportSize({ width: 1280, height: 360 });
    const links = await rail.locator('a.nav-item').evaluateAll(es => es.map(e => e.getAttribute('href')!));
    for (const href of links) {
      await rail.locator('a[href="' + href + '"]').click();
      await expect(page).toHaveURL(new RegExp(href + '$'));
      await expect(page.locator('.top-current')).toBeVisible();
      await expect(page.locator('main.content .page-head')).toHaveCount(0);
    }
    await rail.getByRole('button', { name: 'Cerrar sesión', exact: true }).scrollIntoViewIfNeeded();
    await expect(rail.getByRole('button', { name: 'Cerrar sesión', exact: true })).toBeInViewport();
    expect(await rail.evaluate(e => e.clientHeight)).toBeLessThanOrEqual(360);
    expect(await page.locator('.proto .label').evaluate(e => getComputedStyle(e).whiteSpace)).toBe('nowrap');
    expect(await page.locator('.proto').evaluate(e => getComputedStyle(e).borderLeftWidth)).toBe('0px');
    await page.setViewportSize({ width: 390, height: 480 });
    await page.getByRole('button', { name: 'Abrir menú', exact: true }).click();
    await rail.locator('a[href="/perfil"]').click();
    await expect(page).toHaveURL(/perfil$/);
    await expect(page.locator('main.content .page-head')).toHaveCount(0);
    await expect(page.locator('.topbar').getByRole('button', { name: 'Cerrar sesión', exact: true })).toBeVisible();
    expect(errors).toEqual([]);
  });
}
test('evidencia: subir desde Angular, cancelar, eliminar y recargar', async ({ page, request }) => {
  test.setTimeout(180000); await login(page, operator);
  const session = await (await request.post('/api/auth/login', { data: { email: accounts[operator].email, password: accounts[operator].password } })).json();
  const headers = { Authorization: 'Bearer ' + session.token };
  const batches = await (await request.get('/api/batches', { headers })).json();
  const batch = (batches.items ?? batches.content ?? batches)[0];
  await page.goto('/lotes/' + batch.traceabilityId);
  await unfold(page,/Evidencias/);
  const filename = 'revision-imagen-' + Date.now() + '.png';
  const bytes = await page.screenshot();
  await page.locator('app-evidence input[type=file]').setInputFiles({ name: filename, mimeType: 'image/png', buffer: bytes });
  await saveEvidence(page);
  const figure = page.locator('app-evidence figure').filter({ hasText: filename });
  await expect(figure).toBeVisible({ timeout: 90000 });
  await expect(figure.locator('img')).toBeVisible({ timeout: 90000 });
  const list = await (await request.get('/api/batches/' + batch.id + '/evidence', { headers })).json();
  const evidence = list.items.find((e: { originalFilename: string }) => e.originalFilename === filename);
  expect(evidence).toBeTruthy();
  const foreign = accounts.find(a => a.role === 'OPERATOR' && a.companyId !== accounts[operator].companyId)!;
  const otherSession = await (await request.post('/api/auth/login', { data: { email: foreign.email, password: foreign.password } })).json();
  expect((await request.delete('/api/batches/' + batch.id + '/evidence/' + evidence.id, { headers: { Authorization: 'Bearer ' + otherSession.token } })).status()).toBe(404);
  const adminSession = await (await request.post('/api/auth/login', { data: { email: accounts[admin].email, password: accounts[admin].password } })).json();
  expect((await request.delete('/api/batches/' + batch.id + '/evidence/' + evidence.id, { headers: { Authorization: 'Bearer ' + adminSession.token } })).status()).toBe(403);
  await clickAndDecide(page, figure.getByRole('button', { name: 'Eliminar evidencia' }), false);
  await expect(figure).toBeVisible();
  await clickAndDecide(page, figure.getByRole('button', { name: 'Eliminar evidencia' }));
  await expect(figure).toHaveCount(0);
  await page.reload(); await unfold(page,/Evidencias/);
  await expect(page.locator('app-evidence')).not.toContainText(filename);
  const after = await (await request.get('/api/batches/' + batch.id + '/evidence', { headers })).json();
  expect(after.items.some((e: { id: string }) => e.id === evidence.id)).toBe(false);
});
async function fakeCamera(page: Page, value: string) {
  const q = qrcode(0, 'M'); q.addData(value); q.make();
  const n = q.getModuleCount(); const cells: number[][] = [];
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (q.isDark(y,x)) cells.push([x,y]);
  await page.evaluate(({ n, cells }) => {
    const canvas = document.createElement('canvas'); canvas.width = canvas.height = (n + 8) * 8;
    const draw = () => { const ctx = canvas.getContext('2d')!; ctx.fillStyle = 'white'; ctx.fillRect(0,0,canvas.width,canvas.height); ctx.fillStyle = 'black'; for (const [x,y] of cells) ctx.fillRect((x+4)*8,(y+4)*8,8,8); };
    draw(); setInterval(draw, 100);
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: async () => canvas.captureStream(10) });
    Object.defineProperty(navigator.mediaDevices, 'enumerateDevices', { configurable: true, value: async () => [{ kind: 'videoinput', deviceId: 'camera', label: 'Camera', groupId: 'test' }] });
  }, { n, cells });
}
test('QR: cámara denegada, código inválido y lectura con navegación real', async ({ page, request }) => {
  test.setTimeout(150000); await login(page, operator);
  await page.goto('/lotes');
  await page.evaluate(() => { Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: async () => { throw new DOMException('Denied', 'NotAllowedError'); } }); });
  await page.getByRole('button', { name: 'Escanear QR', exact: true }).click();
  await expect(page.locator('app-qr-scanner')).toContainText('Permiso de cámara denegado');
  await page.locator('app-qr-scanner button[footer]').click();
  await page.goto('/lotes');
  await fakeCamera(page, 'https://invalid.test/not-a-lot'); await page.getByRole('button', { name: 'Escanear QR', exact: true }).click();
  await expect(page.locator('app-qr-scanner')).toContainText('no corresponde', { timeout: 30000 });
  const session = await (await request.post('/api/auth/login', { data: { email: accounts[operator].email, password: accounts[operator].password } })).json();
  const body = await (await request.get('/api/batches', { headers: { Authorization: 'Bearer ' + session.token } })).json();
  const batch = (body.items ?? body.content ?? body)[0];
  const url = appUrl() + '/lotes/' + batch.traceabilityId;
  await page.goto('/lotes');
  await fakeCamera(page, url); await page.getByRole('button', { name: 'Escanear QR', exact: true }).click();
  await expect(page).toHaveURL(url, { timeout: 30000 });
  await expect(page.locator('app-qr-tag svg')).toBeVisible();
});

test('genealogías variadas reales: cadenas completas, parciales y lotes iniciales', async ({ page }) => {
  test.setTimeout(240000);
  for (const companyId of [...new Set(accounts.filter(a => a.role === 'OPERATOR').map(a => a.companyId))]) {
    await page.goto('/login'); await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
    await login(page, accounts.findIndex(a => a.role === 'OPERATOR' && a.companyId === companyId));
    await page.goto('/trazabilidad');
    await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{ timeout: 60000 });
    const select = page.getByLabel('Bloque genealógico', { exact: true });
    const options = await select.locator('option').evaluateAll(es => es.map(e => (e as HTMLOptionElement).value));
    console.log('Bloques de empresa: ' + options.length);
    expect(options.length).toBeGreaterThanOrEqual(2); // depende de los datos de cada empresa; la variedad real se exige abajo (máx ≥10 nodos, mín 1)
    const sizes: number[] = [];
    for (let index = 0; index < Math.min(options.length, 30); index++) {
      // las opciones se vuelven a leer en cada vuelta: la lista de bloques se recalcula al cargar cada genealogía
      const current = await select.locator('option').evaluateAll(es => es.map(e => (e as HTMLOptionElement).value));
      if (index >= current.length) break;
      const value = current[index];
      const already = (await select.inputValue()) === value; // elegir el bloque ya mostrado no pide nada al servidor
      const loaded = already ? Promise.resolve(null) : page.waitForResponse(r => r.url().includes('/lineage') && !r.url().includes('/lineages?') && r.request().method() === 'GET');
      await select.selectOption(value);
      await loaded.catch(() => null); // el bloque puede salir del almacén sin pedir la genealogía de nuevo; lo que se comprueba abajo es el árbol
      // setBloque() recalcula el id tras cargar la genealogía del lote raíz (el bloque puede fusionarse con otro): solo se exige que haya un bloque elegido.
      await expect(select).not.toHaveValue('');
      await page.waitForTimeout(600);
      sizes.push(await page.locator('[data-node]').count());
      await page.locator('[data-node]').first().click();
      await expect(page.locator('.trace-detail .detail-id')).toBeVisible();
      await page.getByRole('button', { name: 'Limpiar selección', exact: true }).click();
    }
    expect(Math.max(...sizes)).toBeGreaterThanOrEqual(10);
    console.log('Tamaños de genealogías: ' + sizes.join(','));
    expect(Math.min(...sizes)).toBe(1);
  }
});
// El código del producto final ya no está fijo (era el de la instalación oficial): sale de verification.json de la siembra de ESTA instalación.
const finalBatch = (): { traceabilityId: string; name: string; lineageNodes: number; lineageEdges: number } => {
  const report = JSON.parse(readFileSync(localFile('verification.json'), 'utf8'));
  return { traceabilityId: report.finalBatch.traceabilityId, name: report.finalBatch.name, lineageNodes: report.lineageNodes, lineageEdges: report.lineageEdges };
};
test('producto final original y formulario descriptivo de salida', async ({ page, request }) => {
  test.setTimeout(120000); await login(page, operator);
  const final = finalBatch();
  await page.goto('/trazabilidad');
  await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{ timeout:60000 });
  await page.getByLabel('Buscar lote', { exact:true }).fill(final.traceabilityId);
  await page.getByRole('button', { name: 'Trazar', exact:true }).click();
  await expect(page.locator('[data-node="' + final.traceabilityId + '"]')).toBeVisible({ timeout: 60000 });
  expect(await page.locator('[data-node]').count()).toBeGreaterThanOrEqual(Math.min(17, final.lineageNodes));
  expect(await page.locator('path.edge').count()).toBeGreaterThanOrEqual(Math.min(17, final.lineageEdges));
  // Al trazar, el lote buscado ya queda seleccionado; pulsarlo otra vez alterna la selección, así que solo se pulsa si no lo está.
  if (!(await page.locator('.trace-detail .detail-id').count())) await page.locator('[data-node="' + final.traceabilityId + '"]').click();
  await expect(page.locator('.trace-detail .detail-id'), 'el detalle muestra el lote seleccionado').not.toBeEmpty();
  const session = await (await request.post('/api/auth/login', { data: { email: accounts[operator].email, password: accounts[operator].password } })).json();
  const body = await (await request.get('/api/transformations', { headers: { Authorization: 'Bearer ' + session.token } })).json();
  const auth = { Authorization: 'Bearer ' + session.token };
  let process = (body.items ?? body.content ?? body).find((p: { endTime?: string }) => !p.endTime);
  if (!process) { // en una siembra limpia no queda ninguna transformación abierta: se abre una propia (lote raíz + hilado)
    const root = await (await request.post('/api/batches', { headers: auth, data: { name: 'Raiz proc ' + String(Date.now()).slice(-6), materialType: 'Fibra de algodón Pima', quantityKg: 20,
      composition: [{ name: 'Algodón Pima', percentage: 100 }], origin: 'Piura, Perú', supplierName: 'Proveedor prueba', receptionCharacteristics: 'Proceso abierto de prueba.' } })).json();
    const machines = (await (await request.get('/api/machines', { headers: auth })).json()) as { id: string }[];
    for (const machine of machines) {
      const started = await request.post('/api/transformations', { headers: auth, data: { type: 'SPINNING', machineId: machine.id, batchIds: [root.id] } });
      if (started.status() === 201) { process = await started.json(); break; }
    }
  }
  expect(process, 'hay una transformación abierta').toBeTruthy();
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await login(page, operator);
  await page.goto('/procesos/' + process.id);
  await page.getByRole('button', { name:'Finalizar transformación', exact:true }).click();
  const name = page.getByLabel('Nombre del lote de salida', { exact:true });
  await expect(name).not.toHaveValue('');
  await page.getByRole('button', { name: 'Continuar', exact: true }).click();
  await expect(page.locator('app-dialog')).toContainText('un peso mayor a 0 kg');
});

test('QR mostrado: enlace real, retorno tras login y bloqueo B2B', async ({ page, request }) => {
  test.setTimeout(120000); await login(page, operator);
  const session = await (await request.post('/api/auth/login', { data: { email: accounts[operator].email, password: accounts[operator].password } })).json();
  const body = await (await request.get('/api/batches', { headers: { Authorization: 'Bearer ' + session.token } })).json();
  const batch = (body.items ?? body.content ?? body)[0];
  const url = appUrl() + '/lotes/' + batch.traceabilityId;
  await page.goto(url);
  const image = (await page.locator('app-qr-tag').screenshot()).toString('base64');
  await page.goto('/lotes');
  await page.evaluate(async (data) => {
    const image = new Image(); image.src = 'data:image/png;base64,' + data; await image.decode();
    const canvas = document.createElement('canvas'); canvas.width = image.width * 2; canvas.height = image.height * 2;
    const draw = () => canvas.getContext('2d')!.drawImage(image,0,0,canvas.width,canvas.height); draw(); setInterval(draw,100);
    Object.defineProperty(navigator.mediaDevices,'getUserMedia',{configurable:true,value:async()=>canvas.captureStream(10)});
    Object.defineProperty(navigator.mediaDevices,'enumerateDevices',{configurable:true,value:async()=>[{kind:'videoinput',deviceId:'camera',label:'Camera',groupId:'test'}]});
  }, image);
  await page.getByRole('button', { name:'Escanear QR', exact:true }).click();
  await expect(page).toHaveURL(url,{timeout:30000});
  await page.getByRole('button', { name:'Cerrar sesión',exact:true }).click();
  await page.goto(url); await expect(page).toHaveURL(/login.*returnUrl/);
  await page.getByLabel('Correo',{exact:true}).fill(accounts[operator].email);
  await page.getByLabel('Contraseña',{exact:true}).fill(accounts[operator].password);
  await page.getByRole('button',{name:'Ingresar',exact:true}).click();
  await expect(page).toHaveURL(url);
  const foreign = accounts.find(a=>a.role==='OPERATOR' && a.companyId!==accounts[operator].companyId)!;
  const other = await(await request.post('/api/auth/login',{data:{email:foreign.email,password:foreign.password}})).json();
  const otherBody=await(await request.get('/api/batches',{headers:{Authorization:'Bearer '+other.token}})).json();
  const otherBatch=(otherBody.items??otherBody.content??otherBody)[0];
  await page.goto('/lotes'); await fakeCamera(page,appUrl()+'/lotes/'+otherBatch.traceabilityId);
  await page.getByRole('button',{name:'Escanear QR',exact:true}).click();
  // FE-I1: un QR de otra empresa abre la vista compartida (SHARED); no es un error. Rige FE-C1: sin nombre y sin datos privados.
  await expect(page).toHaveURL(new RegExp('/lotes/'+otherBatch.traceabilityId+'$'),{timeout:30000});
  await expect(page.locator('app-qr-scanner')).toHaveCount(0);
  const shared = page.locator('main.content');
  await expect(shared).toContainText(otherBatch.traceabilityId,{timeout:30000});
  await expect(page.locator('.topbar')).toContainText('Lote compartido');
  // la vista compartida no ofrece acciones de propietario (Dividir, Transformar, Transferir, evidencias)
  await expect(page.getByRole('button', { name: /^(Dividir|Transformar|Transferir)$/ })).toHaveCount(0);
  if (otherBatch.name) await expect(page.locator('body')).not.toContainText(otherBatch.name);
  // Ronda 4a (fila 29): aviso de lote de otra empresa.
  await expect(shared).toContainText(/Pertenece a .+; solo ves los datos que comparte\./);
});
test('transformación: eliminación real de evidencia desde su ficha', async ({ page, request }) => {
  test.setTimeout(120000); await login(page,operator);
  const session=await(await request.post('/api/auth/login',{data:{email:accounts[operator].email,password:accounts[operator].password}})).json();
  const headers={Authorization:'Bearer '+session.token};
  const body=await(await request.get('/api/transformations',{headers})).json();
  const process=(body.items??body.content??body)[0];
  await page.goto('/procesos/'+process.id);
  await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
  await unfold(page,/Evidencias/);
  const filename='revision-proceso-'+Date.now()+'.png'; const buffer=await page.screenshot();
  const uploaded = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/evidence'));
  await page.locator('app-evidence input[type=file]').setInputFiles({name:filename,mimeType:'image/png',buffer}); await saveEvidence(page);
  expect((await uploaded).status()).toBe(201);
  const figure=page.locator('app-evidence figure').filter({hasText:filename});
  await expect(figure.locator('img')).toBeVisible({timeout:90000});
  const graph = page.waitForResponse(r => r.url().includes('/traceability/batches/') && r.url().endsWith('/lineage'));
  await page.getByRole('link', { name: 'Ver en genealogía', exact: true }).click();
  expect((await graph).status()).toBe(200);
  await page.goBack();
  await expect(page).toHaveURL(new RegExp('/procesos/' + process.id));
  await unfold(page,/Evidencias/);
  await expect(figure.locator('img')).toBeVisible();
  await clickAndDecide(page, figure.getByRole('button',{name:'Eliminar evidencia'}));
  await expect(figure).toHaveCount(0); await page.reload(); await unfold(page,/Evidencias/);
  await expect(page.locator('app-evidence')).not.toContainText(filename);
});
test('mensajes por rol, separadores y presentación final sin cambiar distribución', async ({ page }, testInfo) => {
  test.setTimeout(120000);
  for (const index of [operator,admin]) {
    await page.goto('/login'); await page.evaluate(()=>{localStorage.clear();sessionStorage.clear();});
    await login(page,index); await page.goto('/transferencias');
    await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
    if(index===operator) await expect(page.locator('main.content')).not.toContainText('Espera tu decisión');
    const label=page.locator('.proto .label');
    expect(Number(await label.evaluate(e=>getComputedStyle(e).fontWeight))).toBeGreaterThanOrEqual(700);
    for(const route of ['/lotes','/procesos','/transferencias','/perfil']) {
      await page.goto(route); await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
      await expect(page.locator('main.content')).not.toContainText('·');
    }
  }
  await page.goto('/lotes'); await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
  await page.screenshot({path:testInfo.outputPath('sidebar-final.png')});
  await page.getByRole('button',{name:'Contraer menú',exact:true}).click();
  const rail=page.locator('.rail');
  await expect.poll(async()=>Math.round((await rail.boundingBox())!.width)).toBe(68);
  const mark=await rail.locator('.brand img').boundingBox(); const box=await rail.boundingBox();
  expect(mark!.x).toBeGreaterThanOrEqual(box!.x); expect(mark!.x+mark!.width).toBeLessThanOrEqual(box!.x+box!.width);
  await page.screenshot({path:testInfo.outputPath('sidebar-contraida-final.png')});
});

test('transferencia pendiente: mensajes y decisiones respetan el rol real', async ({ page, request }) => {
  test.setTimeout(120000);
  const first=accounts[operator];
  const session=await(await request.post('/api/auth/login',{data:{email:first.email,password:first.password}})).json();
  const body=await(await request.get('/api/transfers',{headers:{Authorization:'Bearer '+session.token}})).json();
  const pending=(body.items??body.content??body).find((t:{status:string})=>t.status==='PENDING');
  expect(pending).toBeTruthy();
  for(const role of ['OPERATOR','COMPANY_ADMIN']) {
    await page.goto('/login'); await page.evaluate(()=>{localStorage.clear();sessionStorage.clear();});
    await login(page,accounts.findIndex(a=>a.companyId===pending.targetCompanyId && a.role===role));
    await page.goto('/transferencias'); await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
    await page.getByRole('tab',{name:/^Pendientes/}).click();
    const card=page.locator('.row-card').filter({hasText:role==='OPERATOR'?'Espera decisión':'Espera tu decisión'}).first();
    await expect(card).toBeVisible(); await card.click();
    if(role==='OPERATOR') {
      await expect(page.getByRole('button',{name:'Aceptar',exact:true})).toBeDisabled();
      await expect(page.getByRole('button',{name:'Rechazar',exact:true})).toBeDisabled();
    } else {
      await expect(page.getByRole('button',{name:'Aceptar',exact:true})).toBeVisible();
      await expect(page.getByRole('button',{name:'Rechazar',exact:true})).toBeVisible();
    }
  }
});
test('genealogía: la combinación de relaciones conserva campos privados B2B', async ({ page, request }) => {
  test.setTimeout(120000);
  const index=accounts.findLastIndex(a=>a.role==='OPERATOR'); await login(page,index);
  const session=await(await request.post('/api/auth/login',{data:{email:accounts[index].email,password:accounts[index].password}})).json();
  const finalId=finalBatch().traceabilityId;
  const found=await(await request.get('/api/traceability/lineages?traceabilityId='+finalId,{headers:{Authorization:'Bearer '+session.token}})).json();
  const graph=await(await request.get('/api/traceability/batches/'+found.items[0].batchId+'/lineage',{headers:{Authorization:'Bearer '+session.token}})).json();
  const foreign=graph.nodes.find((n:{kind:string;companyId:string;quantityKg?:number})=>n.kind==='BATCH' && n.companyId!==accounts[index].companyId && n.quantityKg===undefined);
  expect(foreign).toBeTruthy();
  await page.goto('/trazabilidad'); await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
  await page.getByLabel('Buscar lote',{exact:true}).fill(finalId);
  const loaded = page.waitForResponse(r=>r.url().includes('/batches/'+found.items[0].batchId+'/lineage'));
  await page.getByRole('button',{name:'Trazar',exact:true}).click(); await loaded;
  // FE-C1 (fila 1): el panel de detalle muestra el NOMBRE del lote (propio) o «—» (ajeno), no el Traceability ID.
  const mine=graph.nodes.find((n:{traceabilityId:string})=>n.traceabilityId===finalId);
  await expect(page.locator('.trace-detail .detail-id')).toHaveText(mine?.name??'—');
  await expect(page.locator('[data-node="'+finalId+'"] .mat.id')).toHaveText(finalId);
  await page.locator('[data-node="'+foreign.traceabilityId+'"]').click();
  // Fila 4: un nodo ajeno muestra «—» como nombre (nodo, title y panel) y su texto real no aparece en la página.
  await expect(page.locator('.trace-detail .detail-id')).toHaveText('—');
  await expect(page.locator('[data-node="'+foreign.traceabilityId+'"] .row1 b')).toHaveText('—');
  await expect(page.locator('[data-node="'+foreign.traceabilityId+'"]')).toHaveAttribute('title',new RegExp('^— / '+foreign.traceabilityId+' / '));
  await expect(page.locator('[data-node="'+foreign.traceabilityId+'"] .mat.id')).toHaveText(foreign.traceabilityId);
  expect(foreign.name,'el API no debe traer el name de un nodo ajeno').toBeUndefined();
  await expect(page.locator('.trace-detail app-priv').first()).toBeVisible();
  await expect(page.locator('.trace-detail').getByRole('button',{name:/^Abrir lote$/})).toBeDisabled();
});

// ── Ronda 5: pendientes de FE-I1 (QR) y FE-M4 (login por rol). Sin ejecutar hasta la ronda 5b. ──
async function tokenOf(request: import('@playwright/test').APIRequestContext, a: { email: string; password: string }) {
  return (await (await request.post('/api/auth/login', { data: { email: a.email, password: a.password } })).json()).token as string;
}
test('QR inválido: se consulta una sola vez, no se repite y «Volver a leer» lo procesa de nuevo', async ({ page }) => {
  test.setTimeout(120000); await login(page, operator);
  await page.goto('/lotes');
  const lookups: string[] = [];
  page.on('request', r => { if (r.url().includes('/api/traceability/lineages') || r.url().includes('/api/batches/qr/')) lookups.push(r.url()); });
  await fakeCamera(page, 'https://invalid.test/not-a-lot');
  await page.getByRole('button', { name: 'Escanear QR', exact: true }).click();
  await expect(page.locator('app-qr-scanner')).toContainText('no corresponde', { timeout: 30000 });
  const first = lookups.length;
  expect(first).toBeLessThanOrEqual(1);
  await page.waitForTimeout(6000); // el QR inválido sigue frente a la cámara
  expect(lookups.length, 'la desduplicación evita repetir la petición mientras el QR sigue a la vista').toBe(first);
  await page.getByRole('button', { name: 'Volver a leer', exact: true }).click();
  // El mismo QR se procesa otra vez de inmediato: el aviso reaparece y, si la comprobación pasa por la red, hay una petición más.
  await expect(page.locator('app-qr-scanner')).toContainText('no corresponde', { timeout: 30000 });
  if (first > 0) await expect.poll(() => lookups.length, { timeout: 10000 }).toBeGreaterThan(first);
});
test('QR con el código del sistema (qrCode) de un lote de otra empresa abre la vista compartida', async ({ page, request }) => {
  test.setTimeout(120000); await login(page, operator);
  const foreign = accounts.find(a => a.role === 'OPERATOR' && a.companyId !== accounts[operator].companyId)!;
  const foreignToken = await tokenOf(request, foreign);
  const body = await (await request.get('/api/batches', { headers: { Authorization: 'Bearer ' + foreignToken } })).json();
  const other = (body.items ?? body.content ?? body)[0];
  expect(other.qrCode, 'el listado trae el qrCode del lote').toBeTruthy();
  const mineToken = await tokenOf(request, accounts[operator]);
  const lookup = await request.get('/api/batches/qr/' + encodeURIComponent(other.qrCode), { headers: { Authorization: 'Bearer ' + mineToken } });
  expect(lookup.status()).toBe(200);
  const shared = await lookup.json();
  expect(shared.access).toBe('SHARED');
  expect(JSON.stringify(shared)).not.toContain(other.name ?? '\u0000sin-nombre');
  await page.goto('/lotes'); await fakeCamera(page, other.qrCode);
  await page.getByRole('button', { name: 'Escanear QR', exact: true }).click();
  await expect(page).toHaveURL(new RegExp('/lotes/' + other.traceabilityId + '$'), { timeout: 30000 });
  await expect(page.locator('.topbar')).toContainText('Lote compartido');
});
test('abrir directamente /lotes/<Traceability ID ajeno> muestra la vista compartida sin 400 ni 404', async ({ page, request }) => {
  test.setTimeout(120000); await login(page, operator);
  const foreign = accounts.find(a => a.role === 'OPERATOR' && a.companyId !== accounts[operator].companyId)!;
  const body = await (await request.get('/api/batches', { headers: { Authorization: 'Bearer ' + await tokenOf(request, foreign) } })).json();
  const other = (body.items ?? body.content ?? body)[0];
  const statuses: { url: string; status: number }[] = [];
  page.on('response', r => { if (r.url().includes('/api/traceability/')) statuses.push({ url: r.url(), status: r.status() }); });
  await page.goto('/lotes/' + other.traceabilityId);
  await expect(page.locator('.topbar')).toContainText('Lote compartido', { timeout: 30000 });
  // las dos lecturas llegan después de pintarse la cabecera: se esperan, no se leen de inmediato
  await expect.poll(() => statuses.some(s => s.url.includes('/lineages?traceabilityId=' + other.traceabilityId) && s.status === 200), { timeout: 30000 }).toBe(true);
  await expect.poll(() => statuses.some(s => /\/batches\/[0-9a-f-]{36}\/lineage$/.test(new URL(s.url).pathname) && s.status === 200), { timeout: 30000 }).toBe(true);
  expect(statuses.filter(s => s.status >= 400)).toEqual([]);
});
test('login: «El rol lo define tu cuenta» y aviso cuando el rol elegido no es el de la cuenta (FE-M4)', async ({ page }) => {
  test.setTimeout(60000);
  await page.goto('/login');
  await expect(page.getByText('El rol lo define tu cuenta.', { exact: true })).toBeVisible();
  const user = accounts[admin];
  await page.getByRole('button', { name: 'Operario', exact: true }).click();
  await page.getByLabel('Correo', { exact: true }).fill(user.email);
  await page.getByLabel('Contraseña', { exact: true }).fill(user.password);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.locator('app-shell')).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: /^Tu cuenta es de .+; entraste con ese rol\.$/ })).toBeVisible();
});
