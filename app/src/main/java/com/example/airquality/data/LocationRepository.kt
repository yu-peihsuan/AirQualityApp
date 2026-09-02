package com.example.airquality.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 定位結果。
 *
 * 「沒有權限」與「有權限但抓不到座標」是兩種不同的情況，呼叫端要給的
 * 回饋也不同（前者引導使用者授權或改選地區，後者只是稍後再試），
 * 所以用 sealed class 區分，而不是一律回 null。
 */
sealed class LocationResult {
    data class Available(val coordinates: Coordinates) : LocationResult()
    /** 已授權，但定位服務這次沒給座標（室內、剛開機、GPS 關閉等）。 */
    object Unavailable : LocationResult()
    /** 使用者尚未授權定位。 */
    object PermissionDenied : LocationResult()
}

/**
 * 裝置定位。只有這一層拿得到 Context，ViewModel 與 Composable 都改為呼叫這裡。
 */
open class LocationRepository(private val appContext: Context) {

    open fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * 取得目前座標。未授權時直接回 [LocationResult.PermissionDenied]，
     * 不會呼叫定位 API，也就不會丟出 SecurityException。
     */
    open suspend fun currentLocation(highAccuracy: Boolean = false): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied
        val priority =
            if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY
            else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val coords = requestLocation(priority)
        return if (coords != null) LocationResult.Available(coords) else LocationResult.Unavailable
    }

    // 權限已在 currentLocation() 檢查過；lint 無法追蹤跨函式的檢查，故在此抑制。
    // 使用者在 App 執行中從系統設定收回權限仍可能丟 SecurityException，一併接住。
    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(priority: Int): Coordinates? =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(appContext)
            val cts = CancellationTokenSource()
            try {
                client.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { loc ->
                        cont.resume(loc?.let { Coordinates(it.latitude, it.longitude) })
                    }
                    .addOnFailureListener { cont.resume(null) }
            } catch (e: SecurityException) {
                Log.w("LocationRepository", "定位權限已被收回：${e.message}")
                cont.resume(null)
            }
            cont.invokeOnCancellation { cts.cancel() }
        }
}
