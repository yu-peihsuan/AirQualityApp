package com.example.airquality

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.UUID

// ── 認證 API 資料結構 ─────────────────────────────────────────────────────────

data class DeviceRegisterRequest(
    @SerializedName("device_id") val deviceId: String
)

data class AuthResponse(
    val status: String = "",
    @SerializedName("access_token")  val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in")    val expiresIn: Int = 0
)

/**
 * 認證端點。刻意與 AirQualityApiService 分開，並使用另一個未掛
 * Interceptor／Authenticator 的 Retrofit 實體——否則續期請求本身
 * 會再次觸發續期流程，形成無窮遞迴。
 *
 * 回傳 Call 而非 suspend：這些呼叫是在 OkHttp 的攔截器執行緒上同步執行的。
 */
interface AuthApiService {
    @POST("api/auth/device")
    fun registerDevice(@Body request: DeviceRegisterRequest): Call<AuthResponse>

    @POST("api/auth/refresh")
    fun refresh(@Header("Authorization") authorization: String): Call<AuthResponse>
}

/**
 * 裝置匿名憑證的取得與保管。
 *
 * 後端所有寫入類端點（回報、AI 建議、推播設定）都需要 Bearer token。
 * 本 App 沒有登入畫面，改為在首次使用時以裝置識別碼向後端註冊換取 JWT，
 * 之後由 [AuthInterceptor] 自動附加在每個請求上、[TokenAuthenticator]
 * 在後端回 401 時自動續期。整個流程對畫面層是透明的。
 */
object AuthManager {

    private const val TAG = "Auth"
    private const val PREFS = "auth"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_FALLBACK_ID = "fallback_device_id"

    private lateinit var appContext: Context

    // 同一時間只允許一個執行緒註冊或續期，避免多個並行請求各自打一次認證端點
    private val lock = Any()

    private val api: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }

    /** 由 [AirQualityApplication] 在 App 啟動時呼叫。 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 取得目前可用的 access token；尚未註冊過就先註冊。取不到時回 null。 */
    fun accessToken(): String? {
        synchronized(lock) {
            return prefs().getString(KEY_ACCESS, null) ?: registerLocked()
        }
    }

    /**
     * 後端回 401 後換發新的 access token。
     *
     * [staleToken] 是失敗的那次請求所帶的憑證。若在等待鎖的期間已有其他
     * 執行緒換過 token，就直接沿用結果，不必重複續期。
     */
    fun renewAccessToken(staleToken: String?): String? {
        synchronized(lock) {
            val current = prefs().getString(KEY_ACCESS, null)
            if (current != null && current != staleToken) return current
            // 續期失敗（refresh token 過期，或後端重啟後裝置紀錄已不存在）
            // 就退回重新註冊，使用者不會察覺
            return refreshLocked() ?: registerLocked()
        }
    }

    /** 清除本機憑證，下次呼叫 API 時會重新註冊。 */
    fun clear() {
        synchronized(lock) {
            prefs().edit().remove(KEY_ACCESS).remove(KEY_REFRESH).apply()
        }
    }

    // ── 以下皆須在持有 lock 的情況下呼叫 ──────────────────────────────────

    private fun refreshLocked(): String? {
        val refreshToken = prefs().getString(KEY_REFRESH, null) ?: return null
        return try {
            val resp = api.refresh("Bearer $refreshToken").execute()
            if (resp.isSuccessful) {
                resp.body()?.let { save(it) }
            } else {
                Log.i(TAG, "憑證續期遭拒（${resp.code()}），改為重新註冊")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "憑證續期失敗：${e.message}")
            null
        }
    }

    private fun registerLocked(): String? {
        return try {
            val resp = api.registerDevice(DeviceRegisterRequest(deviceId())).execute()
            if (resp.isSuccessful) {
                resp.body()?.let { save(it) }
            } else {
                Log.e(TAG, "裝置註冊失敗：${resp.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "裝置註冊失敗：${e.message}")
            null
        }
    }

    private fun save(body: AuthResponse): String? {
        if (body.accessToken.isEmpty()) return null
        prefs().edit().apply {
            putString(KEY_ACCESS, body.accessToken)
            body.refreshToken?.let { putString(KEY_REFRESH, it) }
        }.apply()
        return body.accessToken
    }

    /**
     * 送給後端的裝置識別碼。後端會再做一次雜湊才儲存，不會留下原始值。
     * 少數裝置取不到 ANDROID_ID，改用本機產生並保存的隨機值。
     */
    @SuppressLint("HardwareIds")
    private fun deviceId(): String {
        val androidId = Settings.Secure.getString(
            appContext.contentResolver, Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrBlank()) return androidId

        prefs().getString(KEY_FALLBACK_ID, null)?.let { return it }
        return UUID.randomUUID().toString().also {
            prefs().edit().putString(KEY_FALLBACK_ID, it).apply()
        }
    }
}

/** 為每個 API 請求補上 Authorization header；認證端點本身不帶。 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.startsWith("/api/auth/")) {
            return chain.proceed(request)
        }
        // 取不到憑證（例如首次啟動時剛好沒有網路）仍照常送出：
        // 公開端點不受影響，需認證的端點會拿到 401，由使用者重試時再補上。
        val token = AuthManager.accessToken() ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build()
        )
    }
}

/** 後端回 401 時自動換發憑證並重送一次原請求。 */
class TokenAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // 已經重試過一次仍是 401 就放棄，避免無限重試
        if (response.priorResponse != null) return null

        val staleToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
        val freshToken = AuthManager.renewAccessToken(staleToken) ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
    }
}
