package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.ui.theme.*

@Composable
fun NotificationScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgWarm)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("通知中心", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(Modifier.height(20.dp))

        NotifyCard(
            emoji    = "⚠️",
            emojiBg  = RedCard,
            tag      = "緊急警報",
            tagColor = RedText,
            time     = "1分鐘前",
            title    = "數值異常飆升",
            body     = "中正區 PM2.5 濃度急速上升，建議立即撤離。",
            cardBg   = RedCard
        )

        Spacer(Modifier.height(12.dp))

        NotifyCard(
            emoji    = "🔥",
            emojiBg  = OrangeLight,
            tag      = "民眾回報",
            tagColor = OrangeMain,
            time     = "30分鐘前",
            title    = "火災事件",
            body     = "附近近來火災，沾染煙霧來源，很濃。",
            cardBg   = CardWhite
        )

        Spacer(Modifier.height(24.dp))

        Text("歷史紀錄", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextMid)
        Spacer(Modifier.height(12.dp))

        NotifyCard(
            emoji    = "✅",
            emojiBg  = GreenCard,
            tag      = "空氣品質轉好",
            tagColor = GreenText,
            time     = "昨日",
            title    = "空氣品質轉好",
            body     = "昨日天氣擴散條件改善，可以關窗通風了。",
            cardBg   = GreenCard
        )

        Spacer(Modifier.height(32.dp))
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
            Text(emoji, fontSize = 18.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tag, color = tagColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(time, color = TextGray, fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(title, color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = TextMid, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
