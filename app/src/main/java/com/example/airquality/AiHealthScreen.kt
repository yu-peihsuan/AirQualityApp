package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

// 定義訊息資料結構，增加是否為警告狀態的判斷
data class ChatMessage(
    val text: String,
    val subText: String = "",
    val isWarning: Boolean = false
)

@Composable
fun AiHealthScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    // 觀測 AQI 與 天氣狀態
    val aqiState by homeViewModel.uiState.collectAsState()
    val weatherState by homeViewModel.weatherState.collectAsState()

    // 模擬圖片中的兩則訊息資料
    val baseMessages = mutableListOf<ChatMessage>()

    if (aqiState is AqiUiState.Success && weatherState is WeatherUiState.Success) {
        val aqiRecord = (aqiState as AqiUiState.Success).nearestRecord
        val weatherRecord = (weatherState as WeatherUiState.Success).nearestRecord
        val windDirString = homeViewModel.getWindDirectionString(weatherRecord.windDirection, weatherRecord.windSpeed)
        
        val isUnhealthy = aqiRecord.status.contains("不良") || 
                          aqiRecord.status.contains("不佳") || 
                          aqiRecord.status.contains("不健康") || 
                          aqiRecord.status.contains("有害") ||
                          aqiRecord.status.contains("危險") ||
                          aqiRecord.status.contains("警告")

        baseMessages.add(
            ChatMessage(
                text = "您附近的測站為${aqiRecord.sitename}測站，空氣品質為${aqiRecord.status}。最近氣象測站為${weatherRecord.sitename}，風速${weatherRecord.windSpeed}，吹$windDirString",
                isWarning = isUnhealthy
            )
        )
    }

    val messages = baseMessages.toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF)) 
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        AppHeader(title = "AI 健康顧問", centeredTitle = true, fontSize = 18.sp)

        // ── 訊息列表 (帶框框的卡片) ─────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            


            messages.forEachIndexed { index, msg ->
                HealthAdviceCard(msg)
                if (index < messages.size - 1) {
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun HealthAdviceCard(message: ChatMessage) {
    // 根據是否為警告切換顏色
    val backgroundColor = if (message.isWarning) Color(0xFFFDF2F2) else Color(0xFFF2F9E8)
    val borderColor = if (message.isWarning) Color(0xFFF5E0E0) else Color(0xFFE5EFD8)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(16.dp)
    ) {
            // 主要文字
            Text(
                text = message.text,
                color = Color(0xFF444444),
                fontSize = 18.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium
            )

            // 如果有次要建議文字，顯示內層的小框框
            if (message.subText.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = Color(0xFFF4E7E7), // 內層稍深的粉色
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // 盾牌圖示
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