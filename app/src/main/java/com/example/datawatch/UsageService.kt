package com.example.datawatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class UsageService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var screenOn = true
    private lateinit var nm: NotificationManager

    // Power saving: 30s when screen is on, 5 min when screen is off
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
        val live = NotificationChannel(
            "live", getString(R.string.ch_live), NotificationManager.IMPORTANCE_LOW
        )
        live.setShowBadge(false)
        nm.createNotificationChannel(live)
        val alerts = NotificationChannel(
            "alerts", getString(R.string.ch_alerts), NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(alerts)
        val f = IntentFilter()
        f.addAction(Intent.ACTION_SCREEN_ON)
        f.addAction(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(this, screenRx, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            1,
            buildLive(Usage.snapshot(this)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        h.removeCallbacks(tick)
        h.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(tick)
        try {
            unregisterReceiver(screenRx)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    private fun fmt(b: Long): String = Formatter.formatFileSize(this, b)

    private fun openIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildLive(s: Snapshot): Notification {
        val b = NotificationCompat.Builder(this, "live")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent())
        if (!s.access) {
            return b.setContentTitle(getString(R.string.need_access))
                .setContentText(getString(R.string.open_app_hint))
                .build()
        }
        if (s.limit > 0) {
            b.setContentTitle(fmt(s.counted) + " / " + fmt(s.limit) + "  (" + s.pct.toInt() + "%)")
            b.setProgress(100, s.pct.toInt().coerceAtMost(100), false)
        } else {
            b.setContentTitle(getString(R.string.usage_title, fmt(s.counted)))
        }
        b.setContentText(getString(R.string.wifi_data, fmt(s.wifi), fmt(s.mobile)))
        return b.build()
    }

    private fun update() {
        val s = Usage.snapshot(this)
        nm.notify(1, buildLive(s))
        if (!s.access || s.limit <= 0) return
        val p = Usage.prefs(this)
        val warn = p.getInt("warn", 80)
        if (s.pct >= 100) {
            if (p.getLong("hitLim", 0L) != s.start) {
                alert(
                    2,
                    getString(R.string.limit_title),
                    getString(R.string.limit_text, fmt(s.counted), fmt(s.limit))
                )
                p.edit().putLong("hitLim", s.start).putLong("hitWarn", s.start).apply()
            }
        } else if (s.pct >= warn && p.getLong("hitWarn", 0L) != s.start) {
            alert(
                3,
                getString(R.string.warn_title),
                getString(R.string.warn_text, s.pct.toInt(), fmt(s.counted), fmt(s.limit))
            )
            p.edit().putLong("hitWarn", s.start).apply()
        }
    }

    private fun alert(id: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(this, "alerts")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openIntent())
            .build()
        nm.notify(id, n)
    }
}
