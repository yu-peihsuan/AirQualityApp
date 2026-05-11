package com.example.airquality

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TokenManager {

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences("fcm", Context.MODE_PRIVATE)
            .edit().putString("token", token).apply()
        Log.d("FCM", "Token 已更新：$token")
    }

    fun getToken(context: Context): String? {
        return context.getSharedPreferences("fcm", Context.MODE_PRIVATE)
            .getString("token", null)
    }

    /** App 啟動時呼叫，取得目前 Token 並存起來 */
    fun fetchAndSaveToken(context: Context) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            saveToken(context, token)
        }
    }

    /**
     * GPS 取得縣市後呼叫，將 Token + 縣市上傳到後端。
     * 後端用這份清單決定推播給哪些裝置。
     */
    fun uploadTokenWithCounty(
        context: Context,
        county: String,
        lat: Double? = null,
        lng: Double? = null
    ) {
        val token = getToken(context) ?: return
        val prefs = context.getSharedPreferences("health_profile", Context.MODE_PRIVATE)
        val ageGroup   = prefs.getString("health_age_group", "") ?: ""
        val conditions = buildString {
            prefs.getString("health_conditions", "")?.let { if (it.isNotEmpty()) append(it) }
            if (ageGroup.contains("18歲以下")) append(",18歲以下")
            if (ageGroup.contains("65"))       append(",65歲以上")
        }.trimStart(',')

        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.apiService.registerFcmToken(
                    FcmTokenRequest(
                        token      = token,
                        county     = county,
                        lat        = lat,
                        lng        = lng,
                        conditions = conditions
                    )
                )
                Log.d("FCM", "Token 已上傳，縣市：$county")
            } catch (e: Exception) {
                Log.e("FCM", "Token 上傳失敗：${e.message}")
            }
        }
    }
}
