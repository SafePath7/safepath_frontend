const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const { WebSocket } = require('ws');
const path = require('node:path');

(async () => {
  const port = 18761;
  const server = spawn(process.execPath, [path.join(__dirname, 'test-server.cjs')], { env: { ...process.env, SAFEPATH_TEST_PORT: String(port) } });
  let output = '';
  try {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('server startup timeout')), 5000);
      server.on('exit', code => reject(new Error(`server exited: ${code}`)));
      server.stdout.on('data', chunk => { output += chunk; if (output.includes('같은 PC 웹 링크:')) { clearTimeout(timer); resolve(); } });
    });
    const publisher = output.match(/SAFEPATH_TEST_TOKEN=([^\s]+)/)[1];
    const viewer = output.match(/#token=([^\s]+)/)[1];
    const base = `http://localhost:${port}`;
    const post = (url, token, body = {}) => fetch(base + url, { method: 'POST', headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: JSON.stringify(body) });
    assert.equal((await post('/api/viewer/session', 'bad')).status, 403);
    assert.equal((await (await post('/api/viewer/session', viewer)).json()).status, 'WAITING');
    const ws = new WebSocket(`ws://localhost:${port}/ws/viewer`, { origin: base });
    const received = [];
    ws.on('message', data => received.push(JSON.parse(data)));
    await new Promise((resolve, reject) => { ws.on('open', resolve); ws.on('error', reject); });
    ws.send(JSON.stringify({ type: 'authenticate', token: viewer }));
    await new Promise(resolve => setTimeout(resolve, 30));
    assert.equal(received[0].type, 'authenticated');
    assert.equal((await post('/api/test/start', publisher, { latitude: 35.85, longitude: 128.49, session: { origin: 'A', destination: 'B', recipients: ['C'], routeType: 'Recommended' }, route: { type: 'LineString', coordinates: [[128.49, 35.85], [128.50, 35.86]] } })).status, 200);
    assert.equal((await post('/api/test/location', publisher, { latitude: 35.851, longitude: 128.49 })).status, 200);
    await new Promise(resolve => setTimeout(resolve, 30));
    assert.equal(received.at(-1).location.latitude, 35.851);
    assert.equal(received.at(-1).route.coordinates.length, 2);
    assert.deepEqual(received.at(-1).session.recipients, ['C']);
    assert.equal(received.at(-1).session.origin, 'A');
    assert.equal(received.at(-1).session.routeType, 'Recommended');
    assert.ok(Date.parse(received.at(-1).session.startedAt));
    assert.equal((await post('/api/test/start', publisher, { latitude: 35.9, longitude: 128.5 })).status, 200);
    const replaced = await (await post('/api/viewer/session', viewer)).json();
    assert.equal(replaced.location.latitude, 35.9);
    assert.equal(replaced.route, null);
    assert.equal((await post('/api/test/end', publisher)).status, 200);
    await new Promise(resolve => setTimeout(resolve, 30));
    assert.equal(received.at(-1).status, 'WAITING');
    assert.equal(received.at(-1).location, null);
    assert.equal(received.at(-1).route, null);
    assert.equal((await post('/api/test/start', publisher, { route: { type: 'LineString', coordinates: [[128.49, 35.85], [128.5, 35.86]] } })).status, 200);
    const restarted = await (await post('/api/viewer/session', viewer)).json();
    assert.equal(restarted.status, 'MOVING');
    assert.equal(restarted.route.coordinates.length, 2);
    ws.close();
    console.log('간이 서버 인증·위치 갱신·종료 테스트 통과');
  } finally { server.kill(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
