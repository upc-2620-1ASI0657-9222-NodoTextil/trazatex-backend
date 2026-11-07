import { test, expect, Page, APIRequestContext } from './base';
import { readFileSync } from 'node:fs';
import { credentialsFile, isolatedProject } from './paths';
import { psql, confirmNext } from './support';

interface Account {key: string; email: string; password: string; role: string;}
const accounts: Account[] = JSON.parse(readFileSync(credentialsFile(), 'utf8')).accounts;
const account = (key: string) => { const a=accounts.find(a=>a.key===key); if(!a)throw new Error('Falta la cuenta aislada.'); return a; };
test.use({ trace: 'off', screenshot: 'off', video: 'off' });
test.beforeEach(()=>test.skip(!isolatedProject(),'Los datos oficiales no se modifican.'));
test.afterEach(async({page})=>{
 if(!page.isClosed())await page.locator('input[type="password"],input[type="email"]').evaluateAll(inputs=>inputs.forEach(input=>(input as HTMLInputElement).value=''));
});
async function enter(page: Page, user: Account) {
 await page.goto('/login');
 await page.getByRole('button',{name:user.role==='LICENSE_OWNER'?'Dueño':user.role==='COMPANY_ADMIN'?'Administrador':'Operario',exact:true}).click();
 await page.getByLabel('Correo',{exact:true}).fill(user.email);
 await page.getByLabel('Contraseña',{exact:true}).evaluate((input,value)=>{
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value')!.set!.call(input,value);
  input.dispatchEvent(new Event('input',{bubbles:true}));
 },user.password);
 await page.getByRole('button',{name:'Ingresar',exact:true}).click();
 await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false');
 await page.goto(user.role==='LICENSE_OWNER'?'/empresas':'/lotes');
}
async function open(page:Page) {
 await page.getByRole('button',{name:'Registrar lote',exact:true}).click();
 return page.getByRole('dialog',{name:'Registrar lote',exact:true});
}
async function fill(page:Page,name:string) {
 const dialog=await open(page);
 await dialog.getByLabel('Nombre del lote',{exact:true}).fill(name);
 await dialog.getByLabel('Cantidad (kg)',{exact:true}).fill('14');
 await dialog.getByLabel('Origen geográfico',{exact:true}).fill('Piura, Perú');
 await dialog.getByLabel('Características técnicas de recepción',{exact:true}).fill('Fibra limpia, humedad documentada.');
 await dialog.getByRole('button',{name:'Continuar',exact:true}).click();
 return dialog;
}

test('RNF-05: el formulario y su fotografía sobreviven a recargar y se aíslan por usuario',async({page,context})=>{
 test.setTimeout(90000);
 await enter(page,account('operator/hilanderia/1'));
 const dialog=await fill(page,'Pima Resguardo');
 await dialog.locator('input[type="file"]').setInputFiles({name:'recepcion.jpg',mimeType:'image/jpeg',buffer:readFileSync('public/login/imagen4_login.jpg')});
 await expect(dialog.locator('figure img')).toBeVisible();
 await context.setOffline(true);
 await expect(page.getByRole('alert').filter({hasText:'Sin conexión'})).toBeVisible();
 await context.setOffline(false);
 await expect(page.getByRole('alert').filter({hasText:'Sin conexión'})).toHaveCount(0);
 // El estado se confirma en IndexedDB antes de cerrar la página.
 await expect.poll(()=>page.evaluate(()=>new Promise<number>((resolve,reject)=>{
  const request=indexedDB.open('trazatex-drafts',1);
  request.onsuccess=()=>{
   const db=request.result;
   const user=JSON.parse(sessionStorage.getItem('trazatex.auth')!).user.id;
   const read=db.transaction('drafts').objectStore('drafts').get('form/'+user+'/batch/new');
   read.onsuccess=()=>{resolve(read.result?.files?.size??0);db.close();};read.onerror=()=>reject(read.error);
  };
 }))).toBe(1);
 await page.reload();
 const restored=await open(page);
 await expect(restored.locator('figure img')).toBeVisible();
 expect(await restored.locator('figure img').evaluate((image:HTMLImageElement)=>image.complete&&image.naturalWidth>0)).toBe(true);
 await restored.getByRole('button',{name:'Atrás',exact:true}).click();
 await expect(restored.getByLabel('Nombre del lote',{exact:true})).toHaveValue('Pima Resguardo');
 await expect(restored.getByLabel('Cantidad (kg)',{exact:true})).toHaveValue('14');
 await expect(restored.getByLabel('Origen geográfico',{exact:true})).toHaveValue('Piura, Perú');
 await restored.getByRole('button',{name:'Cerrar',exact:true}).click();
 await page.getByRole('button',{name:'Cerrar sesión',exact:true}).click();
 await enter(page,account('operator/hilanderia/2'));
 const other=await open(page);
 await expect(other.getByLabel('Nombre del lote',{exact:true})).not.toHaveValue('Pima Resguardo');
});

