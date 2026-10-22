/**
 * PMAP - Hệ thống Giám Sát Tốc Độ & Camera Phạt Nguội Xe Máy Toàn Bản Đồ Việt Nam
 * Lõi định vị GPS thời gian thực, cảnh báo vượt tốc độ và radar camera
 */

// ================= GLOBAL STATE =================
const state = {
  currentLat: 10.7769,    // Mặc định TP.HCM (hoặc GPS thực tế)
  currentLng: 106.7009,
  currentSpeed: 0,        // km/h
  currentHeading: 0,      // độ (0-360)
  speedLimit: 50,         // km/h (Mặc định khu đô thị)
  currentZone: 'ĐÔ THỊ',
  isVoiceEnabled: true,
  isMiniHudMode: false,
  isSimulating: false,
  simulationInterval: null,
  lastAlertTime: 0,
  cameras: [],
  userMarker: null,
  map: null,
  cameraLayerGroup: null,
};

// ================= DOM ELEMENTS =================
const liveSpeedEl = document.getElementById('liveSpeed');
const speedStatusTextEl = document.getElementById('speedStatusText');
const limitValueEl = document.getElementById('limitValue');
const zoneLabelEl = document.getElementById('zoneLabel');
const compassArrowEl = document.getElementById('compassArrow');
const headingTextEl = document.getElementById('headingText');
const camNameEl = document.getElementById('camName');
const camDescEl = document.getElementById('camDesc');
const camDistanceEl = document.getElementById('camDistance');
const cameraAlertBoxEl = document.getElementById('cameraAlertBox');
const camIconBoxEl = document.getElementById('camIconBox');
const cameraCountEl = document.getElementById('cameraCount');
const simDrawerEl = document.getElementById('simDrawer');

// Floating Mini HUD elements
const floatingHudEl = document.getElementById('floatingHud');
const floatingPillEl = document.getElementById('floatingPill');
const pillLiveSpeedEl = document.getElementById('pillLiveSpeed');
const pillLimitBadgeEl = document.getElementById('pillLimitBadge');
const pillCamDistEl = document.getElementById('pillCamDist');
const pillCamIconEl = document.getElementById('pillCamIcon');

// Audio
const beepAudioEl = document.getElementById('beepAudio');

// ================= 1. INIT LEAFLET MAP =================
function initMap() {
  state.map = L.map('vietnamMap', {
    zoomControl: false,
    attributionControl: false
  }).setView([state.currentLat, state.currentLng], 14);

  // Thêm điều khiển Zoom ở góc phải
  L.control.zoom({ position: 'topright' }).addTo(state.map);

  // Bản đồ OpenStreetMap chính thống - 100% Miễn phí, Không cần bất kỳ API key nào!
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '© OpenStreetMap'
  }).addTo(state.map);

  state.cameraLayerGroup = L.layerGroup().addTo(state.map);

  // Tạo marker vị trí xe máy của người dùng
  const bikeIcon = L.divIcon({
    className: 'user-bike-marker',
    html: `
      <div style="
        width: 38px; height: 38px; border-radius: 50%;
        background: #00ff88; border: 3px solid #ffffff;
        box-shadow: 0 0 20px #00ff88, 0 0 40px rgba(0,255,136,0.6);
        display: flex; align-items: center; justify-content: center;
        font-size: 18px; transform: rotate(${state.currentHeading}deg);
      ">🏍️</div>
    `,
    iconSize: [38, 38],
    iconAnchor: [19, 19]
  });

  state.userMarker = L.marker([state.currentLat, state.currentLng], { icon: bikeIcon }).addTo(state.map);
}

// ================= 2. LOAD CAMERA DATASET =================
async function loadCameras() {
  if (window.VIETNAM_CAMERAS && window.VIETNAM_CAMERAS.length > 0) {
    state.cameras = window.VIETNAM_CAMERAS;
    cameraCountEl.textContent = `${state.cameras.length} Camera`;
    renderCamerasOnMap();
    return;
  }

  try {
    const res = await fetch('data/vietnam_cameras.json');
    state.cameras = await res.json();
    cameraCountEl.textContent = `${state.cameras.length} Camera`;
    renderCamerasOnMap();
  } catch (err) {
    console.error('Không thể tải cameras_vietnam.json:', err);
  }
}

