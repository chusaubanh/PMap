package com.pmap.vn

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.pmap.vn.data.CameraItem
import com.pmap.vn.data.CameraRepository
import com.pmap.vn.engine.SpeedState
import com.pmap.vn.service.PMapFloatingService
import com.pmap.vn.service.PMapLocationService

class MainActivity : AppCompatActivity() {

    private lateinit var mapWebView: WebView
    private lateinit var tvMainSpeed: TextView
    private lateinit var tvSpeedLimit: TextView
    private lateinit var tvZoneType: TextView
    private lateinit var tvSpeedWarningStatus: TextView
    private lateinit var tvCompassArrow: TextView
    private lateinit var tvHeadingDegree: TextView
    private lateinit var tvNearestCamName: TextView
    private lateinit var tvNearestCamAddress: TextView
    private lateinit var tvNearestCamDist: TextView
    private lateinit var tvCamEmoji: TextView
    private lateinit var tvGpsStatus: TextView

    private var currentLat = 10.7769
    private var currentLng = 106.7009
    private var currentHeading = 0f
    private var isVoiceActive = true

    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PMapFloatingService.ACTION_UPDATE_HUD) {
                val speed = intent.getIntExtra(PMapFloatingService.EXTRA_SPEED, 0)
                val limit = intent.getIntExtra(PMapFloatingService.EXTRA_LIMIT, 50)
                val camDist = intent.getIntExtra(PMapFloatingService.EXTRA_CAM_DIST, -1)
                val camType = intent.getStringExtra(PMapFloatingService.EXTRA_CAM_TYPE) ?: "SPEED"
                val camName = intent.getStringExtra("EXTRA_CAM_NAME") ?: ""
                val camAddr = intent.getStringExtra("EXTRA_CAM_ADDR") ?: ""
                val stateOrdinal = intent.getIntExtra(PMapFloatingService.EXTRA_STATE, SpeedState.SAFE.ordinal)
                val speedState = SpeedState.values()[stateOrdinal]
                val heading = intent.getFloatExtra("EXTRA_HEADING", 0f)

                currentLat = intent.getDoubleExtra("EXTRA_LAT", currentLat)
                currentLng = intent.getDoubleExtra("EXTRA_LNG", currentLng)
                currentHeading = heading

                updateCockpitUI(speed, limit, camDist, camType, camName, camAddr, speedState, heading)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initWebViewMap()
        checkPermissionsAndStart()

        val filter = IntentFilter(PMapFloatingService.ACTION_UPDATE_HUD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(locationUpdateReceiver, filter)
        }
    }

    private fun initViews() {
        mapWebView = findViewById(R.id.mapWebView)
        tvMainSpeed = findViewById(R.id.tvMainSpeed)
        tvSpeedLimit = findViewById(R.id.tvSpeedLimit)
        tvZoneType = findViewById(R.id.tvZoneType)
        tvSpeedWarningStatus = findViewById(R.id.tvSpeedWarningStatus)
        tvCompassArrow = findViewById(R.id.tvCompassArrow)
        tvHeadingDegree = findViewById(R.id.tvHeadingDegree)
        tvNearestCamName = findViewById(R.id.tvNearestCamName)
        tvNearestCamAddress = findViewById(R.id.tvNearestCamAddress)
        tvNearestCamDist = findViewById(R.id.tvNearestCamDist)
        tvCamEmoji = findViewById(R.id.tvCamEmoji)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)

        // Nút chuyển sang chế độ Bong bóng nổi đè Google Maps
        findViewById<Button>(R.id.btnStartOverlay).setOnClickListener {
            checkOverlayPermissionAndStart()
        }

        // Nút đánh dấu nhanh Camera mới
        findViewById<FloatingActionButton>(R.id.fabQuickMark).setOnClickListener {
            val newCam = CameraItem(
                id = "USER_${System.currentTimeMillis()}",
                name = "Camera bạn vừa ghi nhận",
                lat = currentLat,
                lng = currentLng,
                type = "SPEED",
                speed_limit_moto = tvSpeedLimit.text.toString().toIntOrNull() ?: 50,
                bearing = currentHeading.toInt(),
                address = "Tọa độ: ${"%.5f".format(currentLat)}, ${"%.5f".format(currentLng)}",
                warning_text = "Camera cá nhân do chính bạn đánh dấu"
            )
            CameraRepository.addCustomCamera(newCam)
            Toast.makeText(this, "Đã lưu vị trí Camera mới vào bản đồ PMap!", Toast.LENGTH_SHORT).show()
        }

        // Nút Recenter
        findViewById<FloatingActionButton>(R.id.fabRecenter).setOnClickListener {
            mapWebView.evaluateJavascript("if (window.recenterMap) window.recenterMap($currentLat, $currentLng);", null)
        }

        // Bật/tắt giọng nói
        val btnVoice = findViewById<ImageButton>(R.id.btnVoiceToggle)
        btnVoice.setOnClickListener {
            isVoiceActive = !isVoiceActive
            btnVoice.setColorFilter(if (isVoiceActive) Color.parseColor("#00FF88") else Color.parseColor("#888888"))
            Toast.makeText(this, if (isVoiceActive) "Đã bật giọng nói cảnh báo" else "Đã tắt giọng nói", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebViewMap() {
        val webSettings: WebSettings = mapWebView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.allowFileAccess = true

        mapWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tvGpsStatus.text = "BẢN ĐỒ TOÀN QUỐC SẴN SÀNG"
            }
        }

        // Tải trang Web HUD tích hợp bản đồ OSM toàn Việt Nam từ Assets
        mapWebView.loadUrl("file:///android_asset/web_map.html")
    }

    private fun updateCockpitUI(
        speed: Int,
        limit: Int,
        camDist: Int,
        camType: String,
        camName: String,
        camAddr: String,
        speedState: SpeedState,
        heading: Float
    ) {
        tvMainSpeed.text = speed.toString()
        tvSpeedLimit.text = limit.toString()

        tvCompassArrow.rotation = heading
        tvHeadingDegree.text = "${heading.toInt()}°"

        // Đổi màu tốc độ và cảnh báo
        when (speedState) {
            SpeedState.OVERSPEED -> {
                tvMainSpeed.setTextColor(Color.parseColor("#FF0055"))
                tvSpeedWarningStatus.text = "🚨 VƯỢT TỐC ĐỘ: +${speed - limit} km/h"
                tvSpeedWarningStatus.setTextColor(Color.parseColor("#FF0055"))
                tvSpeedWarningStatus.setBackgroundColor(Color.parseColor("#33FF0055"))
            }
            SpeedState.CAUTION -> {
                tvMainSpeed.setTextColor(Color.parseColor("#FFB700"))
                tvSpeedWarningStatus.text = "⚠️ CHẠM NGƯỠNG (+${speed - limit} km/h)"
                tvSpeedWarningStatus.setTextColor(Color.parseColor("#FFB700"))
                tvSpeedWarningStatus.setBackgroundColor(Color.parseColor("#33FFB700"))
            }
            SpeedState.SAFE -> {
                tvMainSpeed.setTextColor(Color.parseColor("#00FF88"))
                tvSpeedWarningStatus.text = "TỐC ĐỘ AN TOÀN"
                tvSpeedWarningStatus.setTextColor(Color.parseColor("#00FF88"))
                tvSpeedWarningStatus.setBackgroundColor(Color.parseColor("#1AFFFFFF"))
            }
        }

        // Cập nhật Radar Camera
        if (camDist in 0..1500 && camName.isNotEmpty()) {
            tvNearestCamName.text = camName
            tvNearestCamAddress.text = camAddr
            tvNearestCamDist.text = "${camDist}m"
            tvCamEmoji.text = when (camType) {
                "RED_LIGHT" -> "🚦"
                "EXPRESSWAY_BAN" -> "⛔"
                "LANE" -> "🚸"
                else -> "📷"
            }
        } else {
            tvNearestCamName.text = "Đang quét camera trên lộ trình..."
            tvNearestCamAddress.text = "Hệ thống sẵn sàng cảnh báo trước 500m"
            tvNearestCamDist.text = "--m"
            tvCamEmoji.text = "📷"
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        } else {
            startLocationService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationService()
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, PMapLocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 202)
            Toast.makeText(this, "Vui lòng cấp quyền 'Hiển thị trên các ứng dụng khác' cho PMap để vẽ HUD nổi đè lên Google Maps!", Toast.LENGTH_LONG).show()
        } else {
            startFloatingService()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 202) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                Toast.makeText(this, "Chưa cấp quyền hiển thị nổi!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFloatingService() {
        val floatIntent = Intent(this, PMapFloatingService::class.java)
        startService(floatIntent)
        Toast.makeText(this, "Đã kích hoạt Bong bóng nổi PMap! Bây giờ bạn có thể mở Google Maps", Toast.LENGTH_LONG).show()

        // Thu nhỏ PMap về Home để người dùng mở Google Maps
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(locationUpdateReceiver)
    }
}
