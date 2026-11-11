package com.example.datawatch

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class UsageService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var screenOn = true
    private lateinit var nm: NotificationManager

    // موفر للطاقة: كل 30 ثانية والشاشة مفتوحة، وكل 5 دقائق (بدون إيقاظ الجهاز) والشاشة مقفولة
    private val tick = object : Runnable {
        override fun run() {
            update()
            h.postDelayed(this, if (screenOn) 30_000L else 300_000L)
        }
    }
    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            screenOn = i.action == Intent.ACTION_SCREEN_ON
            h.removeCallbacks(tick)
            if (screenOn) h.post(tick) else h.postDelayed(tick, 300_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createN
