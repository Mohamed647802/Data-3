package com.example.datawatch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.Formatter
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val h = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { render(); h.postDelayed(this, 5000) }
    }
    private lateinit var tvStatus: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnPerm: Button
    private lateinit var etLimit: EditText
    private lateinit var etWarn: EditText
    private lateinit var etDay: EditText
    private lateinit var rbGb: RadioButton
    private lateinit var rbMb: RadioButton
    private lateinit var rgSrc: RadioGroup

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tvStatus); progress = findViewById(R.id.progress)
        btnPerm = findViewById(R.id.btnPerm); etLimit = findViewById(R.id.etLimit)
        etWarn = findViewById(R.id.etWarn); etDay = findViewById(R.id.etDay)
        rbGb = findViewById(R.id.rbGb); rbMb = findViewById(R.id.rbMb)
        rgSrc = findViewById(R.id.rgSrc)

        val p = Usage.prefs(this)
        val lim = p.getFloat("limit", 0f)
        if (lim > 0) etLimit.setText(if (lim % 1f == 0f) lim.toInt().toString() else lim.toString())
        etWarn.setText(p.getInt("warn", 80).toString())
        etDay.setText(p.getInt("day", 1).toString())
        if (p.getInt("unit", 1) == 1) rbGb.isChecked = true else rbMb.isChecked = true
        rgSrc.check(when (p.getInt("src", Usage.SRC_BOTH)) {
            Usage.SRC_WIFI -> R.id.rbWifi; Usage.SRC_MOBILE -> R.id.rbMobile; else -> R.id.rbBoth
        })

        btnPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnRestart).setOnClickListener {
            Usage.prefs(this).edit().putLong("custom", System.currentTimeMillis())
                .remove("hitWarn").remove("hitLim").apply()
            startMonitor()
            Toast.makeText(this, "تم التصفير", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            Usage.prefs(this).edit().putBoolean("enabled", false).apply()
            stopService(Intent(this, UsageService::class.java))
            Toast.makeText(this, "تم الإيقاف", Toast.LENGTH_SHORT).show()
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun save() {
        val p = Usage.prefs(this)
        val day = (etDay.text.toString().toIntOrNull() ?: 1).coerceIn(1, 28)
        val ed = p.edit()
            .putFloat("limit", etLimit.text.toString().toFloatOrNull() ?: 0f)
            .putInt("unit", if (rbGb.isChecked) 1 else 0)
            .putInt("src", when (rgSrc.checkedRadioButtonId) {
                R.id.rbWifi -> Usage.SRC_WIFI; R.id.rbMobile -> Usage.SRC_MOBILE; else -> Usage.SRC_BOTH
            })
            .putInt("warn", (etWarn.text.toString().toIntOrNull() ?: 80).coerceIn(1, 99))
            .putInt("day", day)
            .putBoolean("enabled", true)
            .remove("hitWarn").remove("hitLim")
        if (day != p.getInt("day", 1)) ed.putLong("custom", 0L)
        ed.apply()
        startMonitor()
        Toast.makeText(this, "تم الحفظ والتشغيل", Toast.LENGTH_SHORT).show()
    }

    private fun startMonitor() {
        if (Usage.hasAccess(this))
            ContextCompat.startForegroundService(this, Intent(this, UsageService::class.java))
        render()
    }

    private fun render() {
        val ok = Usage.hasAccess(this)
        btnPerm.visibility = if (ok) android.view.View.GONE else android.view.View.VISIBLE
        if (!ok) { tvStatus.text = "فعّل صلاحية الوصول لبيانات الاستخدام من الزر بالأسفل ثم اختر التطبيق وفعّله."; return }
        val s = Usage.snapshot(this)
        fun f(x: Long) = Formatter.formatFileSize(this, x)
        var t = "واي فاي: ${f(s.wifi)}\nداتا: ${f(s.mobile)}\nالمحسوب: ${f(s.counted)}"
        if (s.limit > 0) t += " من ${f(s.limit)} (${s.pct.toInt()}%)"
        tvStatus.text = t
        progress.progress = s.pct.toInt().coerceAtMost(100)
    }

    override fun onResume() { super.onResume(); h.post(refresh) }
    override fun onPause() { super.onPause(); h.removeCallbacks(refresh) }
}
