import {test,expect,APIRequestContext,Page} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {credentialsFile,isolatedProject} from './paths';

interface Account {key:string;email:string;password:string;role:string;companyId:string|null;}
interface Notice {id:string;read:boolean;title:string;type:string;}
// Notification I5: GET /api/notifications devuelve una página, no un arreglo (20 por omisión, máximo 100).
interface NoticePage {items:Notice[];page:number;size:number;totalElements:number;totalPages:number;}
interface Control {id:string;status:string;}
const accounts:Account[]=JSON.parse(readFileSync(credentialsFile(),'utf8')).accounts;
test.use({trace:'off',screenshot:'off',video:'off'});
test.afterEach(async({page})=>{
 if(!page.isClosed())await page.locator('input[type="password"],input[type="email"],input[name="username"]').evaluateAll(inputs=>inputs.forEach(input=>(input as HTMLInputElement).value=''));
});
const account=(key:string)=>{const a=accounts.find(a=>a.key===key);if(!a)throw new Error('Falta la cuenta de exposición solicitada.');return a;};
async function session(request:APIRequestContext,a:Account){
 const r=await request.post('/api/auth/login',{data:{email:a.email,password:a.password}});
 expect(r.status(),'La cuenta debe autenticar con su configuración privada vigente.').toBe(200);
 const data=await r.json();expect(data.user.role).toBe(a.role);
 return {Authorization:'Bearer '+data.token};
}
async function enter(page:Page,a:Account){
 await page.goto('/login');
 await page.getByRole('button',{name:a.role==='LICENSE_OWNER'?'Dueño':a.role==='OPERATOR'?'Operario':'Administrador',exact:true}).click();
 await page.getByLabel('Correo',{exact:true}).fill(a.email);
 await page.getByLabel('Contraseña',{exact:true}).evaluate((element,value)=>{
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value')!.set!.call(element,value);
  element.dispatchEvent(new Event('input',{bubbles:true}));
 },a.password);
 await page.getByRole('button',{name:'Ingresar',exact:true}).click();
 await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false');
}

test('arquitectura: las 16 cuentas autentican y su contador coincide con la bandeja autorizada',async({request})=>{
 test.setTimeout(90000);expect(accounts).toHaveLength(16);
 for(const a of accounts){
  const headers=await session(request,a);
  // La bandeja completa ya no cabe en una página: el contador se compara con el total de no leídas que informa el servidor.
  const list=await request.get('/api/notifications?unread=true&size=1',{headers});expect(list.status()).toBe(200);
  const page:NoticePage=await list.json();
  expect(Array.isArray(page.items),'el contrato es paginado, no un arreglo').toBe(true);
  const count=await request.get('/api/notifications/unread-count',{headers});expect(count.status()).toBe(200);
  expect((await count.json()).count).toBe(page.totalElements);
 }
});

test('arquitectura: Angular consulta Quality y Analytics y descarga un PDF real sin cambiar datos',async({page,request})=>{
 test.setTimeout(120000);
 const a=account('admin/hilanderia'),headers=await session(request,a);
 const response=await request.get('/api/quality/controls?size=100',{headers});expect(response.status()).toBe(200);
 const controls:Control[]=(await response.json()).items;const finished=controls.find(c=>c.status==='FINISHED');
 expect(finished,'Debe existir un control finalizado de esta empresa.').toBeDefined();
 await enter(page,a);
 await page.goto('/calidad/'+finished!.id);
 await expect(page.getByRole('button',{name:'Informe PDF',exact:true})).toBeVisible();
 const pendingDownload=page.waitForEvent('download');
 await page.getByRole('button',{name:'Informe PDF',exact:true}).click();
 const download=await pendingDownload;const stream=await download.createReadStream();
 if(!stream)throw new Error('La descarga no contiene un documento.');
 const chunks:Buffer[]=[];for await(const chunk of stream)chunks.push(Buffer.from(chunk));
 const pdf=Buffer.concat(chunks);expect(pdf.subarray(0,5).toString()).toBe('%PDF-');expect(pdf.length).toBeGreaterThan(1000);
 await page.goto('/analitica');
 await expect(page.getByRole('button',{name:'Consultar',exact:true})).toBeEnabled();
 await page.getByLabel('Desde',{exact:true}).fill('2026-01-01');
 await page.getByLabel('Hasta',{exact:true}).fill('2026-12-31');
 const pending=page.waitForResponse(r=>r.url().includes('/api/analytics/summary?from=2026-01-01'));
 await page.getByRole('button',{name:'Consultar',exact:true}).click();
 const result=await pending;expect(result.status()).toBe(200);const data=await result.json();
 const total=data.quality.reduce((n:number,r:{count:number})=>n+Number(r.count),0);
 expect(total).toBeGreaterThan(0);
 await expect(page.locator('.kpi .v').first()).toHaveText(String(total));
 await page.getByRole('tab',{name:'Pérdidas',exact:true}).click();
 await expect(page.getByText('Merma acumulada',{exact:true})).toBeVisible();
 await expect(page.locator('.notice[role="alert"]')).toHaveCount(0);
 await page.goto('/notificaciones');
 // La primera fila de la pantalla (6 por página) es items[0] de ?page=0&size=6.
 const notes=await request.get('/api/notifications?page=0&size=6',{headers});const first:NoticePage=await notes.json();
 expect(first.items.length).toBeGreaterThan(0);
 await expect(page.locator('.inbox li').first()).toBeVisible();
 await expect(page.locator('.inbox li').first()).toContainText(first.items[0].title);
});

