// SafePath Web Viewer - 시연 전용. 실제 사용자 위치나 서버에 연결하지 않습니다.
{
  document.querySelector(".live").textContent = "● DEMO";
  document.querySelector(".demo-pill").textContent = "시연 화면";
  document.querySelectorAll(".info-row strong")[0].textContent = "데모 사용자";
  document.querySelectorAll(".info-row strong")[1].textContent = "계명대학교 성서캠퍼스";
  document.querySelectorAll(".info-row strong")[2].textContent = "대구 성서 지역 (데모)";
  document.getElementById("selectedRoute").textContent = "추천 경로 (시연)";
  document.querySelector(".map-indicator").textContent = "● 시뮬레이션";
  document.querySelector(".heading p").textContent =
    "실제 위치와 연결되지 않은 시뮬레이션 화면입니다.";
  document.querySelector(".sidebar-bottom p").textContent =
    "현재는 시연 화면이며 실제 공유 링크가 아닙니다.";
  document.querySelector(".notice p").textContent =
    "시연 데이터만 표시됩니다. 실제 공유 링크의 접근 제한은 서버 연동 후 적용됩니다.";
  document.querySelector(".session-tag").textContent = "◉ 데모 세션";
  document.querySelector(".activity h2 span").textContent = "● 시뮬레이션";
  document.querySelectorAll(".stat-sub")[3].textContent =
    "시뮬레이션 갱신 시각";
}
const points = [
  [35.8531, 128.4881],
  [35.8526, 128.4891],
  [35.852, 128.4903],
  [35.8513, 128.4914],
  [35.8507, 128.4926],
  [35.85, 128.4938],
  [35.8493, 128.4951],
  [35.8487, 128.4962],
  [35.848, 128.4973],
  [35.8475, 128.4984],
  [35.8469, 128.4995],
  [35.8464, 128.5007],
];
const $ = (id) => document.getElementById(id);
let index = 3,
  paused = false,
  risk = false,
  lastUpdate = Date.now();
