import { test, expect, Page, APIRequestContext } from './base';
import { readFileSync } from 'node:fs';
import { credentialsFile, localFile } from './paths';
const state=JSON.parse(readFileSync(localFile('scenarios.json'),'utf8'));
const treeBatch=JSON.parse(readFileSync(localFile('state.json'),'utf8')).operations['cadena-mixta/v1/weaving/complete'].outputs[0];
const accounts=JSON.parse(readFileSync(credentialsFile(),'utf8')).accounts;
const account=(key:string)=>accounts.find((a:{key:string})=>a.key===key);
async function login(page:Page,key:string){const a=account(key);await page.goto('/login');await page.getByRole('button',{name:key.startsWith('operator')?'Operario':'Administrador',exact:true}).click();await page.getByLabel('Correo',{exact:true}).fill(a.email);await page.getByLabel('Contraseña',{exact:true}).fill(a.password);await page.getByRole('button',{name:'Ingresar',exact:true}).click();await expect(page).not.toHaveURL(/\/login/);await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});}
async function token(request:APIRequestContext,key:string){const a=account(key);const r=await request.post('/api/auth/login',{data:{email:a.email,password:a.password}});expect(r.ok()).toBeTruthy();return(await r.json()).token;}

test('genealogía litoral, estados reales, filtros, falla y PDF autorizado',async({page,request})=>{
 test.setTimeout(150000);
 // Precondición: la hilandería (dueña de las raíces) mantiene el proveedor PRIVADO, como la siembra; otros specs pueden haberlo compartido.
 {const owner=await token(request,'admin/hilanderia');const put=await request.put('/api/privacy-settings',{headers:{Authorization:'Bearer '+owner},data:{fields:{SUPPLIER:'PRIVATE'}}});expect(put.status()).toBe(200);}await login(page,'admin/tejeduria');
 await page.getByLabel('Buscar lote',{exact:true}).fill(treeBatch.traceabilityId);await page.getByRole('button',{name:'Trazar',exact:true}).click();
 const failed=page.locator('.node.lot[data-node="'+state.failedBatch.traceabilityId+'"]');
 const derived=page.locator('.node.lot[data-node="'+state.relatedBatch.traceabilityId+'"]');
 await expect(failed).toHaveClass(/is-fail/);await expect(failed.locator('.qdot')).toHaveClass(/q-fail/);await expect(derived).toHaveClass(/is-derived/);await expect(derived.locator('.qdot')).toHaveClass(/q-derived/);
 expect(await page.locator('svg.edges path').count()).toBeGreaterThan(10);
 await page.getByText('Más filtros',{exact:true}).click();await page.getByLabel('Filtrar por calidad').selectOption('Con falla');await expect(failed).not.toHaveClass(/is-dim/);await expect(derived).toHaveClass(/is-dim/);await page.getByRole('button',{name:'Restablecer filtros'}).click();await expect(derived).not.toHaveClass(/is-dim/);
 const qualityNode=page.locator('.node.ctl[data-node="'+state.reportControlId+'"]');await qualityNode.click();await expect(page.locator('.detail-id')).toHaveText('Control de calidad');await page.getByRole('button',{name:'Abrir control de calidad',exact:true}).click();await expect(page).toHaveURL(new RegExp('/calidad/'+state.reportControlId));
 await page.goto('/fallas/'+state.failureId);await page.getByRole('button',{name:/Registro y decisión/}).click();await expect(page.getByLabel('Causa',{exact:true})).toHaveValue('Tensión irregular del telar en la trama');
 await page.goto('/calidad/'+state.reportControlId);const download=page.waitForEvent('download');await page.getByRole('button',{name:'Informe PDF',exact:true}).click();const file=await download;expect(file.suggestedFilename()).toMatch(/\.pdf$/);const stream=await file.createReadStream();const chunks:Buffer[]=[];for await(const c of stream!)chunks.push(c);expect(Buffer.concat(chunks).subarray(0,5).toString()).toBe('%PDF-');
 const foreign=await token(request,'admin/marca');expect((await request.get('/api/quality/controls/'+state.reportControlId+'/report',{headers:{Authorization:'Bearer '+foreign}})).status()).toBe(404);
 const graph=await request.get('/api/traceability/batches/'+state.finalBatch.id+'/lineage',{headers:{Authorization:'Bearer '+foreign}});expect(graph.ok()).toBeTruthy();const roots=(await graph.json()).nodes.filter((n:{id:string})=>state.rootIds.includes(n.id));expect(roots).toHaveLength(2);for(const node of roots){expect(node.supplierName).toBeUndefined();expect(node.commercialData).toBeUndefined();}
});

