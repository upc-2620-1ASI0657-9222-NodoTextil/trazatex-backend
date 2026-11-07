import net from 'node:net';
import { spawn } from 'node:child_process';
const servers = [];
for (const [port, host] of [[14200, 'web'], [15173, 'landing']]) {
  const server = net.createServer(client => {
    const upstream = net.connect({ port: 80, host });
    client.on('error', () => upstream.destroy());
    upstream.on('error', () => client.destroy());
    client.pipe(upstream).pipe(client);
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve); });
  servers.push(server);
}
const child = spawn('./node_modules/.bin/playwright', ['test', ...process.argv.slice(2)], { stdio: 'inherit' });
child.on('error', error => { console.error(error.message); process.exit(1); });
child.on('exit', code => { for (const server of servers) server.close(); process.exit(code ?? 1); });