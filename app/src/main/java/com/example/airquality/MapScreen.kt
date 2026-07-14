package com.example.airquality

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.example.airquality.ui.theme.*

// ── ViewModel ─────────────────────────────────────────────────────────────────

sealed class MapUiState {
    object Loading : MapUiState()
    data class Success(
        val hotspots: List<HotspotRecord>,
        val reports: List<NewsRecord>,
        val fireAlerts: List<NewsRecord>,
        val userLocation: LatLng?
    ) : MapUiState()
    data class Error(val message: String) : MapUiState()
}

// 保留舊名稱的 typealias，避免其他地方有引用
typealias HotspotUiState = MapUiState

class MapViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun fetchMapData(context: Context) {
        viewModelScope.launch {
            _uiState.value = MapUiState.Loading
            try {
                val hotspotsDeferred    = async { RetrofitClient.apiService.getHotspots() }
                val reportsDeferred     = async { RetrofitClient.apiService.getUserReports() }
                val fireAlertsDeferred  = async {
                    try { RetrofitClient.apiService.getFireAlerts() } catch (e: Exception) { null }
                }
                // 若使用者已手動選擇地點，直接使用該座標；否則取 GPS
                val locationDeferred    = async {
                    AppLocationState.selectedLatLng.value
                        ?.let { (lat, lng) -> LatLng(lat, lng) }
                        ?: getCurrentLocation(context)
                }

                val hotspots     = hotspotsDeferred.await().hotspots
                val reports      = reportsDeferred.await().records.filter {
                    it.latitude != null && it.longitude != null
                }
                val fireAlerts   = (fireAlertsDeferred.await()?.records ?: emptyList()).filter {
                    it.latitude != null && it.longitude != null
                }
                val userLocation = locationDeferred.await()

                // 去重：同縣市 + 火災類型 + 時間差 ≤ 2 小時的回報，以消防署警示為準
                val dedupedReports = deduplicateWithFireAlerts(fireAlerts, reports)

                _uiState.value = MapUiState.Success(hotspots, dedupedReports, fireAlerts, userLocation)
            } catch (e: Exception) {
                Log.e("MapViewModel", "fetchMapData failed", e)
                _uiState.value = MapUiState.Error("地圖資料取得失敗")
            }
        }
    }

    fun timeAlpha(publishedAt: String): Float {
        val ms = parseTimestampMillis(publishedAt) ?: return 0.4f
        val hoursAgo = (System.currentTimeMillis() - ms) / 3_600_000f
        return (1.0f - (hoursAgo / 24f) * 0.7f).coerceIn(0.3f, 1.0f)
    }

    private fun parseTimestampMillis(ts: String): Long? {
        if (ts.isBlank()) return null
        return try {
            // 處理 "2026-04-22T17:21:59+08:00" → "2026-04-22T17:21:59+0800"
            val normalized = ts.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.getDefault())
                .parse(normalized)?.time
        } catch (e: Exception) { null }
    }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val sinDLat = Math.sin(dLat / 2)
        val sinDLng = Math.sin(dLng / 2)
        val a = sinDLat * sinDLat +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinDLng * sinDLng
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    /**
     * 從 candidates 中移除「與任一 fireAlert 距離 ≤ 5km 且時間差 ≤ 4 小時的火災事件」。
     * 兩者都需要有座標才執行距離去重；缺少座標的事件一律保留。
     */
    private fun deduplicateWithFireAlerts(
        fireAlerts: List<NewsRecord>,
        candidates: List<NewsRecord>
    ): List<NewsRecord> {
        if (fireAlerts.isEmpty()) return candidates
        val fourHoursMs = 4 * 60 * 60 * 1000L
        return candidates.filter { candidate ->
            val evType = candidate.structuredEvent?.eventType ?: candidate.category
            if (evType != "fire") return@filter true          // 非火災，保留
            val cLat  = candidate.latitude  ?: return@filter true  // 無座標，保留
            val cLng  = candidate.longitude ?: return@filter true
            val cTime = parseTimestampMillis(candidate.publishedAt) ?: return@filter true
            fireAlerts.none { alert ->
                val aLat  = alert.latitude  ?: return@none false
                val aLng  = alert.longitude ?: return@none false
                val aTime = parseTimestampMillis(alert.publishedAt) ?: return@none false
                haversineKm(cLat, cLng, aLat, aLng) <= 5.0 &&
                    kotlin.math.abs(aTime - cTime) <= fourHoursMs
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(context: Context): LatLng? =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    cont.resume(if (loc != null) LatLng(loc.latitude, loc.longitude) else null)
                }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }
}

// ── 工具：事件類型轉中文 ──────────────────────────────────────────────────────

private fun eventTypeLabel(type: String?): String = when (type) {
    "fire"                -> "火災/濃煙"
    "chemical"            -> "化學異味"
    "dust"                -> "揚塵"
    "odor"                -> "異味"
    "vehicle"             -> "車輛廢氣"
    "factory"             -> "工廠排放"
    "general_air_quality" -> "空氣品質不良"
    else                  -> "污染回報"
}

