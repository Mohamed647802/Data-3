package com.example.datawatch

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Process
import java.util.Calendar

data class Snapshot(
    val access: Boolean,
    val wifi: Long,
    val mobile: Long,
    val counted: Long,
    val limit: Long,
    val start: Long
) {
    val pct: Double
        get() = if (limit > 0) counted * 100.0 / limit else 0.0
}

object Usage {
    const val SRC_WIFI = 0
    const val SRC_MOBILE = 1
    const val SRC_BOTH = 2

    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("datawatch", Context.MODE_PRIVATE)

    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            c.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun cycleStart(c: Context): Long {
        val p = prefs(c)
        val custom = p.getLong("custom", 0L)
        if (custom > 0) return custom
        val day = p.getInt("day", 1).coerceIn(1, 28)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val today = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        if (today < day) cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        return cal.timeInMillis
    }

    fun limitBytes(c: Context): Long {
        val p = prefs(c)
        val v = p.getFloat("limit", 0f)
        val mult = if (p.getInt("unit", 0) == 1) 1_000_000_000.0 else 1_000_000.0
        return (v * mult).toLong()
    }

    private fun query(c: Context, type: Int, start: Long, end: Long): Long {
        return try {
            val nsm = c.getSystemService(NetworkStatsManager::class.java)
            val b = nsm.querySummaryForDevice(type, null, start, end)
            b.rxBytes + b.txBytes
        } catch (e: Exception) {
            0L
        }
    }

    fun snapshot(c: Context): Snapshot {
        val start = cycleStart(c)
        if (!hasAccess(c)) return Snapshot(false, 0, 0, 0, limitBytes(c), start)
        val end = System.currentTimeMillis() + 60_000
        val wifi = query(c, ConnectivityManager.TYPE_WIFI, start, end)
        val mobile = query(c, ConnectivityManager.TYPE_MOBILE, start, end)
        val counted = when (prefs(c).getInt("src", SRC_BOTH)) {
            SRC_WIFI -> wifi
            SRC_MOBILE -> mobile
            else -> wifi + mobile
        }
        return Snapshot(true, wifi, mobile, counted, limitBytes(c), start)
    }
}
