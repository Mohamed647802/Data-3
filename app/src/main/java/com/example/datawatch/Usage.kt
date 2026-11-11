package com.example.datawatch

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Process
import java.util.Calendar

data class Snapshot(
    val access: Boolean, val wifi: Long, val mobile: Long,
    val counted: Long, val limit: Long, val start: Long
) {
    val pct: Double get() = if (limit > 0) counted * 100.0 / limit else 0.0
}

object Usage {
    const val SRC_WIFI = 0
    const val SRC_MOBILE = 1
    const val SRC_BOTH = 2

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("datawatch", Context.MODE_PRIVATE)

    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** بداية الدورة: إما وقت التصفير اليدوي أو يوم الشهر المخ
