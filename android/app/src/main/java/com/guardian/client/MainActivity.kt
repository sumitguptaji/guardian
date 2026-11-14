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

    private val perms = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

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
        val status = TextView(this).apply { text = "Guardian setup" }

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
                Scheduler.scheduleAll(this@MainActivity)   // re-arm with the new daily hour
                status.text = "Saved and reports rescheduled (daily at ${cfg.dailyHour}:00)."
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
                // Direct "allow?" dialog first; fall back to the battery list, then
                // to this app's details page - one of these exists on every device.
                val intents = listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName"))
                )
                val opened = intents.any {
                    try { startActivity(it); true } catch (_: Exception) { false }
                }
                status.text = if (opened) "Opened battery settings - set Guardian to unrestricted."
                              else "Open Settings > Apps > Guardian > Battery and set it to unrestricted."
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
                Scheduler.scheduleAll(this@MainActivity)
                status.text = "Started. Daily + 4-hourly reports scheduled."
            }
        }

        val test = Button(this).apply {
            text = "Send test report now"
            setOnClickListener {
                status.text = "Sending test report..."
                Thread {
                    val cfg = Config(this@MainActivity)
                    val msg = try {
                        Collectors.collectAll(this@MainActivity)
                        val now = System.currentTimeMillis()
                        val pdf = ReportBuilder.build(this@MainActivity,
                            "Test report", now - 24 * 3600_000L, now)
                        Mailer.send(cfg, "Lockkout - TEST report",
                            "This is a manual test from Lockkout.", pdf)
                        pdf.delete()
                        "Test report sent to ${cfg.toEmail}. Check inbox/spam."
                    } catch (e: Exception) {
                        "Send FAILED: ${e.javaClass.simpleName}: ${e.message}"
                    }
                    runOnUiThread { status.text = msg }
                }.start()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 50, 40, 50)
            addView(TextView(this@MainActivity).apply { text = "Lockkout"; textSize = 24f })
            listOf(host, port, user, pass, from, to, hour).forEach(::addView)
            addView(smsBody); addView(save); addView(grant)
            addView(notifAccess); addView(battery); addView(start); addView(test); addView(status)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
