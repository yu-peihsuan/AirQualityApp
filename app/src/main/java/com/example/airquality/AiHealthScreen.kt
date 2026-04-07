package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airquality.ui.theme.*

// 定義訊息資料結構
data class ChatMessage(
    val text: String,
    val subText: String = "",
    val isWarning: Boolean = false
)

@Composable
fun AiHealthScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current

    // 觀測 AQI 與天氣狀態
    val aqiState by homeViewModel.uiState.collectAsState()
    val weatherState by homeViewModel.weatherState.collectAsState()

    // 觀測 RAG 建議狀態
    val ragAdviceState by homeViewModel.ragAdviceState.collectAsState()

    // 進入畫面時自動觸發 RAG 建議
    LaunchedEffect(aqiState) {
        if (aqiState is AqiUiState.Success) {
            homeViewModel.fetchRagAdvice(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        AppHeader(title = "AI 健康顧問", centeredTitle = true, fontSize = 18.sp)

        // ── 主要內容 ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            // ── 環境數據概覽卡（舊有邏輯，保留） ──────────────────────────
            if (aqiState is AqiUiState.Success && weatherState is WeatherUiState.Success) {
                val aqiRecord = (aqiState as AqiUiState.Success).nearestRecord
                val weatherRecord = (weatherState as WeatherUiState.Success).nearestRecord
                val windDirString = homeViewModel.getWindDirectionString(
                    weatherRecord.windDirection, weatherRecord.windSpeed
                )
                val isUnhealthy = aqiRecord.status.contains("不良") ||
                        aqiRecord.status.contains("不佳") ||
                        aqiRecord.status.contains("不健康") ||
                        aqiRecord.status.contains("有害") ||
                        aqiRecord.status.contains("危險") ||
                        aqiRecord.status.contains("警告")

                HealthAdviceCard(
                    ChatMessage(
                        text = "您附近的測站為${aqiRecord.sitename}測站，空氣品質為${aqiRecord.status}。" +
                                "最近氣象測站為${weatherRecord.sitename}，風速${weatherRecord.windSpeed}，吹$windDirString",
                        isWarning = isUnhealthy
                    )
                )
                Spacer(Modifier.height(12.dp))
            }

            // ── AI 個人化建議卡 ───────────────────────────────────────────
            Text(
                text = "AI 個人化建議",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF888888),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when (val state = ragAdviceState) {

                // 尚未載入
                is RagAdviceUiState.Idle -> {
                    RagPlaceholderCard("點擊下方按鈕取得個人化建議")
                }

                // 載入中
                is RagAdviceUiState.Loading -> {
                    RagLoadingCard()
                }

                // 成功
                is RagAdviceUiState.Success -> {
                    val resp = state.response
                    val hasEvent = resp.eventContext != null &&
                            resp.eventContext.isNotBlank() &&
                            resp.eventContext != "無"
                    val isWarning = (resp.aqi ?: 0) > 100 || hasEvent

                    HealthAdviceCard(
                        ChatMessage(
                            text    = resp.advice ?: "目前無法取得建議，請稍後再試。",
                            subText = if (hasEvent) "⚠️ 附近事件：${resp.eventContext}" else "",
                            isWarning = isWarning
                        )
                    )

                    Spacer(Modifier.height(8.dp))
                    // 顯示 AQI 等級小標籤
                    if (resp.aqiLevel != null) {
                        Text(
                            text = "空氣品質：${resp.aqiLevel}（AQI ${resp.aqi ?: "-"}）",
                            fontSize = 12.sp,
                            color = Color(0xFF999999),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // 失敗
                is RagAdviceUiState.Error -> {
                    RagPlaceholderCard("⚠️ ${state.message}")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── 重新取得建議按鈕 ──────────────────────────────────────────
            OutlinedButton(
                onClick = { homeViewModel.fetchRagAdvice(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                enabled = ragAdviceState !is RagAdviceUiState.Loading
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "重新取得",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("重新取得 AI 建議", fontSize = 14.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── 元件 ──────────────────────────────────────────────────────────────────────

@Composable
fun HealthAdviceCard(message: ChatMessage) {
    val backgroundColor = if (message.isWarning) Color(0xFFFDF2F2) else Color(0xFFF2F9E8)
    val borderColor     = if (message.isWarning) Color(0xFFF5E0E0) else Color(0xFFE5EFD8)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Text(
            text = message.text,
            color = Color(0xFF444444),
            fontSize = 18.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium
        )

        if (message.subText.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = Color(0xFFF4E7E7),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = "Warning",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF666666)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = message.subText,
                        color = Color(0xFF555555),
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RagLoadingCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF5F5F5))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = Color(0xFFFF6B35),
            strokeWidth = 3.dp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "AI 正在分析您的健康狀況與當前空氣品質...",
            fontSize = 14.sp,
            color = Color(0xFF888888)
        )
    }
}

@Composable
private fun RagPlaceholderCard(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF5F5F5))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            fontSize = 14.sp,
            color = Color(0xFF888888),
            lineHeight = 22.sp
        )
    }
}