const started = new Date();
started.setMinutes(started.getMinutes() - 7);
$("started").textContent = started.toLocaleTimeString("ko-KR", {
  hour: "2-digit",
  minute: "2-digit",
});
const map = L.map("map", { zoomControl: false }).setView(
  [35.8499, 128.4942],
  15,
);
const mapboxToken = document.querySelector('meta[name="mapbox-token"]').content.trim();
const mapMessage = L.control({ position: "topleft" });
mapMessage.onAdd = () => {
  const message = L.DomUtil.create("div", "map-message");
  message.setAttribute("role", "status");
  return message;
};
mapMessage.addTo(map);
const showMapMessage = (message) => {
  mapMessage.getContainer().textContent = message;
  mapMessage.getContainer().hidden = !message;
};
if (!mapboxToken) {
  showMapMessage("지도를 표시하려면 Mapbox 공개 토큰을 설정해 주세요.");
} else {
  showMapMessage("");
  const tiles = L.tileLayer(
    `https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/512/{z}/{x}/{y}@2x?access_token=${encodeURIComponent(mapboxToken)}`,
    {
      tileSize: 512,
      zoomOffset: -1,
      maxZoom: 22,
      attribution:
        '&copy; <a href="https://www.mapbox.com/about/maps/">Mapbox</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    },
  );
  tiles.on("tileerror", () => {
    showMapMessage("지도 이미지를 불러오지 못했습니다. 토큰과 인터넷 연결을 확인해 주세요.");
  });
  tiles.addTo(map);
}
L.control.zoom({ position: "bottomright" }).addTo(map);
const route = L.polyline(points, {
  color: "#a7b9c5",
  weight: 5,
  opacity: 0.7,
  dashArray: "6 8",
}).addTo(map);
let traveled = L.polyline(points.slice(0, index + 1), {
  color: "#2789b8",
  weight: 6,
}).addTo(map);
const icon = (className) =>
  L.divIcon({
    className: "",
    html: `<div class="${className}"></div>`,
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });
const marker = L.marker(points[index], { icon: icon("current-marker") }).addTo(
  map,
);
$("recenter").disabled = false;
$("recenter").onclick = () => map.setView(marker.getLatLng(), Math.max(map.getZoom(), 16));
L.marker(points.at(-1), { icon: icon("destination-marker") })
  .addTo(map)
  .bindPopup("목적지 (데모)");
L.circleMarker(points[0], {
  radius: 6,
  color: "#19445e",
  fillColor: "#19445e",
  fillOpacity: 1,
})
  .addTo(map)
  .bindPopup("출발지");
map.fitBounds(route.getBounds(), { padding: [45, 45] });
function event(title, detail, symbol = "✓") {
  const node = document.createElement("div");
  node.className = "event";
  const badge = document.createElement("div");
  badge.className = "event-icon";
  badge.textContent = symbol;
  const content = document.createElement("div");
  const strong = document.createElement("strong");
  strong.textContent = title;
  const p = document.createElement("p");
  p.textContent = detail;
  content.append(strong, p);
  node.append(badge, content);
  $("events").prepend(node);
  while ($("events").children.length > 4) $("events").lastElementChild.remove();
}
function render() {
  const progress = Math.round((index / (points.length - 1)) * 100);
  $("distance").textContent = ((1.8 * index) / (points.length - 1)).toFixed(2);
  $("remaining").textContent = (
    1.8 *
    (1 - index / (points.length - 1))
  ).toFixed(2);
  $("progressLabel").textContent = progress + "%";
  $("progressBar").style.width = progress + "%";
  traveled.setLatLngs(points.slice(0, index + 1));
  marker.setLatLng(points[index]);
  $("status").textContent = risk
    ? "⚠ 위험 상황 감지"
    : index === points.length - 1
      ? "● 목적지 도착"
      : "● 안전하게 이동 중";
  $("status").style.color = risk ? "#d63e40" : "#198365";
  $("statusDetail").textContent = risk
    ? "위험 감지 시연 중 · 실제 알림은 발송되지 않습니다"
    : index === points.length - 1
      ? "목적지에 도착했습니다 · 세션 종료는 별도 인증 필요"
      : "시뮬레이션 위치입니다";
  lastUpdate = Date.now();
  $("updated").textContent = "방금 전";
}
function tick() {
  if (!paused && !risk && index < points.length - 1) {
    index++;
    render();
    event(
      "위치 정보 업데이트",
      "현재 위치가 갱신되었습니다 · " + new Date().toLocaleTimeString("ko-KR"),
      "⌖",
    );
  } else
    $("updated").textContent =
      Math.floor((Date.now() - lastUpdate) / 1000) + "초 전";
  $("clock").textContent = new Date().toLocaleTimeString("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
  });
}
setInterval(tick, 4000);
setInterval(() => {
  $("clock").textContent = new Date().toLocaleTimeString("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
  });
}, 1000);
$("play").onclick = () => {
  paused = !paused;
  $("play").textContent = paused ? "▶ 재생" : "⏸ 일시정지";
  event(
    paused ? "위치 시뮬레이션 일시정지" : "위치 시뮬레이션 재개",
    "데모 컨트롤에서 변경되었습니다",
  );
};
$("risk").onclick = () => {
  risk = !risk;
  render();
  event(
    risk ? "위험 상황 시뮬레이션" : "위험 상황 해제",
    "데모 화면에서만 상태가 변경됩니다",
    risk ? "!" : "✓",
  );
  $("risk").textContent = risk ? "✓ 위험 해제" : "⚠ 위험 상황 시뮬레이션";
};
$("end").onclick = () => {
  paused = true;
  risk = false;
  map.removeLayer(marker);
  $("recenter").disabled = true;
  map.removeLayer(traveled);
  map.removeLayer(route);
  $("status").textContent = "공유 종료 (데모)";
  $("statusDetail").textContent = "위치 공유가 종료되었습니다.";
  $("play").disabled = true;
  $("risk").disabled = true;
  event("공유 종료", "데모 세션이 종료되었습니다.");
};
$("reset").onclick = () => {
  index = 3;
  paused = false;
  risk = false;
  $("play").disabled = false;
  $("risk").disabled = false;
  if (!map.hasLayer(route)) route.addTo(map);
  if (!map.hasLayer(traveled)) traveled.addTo(map);
  if (!map.hasLayer(marker)) marker.addTo(map);
  $("recenter").disabled = false;
  $("play").textContent = "⏸ 일시정지";
  $("risk").textContent = "⚠ 위험 상황 시뮬레이션";
  render();
  event("데모 초기화", "시뮬레이션이 처음부터 시작됩니다", "↻");
};
event("위치 공유 시작", "시연용 공유 화면이 시작되었습니다");
event("안전귀가 세션 시작", "출발 지점에서 이동을 시작했습니다", "↗");
render();
// 실제 서비스 연동 시: 서버가 공유 토큰을 검증한 후 읽기 전용 상태만 전송해야 합니다.
// WebSocket 예: new WebSocket(`wss://YOUR_DOMAIN/ws/share?token=...`)
// 수신 메시지는 반드시 서버에서 인증/권한 검증 후 제공하고, 화면에서는 textContent로 출력하세요.
