// 개인 Wi-Fi에서만 사용하는 휘발성 간이 테스트 서버입니다. 운영 배포 금지.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { WebSocketServer, WebSocket } = require('ws');
const port = Number(process.env.SAFEPATH_TEST_PORT || 8080);
const viewerToken = process.env.SAFEPATH_VIEWER_TOKEN || crypto.randomBytes(24).toString('base64url');
const publisherToken = process.env.SAFEPATH_TEST_TOKEN || crypto.randomBytes(24).toString('base64url');
let state = { sequence: 0, status: 'WAITING', location: null, route: null };
const clients = new Set();
const safeEquals = (a, b) => typeof a === 'string' && Buffer.byteLength(a) === Buffer.byteLength(b) && crypto.timingSafeEqual(Buffer.from(a), Buffer.from(b));
const json = (res, code, body) => { res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(body)); };
const auth = (req, secret) => safeEquals(req.headers.authorization, `Bearer ${secret}`);
function notify() { for (const ws of clients) if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: 'snapshot', ...state })); }
const server = http.createServer((req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  res.setHeader('Referrer-Policy', 'no-referrer');
  if (req.method === 'POST' && pathname === '/api/viewer/session') {
    if (!auth(req, viewerToken)) return json(res, 403, {});
    return json(res, 200, state);
  }
  if (req.method === 'POST' && ['/api/test/start', '/api/test/location', '/api/test/end'].includes(pathname)) {
    if (!auth(req, publisherToken)) return json(res, 403, {});
    let raw = '';
    req.on('data', chunk => { raw += chunk; if (raw.length > 1048576) req.destroy(); });
    req.on('end', () => {
      let data; try { data = JSON.parse(raw || '{}'); } catch { return json(res, 400, {}); }
      if (pathname.endsWith('/end')) {
        state = { sequence: state.sequence + 1, status: 'WAITING', location: null, route: null };
        notify(); return json(res, 200, {});
      }
      if (pathname.endsWith('/location') && state.status !== 'MOVING') return json(res, 409, {});
      let session = state.session || null;
      if (pathname.endsWith('/start')) {
        const info = data.session || {};
        if (typeof info !== 'object' || (info.origin != null && (typeof info.origin !== 'string' || info.origin.length > 200)) || (info.destination != null && (typeof info.destination !== 'string' || info.destination.length > 200)) || (info.recipients != null && (!Array.isArray(info.recipients) || info.recipients.length > 30 || !info.recipients.every(n => typeof n === 'string' && n.length <= 100)))) return json(res, 400, {});
        if (info.routeType != null && !['Safe', 'Recommended', 'Shortest'].includes(info.routeType)) return json(res, 400, {});
        session = { origin: info.origin || '', destination: info.destination || '', recipients: info.recipients || [], routeType: info.routeType || null, startedAt: new Date().toISOString() };
      }
      let route = pathname.endsWith('/start') ? null : state.route;
      if (pathname.endsWith('/start') && data.route != null) {
        const coords = data.route.coordinates;
        if (data.route.type !== 'LineString' || !Array.isArray(coords) || coords.length < 2 || coords.length > 10000 || !coords.every(p => Array.isArray(p) && p.length >= 2 && Number.isFinite(p[0]) && Math.abs(p[0]) <= 180 && Number.isFinite(p[1]) && Math.abs(p[1]) <= 90)) return json(res, 400, {});
        route = { type: 'LineString', coordinates: coords.map(p => [p[0], p[1]]) };
      }
      let location = pathname.endsWith('/start') ? null : state.location;
      if (data.latitude != null || data.longitude != null) {
        if (!Number.isFinite(data.latitude) || Math.abs(data.latitude) > 90 || !Number.isFinite(data.longitude) || Math.abs(data.longitude) > 180) return json(res, 400, {});
        location = { latitude: data.latitude, longitude: data.longitude, updatedAt: new Date().toISOString() };
      }
      if (pathname.endsWith('/start') && state.status !== 'WAITING') {
        state = { sequence: state.sequence + 1, status: 'WAITING', location: null, route: null };
        notify();
      }
      state = { sequence: state.sequence + 1, status: 'MOVING', location, route, session };
      notify(); json(res, 200, {});
    });
    return;
  }
  if (req.method !== 'GET') return json(res, 404, {});
  if (pathname === '/config.js') { res.writeHead(200, { 'Content-Type': 'application/javascript', 'Cache-Control': 'no-store' }); return res.end('window.SAFEPATH_CONFIG={enableDemo:false,enableLanTest:true};'); }
  const files = { '/': 'index.html', '/index.html': 'index.html', '/viewer.js': 'viewer.js', '/style.css': 'style.css' };
  if (!files[pathname]) return json(res, 404, {});
  res.writeHead(200, { 'Content-Type': pathname.endsWith('.js') ? 'application/javascript' : pathname.endsWith('.css') ? 'text/css' : 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
  fs.createReadStream(path.join(__dirname, files[pathname])).pipe(res);
});
const wss = new WebSocketServer({ server, path: '/ws/viewer', maxPayload: 4096 });
wss.on('connection', (ws, req) => {
  if (req.headers.origin !== `http://${req.headers.host}`) return ws.close();
  const timeout = setTimeout(() => ws.close(), 5000);
  ws.once('message', raw => {
    clearTimeout(timeout);
    let msg; try { msg = JSON.parse(raw); } catch { return ws.close(); }
    if (msg.type !== 'authenticate' || !safeEquals(msg.token, viewerToken)) { ws.send(JSON.stringify({ type: 'expired' })); return ws.close(); }
    clients.add(ws); ws.send(JSON.stringify({ type: 'authenticated' }));
  });
  ws.on('close', () => { clearTimeout(timeout); clients.delete(ws); });
  ws.on('error', () => clients.delete(ws));
});
server.listen(port, '0.0.0.0', () => {
  const addresses = Object.values(os.networkInterfaces()).flat().filter(item => item && item.family === 'IPv4' && !item.internal).map(item => item.address);
  console.log('개인 네트워크 간이 테스트 전용. 서버 종료 시 모든 데이터가 삭제됩니다.');
  console.log(`앱 설정 SAFEPATH_TEST_URL=http://<PC Wi-Fi IP>:${port}/`);
  console.log(`앱 설정 SAFEPATH_TEST_TOKEN=${publisherToken}`);
  for (const ip of addresses) console.log(`웹 링크: http://${ip}:${port}/#token=${viewerToken}`);
  console.log(`같은 PC 웹 링크: http://localhost:${port}/#token=${viewerToken}`);
});
