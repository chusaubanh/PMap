package com.pmap.vn.engine

import com.pmap.vn.data.CameraItem
import com.pmap.vn.data.CameraRepository
import kotlin.math.*

data class RadarResult(
    val nearestCamera: CameraItem?,
    val distanceMeters: Int,
    val isAlertZone: Boolean,       // Trong cự ly cảnh báo <= 500m
    val isSameDirection: Boolean     // Cùng hướng di chuyển
)

object CameraRadarEngine {

    /**
     * Tính khoảng cách Haversine giữa 2 tọa độ GPS (Đơn vị: Mét)
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val r = 6371000.0 // Bán kính Trái Đất (mét)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toInt()
    }

    /**
     * Quét tìm camera gần nhất và kiểm tra hướng di chuyển
     */
    fun scanNearestCamera(currentLat: Double, currentLng: Double, currentHeading: Float): RadarResult {
        val allCameras = CameraRepository.getAllCameras()
        var nearest: CameraItem? = null
        var minDistance = Int.MAX_VALUE

        for (cam in allCameras) {
            val dist = calculateDistance(currentLat, currentLng, cam.lat, cam.lng)
            if (dist < minDistance) {
                minDistance = dist
                nearest = cam
            }
        }

        if (nearest == null || minDistance > 1500) {
            return RadarResult(null, minDistance, isAlertZone = false, isSameDirection = false)
        }

        // Kiểm tra sai lệch góc di chuyển
        val angleDiff = abs(currentHeading - nearest.bearing)
        val normalizedDiff = min(angleDiff, 360 - angleDiff)
        val isSameDir = normalizedDiff <= 65 // Cho phép sai lệch góc ±65 độ

        return RadarResult(
            nearestCamera = nearest,
            distanceMeters = minDistance,
            isAlertZone = minDistance <= 500,
            isSameDirection = isSameDir
        )
    }
}
