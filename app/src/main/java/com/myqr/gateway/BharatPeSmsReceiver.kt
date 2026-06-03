package com.myqr.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Reads ONLY new incoming standard SMS broadcasts.
 * It does NOT read old inbox history and does NOT forward OTP because the filter blocks OTP/code words.
 */
class BharatPeSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sender = messages.firstOrNull()?.displayOriginatingAddress
                ?: messages.firstOrNull()?.originatingAddress
                ?: ""
            val body = messages.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }.trim()
            val receivedAt = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            BharatPeSmsForwarder.postBharatPeSms(context.applicationContext, sender, body, receivedAt, "sms_broadcast")
        } catch (_: Throwable) {}
    }
}
