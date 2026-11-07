import { test, expect, Page, APIRequestContext } from './base';
import { readFileSync } from 'node:fs';
import { credentialsFile, localFile } from './paths';
test.use({trace:'off'});
const accounts=JSON.parse(readFileSync(credentialsFile(),'utf8')).accounts;
const state=()=>JSON.parse(readFileSync(localFile('state.json'),'utf8'));
// FE-C1: el `name` del lote solo lo ve la empresa dueña. El grafo (API) lo trae solo en los nodos propios; en los ajenos la pantalla muestra «—».
async function expectedName(request:APIRequestContext,role:string,traceabilityId:string):Promise<string>{
 const user=accounts.find((a:{role:string})=>a.role===role);
 const token=(await (await request.post('/api/auth/login',{data:{email:user.email,password:user.password}})).json()).token as string;
 const headers={Authorization:'Bearer '+token};
 // el Gateway limita las lecturas por IP (429 + Retry-After): se espera y se repite en vez de fallar por el ritmo de la prueba
 const read=async(path:string)=>{for(let attempt=0;attempt<6;attempt++){const response=await request.get(path,{headers});if(response.status()!==429)return response.json();await new Promise(r=>setTimeout(r,((Number(response.headers()['retry-after'])||2)+1)*1000));}throw new Error('429 persistente en '+path);};
 const found=await read('/api/traceability/lineages?traceabilityId='+encodeURIComponent(traceabilityId));
 const graph=await read('/api/traceability/batches/'+found.items[0].batchId+'/lineage');
 const node=graph.nodes.find((n:{traceabilityId:string})=>n.traceabilityId===traceabilityId);
 return node?.name??'—';
}
async function enter(page:Page,role:string){
 const user=accounts.find((a:{role:string})=>a.role===role);
 await page.goto('/login'); await page.getByLabel('Correo',{exact:true}).fill(user.email); await page.getByLabel('Contraseña',{exact:true}).fill(user.password);
 await page.getByRole('button',{name:'Ingresar',exact:true}).click(); await expect(page.locator('app-shell')).toBeVisible();
 await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
}
for(const role of ['LICENSE_OWNER','COMPANY_ADMIN','OPERATOR']) test('encabezado, iconos y barra fija sin scroll: '+role,async({page})=>{
 test.setTimeout(120000); await enter(page,role);
 const routes=role==='LICENSE_OWNER'?['/usuarios','/perfil','/empresas']:role==='COMPANY_ADMIN'?['/usuarios','/perfil','/lotes']:['/perfil','/lotes','/procesos'];
 for(const route of routes){
  await page.goto(route); await expect(page.locator('.proto')).toBeVisible();
  const top=await page.locator('.topbar').boundingBox();const badge=await page.locator('.proto').boundingBox();
  expect(Math.abs(top!.x+top!.width-badge!.x-badge!.width)).toBeLessThan(45);
  if(route==='/perfil'){ expect(await page.locator('.top-current svg path').count(), 'icono de hilo: un solo trazo').toBe(1); expect(await page.locator('.top-current svg circle').count()).toBe(0); }
 }
 const rail=page.locator('.rail');await expect(rail.locator('.brand img')).toHaveAttribute('src','brand/mark-blanco-480.webp');
 for(const size of [{width:1280,height:720},{width:1280,height:360},{width:390,height:480}]){
  await page.setViewportSize(size);
  if(size.width<900) await page.getByRole('button',{name:'Abrir menú',exact:true}).click();
  expect(await rail.evaluate(e=>getComputedStyle(e).position)).toBe('fixed');
  const geometry=await rail.evaluate(e=>({height:e.clientHeight,scroll:e.scrollHeight,width:e.clientWidth,scrollWidth:e.scrollWidth}));
  expect(geometry.height).toBe(size.height);expect(geometry.scroll).toBeLessThanOrEqual(geometry.height+1);expect(geometry.scrollWidth).toBeLessThanOrEqual(geometry.width+1);
  const icons=rail.locator('.nav-item app-icon');for(let i=0;i<await icons.count();i++) if(await icons.nth(i).isVisible()) await expect(icons.nth(i)).toBeInViewport({ratio:1});
  await page.evaluate(()=>window.scrollTo(0,document.body.scrollHeight));expect((await rail.boundingBox())!.y).toBe(0);
 }
});
test('login conserva formulario y reutiliza iconos de navegación',async({page})=>{
 await page.goto('/login');await expect(page.locator('.roles .role img')).toHaveCount(0);await expect(page.locator('.roles .role app-icon')).toHaveCount(3);
 for(const role of ['Dueño','Administrador','Operario']){const b=page.getByRole('button',{name:role,exact:true});await b.click();await expect(b).toHaveAttribute('aria-pressed','true');}
});
test('detalle de lote y proceso: un encabezado, acciones conservadas y navegación',async({page})=>{
 test.setTimeout(120000);await enter(page,'OPERATOR');const s=state();
 const lot=s.operations['batch/depth/camisas'];const process=s.operations['transformation/depth/camisas/0'];
 for(const [route,back] of [['/lotes/'+lot.traceabilityId,'Lotes'],['/procesos/'+process.id,'Procesos']]){
  await page.goto(route);await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
  await expect(page.locator('main.content .page-head')).toHaveCount(0);
  const button=page.locator('.topbar').getByRole('link',{name:'Ver en genealogía',exact:true});await expect(button).toBeVisible();
  expect(await button.evaluate(e=>getComputedStyle(e).borderTopColor)).not.toBe('rgba(0, 0, 0, 0)');
  await expect(page.locator('.topbar').getByRole('link',{name:back,exact:true})).toBeVisible();
  await button.click();await expect(page).toHaveURL(/trazabilidad/);await expect(page.locator('[data-node]').first()).toBeVisible({timeout:60000});
 }
});
test('genealogías profundas y parciales, sin ocultar lotes históricos',async({page,request})=>{
 test.setTimeout(180000);await enter(page,'OPERATOR');const s=state();const role='OPERATOR';
 for(const [key,stage,minimum] of [['camisas',4,11],['hilatura',0,3],['indigo',2,9],['recepcion',-1,1]] as const){
  const batch=stage<0?s.operations['batch/depth/'+key]:s.operations['complete/depth/'+key+'/'+stage].outputs[0];
  await page.goto('/trazabilidad');await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});
  await page.getByLabel('Buscar lote',{exact:true}).fill(batch.traceabilityId);await page.getByRole('button',{name:'Trazar',exact:true}).click();
  await expect(page.locator('.trace-detail .detail-id')).toHaveText(await expectedName(request,role,batch.traceabilityId),{timeout:60000});
  await expect(page.locator('[data-node="'+batch.traceabilityId+'"] .mat.id')).toHaveText(batch.traceabilityId);
  expect(await page.locator('[data-node]').count()).toBeGreaterThanOrEqual(minimum);
  if(key==='camisas'){expect(await page.locator('.stage .company').count()).toBe(5);expect(await page.locator('path.edge').count()).toBeGreaterThanOrEqual(10);}
 }
});