test('ADR-010: respuesta perdida tras commit, aviso antes de 3 s y reintento tras recargar sin duplicar',async({page,request})=>{
 test.setTimeout(90000);
 const user=account('operator/hilanderia/1');
 const login=await request.post('/api/auth/login',{data:{email:user.email,password:user.password}});
 expect(login.status()).toBe(200);
 const headers={Authorization:'Bearer '+(await login.json()).token};
 const before=await (await request.get('/api/batches?size=8',{headers})).json();
 await enter(page,user);
 const dialog=await fill(page,'Pima Recuperado');
 await dialog.getByRole('button',{name:'Continuar',exact:true}).click();
 const keys:string[]=[];let created:{id:string;traceabilityId:string}|undefined;
 let committed!:()=>void;const commit=new Promise<void>(resolve=>committed=resolve);
 await page.route('**/api/batches',async route=>{
  if(route.request().method()!=='POST')return route.continue();
  keys.push(route.request().headers()['idempotency-key']);
  if(keys.length>1)return route.continue();
  const response=await route.fetch();expect(response.status()).toBe(201);created=await response.json();committed();
  await new Promise(resolve=>setTimeout(resolve,3500));
  await route.abort('timedout').catch(()=>{});
 });
 const started=Date.now();
 await dialog.getByRole('button',{name:'Registrar lote',exact:true}).click();
 await commit;
 await expect(dialog.locator('.err')).toContainText('No se pudo confirmar la respuesta',{timeout:4000});
 expect(Date.now()-started,'Detección de la pérdida de respuesta en este recorrido').toBeLessThan(3000);
 await page.reload();
 const restored=await open(page);
 await expect(restored).toContainText('Pima Recuperado');
 const response=page.waitForResponse(r=>r.url().endsWith('/api/batches')&&r.request().method()==='POST');
 await restored.getByRole('button',{name:'Registrar lote',exact:true}).click();
 expect((await response).status()).toBe(201);
 expect(keys).toHaveLength(2);expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/i);expect(keys[1]).toBe(keys[0]);
 await expect(page).toHaveURL(/\/lotes\//);
 const after=await (await request.get('/api/batches?size=8',{headers})).json();
 expect(after.totalElements).toBe(before.totalElements+1);
 expect(after.items.filter((item:{id:string})=>item.id===created!.id)).toHaveLength(1);
 await expect.poll(async()=>{
  const response=await request.get('/api/traceability/batches/'+created!.id+'/lineage',{headers});
  return response.status()===200?(await response.json()).nodes.some((node:{id:string})=>node.id===created!.id):false;
 },{timeout:20000}).toBe(true);
});