test('analítica y notificaciones de las cinco empresas con datos del backend',async({page})=>{
 test.setTimeout(240000);
 for(const key of ['hilanderia','tejeduria','tintoreria','confeccion','marca']){
  await page.context().clearCookies();await page.goto('/login');await page.evaluate(()=>{localStorage.clear();sessionStorage.clear();});await login(page,'admin/'+key);
  const response=page.waitForResponse(r=>r.url().includes('/api/analytics/summary?')&&r.status()===200);await page.getByRole('link',{name:'Analítica',exact:true}).click();const data=await(await response).json();expect(data.quality.reduce((n:number,r:{count:number})=>n+r.count,0)).toBeGreaterThan(0);await expect(page.locator('.kpi .v').first()).toHaveText(String(data.quality.reduce((n:number,r:{count:number})=>n+r.count,0)));
  await page.getByRole('tab',{name:'Pérdidas',exact:true}).click();await expect(page.getByText('Merma acumulada',{exact:true})).toBeVisible();expect(data.materials.completedTransformations).toBeGreaterThan(0);
  if(key==='tejeduria'){await page.getByRole('tab',{name:'Fallas',exact:true}).click();await expect(page.getByText('Cantidad de fallas por causa',{exact:true})).toBeVisible();await expect(page.getByRole('link',{name:'Fallas',exact:true})).toHaveCount(0);}
  await page.getByRole('link', { name: /^Notificaciones/ }).click();await page.getByLabel('Tipo',{exact:true}).selectOption(key==='tintoreria'?'Transferencia aceptada':'Posible falla derivada');await expect(page.locator('.inbox li').first()).toBeVisible();
  if(key==='tejeduria'){const row=page.locator('.inbox li').first();const button=row.getByRole('button',{name:'Marcar como leída',exact:true});if(await button.count())await button.click({timeout:5000}).catch(()=>undefined);await expect(row).not.toHaveClass(/unread/);await row.locator('button.body').click();await expect(page).toHaveURL(/\/lotes\/TT-/);/* el aviso abre el lote que falló (resourceId); «Posible falla derivada» es el estado de sus lotes relacionados, que van en data.batchIds */await expect(page.locator('main.content')).toContainText(/Con falla|Por reevaluar/);}
 }
});

test('control abierto y permisos del operario en los datos nuevos',async({page,request})=>{
 test.setTimeout(100000);await login(page,'admin/confeccion');await page.goto('/calidad/'+state.openControlId);await page.getByRole('button',{name:/Pruebas/}).click();await expect(page.getByRole('button',{name:'Agregar prueba'})).toBeVisible();
 const admin=await token(request,'admin/confeccion');const auth={Authorization:'Bearer '+admin};expect((await request.get('/api/quality/controls/'+state.openControlId+'/report',{headers:auth})).status()).toBe(409);
 await page.context().clearCookies();await page.goto('/login');await page.evaluate(()=>{localStorage.clear();sessionStorage.clear();});await login(page,'operator/tejeduria/1');await expect(page.getByRole('complementary',{name:'Navegación principal'}).getByRole('link',{name:'Analítica',exact:true})).toHaveCount(0);
 const op=await token(request,'operator/tejeduria/1');for(const path of ['/api/analytics/summary?from='+new Date().getFullYear()+'-01-01&to='+new Date().getFullYear()+'-12-31','/api/quality/controls/'+state.reportControlId])expect((await request.get(path,{headers:{Authorization:'Bearer '+op}})).status()).toBe(403);
 await page.getByRole('link',{name:'Lotes',exact:true}).click();await page.getByLabel('Buscar lotes').fill(state.failedBatch.traceabilityId);await expect(page.locator('main.content')).toContainText(state.failedBatch.traceabilityId);await page.getByText(state.failedBatch.traceabilityId,{exact:true}).first().click();await expect(page.locator('main.content')).toContainText('Con falla');
});
