const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

test('LIVE 인증 후 상태 갱신, 중복 무시, 종료 시 재연결 중단', async () => {
  const elements = new Map();
  function element(key) {
    if (!elements.has(key)) elements.set(key, { textContent: '', style: {}, hidden: false, remove() {}, after() {}, setAttribute() {} });
    return elements.get(key);
  }
  const document = {
    getElementById: element,
    querySelector: element,
    querySelectorAll: (selector) => Array.from({ length: 4 }, (_, i) => element(`${selector}:${i}`)),
    createElement: () => element('banner'),
    body: { append() {} },
  };
  let sockets = [], fetches = 0;
  class Socket {
    static OPEN = 1;
    constructor() { this.readyState = 1; sockets.push(this); }
    send(value) { this.sent = JSON.parse(value); }
    close() { this.readyState = 3; if (this.onclose) this.onclose(); }
  }
  const context = {
    document, window: { SAFEPATH_CONFIG: { enableDemo: false } },
    location: { hash: '#token=sample', pathname: '/', search: '', hostname: 'localhost', host: 'localhost', protocol: 'http:' },
    history: { replaceState() {} }, URLSearchParams, WebSocket: Socket, AbortController,
    fetch: async () => ({ ok: true, json: async () => ({ sequence: ++fetches, status: 'MOVING', location: null }) }),
    setTimeout: () => 1, clearTimeout() {}, setInterval() {}, addEventListener() {},
  };
  vm.runInNewContext(fs.readFileSync(require('node:path').join(__dirname, 'viewer.js'), 'utf8'), context);
  await new Promise(setImmediate);
  const ws = sockets[0];
  ws.onopen();
  assert.equal(ws.sent.type, 'authenticate');
  await ws.onmessage({ data: JSON.stringify({ type: 'authenticated' }) });
  await ws.onmessage({ data: JSON.stringify({ type: 'snapshot', sequence: 3, status: 'SUSPECTED', location: null }) });
  assert.match(element('status').textContent, /확인 중/);
  await ws.onmessage({ data: JSON.stringify({ type: 'snapshot', sequence: 3, status: 'SOS', location: null }) });
  assert.match(element('status').textContent, /확인 중/);
  await ws.onmessage({ data: JSON.stringify({ type: 'snapshot', sequence: 4, status: 'SOS', location: null }) });
  assert.match(element('status').textContent, /SOS/);
  await ws.onmessage({ data: JSON.stringify({ type: 'snapshot', sequence: 5, status: 'MOVING', session: { origin: 'A', destination: 'B', recipients: ['C'], startedAt: '2026-01-01T00:00:00Z' }, route: { type: 'LineString', coordinates: [[0, 0], [0.01, 0]] }, location: { latitude: 0, longitude: 0.005, updatedAt: '2026-01-01T00:00:00Z' } }) });
  assert.equal(element('.info-row strong:0').textContent, 'C');
  assert.equal(element('.info-row strong:1').textContent, 'A');
  assert.equal(element('.info-row strong:2').textContent, 'B');
  assert.equal(element('progressLabel').textContent, '50%');
  await ws.onmessage({ data: JSON.stringify({ type: 'expired' }) });
  assert.match(element('status').textContent, /종료/);
  assert.equal(ws.readyState, 3);
  assert.equal(sockets.length, 1);
});