async function saved(page:Page,suffix:string,field:string,value:unknown) {
 await expect.poll(()=>page.evaluate(({suffix,field,value})=>new Promise<boolean>((resolve,reject)=>{
  const request=indexedDB.open('trazatex-drafts',1);
  request.onsuccess=()=>{
   const db=request.result,store=db.transaction('drafts').objectStore('drafts');
   const id=JSON.parse(sessionStorage.getItem('trazatex.auth')!).user.id;
   const read=store.get('form/'+id+'/'+suffix);
   read.onsuccess=()=>{resolve(JSON.stringify(read.result?.[field])===JSON.stringify(value));db.close();};
   read.onerror=()=>reject(read.error);
  };
 }),{suffix,field,value})).toBe(true);
}

test('RNF-05: división conserva nombres y cantidades sin ejecutar la operación',async({page,request})=>{
 const user=account('operator/hilanderia/1');
 const auth=await request.post('/api/auth/login',{data:{email:user.email,password:user.password}});
 const headers={Authorization:'Bearer '+(await auth.json()).token};
 const batches=await (await request.get('/api/batches?size=100',{headers})).json();
 const batch=batches.items.find((b:{name:string;phase:string})=>b.name==='Pima Recuperado'&&b.phase==='AVAILABLE');
 expect(batch).toBeDefined();
 await enter(page,user);await page.goto('/lotes/'+batch.traceabilityId);
 await page.getByRole('button',{name:'Dividir',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'Dividir '+batch.name,exact:true});
 await dialog.getByRole('button',{name:'Continuar',exact:true}).click();
 await dialog.getByLabel('Nombre hijo 1',{exact:true}).fill('Pima Selección');
 await dialog.getByLabel('Cantidad hijo 1',{exact:true}).fill('5');
 await saved(page,'batch/split/'+batch.traceabilityId,'hijos',[5,7]);
 await page.reload();await page.getByRole('button',{name:'Dividir',exact:true}).click();
 await expect(dialog.getByLabel('Nombre hijo 1',{exact:true})).toHaveValue('Pima Selección');
 await expect(dialog.getByLabel('Cantidad hijo 1',{exact:true})).toHaveValue('5');
});

test('RNF-05: maquinaria y empresa recuperan sus formularios sin enviar registros ni correos',async({page})=>{
 test.setTimeout(90000);
 await enter(page,account('admin/hilanderia'));await page.goto('/maquinaria');
 await page.getByRole('button',{name:'Registrar maquinaria',exact:true}).click();
 const machine=page.getByRole('dialog',{name:'Registrar maquinaria',exact:true});
 await machine.getByLabel('Nombre',{exact:true}).fill('Cardadora de reserva');
 await saved(page,'machine/new','nombre','Cardadora de reserva');
 await page.reload();await page.getByRole('button',{name:'Registrar maquinaria',exact:true}).click();
 await expect(machine.getByLabel('Nombre',{exact:true})).toHaveValue('Cardadora de reserva');
 await machine.getByRole('button',{name:'Cerrar',exact:true}).click();
 await page.getByRole('button',{name:'Cerrar sesión',exact:true}).click();
 await enter(page,account('owner'));
 await page.getByRole('button',{name:'Registrar empresa',exact:true}).click();
 const company=page.getByRole('dialog',{name:'Registrar empresa',exact:true});
 await company.getByLabel('Razón social',{exact:true}).fill('Textiles de la Costa');
 await saved(page,'company/new','razonSocial','Textiles de la Costa');
 await page.reload();await page.getByRole('button',{name:'Registrar empresa',exact:true}).click();
 await expect(company.getByLabel('Razón social',{exact:true})).toHaveValue('Textiles de la Costa');
});

