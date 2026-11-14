package com.guardian.client

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Lockkout setup. Visible, honestly-named monitoring app. Optional PIN gates the
 * settings screen; Device Admin makes the app resist uninstall. No disguise and
 * no hidden mode by design.
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
        if (cfg.pin.isNotBlank()) promptPin(cfg) else showSettings(cfg)
    }

    /** PIN gate shown before settings when a PIN is set. */
    private fun promptPin(cfg: Config) {
        val title = TextView(this).apply { text = "Lockkout"; textSize = 24f }
        val msg = TextView(this).apply { text = "Enter PIN" }
        val entry = EditText(this).apply {
            hint = "PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val unlock = Button(this).apply {
            text = "Unlock"
            setOnClickListener {
                if (entry.text.toString() == cfg.pin) showSettings(cfg)
                else msg.text = "Wrong PIN"
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 80, 40, 40)
            addView(title); addView(msg); addView(entry); addView(unlock)
        })
    }

    private fun showSettings(cfg: Config) {
        fun field(hint: String, value: String) = EditText(this).apply {
            this.hint = hint; setText(value)
        }

        val host = field("SMTP host", cfg.smtpHost)
        val port = field("SMTP port", cfg.smtpPort)
        val user = field("SMTP login", cfg.smtpUser)
        val pass = field("SMTP password / API key", cfg.smtpPass).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
        }
        val from = field("From email (verified sender)", cfg.fromEmail)
        val to = field("Send reports to", cfg.toEmail)
        val hour = field("Daily report hour (0-23, IST)", cfg.dailyHour.toString())
        val pin = field("Settings PIN (blank = no lock)", cfg.pin).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val smsBody = CheckBox(this).apply {
            text = "Include full SMS text"; isChecked = cfg.sendSmsBody
        }
        val status = TextView(this).apply { text = "Lockkout setup" }

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
                cfg.pin = pin.text.toString().trim()
                cfg.sendSmsBody = smsBody.isChecked
                Scheduler.scheduleAll(this@MainActivity)
                status.text = "Saved and rescheduled (daily at ${cfg.dailyHour}:00)."
            }
        }
        val grant = Button(this).apply {
            text = "Grant call / SMS / location permissions"
            setOnClickListener { ActivityCompat.requestPermissions(this@MainActivity, perms, 1) }
        }
        val notifAccess = Button(this).apply {
            text = "Enable notification access (for app message previews)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        val battery = Button(this).apply {
            text = "Allow to run in background (battery)"
            setOnClickListener {
                val intents = listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName"))
                )
                val opened = intents.any { try { startActivity(it); true } catch (_: Exception) { false } }
                status.text = if (opened) "Opened battery settings - set Lockkout to unrestricted."
                              else "Open Settings > Apps > Lockkout > Battery and set it to unrestricted."
            }
        }
        val admin = Button(this).apply {
            text = "Enable uninstall protection (device admin)"
            setOnClickListener {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val cn = ComponentName(this@MainActivity, AdminReceiver::class.java)
                if (dpm.isAdminActive(cn)) {
                    status.text = "Uninstall protection is already on."
                } else {
                    startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Prevents Lockkout from being uninstalled or stopped until an adult turns this off.")
                    })
                }
            }
        }
        val start = Button(this).apply {
            text = "Start monitoring"
            setOnClickListener {
                val missing = perms.any {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
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
                    val msg = try {
                        Collectors.collectAll(this@MainActivity)
                        val now = System.currentTimeMillis()
                        val pdf = ReportBuilder.build(this@MainActivity, "Test report", now - 24 * 3600_000L, now)
                        Mailer.send(cfg, "Lockkout - TEST report", "Manual test from Lockkout.", pdf)
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
            listOf(host, port, user, pass, from, to, hour, pin).forEach(::addView)
            addView(smsBody); addView(save); addView(grant); addView(notifAccess)
            addView(battery); addView(admin); addView(start); addView(test); addView(status)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
