package com.example.datawatch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val h = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            h.postDelayed(this, 2000)
        }
    }

    private lateinit var tvState: TextView
    private lateinit var tvWifi: TextView
    private lateinit var tvMobile: TextView
    private lateinit var tvWifiLimit: TextView
    private lateinit var tvMobileLimit: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvSince: TextView
    private lateinit var pbWifi: ProgressBar
    private lateinit var pbMobile: ProgressBar
    private lateinit var etWifi: EditText
    private lateinit var etMobile: EditText
    private lateinit var etWarn: EditText
    private lateinit var rbWifiMb: RadioButton
    private lateinit var rbWifiGb: RadioButton
    private lateinit var rbMobMb: RadioButton
    private lateinit var rbMobGb: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvState = findViewById(R.id.tvState)
        tvWifi = findViewById(R.id.tvWifi)
        tvMobile = findViewById(R.id.tvMobile)
        tvWifiLimit = findViewById(R.id.tvWifiLimit)
        tvMobileLimit = findViewById(R.id.tvMobileLimit)
        tvTotal = findViewById(R.id.tvTotal)
        tvSince = findViewById(R.id.tvSince)
        pbWifi = findViewById(R.id.pbWifi)
        pbMobile = findViewById(R.id.pbMobile)
        etWifi = findViewById(R.id.etWifi)
        etMobile = findViewById(R.id.etMobile)
        etWarn = findViewById(R.id.etWarn)
        rbWifiMb = findViewById(R.id.rbWifiMb)
        rbWifiGb = findViewById(R.id.rbWifiGb)
        rbMobMb = findViewById(R.id.rbMobMb)
        rbMobGb = findViewById(R.id.rbMobGb)

        val p = Usage.prefs(this)
        fill(etWifi, p.getFloat("wifiV", 0f))
        fill(etMobile, p.getFloat("mobV", 0f))
        if (p.getInt("wifiU", 1) == 1) rbWifiGb.isChecked = true else rbWifiMb.isChecked = true
        if (p.getInt("mobU", 1) == 1) rbMobGb.isChecked = true else rbMobMb.isChecked = true
        etWarn.setText(p.getInt("warn", 80).toString())

        findViewById<TextView>(R.id.btnStart).setOnClickListener { start() }
        findViewById<TextView>(R.id.btnStop).setOnClickListener { stop() }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
    }

    private fun fill(et: EditText, v: Float) {
        if (v > 0f) {
            et.setText(if (v % 1f == 0f) v.toInt().toString() else v.toString())
        }
    }

    private fun saveInputs() {
        val p = Usage.prefs(this)
        val wv = etWifi.text.toString().toFloatOrNull() ?: 0f
        val wu = if (rbWifiGb.isChecked) 1 else 0
        val mv = etMobile.text.toString().toFloatOrNull() ?: 0f
        val mu = if (rbMobGb.isChecked) 1 else 0
        val warn = (etWarn.text.toString().toIntOrNull() ?: 80).coerceIn(1, 99)
        val changed = wv != p.getFloat("wifiV", 0f) ||
            wu != p.getInt("wifiU", 1) ||
            mv != p.getFloat("mobV", 0f) ||
            mu != p.getInt("mobU", 1) ||
            warn != p.getInt("warn", 80)
        val ed = p.edit()
            .putFloat("wifiV", wv)
            .putInt("wifiU", wu)
            .putFloat("mobV", mv)
            .putInt("mobU", mu)
            .putInt("warn", warn)
        if (changed) {
            ed.remove("hitLimWifi").remove("hitWarnWifi")
                .remove("hitLimMob").remove("hitWarnMob")
        }
        ed.apply()
    }

    private fun start() {
        saveInputs()
        Usage.begin(this)
        ContextCompat.startForegroundService(this, Intent(this, UsageService::class.java))
        Toast.makeText(this, R.string.toast_started, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun stop() {
        Usage.end(this)
        stopService(Intent(this, UsageService::class.java))
        Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun f(x: Long): String = Formatter.formatFileSize(this, x)

    private fun limitText(limit: Long): String {
        return if (limit > 0) getString(R.string.of_limit, f(limit)) else getString(R.string.no_limit)
    }

    private fun render() {
        val s = Usage.snapshot(this)
        tvState.text = getString(if (s.running) R.string.state_on else R.string.state_off)
        tvState.setTextColor(Color.parseColor(if (s.running) "#66BB6A" else "#EF5350"))
        tvWifi.text = f(s.wifi)
        tvMobile.text = f(s.mobile)
        tvWifiLimit.text = limitText(s.wifiLimit)
        tvMobileLimit.text = limitText(s.mobileLimit)
        pbWifi.progress = Usage.pct(s.wifi, s.wifiLimit).coerceAtMost(100)
        pbMobile.progress = Usage.pct(s.mobile, s.mobileLimit).coerceAtMost(100)
        tvTotal.text = f(s.wifi + s.mobile)
        if (s.startTime > 0) {
            val t = SimpleDateFormat("HH:mm  dd/MM", Locale.getDefault()).format(Date(s.startTime))
            tvSince.text = getString(R.string.since_label, t)
        } else {
            tvSince.text = getString(R.string.not_started)
        }
    }

    override fun onResume() {
        super.onResume()
        h.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(refresh)
        saveInputs()
    }
}
