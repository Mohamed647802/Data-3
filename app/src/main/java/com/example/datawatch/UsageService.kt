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

    // Power saving: 30s when the screen is on, 2 min when it is off (no wake-ups)
    private val tick = object : Runnable {
        override fun run() {
            update()
            h.postDelayed(this, if (screenOn) 30_000L else 120_000L)
        }
    }

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            screenOn = i.action == Intent.ACTION_SCREEN_ON
            h.removeCallbacks(tick)
            if (screenOn) h.post(tick) else h.postDelayed(tick, 120_000L)
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
        if (!Usage.prefs(this).getBoolean("enabled", false)) {
            stopSelf()
            return START_NOT_STICKY
        }
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

    private fun line(nameRes: Int, used: Long, limit: Long): String {
        val name = getString(nameRes)
        return if (limit > 0) {
            name + ": " + fmt(used) + " / " + fmt(limit) + " (" + Usage.pct(used, limit) + "%)"
        } else {
            name + ": " + fmt(used)
        }
    }

    private fun buildLive(s: Snapshot): Notification {
        val wl = line(R.string.label_wifi, s.wifi, s.wifiLimit)
        val ml = line(R.string.label_mobile, s.mobile, s.mobileLimit)
        val top = maxOf(Usage.pct(s.wifi, s.wifiLimit), Usage.pct(s.mobile, s.mobileLimit))
        val b = NotificationCompat.Builder(this, "live")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent())
            .setContentTitle(wl)
            .setContentText(ml)
            .setStyle(NotificationCompat.BigTextStyle().bigText(wl + "\n" + ml))
        if (s.wifiLimit > 0 || s.mobileLimit > 0) {
            b.setProgress(100, top.coerceAtMost(100), false)
        }
        return b.build()
    }

    private fun update() {
        val s = Usage.snapshot(this)
        if (!s.running) {
            stopSelf()
            return
        }
        nm.notify(1, buildLive(s))
        checkLimit("Wifi", R.string.label_wifi, s.wifi, s.wifiLimit, 2)
        checkLimit("Mob", R.string.label_mobile, s.mobile, s.mobileLimit, 4)
    }

    private fun checkLimit(key: String, nameRes: Int, used: Long, limit: Long, idBase: Int) {
        if (limit <= 0) return
        val p = Usage.prefs(this)
        val pct = Usage.pct(used, limit)
        val warn = p.getInt("warn", 80)
        val name = getString(nameRes)
        if (pct >= 100) {
            if (!p.getBoolean("hitLim" + key, false)) {
                alert(
                    idBase,
                    getString(R.string.limit_title),
                    getString(R.string.limit_text, name, fmt(used), fmt(limit))
                )
                p.edit()
                    .putBoolean("hitLim" + key, true)
                    .putBoolean("hitWarn" + key, true)
                    .apply()
            }
        } else if (pct >= warn && !p.getBoolean("hitWarn" + key, false)) {
            alert(
                idBase + 1,
                getString(R.string.warn_title),
                getString(R.string.warn_text, name, pct, fmt(used), fmt(limit))
            )
            p.edit().putBoolean("hitWarn" + key, true).apply()
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
