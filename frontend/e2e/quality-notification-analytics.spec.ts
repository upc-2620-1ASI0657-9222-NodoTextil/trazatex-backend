import { test, expect, APIRequestContext, Page } from './base';
import { confirmNext, unfold, saveEvidence } from './support';
// Los lotes se nombran con una marca por ejecución (máx. 20 caracteres): repetir el spec en el mismo stack ya no choca con un nombre existente (409).
const RUN_TAG=String(Date.now()).slice(-6);
const UNIFORM='Alg unif '+RUN_TAG;
const CROSSED='Alg cruz '+RUN_TAG;
import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { credentialsFile } from './paths';

interface Account { key: string; email: string; password: string; role: string; companyId: string; }
const accounts: Account[] = JSON.parse(readFileSync(credentialsFile(),'utf8')).accounts;
const admin = accounts.find(a => a.key === 'admin/hilanderia')!;
const operator = accounts.find(a => a.key === 'operator/hilanderia/1')!;
const other = accounts.find(a => a.key === 'admin/tejeduria')!;
let reviewedBatch: { id: string; traceabilityId: string };
async function session(request: APIRequestContext, account: Account) {
  const response=await request.post('/api/auth/login',{data:{email:account.email,password:account.password}});
  expect(response.ok()).toBeTruthy();
  return (await response.json()).token as string;
}
async function login(page:Page) {
  await page.goto('/login');
  await page.getByRole('button',{name:'Administrador',exact:true}).click();
  await page.getByLabel('Correo',{exact:true}).fill(admin.email);
  await page.getByLabel('Contraseña',{exact:true}).fill(admin.password);
  await page.getByRole('button',{name:'Ingresar',exact:true}).click();
  await expect(page).not.toHaveURL(/\/login/);
  await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
}
test('evaluación, falla, reevaluación y PDF desde Angular con permisos reales',async({page,request}) => {
  test.setTimeout(180000);
  const opToken=await session(request,operator);
  const adminToken=await session(request,admin);
  const otherToken=await session(request,other);
  const available=await request.get('/api/batches?size=100',{headers:{Authorization:'Bearer '+opToken}});
  const reusable=(await available.json()).items.find((b:{name:string;phase:string;qualityStatus:string})=>b.name===UNIFORM && b.phase==='AVAILABLE' && b.qualityStatus==='NOT_REVIEWED');
  const created=reusable ? null : await request.post('/api/batches',{headers:{Authorization:'Bearer '+opToken,'Idempotency-Key':randomUUID()},data:{
    name:UNIFORM,materialType:'Fibra de algodón Pima',quantityKg:12,
    composition:[{name:'Algodón Pima',percentage:100}],origin:'Piura, Perú',supplierName:'Cooperativa Algodonera del Norte',
    receptionCharacteristics:'Partida reservada para revisión de uniformidad.'
  }});
  if (created) expect(created.status()).toBe(201);
  const batch=reusable ?? await created!.json();
  reviewedBatch=batch;
  await login(page);
  await page.getByRole('link',{name:'Calidad',exact:true}).click();
  await page.getByRole('button',{name:'Iniciar control',exact:true}).click();
  await expect(page.getByLabel('Lote a controlar')).toContainText(batch.traceabilityId,{timeout:30000});
  await page.getByLabel('Lote a controlar').selectOption(batch.traceabilityId);
  await page.getByRole('button',{name:'Iniciar y registrar pruebas'}).click();
  await expect(page).toHaveURL(/\/calidad\/[0-9a-f-]+/,{timeout:30000});
  const controlId=page.url().split('/').pop()!;
  const unfinished=await request.get('/api/quality/controls/'+controlId+'/report',{headers:{Authorization:'Bearer '+adminToken}});
  expect(unfinished.status()).toBe(409);
  expect((await request.get('/api/quality/controls/'+controlId,{headers:{Authorization:'Bearer '+opToken}})).status()).toBe(403);
  expect((await request.get('/api/quality/controls/'+controlId,{headers:{Authorization:'Bearer '+otherToken}})).status()).toBe(404);
  await unfold(page,'Pruebas');await page.getByLabel('Criterio',{exact:true}).fill('Uniformidad de fibra');
  await page.getByLabel('Valor esperado',{exact:true}).fill('Mayor o igual a 95');
  await page.getByLabel('Valor obtenido',{exact:true}).fill('89');
  await page.getByLabel('Unidad',{exact:true}).fill('%');
  await page.getByRole('button',{name:'Con falla',exact:true}).click();
  await page.getByRole('button',{name:'Agregar prueba'}).click();
  await expect(page.getByText('Prueba registrada',{exact:true})).toBeVisible();
  await unfold(page,/^Evidencias/);
  await page.locator('app-evidence input[type=file]').setInputFiles({name:'muestra-uniformidad.jpg',mimeType:'image/jpeg',buffer:readFileSync('public/login/imagen4_login.jpg')});
  await saveEvidence(page);
  await expect(page.getByText('Evidencia guardada.',{exact:true})).toBeVisible();
  await expect.poll(async()=>{
    const response=await request.get('/api/quality/controls/'+controlId+'/evidence',{headers:{Authorization:'Bearer '+adminToken}});
    return (await response.json()).items[0]?.status;
  },{timeout:90000,intervals:[3000]}).toBe('UPLOADED');
  await page.getByRole('button',{name:'Finalizar control',exact:true}).click();
  await page.getByRole('button',{name:'Finalizar como Con falla'}).click();
  await expect(page).toHaveURL(/\/fallas\/[0-9a-f-]+/,{timeout:30000});
  const failureId=page.url().split('/').pop()!;
  await unfold(page,/Registro y decisión/);await page.getByLabel('Causa',{exact:true}).fill('Uniformidad insuficiente de la muestra');
  await page.getByLabel('Cantidad afectada (kg)').fill('4');
  await page.getByRole('button',{name:'Guardar registro'}).click();
  await expect(page.getByText('Registro de la falla guardado',{exact:true})).toBeVisible();
  await page.screenshot({path:'test-results/falla-integrada.png',fullPage:true});
  await confirmNext(page);
  await page.getByRole('button',{name:'Enviar a reevaluación',exact:true}).click();
  await expect.poll(async()=> {
    const result=await request.get('/api/batches/'+batch.id,{headers:{Authorization:'Bearer '+opToken}});
    return (await result.json()).qualityStatus;
  }).toBe('PENDING_REEVALUATION');
  await page.getByRole('link',{name:'Calidad',exact:true}).first().click();
  await page.getByRole('button',{name:'Iniciar control',exact:true}).click();
  await page.getByLabel('Lote a controlar').selectOption(batch.traceabilityId);
  await page.getByRole('button',{name:'Iniciar y registrar pruebas'}).click();
  await expect(page).toHaveURL(/\/calidad\/[0-9a-f-]+/,{timeout:30000});
  await unfold(page,'Pruebas');await page.getByLabel('Criterio',{exact:true}).fill('Uniformidad de fibra');
  await page.getByLabel('Valor esperado',{exact:true}).fill('Mayor o igual a 95');
  await page.getByLabel('Valor obtenido',{exact:true}).fill('98');
  await page.getByLabel('Unidad',{exact:true}).fill('%');
  await page.getByRole('button',{name:'Agregar prueba'}).click();
  await expect(page.getByText('Prueba registrada',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Finalizar control',exact:true}).click();
  await page.getByRole('button',{name:'Finalizar como Conforme'}).click();
  await expect.poll(async()=> {
    const result=await request.get('/api/quality/failures/'+failureId,{headers:{Authorization:'Bearer '+adminToken}});
    return (await result.json()).status;
  }).toBe('RESOLVED');
  await page.goto('/calidad/'+controlId);
  await expect(page.getByRole('button',{name:'Informe PDF'})).toBeVisible({timeout:30000});
  // Tras mucha navegación el Gateway puede responder 429 (cubo de 120/min por usuario); la pantalla ofrece «Reintentar», que es lo que se usa.
  let file=null;
  for(let attempt=0;attempt<5&&!file;attempt++){
    const pending=page.waitForEvent('download',{timeout:10000}).catch(()=>null);
    const retry=page.getByRole('button',{name:'Reintentar',exact:true});
    if(await retry.count()) await retry.click(); else await page.getByRole('button',{name:'Informe PDF'}).click();
    file=await pending;
    if(!file) await page.waitForTimeout(4000);
  }
  expect(file,'el informe PDF se descarga (con reintentos ante 429)').toBeTruthy();
  await file!.saveAs('test-results/control-calidad.pdf');
  expect(readFileSync('test-results/control-calidad.pdf').subarray(0,5).toString()).toBe('%PDF-');
  await page.screenshot({path:'test-results/control-integrado.png',fullPage:true});
});
test('notificaciones persistentes, aislamiento por empresa e indicadores por periodo',async({page,request}) => {
  test.setTimeout(90000);
  const token=await session(request,admin);
  const otherToken=await session(request,other);
  const opToken=await session(request,operator);
  const headers={Authorization:'Bearer '+token};
  // Notification I5/I1: página {items,...}; `type` es un código estable y la alerta es una por falla y empresa (data.batchIds).
  const response=await request.get('/api/notifications?type=POTENTIAL_DERIVED_FAILURE&size=100',{headers});
  expect(response.status()).toBe(200);
  const paged=await response.json();
  expect(Array.isArray(paged.items)).toBe(true);
  const notice=paged.items.find((n:{type:string})=>n.type==='POTENTIAL_DERIVED_FAILURE');
  expect(notice).toBeTruthy();
  expect(Array.isArray(notice.data?.batchIds)).toBe(true);
  expect((await request.put('/api/notifications/'+notice.id+'/read',{headers:{Authorization:'Bearer '+otherToken}})).status()).toBe(404);
  await request.put('/api/notifications/'+notice.id+'/read?value=false',{headers});
  await login(page);
  await page.getByRole('complementary',{name:'Navegación principal'}).getByRole('link', { name: /^Notificaciones/ }).click();
  // Ronda 4a (fila 26): el shell tiene el único h1; la página trae su propio h2 con el mismo nombre.
  await expect(page.getByRole('heading',{name:'Notificaciones',exact:true,level:1})).toBeVisible();
  await expect(page.getByRole('heading',{level:1})).toHaveCount(1);
  await expect(page.locator('li.unread').first()).toBeVisible({timeout:30000});
  await page.getByRole('button',{name:'Marcar como leídas'}).click();
  await expect.poll(async()=> {
    const result=await request.get('/api/notifications?unread=true&size=1',{headers});
    return (await result.json()).totalElements;
  }).toBe(0);
  await page.reload();
  await expect(page.getByRole('button',{name:'Marcar como leídas'})).toBeDisabled({timeout:30000});
  await page.screenshot({path:'test-results/notificaciones-integradas.png',fullPage:true});
  const stats=await request.get('/api/analytics/summary?from='+new Date().getFullYear()+'-01-01&to='+new Date().getFullYear()+'-12-31',{headers});
  expect(stats.status()).toBe(200);
  const summary=await stats.json();
  expect(summary.quality.reduce((n:number,r:{count:number})=>n+r.count,0)).toBeGreaterThan(0);
  expect(summary.failureCauses.length).toBeGreaterThan(0);
  expect(summary.durations.some((d:{operation:string})=>d.operation==='Controles de calidad')).toBeTruthy();
  expect(summary.materials.shrinkageKg).toBeGreaterThanOrEqual(0);
  const forbidden=await request.get('/api/analytics/summary?from=2026-10-01&to=2026-10-26',{headers:{Authorization:'Bearer '+opToken}});
  expect(forbidden.status()).toBe(403);expect((await forbidden.json()).code).toBe('FORBIDDEN');
  const reversed=await request.get('/api/analytics/summary?from=2026-10-26&to=2026-10-01',{headers});
  expect(reversed.status()).toBe(400);expect((await reversed.json()).code).toBe('INVALID_PERIOD');
  const empty=await request.get('/api/analytics/summary?from=2000-01-01&to=2000-01-02',{headers});
  expect((await empty.json()).quality).toEqual([]);
  await page.getByRole('link',{name:'Analítica',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Analítica',exact:true,level:1})).toBeVisible();
  await expect(page.getByRole('heading',{level:1})).toHaveCount(1);
  await expect(page.getByText('Lotes por estado de calidad',{exact:true})).toBeVisible({timeout:30000});
  await page.screenshot({path:'test-results/analitica-integrada.png',fullPage:true});
  await page.getByRole('tab',{name:'Fallas',exact:true}).click();
  await expect(page.getByText('Cantidad de fallas por causa',{exact:true})).toBeVisible();
  await page.getByRole('tab',{name:'Tiempos',exact:true}).click();
  await expect(page.getByText('Controles de calidad',{exact:true})).toBeVisible();
  await page.getByRole('tab',{name:'Pérdidas',exact:true}).click();
  await expect(page.getByText('Merma acumulada',{exact:true})).toBeVisible();
});

test('valores de calidad y causa de falla se comparten por separado en B2B',async({request})=>{
  test.setTimeout(60000);
  const token=await session(request,admin);
  if (!reviewedBatch) {
    const batches=await (await request.get('/api/batches?size=100',{headers:{Authorization:'Bearer '+token}})).json();
    reviewedBatch=batches.items.find((batch:{name:string;qualityStatus:string})=>
      batch.name===UNIFORM && batch.qualityStatus==='CONFORMING');
  }
  expect(reviewedBatch).toBeTruthy();
  const foreignToken=await session(request,other);
  const headers={Authorization:'Bearer '+token};
  const original=await (await request.get('/api/privacy-settings',{headers})).json();
  const patch=async(fields:Record<string,string>)=>{
    const result=await request.put('/api/privacy-settings',{headers,data:{fields}});
    expect(result.ok()).toBeTruthy();
  };
  const node=async(token:string)=>{
    const response=await request.get('/api/traceability/batches/'+reviewedBatch.id+'/lineage',{headers:{Authorization:'Bearer '+token}});
    expect(response.status()).toBe(200);
    return (await response.json()).nodes.find((n:{id:string})=>n.id===reviewedBatch.id);
  };
  await expect.poll(async()=>((await node(token))?.qualityTests?.[0]?.obtainedValue)).toBe('98');
  try {
    await patch({QUALITY_TEST_VALUES:'PRIVATE',FAILURE_CAUSE:'PRIVATE'});
    await expect.poll(async()=>Object.hasOwn(await node(foreignToken),'qualityTests')).toBe(false);
    await patch({QUALITY_TEST_VALUES:'SHARED'});
    await expect.poll(async()=>((await node(foreignToken))?.qualityTests?.[0]?.obtainedValue)).toBe('98');
    const shared=await node(foreignToken);
    expect(shared.failureCause).toBeUndefined();
    expect(shared.qualityTests[0].remarks).toBeUndefined();
    expect(shared.inspectorId).toBeUndefined();
    await patch({FAILURE_CAUSE:'SHARED'});
    // I15 (progress.md): con la falla RESOLVED (el lote ya se reevaluó y se liberó) la causa del grafo queda vacía aunque se comparta; la causa visible de una falla CONFIRMED se cubre en ronda5c-privacy.spec.ts.
    await expect.poll(async()=>(await node(foreignToken))?.failureCause).toBe('');
  } finally {
    await patch({QUALITY_TEST_VALUES:original.fields.QUALITY_TEST_VALUES,FAILURE_CAUSE:original.fields.FAILURE_CAUSE});
  }
});

test('invitación visible, cupo real y cancelación persistente desde Usuarios',async({page,request})=>{
  test.setTimeout(90000);
  const token=await session(request,admin);
  const foreignToken=await session(request,other);
  const headers={Authorization:'Bearer '+token};
  const before=await (await request.get('/api/companies/current/capacity',{headers})).json();
  const email='elena.inspeccion+'+Date.now()+'@pima.test';
  await login(page);
  await page.getByRole('link',{name:'Usuarios',exact:true}).click();
  await page.getByRole('button',{name:'Invitar Operario',exact:true}).click();
  const dialog=page.getByRole('dialog');
  await dialog.getByLabel('Nombre del invitado').fill('Elena Suárez');
  await dialog.getByLabel('Correo',{exact:true}).fill(email);
  await dialog.getByLabel('Cargo').fill('Inspección de recepción');
  await dialog.getByRole('button',{name:'Enviar invitación',exact:true}).click();
  await expect(dialog).toHaveCount(0);
  // la lista se pagina (6 por página) y crece con cada ejecución: se busca la invitación por su correo
  await page.getByRole('tab',{name:/^Invitaciones/}).click();
  await page.getByLabel('Buscar',{exact:true}).fill(email);
  await expect(page.locator('.rc-meta').filter({hasText:email})).toBeVisible();
  const invitationsBody=await (await request.get('/api/invitations?size=100',{headers})).json();
  const invitations=invitationsBody.items??invitationsBody;
  const invitation=invitations.find((i:{email:string})=>i.email===email);
  expect(invitation.status).toBe('PENDING');
  const after=await (await request.get('/api/companies/current/capacity',{headers})).json();
  expect(after.pendingInvitations).toBe(before.pendingInvitations+1);
  expect((await request.post('/api/invitations/'+invitation.id+'/cancel',{headers:{Authorization:'Bearer '+foreignToken}})).status()).toBe(403);
  await page.getByLabel('Buscar',{exact:true}).fill(email);
  await confirmNext(page);
  await page.getByRole('button',{name:'Cancelar invitación',exact:true}).click();
  await expect(page.locator('.rc-state').filter({hasText:'Cancelada'})).toBeVisible();
  await page.reload();
  await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
  await page.getByRole('tab',{name:/^Invitaciones/}).click();
  await page.getByLabel('Buscar',{exact:true}).fill(email);
  await expect(page.locator('.rc-state').filter({hasText:'Cancelada'})).toBeVisible();
  const final=await (await request.get('/api/companies/current/capacity',{headers})).json();
  expect(final.pendingInvitations).toBe(before.pendingInvitations);
});

test('teclado en modales, horario de Lima y pantallas nuevas en móvil',async({page})=>{
  test.setTimeout(90000);
  await login(page);
  await page.getByRole('link',{name:'Usuarios',exact:true}).click();
  const invite=page.getByRole('button',{name:'Invitar Operario',exact:true});
  await invite.click();
  const dialog=page.getByRole('dialog');
  await expect(dialog.getByRole('button',{name:'Cerrar',exact:true})).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(dialog.getByRole('button',{name:'Enviar invitación',exact:true})).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(dialog.getByRole('button',{name:'Cerrar',exact:true})).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(invite).toBeFocused();
  const response=page.waitForResponse(r=>r.url().includes('/api/analytics/summary?') && r.status()===200);
  await page.getByRole('link',{name:'Analítica',exact:true}).click();
  const generatedAt=(await (await response).json()).generatedAt;
  const lima=new Date(Date.parse(generatedAt)-5*3600000).toISOString().slice(11,16);
  await expect(page.getByText(/Actualizado:/)).toContainText('/ '+lima);
  const rail=page.getByRole('complementary',{name:'Navegación principal'});
  const menuRight=async()=>{const box=await rail.boundingBox();return box?box.x+box.width:0;};
  await page.setViewportSize({width:390,height:844});
  await expect.poll(menuRight).toBeLessThanOrEqual(1);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1)).toBe(true);
  await page.screenshot({path:'test-results/analitica-movil.png',fullPage:true});
  await page.getByRole('button',{name:'Abrir menú',exact:true}).click();
  await page.getByRole('complementary',{name:'Navegación principal'}).getByRole('link', { name: /^Notificaciones/ }).click();
  await expect(page.getByRole('heading',{name:'Notificaciones',exact:true,level:1})).toBeVisible();
  await expect.poll(menuRight).toBeLessThanOrEqual(1);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1)).toBe(true);
  await page.screenshot({path:'test-results/notificaciones-movil.png',fullPage:true});
});

