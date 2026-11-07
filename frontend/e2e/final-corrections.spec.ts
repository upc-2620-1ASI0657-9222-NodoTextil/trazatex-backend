import { test, expect, Page, APIRequestContext } from './base';
import { readFileSync, writeFileSync, existsSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { credentialsFile, localFile, resultsDir } from './paths';
import { psql } from './support';
const root = resolve('..');
const accounts = JSON.parse(readFileSync(credentialsFile(), 'utf8')).accounts;
const credentials = (key: string) => accounts.find((item: {key: string}) => item.key === key);
const checkpoint = localFile('correction-checks.json');
// El punto de control solo vale para la instalación sembrada que lo creó (se ata a la marca de tiempo de credentials.json); con otra siembra se descarta.
const installation = String(statSync(credentialsFile()).mtimeMs);
const previous = existsSync(checkpoint) ? JSON.parse(readFileSync(checkpoint, 'utf8')) : undefined;
const checks = previous?.installation === installation ? previous : {installation, splits: []};
const save = () => writeFileSync(checkpoint, JSON.stringify(checks, null, 2));
async function session(request: APIRequestContext, key = 'operator/hilanderia/1') {
  const account = credentials(key);
  const response = await request.post('/api/auth/login', {data: {email: account.email, password: account.password}});
  expect(response.ok()).toBeTruthy();
  const auth = await response.json();
  return {Authorization: 'Bearer ' + auth.token};
}
async function login(page: Page, key = 'operator/hilanderia/1') {
  const account = credentials(key);
  await page.goto('/login');
  await page.getByLabel('Correo', {exact: true}).fill(account.email);
  await page.getByLabel('Contraseña', {exact: true}).fill(account.password);
  await page.getByRole('button', {name: 'Ingresar', exact: true}).click();
  await expect(page.locator('main.content')).toHaveAttribute('aria-busy', 'false', {timeout: 60000});
}
async function get(request: APIRequestContext, path: string, headers: Record<string,string>) {
  const response = await request.get('/api' + path, {headers});
  expect(response.ok(), path).toBeTruthy(); return response.json();
}
async function create(request: APIRequestContext, headers: Record<string,string>, name: string, quantityKg = 120) {
  const response = await request.post('/api/batches', {headers, data: {name, materialType: 'Fibra de algodón Pima', quantityKg,
    composition: [{name: 'Algodón Pima', percentage: 100}], origin: 'Piura', supplierName: 'Cooperativa del Norte', receptionCharacteristics: 'Fibra recibida y pesada en almacén.'}});
  expect(response.status()).toBe(201); return response.json();
}

test('divisiones de dos, tres y cuatro hijos desde Angular hasta Neo4j', async ({page, request}) => {
  test.setTimeout(240000);
  await login(page); const auth = await session(request);
  for (const count of [2,3,4]) {
    if (checks.splits.includes(count)) continue;
    await page.goto('/lotes');
    await expect(page.locator('button.row-card')).toHaveCount(8);
    await page.getByRole('button', {name: 'Registrar lote', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Registrar lote', exact: true});
    await dialog.getByLabel('Nombre del lote', {exact: true}).fill('Pima Reserva ' + count);
    await dialog.getByLabel('Cantidad (kg)', {exact: true}).fill('120');
    await dialog.getByLabel('Origen geográfico', {exact: true}).fill('Piura');
    await dialog.getByLabel('Características técnicas de recepción', {exact: true}).fill('Fibra seca, recepción documentada de 120 kg.');
    await dialog.getByRole('button', {name: 'Continuar', exact: true}).click();
    if (count === 2) {
      await dialog.getByRole('button', {name: 'Agregar componente', exact: true}).click();
      await dialog.getByLabel('Componente 2', {exact: true}).fill('Poliéster');
      await dialog.getByLabel('Porcentaje 2', {exact: true}).fill('25');
      await expect(dialog.getByLabel('Porcentaje 1', {exact: true})).toHaveValue('75');
      await dialog.locator('input[type=file]').setInputFiles(resolve(root, 'frontend/public/brand/favicon-64.png'));
      await expect(dialog.locator('.evid img')).toBeVisible();
      await dialog.getByLabel('Nombre descriptivo', {exact: true}).fill('Recepción de fibra');
      await dialog.getByRole('button', {name: 'Quitar fotografía', exact: true}).click();
      await expect(dialog.locator('.evid img')).toHaveCount(0);
    }
    await dialog.getByRole('button', {name: 'Continuar', exact: true}).click();
    const observed: string[] = [];
    const observe = (response: any) => { if (response.request().method() === 'GET') observed.push(new URL(response.url()).pathname); };
    page.on('response', observe);
    const response = page.waitForResponse(response => response.url().endsWith('/api/batches') && response.request().method() === 'POST');
    const started = Date.now();
    await dialog.getByRole('button', {name: 'Registrar lote', exact: true}).click();
    const parent = await (await response).json();
    await expect(page).toHaveURL(new RegExp('/lotes/' + parent.traceabilityId));
    page.off('response', observe);
    expect(observed).not.toContain('/api/transformations');
    expect(observed).not.toContain('/api/transfers');
    console.log('Guardar lote sin recarga general: ' + (Date.now() - started) + ' ms');
    await page.getByRole('button', {name: 'Dividir', exact: true}).click();
    const split = page.getByRole('dialog', {name: 'Dividir Pima Reserva ' + count});
    await split.getByRole('button', {name: 'Continuar', exact: true}).click();
    for (let i=2; i<count; i++) await split.getByRole('button', {name: 'Agregar hijo', exact: true}).click();
    for (let i=0; i<count; i++) {
      await split.getByLabel('Nombre hijo ' + (i+1), {exact:true}).fill('Pima R' + count + ' Partida ' + (i+1));
      await expect(split.getByLabel('Cantidad hijo ' + (i+1), {exact:true})).toHaveValue(String(120/count));
    }
    await split.getByRole('button', {name: 'Continuar', exact: true}).click();
    await expect(split).toContainText('Cantidad total validada');
    await split.getByRole('button', {name: 'Continuar', exact: true}).click();
    const splitResponse = page.waitForResponse(response => response.url().endsWith('/split') && response.request().method() === 'POST');
    await split.getByRole('button', {name: 'Dividir lote', exact: true}).click();
    const result = await (await splitResponse).json();
    expect(result.children).toHaveLength(count);
    await expect(page).toHaveURL(/\/lotes$/);
    await expect.poll(async () => { const response = await request.get('/api/traceability/batches/' + parent.id + '/lineage', {headers:auth}); if (response.status() === 404) return 0; expect(response.ok()).toBeTruthy(); return (await response.json()).edges.filter((edge: any) => edge.from === parent.id && edge.type === 'SPLIT_INTO').length; }, {timeout:30000}).toBe(count);
    const graph = await get(request, '/traceability/batches/' + parent.id + '/lineage', auth);
    for (const child of result.children) expect(graph.nodes.find((node: any) => node.id === child.id).name).toBe(child.name);
    await page.goto('/trazabilidad?lote=' + parent.traceabilityId);
    for (const child of result.children) {
      const node = page.locator('[data-node="' + child.traceabilityId + '"]');
      await expect(node).toBeAttached({timeout:30000}); await expect(node).toContainText(child.name);
    }
    await page.getByLabel('Buscar lote', {exact:true}).fill('Pima Reserva ' + count);
    await page.getByRole('button', {name:'Trazar',exact:true}).click();
    await expect(page.getByRole('button', {name:'Limpiar búsqueda',exact:true})).toBeVisible();
    await page.getByRole('button', {name:'Limpiar búsqueda',exact:true}).click();
    await expect(page.getByLabel('Buscar lote',{exact:true})).toHaveValue('');
    checks.splits.push(count); save();
  }
  const invalid = await request.post('/api/batches', {headers:auth,data:{name:'123456789012345678901',materialType:'Fibra',quantityKg:1,composition:[{name:'Algodón',percentage:100}],origin:'Piura',receptionCharacteristics:'Fibra limpia'}});
  expect(invalid.status()).toBe(400);
  await page.screenshot({path:resolve(resultsDir(),'genealogy-check.png')});
});

test('balance corregible y transformación con varias salidas reales', async ({page,request}) => {
  test.setTimeout(150000); const auth=await session(request);
  let process=checks.process;
  if (!process) {
    const input=await create(request,auth,'Pima Costa Hilado',100);
    const machines=await get(request,'/machines',auth);
    const response=await request.post('/api/transformations',{headers:auth,data:{type:'SPINNING',machineId:machines.find((m:any)=>m.status==='ACTIVE').id,batchIds:[input.id]}});
    expect(response.status()).toBe(201); process=await response.json();checks.process=process;save();
  }
  if (!checks.completedProcess) {
    await login(page);await page.goto('/procesos/'+process.id);
    await page.getByRole('button',{name:'Finalizar transformación',exact:true}).click();
    const dialog=page.getByRole('dialog',{name:'Finalizar transformación',exact:true});
    await expect(dialog).toContainText('Pima Costa Hilado');
    await dialog.getByLabel('Nombre del lote de salida',{exact:true}).fill('Hilo Costa Claro');
    await dialog.getByLabel('Cantidad salida 1',{exact:true}).fill('120');
    await dialog.getByRole('button',{name:'Continuar',exact:true}).click();
    await dialog.getByRole('button',{name:'Continuar',exact:true}).click();
    await dialog.getByRole('button',{name:'Continuar',exact:true}).click();
    await dialog.getByRole('button',{name:'Corregir pesos de salida',exact:true}).click();
    await expect(dialog.getByLabel('Cantidad salida 1',{exact:true})).toBeFocused();
    await dialog.getByLabel('Cantidad salida 1',{exact:true}).fill('45');
    await dialog.getByRole('button',{name:'Agregar lote de salida',exact:true}).click();
    await dialog.getByLabel('Nombre del lote de salida',{exact:true}).nth(1).fill('Hilo Costa Fino');
    await dialog.getByLabel('Cantidad salida 2',{exact:true}).fill('45');
    await dialog.getByRole('button',{name:'Continuar',exact:true}).click();await dialog.getByRole('button',{name:'Continuar',exact:true}).click();
    await dialog.getByLabel(/Desperdicio \(kg\)/).fill('5');
    await dialog.getByLabel('Motivo del desperdicio',{exact:true}).fill('Fibras cortas separadas');
    await dialog.getByRole('button',{name:'Continuar',exact:true}).click();
    await dialog.getByRole('button',{name:'Finalizar transformación',exact:true}).click();
    await expect(page).toHaveURL(/\/lotes$/);
    const actual=await get(request,'/transformations/'+process.id,auth);expect(actual.outputBatchIds).toHaveLength(2);expect(actual.shrinkageKg).toBe(5);
    checks.completedProcess=true;save();
  }
});

test('Quality con previsualización, eliminación real, finalización y PDF autorizado',async({page,request})=>{
  test.setTimeout(180000); const op=await session(request);const admin=await session(request,'admin/hilanderia');
  if(!checks.quality){const batch=await create(request,op,'Pima Control Costa',30);await expect.poll(async()=> (await get(request,'/quality/batches?size=100',admin)).items.some((b:any)=>b.batchId===batch.id),{timeout:30000}).toBe(true);const response=await request.post('/api/quality/batches/'+batch.id+'/controls',{headers:admin,data:{}});expect(response.status()).toBe(201);checks.quality={batch,control:await response.json()};save();}
  const {control}=checks.quality;
  await expect.poll(async()=>(await get(request,'/quality/controls/'+control.id,admin)).status,{timeout:30000}).toBe('IN_PROGRESS');
  await login(page,'admin/hilanderia');await page.goto('/calidad/'+control.id);
  const evidence=page.locator('app-evidence');
  await evidence.locator('input[type=file]').setInputFiles(resolve(root,'frontend/public/brand/favicon-64.png'));
  await evidence.getByLabel('Nombre descriptivo',{exact:true}).fill('Recepción de fibra');
  await expect(evidence.locator('.evidence-draft img')).toBeVisible();
  await evidence.getByRole('button',{name:'Quitar fotografía',exact:true}).click();
  await expect(evidence.locator('.evidence-draft')).toHaveCount(0);
  await evidence.locator('input[type=file]').setInputFiles(resolve(root,'frontend/public/brand/favicon-64.png'));
  await evidence.getByLabel('Nombre descriptivo',{exact:true}).fill('Registro de recepción');
  await evidence.getByRole('button',{name:'Guardar evidencia',exact:true}).click();
  await expect.poll(async()=>(await get(request,'/quality/controls/'+control.id+'/evidence',admin)).items.filter((e:any)=>e.status==='UPLOADED').length,{timeout:45000}).toBe(1);
  const photo=(await get(request,'/quality/controls/'+control.id+'/evidence',admin)).items[0];
  expect(photo.originalFilename).toBe('Registro de recepción');
  await evidence.getByRole('button',{name:'Eliminar evidencia',exact:true}).click();
  await page.getByRole('dialog',{name:'Confirmar acción',exact:true}).getByRole('button',{name:'Cancelar',exact:true}).click();
  expect((await get(request,'/quality/controls/'+control.id+'/evidence',admin)).items).toHaveLength(1);
  await evidence.getByRole('button',{name:'Eliminar evidencia',exact:true}).click();
  await page.getByRole('dialog',{name:'Confirmar acción',exact:true}).getByRole('button',{name:'Confirmar',exact:true}).click();
  await expect.poll(async()=>(await get(request,'/quality/controls/'+control.id+'/evidence',admin)).items.length).toBe(0);
  // Consulta directa a la base de Quality del proyecto aislado (por etiquetas de Compose; ver support.psql).
  await expect.poll(()=>Number(psql('quality_db',"SELECT count(*) FROM evidence WHERE id='"+photo.id+"'")),{timeout:30000,intervals:[3000]}).toBe(0);
  await page.getByRole('button',{name:/^Pruebas/}).click();
  await page.getByLabel('Criterio',{exact:true}).fill('Resistencia de la fibra');
  await page.getByLabel('Valor esperado',{exact:true}).fill('Mayor o igual a 28');await page.getByLabel('Valor obtenido',{exact:true}).fill('31');await page.getByLabel('Unidad',{exact:true}).fill('cN/tex');
  await page.getByRole('button',{name:'Agregar prueba',exact:true}).click();
  await expect(page.locator('.mini-cards')).toContainText('Resistencia de la fibra');
  await page.getByRole('button',{name:'Finalizar control',exact:true}).click();await page.getByRole('button',{name:'Finalizar como Conforme',exact:true}).click();
  await expect.poll(async()=>(await get(request,'/quality/controls/'+control.id,admin)).status,{timeout:30000}).toBe('FINISHED');
  const download=page.waitForEvent('download');await page.getByRole('button',{name:'Informe PDF',exact:true}).click();expect((await download).suggestedFilename()).toMatch(/\.pdf$/);
  expect((await request.get('/api/quality/controls/'+control.id+'/report',{headers:op})).status()).toBe(403);
  const foreign=await session(request,'admin/tejeduria');expect((await request.get('/api/quality/controls/'+control.id+'/report',{headers:foreign})).status()).toBe(404);
  await page.screenshot({path:resolve(resultsDir(),'quality-check.png')});
  checks.qualityVerified=true;save();
});


test('composición con centésimas y promedio ponderado igual al backend', async ({page,request}) => {
  test.setTimeout(90000); const auth=await session(request);
  const inputs=[];
  for (const [name,material,quantityKg] of [['Alpaca Reserva','Alpaca',1],['Lana Fina Reserva','Lana',2]] as const) {
    const response=await request.post('/api/batches',{headers:auth,data:{name,materialType:'Fibra de '+material.toLowerCase(),quantityKg,composition:[{name:material,percentage:100}],origin:'Arequipa',supplierName:'Fibras del Altiplano',receptionCharacteristics:'Fibra seleccionada y pesada.'}});expect(response.status()).toBe(201);inputs.push(await response.json());
  }
  const machines=await get(request,'/machines',auth);const started=await request.post('/api/transformations',{headers:auth,data:{type:'SPINNING',machineId:machines.find((m:any)=>m.status==='ACTIVE').id,batchIds:inputs.map(x=>x.id)}});expect(started.status()).toBe(201);const process=await started.json();
  await login(page);await page.goto('/procesos/'+process.id);await page.getByRole('button',{name:'Finalizar transformación',exact:true}).click();const dialog=page.getByRole('dialog',{name:'Finalizar transformación',exact:true});await dialog.getByLabel('Cantidad salida 1',{exact:true}).fill('2.7');await dialog.getByLabel('Nombre del lote de salida',{exact:true}).fill('Hilo Andino');await dialog.getByRole('button',{name:'Continuar',exact:true}).click();await expect(dialog).toContainText('33.33');await expect(dialog).toContainText('66.67');await dialog.getByRole('button',{name:'Continuar',exact:true}).click();await dialog.getByRole('button',{name:'Continuar',exact:true}).click();await dialog.getByRole('button',{name:'Finalizar transformación',exact:true}).click();await expect(dialog).toHaveCount(0);const completed=await get(request,'/transformations/'+process.id,auth);const output=await get(request,'/batches/'+completed.outputBatchIds[0],auth);expect(output.composition).toHaveLength(2);expect(output.composition).toEqual(expect.arrayContaining([{name:'Alpaca',percentage:33.33},{name:'Lana',percentage:66.67}]));
  await page.goto('/lotes');await page.getByRole('button',{name:'Registrar lote',exact:true}).click();const batch=page.getByRole('dialog',{name:'Registrar lote',exact:true});await batch.getByLabel('Nombre del lote',{exact:true}).fill('Fibra Andina');await batch.getByLabel('Cantidad (kg)',{exact:true}).fill('10');await batch.getByLabel('Origen geográfico',{exact:true}).fill('Puno');await batch.getByLabel('Características técnicas de recepción',{exact:true}).fill('Fibra limpia');await batch.getByRole('button',{name:'Continuar',exact:true}).click();await batch.getByRole('button',{name:'Agregar componente',exact:true}).click();await batch.getByLabel('Componente 2',{exact:true}).fill('Alpaca');await batch.getByRole('button',{name:'Agregar componente',exact:true}).click();await batch.getByLabel('Componente 3',{exact:true}).fill('Lana');await batch.getByLabel('Porcentaje 1',{exact:true}).fill('0.1');await batch.getByLabel('Porcentaje 3',{exact:true}).fill('0.2');await batch.getByLabel('Porcentaje 1',{exact:true}).fill('0.1');await batch.getByRole('button',{name:'Quitar componente',exact:true}).nth(2).click();await expect.poll(()=>batch.locator('input[type=number]').evaluateAll(inputs=>inputs.map(input=>Number((input as HTMLInputElement).value))),{timeout:10000}).toEqual([0.3,99.7]);await batch.getByRole('button',{name:'Cerrar',exact:true}).click();
});
