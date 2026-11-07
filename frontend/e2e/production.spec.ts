import { test, expect, APIRequestContext } from './base';
import { readFileSync } from 'node:fs';
import { account, ensureAccount, env } from './support';
import { credentialsFile } from './paths';
// En una instalación sembrada (TRAZATEX_CREDENTIALS_PATH) las administraciones «a» y «b» ya existen con su empresa: se usan esas
// empresas en vez de registrar otras con el mismo correo (que el backend rechaza con EMAIL_ALREADY_REGISTERED).
const seededCompanyOf = (key: string): string | undefined => {
  if (!process.env['TRAZATEX_CREDENTIALS_PATH']) return undefined;
  const alias: Record<string, string> = { a: 'admin/hilanderia', b: 'admin/tejeduria' };
  return JSON.parse(readFileSync(credentialsFile(), 'utf8')).accounts.find((x: { key: string }) => x.key === alias[key])?.companyId;
};
test('contratos reales: empresas, operarios, lotes, mezclas, transferencias y privacidad', async ({
  request,
}) => {
  test.setTimeout(240000);
  const owner = await ensureAccount(request, account('owner', env['TRAZATEX_OWNER_EMAIL']));
  const call = async (method: string, path: string, token: string, data?: unknown) => {
    const response = await request.fetch('/api' + path, {
      method,
      headers: { Authorization: 'Bearer ' + token },
      data,
    });
    expect(response.ok(), path + ': ' + (await response.text())).toBe(true);
    return await response.json();
  };
  const companies = await call('GET', '/companies', owner.token);
  const create = async (key: string, ruc: string) => {
    const admin = account('admin-' + key);
    const seeded = seededCompanyOf(key);
    const existing = seeded
      ? companies.find((c: { id: string }) => c.id === seeded)
      : companies.find((c: { ruc: string }) => c.ruc === ruc);
    const company =
      existing ??
      (
        await call('POST', '/companies', owner.token, {
          ruc,
          businessName: 'Prueba de integración ' + key,
          activities: ['SPINNING', 'WEAVING', 'DYEING'],
          adminEmail: admin.email,
        })
      ).company;
    return { company, admin: await ensureAccount(request, admin) };
  };
  const a = await create('a', '20100070970');
  const b = await create('b', '20131312955');
  await call('PUT', '/privacy-settings', a.admin.token, { fields: { SUPPLIER: 'PRIVATE' } });
  const operator = account('operator-a');
  const users = await call('GET', '/users', a.admin.token);
  if (!users.some((u: { email: string }) => u.email === operator.email)) {
    await call('POST', '/invitations/operators', a.admin.token, {
      firstName: 'Prueba',
      lastName: 'Operario',
      email: operator.email,
      position: 'Producción',
    });
  }
  const op = await ensureAccount(request, operator);
  expect(
    (
      await request.get('/api/companies', { headers: { Authorization: 'Bearer ' + op.token } })
    ).status(),
  ).toBe(403);
  expect(
    (
      await request.get('/api/users', { headers: { Authorization: 'Bearer ' + op.token } })
    ).status(),
  ).toBe(403);
  const machines = await call('GET', '/machines', a.admin.token);
  const machine =
    machines.find((m: { internalCode: string }) => m.internalCode === 'E2E-MQ') ??
    (await call('POST', '/machines', a.admin.token, {
      internalCode: 'E2E-MQ',
      name: 'Hiladora de prueba',
      type: 'Hiladora',
    }));
  const batch = await call('POST', '/batches', op.token, {
    name: 'Lote prueba ' + String(Date.now()).slice(-6),
    materialType: 'Fibra de algodón Pima',
    quantityKg: 100,
    composition: [{ name: 'Algodón Pima', percentage: 100 }],
    origin: 'Perú',
    supplierName: 'Proveedor privado',
    receptionCharacteristics: 'Recepción verificada',
  });
  expect(
    (
      await request.get('/api/batches/' + batch.id, {
        headers: { Authorization: 'Bearer ' + b.admin.token },
      })
    ).status(),
  ).toBe(404);
  const split = await call('POST', '/batches/' + batch.id + '/split', op.token, {
    children: [
      { name: 'Hijo A', quantityKg: 60 },
      { name: 'Hijo B', quantityKg: 40 },
    ],
  });
  expect(split.children).toHaveLength(2);
  const process = await call('POST', '/transformations', op.token, {
    type: 'SPINNING',
    machineId: machine.id,
    batchIds: split.children.map((c: { id: string }) => c.id),
  });
  const completed = await call('POST', '/transformations/' + process.id + '/complete', op.token, {
    outputs: [
      { name: 'Hilo resultado', materialType: 'Hilo peinado', quantityKg: 90, finalProduct: false },
    ],
    wasteKg: 5,
    wasteReason: 'Prueba controlada',
  });
  expect(completed.outputs).toHaveLength(1);
  const output = completed.outputs[0];
  const transfer = await call('POST', '/transfers', op.token, {
    targetCompanyId: b.company.id,
    batchIds: [output.id],
  });
  const rejected = await call('POST', '/transfers/' + transfer.id + '/reject', b.admin.token, {
    reason: 'Rechazo de prueba',
  });
  expect(rejected.status).toBe('REJECTED');
  const second = await call('POST', '/transfers', op.token, {
    targetCompanyId: b.company.id,
    batchIds: [output.id],
  });
  const accepted = await call('POST', '/transfers/' + second.id + '/accept', b.admin.token);
  expect(accepted.status).toBe('ACCEPTED');
  expect((await call('GET', '/batches/' + output.id, b.admin.token)).companyId).toBe(b.company.id);
  const lineage = async () =>
    request.get('/api/traceability/batches/' + output.id + '/lineage', {
      headers: { Authorization: 'Bearer ' + b.admin.token },
    });
  await expect
    .poll(
      async () => {
        const r = await lineage();
        return r.ok() && (await r.json()).nodes.length >= 4;
      },
      { timeout: 45000 },
    )
    .toBe(true);
  const graph = await (await lineage()).json();
  const upstream = graph.nodes.find((n: { id: string }) => n.id === batch.id);
  await expect
    .poll(
      async () => {
        const current = await (await lineage()).json();
        const parent = current.nodes.find((n: { id: string }) => n.id === batch.id);
        return !!parent && parent.supplierName === undefined;
      },
      { timeout: 45000 },
    )
    .toBe(true);
  expect(upstream.commercialData).toBeUndefined();
  expect(upstream.createdBy).toBeUndefined();
  await call('PUT', '/privacy-settings', a.admin.token, { fields: { SUPPLIER: 'SHARED' } });
  await expect
    .poll(
      async () => {
        const graph = await (await lineage()).json();
        return graph.nodes.find((n: { id: string }) => n.id === batch.id)?.supplierName;
      },
      { timeout: 45000 },
    )
    .toBe('Proveedor privado');
  // Ronda 5c: la siembra deja el proveedor de la hilandería en PRIVADO. Se restablece para que otros specs (demo-scenarios) no dependan del orden.
  await call('PUT', '/privacy-settings', a.admin.token, { fields: { SUPPLIER: 'PRIVATE' } });
});
