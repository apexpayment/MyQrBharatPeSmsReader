package com.myqr.gateway

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Notification fallback for BharatPe RCS / Google Messages.
 * It forwards only text that matches BharatPe payment-received pattern.
 */
class MyQrNotificationListener : NotificationListenerService() {

    private val bharatPeMessagePackages = setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.miui.mms"
    )

    private val merchantAppPackages = setOf(
        "com.paytm.business",
        "net.one97.paytm.business",
        "com.phonepe.app.business",
        "com.google.android.apps.nbu.paisa.user"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return

        if (bharatPeMessagePackages.contains(pkg)) {
            captureBharatPeMessageNotification(sbn, "rcs_notification")
            return
        }

        // Optional diagnostic for merchant-app notifications, using a separate endpoint if you saved it.
        if (merchantAppPackages.contains(pkg)) {
            captureMerchantNotification(sbn, "posted")
        }
    }

    private fun captureBharatPeMessageNotification(sbn: StatusBarNotification, sourceType: String) {
        try {
            val n = sbn.notification ?: return
            val extras = n.extras ?: Bundle()

            val title = getCs(extras, Notification.EXTRA_TITLE, "android.title")
            val text = getCs(extras, Notification.EXTRA_TEXT, "android.text")
            val bigText = getCs(extras, Notification.EXTRA_BIG_TEXT, "android.bigText")
            val textLines = getLines(extras, Notification.EXTRA_TEXT_LINES, "android.textLines")

            val combined = StringBuilder()
            if (text.isNotBlank()) combined.append(text).append("\n")
            if (bigText.isNotBlank()) combined.append(bigText).append("\n")
            for (i in 0 until textLines.length()) combined.append(textLines.optString(i)).append("\n")

            val body = combined.toString().trim()
            if (body.isBlank()) return

            BharatPeSmsForwarder.postBharatPeSms(applicationContext, title, body, sbn.postTime, sourceType)
        } catch (_: Throwable) {}
    }

    private fun captureMerchantNotification(sbn: StatusBarNotification, reason: String) {
        val notificationPushUrl = ConfigStore.getNotificationPushUrl(applicationContext)
        val deviceToken = ConfigStore.getDeviceToken(applicationContext)
        if (!notificationPushUrl.startsWith("https://") || deviceToken.isBlank()) return

        try {
            val n = sbn.notification ?: return
            val extras = n.extras ?: Bundle()
            val payload = JSONObject()
                .put("app_package", sbn.packageName ?: "")
                .put("title", getCs(extras, Notification.EXTRA_TITLE, "android.title"))
                .put("text", getCs(extras, Notification.EXTRA_TEXT, "android.text"))
                .put("big_text", getCs(extras, Notification.EXTRA_BIG_TEXT, "android.bigText"))
                .put("text_lines", getLines(extras, Notification.EXTRA_TEXT_LINES, "android.textLines"))
                .put("notification_key", sbn.key ?: "")
                .put("notification_id", sbn.id)
                .put("posted_at", sbn.postTime)
                .put("capture_reason", reason)

            Thread { postJson(notificationPushUrl, deviceToken, payload) }.start()
        } catch (_: Throwable) {}
    }

    private fun postJson(url: String, token: String, payload: JSONObject) {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-device-token", token)
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }
            conn.disconnect()
        } catch (_: Throwable) {}
    }

    private fun getCs(extras: Bundle, vararg keys: String): String {
        keys.forEach { key ->
            try {
                val value = extras.get(key)
                if (value is CharSequence && value.toString().trim().isNotEmpty()) return value.toString()
                if (value is String && value.trim().isNotEmpty()) return value
            } catch (_: Throwable) {}
        }
        return ""
    }

    private fun getLines(extras: Bundle, vararg keys: String): JSONArray {
        val arr = JSONArray()
        keys.forEach { key ->
            try {
                val value = extras.get(key)
                when (value) {
                    is Array<*> -> value.forEach { if (it != null) arr.put(it.toString()) }
                    is Iterable<*> -> value.forEach { if (it != null) arr.put(it.toString()) }
                    is CharSequence -> if (value.toString().trim().isNotEmpty()) arr.put(value.toString())
                }
            } catch (_: Throwable) {}
        }
        return arr
    }
}
