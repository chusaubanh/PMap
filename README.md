# 🏍️ PMap — Bản Đồ Dẫn Đường & Radar Tốc Độ Xe Máy Toàn Bản Đồ Việt Nam
> **Dự án "Em Yêu Khoa Học" dành riêng cho xe máy cá nhân trên Android (Personal Edition)**

---

## ⚡ Các Tính Năng Độc Quyền Cho Xe Máy

1. **Đọc GPS Phần Cứng Trực Tiếp & Cảnh Báo Vượt Tốc Độ (Active Overspeed Warning)**:
   - Đọc trực tiếp tốc độ từ vệ tinh GPS theo thời gian thực (km/h) với tần số 1Hz.
   - Cơ chế phát hiện vi phạm tốc độ theo Thông tư 31/2019/TT-BGTVT:
     * **Tốc độ an toàn (≤ Giới hạn)**: Màu xanh Emerald Neon (`#00ff88`).
     * **Chạm ngưỡng (+1 đến +4 km/h)**: Màu vàng hổ phách Neon (`#ffb700`).
     * **Vượt tốc độ nguy hiểm (≥ +5 km/h - mức phạt CSGT)**: Màn hình nhấp nháy Đỏ rực (`#ff0055`) + chuông báo động dồn dập + giọng nói Tiếng Việt phát qua tai nghe/loa ngoài:
       > *"Cảnh báo! Bạn đang chạy quá tốc độ: 56 trên 50 kilômét một giờ! Hãy giảm ga!"*

2. **Song Hành 2 Chế Độ Thông Minh**:
   - **Chế độ 1: Thay thế luôn Google Maps (Full Navigation Mode)**:
     * Bản đồ Dark Neon AMOLED toàn quốc siêu nét, tiết kiệm pin tối đa cho màn hình điện thoại Xiaomi.
     * Dẫn đường né 100% đường cao tốc và cầu vượt cấm xe 2 bánh.
     * Tích hợp đồng hồ tốc độ kỹ thuật số cỡ đại (nhìn rõ trong 0.5s dưới trời nắng gắt).
   - **Chế độ 2: Mini HUD Nổi đè lên Google Maps (Floating Bubble / Overlay)**:
     * Hiển thị một thanh viên thuốc dạ quang nổi trên góc màn hình Google Maps.
     * Cho phép lấy ngón tay kéo thả tự do sang bất kỳ góc nào để không che bản đồ Google Maps.
     * Hiển thị đầy đủ: `[Tốc độ GPS thực]` / `[Biển giới hạn 50/60]` + `[Icon Camera & Cự ly đếm lùi]`.

3. **Radar Camera Phạt Nguội & Cảnh Báo Cao Tốc Cấm Xe Máy**:
   - Phân loại rõ ràng 4 loại biển & camera:
     * 📷 **Camera Phạt Tốc Độ**
     * 🚦 **Camera Phạt Vượt Đèn Đỏ**
     * 🚸 **Camera Giám Sát Sai Làn Đường Xe Máy**
     * ⛔ **Cảnh Báo Lạc Vào Đường Cao Tốc**: Tự động phát chuông hú khẩn cấp khi xe máy cách lối vào cao tốc dưới 300m.
   - Thuật toán Bearing Filter: Chỉ cảnh báo camera khi xe chạy cùng chiều, bỏ qua camera chiều ngược lại.

4. **Nút 1-Chạm Ghi Nhận Camera Mới (Quick Mark)**:
   - Chạm 1 phát khi đang dừng đèn đỏ để lưu ngay tọa độ camera mới cắm vào cơ sở dữ liệu cá nhân của bạn.

5. **Chạy Ngầm Bền Bỉ (Background Service)**:
   - Dịch vụ Foreground Service với icon cố định, không bị HyperOS/MIUI kill app khi tắt màn hình đút túi quần.
   - Phát cảnh báo bằng giọng nói tiếng Việt to rõ vào tai nghe mũ bảo hiểm Bluetooth.

---

## 📂 Cấu Trúc Mã Nguồn

```
moto-radar-vn/
├── .github/workflows/
│   └── build-apk.yml            # CI/CD tự động biên dịch file PMap.apk trên đám mây
├── android/                     # Dự án Android Native (Kotlin + Jetpack)
│   ├── app/src/main/
│   │   ├── java/com/pmap/vn/
│   │   │   ├── PMapApp.kt       # Application class khởi tạo dữ liệu
│   │   │   ├── MainActivity.kt  # Màn hình chính Cockpit & Bản đồ
│   │   │   ├── service/         # Dịch vụ chạy ngầm GPS & Bong bóng nổi Google Maps
│   │   │   ├── engine/          # Lõi GPS, Tốc độ, Quét Radar & Giọng nói TTS
│   │   │   └── data/            # Repository dữ liệu camera toàn quốc
│   │   ├── res/                 # Layout, Màu sắc Dark Neon, Biển báo giao thông
│   │   └── assets/              # File dữ liệu camera & bản đồ offline
│   ├── build.gradle
│   └── settings.gradle
├── web-hud/                     # Bản Web HUD Dark Neon PWA trải nghiệm ngay
│   ├── index.html               # Giao diện Cockpit xe máy
│   ├── style.css                # CSS tương phản cao chống lóa nắng
│   └── app.js                   # Logic GPS trực tiếp & Giả lập xe máy
├── data/
│   ├── vietnam_cameras.json     # Cơ sở dữ liệu camera phạt nguội toàn quốc
│   ├── vietnam_speed_rules.json # Quy chuẩn tốc độ Thông tư 31/2019/TT-BGTVT
│   ├── generate_datasets.py     # Script tạo và đồng bộ dữ liệu
│   └── tra_cuu_phat_nguoi.py    # Công cụ tra cứu phạt nguội cổng CSGT
└── README.md
```

---

## 🚀 Trải Nghiệm Nhanh

### 1. Trải nghiệm ngay trên Trình duyệt / Điện thoại (Web HUD)
Mở file `web-hud/index.html` trên trình duyệt Chrome / Brave (hoặc chạy local server):
- Đã tích hợp sẵn **Bộ Giả Lập Lái Xe (GPS Simulator)**:
  * Bấm *"TP.HCM Võ Văn Kiệt (Vượt tốc độ 58/50)"* để xem màn hình đổi sang Đỏ rực và nghe giọng nói cảnh báo *"Cảnh báo! Bạn đang chạy quá tốc độ: 58 trên 50 km/h!"*.
  * Bấm *"Chế độ Mini HUD"* để thử nghiệm kéo thả thanh bong bóng nổi.

### 2. Biên dịch File Android APK (GitHub Actions)
Repository được liên kết với tài khoản GitHub `chusaubanh`:
- Khi đẩy code lên GitHub, GitHub Actions sẽ tự động biên dịch ra file **`PMap.apk`** trong vòng 2 phút.
- Tải file APK về điện thoại Android / Xiaomi, mở lên và cấp quyền định vị + hiển thị nổi là có thể vi vu chạy xe an toàn!
