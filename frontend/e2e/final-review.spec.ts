import {test,expect,Page} from './base';
import {existsSync,readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {credentialsFile,landingUrl,localFile,resultsDir} from './paths';
const accounts=JSON.parse(readFileSync(credentialsFile(),'utf8')).accounts;
async function login(page:Page,key:string){const a=accounts.find((x:{key:string})=>x.key===key);await page.goto('/login');await page.getByRole('button',{name:key==='owner'?'Dueño':key.startsWith('operator/')?'Operario':'Administrador',exact:true}).click();await page.getByLabel('Correo',{exact:true}).fill(a.email);await page.getByLabel('Contraseña',{exact:true}).fill(a.password);await page.getByRole('button',{name:'Ingresar',exact:true}).click();await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false',{timeout:60000});}
async function contrast(page:Page,selector:string){return page.locator(selector).evaluateAll(elements=>{
 const rgba=(v:string)=>{const n=v.match(/[\d.]+/g)?.map(Number)??[0,0,0];return [n[0],n[1],n[2],n[3]??1];};
 const blend=(a:number[],b:number[])=>[0,1,2].map(i=>a[i]*a[3]+b[i]*(1-a[3]));
 const lum=(rgb:number[])=>rgb.map(x=>{x/=255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4;}).reduce((sum,x,i)=>sum+x*[.2126,.7152,.0722][i],0);
 return elements.filter(e=>(e as HTMLElement).offsetWidth>0).map(e=>{const chain:Element[]=[];for(let n:Element|null=e;n;n=n.parentElement)chain.unshift(n);let bg=[255,255,255];for(const n of chain)bg=blend(rgba(getComputedStyle(n).backgroundColor),bg);const style=getComputedStyle(e);const fg=blend(rgba(style.color),bg);const a=lum(fg),b=lum(bg);return {text:e.textContent?.trim().slice(0,70),ratio:(Math.max(a,b)+.05)/(Math.min(a,b)+.05)};});
});}
test('login conserva su apariencia y utiliza los tres iconos de hilo',async({page})=>{
  // Clase C: compara contra una captura de referencia de la instalación oficial (revision-login-before.png) que no se genera en un stack aislado.
  test.skip(!existsSync(localFile('revision-login-before.png')),'Falta la captura de referencia revision-login-before.png de la instalación oficial.');
  await page.setViewportSize({width:1440,height:900});
  await page.goto('/login');
  await page.evaluate(()=>document.fonts.ready);
  const icons=page.locator('.roles app-icon svg');
  await expect(icons).toHaveCount(3);
  for(const icon of await icons.all()){
    await expect(icon).toHaveAttribute('width','34');
    await expect(icon).toHaveAttribute('stroke-linecap','round');
    await expect(icon.locator('path')).toHaveCount(1);
    await expect(icon.locator('rect,circle,[stroke-dasharray]')).toHaveCount(0);
  }
  const boxes=await icons.evaluateAll(elements=>elements.map(e=>{
    const r=e.getBoundingClientRect();return {x:r.x-3,y:r.y-3,width:r.width+6,height:r.height+6};
  }));
  const actual=(await page.screenshot()).toString('base64');
  const previous=readFileSync(localFile('revision-login-before.png')).toString('base64');
  const comparison=await page.evaluate(async({actual,previous,boxes})=>{
    const load=(data:string)=>new Promise<HTMLImageElement>((resolve,reject)=>{
      const image=new Image();image.onload=()=>resolve(image);image.onerror=reject;image.src='data:image/png;base64,'+data;
    });
    const [before,after]=await Promise.all([load(previous),load(actual)]);
    if(before.width!==after.width||before.height!==after.height)throw new Error('Cambió el tamaño del login.');
    const pixels=(image:HTMLImageElement)=>{
      const canvas=document.createElement('canvas');canvas.width=image.width;canvas.height=image.height;
      const context=canvas.getContext('2d')!;context.drawImage(image,0,0);return context.getImageData(0,0,image.width,image.height).data;
    };
    const a=pixels(before),b=pixels(after);let outside=0,inside=0;
    for(let y=0;y<after.height;y++)for(let x=0;x<after.width;x++){
      const index=(y*after.width+x)*4;
      if(a[index]===b[index]&&a[index+1]===b[index+1]&&a[index+2]===b[index+2]&&a[index+3]===b[index+3])continue;
      if(boxes.some(r=>x>=r.x&&x<r.x+r.width&&y>=r.y&&y<r.y+r.height))inside++;else outside++;
    }
    return {outside,inside};
  },{actual,previous,boxes});
  expect(comparison.outside,'La pantalla debe permanecer idéntica fuera de los tres iconos.').toBe(0);
  expect(comparison.inside,'Los iconos deben cambiar a los trazos actuales.').toBeGreaterThan(0);
});
test('Quality uniforme y solo lectura junto al rol',async({page})=>{test.setTimeout(90000);await login(page,'admin/tejeduria');await page.goto('/lotes');const referenceBg=await page.locator('.subnav').evaluate(e=>getComputedStyle(e).backgroundColor);await page.goto('/calidad');await expect(page.locator('.page--calidad .subnav')).toBeVisible();const bg=await page.locator('.subnav').evaluate(e=>getComputedStyle(e).backgroundColor);expect(bg).toBe(referenceBg);await expect(page.locator('.row-card').first()).toBeVisible();for(const result of await contrast(page,'.subnav button,.row-card .rc-title,.row-card .rc-id'))expect(result.ratio,JSON.stringify(result)).toBeGreaterThanOrEqual(4.5);await page.goto('/procesos');await expect(page.locator('.proto [aria-label="Solo lectura"]')).toBeVisible();const eye=await page.locator('.header-readonly').boundingBox();const role=await page.locator('.proto .label').boundingBox();expect(eye!.x+eye!.width).toBeLessThanOrEqual(role!.x);await page.goto('/usuarios');await expect(page.locator('.row-card .rc-id app-icon[name="eye"]')).toHaveCount(0);});
test('Licencia legible en encabezados, porcentajes y contadores',async({page})=>{await login(page,'owner');await page.goto('/licencia');await expect(page.locator('.tag .label')).toHaveText('Licencia');await expect(page.locator('.slot.used')).toHaveCount(5);const results=await contrast(page,'.tag>.label,.sheet-head .capacity-percent,.sheet-head .sheet-title,.kpi .label,.kpi .v');expect(results.length).toBeGreaterThan(5);for(const result of results)expect(result.ratio,JSON.stringify(result)).toBeGreaterThanOrEqual(4.5);});

test('genealogía con superficies legibles y landing sin alterar el hero',async({page})=>{
 test.setTimeout(90000);await login(page,'admin/tejeduria');const state=JSON.parse(readFileSync(localFile('state.json'),'utf8')).operations;const batch=state['cadena-mixta/v1/weaving/complete'].outputs[0];await page.goto('/trazabilidad');await page.getByLabel('Buscar lote',{exact:true}).fill(batch.traceabilityId);await page.getByRole('button',{name:'Trazar',exact:true}).click();await expect(page.locator('.node.lot').first()).toBeVisible();await expect(page.locator('.node.is-fail').first()).toBeVisible();const nodeContrast=await contrast(page,'.node.lot .row1 b,.node.lot .mat,.node.lot .meta');expect(nodeContrast.filter(r=>r.ratio<4.5)).toEqual([]);await page.screenshot({path:resolve(resultsDir(),'revision-genealogy.png')});
 await page.goto(landingUrl());await expect(page.locator('body')).not.toHaveClass(/is-loading/);await expect(page.locator('.hero__media img')).toHaveCount(1);await expect(page.locator('.hero__media img')).toHaveAttribute('src','/img/hero/conos-textiles.webp');expect(await page.locator('.hero__media img').evaluate(e=>getComputedStyle(e).filter)).toBe('brightness(0.82)');expect(await page.locator('.hero__actions .btn').first().evaluate(e=>getComputedStyle(e).opacity)).toBe('0.81');const nav=await page.locator('.nav').evaluate(e=>({background:getComputedStyle(e).backgroundColor,expected:getComputedStyle(e).getPropertyValue('--green-950').trim()}));const rgb=nav.background.match(/\d+/g)!.map(Number);expect(rgb.reduce((a,b)=>a+b,0)).toBeLessThan(80);for(const result of await contrast(page,'.nav__links a'))expect(result.ratio,JSON.stringify(result)).toBeGreaterThanOrEqual(4.5);
});


test('exposición: avisos administrativos reales con estados leído y no leído', async ({page}) => {
  // Clase B: los cinco avisos (cuatro sin leer) son los de la instalación oficial; en un stack aislado dependen del orden en que corrieron los demás specs.
  test.skip(process.env['TRAZATEX_EXPOSURE_DATA']!=='true','Cuentas exactas de avisos de la instalación oficial (TRAZATEX_EXPOSURE_DATA=true).');
  await login(page,'owner');
  await page.goto('/notificaciones');
  await expect(page.locator('.inbox li')).toHaveCount(5);
  await expect(page.locator('.inbox li.unread')).toHaveCount(4);
  await page.getByRole('combobox',{name:'Tipo',exact:true}).selectOption('Solicitud de administrador');
  await expect(page.locator('.inbox li')).toHaveCount(5);
  const first = page.locator('.inbox .body').first();
  await first.click();
  await expect(page).toHaveURL(/\/usuarios\?solicitud=/);
  await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false');
});

test('exposición: filtro completo aunque el operario no tenga avisos administrativos', async ({page}) => {
  await login(page,'operator/tejeduria/1');
  await page.goto('/notificaciones');
  const filter=page.getByRole('combobox',{name:'Tipo',exact:true});
  await expect(filter.locator('option')).toHaveText(['Todo tipo','Solicitud de administrador','Solicitud revisada','Transferencia entrante','Transferencia aceptada','Transferencia rechazada','Posible falla derivada']);
  await expect(page.locator('.inbox li')).toHaveCount(0);
  await filter.selectOption('Transferencia entrante');
  await expect(page.getByText('No hay notificaciones para este filtro.',{exact:true})).toBeVisible();
  await filter.selectOption('');
});

test('exposición: maquinaria natural coherente con el catálogo real', async ({page}) => {
  await login(page,'admin/tejeduria');
  await page.goto('/maquinaria');
  for(const name of ['Telar de pinzas 01','Telar de aire 01','Telar de pinzas 02','Telar de aire 02']) {
    await expect(page.getByText(name,{exact:true})).toBeVisible();
  }
  await expect(page.getByText(/Picanol|OptiMax|OmniPlus/)).toHaveCount(0);
});