function renderCamerasOnMap() {
  state.cameraLayerGroup.clearLayers();

  state.cameras.forEach(cam => {
    let iconEmoji = '📷';
    let borderColor = '#ffb700';

    if (cam.type === 'RED_LIGHT') {
      iconEmoji = '🚦';
      borderColor = '#ff3366';
    } else if (cam.type === 'LANE') {
      iconEmoji = '🚸';
      borderColor = '#00d2ff';
    } else if (cam.type === 'EXPRESSWAY_BAN') {
      iconEmoji = '⛔';
      borderColor = '#ff0033';
    }

    const camIcon = L.divIcon({
      className: 'camera-pin-marker',
      html: `
        <div style="
          width: 32px; height: 32px; border-radius: 10px;
          background: #0d1117; border: 2px solid ${borderColor};
          box-shadow: 0 0 10px ${borderColor};
          display: flex; align-items: center; justify-content: center;
          font-size: 16px;
        ">${iconEmoji}</div>
      `,
      iconSize: [32, 32],
      iconAnchor: [16, 16]
    });

    const marker = L.marker([cam.lat, cam.lng], { icon: camIcon });
    marker.bindPopup(`
      <div style="color: #000; font-family: sans-serif; font-size: 13px;">
        <b style="color: #da251d;">[${cam.type}] ${cam.name}</b><br/>
        <span>📍 ${cam.address}</span><br/>
        <span>🏍️ Tốc độ cho phép: <b>${cam.speed_limit_moto} km/h</b></span><br/>
        <p style="margin-top: 5px; font-size: 12px; color: #333;">${cam.warning_text}</p>
      </div>
    `);
    state.cameraLayerGroup.addLayer(marker);
  });
}

// ================= 3. GPS HARDWARE DIRECT ENGINE =================
function startGpsTracking() {
  if (!navigator.geolocation) {
    speedStatusTextEl.textContent = 'TRÌNH DUYỆT KHÔNG HỖ TRỢ GPS';
    return;
  }

  speedStatusTextEl.textContent = 'ĐANG BẮT TÍN HIỆU VỆ TINH GPS...';

  navigator.geolocation.watchPosition(
    position => {
      if (state.isSimulating) return; // Nếu đang bật giả lập thì bỏ qua GPS thực tế

      const coords = position.coords;
      state.currentLat = coords.latitude;
      state.currentLng = coords.longitude;

      // coords.speed trả về mét/giây -> quy đổi sang km/h
      let speedKmh = 0;
      if (coords.speed !== null && !isNaN(coords.speed) && coords.speed > 0) {
        speedKmh = Math.round(coords.speed * 3.6);
      }
      state.currentSpeed = speedKmh;

      if (coords.heading !== null && !isNaN(coords.heading)) {
        state.currentHeading = Math.round(coords.heading);
      }

      speedStatusTextEl.textContent = 'GPS TRỰC TIẾP (CHUẨN XÁC)';
      updateCockpitUI();
      updateRadar();
      recenterMap(state.currentLat, state.currentLng);
    },
    error => {
      console.warn('Lỗi GPS:', error.message);
      if (!state.isSimulating) {
        speedStatusTextEl.textContent = 'GPS YẾU (HÃY BẬT VỊ TRÍ CHÍNH XÁC)';
      }
    },
    {
      enableHighAccuracy: true,
      maximumAge: 0,
      timeout: 10000
    }
  );
}

// ================= 4. HAVERSINE DISTANCE & BEARING RADAR =================
function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371e3; // Mét
  const φ1 = lat1 * Math.PI / 180;
  const φ2 = lat2 * Math.PI / 180;
  const Δφ = (lat2 - lat1) * Math.PI / 180;
  const Δλ = (lon2 - lon1) * Math.PI / 180;

  const a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
            Math.cos(φ1) * Math.cos(φ2) *
            Math.sin(Δλ/2) * Math.sin(Δλ/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return Math.round(R * c);
}