test('evidencia de falla desde Angular y rechazo de una imagen inválida',async({page,request})=>{
  test.setTimeout(150000);
  await page.waitForTimeout(25000); // recarga del cubo de 120/min por usuario del Gateway: las pruebas anteriores usan la misma cuenta; el límite no se toca
  const token=await session(request,admin);
  const headers={Authorization:'Bearer '+token};
  const failures=(await (await request.get('/api/quality/failures?size=100',{headers})).json()).items;
  const failure=failures.find((item:{rootCause:string})=>item.rootCause==='Uniformidad insuficiente de la muestra');
  expect(failure).toBeTruthy();
  await login(page);
  await page.goto('/fallas/'+failure.id);
  await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
  await unfold(page,/^Evidencias/);
  const input=page.locator('app-evidence input[type=file]');
  const filename='muestra-falla-'+Date.now()+'.jpg';
  await input.setInputFiles({name:filename,mimeType:'image/jpeg',buffer:readFileSync('public/login/imagen4_login.jpg')});
  await saveEvidence(page);
  await expect(page.getByText('Evidencia guardada.',{exact:true})).toBeVisible();
  await expect.poll(async()=>{
    const result=await request.get('/api/quality/failures/'+failure.id+'/evidence',{headers});
    return (await result.json()).items.find((item:{originalFilename:string})=>item.originalFilename===filename)?.status;
  },{timeout:90000,intervals:[3000]}).toBe('UPLOADED');
  const rejected=page.waitForResponse(r=>r.url().endsWith('/api/quality/evidence') && r.request().method()==='POST');
  await input.setInputFiles({name:'imagen-invalida.png',mimeType:'image/png',buffer:Buffer.from('Este archivo no contiene una imagen.')});
  await saveEvidence(page);
  expect((await rejected).status()).toBe(400);
  const toast=page.locator('.toast--error');
  await expect(toast).toBeVisible();
  // Ronda 4a (fila 25, FE-I8): un aviso de error NO se retira solo; se queda hasta pulsar «Cerrar» y es role="alert".
  await expect(toast).toHaveAttribute('role','alert');
  await page.waitForTimeout(8000);
  await expect(toast).toBeVisible();
  await toast.locator('.toast-close').click();
  await expect(toast).toHaveCount(0);
});



