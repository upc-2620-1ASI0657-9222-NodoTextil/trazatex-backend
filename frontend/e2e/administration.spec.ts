import { test, expect, Locator } from './base';
import { env } from './support';

test.use({ trace: 'off', screenshot: 'off', video: 'off', ignoreHTTPSErrors: true });
test.afterEach(async({page})=>{
 if(!page.isClosed())await page.locator('input[type="password"],input[type="email"],input[name="username"]').evaluateAll(inputs=>inputs.forEach(input=>(input as HTMLInputElement).value=''));
});
const internal=process.env['TRAZATEX_TEST_INTERNAL_NETWORK']==='true';
const portainer=internal?'https://portainer:9443':'https://127.0.0.1:19443';
const pgadmin=internal?'http://pgadmin':'http://127.0.0.1:15050';
const kafka=internal?'http://kafka-ui:8080':'http://127.0.0.1:18090';
const neo4j=internal?'http://neo4j:7474':'http://127.0.0.1:17474';
const admin=internal?'http://boot-admin:8080':'http://127.0.0.1:18095';
async function secret(input:Locator,value:string){
  await input.evaluate((element,value)=>{
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value')!.set!.call(element,value);
    element.dispatchEvent(new Event('input',{bubbles:true}));
    element.dispatchEvent(new Event('change',{bubbles:true}));
  },value);
}


test('Portainer autentica, agrupa TrazaTex y permite consultar estado, logs y estadísticas', async ({
  request,
  page,
}) => {
  const base = portainer;
  const auth = await request.post(base + '/api/auth', {
    data: { Username: env['ADMIN_USERNAME'], Password: env['PORTAINER_PASSWORD'] },
  });
  expect(auth.status()).toBe(200);
  const headers = { Authorization: 'Bearer ' + (await auth.json()).jwt };
  const endpoints = await (await request.get(base + '/api/endpoints', { headers })).json();
  expect(endpoints.length).toBeGreaterThan(0);
  const endpoint = endpoints[0].Id;
  const filter = encodeURIComponent(
    JSON.stringify({ label: ['com.docker.compose.project='+(process.env['TRAZATEX_COMPOSE_PROJECT']??'trazatex-platform')] }),
  );
  const response = await request.get(
    base + '/api/endpoints/' + endpoint + '/docker/containers/json?all=true&filters=' + filter,
    { headers },
  );
  expect(response.status()).toBe(200);
  const containers = (await response.json()).filter(
    (item: { Labels: Record<string, string> }) =>
      !['e2e','database-init','administration-init'].includes(item.Labels['com.docker.compose.service']),
  );
  expect(containers.length).toBeGreaterThanOrEqual(13);
  expect(containers.every((item: { State: string }) => item.State === 'running')).toBe(true);
  const web = containers.find(
    (item: { Labels: Record<string, string> }) =>
      item.Labels['com.docker.compose.service'] === 'web',
  );
  const stats = await request.get(
    base + '/api/endpoints/' + endpoint + '/docker/containers/' + web.Id + '/stats?stream=false',
    { headers },
  );
  expect(stats.status()).toBe(200);
  const statistics = await stats.json();
  expect(statistics.memory_stats).toBeDefined();
  expect(statistics.cpu_stats).toBeDefined();
  const logs = await request.get(
    base + '/api/endpoints/' + endpoint + '/docker/containers/' + web.Id + '/logs?stdout=1&tail=5',
    { headers },
  );
  expect(logs.status()).toBe(200);
  const containerUrl = base + '/api/endpoints/' + endpoint + '/docker/containers/' + web.Id;
  if (process.env['TRAZATEX_TEST_PANEL_CONTROLS']==='true') {
    try {
      expect((await request.post(containerUrl + '/stop?t=15', { headers })).status()).toBe(204);
      expect((await (await request.get(containerUrl + '/json', { headers })).json()).State.Status).toBe(
        'exited',
      );
      expect((await request.post(containerUrl + '/start', { headers })).status()).toBe(204);
      await expect
        .poll(
          async () =>
            (await (await request.get(containerUrl + '/json', { headers })).json()).State.Health.Status,
        )
        .toBe('healthy');
      expect((await request.post(containerUrl + '/restart?t=15', { headers })).status()).toBe(204);
      await expect
        .poll(
          async () =>
            (await (await request.get(containerUrl + '/json', { headers })).json()).State.Health.Status,
        )
        .toBe('healthy');
    } finally {
      await request.post(containerUrl + '/start',{headers});
    }
  }
  await page.goto(base);
  await expect(page.locator('body')).not.toBeEmpty();
});

