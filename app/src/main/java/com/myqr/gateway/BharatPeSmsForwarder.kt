package com.myqr.gateway

import android.content.Context
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends ONLY BharatPe payment-received SMS/RCS text to your Hostinger server.
 * OTP/login/password messages are ignored locally and never posted.
 */
object BharatPeSmsForwarder {

    fun looksLikeBharatPePayment(sender: String, body: String): Boolean {
        val hay = (sender + " " + body).lowercase()

        val blocked = listOf(
            "otp", "one time password", "one-time password", "verification code",
            "login code", "password", "pin", "do not share", "otp is", "code is"
        )
        if (blocked.any { hay.contains(it) }) return false

        val hasBrand = hay.contains("bharatpe") || hay.contains("bharat pe")
        val hasReceiveWord = Regex("\\b(received|credited|added)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasAmount = Regex("(?:rs\\.?|inr|₹)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasMerchantContext = hay.contains("bharatpe qr") || hay.contains("bharatpe account") || hay.contains("paymentdashboard") || hay.contains("qr")

        return hasBrand && hasReceiveWord && hasAmount && hasMerchantContext
    }

    fun postBharatPeSms(context: Context, sender: String, body: String, receivedAt: Long, sourceType: String) {
        if (!looksLikeBharatPePayment(sender, body)) return

        val smsPushUrl = ConfigStore.getSmsPushUrl(context)
        val deviceToken = ConfigStore.getDeviceToken(context)
        if (!smsPushUrl.startsWith("https://") || deviceToken.isBlank()) return

        Thread {
            try {
                val payload = JSONObject()
                    .put("sender", sender)
                    .put("body", body)
                    .put("received_at", receivedAt)
                    .put("source_type", sourceType)

                val conn = (URL(smsPushUrl).openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-device-token", deviceToken)
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader()?.use { it.readText() }
                conn.disconnect()
            } catch (_: Throwable) {
                // Private merchant app: failed events can be retried in a future version if needed.
            }
        }.start()
    }
}