function updateRadar() {
  if (!state.cameras || state.cameras.length === 0) return;

  let nearestCam = null;
  let minDistance = Infinity;

  for (const cam of state.cameras) {
    const dist = calculateDistance(state.currentLat, state.currentLng, cam.lat, cam.lng);
    if (dist < minDistance) {
      minDistance = dist;
      nearestCam = cam;
    }
  }

  if (nearestCam && minDistance <= 1500) {
    camNameEl.textContent = nearestCam.name;
    camDescEl.textContent = nearestCam.address;
    camDistanceEl.textContent = minDistance;

    // Mini pill HUD
    pillCamDistEl.textContent = `${minDistance}m`;

    if (nearestCam.type === 'RED_LIGHT') {
      camIconBoxEl.textContent = '🚦';
      pillCamIconEl.textContent = '🚦';
    } else if (nearestCam.type === 'EXPRESSWAY_BAN') {
      camIconBoxEl.textContent = '⛔';
      pillCamIconEl.textContent = '⛔';
    } else {
      camIconBoxEl.textContent = '📷';
      pillCamIconEl.textContent = '📷';
    }

    // Cập nhật tốc độ giới hạn theo đoạn đường nếu camera có quy định
    if (nearestCam.speed_limit_moto > 0 && minDistance <= 400) {
      state.speedLimit = nearestCam.speed_limit_moto;
      limitValueEl.textContent = state.speedLimit;
      pillLimitBadgeEl.textContent = state.speedLimit;
    }

    // Kích hoạt trạng thái cảnh báo nếu cách dưới 500m
    if (minDistance <= 500) {
      cameraAlertBoxEl.className = 'camera-alert-box active-alert';
      triggerAudioAlert(nearestCam, minDistance);
    } else {
      cameraAlertBoxEl.className = 'camera-alert-box';
    }
  } else {
    camNameEl.textContent = 'Không có camera trong bán kính 1.5km';
    camDescEl.textContent = 'An tâm lái xe an toàn';
    camDistanceEl.textContent = '--';
    pillCamDistEl.textContent = '--m';
    cameraAlertBoxEl.className = 'camera-alert-box';
  }
}

// ================= 5. OVERSPEED DETECTION & COCKPIT UI =================
function updateCockpitUI() {
  liveSpeedEl.textContent = state.currentSpeed;
  pillLiveSpeedEl.textContent = state.currentSpeed;

  compassArrowEl.style.transform = `rotate(${state.currentHeading}deg)`;
  headingTextEl.textContent = `${state.currentHeading}°`;

  // Kiểm tra vi phạm tốc độ xe máy
  const overspeed = state.currentSpeed - state.speedLimit;

  if (overspeed >= 5) {
    // VƯỢT TỐC ĐỘ NGUY HIỂM (MỨC PHẠT NGHỊ ĐỊNH 100/123)
    document.body.classList.add('state-overspeed');
    document.documentElement.style.setProperty('--speed-color', '#ff0055');
    speedStatusTextEl.textContent = `🚨 VƯỢT TỐC ĐỘ: +${overspeed} km/h`;
    
    // Phát âm thanh cảnh báo bíp & giọng nói
    triggerOverspeedVoice(state.currentSpeed, state.speedLimit);
  } else if (overspeed > 0) {
    // CHẠM NGƯỠNG CẢNH BÁO (+1 đến +4 km/h)
    document.body.classList.remove('state-overspeed');
    document.documentElement.style.setProperty('--speed-color', '#ffb700');
    speedStatusTextEl.textContent = `⚠️ CHẠM NGƯỠNG TỐC ĐỘ (+${overspeed} km/h)`;
  } else {
    // TỐC ĐỘ AN TOÀN
    document.body.classList.remove('state-overspeed');
    document.documentElement.style.setProperty('--speed-color', '#00ff88');
    if (!state.isSimulating) {
      speedStatusTextEl.textContent = 'GPS TRỰC TIẾP (CHUẨN XÁC)';
    }
  }

  // Cập nhật vị trí và góc xoay của xe trên bản đồ
  if (state.userMarker) {
    state.userMarker.setLatLng([state.currentLat, state.currentLng]);
    const markerEl = state.userMarker.getElement();
    if (markerEl) {
      const bikeDiv = markerEl.querySelector('div');
      if (bikeDiv) {
        bikeDiv.style.transform = `rotate(${state.currentHeading}deg)`;
      }
    }
  }
}