test('RNF-05: Quality conserva la prueba sin registrarla automáticamente',async({page,request})=>{
 const user=account('admin/confeccion');
 const auth=await request.post('/api/auth/login',{data:{email:user.email,password:user.password}});
 const headers={Authorization:'Bearer '+(await auth.json()).token};
 const controls=(await (await request.get('/api/quality/controls?size=100',{headers})).json()).items;
 const control=controls.find((c:{status:string})=>c.status==='IN_PROGRESS');expect(control).toBeDefined();
 await enter(page,user);await page.goto('/calidad/'+control.id);
 await page.getByRole('button',{name:/^Pruebas \d+ registrada/}).click();
 await page.getByLabel('Criterio',{exact:true}).fill('Resistencia longitudinal');
 await page.getByLabel('Valor esperado',{exact:true}).fill('5');
 await saved(page,'quality/test/'+control.id,'criterio','Resistencia longitudinal');
 await page.reload();await page.getByRole('button',{name:/^Pruebas \d+ registrada/}).click();
 await expect(page.getByLabel('Criterio',{exact:true})).toHaveValue('Resistencia longitudinal');
 await expect(page.getByLabel('Valor esperado',{exact:true})).toHaveValue('5');
 const detail=await (await request.get('/api/quality/controls/'+control.id,{headers})).json();
 expect(detail.tests.some((t:{criterion:string})=>t.criterion==='Resistencia longitudinal')).toBe(false);
});


test('ADR-010: cerrar tras confirmar el lote conserva su clave hasta completar las fotografías',async({page,request})=>{
 test.setTimeout(90000);
 const user=account('operator/hilanderia/1');
 const auth=await request.post('/api/auth/login',{data:{email:user.email,password:user.password}});
 const headers={Authorization:'Bearer '+(await auth.json()).token};
 const before=await (await request.get('/api/batches?size=8',{headers})).json();
 await enter(page,user);
 const dialog=await fill(page,'Pima Archivo');
 await dialog.locator('input[type="file"]').setInputFiles({name:'recepcion.jpg',mimeType:'image/jpeg',buffer:readFileSync('public/login/imagen4_login.jpg')});
 await dialog.getByRole('button',{name:'Continuar',exact:true}).click();
 const keys:string[]=[];
 await page.route('**/api/batches',async route=>{
  if(route.request().method()==='POST')keys.push(route.request().headers()['idempotency-key']);
  return route.continue();
 });
 let started!:()=>void,release!:()=>void,photos=0;
 const photoStarted=new Promise<void>(resolve=>started=resolve),block=new Promise<void>(resolve=>release=resolve);
 await page.route('**/api/batches/*/evidence',async route=>{
  if(route.request().method()!=='POST')return route.continue();
  if(++photos===1){started();await block;}
  await route.abort('internetdisconnected').catch(()=>{});
 });
 try{
  const response=page.waitForResponse(r=>r.url().endsWith('/api/batches')&&r.request().method()==='POST');
  await dialog.getByRole('button',{name:'Registrar lote',exact:true}).click();
  expect((await response).status()).toBe(201);await photoStarted;
  await page.reload();release();
  const restored=await open(page);
  await expect(restored).toContainText('Pima Archivo');
  await restored.getByRole('button',{name:'Registrar lote',exact:true}).click();
  await expect(restored.locator('.err')).toContainText('El lote ya está registrado');
  expect(keys).toHaveLength(2);expect(keys[1]).toBe(keys[0]);
  const after=await (await request.get('/api/batches?size=8',{headers})).json();
  expect(after.totalElements).toBe(before.totalElements+1);
  // Un cambio de datos no puede convertir un reintento incierto en un segundo registro.
  // Los avisos de error ya no se retiran solos (FE-M9, fila 25): hay que cerrarlos para que no tapen el botón.
  for(const close of await page.locator('.toast--error .toast-close').all())await close.click();
  await restored.getByRole('button',{name:'Atrás',exact:true}).click();
  await restored.getByRole('button',{name:'Atrás',exact:true}).click();
  await restored.getByLabel('Nombre del lote',{exact:true}).fill('Pima Modificado');
  await restored.getByRole('button',{name:'Continuar',exact:true}).click();
  await restored.getByRole('button',{name:'Continuar',exact:true}).click();
  await restored.getByRole('button',{name:'Registrar lote',exact:true}).click();
  await expect(restored.locator('.err')).toContainText('Hay una solicitud anterior con otros datos');
  expect(keys).toHaveLength(2);
  await page.reload();const original=await open(page);
  await expect(original).toContainText('Pima Archivo');
 }finally{release();}
});