test('pgAdmin y Kafka UI presentan autenticación y Neo4j Browser carga sus recursos', async ({
  page,
}) => {
  await page.goto(pgadmin+'/login');
  await expect(page.locator('input[type="password"]')).toBeVisible();
  await page.locator('input[name="email"]').fill(env['PGADMIN_EMAIL']);
  await secret(page.locator('input[name="password"]'),env['PGADMIN_PASSWORD']);
  await page.locator('input[type="submit"], button[type="submit"]').first().click();
  await expect(page).toHaveURL(/\/browser\//);
  await page.getByText('TrazaTex', { exact: true }).dblclick();
  await expect(page.getByText(/^(Organization & Access|ORGACCESS)$/)).toBeVisible();
  await expect(page.getByText(/^(Production|PRODUCTION)$/)).toBeVisible();
  await page.getByText(/^(Organization & Access|ORGACCESS)$/).dblclick();
  await expect(page.getByText(/^Databases/).first()).toBeVisible();
  await page.goto(kafka);
  await expect(page.locator('input[type="password"]')).toBeVisible();
  await page.locator('input[name="username"]').fill(env['ADMIN_USERNAME']);
  await secret(page.locator('input[name="password"]'),env['KAFKA_UI_PASSWORD']);
  await page.getByRole('button', { name: /sign in|log in/i }).click();
  await expect(page.locator('body')).toContainText('TrazaTex');
  await page.goto(neo4j+'/browser/');
  await expect(page.locator('body')).toContainText(/Neo4j|Connect/i);
});

test('Spring Boot Admin requiere credenciales y muestra los siete componentes con métricas', async ({
  playwright,
}) => {
  const anonymous = await playwright.request.newContext();
  expect((await anonymous.get(admin+'/applications')).status()).toBe(401);
  await anonymous.dispose();
  const request = await playwright.request.newContext({
    extraHTTPHeaders: { Accept: 'application/json' },
    httpCredentials: { username: env['ADMIN_USERNAME'], password: env['BOOT_ADMIN_PASSWORD'] },
  });
  const response = await request.get(admin+'/instances');
  expect(response.status()).toBe(200);
  await expect
    .poll(
      async () => {
        const services = await (await request.get(admin+'/instances')).json();
        return services.filter(
          (item: { statusInfo: { status: string } }) => item.statusInfo.status === 'UP',
        ).length;
      },
      { timeout: 45000 },
    )
    .toBe(7);
  const services = await (await request.get(admin+'/instances')).json();
  for (const service of services) {
    expect(
      (
        await request.get(admin+'/instances/' + service.id + '/actuator/metrics')
      ).status(),
    ).toBe(200);
  }
  await request.dispose();
});


test('Neo4j autentica el usuario nativo solicitado y conserva la genealogía',async({request})=>{
  const response=await request.post(neo4j+'/db/neo4j/tx/commit',{
    headers:{Authorization:'Basic '+Buffer.from(env['NEO4J_USER']+':'+env['NEO4J_PASSWORD']).toString('base64')},
    data:{statements:[{statement:'MATCH (b:Batch) RETURN count(b)'},{statement:'MATCH ()-[r]->() RETURN count(r)'}]},
  });
  expect(response.status()).toBe(200);
  const result=await response.json();
  expect(result.errors).toEqual([]);
  expect(result.results[0].data[0].row[0]).toBe(59);
  expect(result.results[1].data[0].row[0]).toBeGreaterThan(60);
});


test('pgAdmin conecta las otras cuatro bases con sus cuentas individuales',async({page})=>{
  await page.goto(pgadmin+'/login');
  await page.locator('input[name="email"]').fill(env['PGADMIN_EMAIL']);
  await secret(page.locator('input[name="password"]'),env['PGADMIN_PASSWORD']);
  await page.getByRole('button',{name:'Login',exact:true}).click();
  await expect(page).toHaveURL(/\/browser\//);
  await page.getByText('TrazaTex',{exact:true}).dblclick();
  for(const label of [/^(Production|PRODUCTION)$/,/^(Quality|QUALITY)$/,/^(Notification|NOTIFICATION)$/,/^(Analytics|ANALYTICS)$/]){
    const before=await page.getByText(/^Databases/).count();
    await page.getByText(label).first().dblclick();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByText(/^Databases/)).toHaveCount(before+1);
  }
});