function recenterMap(lat, lng) {
  if (state.map) {
    state.map.panTo([lat, lng], { animate: true, duration: 0.5 });
  }
}

// ================= 6. TEXT-TO-SPEECH (TTS) VIỆT NAM =================
function speakVietnamese(text) {
  if (!state.isVoiceEnabled || !('speechSynthesis' in window)) return;

  // Hủy lời thoại cũ đang chờ để phát cảnh báo mới ngay
  window.speechSynthesis.cancel();

  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = 'vi-VN';
  utterance.rate = 1.05; // Tốc độ vừa phải, dứt khoát
  utterance.pitch = 1.0;

  // Tìm giọng tiếng Việt nếu có sẵn
  const voices = window.speechSynthesis.getVoices();
  const viVoice = voices.find(v => v.lang.includes('vi') || v.lang.includes('VIE'));
  if (viVoice) {
    utterance.voice = viVoice;
  }

  window.speechSynthesis.speak(utterance);
}

function triggerAudioAlert(cam, distance) {
  const now = Date.now();
  // Giãn cách các lần đọc ít nhất 8 giây để không nói đè liên tục
  if (now - state.lastAlertTime < 8000) return;

  state.lastAlertTime = now;

  if (cam.type === 'EXPRESSWAY_BAN') {
    speakVietnamese(`Cảnh báo nguy hiểm! Phía trước cách ${distance} mét là đường cao tốc cấm xe máy! Hãy đi vào làn đường gom!`);
  } else if (cam.type === 'RED_LIGHT') {
    speakVietnamese(`Chú ý! Phía trước cách ${distance} mét có camera phạt nguội vượt đèn đỏ!`);
  } else if (cam.type === 'SPEED') {
    speakVietnamese(`Chú ý! Phía trước cách ${distance} mét có camera bắn tốc độ! Tốc độ tối đa xe máy ${cam.speed_limit_moto} kilômét một giờ!`);
  } else {
    speakVietnamese(`Chú ý! Phía trước cách ${distance} mét có camera giám sát làn đường xe máy!`);
  }
}

let lastOverspeedAlert = 0;
function triggerOverspeedVoice(current, limit) {
  const now = Date.now();
  if (now - lastOverspeedAlert < 6000) return; // Nhắc lại mỗi 6s nếu chưa giảm ga
  lastOverspeedAlert = now;

  speakVietnamese(`Cảnh báo! Bạn đang chạy quá tốc độ: ${current} trên ${limit} kilômét một giờ! Hãy giảm ga!`);
}

