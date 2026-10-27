package com.example.airquality

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.airquality.data.AppContainer
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // onNewToken 沒有生命週期範圍可用，且上傳要在 Service 結束後仍能跑完
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "空氣品質警報"
        val body  = remoteMessage.notification?.body  ?: remoteMessage.data["body"]  ?: ""
        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        // Token 更新時存本機並立即上傳至後端。
        // 這裡沒有新的縣市資訊，county 留空由 FcmTokenRepository 補上「上次記住的縣市」——
        // 否則使用者在下次定位或手動選地區之前，都收不到任何縣市推播。
        AppContainer.fcmToken.saveToken(token)
        scope.launch { AppContainer.fcmToken.uploadRegistration() }
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = ensureChannel(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)   // Android 7 以下沒有頻道，靠這個決定是否跳出
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        /** 後端推播指定的頻道（fcm_sender.py 的 ANDROID_CHANNEL_ID），兩邊要一致。 */
        const val CHANNEL_ID = "air_quality_alerts"

        /**
         * 建立「空氣品質警報」高重要度頻道。
         *
         * App 在背景時，推播由系統直接顯示、不經過 onMessageReceived，系統會用
         * 推播指定（或 Manifest 預設）的頻道；頻道不存在就退回 Firebase 自建的
         * 「其他」頻道——預設重要度、鎖定時不點亮螢幕，使用者要解鎖才看得到。
         * 所以必須在 App 啟動時就建好，不能等到第一則前景推播才建。
         * 重複呼叫是安全的：同 ID 的頻道已存在時系統不會變更它。
         */
        fun ensureChannel(context: Context): NotificationManager {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "空氣品質警報",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }
                manager.createNotificationChannel(channel)
            }
            return manager
        }
    }
}
