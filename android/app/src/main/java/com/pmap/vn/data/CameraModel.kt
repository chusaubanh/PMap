package com.pmap.vn.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

data class CameraItem(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val type: String,               // SPEED, RED_LIGHT, LANE, EXPRESSWAY_BAN
    val speed_limit_moto: Int,      // Tốc độ tối đa xe máy (km/h)
    val bearing: Int,               // Hướng camera quét (0 - 360 độ)
    val address: String,
    val warning_text: String
)

object CameraRepository {
    private val cameras = mutableListOf<CameraItem>()
    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        try {
            val inputStream = context.assets.open("vietnam_cameras.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<CameraItem>>() {}.type
            val list: List<CameraItem> = Gson().fromJson(reader, type)
            cameras.clear()
            cameras.addAll(list)
            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAllCameras(): List<CameraItem> = cameras

    fun addCustomCamera(camera: CameraItem) {
        cameras.add(camera)
    }
}
