package com.guardian.client

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * All settings and setup in one screen (no config files to edit by hand).
 * Everything is visible on the phone - this app has no hidden mode.
 */
class MainActivity : AppCompatActivity() {

    private val perms = buildList {
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = Config(this)

        fun field(hint: String, value: String) = EditText(this).apply {
            this.hint = hint; setText(value)
        }

        val host = field("SMTP host", cfg.smtpHost)
        val port = field("SMTP port", cfg.smtpPort)
        val user = field("SMTP login", cfg.smtpUser)
        val pass = field("SMTP password / API key", cfg.smtpPass).apply {
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or
                        android.text.InputType.TYPE_CLASS_TEXT
        }
        val from = field("From email (verified sender)", cfg.fromEmail)
        val to = field("Send reports to", cfg.toEmail)
        val hour = field("Daily report hour (0-23, IST)", cfg.dailyHour.toString())
        val smsBody = CheckBox(this).apply {
            text = "Include full SMS text"; isChecked = cfg.sendSmsBody
        }
        val status = TextView(this).apply {
            text = "Monitoring is always visible on this phone."
        }

        val save = Button(this).apply {
            text = "Save settings"
            setOnClickListener {
                cfg.smtpHost = host.text.toString().trim()
                cfg.smtpPort = port.text.toString().trim()
                cfg.smtpUser = user.text.toString().trim()
                cfg.smtpPass = pass.text.toString().trim()
                cfg.fromEmail = from.text.toString().trim()
                cfg.toEmail = to.text.toString().trim()
                cfg.dailyHour = hour.text.toString().toIntOrNull() ?: 8
                cfg.sendSmsBody = smsBody.isChecked
                status.text = "Saved."
            }
        }
        val grant = Button(this).apply {
            text = "Grant call / SMS / location permissions"
            setOnClickListener { ActivityCompat.requestPermissions(this@MainActivity, perms, 1) }
        }
        val notifAccess = Button(this).apply {
            text = "Enable notification access (for app message previews)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        val battery = Button(this).apply {
            text = "Allow to run in background (battery)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")))
            }
        }
        val start = Button(this).apply {
            text = "Start monitoring"
            setOnClickListener {
                val missing = perms.any {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                        PackageManager.PERMISSION_GRANTED
                }
                if (missing) { status.text = "Grant permissions first."; return@setOnClickListener }
                ContextCompat.startForegroundService(
                    this@MainActivity, Intent(this@MainActivity, MonitorService::class.java))
                Scheduler.scheduleAll(this@MainActivity)
                status.text = "Monitoring started. Daily + 4-hourly reports scheduled."
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 50, 40, 50)
            addView(TextView(this@MainActivity).apply { text = "Guardian"; textSize = 24f })
            listOf(host, port, user, pass, from, to, hour).forEach(::addView)
            addView(smsBody); addView(save); addView(grant)
            addView(notifAccess); addView(battery); addView(start); addView(status)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