private fun reportColor(eventType: String?): Color = when (eventType) {
    "fire"                -> Color(0xFFE53935)
    "chemical"            -> Color(0xFF8E24AA)
    "dust"                -> Color(0xFFFF8F00)
    "odor"                -> Color(0xFF00897B)
    "vehicle"             -> Color(0xFF546E7A)
    "factory"             -> Color(0xFFBF360C)
    "general_air_quality" -> Color(0xFF1565C0)
    else                  -> Color(0xFF757575)
}

// ── MapScreen ─────────────────────────────────────────────────────────────────

@Composable
fun MapScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val selectedLatLng by AppLocationState.selectedLatLng.collectAsState()

    LaunchedEffect(Unit) { viewModel.fetchMapData(context) }

    val taiwan = LatLng(23.6978, 120.9605)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(taiwan, 7f)
    }

    // 取得使用者位置後聚焦鏡頭（初次載入）
    LaunchedEffect(uiState) {
        if (uiState is MapUiState.Success) {
            val loc = (uiState as MapUiState.Success).userLocation
            if (loc != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(loc, 13f)
                )
            }
        }
    }

    // 地圖已開著時，使用者切換地點也能即時移動鏡頭
    LaunchedEffect(selectedLatLng) {
        selectedLatLng?.let { (lat, lng) ->
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 13f))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
        AppHeader(
            title = "污染事件地圖",
            centeredTitle = false,
            actions = {
                Text(
                    "返回",
                    color = OrangeMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(8.dp)
                )
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {

            // ── 地圖本體 ──────────────────────────────────────────────────────
            // 手動選擇地點時關閉 GPS 藍點，改在選擇座標顯示自訂藍圈
            val isGpsMode = selectedLatLng == null
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = isGpsMode),
                uiSettings = MapUiSettings(myLocationButtonEnabled = isGpsMode)
            ) {
                // 手動模式：在選擇座標畫仿 GPS 藍圈
                selectedLatLng?.let { (lat, lng) ->
                    val pos = com.google.android.gms.maps.model.LatLng(lat, lng)
                    Circle(
                        center      = pos,
                        radius      = 80.0,
                        fillColor   = Color(0x220088FF),
                        strokeColor = Color(0xFF0088FF),
                        strokeWidth = 2f
                    )
                    Circle(
                        center      = pos,
                        radius      = 20.0,
                        fillColor   = Color(0xFF0088FF),
                        strokeColor = Color.White,
                        strokeWidth = 2.5f
                    )
                }

                if (uiState is MapUiState.Success) {
                    val data = uiState as MapUiState.Success

                    // ── 層0：消防署火災警示 pin ───────────────────────────────
                    data.fireAlerts.forEach { alert ->
                        val lat = alert.latitude ?: return@forEach
                        val lng = alert.longitude ?: return@forEach
                        val alpha = viewModel.timeAlpha(alert.publishedAt)
                        MarkerComposable(
                            state   = MarkerState(position = LatLng(lat, lng)),
                            title   = "🔥 重大火災警示",
                            snippet = "${alert.title.take(30)}｜${alert.summary.take(20)}",
                        ) {
                            FireAlertPin(alpha = alpha)
                        }
                    }

                    // 註：新聞事件因僅能從文字粗略定位、精度不足，不在地圖上顯示，
                    // 改僅列於「通知中心」列表。地圖只保留有可信座標的事件
                    // （官方火災警示、民眾 GPS 回報、回報聚合熱點）。

                    // ── 層2：個別回報 pin ─────────────────────────────────────
                    data.reports.forEach { report ->
                        val lat = report.latitude ?: return@forEach
                        val lng = report.longitude ?: return@forEach
                        val evType  = report.structuredEvent?.eventType
                        val color   = reportColor(evType)
                        val label   = eventTypeLabel(evType ?: report.category)
                        val snippet = report.summary.take(40).ifBlank { report.region }
                        val alpha   = viewModel.timeAlpha(report.publishedAt)

                        MarkerComposable(
                            state   = MarkerState(position = LatLng(lat, lng)),
                            title   = label,
                            snippet = snippet,
                        ) {
                            ReportPin(color = color, alpha = alpha)
                        }
                    }

                    // ── 層2：熱點叢集（警戒圓 + 中心標記）───────────────────
                    data.hotspots.forEach { hotspot ->
                        val markerColor = when {
                            hotspot.intensity >= 0.8 -> Color(0xFFE53935)
                            hotspot.intensity >= 0.5 -> Color(0xFFFF6600)
                            else                     -> Color(0xFFFFCC00)
                        }
                        val typeLabel = eventTypeLabel(hotspot.dominantType)
                        val windInfo = if (hotspot.isCalmWind) {
                            "⚠ 擴散條件差（近乎無風）"
                        } else {
                            val dir = windDirectionLabel(hotspot.windDirection)
                            "風向：$dir  風速：${hotspot.windSpeed} m/s"
                        }

                        // 警戒範圍圓：無風時用灰紫色
                        val circleColor = if (hotspot.isCalmWind) Color(0xFF6A1B9A) else markerColor
                        Circle(
                            center      = LatLng(hotspot.lat, hotspot.lng),
                            radius      = hotspot.radiusKm * 1000.0,
                            fillColor   = circleColor.copy(alpha = 0.18f),
                            strokeColor = circleColor.copy(alpha = 0.80f),
                            strokeWidth = if (hotspot.isCalmWind) 3.5f else 2.5f
                        )

                        // 叢集中心標記
                        MarkerComposable(
                            state   = MarkerState(position = LatLng(hotspot.lat, hotspot.lng)),
                            title   = "$typeLabel（${hotspot.count} 筆回報）",
                            snippet = "強度：${(hotspot.intensity * 100).toInt()}%  範圍：${hotspot.radiusKm} km｜$windInfo",
                        ) {
                            HotspotMarker(count = hotspot.count, color = markerColor, isCalmWind = hotspot.isCalmWind)
                        }
                    }
                }
            }

            // ── 載入中 ────────────────────────────────────────────────────────
            if (uiState is MapUiState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = OrangeMain)
                }
            }

            // ── 錯誤 ──────────────────────────────────────────────────────────
            if (uiState is MapUiState.Error) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text((uiState as MapUiState.Error).message, color = RedText)
                }
            }

            // ── 無任何資料提示（熱點＆回報都空才顯示） ───────────────────────
            if (uiState is MapUiState.Success) {
                val data = uiState as MapUiState.Success
                if (data.hotspots.isEmpty() && data.reports.isEmpty() && data.fireAlerts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("目前尚無回報資料", color = TextMid, fontSize = 14.sp)
                    }
                }
            }

            // ── 圖例 ──────────────────────────────────────────────────────────
            if (uiState is MapUiState.Success) {
                val data = uiState as MapUiState.Success
                if (data.hotspots.isNotEmpty() || data.reports.isNotEmpty() || data.fireAlerts.isNotEmpty()) {
                    var legendExpanded by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 64.dp, end = 12.dp, start = 12.dp, bottom = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.93f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.clickable { legendExpanded = !legendExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("圖例", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text(
                                if (legendExpanded) "▲" else "▼",
                                fontSize = 9.sp,
                                color = TextGray
                            )
                        }
                        if (legendExpanded) {
                            if (data.fireAlerts.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text("— 消防署火災警示", fontSize = 10.sp, color = TextGray)
                                LegendItem(Color(0xFFB71C1C), "重大火災", circle = false, isFireAlert = true)
                            }
                            if (data.hotspots.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text("— 熱點叢集", fontSize = 10.sp, color = TextGray)
                                LegendItem(Color(0xFFE53935), "高強度 ≥ 80%", circle = true)
                                LegendItem(Color(0xFFFF6600), "中強度 ≥ 50%", circle = true)
                                LegendItem(Color(0xFFFFCC00), "低強度 < 50%", circle = true)
                                LegendItem(Color(0xFF6A1B9A), "擴散條件差（無風）", circle = true)
                            }
                            if (data.reports.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text("— 個別回報", fontSize = 10.sp, color = TextGray)
                                LegendItem(Color(0xFFE53935), "火災/濃煙",   circle = false)
                                LegendItem(Color(0xFF8E24AA), "化學異味",    circle = false)
                                LegendItem(Color(0xFFFF8F00), "揚塵",        circle = false)
                                LegendItem(Color(0xFF00897B), "異味",        circle = false)
                                LegendItem(Color(0xFF546E7A), "車輛/工廠",   circle = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 風向角度轉中文方位 ─────────────────────────────────────────────────────────

private fun windDirectionLabel(deg: Double): String = when ((deg + 22.5).toInt() / 45 % 8) {
    0 -> "北"; 1 -> "東北"; 2 -> "東"; 3 -> "東南"
    4 -> "南"; 5 -> "西南"; 6 -> "西"; else -> "西北"
}

// ── 消防署火災警示 Pin ─────────────────────────────────────────────────────────

@Composable
fun FireAlertPin(alpha: Float = 1f) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFFB71C1C).copy(alpha = 0.22f * alpha))
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFB71C1C).copy(alpha = alpha)),
            contentAlignment = Alignment.Center
        ) {
            Text("🔥", fontSize = 14.sp)
        }
    }
}

// ── 個別回報小 Pin ─────────────────────────────────────────────────────────────

@Composable
fun ReportPin(color: Color, alpha: Float = 1f) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.22f * alpha))
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
    }
}

// ── 熱點叢集標記 ──────────────────────────────────────────────────────────────

@Composable
fun HotspotMarker(count: Int, color: Color, isCalmWind: Boolean = false) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // 無風擴散條件差：右上角顯示警示標誌
        if (isCalmWind) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6A1B9A)),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── 圖例列 ────────────────────────────────────────────────────────────────────

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    circle: Boolean,
    isFireAlert: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            isFireAlert -> Box(
                modifier = Modifier.size(12.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center
            ) {
                Text("🔥", fontSize = 7.sp)
            }
            circle -> Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(color)
            )
            else -> Box(
                modifier = Modifier.size(8.dp).clip(CircleShape).background(color)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = TextMid)
    }
}