test('descarte, alerta derivada y revisión preventiva completa desde Angular',async({page,request})=>{
  test.setTimeout(270000);
  await page.waitForTimeout(25000); // recarga del cubo de 120/min por usuario del Gateway: las pruebas anteriores usan la misma cuenta; el límite no se toca
  const operatorToken=await session(request,operator);
  const adminToken=await session(request,admin);
  const headers={Authorization:'Bearer '+operatorToken,'Idempotency-Key':randomUUID()};
  const created=await request.post('/api/batches',{headers,data:{
    name:CROSSED,materialType:'Fibra de algodón Pima',quantityKg:80,
    composition:[{name:'Algodón Pima',percentage:100}],origin:'Piura, Perú',
    receptionCharacteristics:'Dos partidas de una misma recepción para contrastar humedad.'
  }});
  expect(created.status()).toBe(201);
  const root=await created.json();
  const split=await request.post('/api/batches/'+root.id+'/split',{headers:{...headers,'Idempotency-Key':randomUUID()},
    data:{children:[{name:'Humedad fuera rango',quantityKg:40},{name:'Revision humedad',quantityKg:40}]}});
  expect(split.status()).toBe(201);
  const [source,related]=(await split.json()).children;
  await login(page);
  const start=async(code:string)=>{
    await page.getByRole('complementary',{name:'Navegación principal'}).getByRole('link',{name:'Calidad',exact:true}).click();
    await page.getByRole('button',{name:'Iniciar control',exact:true}).click();
    await page.getByLabel('Lote a controlar').selectOption(code);
    await page.getByRole('button',{name:'Iniciar y registrar pruebas'}).click();
    await expect(page).toHaveURL(/\/calidad\/[0-9a-f-]+/,{timeout:30000});
  };
  const finish=async(failed:boolean)=>{
    await unfold(page,'Pruebas');await page.getByLabel('Criterio',{exact:true}).fill('Humedad de la fibra');
    await page.getByLabel('Valor esperado',{exact:true}).fill('Entre 7 y 9');
    await page.getByLabel('Valor obtenido',{exact:true}).fill(failed?'13':'8');
    await page.getByLabel('Unidad',{exact:true}).fill('%');
    if(failed) await page.getByRole('button',{name:'Con falla',exact:true}).click();
    await page.getByRole('button',{name:'Agregar prueba'}).click();
    await expect(page.getByText('Prueba registrada',{exact:true})).toBeVisible();
    await page.getByRole('button',{name:'Finalizar control',exact:true}).click();
    await page.getByRole('button',{name:failed?'Finalizar como Con falla':'Finalizar como Conforme',exact:true}).click();
  };
  await start(source.traceabilityId);
  await finish(true);
  await expect(page).toHaveURL(/\/fallas\/[0-9a-f-]+/);
  await unfold(page,/Registro y decisión/);await page.getByLabel('Causa',{exact:true}).fill('Exceso de humedad en una partida de recepción');
  await page.getByLabel('Cantidad afectada (kg)').fill('12');
  await page.getByRole('button',{name:'Guardar registro'}).click();
  await expect(page.getByText('Registro de la falla guardado',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Descartar lote',exact:true}).click();
  await page.getByRole('dialog').getByRole('button',{name:'Descartar lote',exact:true}).click();
  await expect.poll(async()=>{
    const result=await (await request.get('/api/batches/'+related.id,{headers:{Authorization:'Bearer '+operatorToken}})).json();
    return result.qualityStatus+'/'+result.qualityHold;
  },{timeout:30000}).toBe('POTENTIAL_DERIVED_FAILURE/true');
  expect((await (await request.get('/api/batches/'+source.id,{headers:{Authorization:'Bearer '+operatorToken}})).json()).phase).toBe('DISCARDED');
  const blocked=await request.post('/api/batches/'+related.id+'/split',{headers:{...headers,'Idempotency-Key':randomUUID()},
    data:{children:[{name:'Primera mitad',quantityKg:20},{name:'Segunda mitad',quantityKg:20}]}});
  expect(blocked.status()).toBe(409);
  await page.getByRole('complementary',{name:'Navegación principal'}).getByRole('link', { name: /^Notificaciones/ }).click();
  // El aviso abre el lote que falló (resourceId) y resume «y N más» (data.batchIds); el lote relacionado se comprueba en su propia ficha.
  const notice=page.locator('li').filter({hasText:'Posible falla derivada'}).first();
  await expect(notice).toBeVisible({timeout:45000});
  await notice.locator('button.body').click();
  await expect(page).toHaveURL(/\/lotes\/TT-/);
  await page.goto('/lotes/'+related.traceabilityId);
  await expect(page.locator('main.content')).toContainText('Posible falla derivada');
  await start(related.traceabilityId);
  const controlId=page.url().split('/').pop();
  const control=await (await request.get('/api/quality/controls/'+controlId,{headers:{Authorization:'Bearer '+adminToken}})).json();
  expect(control.controlType).toBe('EVALUATION');
  await finish(false);
  await expect.poll(async()=>{
    const result=await (await request.get('/api/batches/'+related.id,{headers:{Authorization:'Bearer '+operatorToken}})).json();
    return result.qualityStatus+'/'+result.qualityHold+'/'+result.phase;
  }).toBe('CONFORMING/false/AVAILABLE');
});