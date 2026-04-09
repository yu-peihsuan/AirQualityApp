package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.ui.theme.*

@Composable
fun NotificationScreen(viewModel: NotificationViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.fetchNotifications(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        AppHeader(title = "通知中心")

        // ── 主內容 (scrollable) ─────────────────────────────────────────────
        when (uiState) {
            is NotificationUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangeMain)
                }
            }
            is NotificationUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text((uiState as NotificationUiState.Error).message, color = RedText)
                }
            }
            is NotificationUiState.Success -> {
                val notifications = (uiState as NotificationUiState.Success).notifications
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(Modifier.height(20.dp))
                    
                    if (notifications.isEmpty()) {
                        Text("目前沒有與您所在區域相關的最新通知。", color = TextMid, fontSize = 16.sp)
                    } else {
                        notifications.forEach { news ->
                            val isWarning = news.title.contains("大火") || news.title.contains("火災") || news.title.contains("火警") || news.title.contains("濃煙") || news.title.contains("失火") || news.title.contains("火燒山") || news.title.contains("空氣品質不良") || news.title.contains("空氣品質惡化") || news.title.contains("空氣品質紅色警戒")
                            val isGood = news.title.contains("轉好") || news.title.contains("改善")
                            
                            val emoji = if (isWarning) "⚠️" else if (isGood) "✅" else "📰"
                            val emojiBg = if (isWarning) RedCard else if (isGood) GreenCard else CardWhite
                            val tag = news.source
                            val tagColor = if (isWarning) RedText else if (isGood) GreenText else OrangeMain
                            val cardBg = if (isWarning) RedCard else if (isGood) GreenCard else CardWhite
                            
                            NotifyCard(
                                emoji = emoji,
                                emojiBg = emojiBg,
                                tag = tag,
                                tagColor = tagColor,
                                time = news.publishedAt.take(16),
                                title = news.title,
                                body = news.summary.ifBlank { news.region },
                                cardBg = cardBg
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun NotifyCard(
    emoji: String,
    emojiBg: androidx.compose.ui.graphics.Color,
    tag: String,
    tagColor: androidx.compose.ui.graphics.Color,
    time: String,
    title: String,
    body: String,
    cardBg: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(emojiBg),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 21.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tag, color = tagColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(time, color = TextGray, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(title, color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = TextMid, fontSize = 16.sp, lineHeight = 22.sp)
        }
    }
}
