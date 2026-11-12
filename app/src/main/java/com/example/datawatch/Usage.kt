package com.example.datawatch

import android.content.Context
import android.content.SharedPreferences
import android.net.TrafficStats

data class Snapshot(
    val running: Boolean,
    val wifi: Long,
    val mobile: Long,
    val wifiLimit: Long,
    val mobileLimit: Long,
    val startTime: Long
)

object Usage {
    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("datawatch", Context.MODE_PRIVATE)

    // No special permission is needed any more (kept so BootReceiver still compiles)
    fun hasAccess(c: Context): Boolean = true

    fun pct(used: Long, limit: Long): Int =
        if (limit > 0) (used * 100 / limit).toInt() else 0

    private fun mobileNow(): Long {
        val r = TrafficStats.getMobileRxBytes()
        val t = TrafficStats.getMobileTxBytes()
        return if (r < 0 || t < 0) 0L else r + t
    }

    private fun totalNow(): Long {
        val r = TrafficStats.getTotalRxBytes()
        val t = TrafficStats.getTotalTxBytes()
        return if (r < 0 || t < 0) 0L else r + t
    }

    @Synchronized
    fun begin(c: Context) {
        prefs(c).edit()
            .putBoolean("enabled", true)
            .putLong("sessWifi", 0L)
            .putLong("sessMob", 0L)
            .putLong("lastTotal", totalNow())
            .putLong("lastMob", mobileNow())
            .putLong("startTime", System.currentTimeMillis())
            .remove("hitLimWifi")
            .remove("hitWarnWifi")
            .remove("hitLimMob")
            .remove("hitWarnMob")
            .apply()
    }

    @Synchronized
    fun end(c: Context) {
        sync(c)
        prefs(c).edit().putBoolean("enabled", false).apply()
    }

    // Adds the bytes used since the last read. Handles a reboot (counters restart at zero).
    @Synchronized
    private fun sync(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("enabled", false)) return
        val m = mobileNow()
        val t = totalNow()
        val lastM = p.getLong("lastMob", m)
        val lastT = p.getLong("lastTotal", t)
        val dM = if (m >= lastM) m - lastM else m
        val dT = if (t >= lastT) t - lastT else t
        val dW = (dT - dM).coerceAtLeast(0L)
        p.edit()
            .putLong("sessMob", p.getLong("sessMob", 0L) + dM)
            .putLong("sessWifi", p.getLong("sessWifi", 0L) + dW)
            .putLong("lastMob", m)
            .putLong("lastTotal", t)
            .apply()
    }

    private fun limitBytes(p: SharedPreferences, k: String): Long {
        val v = p.getFloat(k + "V", 0f)
        val mult = if (p.getInt(k + "U", 1) == 1) 1_000_000_000.0 else 1_000_000.0
        return (v * mult).toLong()
    }

    fun snapshot(c: Context): Snapshot {
        sync(c)
        val p = prefs(c)
        return Snapshot(
            p.getBoolean("enabled", false),
            p.getLong("sessWifi", 0L),
            p.getLong("sessMob", 0L),
            limitBytes(p, "wifi"),
            limitBytes(p, "mob"),
            p.getLong("startTime", 0L)
        )
    }
}
