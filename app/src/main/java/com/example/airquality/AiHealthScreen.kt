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
import com.example.airquality.ui.theme.*

data class ChatMessage(val text: String, val isUser: Boolean, val subText: String = "")

@Composable
fun AiHealthScreen() {
    var inputText by remember { mutableStateOf("") }
    val messages  = remember {
        mutableStateListOf(
            ChatMessage(
                "王先生您好，你的附近有火警，受東北季風影響，您正處於潛傷下風處。",
                isUser  = false,
                subText = "考量您有氣喘疾病，建議您暫時關閉窗戶並暫停戶外運動。"
            ),
            ChatMessage(
                "王先生您好，現在空氣良好，對您的呼吸道負擔較小，適合出門運動。",
                isUser = false
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
                        fontSize = 20.sp,
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
        ) { Text("🤖", fontSize = 18.sp) }

        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .background(BubbleLeft)
                .padding(14.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(text, color = TextDark, fontSize = 14.sp, lineHeight = 21.sp)
            if (subText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(subText, color = TextMid, fontSize = 13.sp, lineHeight = 20.sp)
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
            Text(text, color = TextDark, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}