// ================= 7. SIMULATOR (EM YÊU KHOA HỌC) =================
function startSimulation(preset) {
  stopSimulation();
  state.isSimulating = true;
  simDrawerEl.style.display = 'none';

  if (preset === 'hcm_speeding') {
    // Giả lập chạy trên Đại lộ Võ Văn Kiệt TP.HCM, tăng tốc lên 58 km/h ở đoạn 50 km/h
    state.currentLat = 10.73210;
    state.currentLng = 106.62540;
    state.currentHeading = 70;
    state.speedLimit = 50;
    limitValueEl.textContent = '50';
    pillLimitBadgeEl.textContent = '50';
    zoneLabelEl.textContent = 'ĐÔ THỊ VÕ VĂN KIỆT';

    let currentSpd = 35;
    state.simulationInterval = setInterval(() => {
      // Tiến dần về phía camera Võ Văn Kiệt
      state.currentLat += 0.00015;
      state.currentLng += 0.00030;
      currentSpd = Math.min(58, currentSpd + 3);
      state.currentSpeed = currentSpd;

      speedStatusTextEl.textContent = '🧪 ĐANG GIẢ LẬP: CHẠY XE MÁY VÕ VĂN KIỆT';
      updateCockpitUI();
      updateRadar();
      recenterMap(state.currentLat, state.currentLng);
    }, 1000);

  } else if (preset === 'hn_redlight') {
    // Giả lập chạy trên đường Xã Đàn Hà Nội tiếp cận camera đèn đỏ
    state.currentLat = 21.01124 - 0.0050;
    state.currentLng = 105.83652 - 0.0050;
    state.currentHeading = 85;
    state.speedLimit = 50;
    limitValueEl.textContent = '50';
    pillLimitBadgeEl.textContent = '50';
    zoneLabelEl.textContent = 'ĐÔ THỊ HÀ NỘI';
    state.currentSpeed = 46;

    state.simulationInterval = setInterval(() => {
      state.currentLat += 0.00025;
      state.currentLng += 0.00025;
      speedStatusTextEl.textContent = '🧪 ĐANG GIẢ LẬP: TIẾP CẬN CAMERA ĐÈN ĐỎ XÃ ĐÀN';
      updateCockpitUI();
      updateRadar();
      recenterMap(state.currentLat, state.currentLng);
    }, 1000);

  } else if (preset === 'expressway_warning') {
    // Giả lập tiến gần lối vào Cao tốc Long Thành cấm xe máy
    state.currentLat = 10.79321 - 0.0040;
    state.currentLng = 106.75841 - 0.0040;
    state.currentHeading = 95;
    state.currentSpeed = 52;
    state.speedLimit = 50;

    state.simulationInterval = setInterval(() => {
      state.currentLat += 0.00020;
      state.currentLng += 0.00020;
      speedStatusTextEl.textContent = '🧪 ĐANG GIẢ LẬP: CẢNH BÁO LẠC VÀO ĐƯỜNG CAO TỐC';
      updateCockpitUI();
      updateRadar();
      recenterMap(state.currentLat, state.currentLng);
    }, 1000);
  }
}

function stopSimulation() {
  state.isSimulating = false;
  if (state.simulationInterval) {
    clearInterval(state.simulationInterval);
    state.simulationInterval = null;
  }
  document.body.classList.remove('state-overspeed');
  speedStatusTextEl.textContent = 'GPS TRỰC TIẾP (CHUẨN XÁC)';
}

// ================= 8. FLOATING MINI HUD DRAG & DROP =================
function initFloatingHudDrag() {
  let isDragging = false;
  let startX, startY, initialLeft, initialTop;

  floatingPillEl.addEventListener('pointerdown', e => {
    isDragging = true;
    startX = e.clientX;
    startY = e.clientY;
    const rect = floatingHudEl.getBoundingClientRect();
    initialLeft = rect.left;
    initialTop = rect.top;
    floatingPillEl.setPointerCapture(e.pointerId);
  });

  window.addEventListener('pointermove', e => {
    if (!isDragging) return;
    const dx = e.clientX - startX;
    const dy = e.clientY - startY;
    floatingHudEl.style.left = `${initialLeft + dx}px`;
    floatingHudEl.style.top = `${initialTop + dy}px`;
    floatingHudEl.style.right = 'auto';
  });

  window.addEventListener('pointerup', e => {
    isDragging = false;
  });
}