// ── Ronda 5 · specs nuevos de FE-I4 y FE-I2 (filas 5, 7, 8 y 9 de PENDIENTES_E2E.md). Sin ejecutar hasta la ronda 5b. ──
const sleep=(ms:number)=>new Promise<void>(resolve=>setTimeout(resolve,ms));
const idbOperations=(page:Page)=>page.evaluate(()=>new Promise<{key:string;createdAt:number}[]>((resolve,reject)=>{
 const open=indexedDB.open('trazatex-drafts',1);
 open.onsuccess=()=>{
  const db=open.result,read=db.transaction('drafts').objectStore('drafts').openCursor(),rows:{key:string;createdAt:number}[]=[];
  read.onsuccess=()=>{const row=read.result;if(!row){db.close();resolve(rows);return;}
   if(String(row.key).startsWith('operation/'))rows.push({key:String(row.key),createdAt:row.value?.createdAt});row.continue();};
  read.onerror=()=>reject(read.error);
 };
}));
const ageOperations=(page:Page,hours:number)=>page.evaluate((hours)=>new Promise<number>((resolve,reject)=>{
 const open=indexedDB.open('trazatex-drafts',1);
 open.onsuccess=()=>{
  const db=open.result,tx=db.transaction('drafts','readwrite'),store=tx.objectStore('drafts'),cursor=store.openCursor();let changed=0;
  cursor.onsuccess=()=>{const row=cursor.result;if(!row)return;
   if(String(row.key).startsWith('operation/')){store.put({...row.value,createdAt:Date.now()-hours*3600*1000},row.key);changed++;}row.continue();};
  tx.oncomplete=()=>{db.close();resolve(changed);};tx.onerror=()=>reject(tx.error);
 };
}),hours);
async function api(request:APIRequestContext,user:Account){
 const login=await request.post('/api/auth/login',{data:{email:user.email,password:user.password}});expect(login.status()).toBe(200);
 return {Authorization:'Bearer '+(await login.json()).token};
}
async function sendTransfer(request:APIRequestContext,name:string){
 const operator=await api(request,account('operator/hilanderia/1'));
 const created=await request.post('/api/batches',{headers:operator,data:{name,materialType:'Fibra de algodón Pima',quantityKg:6,
  composition:[{name:'Algodón Pima',percentage:100}],origin:'Piura, Perú',receptionCharacteristics:'Lote para prueba de operaciones caducadas.'}});
 expect(created.status()).toBe(201);const batch=await created.json();
 const target=accounts.find(a=>a.key==='admin/tejeduria') as Account&{companyId:string};
 const sent=await request.post('/api/transfers',{headers:operator,data:{targetCompanyId:target.companyId,batchIds:[batch.id]}});
 expect(sent.status()).toBe(201);
 return (await sent.json()) as {id:string};
}

