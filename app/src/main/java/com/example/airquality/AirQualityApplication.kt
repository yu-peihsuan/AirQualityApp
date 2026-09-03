package com.example.airquality

import android.app.Application
import com.example.airquality.data.AppContainer
import kotlin.concurrent.thread

/**
 * App 層級的初始化進入點。
 *
 * [AppContainer] 與 [AuthManager] 都需要一個 Application Context，且必須在任何
 * API 呼叫之前備妥。
 * 放在 MainActivity 不夠：MyFirebaseMessagingService 的 onNewToken 可能在
 * 使用者開啟畫面之前就觸發並上傳 FCM Token，那時憑證必須已經可以取得。
 */
class AirQualityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Repository 一律在這裡建好，並且只持有 applicationContext；
        // ViewModel 以建構子預設值取用，畫面層不再自己傳 Context 進去。
        AppContainer.init(this)
        AuthManager.init(this)

        // 先在背景取得憑證。不做這件事程式仍然正確（攔截器會在第一個需要
        // 憑證的請求上臨時註冊），但那會讓首頁的第一次查詢多等一次來回。
        thread(isDaemon = true) { AuthManager.accessToken() }
    }
}