test('arquitectura: JWT alterado, identidad falsificada y recursos B2B se rechazan en la plataforma real',async({request})=>{
 const operator=await session(request,account('operator/hilanderia/1'));
 const other=await session(request,account('admin/tejeduria'));
 const lots=await request.get('/api/batches?size=8',{headers:other});expect(lots.status()).toBe(200);
 const batch=(await lots.json()).items[0];expect(batch).toBeDefined();
 const forged={...operator,'X-Company-Id':account('admin/tejeduria').companyId!,'X-User-Role':'LICENSE_OWNER'};
 expect((await request.get('/api/batches/'+batch.id,{headers:forged})).status()).toBe(404);
 expect((await request.get('/api/license',{headers:forged})).status()).toBe(403);
 expect((await request.get('/api/analytics/summary?from=2026-01-01&to=2026-12-31',{headers:operator})).status()).toBe(403);
 expect((await request.get('/api/analytics?from=2026-01-01&to=2026-12-31',{headers:other})).status(),'la ruta antigua ya no existe (Analytics I6)').toBe(404);
 expect((await request.get('/api/quality/controls',{headers:operator})).status()).toBe(403);
 const parts=operator.Authorization.slice(7).split('.');
 parts[1]=Buffer.from(JSON.stringify({sub:'11111111-1111-1111-1111-111111111111',role:'LICENSE_OWNER',exp:9999999999})).toString('base64url');
 expect((await request.get('/api/license',{headers:{Authorization:'Bearer '+parts.join('.')}})).status()).toBe(401);
 expect((await request.get('/api/internal/batches/'+batch.id+'/phase-transitions',{headers:operator})).status()).toBe(404);
});


test('arquitectura aislada: inactivar revoca el token y reactivar restablece el acceso mediante Kafka',async({request})=>{
 test.skip(!isolatedProject(),'Solo se modifica la instalación aislada.');
 test.setTimeout(60000);
 const a=account('operator/hilanderia/2'),admin=await session(request,account('admin/hilanderia'));
 const operator=await session(request,a);
 const users=await (await request.get('/api/users',{headers:admin})).json();
 const user=users.find((u:{email:string})=>u.email===a.email);expect(user?.status).toBe('ACTIVE');
 const change=async(status:string)=>request.patch('/api/users/'+user.id+'/status',{headers:{...admin,'Idempotency-Key':crypto.randomUUID()},data:{status}});
 try{
  expect((await change('INACTIVE')).status()).toBe(200);
  await expect.poll(async()=>(await request.get('/api/notifications/unread-count',{headers:operator})).status(),{timeout:20000}).toBe(401);
  expect((await (await request.get('/api/notifications/unread-count',{headers:operator})).json()).code).toBe('USER_INACTIVE');
 }finally{
  expect((await change('ACTIVE')).status()).toBe(200);
  await expect.poll(async()=>(await request.get('/api/notifications/unread-count',{headers:operator})).status(),{timeout:20000}).toBe(200);
 }
});

test('arquitectura aislada: el límite real devuelve 429 y Retry-After sin bloquear a otro usuario',async({request})=>{
 test.skip(!isolatedProject(),'La comprobación acotada se reserva a la instalación aislada.');
 test.setTimeout(45000);
 const operator=await session(request,account('operator/marca/2'));
 let limited=false;
 for(let count=0;count<160;count++){
  const response=await request.get('/api/notifications/unread-count',{headers:operator});
  if(response.status()===429){expect(Number(response.headers()['retry-after'])).toBeGreaterThan(0);limited=true;break;}
  expect(response.status()).toBe(200);
 }
 expect(limited,'El cubo del usuario debe rechazar solicitudes al agotarse.').toBe(true);
 const other=await session(request,account('operator/marca/1'));
 expect((await request.get('/api/notifications/unread-count',{headers:other})).status()).toBe(200);
});