test('Usuarios y Perfil: icono correcto, contraste y rol a la derecha',async({page})=>{
 test.setTimeout(90000);await enter(page,'COMPANY_ADMIN');
 for(const route of ['/usuarios','/perfil']){
  await page.goto(route);const link=page.locator('.rail a[href="'+route+'"]');await expect(link).toHaveClass(/active/);
  expect(await link.locator('svg path').count(), 'icono de hilo: un solo trazo').toBe(1);expect(await link.locator('svg circle').count()).toBe(0);
  const colors=await link.evaluate(e=>({color:getComputedStyle(e.querySelector('app-icon')!).color,background:getComputedStyle(e).backgroundColor}));
  const luminance=(color:string)=>{const c=color.match(/[\d.]+/g)!.slice(0,3).map(Number).map(n=>{n/=255;return n<=.04045?n/12.92:((n+.055)/1.055)**2.4;});return c[0]*.2126+c[1]*.7152+c[2]*.0722;};
  const a=luminance(colors.color),b=luminance(colors.background);expect((Math.max(a,b)+.05)/(Math.min(a,b)+.05)).toBeGreaterThanOrEqual(3);
  for(const width of [1280,390]){
   await page.setViewportSize({width,height:720});const bar=(await page.locator('.topbar').boundingBox())!,role=(await page.locator('.proto').boundingBox())!;
   expect(Math.abs(bar.x+bar.width-role.x-role.width)).toBeLessThan(45);
  }
 }
});
