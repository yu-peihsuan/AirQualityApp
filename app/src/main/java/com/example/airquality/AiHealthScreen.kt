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
    // 觀測 AQI 狀態（用來觸發 RAG 建議）
    val aqiState by homeViewModel.uiState.collectAsState()

    // 觀測 RAG 建議狀態
    val ragAdviceState by homeViewModel.ragAdviceState.collectAsState()

    // 進入畫面時自動觸發 RAG 建議
    LaunchedEffect(aqiState) {
        if (aqiState is AqiUiState.Success) {
            homeViewModel.fetchRagAdvice()
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
                    val isWarning = (resp.aqi ?: 0) > 100 || resp.isDownwind == true

                    // ── 下風處警告卡 ──────────────────────────────────────
                    if (resp.isDownwind == true) {
                        val src = resp.downwindSources?.firstOrNull()
                        val typeLabel = when (src?.dominantType) {
                            "fire"               -> "火災/濃煙"
                            "chemical"           -> "化學異味"
                            "dust"               -> "揚塵"
                            "odor"               -> "異味"
                            "vehicle"            -> "車輛廢氣"
                            "factory"            -> "工廠排放"
                            "general_air_quality"-> "空氣品質不良"
                            else                 -> "污染源"
                        }
                        val distText = src?.let { "距離約 ${it.distanceKm} km" } ?: ""
                        val windDirStr = resp.windDirection?.let {
                            homeViewModel.getWindDirectionString(it.toString(), resp.windSpeed?.toString() ?: "0")
                        } ?: ""

                        DownwindWarningCard(
                            typeLabel = typeLabel,
                            distText  = distText,
                            windDir   = windDirStr
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    HealthAdviceCard(
                        ChatMessage(
                            text      = resp.advice ?: "目前無法取得建議，請稍後再試。",
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
                onClick = { homeViewModel.fetchRagAdvice() },
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

@Composable
fun DownwindWarningCard(typeLabel: String, distText: String, windDir: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFF3E0))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = "下風處警告",
            tint = Color(0xFFE65100),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "下風處警告",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "您目前位於「$typeLabel」污染熱點的下風處${if (distText.isNotEmpty()) "（$distText）" else ""}。" +
                       "${if (windDir.isNotEmpty()) "當前吹$windDir，" else ""}污染物可能隨風飄向您所在位置，請注意防護。",
                fontSize = 14.sp,
                color = Color(0xFF5D4037),
                lineHeight = 21.sp
            )
        }
    }
}