test('FE-I4: una operación guardada de más de 23 h se descarta con aviso, sin enviar nada, y el siguiente intento usa otra clave',async({page,request})=>{
 test.setTimeout(120000);
 const transfer=await sendTransfer(request,'Pima Caducada');
 const admin=account('admin/tejeduria');
 await enter(page,admin);await page.goto('/transferencias');
 await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
 await page.getByRole('tab',{name:/^Todas/}).click();
 await page.getByLabel('Buscar transferencias',{exact:true}).fill(transfer.id);
 await expect(page.locator('button.row-card')).toHaveCount(1);
 await page.locator('button.row-card').click();
 const drawer=page.getByRole('dialog');
 const keys:string[]=[];let blocked=true;let sentWhileStale=0;
 await page.route('**/api/transfers/*/accept',async route=>{
  if(route.request().method()!=='POST')return route.continue();
  keys.push(route.request().headers()['idempotency-key']);
  if(blocked)return route.abort('failed');
  return route.continue();
 });
 // 1) primer intento: la respuesta no llega; queda un registro operation/* con su Idempotency-Key
 await confirmNext(page);await drawer.getByRole('button',{name:'Aceptar',exact:true}).click();
 await expect(page.getByText(/No se pudo confirmar la respuesta del servidor/).first()).toBeVisible({timeout:15000});
 expect(keys).toHaveLength(1);
 expect((await idbOperations(page)).length).toBeGreaterThanOrEqual(1);
 // 2) el registro envejece más de 23 h: el siguiente intento muestra el aviso y NO envía ninguna petición
 expect(await ageOperations(page,24)).toBeGreaterThanOrEqual(1);
 for(const close of await page.getByRole('button',{name:'Cerrar aviso de error'}).all())await close.click();
 await confirmNext(page);await drawer.getByRole('button',{name:'Aceptar',exact:true}).click();
 await expect(page.getByText(/La operación anterior se envió hace más de 23 horas y pudo haberse registrado/)).toBeVisible({timeout:15000});
 sentWhileStale=keys.length;expect(sentWhileStale,'no se envió ninguna petición con el registro caducado').toBe(1);
 expect((await idbOperations(page)).filter(r=>Date.now()-r.createdAt>23*3600*1000),'el registro viejo desapareció de IndexedDB').toHaveLength(0);
 // 3) segundo intento: parte de cero con una clave distinta y deja un registro nuevo
 for(const close of await page.getByRole('button',{name:'Cerrar aviso de error'}).all())await close.click();
 blocked=false;
 await confirmNext(page);await drawer.getByRole('button',{name:'Aceptar',exact:true}).click();
 await expect.poll(()=>keys.length,{timeout:15000}).toBe(2);
 expect(keys[1]).toMatch(/^[0-9a-f-]{36}$/i);expect(keys[1]).not.toBe(keys[0]);
 await expect(drawer).toContainText('Aceptada',{timeout:30000});
});

