package com.myqr.gateway

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var smsUrlInput: EditText
    private lateinit var notificationUrlInput: EditText
    private lateinit var tokenInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }
        root.addView(layout)

        val title = TextView(this).apply {
            text = "MyQR BharatPe SMS Reader"
            textSize = 23f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val info = TextView(this).apply {
            text = "Private merchant companion app. It forwards only BharatPe payment received SMS/RCS text. OTP/login/password messages are blocked locally."
            textSize = 15f
            setPadding(0, 18, 0, 18)
        }

        smsUrlInput = EditText(this).apply {
            hint = "SMS Push URL: https://your-domain.com/api/sms-push.php"
            setSingleLine(false)
            minLines = 2
            setText(ConfigStore.getSmsPushUrl(this@MainActivity))
        }
        notificationUrlInput = EditText(this).apply {
            hint = "Optional Notification URL: https://your-domain.com/api/notification-push.php"
            setSingleLine(false)
            minLines = 2
            setText(ConfigStore.getNotificationPushUrl(this@MainActivity))
        }
        tokenInput = EditText(this).apply {
            hint = "Device token from dashboard pairing"
            setSingleLine(false)
            minLines = 2
            setText(ConfigStore.getDeviceToken(this@MainActivity))
        }

        val saveBtn = Button(this).apply {
            text = "Save Server Settings"
            setOnClickListener {
                ConfigStore.save(
                    this@MainActivity,
                    smsUrlInput.text.toString(),
                    notificationUrlInput.text.toString(),
                    tokenInput.text.toString()
                )
                showStatus()
            }
        }

        val smsBtn = Button(this).apply {
            text = "Allow SMS Receive"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 23) {
                    requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1001)
                }
            }
        }

        val notifBtn = Button(this).apply {
            text = "Open Notification Access for RCS"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }

        val batteryBtn = Button(this).apply {
            text = "Open Battery Settings"
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                } catch (_: Throwable) {}
            }
        }

        val testBtn = Button(this).apply {
            text = "Send Test BharatPe SMS to Server"
            setOnClickListener {
                ConfigStore.save(
                    this@MainActivity,
                    smsUrlInput.text.toString(),
                    notificationUrlInput.text.toString(),
                    tokenInput.text.toString()
                )
                BharatPeSmsForwarder.postBharatPeSms(
                    this@MainActivity,
                    "BharatPe for Business",
                    "Received Rs.1.00 from VAIBHAV PANDEY on BharatPe QR. The funds are added to your BharatPe Account.",
                    System.currentTimeMillis(),
                    "manual_test"
                )
                status.text = "Test sent. Check Hostinger dashboard/device events."
            }
        }

        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, 20, 0, 0)
        }

        layout.addView(title)
        layout.addView(info)
        layout.addView(label("1) Paste pairing details"))
        layout.addView(smsUrlInput)
        layout.addView(notificationUrlInput)
        layout.addView(tokenInput)
        layout.addView(saveBtn)
        layout.addView(label("2) Permissions"))
        layout.addView(smsBtn)
        layout.addView(notifBtn)
        layout.addView(batteryBtn)
        layout.addView(label("3) Test"))
        layout.addView(testBtn)
        layout.addView(status)

        setContentView(root)
        showStatus()
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 17f
        setPadding(0, 24, 0, 8)
    }

    private fun showStatus() {
        val ready = ConfigStore.isReady(this)
        status.text = if (ready) {
            "Status: Ready. Now keep this phone online. BharatPe payment SMS with exact random amount will be forwarded."
        } else {
            "Status: Not ready. Paste https SMS Push URL + device token, then save."
        }
    }
}
