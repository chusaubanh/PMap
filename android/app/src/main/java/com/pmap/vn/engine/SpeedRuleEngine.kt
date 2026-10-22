package com.pmap.vn.engine

import android.location.Location

enum class SpeedState {
    SAFE,       // Chạy đúng tốc độ (Màu xanh neon)
    CAUTION,    // Chạm ngưỡng (+1 đến +4 km/h) (Màu vàng cam neon)
    OVERSPEED   // Vượt tốc độ (Từ +5 km/h trở lên - bị phạt tiền) (Màu đỏ neon nhấp nháy)
}

object SpeedRuleEngine {
    // Mặc định đô thị theo Thông tư 31/2019/TT-BGTVT
    var currentSpeedLimit: Int = 50

    /**
     * Quy đổi tốc độ từ Location GPS (m/s) sang km/h
     */
    fun getSpeedKmh(location: Location): Int {
        if (!location.hasSpeed()) return 0
        val kmh = location.speed * 3.6f
        return if (kmh < 1.0f) 0 else kmh.toInt()
    }

    /**
     * Đánh giá trạng thái vi phạm tốc độ
     */
    fun evaluateSpeedState(currentSpeed: Int, limit: Int = currentSpeedLimit): SpeedState {
        val diff = currentSpeed - limit
        return when {
            diff >= 5 -> SpeedState.OVERSPEED
            diff in 1..4 -> SpeedState.CAUTION
            else -> SpeedState.SAFE
        }
    }
}
