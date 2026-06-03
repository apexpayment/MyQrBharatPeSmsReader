package com.myqr.gateway

import android.content.Context

object ConfigStore {
    private const val PREF = "myqr_bharatpe_config"
    private const val KEY_SMS_URL = "sms_push_url"
    private const val KEY_NOTIFICATION_URL = "notification_push_url"
    private const val KEY_DEVICE_TOKEN = "device_token"

    fun getSmsPushUrl(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_SMS_URL, "")?.trim().orEmpty()

    fun getNotificationPushUrl(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_NOTIFICATION_URL, "")?.trim().orEmpty()

    fun getDeviceToken(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_DEVICE_TOKEN, "")?.trim().orEmpty()

    fun save(context: Context, smsUrl: String, notificationUrl: String, deviceToken: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_SMS_URL, smsUrl.trim())
            .putString(KEY_NOTIFICATION_URL, notificationUrl.trim())
            .putString(KEY_DEVICE_TOKEN, deviceToken.trim())
            .apply()
    }

    fun isReady(context: Context): Boolean =
        getSmsPushUrl(context).startsWith("https://") && getDeviceToken(context).isNotBlank()
}
