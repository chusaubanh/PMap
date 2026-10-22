package com.pmap.vn.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pmap.vn.MainActivity
import com.pmap.vn.R
import com.pmap.vn.engine.CameraRadarEngine
import com.pmap.vn.engine.SpeedRuleEngine
import com.pmap.vn.engine.SpeedState
import com.pmap.vn.engine.VietnameseTtsEngine

class PMapLocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var ttsEngine: VietnameseTtsEngine
    private var lastSpeedAlertTime = 0L
    private var lastCamAlertTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        ttsEngine = VietnameseTtsEngine(this)

        startAsForeground()
        startGpsUpdates()
    }

    private fun startAsForeground() {
        val channelId = "pmap_location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PMap Giám sát Tốc độ Xe Máy",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Chạy ngầm cảnh báo tốc độ và camera phạt nguội"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("PMap đang chạy ngầm")
            .setContentText("Đang lắng nghe GPS và quét camera trên đường...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1001, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGpsUpdates() {
        try {
            // Lắng nghe trực tiếp từ phần cứng vệ tinh GPS với tần số 1000ms
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    this
                )
            }
            // Lớp mạng dự phòng
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    0f,
                    this
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        val speedKmh = SpeedRuleEngine.getSpeedKmh(location)
        val heading = if (location.hasBearing()) location.bearing else 0f
        val limit = SpeedRuleEngine.currentSpeedLimit

        // 1. Kiểm tra vi phạm tốc độ
        val speedState = SpeedRuleEngine.evaluateSpeedState(speedKmh, limit)
        if (speedState == SpeedState.OVERSPEED) {
            triggerOverspeedAlert(speedKmh, limit)
        }

        // 2. Quét camera phạt nguội gần nhất
        val radarResult = CameraRadarEngine.scanNearestCamera(location.latitude, location.longitude, heading)
        val nearestCam = radarResult.nearestCamera
        val dist = radarResult.distanceMeters

        if (radarResult.isAlertZone && nearestCam != null) {
            triggerCameraAlert(nearestCam.name, nearestCam.type, dist, nearestCam.warning_text)
        }

        // 3. Gửi broadcast tới Floating HUD và MainActivity
        val updateIntent = Intent(PMapFloatingService.ACTION_UPDATE_HUD).apply {
            putExtra(PMapFloatingService.EXTRA_SPEED, speedKmh)
            putExtra(PMapFloatingService.EXTRA_LIMIT, limit)
            putExtra(PMapFloatingService.EXTRA_CAM_DIST, dist)
            putExtra(PMapFloatingService.EXTRA_CAM_TYPE, nearestCam?.type ?: "SPEED")
            putExtra(PMapFloatingService.EXTRA_STATE, speedState.ordinal)
            putExtra("EXTRA_HEADING", heading)
            putExtra("EXTRA_LAT", location.latitude)
            putExtra("EXTRA_LNG", location.longitude)
            putExtra("EXTRA_CAM_NAME", nearestCam?.name ?: "")
            putExtra("EXTRA_CAM_ADDR", nearestCam?.address ?: "")
        }
        sendBroadcast(updateIntent)
    }

    private fun triggerOverspeedAlert(speed: Int, limit: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSpeedAlertTime > 6000L) { // Nhắc lại sau mỗi 6s nếu chưa giảm ga
            lastSpeedAlertTime = now
            ttsEngine.speak("Cảnh báo! Bạn đang chạy quá tốc độ: $speed trên $limit kilômét một giờ!")
        }
    }

    private fun triggerCameraAlert(name: String, type: String, dist: Int, warningText: String) {
        val now = System.currentTimeMillis()
        if (now - lastCamAlertTime > 8000L) {
            lastCamAlertTime = now
            val prefix = when (type) {
                "EXPRESSWAY_BAN" -> "Cảnh báo nguy hiểm! Phía trước là đường cao tốc cấm xe máy! Cách"
                "RED_LIGHT" -> "Chú ý! Phía trước có camera phạt nguội vượt đèn đỏ! Cách"
                "SPEED" -> "Chú ý! Phía trước có camera bắn tốc độ! Cách"
                else -> "Chú ý! Phía trước có camera giám sát làn đường! Cách"
            }
            ttsEngine.speak("$prefix $dist mét! $warningText")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
        ttsEngine.shutdown()
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