// ================= 9. EVENT LISTENERS =================
function setupEventListeners() {
  // Bật/tắt âm thanh giọng nói
  const btnVoice = document.getElementById('btnVoiceToggle');
  btnVoice.addEventListener('click', () => {
    state.isVoiceEnabled = !state.isVoiceEnabled;
    btnVoice.classList.toggle('active', state.isVoiceEnabled);
    speakVietnamese(state.isVoiceEnabled ? 'Đã bật cảnh báo giọng nói PMap' : 'Đã tắt giọng nói');
  });

  // Chuyển chế độ Toàn màn hình <-> Floating Mini HUD
  const btnSwitchMode = document.getElementById('btnSwitchMode');
  const btnBackToFull = document.getElementById('btnBackToFull');
  const modeIndicatorEl = document.getElementById('modeIndicator');

  btnSwitchMode.addEventListener('click', () => {
    state.isMiniHudMode = true;
    floatingHudEl.style.display = 'block';
    modeIndicatorEl.textContent = 'CHẾ ĐỘ HUD NỔI ĐÈ MAPS';
  });

  btnBackToFull.addEventListener('click', () => {
    state.isMiniHudMode = false;
    floatingHudEl.style.display = 'none';
    modeIndicatorEl.textContent = 'BẢN ĐỒ DẪN ĐƯỜNG';
  });

  // Nút 1-Chạm ghi nhận Camera mới (Quick Mark)
  const btnQuickMark = document.getElementById('btnQuickMark');
  btnQuickMark.addEventListener('click', () => {
    const newCam = {
      id: `USER-${Date.now()}`,
      name: `Camera bạn vừa đánh dấu lúc ${new Date().toLocaleTimeString('vi-VN')}`,
      lat: state.currentLat,
      lng: state.currentLng,
      type: 'SPEED',
      speed_limit_moto: state.speedLimit,
      bearing: state.currentHeading,
      address: `Tọa độ: ${state.currentLat.toFixed(5)}, ${state.currentLng.toFixed(5)}`,
      warning_text: 'Camera do chính bạn ghi nhận trên đường chạy'
    };

    state.cameras.push(newCam);
    cameraCountEl.textContent = `${state.cameras.length} Camera`;
    renderCamerasOnMap();
    speakVietnamese('Đã lưu vị trí camera mới vào bản đồ PMap cá nhân của bạn!');
  });

  // Nút căn giữa lại xe
  document.getElementById('btnRecenterGPS').addEventListener('click', () => {
    recenterMap(state.currentLat, state.currentLng);
  });

  // Nút mở bảng giả lập
  document.getElementById('btnSimulateRide').addEventListener('click', () => {
    simDrawerEl.style.display = simDrawerEl.style.display === 'none' ? 'block' : 'none';
  });

  document.getElementById('btnCloseSim').addEventListener('click', () => {
    simDrawerEl.style.display = 'none';
  });

  // Tìm đường xe máy
  document.getElementById('btnSearchGo').addEventListener('click', async () => {
    const query = document.getElementById('mapSearchInput').value.trim();
    if (!query) return;

    try {
      const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&countrycodes=vn&q=${encodeURIComponent(query)}`);
      const results = await res.json();
      if (results && results.length > 0) {
        const target = results[0];
        const destLat = parseFloat(target.lat);
        const destLng = parseFloat(target.lon);

        L.marker([destLat, destLng])
          .addTo(state.map)
          .bindPopup(`<b>Điểm đến:</b> ${target.display_name}`)
          .openPopup();

        // Vẽ đường bay định hướng
        const latlngs = [
          [state.currentLat, state.currentLng],
          [destLat, destLng]
        ];
        L.polyline(latlngs, { color: '#00d2ff', weight: 4, dashArray: '8, 8' }).addTo(state.map);
        state.map.fitBounds(latlngs, { padding: [50, 50] });

        speakVietnamese(`Đã tìm thấy tuyến đường xe máy tới ${target.display_name.split(',')[0]}`);
      } else {
        alert('Không tìm thấy địa điểm tại Việt Nam. Vui lòng nhập rõ hơn!');
      }
    } catch (e) {
      console.error(e);
    }
  });
}

// ================= 10. BOOTSTRAP =================
window.addEventListener('DOMContentLoaded', async () => {
  initMap();
  await loadCameras();
  startGpsTracking();
  initFloatingHudDrag();
  setupEventListeners();

  // Chào mừng khởi động
  setTimeout(() => {
    speakVietnamese('PMap đã sẵn sàng. Chúc bạn vạn dặm bình an!');
  }, 1200);
});
