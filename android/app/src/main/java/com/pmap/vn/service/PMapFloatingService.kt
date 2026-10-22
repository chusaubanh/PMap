package com.pmap.vn.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.pmap.vn.MainActivity
import com.pmap.vn.R
import com.pmap.vn.engine.SpeedState
import kotlin.math.abs

class PMapFloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    private var tvFloatSpeed: TextView? = null
    private var tvFloatLimit: TextView? = null
    private var tvFloatCamDist: TextView? = null
    private var tvFloatCamIcon: TextView? = null

    // Lắng nghe cập nhật GPS từ PMapLocationService
    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_UPDATE_HUD) {
                val speed = intent.getIntExtra(EXTRA_SPEED, 0)
                val limit = intent.getIntExtra(EXTRA_LIMIT, 50)
                val camDist = intent.getIntExtra(EXTRA_CAM_DIST, -1)
                val camType = intent.getStringExtra(EXTRA_CAM_TYPE) ?: "SPEED"
                val stateOrdinal = intent.getIntExtra(EXTRA_STATE, SpeedState.SAFE.ordinal)
                val speedState = SpeedState.values()[stateOrdinal]

                updateHudUI(speed, limit, camDist, camType, speedState)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Đăng ký receiver
        val filter = IntentFilter(ACTION_UPDATE_HUD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dataReceiver, filter)
        }

        initFloatingLayout()
    }

    private fun initFloatingLayout() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_hud_layout, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 150

        tvFloatSpeed = floatingView?.findViewById(R.id.tvFloatSpeed)
        tvFloatLimit = floatingView?.findViewById(R.id.tvFloatLimit)
        tvFloatCamDist = floatingView?.findViewById(R.id.tvFloatCamDist)
        tvFloatCamIcon = floatingView?.findViewById(R.id.tvFloatCamIcon)

        // Nút mở lại ứng dụng PMap
        floatingView?.findViewById<View>(R.id.btnFloatOpenApp)?.setOnClickListener {
            val appIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(appIntent)
        }

        // Logic Kéo thả bong bóng nổi trên màn hình
        setupDragListener()

        windowManager.addView(floatingView, params)
    }

    private fun setupDragListener() {
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Tự động hít vào mép trái hoặc phải màn hình khi buông tay
                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val middle = screenWidth / 2
                        params.x = if (params.x + (floatingView?.width ?: 0) / 2 < middle) 20 else screenWidth - (floatingView?.width ?: 0) - 20
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun updateHudUI(speed: Int, limit: Int, camDist: Int, camType: String, speedState: SpeedState) {
        tvFloatSpeed?.text = speed.toString()
        tvFloatLimit?.text = limit.toString()

        // Đổi màu tốc độ theo trạng thái
        when (speedState) {
            SpeedState.OVERSPEED -> {
                tvFloatSpeed?.setTextColor(Color.parseColor("#FF0055"))
                floatingView?.setBackgroundResource(R.drawable.bg_floating_pill_overspeed)
            }
            SpeedState.CAUTION -> {
                tvFloatSpeed?.setTextColor(Color.parseColor("#FFB700"))
                floatingView?.setBackgroundResource(R.drawable.bg_floating_pill)
            }
            SpeedState.SAFE -> {
                tvFloatSpeed?.setTextColor(Color.parseColor("#00FF88"))
                floatingView?.setBackgroundResource(R.drawable.bg_floating_pill)
            }
        }

        // Cập nhật khoảng cách camera
        if (camDist in 0..1500) {
            tvFloatCamDist?.text = "${camDist}m"
            tvFloatCamIcon?.text = when (camType) {
                "RED_LIGHT" -> "🚦"
                "EXPRESSWAY_BAN" -> "⛔"
                "LANE" -> "🚸"
                else -> "📷"
            }
        } else {
            tvFloatCamDist?.text = "--m"
            tvFloatCamIcon?.text = "📷"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dataReceiver)
        if (floatingView != null) {
            windowManager.removeView(floatingView)
        }
    }

    companion object {
        const val ACTION_UPDATE_HUD = "com.pmap.vn.ACTION_UPDATE_HUD"
        const val EXTRA_SPEED = "EXTRA_SPEED"
        const val EXTRA_LIMIT = "EXTRA_LIMIT"
        const val EXTRA_CAM_DIST = "EXTRA_CAM_DIST"
        const val EXTRA_CAM_TYPE = "EXTRA_CAM_TYPE"
        const val EXTRA_STATE = "EXTRA_STATE"
    }
}