test('FE-I2 (a): una subida de evidencia lenta (6 s) no muestra un falso error y la evidencia queda guardada',async({page,request})=>{
 test.setTimeout(150000);
 const user=account('operator/hilanderia/1');const headers=await api(request,user);
 await enter(page,user);
 const dialog=await fill(page,'Pima Subida Lenta');
 await dialog.locator('input[type="file"]').setInputFiles({name:'recepcion.jpg',mimeType:'image/jpeg',buffer:readFileSync('public/login/imagen4_login.jpg')});
 await dialog.getByRole('button',{name:'Continuar',exact:true}).click();
 let uploads=0;
 await page.route('**/api/batches/*/evidence',async route=>{
  if(route.request().method()!=='POST')return route.continue();
  uploads++;await sleep(6000);await route.continue();
 });
 const created=page.waitForResponse(r=>r.url().endsWith('/api/batches')&&r.request().method()==='POST');
 await dialog.getByRole('button',{name:'Registrar lote',exact:true}).click();
 const batch=await (await created).json();
 await expect(page).toHaveURL(/\/lotes\//,{timeout:60000});
 await expect.poll(async()=>(await (await request.get('/api/batches/'+batch.id+'/evidence',{headers})).json()).items.length,{timeout:90000}).toBe(1);
 expect(uploads).toBe(1);
 await expect(page.getByText(/No se pudo confirmar/)).toHaveCount(0);
});

test('FE-I2 (b): el informe PDF que tarda 12 s se descarga',async({page,request})=>{
 test.setTimeout(120000);
 const admin=account('admin/hilanderia');const headers=await api(request,admin);
 const controls=(await (await request.get('/api/quality/controls?size=100',{headers})).json()).items;
 const finished=controls.find((c:{status:string})=>c.status==='FINISHED');expect(finished).toBeDefined();
 await enter(page,admin);await page.goto('/calidad/'+finished.id);
 await page.route('**/api/quality/controls/*/report',async route=>{await sleep(12000);await route.continue();});
 const download=page.waitForEvent('download',{timeout:45000});
 await page.getByRole('button',{name:'Informe PDF',exact:true}).click();
 const file=await download;const chunks:Buffer[]=[];for await(const c of (await file.createReadStream())!)chunks.push(Buffer.from(c));
 expect(Buffer.concat(chunks).subarray(0,5).toString()).toBe('%PDF-');
 await expect(page.getByText(/No se pudo confirmar/)).toHaveCount(0);
});

test('FE-I2 (c): finalizar un control con respuesta de 6 s termina sin falso error',async({page,request})=>{
 test.setTimeout(150000);
 const operator=await api(request,account('operator/hilanderia/1'));const adminAccount=account('admin/hilanderia');const admin=await api(request,adminAccount);
 const created=await request.post('/api/batches',{headers:operator,data:{name:'Pima Finalizar Lento',materialType:'Fibra de algodón Pima',quantityKg:9,
  composition:[{name:'Algodón Pima',percentage:100}],origin:'Piura, Perú',receptionCharacteristics:'Lote para prueba de plazos.'}});
 expect(created.status()).toBe(201);const batch=await created.json();
 await expect.poll(async()=>(await (await request.get('/api/quality/batches?size=100',{headers:admin})).json()).items.some((b:{batchId:string})=>b.batchId===batch.id),{timeout:30000}).toBe(true);
 const control=await (await request.post('/api/quality/batches/'+batch.id+'/controls',{headers:admin,data:{}})).json();
 await expect.poll(async()=>(await (await request.get('/api/quality/controls/'+control.id,{headers:admin})).json()).status,{timeout:30000}).toBe('IN_PROGRESS');
 await enter(page,adminAccount);await page.goto('/calidad/'+control.id);
 await page.getByRole('button',{name:/^Pruebas/}).click();
 await page.getByLabel('Criterio',{exact:true}).fill('Humedad');await page.getByLabel('Valor esperado',{exact:true}).fill('Entre 7 y 9');
 await page.getByLabel('Valor obtenido',{exact:true}).fill('8');await page.getByLabel('Unidad',{exact:true}).fill('%');
 await page.getByRole('button',{name:'Agregar prueba',exact:true}).click();
 await expect(page.locator('.mini-cards')).toContainText('Humedad');
 await page.route('**/api/quality/controls/*/finish',async route=>{if(route.request().method()!=='POST')return route.continue();await sleep(6000);await route.continue();});
 await page.getByRole('button',{name:'Finalizar control',exact:true}).click();
 await page.getByRole('button',{name:'Finalizar como Conforme',exact:true}).click();
 await expect.poll(async()=>(await (await request.get('/api/quality/controls/'+control.id,{headers:admin})).json()).status,{timeout:60000}).toBe('FINISHED');
 await expect(page.getByText(/No se pudo confirmar/)).toHaveCount(0);
});

// RUC sintético válido (módulo 11) con su validación ya en la caché de organization-access: el alta no llama a Chequea.pe (0 llamadas).
function syntheticRuc():string{
 const digits='20'+String(Math.floor(Math.random()*1e8)).padStart(8,'0');
 const weights=[5,4,3,2,7,6,5,4,3,2];
 const sum=[...digits].reduce((n,d,i)=>n+Number(d)*weights[i],0);
 return digits+String((11-sum%11)%10);
}
test('FE-I2 (d): el alta de empresa con respuesta de 5 s se crea sin falso error',async({page,request})=>{
 test.setTimeout(120000);
 const ruc=syntheticRuc();
 psql('orgaccess_db',"INSERT INTO validated_ruc_cache(ruc,business_name,taxpayer_status,domicile_condition,validated_at) VALUES ('"+ruc+"','Empresa de prueba de plazos','ACTIVO','HABIDO',now())");
 const owner=account('owner');await enter(page,owner);
 await page.getByRole('button',{name:'Registrar empresa',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'Registrar empresa',exact:true});
 await dialog.getByLabel('Razón social',{exact:true}).fill('Textiles Plazo Largo '+ruc.slice(2,6));
 await dialog.getByLabel('RUC',{exact:true}).fill(ruc);
 await dialog.getByLabel('Correo del primer administrador',{exact:true}).fill('plazo-'+Date.now()+'@example.test');
 await dialog.getByLabel('Hilandería').check();
 await page.route('**/api/companies',async route=>{if(route.request().method()!=='POST')return route.continue();await sleep(5000);await route.continue();});
 const response=page.waitForResponse(r=>r.url().endsWith('/api/companies')&&r.request().method()==='POST');
 await dialog.getByRole('button',{name:'Registrar empresa',exact:true}).click();
 expect((await response).status()).toBe(201);
 await expect(page.getByText(/No se pudo confirmar/)).toHaveCount(0);
 const headers=await api(request,owner);
 const companies=await (await request.get('/api/companies',{headers})).json();
 expect(companies.some((c:{ruc:string})=>c.ruc===ruc)).toBe(true);
});

test('FE-I2 (fila 8): un comando de Quality que nunca responde avisa a los ~8 s (no a los 2,5 s), conserva los datos y reintenta con la MISMA Idempotency-Key',async({page,request})=>{
 test.setTimeout(150000);
 const operator=await api(request,account('operator/hilanderia/1'));const adminAccount=account('admin/hilanderia');const admin=await api(request,adminAccount);
 const created=await request.post('/api/batches',{headers:operator,data:{name:'Pima Sin Respuesta',materialType:'Fibra de algodón Pima',quantityKg:7,
  composition:[{name:'Algodón Pima',percentage:100}],origin:'Piura, Perú',receptionCharacteristics:'Lote para prueba de plazo agotado.'}});
 expect(created.status()).toBe(201);const batch=await created.json();
 await expect.poll(async()=>(await (await request.get('/api/quality/batches?size=100',{headers:admin})).json()).items.some((b:{batchId:string})=>b.batchId===batch.id),{timeout:30000}).toBe(true);
 const control=await (await request.post('/api/quality/batches/'+batch.id+'/controls',{headers:admin,data:{}})).json();
 await expect.poll(async()=>(await (await request.get('/api/quality/controls/'+control.id,{headers:admin})).json()).status,{timeout:30000}).toBe('IN_PROGRESS');
 await enter(page,adminAccount);await page.goto('/calidad/'+control.id);
 await page.getByRole('button',{name:/^Pruebas/}).click();
 await page.getByLabel('Criterio',{exact:true}).fill('Humedad');await page.getByLabel('Valor esperado',{exact:true}).fill('Entre 7 y 9');
 await page.getByLabel('Valor obtenido',{exact:true}).fill('8');await page.getByLabel('Unidad',{exact:true}).fill('%');
 const keys:string[]=[];
 await page.route('**/api/quality/controls/*/tests',async route=>{
  if(route.request().method()!=='POST')return route.continue();
  keys.push(route.request().headers()['idempotency-key']);
  // nunca responde: no se reenvía al servidor ni se cumple
 });
 const started=Date.now();
 await page.getByRole('button',{name:'Agregar prueba',exact:true}).click();
 await expect(page.getByText(/No se pudo confirmar la respuesta del servidor/).first()).toBeVisible({timeout:20000});
 const waited=Date.now()-started;
 expect(waited,'el plazo de un comando de Quality es de 8 s, no el de 2,5 s').toBeGreaterThan(6000);
 expect(waited).toBeLessThan(15000);
 await expect(page.getByLabel('Criterio',{exact:true})).toHaveValue('Humedad');
 await page.unroute('**/api/quality/controls/*/tests');
 await page.route('**/api/quality/controls/*/tests',async route=>{if(route.request().method()==='POST')keys.push(route.request().headers()['idempotency-key']);return route.continue();});
 await page.getByRole('button',{name:'Agregar prueba',exact:true}).click();
 await expect.poll(()=>keys.length,{timeout:15000}).toBe(2);
 expect(keys[1]).toBe(keys[0]);
});
