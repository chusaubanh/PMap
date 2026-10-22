package com.pmap.vn

import android.app.Application
import com.pmap.vn.data.CameraRepository

class PMapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Nạp sẵn toàn bộ dữ liệu camera phạt nguội toàn quốc từ Assets
        CameraRepository.init(this)
    }
}
