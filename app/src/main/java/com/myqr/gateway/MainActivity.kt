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
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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
            text = "MyQR BharatPe SMS Reader v21"
            textSize = 23f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val info = TextView(this).apply {
            text = "Private merchant companion app. It forwards only BharatPe payment received SMS/RCS text. OTP/login/password messages are blocked locally. v21 supports direct 6-digit pairing code."
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
            hint = "Paste 6-digit pairing code OR device token"
            setSingleLine(false)
            minLines = 2
            setText(ConfigStore.getDeviceToken(this@MainActivity))
        }

        val pairBtn = Button(this).apply {
            text = "Pair Device Using 6-Digit Code"
            setOnClickListener { pairDevice() }
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
                showStatus("Saved. If third box is only 6 digits, tap Pair Device button first. Real push needs long device token.")
            }
        }

        val smsBtn = Button(this).apply {
            text = "Allow SMS Receive"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 23) {
                    requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 1001)
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
                status.text = "Test sending... wait 5 seconds, then tap Refresh Last Server Response."
            }
        }

        val refreshBtn = Button(this).apply {
            text = "Refresh Last Server Response"
            setOnClickListener { showStatus() }
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
        layout.addView(pairBtn)
        layout.addView(saveBtn)
        layout.addView(label("2) Permissions"))
        layout.addView(smsBtn)
        layout.addView(notifBtn)
        layout.addView(batteryBtn)
        layout.addView(label("3) Test"))
        layout.addView(testBtn)
        layout.addView(refreshBtn)
        layout.addView(status)

        setContentView(root)
        showStatus()
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 17f
        setPadding(0, 24, 0, 8)
    }

    private fun pairDevice() {
        val smsUrl = smsUrlInput.text.toString().trim()
        val code = tokenInput.text.toString().trim()
        if (!smsUrl.startsWith("https://")) {
            status.text = "Paste SMS Push URL first. Example: https://mama567matka.com/api/sms-push.php"
            return
        }
        if (!Regex("^\\d{6}$").matches(code)) {
            status.text = "Third box me 6-digit pairing code paste karo, then Pair Device dabao."
            return
        }

        val pairUrl = smsUrl.replace("/api/sms-push.php", "/api/device-pair.php")
        status.text = "Pairing... $pairUrl"

        Thread {
            try {
                val payload = JSONObject()
                    .put("pairing_code", code)
                    .put("device_name", Build.MANUFACTURER + " " + Build.MODEL)

                val conn = (URL(pairUrl).openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                val httpCode = conn.responseCode
                val stream = if (httpCode in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()

                val json = try { JSONObject(response) } catch (_: Throwable) { null }
                if (httpCode in 200..299 && json != null && json.optBoolean("status")) {
                    val token = json.optString("device_token")
                    val smsPush = json.optString("sms_push_url", smsUrl)
                    val notifPush = json.optString("push_url", notificationUrlInput.text.toString().trim())
                    ConfigStore.save(this, smsPush, notifPush, token)
                    runOnUiThread {
                        smsUrlInput.setText(smsPush)
                        notificationUrlInput.setText(notifPush)
                        tokenInput.setText(token)
                        status.text = "Paired successfully. Long device token saved. Now tap Allow SMS Receive + test."
                    }
                } else {
                    runOnUiThread { status.text = "Pairing failed HTTP $httpCode: $response" }
                }
            } catch (e: Throwable) {
                runOnUiThread { status.text = "Pairing error: ${e.message}" }
            }
        }.start()
    }

    private fun showStatus(extra: String = "") {
        val smsUrl = ConfigStore.getSmsPushUrl(this)
        val token = ConfigStore.getDeviceToken(this)
        val tokenLooksLong = token.length > 20
        val ready = smsUrl.startsWith("https://") && tokenLooksLong
        val last = BharatPeSmsForwarder.getLastServerResponse(this)
        status.text = buildString {
            append(if (ready) "Status: Ready. " else "Status: Not ready. ")
            if (!tokenLooksLong) append("Third box abhi device token nahi lag raha. 6-digit code hai to Pair Device button dabao. ")
            if (extra.isNotBlank()) append("\n$extra")
            if (last.isNotBlank()) append("\n\nLast server response:\n$last")
            append("\n\nNote: App old SMS history nahi padhta. Sirf new incoming BharatPe SMS/RCS forward hota hai.")
        }
    }
}
