package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airquality.ui.theme.*

data class ChatMessage(val text: String, val isUser: Boolean, val subText: String = "")

@Composable
fun AiHealthScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    var inputText by remember { mutableStateOf("") }
    
    // 觀測 AQI 與 天氣狀態
    val aqiState by homeViewModel.uiState.collectAsState()
    val weatherState by homeViewModel.weatherState.collectAsState()
    
    // 動態產生 AI 顧問訊息
    val dynamicAiMessage = remember(aqiState, weatherState) {
        val aqiPart = when (aqiState) {
            is AqiUiState.Success -> {
                val data = (aqiState as AqiUiState.Success).nearestRecord
                "最鄰近空氣品質測站為${data.sitename}"
            }
            is AqiUiState.Loading -> "正在尋找最鄰近的空氣品質測站..."
            is AqiUiState.Error -> "無法取得空氣品質資料"
        }

        val weatherPart = when (weatherState) {
            is WeatherUiState.Success -> {
                val wData = (weatherState as WeatherUiState.Success).nearestRecord
                val directionString = homeViewModel.getWindDirectionString(wData.windDirection, wData.windSpeed)
                "目前最鄰近的氣象站為${wData.sitename}氣象站，風速為 ${wData.windSpeed} m/s，風向為 $directionString"
            }
            is WeatherUiState.Loading -> "正在取得氣象資料..."
            is WeatherUiState.Error -> "無法取得氣象資料"
        }
        
        // 結合成一句話
        if (aqiState is AqiUiState.Success || weatherState is WeatherUiState.Success) {
            "$aqiPart，$weatherPart。"
        } else {
            "正在為您分析環境資料中..."
        }
    }

    val messages = remember(dynamicAiMessage) {
        mutableStateListOf(
            ChatMessage(
                dynamicAiMessage,
                isUser = false,
                subText = "這是我為您整理的最新環境資訊，請問有什麼我可以幫忙的嗎？"
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        AppHeader(
            title = "AI 健康顧問",
            subtitle = "根據您的健康狀況與空氣品質提供建議"
        )

        // ── 訊息列表 ─────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            messages.forEach { msg ->
                if (msg.isUser) UserBubble(msg.text) else AiBubble(msg.text, msg.subText)
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── 輸入列 ────────────────────────────────────────
        Surface(color = CardWhite, shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    placeholder = { Text("輸入您的問題…", color = TextGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = BgMain,
                        focusedContainerColor   = BgMain,
                        unfocusedBorderColor    = DividerColor,
                        focusedBorderColor      = OrangeMain,
                    ),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))

                // 傳送按鈕（用 Emoji）
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(OrangeMain),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "➤",
                        color = White,
                        fontSize = 23.sp,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AiBubble(text: String, subText: String = "") {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(OrangeBadge),
            contentAlignment = Alignment.Center
        ) { Text("🤖", fontSize = 21.sp) }

        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .background(BubbleLeft)
                .padding(14.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(text, color = TextDark, fontSize = 17.sp, lineHeight = 24.sp)
            if (subText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(subText, color = TextMid, fontSize = 16.sp, lineHeight = 23.sp)
            }
        }
    }
}

@Composable
fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                .background(BubbleRight)
                .padding(14.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(text, color = TextDark, fontSize = 17.sp, lineHeight = 24.sp)
        }
    }
}
