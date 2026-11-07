(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const fragment = new URLSearchParams(location.hash.slice(1));
  const demo = fragment.get("mode") === "demo";
  let token = fragment.get("token");
  // ?? ??? ?????? ?? ??? ?? ???? ??? ?????.
  try {
    if (token) sessionStorage.setItem("safepath.viewer.token", token);
    else if (!demo) token = sessionStorage.getItem("safepath.viewer.token");
  } catch { /* ???? ????? ??? ??? ? ????. */ }
  // 토큰을 주소창과 이후 브라우저 기록에서 제거합니다.
  if (location.hash) history.replaceState(null, "", location.pathname + location.search);
  const localhost = ["localhost", "127.0.0.1", "[::1]"].includes(location.hostname);
  if (demo && !token && window.SAFEPATH_CONFIG.enableDemo && localhost) {
    document.querySelector(".controls").hidden = false;
    document.querySelector(".activity").hidden = false;
    document.querySelector(".demo-pill").hidden = false;
    const script = document.createElement("script");
    script.src = "app.js";
    document.body.append(script);
    return;
  }

  document.querySelector(".controls").remove();
  const lanTest = window.SAFEPATH_CONFIG.enableLanTest === true && location.protocol === "http:";
  document.querySelector(".live").textContent = "연결 확인 중";
  if (lanTest) { document.querySelector(".demo-pill").hidden = false; document.querySelector(".demo-pill").textContent = "간이 테스트"; }
  document.querySelector(".heading p").textContent = "공유 세션 연결을 확인하고 있습니다.";
  document.querySelector(".sidebar-bottom p").textContent = "공유가 종료되면 위치 확인이 중단됩니다.";
  document.querySelector(".notice p").textContent = "공유가 허용된 위치와 상태만 표시합니다.";
  document.querySelector(".activity h2 span").textContent = "● LIVE";
  document.querySelector(".session-tag").textContent = "연결 대기";
  document.querySelectorAll(".info-row strong").forEach((el) => { el.textContent = "—"; });
  ["distance", "remaining", "updated", "progressLabel"].forEach((id) => { $(id).textContent = "—"; });
  $("progressBar").style.width = "0%";
  document.querySelector(".update-detail").textContent = "위치 수신을 기다리는 중";
  document.querySelector("footer span:last-child").textContent = "읽기 전용 공유 화면";
  $("status").textContent = "연결 대기";
  $("statusDetail").textContent = "세션을 확인하고 있습니다.";
  document.querySelector(".map-indicator").textContent = "연결 대기";

  let map, marker, line, routeLine, message, originMarker, destinationMarker;
  if (typeof L !== "undefined") {
    map = L.map("map", { zoomControl: false }).setView([35.8499, 128.4942], 15);
    L.control.zoom({ position: "bottomright" }).addTo(map);
    const control = L.control({ position: "topleft" });
    control.onAdd = () => {
      message = L.DomUtil.create("div", "map-message");
      message.setAttribute("role", "status");
      return message;
    };
    control.addTo(map);
    const mapToken = document.querySelector('meta[name="mapbox-token"]').content.trim();
    if (mapToken) {
      const tiles = L.tileLayer(`https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/512/{z}/{x}/{y}@2x?access_token=${encodeURIComponent(mapToken)}`, {
        tileSize: 512, zoomOffset: -1, maxZoom: 22,
        attribution: '&copy; <a href="https://www.mapbox.com/about/maps/">Mapbox</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      });
      tiles.on("tileerror", () => { message.hidden = false; message.textContent = "지도 이미지를 불러오지 못했습니다. Mapbox 토큰과 네트워크를 확인하세요."; });
      tiles.addTo(map);
      message.hidden = true;
    } else message.textContent = "Mapbox 공개 토큰을 설정해 주세요.";
    routeLine = L.polyline([], { color: "#1B4965", weight: 6, opacity: 0.8 }).addTo(map);
    line = L.polyline([], { color: "#2789b8", weight: 6 }).addTo(map);
  } else {
    $("map").textContent = "지도 라이브러리를 불러오지 못했습니다. 인터넷 연결을 확인하세요.";
  }

  const states = {
    WAITING: ["안전귀가 시작 대기", "#75838d"],
    MOVING: ["● 이동 중", "#198365"], OFF_ROUTE: ["⚠ 경로 이탈", "#d89024"],
    SUSPECTED: ["⚠ 위험 의심 · 확인 중", "#d89024"], DANGER: ["⚠ 위험 상황", "#d63e40"],
    SOS: ["⚠ 긴급 SOS", "#d63e40"], ARRIVED: ["● 도착 완료", "#198365"],
    CANCELLED: ["취소됨", "#75838d"], ENDED: ["공유 종료", "#75838d"], EXPIRED: ["공유 만료", "#75838d"],
  };
  const terminalStates = new Set(["ARRIVED", "CANCELLED", "ENDED", "EXPIRED"]);
  let stopped = false, socket, timer, authTimer, retry = 0, sequence = -1, lastLocation = null, positions = [], snapshotAbort;
  const banner = document.createElement("div");
  banner.className = "notice danger-banner";
  banner.hidden = true;
  banner.setAttribute("role", "alert");
  document.querySelector(".heading").after(banner);
  const connection = (text) => { document.querySelector(".map-indicator").textContent = text; };
  $("recenter").onclick = () => { if (map && marker) map.setView(marker.getLatLng(), Math.max(map.getZoom(), 16)); };
  function clearPosition() {
    positions = []; lastLocation = null;
    if (map && marker) { map.removeLayer(marker); marker = null; }
    if (line) line.setLatLngs([]);
    if (routeLine) routeLine.setLatLngs([]);
    if (map && originMarker) { map.removeLayer(originMarker); originMarker = null; }
    if (map && destinationMarker) { map.removeLayer(destinationMarker); destinationMarker = null; }
    $("recenter").disabled = true;
    $("updated").textContent = "—";
    document.querySelector(".update-detail").textContent = "위치 수신을 기다리는 중";
  }
  function stop(text) {
    stopped = true; clearTimeout(timer); clearTimeout(authTimer); snapshotAbort?.abort();
    if (socket) { socket.onclose = null; socket.close(); socket = null; }
    clearPosition();
    renderSession({});
    $("status").textContent = text; $("status").style.color = "#75838d";
    $("statusDetail").textContent = "더 이상 위치를 제공하지 않습니다.";
    document.querySelector(".session-tag").textContent = text;
    document.querySelector(".heading p").textContent = "공유 세션이 종료되었거나 유효하지 않습니다.";
    document.querySelector(".live").textContent = text;
    banner.hidden = true; connection(text);
  }
  function renderSession(data) {
    const rows = document.querySelectorAll(".info-row strong");
    const info = data.session;
    rows[0].textContent = info?.recipients?.join(", ") || "—";
    rows[1].textContent = info?.origin || "—";
    rows[2].textContent = info?.destination || "—";
    rows[3].textContent = info?.startedAt && Number.isFinite(Date.parse(info.startedAt)) ? new Date(info.startedAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" }) : "—";
    const coords = data.route?.coordinates, loc = data.location;
    let progress = null;
    if (Array.isArray(coords) && coords.length >= 2 && loc && Number.isFinite(loc.latitude) && Number.isFinite(loc.longitude)) {
      const scaleX = Math.cos(loc.latitude * Math.PI / 180), scaleY = 111195;
      let total = 0, nearest = Infinity, along = 0;
      for (let i = 1; i < coords.length; i++) {
        const a = coords[i - 1], b = coords[i];
        if (!Array.isArray(a) || !Array.isArray(b) || ![a[0], a[1], b[0], b[1]].every(Number.isFinite)) { total = 0; break; }
        const ax = (a[0] - loc.longitude) * scaleX * scaleY, ay = (a[1] - loc.latitude) * scaleY;
        const dx = (b[0] - a[0]) * scaleX * scaleY, dy = (b[1] - a[1]) * scaleY;
        const length = Math.hypot(dx, dy), t = length ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / (length * length))) : 0;
        const distance = Math.hypot(ax + t * dx, ay + t * dy);
        if (distance < nearest) { nearest = distance; along = total + t * length; }
        total += length;
      }
      if (total > 0 && nearest <= 100) {
        progress = Math.max(0, Math.min(100, Math.round(100 * along / total)));
        $("distance").textContent = (along / 1000).toFixed(2);
        $("remaining").textContent = ((total - along) / 1000).toFixed(2);
      }
    }
    if (progress === null) { $("distance").textContent = "—"; $("remaining").textContent = "—"; }
    $("progressLabel").textContent = progress === null ? "—" : `${progress}%`;
    $("progressBar").style.width = `${progress ?? 0}%`;
  }
  function applySnapshot(data) {
    if (!data || !Number.isSafeInteger(data.sequence) || data.sequence < 0 || !states[data.status]) throw new Error("invalid snapshot");
    if (data.sequence <= sequence) return;
    sequence = data.sequence;
    renderSession(data);
    if (data.status === "WAITING") clearPosition();
    if (terminalStates.has(data.status)) { stop(states[data.status][0]); return; }
    $("status").textContent = states[data.status][0]; $("status").style.color = states[data.status][1];
    const details = {
      WAITING: "안전귀가가 시작되면 위치가 표시됩니다.",
      MOVING: "공유 중인 위치를 확인하고 있습니다.",
      OFF_ROUTE: "선택한 경로를 벗어난 것으로 표시됩니다.",
      SUSPECTED: "위험 여부를 확인 중입니다.",
      DANGER: "위험 상황이 보고되었습니다. 상황을 확인해 주세요.",
      SOS: "긴급 상황이 보고되었습니다. 상황을 확인해 주세요.",
    };
    $("statusDetail").textContent = details[data.status] || "서버에서 제공한 상태입니다.";
    banner.hidden = !["DANGER", "SOS"].includes(data.status);
    banner.textContent = banner.hidden ? "" : "⚠ 긴급 상황이 보고되었습니다.";
    document.querySelector(".heading p").textContent = "공유된 이동 상황을 확인하고 있습니다.";
    document.querySelector(".session-tag").textContent = data.status === "WAITING" ? "시작 대기" : "공유 중";
    const coords = data.route?.type === "LineString" && data.route.coordinates;
    if (routeLine && Array.isArray(coords) && coords.length >= 2 && coords.length <= 10000 && coords.every(p => Array.isArray(p) && Number.isFinite(p[0]) && Math.abs(p[0]) <= 180 && Number.isFinite(p[1]) && Math.abs(p[1]) <= 90)) {
      const points = coords.map(p => [p[1], p[0]]);
      routeLine.setLatLngs(points);
      if (!originMarker) {
        const endpointIcon = (className) => L.divIcon({ className: "", html: `<div class="${className}"></div>`, iconSize: [20, 20], iconAnchor: [10, 10] });
        originMarker = L.marker(points[0], { icon: endpointIcon("origin-marker"), title: "출발지" }).addTo(map);
        destinationMarker = L.marker(points[points.length - 1], { icon: endpointIcon("destination-marker"), title: "목적지" }).addTo(map);
      } else { originMarker.setLatLng(points[0]); destinationMarker.setLatLng(points[points.length - 1]); }
      if (!lastLocation) map.fitBounds(routeLine.getBounds(), { padding: [30, 30] });
    }
    const loc = data.location;
    if (loc && Number.isFinite(loc.latitude) && Math.abs(loc.latitude) <= 90 && Number.isFinite(loc.longitude) && Math.abs(loc.longitude) <= 180 && Number.isFinite(Date.parse(loc.updatedAt))) {
      const stamp = Date.parse(loc.updatedAt);
      if (!lastLocation || stamp > lastLocation.time) {
        lastLocation = { time: stamp };
        const point = [loc.latitude, loc.longitude];
        positions.push(point); if (positions.length > 1000) positions.shift();
        if (map) {
          if (!marker) { marker = L.marker(point, { icon: L.divIcon({ className: "", html: '<div class="current-marker"></div>', iconSize: [24, 24], iconAnchor: [12, 12] }) }).addTo(map); if (!coords) map.setView(point, 16); }
          else marker.setLatLng(point);
          line.setLatLngs(positions);
          $("recenter").disabled = false;
        }
      }
    }
  }
  async function snapshot() {
    snapshotAbort = new AbortController();
    const response = await fetch("/api/viewer/session", { method: "POST", headers: { Authorization: `Bearer ${token}`, Accept: "application/json" }, cache: "no-store", credentials: "omit", referrerPolicy: "no-referrer", signal: snapshotAbort.signal });
    if ([401, 403, 404, 410].includes(response.status)) { stop(response.status === 410 ? "공유 링크 만료" : "접근할 수 없는 링크"); return false; }
    if (!response.ok) throw new Error("snapshot failed");
    applySnapshot(await response.json());
    return !stopped;
  }
  function schedule() {
    if (stopped) return;
    sequence = -1;
    connection("연결 끊김 · 재연결 대기");
    document.querySelector(".live").textContent = "연결 끊김";
    clearTimeout(timer);
    const delay = Math.min(30000, 1000 * 2 ** Math.min(retry++, 5));
    timer = setTimeout(connect, delay);
  }
  async function connect() {
    try {
      connection("세션 확인 중");
      document.querySelector(".live").textContent = "연결 중";
      if (!await snapshot()) return;
      socket = new WebSocket(`${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws/viewer`);
      const activeSocket = socket;
      let authenticated = false, pending = [];
      authTimer = setTimeout(() => activeSocket.close(), 10000);
      activeSocket.onopen = () => activeSocket.send(JSON.stringify({ type: "authenticate", token }));
      activeSocket.onmessage = async (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === "expired") { stop("공유 종료 또는 만료"); return; }
          if (data.type === "authenticated" && !authenticated) {
            authenticated = true;
            // 구독 후 스냅샷을 다시 읽고 그 사이의 이벤트를 순서대로 적용합니다.
            if (!await snapshot()) return;
            if (stopped || socket !== activeSocket || activeSocket.readyState !== WebSocket.OPEN) return;
            pending.sort((a, b) => a.sequence - b.sequence).forEach(applySnapshot);
            pending = null; clearTimeout(authTimer);
            if (!stopped) { retry = 0; connection("실시간 연결됨"); document.querySelector(".live").textContent = "실시간 연결됨"; }
          } else if (data.type === "snapshot" && authenticated) {
            if (pending) pending.push(data); else applySnapshot(data);
          }
        } catch { activeSocket.close(); }
      };
      activeSocket.onerror = () => activeSocket.close();
      activeSocket.onclose = () => { clearTimeout(authTimer); if (socket === activeSocket) { socket = null; schedule(); } };
    } catch (error) { if (!stopped) schedule(); }
  }
  setInterval(() => {
    $("clock").textContent = new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
    if (lastLocation) {
      const seconds = Math.max(0, Math.floor((Date.now() - lastLocation.time) / 1000));
      $("updated").textContent = seconds < 60 ? `${seconds}초 전` : `${Math.floor(seconds / 60)}분 전`;
      document.querySelector(".update-detail").textContent = seconds > 60 ? "위치 갱신이 지연되고 있습니다" : "최근 수신한 위치";
    }
  }, 1000);
  addEventListener("pagehide", () => { stopped = true; clearTimeout(timer); clearTimeout(authTimer); snapshotAbort?.abort(); if (socket) socket.close(); });
  if (!token) stop("공유 토큰 없음");
  else if (!(location.protocol === "https:" || (location.protocol === "http:" && (localhost || lanTest)))) stop("안전한 연결 필요");
  else connect();
})();
